package no.nav.emottak.configuration

import com.sksamuel.hoplite.Masked
import org.apache.kafka.clients.CommonClientConfigs.SECURITY_PROTOCOL_CONFIG
import org.apache.kafka.common.config.SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG
import org.apache.kafka.common.config.SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG
import org.apache.kafka.common.config.SslConfigs.SSL_KEYSTORE_TYPE_CONFIG
import org.apache.kafka.common.config.SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG
import org.apache.kafka.common.config.SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG
import org.apache.kafka.common.config.SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG
import java.util.Properties
import kotlin.time.Duration

data class Config(
    val job: Job,
    val mail: Mail,
    val ebms: Ebms,
    val kafka: Kafka,
    val smtp: Smtp,
    val database: Database
)

fun Config.withKafka(update: Kafka.() -> Kafka) = copy(kafka = kafka.update())

data class Job(
    val initialDelay: Duration,
    val fixedInterval: Duration
)

data class Mail(val inboxLimit: Int)

data class Ebms(val providerUrl: String)

@JvmInline
value class SecurityProtocol(val value: String)

@JvmInline
value class KeystoreType(val value: String)

@JvmInline
value class KeystoreLocation(val value: String)

@JvmInline
value class TruststoreType(val value: String)

@JvmInline
value class TruststoreLocation(val value: String)

data class Kafka(
    val bootstrapServers: String,
    val securityProtocol: SecurityProtocol,
    val keystoreType: KeystoreType,
    val keystoreLocation: KeystoreLocation,
    val keystorePassword: Masked,
    val truststoreType: TruststoreType,
    val truststoreLocation: TruststoreLocation,
    val truststorePassword: Masked,
    val payloadTopic: String,
    val signalTopic: String,
    val groupId: String
)

fun Kafka.toProperties() = Properties()
    .apply {
        put(SECURITY_PROTOCOL_CONFIG, securityProtocol.value)
        put(SSL_KEYSTORE_TYPE_CONFIG, keystoreType.value)
        put(SSL_KEYSTORE_LOCATION_CONFIG, keystoreLocation.value)
        put(SSL_KEYSTORE_PASSWORD_CONFIG, keystorePassword.value)
        put(SSL_TRUSTSTORE_TYPE_CONFIG, truststoreType.value)
        put(SSL_TRUSTSTORE_LOCATION_CONFIG, truststoreLocation.value)
        put(SSL_TRUSTSTORE_PASSWORD_CONFIG, truststorePassword.value)
    }

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
    val smtpPort: Port,
    val smtpHost: Host,
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

private const val MAIL_SMTP_HOST = "mail.smtp.host"
private const val MAIL_SMTP_PORT = "mail.smtp.port"
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
        put(MAIL_POP_3_SOCKET_FACTORY_FALLBACK, pop3FactoryFallback)
        put(MAIL_POP_3_SOCKET_FACTORY_PORT, pop3FactoryPort.value)
        put(MAIL_SMTP_PORT, smtpPort.value)
        put(MAIL_SMTP_HOST, smtpHost.value)
        put(MAIL_POP_3_PORT, pop3Port.value)
        put(MAIL_POP_3_HOST, pop3Host.value)
        put(MAIL_IMAP_SOCKET_FACTORY_FALLBACK, imapFactoryFallback)
        put(MAIL_IMAP_SOCKET_FACTORY_PORT, imapFactoryPort.value)
        put(MAIL_IMAP_PORT, imapPort.value)
        put(MAIL_IMAP_HOST, imapHost.value)
    }

@JvmInline
value class Url(val value: String)

@JvmInline
value class MinimumIdleConnections(val value: Int)

@JvmInline
value class MaxLifeTimeConnections(val value: Int)

@JvmInline
value class MaxConnectionPoolSize(val value: Int)

@JvmInline
value class ConnectionTimeout(val value: Int)

@JvmInline
value class IdleConnectionTimeout(val value: Int)

@JvmInline
value class MountPath(val value: String)

@JvmInline
value class Role(val value: String)

data class Database(
    val url: Url,
    val minimumIdleConnections: MinimumIdleConnections,
    val maxLifetimeConnections: MaxLifeTimeConnections,
    val maxConnectionPoolSize: MaxConnectionPoolSize,
    val connectionTimeout: ConnectionTimeout,
    val idleConnectionTimeout: IdleConnectionTimeout,
    val mountPath: MountPath,
    val userRole: Role,
    val adminRole: Role
)

fun Database.toProperties() = Properties()
    .apply {
        put("jdbcUrl", url.value)
        put("minimumIdle", minimumIdleConnections.value)
        put("maxLifetime", maxLifetimeConnections.value)
        put("maximumPoolSize", maxConnectionPoolSize.value)
        put("connectionTimeout", connectionTimeout.value)
        put("idleTimeout", idleConnectionTimeout.value)
    }
