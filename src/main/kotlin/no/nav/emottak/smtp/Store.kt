package no.nav.emottak.smtp

import jakarta.mail.Authenticator
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Store
import java.util.Properties

private val smtpUsername_incoming = getEnvVar("SMTP_INCOMING_USERNAME", "test@test.test")
private val smtpPassword = getEnvVar("SMTP_PASSWORD", "changeit")

private val properties = Properties()
    .apply {
        setProperty("mail.pop3.socketFactory.fallback", "false")
        setProperty("mail.pop3.socketFactory.port", getEnvVar("SMTP_POP3_FACTORY_PORT", "3110"))
        setProperty("mail.pop3.port", getEnvVar("SMTP_POP3_PORT", "3110"))
        setProperty("mail.pop3.host", getEnvVar("SMTP_POP3_HOST", "localhost"))
        setProperty("mail.imap.socketFactory.fallback", "false")
        setProperty("mail.imap.socketFactory.port", "143")
        setProperty("mail.imap.port", "143")
        setProperty("mail.imap.host", "d32mxvl002.oera-t.local")
    }

val incomingStore = run { smtpPassword.createStore(smtpUsername_incoming, "pop3") }

private fun String.createStore(username: String, protocol: String = "pop3"): Store {
    val auth = object : Authenticator() {
        override fun getPasswordAuthentication() = PasswordAuthentication(username, this@createStore)
    }
    return Session.getInstance(properties, auth)
        .getStore(protocol)
        .also { it.connect() }
}
