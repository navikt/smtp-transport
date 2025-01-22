package no.nav.emottak

import app.cash.sqldelight.driver.jdbc.JdbcDriver
import app.cash.sqldelight.driver.jdbc.asJdbcDriver
import arrow.core.memoize
import com.zaxxer.hikari.HikariConfig
import io.kotest.core.extensions.install
import io.kotest.core.spec.Spec
import io.kotest.extensions.testcontainers.JdbcDatabaseContainerExtension
import no.nav.emottak.queries.PayloadDatabase
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.output.MigrateResult
import org.testcontainers.containers.PostgreSQLContainer
import javax.sql.DataSource

private const val MIGRATIONS_PATH = "filesystem:./build/generated/migrations"
private const val SMTP_TRANSPORT_DB_NAME = "emottak-smtp-transport-db"

fun Spec.payloadDatabase() = PayloadDatabase(jdbcDriver())
fun DataSource.asPayloadDatabase() = PayloadDatabase(asJdbcDriver())

fun runMigrations(): MigrateResult {
    val container = container()
    return Flyway
        .configure()
        .dataSource(container.jdbcUrl, container.username, container.password)
        .locations(MIGRATIONS_PATH)
        .loggers("slf4j")
        .load()
        .migrate()
}

private fun Spec.jdbcDriver(): JdbcDriver {
    val containerExtension = JdbcDatabaseContainerExtension(container())
    return install(containerExtension).asJdbcDriver()
}

private val container: () -> PostgreSQLContainer<Nothing> = {
    PostgreSQLContainer<Nothing>("postgres:14.8")
        .apply {
            startupAttempts = 1
            withDatabaseName("payload-db")
            withUsername("postgres")
            withPassword("postgres")
        }
}
    .memoize()

// Inspirert av: ebms-provider/src/test/kotlin/no/nav/emottak/ebms/DBTest.kt
fun smtpTransportPostgres(initScriptPath: String): PostgreSQLContainer<Nothing> =
    PostgreSQLContainer<Nothing>("postgres:14.8").apply {
        withUsername("$SMTP_TRANSPORT_DB_NAME-admin")
        withReuse(true)
        withLabel("app-navn", "smtp-transport")
        println("Running init SQL script")
        withInitScript(initScriptPath)
        start()
        println(
            "Databasen er startet opp, portnummer: $firstMappedPort, jdbcUrl: jdbc:postgresql://localhost:$firstMappedPort/test, credentials: test og test"
        )
    }

fun PostgreSQLContainer<Nothing>.testConfiguration(): HikariConfig {
    return HikariConfig().apply {
        jdbcUrl = this@testConfiguration.jdbcUrl
        username = this@testConfiguration.username
        password = this@testConfiguration.password
        maximumPoolSize = 5
        minimumIdle = 1
        idleTimeout = 500001
        connectionTimeout = 10000
        maxLifetime = 600001
        initializationFailTimeout = 5000
    }
}
