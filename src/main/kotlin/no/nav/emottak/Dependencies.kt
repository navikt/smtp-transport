package no.nav.emottak

import app.cash.sqldelight.driver.jdbc.asJdbcDriver
import arrow.core.memoize
import arrow.fx.coroutines.ExitCase
import arrow.fx.coroutines.ResourceScope
import arrow.fx.coroutines.autoCloseable
import arrow.fx.coroutines.await.awaitAll
import com.bettercloud.vault.api.database.DatabaseCredential
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.nomisRev.kafka.publisher.KafkaPublisher
import io.github.nomisRev.kafka.publisher.PublisherSettings
import io.github.nomisRev.kafka.receiver.KafkaReceiver
import io.github.nomisRev.kafka.receiver.ReceiverSettings
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.HttpResponse
import io.ktor.http.parameters
import io.ktor.http.path
import io.ktor.serialization.kotlinx.json.json
import io.micrometer.prometheus.PrometheusConfig.DEFAULT
import io.micrometer.prometheus.PrometheusMeterRegistry
import jakarta.mail.Authenticator
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Store
import kotlinx.serialization.json.Json
import no.nav.emottak.configuration.AzureAuth
import no.nav.emottak.configuration.Config
import no.nav.emottak.configuration.Database
import no.nav.emottak.configuration.Kafka
import no.nav.emottak.configuration.Smtp
import no.nav.emottak.configuration.toProperties
import no.nav.emottak.model.TokenInfo
import no.nav.emottak.queries.PayloadDatabase
import no.nav.vault.jdbc.hikaricp.HikariCPVaultUtil.createHikariDataSourceWithVaultIntegration
import no.nav.vault.jdbc.hikaricp.VaultUtil
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.flywaydb.core.Flyway
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Proxy.Type.HTTP
import java.net.URI
import javax.sql.DataSource

data class Dependencies(
    val store: Store,
    val session: Session,
    val kafkaPublisher: KafkaPublisher<String, ByteArray>,
    val kafkaReceiver: KafkaReceiver<String, ByteArray>,
    val payloadDatabase: PayloadDatabase,
    val httpClient: HttpClient,
    val migrationService: Flyway,
    val meterRegistry: PrometheusMeterRegistry
)

private const val MIGRATIONS_PATH = "filesystem:/app/migrations"

internal suspend fun ResourceScope.metricsRegistry(): PrometheusMeterRegistry =
    install({ PrometheusMeterRegistry(DEFAULT) }) { p, _: ExitCase ->
        p.close().also { log.info("Closed prometheus registry") }
    }

internal suspend fun ResourceScope.store(smtp: Smtp): Store =
    install({ session(smtp).getStore(smtp.storeProtocol.value).also { it.connect() } }) { s, _: ExitCase ->
        s.close().also { log.info("Closed session store") }
    }

internal suspend fun ResourceScope.jdbcDriver(dataSource: DataSource) =
    install({ dataSource.asJdbcDriver() }) { j, _: ExitCase -> j.close().also { log.info("Closed jdbc driver") } }

internal suspend fun ResourceScope.hikari(database: Database): HikariDataSource =
    autoCloseable {
        createHikariDataSourceWithVaultIntegration(
            HikariConfig(database.toProperties()),
            database.mountPath.value,
            database.userRole.value
        )
    }

internal suspend fun ResourceScope.kafkaPublisher(kafka: Kafka): KafkaPublisher<String, ByteArray> =
    install({ KafkaPublisher(kafkaPublisherSettings(kafka)) }) { p, _: ExitCase ->
        p.close().also { log.info("Closed kafka publisher") }
    }

internal fun kafkaReceiver(kafka: Kafka): KafkaReceiver<String, ByteArray> =
    KafkaReceiver(kafkaReceiverSettings(kafka))

internal suspend fun ResourceScope.httpTokenClientEngine(): HttpClientEngine =
    install({ CIO.create() }) { c, _: ExitCase -> c.close().also { log.info("Closed http token client engine") } }

internal suspend fun ResourceScope.httpClientEngine(): HttpClientEngine =
    install({ CIO.create() }) { c, _: ExitCase -> c.close().also { log.info("Closed http client engine") } }

internal fun tokenHttpClient(clientEngine: HttpClientEngine, config: AzureAuth): HttpClient =
    HttpClient(clientEngine) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        engine {
            val uri = URI(config.azureHttpProxy.value)
            proxy = Proxy(HTTP, InetSocketAddress(uri.host, uri.port))
        }
    }

internal fun httpClient(
    clientEngine: HttpClientEngine,
    tokenClientEngine: HttpClientEngine,
    config: Config
): HttpClient =
    HttpClient(clientEngine) {
        val tokenClient = tokenHttpClient(tokenClientEngine, config.azureAuth)
        install(ContentNegotiation) { json() }
        install(Auth) {
            bearer {
                refreshTokens {
                    val tokenInfo: TokenInfo = submitForm(tokenClient, config.azureAuth).body()
                    BearerTokens(tokenInfo.accessToken, null)
                }
                sendWithoutRequest { true }
            }
        }
        defaultRequest {
            url {
                host = config.ebmsProvider.baseUrl
                path(config.ebmsProvider.apiUrl)
            }
        }
    }

private suspend fun submitForm(tokenClient: HttpClient, config: AzureAuth): HttpResponse =
    tokenClient.submitForm(
        url = config.azureTokenEndpoint.value,
        formParameters = parameters {
            append("client_id", config.azureAppClientId.value)
            append("client_secret", config.azureAppClientSecret.value)
            append("grant_type", "client_credentials")
            append("scope", config.ebmsProviderScope.value)
        }
    )

private fun migrationService(database: Database): Flyway {
    val adminCredentials = getVaultAdminCredentials(database)
    val user = adminCredentials.username
    val password = adminCredentials.password
    return Flyway
        .configure()
        .dataSource(database.url.value, user, password)
        .initSql("SET ROLE \"${database.adminRole.value}\"")
        .locations(MIGRATIONS_PATH)
        .loggers("slf4j")
        .load()
}

private fun getVaultAdminCredentials(database: Database): DatabaseCredential =
    VaultUtil
        .getInstance()
        .client.database(database.mountPath.value)
        .creds(database.adminRole.value)
        .credential

private fun kafkaPublisherSettings(kafka: Kafka): PublisherSettings<String, ByteArray> =
    PublisherSettings(
        bootstrapServers = kafka.bootstrapServers,
        keySerializer = StringSerializer(),
        valueSerializer = ByteArraySerializer(),
        properties = kafka.toProperties()
    )

private fun kafkaReceiverSettings(kafka: Kafka): ReceiverSettings<String, ByteArray> =
    ReceiverSettings(
        bootstrapServers = kafka.bootstrapServers,
        keyDeserializer = StringDeserializer(),
        valueDeserializer = ByteArrayDeserializer(),
        groupId = kafka.groupId,
        properties = kafka.toProperties()
    )

internal val session: (Smtp) -> Session = { smtp: Smtp ->
    Session.getInstance(
        smtp.toProperties(),
        object : Authenticator() {
            override fun getPasswordAuthentication() = PasswordAuthentication(
                smtp.username.value,
                smtp.password.value
            )
        }
    )
}
    .memoize()

suspend fun ResourceScope.initDependencies(): Dependencies = awaitAll {
    val config = config()

    val store = async { store(config.smtp) }
    val kafkaPublisher = async { kafkaPublisher(config.kafka) }
    val kafkaReceiver = async { kafkaReceiver(config.kafka) }
    val jdbcDriver = async { (jdbcDriver(hikari(config.database))) }
    val migrationService = async { migrationService(config.database) }
    val metricsRegistry = async { metricsRegistry() }
    val httpClient = async { httpClient(httpClientEngine(), httpTokenClientEngine(), config) }

    Dependencies(
        store.await(),
        session(config.smtp),
        kafkaPublisher.await(),
        kafkaReceiver.await(),
        PayloadDatabase(jdbcDriver.await()),
        httpClient.await(),
        migrationService.await(),
        metricsRegistry.await()
    )
}
