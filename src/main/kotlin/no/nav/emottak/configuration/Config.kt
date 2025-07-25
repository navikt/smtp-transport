package no.nav.emottak.configuration

import com.sksamuel.hoplite.Masked
import no.nav.emottak.utils.config.EventLogging
import no.nav.emottak.utils.config.Kafka
import java.util.Properties
import kotlin.time.Duration

data class Config(
    val job: Job,
    val mail: Mail,
    val kafka: Kafka,
    val eventLogging: EventLogging,
    val kafkaTopics: KafkaTopics,
    val smtp: Smtp,
    val database: Database,
    val azureAuth: AzureAuth,
    val server: Server,
    val httpClient: HttpClient,
    val httpTokenClient: HttpClient,
    val ebmsAsync: EbmsAsync
)

fun Config.withKafka(update: Kafka.() -> Kafka) = copy(kafka = kafka.update())

data class Job(val fixedInterval: Duration)

data class Mail(val inboxLimit: Int)

data class Server(val port: Port, val preWait: Duration)

data class EbmsAsync(val baseUrl: String, val apiUrl: String)

@JvmInline
value class Timeout(val value: Long)

data class HttpClient(val connectionTimeout: Timeout)

data class KafkaTopics(
    val payloadInTopic: String,
    val signalInTopic: String,
    val payloadOutTopic: String,
    val signalOutTopic: String
)

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
    val storeProtocol: Protocol,
    val pop3FactoryPort: Port,
    val pop3FactoryFallback: Boolean,
    val smtpFromAddress: String,
    val smtpRedirectAddress: String
)

private const val MAIL_SMTP_HOST = "mail.smtp.host"
private const val MAIL_SMTP_PORT = "mail.smtp.port"
private const val MAIL_POP_3_HOST = "mail.pop3.host"
private const val MAIL_POP_3_PORT = "mail.pop3.port"
private const val MAIL_POP_3_SOCKET_FACTORY_FALLBACK = "mail.pop3.socketFactory.fallback"
private const val MAIL_POP_3_SOCKET_FACTORY_PORT = "mail.pop3.socketFactory.port"

fun Smtp.toProperties() = Properties()
    .apply {
        put(MAIL_POP_3_SOCKET_FACTORY_FALLBACK, pop3FactoryFallback)
        put(MAIL_POP_3_SOCKET_FACTORY_PORT, pop3FactoryPort.value)
        put(MAIL_SMTP_PORT, smtpPort.value)
        put(MAIL_SMTP_HOST, smtpHost.value)
        put(MAIL_POP_3_PORT, pop3Port.value)
        put(MAIL_POP_3_HOST, pop3Host.value)
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
value class MigrationsPath(val value: String)

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
    val adminRole: Role,
    val migrationsPath: MigrationsPath
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

@JvmInline
value class ClusterName(val value: String)

@JvmInline
value class AzureAd(val value: String)

@JvmInline
value class AppScope(val value: String)

@JvmInline
value class AzureHttpProxy(val value: String)

@JvmInline
value class AzureAdAuth(val value: String)

@JvmInline
value class AzureGrantType(val value: String)

@JvmInline
value class AzureWellKnownUrl(val value: String)

@JvmInline
value class AzureTokenEndpoint(val value: String)

@JvmInline
value class AzureApplicationId(val value: String)

@JvmInline
value class AzureApplicationSecret(val value: String)

data class AzureAuth(
    val clusterName: ClusterName,
    val port: Port,
    val azureAd: AzureAd,
    val smtpTransportScope: AppScope,
    val ebmsAsyncScope: AppScope,
    val azureHttpProxy: AzureHttpProxy,
    val azureAdAuth: AzureAdAuth,
    val azureGrantType: AzureGrantType,
    val azureWellKnownUrl: AzureWellKnownUrl,
    val azureTokenEndpoint: AzureTokenEndpoint,
    val azureAppClientId: AzureApplicationId,
    val azureAppClientSecret: AzureApplicationSecret
)
