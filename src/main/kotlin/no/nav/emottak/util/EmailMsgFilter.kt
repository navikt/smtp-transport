package no.nav.emottak.util

import jakarta.mail.internet.MimeMessage
import net.logstash.logback.marker.LogstashMarker
import net.logstash.logback.marker.Markers
import no.nav.emottak.config
import no.nav.emottak.log
import no.nav.emottak.smtp.EmailMsg
import no.nav.emottak.smtp.ForwardableMimeMessage
import org.w3c.dom.Document
import org.w3c.dom.NodeList
import javax.xml.namespace.NamespaceContext
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory

private val ebmsServices = config().ebmsFilter.ebmsMessageTypes
private val bothServices = config().ebmsFilter.bothMessageTypes

fun EmailMsg.filterMessageForwarding(): ForwardableMimeMessage {
    val (forwardingSystem, serviceName) = this.filterMimeMessage()
    val marker: LogstashMarker = Markers.appendEntries(
        mapOf(
            "requestId" to this.requestId.toString(),
            "smtpSender" to this.senderAddress,
            "smtpSubject" to (this.headers["Subject"] ?: ""),
            "service" to serviceName,
            "forwardingSystem" to forwardingSystem
        )
    )
    log.info(marker, "Message forwarding system identified")
    return when (forwardingSystem) {
        ForwardingSystem.EBMS -> ForwardableMimeMessage(forwardingSystem, null)
        ForwardingSystem.EMOTTAK -> ForwardableMimeMessage(forwardingSystem, MimeMessage(originalMimeMessage))
        ForwardingSystem.BOTH -> ForwardableMimeMessage(forwardingSystem, MimeMessage(originalMimeMessage))
    }
}

fun EmailMsg.filterMimeMessage(): Pair<ForwardingSystem, String> {
    val envelopeServiceName = getEnvelope().toXmlDocument()?.getEbxmlServiceName() ?: "Unparseable"
    if (senderAddress.isNotBlank()) {
        if (isFromAcceptedAddress(senderAddress)) {
            if (bothServices.contains(envelopeServiceName)) {
                return ForwardingSystem.BOTH to envelopeServiceName
            }
            if (ebmsServices.contains(envelopeServiceName)) {
                return ForwardingSystem.EBMS to envelopeServiceName
            }
        }
    }
    return ForwardingSystem.EMOTTAK to envelopeServiceName
}

private fun isFromAcceptedAddress(from: String) = config().ebmsFilter.senderAddresses.any { it.equals(from, ignoreCase = true) }

private fun ByteArray.toXmlDocument(): Document? {
    return try {
        val dbFactory = DocumentBuilderFactory.newInstance()
        dbFactory.isNamespaceAware = true
        val dBuilder = dbFactory.newDocumentBuilder()
        val inputStream = this.inputStream() // ByteArrayInputStream
        val doc = dBuilder.parse(inputStream)
        doc.documentElement.normalize()
        doc
    } catch (e: Exception) {
        log.warn("Failed to parse XML: ${e.message}")
        null
    }
}

private fun Document.getEbxmlServiceName(): String {
    return try {
        val nsUri = this.documentElement.namespaceURI
        val xPath = XPathFactory.newInstance().newXPath()
        if (nsUri != null) {
            xPath.namespaceContext = object : NamespaceContext {
                override fun getNamespaceURI(prefix: String?): String = nsUri
                override fun getPrefix(namespaceURI: String?): String? = null
                override fun getPrefixes(namespaceURI: String?): MutableIterator<String> = mutableListOf<String>().iterator()
            }
        }
        val localNameExpr = "//*[local-name()='Service']"
        val nodeList = xPath.evaluate(localNameExpr, this, XPathConstants.NODESET) as NodeList
        return if (nodeList.length == 1) {
            nodeList.item(0).textContent
        } else {
            "Unknown"
        }
    } catch (e: Exception) {
        log.warn("Failed to check XML for element 'Service': ${e.message}")
        "ExceptionThrown"
    }
}

enum class ForwardingSystem {
    EBMS, EMOTTAK, BOTH
}
