package no.nav.emottak.smtp

import arrow.fx.coroutines.resourceScope
import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.ServerSetupTest.SMTP_POP3
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.emottak.config
import no.nav.emottak.model.MailMetadata
import no.nav.emottak.model.Payload
import no.nav.emottak.model.PayloadMessage
import no.nav.emottak.model.SignalMessage
import no.nav.emottak.session
import no.nav.emottak.util.fakeEventLoggingService
import kotlin.uuid.Uuid

class MailSenderSpec : StringSpec({
    lateinit var mailSender: MailSender
    lateinit var greenMail: GreenMail
    val config = config()

    beforeEach {
        val session = session(config.smtp)
        greenMail = GreenMail(SMTP_POP3).apply {
            start()
            setUser(config.smtp.username.value, config.smtp.username.value, config.smtp.password.value)
        }
        mailSender = MailSender(session, fakeEventLoggingService())
    }

    afterEach {
        greenMail.purgeEmailFromAllMailboxes()
        greenMail.stop()
    }

    "send signal message" {
        resourceScope {
            val metadata = MailMetadata("to", "signal", "fromNav")
            val message = SignalMessage(Uuid.random(), getEnvelope().toByteArray())

            greenMail.receivedMessages shouldHaveSize 0

            mailSender.sendSignalMessage(metadata, message)

            greenMail.receivedMessages shouldHaveSize 1
        }
    }

    "send payload message" {
        resourceScope {
            val metadata = MailMetadata("to", "payload", "fromNav")
            val message = PayloadMessage(Uuid.random(), getEnvelope().toByteArray(), listOf(getPayload()))

            greenMail.receivedMessages shouldHaveSize 0

            mailSender.sendPayloadMessage(metadata, message)

            greenMail.receivedMessages shouldHaveSize 1
        }
    }

    "sent message has expected mime headers" {
        resourceScope {
            val sender = "fromNav"
            val receiver = "toSomeone"
            val subject = "A fine subject"
            val metadata = MailMetadata(receiver, subject, sender)
            val message = SignalMessage(Uuid.random(), getEnvelope().toByteArray())

            greenMail.receivedMessages.size shouldBe 0

            mailSender.sendSignalMessage(metadata, message)

            with(greenMail.receivedMessages) {
                this.size shouldBe 1
                this.first().getHeader("From") shouldBe arrayOf(sender)
                this.first().getHeader("To") shouldBe arrayOf(receiver)
                this.first().getHeader("Subject") shouldBe arrayOf(subject)
                this.first().getHeader("SOAPAction") shouldBe arrayOf("\"ebXML\"")
                this.first().getHeader("X-Mailer") shouldBe arrayOf("NAV EBMS")
            }
        }
    }

    "sent message without senderAddress uses fallback" {
        resourceScope {
            val receiver = "toSomeone"
            val subject = "A fine subject"
            val metadata = MailMetadata(receiver, subject, "")
            val message = SignalMessage(Uuid.random(), getEnvelope().toByteArray())

            greenMail.receivedMessages shouldHaveSize 0

            mailSender.sendSignalMessage(metadata, message)

            with(greenMail.receivedMessages) {
                this shouldHaveSize 1
                this.first().getHeader("From") shouldBe arrayOf(config().smtp.smtpFromAddress)
                this.first().getHeader("To") shouldBe arrayOf(receiver)
                this.first().getHeader("Subject") shouldBe arrayOf(subject)
            }
        }
    }
})

private fun getEnvelope() =
    """<?xml version="1.0" encoding="UTF-8"?><soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope"><soap:Body><message>Hello</message></soap:Body></soap:Envelope>"""

private fun getPayload() = Payload(Uuid.random(), "content", "text/plain", "content".toByteArray())
