package no.nav.emottak.plugin

import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.calllogging.processingTimeMillis
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import org.slf4j.LoggerFactory
import org.slf4j.event.Level

fun Application.configureCallLogging() {
    val logger = LoggerFactory.getLogger("CallLogging")

    install(CallLogging) {
        filter { call -> call.request.path().startsWith("/api") }
        format { call ->
            val status = call.response.status() ?: NotFound
            val logLevel = when (status.value) {
                in 500..599 -> Level.ERROR
                404 -> Level.WARN
                else -> Level.INFO
            }
            val httpMethod = call.request.httpMethod.value
            val userAgent = call.request.headers["User-Agent"] ?: "Unknown"
            val path = call.request.path()
            val duration = call.processingTimeMillis()

            val message = """
                Status: $status
                Method: $httpMethod
                Path: $path
                User-Agent: $userAgent
                Duration: ${duration}ms
            """.trimIndent()

            when (logLevel) {
                Level.ERROR -> logger.error(message)
                Level.WARN -> logger.warn(message)
                Level.INFO -> logger.info(message)
                else -> logger.debug(message)
            }

            ""
        }
    }
}
