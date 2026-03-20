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
private const val PAYLOAD_MESSAGE_INVALID_CPAID = "testmail/inntektsforesporsel_invalid_cpaid.eml"
private const val PAYLOAD_MESSAGE_INVALID_SERVICE = "testmail/egenandelforesporsel.eml"
private const val SIGNAL_MESSAGE = "testmail/acknowledgment.eml"
private const val NOT_EBXML_MESSAGE = "testmail/not_ebxml.eml"
private const val EBXML_NO_SERVICE = "testmail/ebxml_no_service.eml"
private const val EBXML_NO_CPAID = "testmail/ebxml_no_cpaid.eml"

class EmailMsgFilterSpec : StringSpec({
    val config = config()
    val classLoader = this::class.java.classLoader

    fun String.emlToEmailMsg(): EmailMsg =
        MimeMessageWrapper(
            mimeMessage = MimeMessage(session(config.smtp), classLoader.getResourceAsStream(this)),
            requestId = Uuid.random()
        ).mapEmailMsg()

    "getForwardingSystem returns BOTH when CPAId is valid and signal message" {
        val forwardingSystem = SIGNAL_MESSAGE.emlToEmailMsg().getForwardingSystem()
        forwardingSystem shouldBe ForwardingSystem.BOTH
    }

    "getForwardingSystem returns EBMS when CPAId is valid and accepted type" {
        val forwardingSystem = PAYLOAD_MESSAGE.emlToEmailMsg().getForwardingSystem()
        forwardingSystem shouldBe ForwardingSystem.EBMS
    }

    "getForwardingSystem returns EMOTTAK when CPAId is missing" {
        val forwardingSystem = EBXML_NO_CPAID.emlToEmailMsg().getForwardingSystem()
        forwardingSystem shouldBe ForwardingSystem.EMOTTAK
    }

    "getForwardingSystem returns EMOTTAK when CPAId is not accepted" {
        val forwardingSystem = PAYLOAD_MESSAGE_INVALID_CPAID.emlToEmailMsg().getForwardingSystem()
        forwardingSystem shouldBe ForwardingSystem.EMOTTAK
    }

    "getForwardingSystem returns EMOTTAK when Service Type is not accepted" {
        val forwardingSystem = PAYLOAD_MESSAGE_INVALID_SERVICE.emlToEmailMsg().getForwardingSystem()
        forwardingSystem shouldBe ForwardingSystem.EMOTTAK
    }

    "getForwardingSystem returns EMOTTAK when Service Type is not found" {
        val forwardingSystem = EBXML_NO_SERVICE.emlToEmailMsg().getForwardingSystem()
        forwardingSystem shouldBe ForwardingSystem.EMOTTAK
    }

    "getForwardingSystem returns EMOTTAK when document is unparsable" {
        val forwardingSystem = NOT_EBXML_MESSAGE.emlToEmailMsg().getForwardingSystem()
        forwardingSystem shouldBe ForwardingSystem.EMOTTAK
    }

    "extracts email address only when From contains angle brackets" {
        "Kari Nordmann <no-reply@nav.no>".extractEmailAddressOnly() shouldBe "no-reply@nav.no"
    }

    "Returns lowercase email when From does not contain angle brackets" {
        "NO-REPLY@NAV.NO".extractEmailAddressOnly() shouldBe "no-reply@nav.no"
    }
})
