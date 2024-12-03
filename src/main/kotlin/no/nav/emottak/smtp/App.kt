package no.nav.emottak.smtp

import arrow.continuations.SuspendApp
import arrow.continuations.ktor.server
import arrow.core.raise.result
import arrow.fx.coroutines.resourceScope
import arrow.resilience.Schedule
import io.ktor.server.netty.Netty
import io.ktor.utils.io.CancellationException
import kotlinx.coroutines.awaitCancellation
import no.nav.emottak.config
import no.nav.emottak.configuration.Job
import no.nav.emottak.initDependencies
import no.nav.emottak.plugin.configureContentNegotiation
import no.nav.emottak.plugin.configureMetrics
import no.nav.emottak.plugin.configureRoutes
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.seconds

internal val log = LoggerFactory.getLogger("no.nav.emottak.smtp")

fun main() = SuspendApp {
    val config = config()
    result {
        resourceScope {
            val deps = initDependencies(config)
            server(Netty, port = 8080, preWait = 5.seconds) {
                configureMetrics(deps.meterRegistry)
                configureContentNegotiation()
                configureRoutes(deps.meterRegistry)
            }
            val mailService = MailService(config, deps.store, deps.httpClient)
            scheduleWithFixedInterval(config.job, mailService::processMessages)

            awaitCancellation()
        }
    }
        .onFailure { error ->
            when (error) {
                is CancellationException -> {} // expected behaviour - normal shutdown
                else -> logError(error)
            }
        }
}

private suspend fun scheduleWithFixedInterval(job: Job, block: suspend () -> Unit) =
    Schedule.spaced<Unit>(job.fixedInterval).repeat { block() }

private fun logError(t: Throwable) = log.error("Shutdown smtp-transport due to: ${t.stackTraceToString()}")
