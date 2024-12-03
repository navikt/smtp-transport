package no.nav.emottak.smtp

import arrow.fx.coroutines.resourceScope
import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.ServerSetupTest.SMTP_POP3
import io.kotest.core.spec.style.StringSpec
import no.nav.emottak.config
import no.nav.emottak.initDependencies

class MailSenderTest : StringSpec({
    val config = config()

    "test send mail" {
        resourceScope {
            val greenMail = GreenMail(SMTP_POP3)
            greenMail.start()

            val smtp = config.smtp
            greenMail.setUser(smtp.username.value, smtp.username.value, smtp.password.value)

            val deps = initDependencies(config)

            val mailSender = MailSender(deps.session)
            mailSender.sendMessage()

            val service = MailService(config, deps.store, deps.httpClient)
            service.processMessages()

            greenMail.stop()
        }
    }
})
