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

private val typesToEbms = config().ebmsFilter.typesToEbms
private val typesToBoth = config().ebmsFilter.typesToBoth
private val cpaIds = config().ebmsFilter.cpaId

fun EmailMsg.filterMessageForwarding(): ForwardableMimeMessage {
    return when (val forwardingSystem = this.getForwardingSystem()) {
        ForwardingSystem.EBMS -> ForwardableMimeMessage(forwardingSystem, null)
        ForwardingSystem.EMOTTAK -> ForwardableMimeMessage(forwardingSystem, MimeMessage(originalMimeMessage))
        ForwardingSystem.BOTH -> ForwardableMimeMessage(forwardingSystem, MimeMessage(originalMimeMessage))
    }
}

fun EmailMsg.getForwardingSystem(): ForwardingSystem {
    val ebxmlDocument = getEnvelope().toXmlDocument()
    val envelopeServiceName = ebxmlDocument?.getEbxmlServiceName() ?: "UnparsableService"
    val envelopeCpaId = ebxmlDocument?.getEbxmlCpaId() ?: "UnparsableCpaId"

    return if (isAcceptedCpaId(envelopeCpaId)) {
        if (typesToBoth.contains(envelopeServiceName)) {
            ForwardingSystem.BOTH
        } else if (typesToEbms.contains(envelopeServiceName)) {
            ForwardingSystem.EBMS
        } else {
            ForwardingSystem.EMOTTAK
        }
    } else {
        ForwardingSystem.EMOTTAK
    }.also {
        val marker: LogstashMarker = Markers.appendEntries(
            mapOf(
                "requestId" to this.requestId.toString(),
                "smtpSender" to this.senderAddress,
                "smtpSubject" to (this.headers["Subject"] ?: "-"),
                "service" to envelopeServiceName,
                "cpaId" to envelopeCpaId,
                "forwardingSystem" to it,
                "sourceSystem" to (this.headers["X-Mailer"] ?: "-")
            )
        )
        log.info(marker, "Message forwarding system identified")
    }
}

private fun isAcceptedCpaId(cpaId: String) = cpaIds.any { it.equals(cpaId, ignoreCase = true) }

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

private fun Document.getEbxmlServiceName(): String = this.getXmlElementValue("Service")
private fun Document.getEbxmlCpaId(): String = this.getXmlElementValue("CPAId")

private fun Document.getXmlElementValue(elementName: String): String {
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
        val localNameExpr = "//*[local-name()='$elementName']"
        val nodeList = xPath.evaluate(localNameExpr, this, XPathConstants.NODESET) as NodeList
        return if (nodeList.length == 1) {
            nodeList.item(0).textContent
        } else {
            "Unknown"
        }
    } catch (e: Exception) {
        log.warn("Failed to check XML for element '$elementName': ${e.message}")
        e::class.simpleName ?: "UnknownError"
    }
}

enum class ForwardingSystem {
    EBMS, EMOTTAK, BOTH
}
