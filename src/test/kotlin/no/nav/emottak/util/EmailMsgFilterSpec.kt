package no.nav.emottak.util

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import jakarta.mail.internet.MimeMessage
import no.nav.emottak.config
import no.nav.emottak.session
import no.nav.emottak.smtp.EmailMsg
import no.nav.emottak.smtp.MimeMessageWrapper
import kotlin.uuid.Uuid

private const val PAYLOAD_MESSAGE = "testmail/inntektsforesporsel.eml"
private const val PAYLOAD_MESSAGE_INVALID_FROM = "testmail/inntektsforesporsel_invalid_from.eml"
private const val PAYLOAD_MESSAGE_INVALID_SERVICE = "testmail/egenandelforesporsel.eml"
private const val SIGNAL_MESSAGE = "testmail/acknowledgment.eml"
private const val SIGNAL_MESSAGE_NO_FROM = "testmail/acknowledgment_no_from.eml"
private const val NOT_EBXML_MESSAGE = "testmail/not_ebxml.eml"
private const val EBXML_NO_SERVICE = "testmail/ebxml_no_service.eml"

class EmailMsgFilterSpec : StringSpec({
    val config = config()
    val classLoader = this::class.java.classLoader

    fun String.emlToEmailMsg(): EmailMsg =
        MimeMessageWrapper(
            mimeMessage = MimeMessage(session(config.smtp), classLoader.getResourceAsStream(this)),
            requestId = Uuid.random()
        ).mapEmailMsg()

    "Returns BOTH when From is valid and signal message" {
        val (forwardingSystem, serviceName) = SIGNAL_MESSAGE.emlToEmailMsg().filterMimeMessage()
        forwardingSystem shouldBe ForwardingSystem.BOTH
        serviceName shouldBe "urn:oasis:names:tc:ebxml-msg:service"
    }

    "Returns EBMS when From is valid and accepted type" {
        val (forwardingSystem, serviceName) = PAYLOAD_MESSAGE.emlToEmailMsg().filterMimeMessage()
        forwardingSystem shouldBe ForwardingSystem.EBMS
        serviceName shouldBe "Inntektsforesporsel"
    }

    "Returns EMOTTAK when From is missing" {
        val (forwardingSystem, serviceName) = SIGNAL_MESSAGE_NO_FROM.emlToEmailMsg().filterMimeMessage()
        forwardingSystem shouldBe ForwardingSystem.EMOTTAK
        serviceName shouldBe "urn:oasis:names:tc:ebxml-msg:service"
    }

    "Returns EMOTTAK when From is not accepted" {
        val (forwardingSystem, serviceName) = PAYLOAD_MESSAGE_INVALID_FROM.emlToEmailMsg().filterMimeMessage()
        forwardingSystem shouldBe ForwardingSystem.EMOTTAK
        serviceName shouldBe "Inntektsforesporsel"
    }

    "Returns EMOTTAK when Service Type is not accepted" {
        val (forwardingSystem, serviceName) = PAYLOAD_MESSAGE_INVALID_SERVICE.emlToEmailMsg().filterMimeMessage()
        forwardingSystem shouldBe ForwardingSystem.EMOTTAK
        serviceName shouldBe "HarBorgerFrikortMengde"
    }

    "Returns EMOTTAK when Service Type is not found" {
        val (forwardingSystem, serviceName) = EBXML_NO_SERVICE.emlToEmailMsg().filterMimeMessage()
        forwardingSystem shouldBe ForwardingSystem.EMOTTAK
        serviceName shouldBe "Unknown"
    }

    "Returns EMOTTAK when document is unparsable" {
        val (forwardingSystem, serviceName) = NOT_EBXML_MESSAGE.emlToEmailMsg().filterMimeMessage()
        forwardingSystem shouldBe ForwardingSystem.EMOTTAK
        serviceName shouldBe "Unparseable"
    }

    "extracts email address only when From contains angle brackets" {
        "Kari Nordmann <no-reply@nav.no>".extractEmailAddressOnly() shouldBe "no-reply@nav.no"
    }

    "Returns lowercase email when From does not contain angle brackets" {
        "NO-REPLY@NAV.NO".extractEmailAddressOnly() shouldBe "no-reply@nav.no"
    }
})
