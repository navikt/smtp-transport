package no.nav.emottak

import app.cash.sqldelight.driver.jdbc.asJdbcDriver
import io.kotest.core.extensions.install
import io.kotest.core.spec.Spec
import io.kotest.extensions.testcontainers.JdbcDatabaseContainerExtension
import no.nav.emottak.smtp.PayloadDatabase
import org.testcontainers.containers.PostgreSQLContainer

fun Spec.payloadDatabase() = PayloadDatabase(jdbcDriver())

private fun Spec.jdbcDriver() = install(jdbcDatabaseContainer()).asJdbcDriver()

private fun jdbcDatabaseContainer() = JdbcDatabaseContainerExtension(
    PostgreSQLContainer<Nothing>("postgres:14.8")
        .apply {
            withInitScript("db/init.sql")
            startupAttempts = 1
            withDatabaseName("payload-db")
            withUsername("postgres")
            withPassword("postgres")
        }
)
