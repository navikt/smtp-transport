package no.nav.emottak

import app.cash.sqldelight.driver.jdbc.asJdbcDriver
import arrow.core.memoize
import arrow.fx.coroutines.ExitCase
import arrow.fx.coroutines.ResourceScope
import arrow.fx.coroutines.autoCloseable
import arrow.fx.coroutines.parZip
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.nomisRev.kafka.publisher.KafkaPublisher
import io.github.nomisRev.kafka.publisher.PublisherSettings
import io.micrometer.prometheus.PrometheusConfig.DEFAULT
import io.micrometer.prometheus.PrometheusMeterRegistry
import jakarta.mail.Authenticator
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Store
import no.nav.emottak.configuration.Config
import no.nav.emottak.configuration.Kafka
import no.nav.emottak.configuration.Smtp
import no.nav.emottak.configuration.toProperties
import no.nav.emottak.smtp.PayloadDatabase
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.StringSerializer
import java.util.Properties
import javax.sql.DataSource

data class Dependencies(
    val store: Store,
    val session: Session,
    val kafkaPublisher: KafkaPublisher<String, ByteArray>,
    val payloadDatabase: PayloadDatabase?,
    val meterRegistry: PrometheusMeterRegistry
)

internal suspend fun ResourceScope.metricsRegistry(): PrometheusMeterRegistry =
    install({ PrometheusMeterRegistry(DEFAULT) }) { p, _: ExitCase ->
        p.close().also { log.info("Closed prometheus registry") }
    }

internal suspend fun ResourceScope.store(smtp: Smtp): Store =
    install({ session(smtp).getStore(smtp.storeProtocol.value).also { it.connect() } }) { s, _: ExitCase ->
        s.close().also { log.info("Closed session store") }
    }

internal suspend fun ResourceScope.jdbcDriver(dataSource: DataSource) =
    install({ dataSource.asJdbcDriver() }) { d, _: ExitCase -> d.close().also { log.info("Closed datasource") } }

internal suspend fun ResourceScope.hikari(properties: Properties): HikariDataSource =
    autoCloseable { HikariDataSource(HikariConfig(properties)) }

internal suspend fun ResourceScope.kafkaPublisher(kafka: Kafka): KafkaPublisher<String, ByteArray> =
    install({ KafkaPublisher(kafkaPublisherSettings(kafka)) }) { p, _: ExitCase ->
        p.close().also { log.info("Closed kafka publisher") }
    }

private fun kafkaPublisherSettings(kafka: Kafka): PublisherSettings<String, ByteArray> =
    PublisherSettings(
        bootstrapServers = kafka.bootstrapServers,
        keySerializer = StringSerializer(),
        valueSerializer = ByteArraySerializer(),
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

suspend fun ResourceScope.initDependencies(config: Config) =
    parZip(
        { store(config.smtp) },
        { kafkaPublisher(config.kafka) },
        { null },
        { metricsRegistry() }
    ) { store, kafkaPublisher, jdbcDriver, metricsRegistry ->
        Dependencies(
            store,
            session(config.smtp),
            kafkaPublisher,
            null,
            metricsRegistry
        )
    }
