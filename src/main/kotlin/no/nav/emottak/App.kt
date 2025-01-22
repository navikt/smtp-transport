package no.nav.emottak

import arrow.continuations.SuspendApp
import arrow.continuations.ktor.server
import arrow.core.raise.result
import arrow.fx.coroutines.resourceScope
import arrow.resilience.Schedule
import io.ktor.server.application.Application
import io.ktor.server.netty.Netty
import io.ktor.utils.io.CancellationException
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlinx.coroutines.awaitCancellation
import no.nav.emottak.configuration.Job
import no.nav.emottak.plugin.configureAuthentication
import no.nav.emottak.plugin.configureContentNegotiation
import no.nav.emottak.plugin.configureMetrics
import no.nav.emottak.plugin.configureRoutes
import no.nav.emottak.processor.MailProcessor
import no.nav.emottak.publisher.MailPublisher
import no.nav.emottak.repository.PayloadRepository
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.seconds

internal val log = LoggerFactory.getLogger("no.nav.emottak.smtp")

fun main() = SuspendApp {
    log.info("smtp-transport starting")
    result {
        resourceScope {
            val deps = initDependencies(config())
            deps.migrationService.migrate()
            val payloadRepository = PayloadRepository(deps.payloadDatabase)

            server(
                Netty,
                port = 8080,
                preWait = 5.seconds,
                module = smtpTransportModule(
                    deps.meterRegistry,
                    payloadRepository
                )
            )

            val mailPublisher = MailPublisher(config().kafka, deps.kafkaPublisher)
            val mailProcessor = MailProcessor(config(), deps, mailPublisher, payloadRepository)

            scheduleProcessMessages(config().job, mailProcessor)

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

fun smtpTransportModule(
    meterRegistry: PrometheusMeterRegistry,
    payloadRepository: PayloadRepository
): Application.() -> Unit {
    return {
        configureMetrics(meterRegistry)
        configureContentNegotiation()
        configureAuthentication()
        configureRoutes(meterRegistry, payloadRepository)
    }
}

private suspend fun scheduleProcessMessages(job: Job, mailProcessor: MailProcessor) =
    Schedule
        .spaced<Unit>(job.fixedInterval)
        .repeat(mailProcessor::processMessages)

private fun logError(t: Throwable) = log.error("Shutdown smtp-transport due to: ${t.stackTraceToString()}")
