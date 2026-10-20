package no.nav.emottak.smtp

import arrow.fx.coroutines.resourceScope
import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.ServerSetupTest.SMTP_POP3
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import jakarta.mail.SendFailedException
import jakarta.mail.internet.MimeMessage
import no.nav.emottak.MESSAGES_SENT_COUNTER
import no.nav.emottak.config
import no.nav.emottak.model.MailMetadata
import no.nav.emottak.model.Payload
import no.nav.emottak.model.PayloadMessage
import no.nav.emottak.model.SignalMessage
import no.nav.emottak.session
import no.nav.emottak.util.ScopedEventLoggingService
import no.nav.emottak.util.fakeEventLoggingService
import no.nav.emottak.utils.kafka.model.EventType
import no.nav.emottak.utils.kafka.model.EventType.ERROR_WHILE_SENDING_MESSAGE_VIA_SMTP
import no.nav.emottak.utils.kafka.model.EventType.MESSAGE_SENT_VIA_SMTP
import kotlin.uuid.Uuid

class MailSenderSpec : StringSpec({
    lateinit var mailSender: MailSender
    lateinit var greenMail: GreenMail
    lateinit var meterRegistry: PrometheusMeterRegistry
    val events = mutableListOf<EventType>()
    val errors = mutableListOf<Pair<Exception, Uuid>>()
    val config = config()

    beforeEach {
        val session = session(config.smtp)
        greenMail = GreenMail(SMTP_POP3).apply {
            start()
            setUser(config.smtp.username.value, config.smtp.username.value, config.smtp.password.value)
        }
        events.clear()
        errors.clear()
        meterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
        val eventLoggingService = object : ScopedEventLoggingService by fakeEventLoggingService() {
            override fun registerEvent(eventType: EventType, error: Exception, requestId: Uuid) {
                events.add(eventType)
                errors.add(error to requestId)
            }

            override fun registerEvent(eventType: EventType, mimeMessage: MimeMessage, requestId: Uuid, eventData: String) {
                events.add(eventType)
            }
        }
        mailSender = MailSender(session, eventLoggingService, config.smtp.copy(smtpRedirectAddress = ""), meterRegistry)
    }

    afterEach {
        mailSender.close()
        meterRegistry.close()
        greenMail.purgeEmailFromAllMailboxes()
        greenMail.stop()
    }

    for (recipient in listOf("", "   ", "mailto://")) {
        for (messageType in listOf("signal", "payload")) {
            "missing recipient '$recipient' reports $messageType failure and allows subsequent messages" {
                val messageId = Uuid.random()
                val metadata = MailMetadata(recipient, messageType, "fromNav")
                val signalMessage = SignalMessage(messageId, getEnvelope().toByteArray())
                val payloadMessage = PayloadMessage(messageId, getEnvelope().toByteArray(), listOf(getPayload()))

                if (messageType == "signal") {
                    mailSender.sendSignalMessage(metadata, signalMessage)
                } else {
                    mailSender.sendPayloadMessage(metadata, payloadMessage)
                }

                greenMail.receivedMessages shouldHaveSize 0
                events shouldBe listOf(ERROR_WHILE_SENDING_MESSAGE_VIA_SMTP)
                errors shouldHaveSize 1
                errors.single().first.shouldBeInstanceOf<SendFailedException>()
                errors.single().first.message shouldBe "Message has no recipients"
                errors.single().second shouldBe messageId
                meterRegistry.find(MESSAGES_SENT_COUNTER).counters() shouldHaveSize 0

                val validMetadata = metadata.copy(recipientAddress = "toSomeone")
                if (messageType == "signal") {
                    mailSender.sendSignalMessage(validMetadata, signalMessage)
                } else {
                    mailSender.sendPayloadMessage(validMetadata, payloadMessage)
                }

                greenMail.receivedMessages shouldHaveSize 1
                events shouldBe listOf(ERROR_WHILE_SENDING_MESSAGE_VIA_SMTP, MESSAGE_SENT_VIA_SMTP)
                meterRegistry.find(MESSAGES_SENT_COUNTER).counter()!!.count() shouldBe 1.0
            }
        }
    }

    "missing recipient uses configured redirect for signal and payload messages" {
        val smtp = config.smtp.copy(smtpRedirectAddress = "redirectRecipient")
        MailSender(session(smtp), fakeEventLoggingService(), smtp, meterRegistry).use { redirectSender ->
            val metadata = MailMetadata("", "redirected", "fromNav")
            redirectSender.sendSignalMessage(metadata, SignalMessage(Uuid.random(), getEnvelope().toByteArray()))
            redirectSender.sendPayloadMessage(metadata, PayloadMessage(Uuid.random(), getEnvelope().toByteArray(), listOf(getPayload())))

            greenMail.receivedMessages shouldHaveSize 2
            greenMail.receivedMessages.forEach {
                it.getHeader("To") shouldBe arrayOf(smtp.smtpRedirectAddress)
            }
        }
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
