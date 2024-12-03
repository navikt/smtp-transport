package no.nav.emottak.smtp

import jakarta.mail.Message.RecipientType.TO
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage

class MailSender(private val session: Session) {

    fun sendMessage() {
        val msg = MimeMessage(session)
        msg.setFrom(InternetAddress("send@test.test"))
        msg.addRecipient(TO, InternetAddress("test@test.test"))
        msg.subject = "Email sent to GreenMail via plain JavaMail"
        msg.setText("Fetch me via POP3")

        Transport.send(msg)
    }
}
