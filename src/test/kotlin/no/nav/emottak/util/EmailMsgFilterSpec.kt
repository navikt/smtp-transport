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

class EmailMsgFilterSpec : StringSpec({
    val config = config()
    val classLoader = this::class.java.classLoader

    fun String.emlToEmailMsg(): EmailMsg =
        MimeMessageWrapper(
            mimeMessage = MimeMessage(session(config.smtp), classLoader.getResourceAsStream(this)),
            requestId = Uuid.random()
        ).mapEmailMsg()

    "Returns BOTH when From is valid and signal message" {
        SIGNAL_MESSAGE.emlToEmailMsg().filterMimeMessage() shouldBe ForwardingSystem.BOTH
    }

    "Returns EBMS when From is valid and accepted type" {
        PAYLOAD_MESSAGE.emlToEmailMsg().filterMimeMessage() shouldBe ForwardingSystem.EBMS
    }

    "Returns EMOTTAK when From is missing" {
        SIGNAL_MESSAGE_NO_FROM.emlToEmailMsg().filterMimeMessage() shouldBe ForwardingSystem.EMOTTAK
    }

    "Returns EMOTTAK when From is not accepted" {
        PAYLOAD_MESSAGE_INVALID_FROM.emlToEmailMsg().filterMimeMessage() shouldBe ForwardingSystem.EMOTTAK
    }

    "Returns EMOTTAK when Service Type is not accepted" {
        PAYLOAD_MESSAGE_INVALID_SERVICE.emlToEmailMsg().filterMimeMessage() shouldBe ForwardingSystem.EMOTTAK
    }

    "extracts email address only when From contains angle brackets" {
        "Kari Nordmann <no-reply@nav.no>".extractEmailAddressOnly() shouldBe "no-reply@nav.no"
    }

    "Returns lowercase email when From does not contain angle brackets" {
        "NO-REPLY@NAV.NO".extractEmailAddressOnly() shouldBe "no-reply@nav.no"
    }
})
