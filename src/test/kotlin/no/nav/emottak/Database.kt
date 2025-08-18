package no.nav.emottak

import app.cash.sqldelight.driver.jdbc.JdbcDriver
import app.cash.sqldelight.driver.jdbc.asJdbcDriver
import arrow.core.memoize
import io.kotest.core.extensions.install
import io.kotest.core.spec.Spec
import io.kotest.extensions.testcontainers.JdbcDatabaseContainerExtension
import no.nav.emottak.migrations.Payload
import no.nav.emottak.queries.PayloadDatabase
import no.nav.emottak.utils.sql.sqldelight.UuidAdapter
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.output.MigrateResult
import org.testcontainers.containers.PostgreSQLContainer

private const val MIGRATIONS_PATH = "filesystem:./build/generated/migrations"
private const val TEST_DATA_PATH = "filesystem:./src/test/resources/testDb"

private var payloadDatabase: PayloadDatabase? = null
fun Spec.payloadDatabase() = payloadDatabase
    ?: PayloadDatabase(jdbcDriver(), Payload.Adapter(UuidAdapter)).also { payloadDatabase = it }

fun runMigrations(): MigrateResult {
    val container = container()
    return Flyway
        .configure()
        .dataSource(container.jdbcUrl, container.username, container.password)
        .locations(MIGRATIONS_PATH, TEST_DATA_PATH)
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
