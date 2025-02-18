package no.nav.emottak.plugin

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.calllogging.processingTimeMillis
import io.ktor.server.plugins.origin
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import org.slf4j.event.Level

fun Application.configureCallLogging() {
    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.path().startsWith("/api") }
        format { call ->
            val status = call.response.status()
            val httpMethod = call.request.httpMethod.value
            val userAgent = call.request.headers["User-Agent"] ?: "Unknown"
            val path = call.request.path()
            val queryParams = call.request.queryParameters
                .entries()
                .joinToString(", ") { "${it.key}=${it.value}" }
                .ifEmpty { "None" }

            val duration = call.processingTimeMillis()
            val remoteHost = call.request.origin.remoteHost

            val coloredStatus = when {
                status == null -> "\u001B[33mUNKNOWN\u001B[0m"
                status.value < 300 -> "\u001B[32m${status.value}\u001B[0m"
                status.value < 400 -> "\u001B[33m${status.value}\u001B[0m"
                else -> "\u001B[31m${status.value}\u001B[0m"
            }
            val coloredMethod = "\u001B[36m$httpMethod\u001B[0m"

            """
            |
            |------------------------ Request Details ------------------------
            |Status: $coloredStatus
            |Method: $coloredMethod
            |Path: $path
            |Query Params: $queryParams
            |Remote Host: $remoteHost
            |User Agent: $userAgent
            |Duration: ${duration}ms
            |------------------------------------------------------------------
            |
            """.trimMargin()
        }
    }
}
