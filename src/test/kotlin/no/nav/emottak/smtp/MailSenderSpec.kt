package no.nav.emottak.smtp

import arrow.fx.coroutines.resourceScope
import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.ServerSetupTest.SMTP_POP3
import io.kotest.core.spec.style.StringSpec
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

    beforeSpec {
        val session = session(config.smtp)
        greenMail = GreenMail(SMTP_POP3).apply {
            start()
            setUser(config.smtp.username.value, config.smtp.username.value, config.smtp.password.value)
        }
        mailSender = MailSender(session, fakeEventLoggingService())
    }

    "send signal message" {
        resourceScope {
            val metadata = MailMetadata("to", "signal", "fromNav")
            val message = SignalMessage(Uuid.random(), getEnvelope().toByteArray())

            mailSender.sendSignalMessage(metadata, message)
        }
    }

    "send payload message" {
        resourceScope {
            val metadata = MailMetadata("to", "payload", "fromNav")
            val message = PayloadMessage(Uuid.random(), getEnvelope().toByteArray(), listOf(getPayload()))

            mailSender.sendPayloadMessage(metadata, message)
        }
    }

    afterSpec { greenMail.stop() }
})

private fun getEnvelope() =
    """<?xml version="1.0" encoding="UTF-8"?><soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope"><soap:Body><message>Hello</message></soap:Body></soap:Envelope>"""

private fun getPayload() = Payload(Uuid.random(), "content", "text/plain", "content".toByteArray())
