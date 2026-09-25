package no.nav.emottak.util

import jakarta.mail.internet.MimeMessage
import net.logstash.logback.marker.LogstashMarker
import net.logstash.logback.marker.Markers
import no.nav.emottak.configuration.ForwardingSystem
import no.nav.emottak.log
import no.nav.emottak.smtp.EmailMsg
import no.nav.emottak.smtp.ForwardableMimeMessage
import org.w3c.dom.Document
import org.w3c.dom.NodeList
import javax.xml.namespace.NamespaceContext
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory

fun EmailMsg.filterMessageForwarding(rules: Map<String, ServiceRule> = filterRules()): ForwardableMimeMessage {
    val ebxmlDocument = getEnvelope().toXmlDocument()
    val service = ebxmlDocument?.getEbxmlServiceName() ?: "UnparsableService"
    val cpaId = ebxmlDocument?.getEbxmlCpaId() ?: "UnparsableCpaId"
    val action = ebxmlDocument?.getEbxmlAction() ?: "UnparsableAction"

    val forwardingDecision = rules.resolveForwarding(service, cpaId)
    val forwardingSystem = forwardingDecision.forwardTo

    val marker: LogstashMarker = Markers.appendEntries(
        mapOf(
            "requestId" to this.requestId.toString(),
            "smtpSender" to this.senderAddress,
            "smtpSubject" to (this.headers["Subject"] ?: "-"),
            "service" to service,
            "cpaId" to cpaId,
            "action" to action,
            "forwardingSystem" to forwardingSystem,
            "filterMatch" to forwardingDecision.filterMatch,
            "sourceSystem" to (this.headers["X-Mailer"] ?: "-"),
            "envelopeSizeBytes" to this.envelopeSizeBytes,
            "payloadSizeBytes" to this.payloadSizeBytes,
            "totalSizeBytes" to this.totalSizeBytes,
            "partCount" to this.parts.size
        )
    )
    log.info(marker, "Message forwarding system identified")

    return when (forwardingSystem) {
        ForwardingSystem.EBMS -> ForwardableMimeMessage(forwardingSystem, null, service, cpaId, action)
        ForwardingSystem.EMOTTAK -> ForwardableMimeMessage(forwardingSystem, MimeMessage(originalMimeMessage), service, cpaId, action)
        ForwardingSystem.BOTH -> ForwardableMimeMessage(forwardingSystem, MimeMessage(originalMimeMessage), service, cpaId, action)
    }
}

private fun ByteArray.toXmlDocument(): Document? {
    return try {
        val dbFactory = DocumentBuilderFactory.newInstance()
        dbFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
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
private fun Document.getEbxmlAction(): String = this.getXmlElementValue("Action")

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
