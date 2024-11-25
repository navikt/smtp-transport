package no.nav.emottak.configuration

import com.sksamuel.hoplite.Masked
import java.util.Properties

data class Config(
    val mail: Mail,
    val ebms: Ebms,
    val smtp: Smtp
)

data class Mail(val inboxLimit: Int)

data class Ebms(val providerUrl: String)

@JvmInline
value class Username(val value: String)

@JvmInline
value class Host(val value: String)

@JvmInline
value class Port(val value: Int)

@JvmInline
value class Protocol(val value: String)

data class Smtp(
    val username: Username,
    val password: Masked,
    val pop3Port: Port,
    val pop3Host: Host,
    val imapPort: Port,
    val imapHost: Host,
    val storeProtocol: Protocol,
    val pop3FactoryPort: Port,
    val imapFactoryPort: Port,
    val pop3FactoryFallback: Boolean,
    val imapFactoryFallback: Boolean
)

private const val MAIL_POP_3_HOST = "mail.pop3.host"
private const val MAIL_POP_3_PORT = "mail.pop3.port"
private const val MAIL_IMAP_HOST = "mail.imap.host"
private const val MAIL_IMAP_PORT = "mail.imap.port"
private const val MAIL_POP_3_SOCKET_FACTORY_FALLBACK = "mail.pop3.socketFactory.fallback"
private const val MAIL_POP_3_SOCKET_FACTORY_PORT = "mail.pop3.socketFactory.port"
private const val MAIL_IMAP_SOCKET_FACTORY_FALLBACK = "mail.imap.socketFactory.fallback"
private const val MAIL_IMAP_SOCKET_FACTORY_PORT = "mail.imap.socketFactory.port"

fun Smtp.toProperties() = Properties()
    .apply {
        setProperty(MAIL_POP_3_SOCKET_FACTORY_FALLBACK, "$pop3FactoryFallback")
        setProperty(MAIL_POP_3_SOCKET_FACTORY_PORT, "${pop3FactoryPort.value}")
        setProperty(MAIL_POP_3_PORT, "${pop3Port.value}")
        setProperty(MAIL_POP_3_HOST, pop3Host.value)
        setProperty(MAIL_IMAP_SOCKET_FACTORY_FALLBACK, "$imapFactoryFallback")
        setProperty(MAIL_IMAP_SOCKET_FACTORY_PORT, "${imapFactoryPort.value}")
        setProperty(MAIL_IMAP_PORT, "${imapPort.value}")
        setProperty(MAIL_IMAP_HOST, imapHost.value)
    }
