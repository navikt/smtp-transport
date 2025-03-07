package no.nav.emottak.smtp

import io.kotest.core.spec.style.StringSpec
import no.nav.emottak.config

class MailSenderSpec : StringSpec({
    val config = config()

    // "test send mail" {
    //     resourceScope {
    //         val session = session(config.smtp)
    //
    //         val greenMail = GreenMail(SMTP_POP3)
    //         greenMail.start()
    //
    //         val smtp = config.smtp
    //         greenMail.setUser(smtp.username.value, smtp.username.value, smtp.password.value)
    //
    //         val mailSender = MailSender(session)
    //         mailSender.sendMessage()
    //
    //         greenMail.stop()
    //     }
    // }
})
