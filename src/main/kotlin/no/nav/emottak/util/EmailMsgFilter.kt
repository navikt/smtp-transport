package no.nav.emottak.util

import jakarta.mail.internet.MimeMessage
import no.nav.emottak.config
import no.nav.emottak.log
import no.nav.emottak.smtp.EmailMsg
import no.nav.emottak.smtp.ForwardableMimeMessage
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import javax.xml.namespace.NamespaceContext
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory

fun EmailMsg.filterMessageForwarding(): ForwardableMimeMessage = when (val forwardingSystem = this.filterMimeMessage()) {
    ForwardingSystem.EBMS -> ForwardableMimeMessage(forwardingSystem, null)
    ForwardingSystem.EMOTTAK -> ForwardableMimeMessage(forwardingSystem, MimeMessage(originalMimeMessage))
    ForwardingSystem.BOTH -> ForwardableMimeMessage(forwardingSystem, MimeMessage(originalMimeMessage))
}

fun EmailMsg.filterMimeMessage(): ForwardingSystem {
    val envelopeDoc = getEnvelope().toXmlDocument()
    if (senderAddress.isNotBlank() && envelopeDoc != null) {
        if (isFromAcceptedAddress(senderAddress)) {
            if (envelopeDoc.ebXMLHasServiceType(URN_OASIS_NAMES_TC_EBXML_MSG_SERVICE)) {
                return ForwardingSystem.BOTH
            }
            if (envelopeDoc.ebXMLHasServiceType(*activatedServices.toTypedArray())) {
                return ForwardingSystem.EBMS
            }
        }
    }
    return ForwardingSystem.EMOTTAK
}

fun String.extractEmailAddressOnly() = if (this.contains("<")) this.substringAfter("<").substringBefore(">").lowercase() else this.lowercase()

const val URN_OASIS_NAMES_TC_EBXML_MSG_SERVICE = "urn:oasis:names:tc:ebxml-msg:service"
val activatedServices = config().ebmsFilter.ebmsMessageTypes

private fun isFromAcceptedAddress(from: String) = config().ebmsFilter.senderAddresses.any { it.equals(from, ignoreCase = true) }

/**
 * Parses the XML from a ByteArray using UTF-8 encoding and returns a Document object.
 */
fun ByteArray.toXmlDocument(): Document? {
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

/**
 * Checks if the given Document contains any of the specified values in the specified element.
 * Handles namespaces and prefixes.
 */
fun Document.ebXMLHasServiceType(vararg values: String): Boolean {
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
        for (i in 0 until nodeList.length) {
            val node = nodeList.item(i)
            if (node is Element && values.any { it == node.textContent }) {
                return true
            }
        }
        false
    } catch (e: Exception) {
        log.warn("Failed to check XML for element 'Service': ${e.message}")
        false
    }
}

enum class ForwardingSystem {
    EBMS, EMOTTAK, BOTH
}
