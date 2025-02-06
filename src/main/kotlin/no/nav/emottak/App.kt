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
import no.nav.emottak.plugin.configureAuthentication
import no.nav.emottak.plugin.configureCallLogging
import no.nav.emottak.plugin.configureContentNegotiation
import no.nav.emottak.plugin.configureMetrics
import no.nav.emottak.plugin.configureRoutes
import no.nav.emottak.processor.MailProcessor
import no.nav.emottak.processor.MessageProcessor
import no.nav.emottak.publisher.MailPublisher
import no.nav.emottak.receiver.SignalReceiver
import no.nav.emottak.repository.PayloadRepository
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.seconds

internal val log = LoggerFactory.getLogger("no.nav.emottak.smtp")

fun main() = SuspendApp {
    result {
        resourceScope {
            val deps = initDependencies()
            deps.migrationService.migrate()

            val mailPublisher = MailPublisher(deps.kafkaPublisher)
            val signalReceiver = SignalReceiver(deps.kafkaReceiver)
            val payloadRepository = PayloadRepository(deps.payloadDatabase)
            val mailProcessor = MailProcessor(deps.store, mailPublisher, payloadRepository)
            val messageProcessor = MessageProcessor(signalReceiver)

            server(
                Netty,
                port = 8080,
                preWait = 5.seconds,
                module = smtpTransportModule(deps.meterRegistry, payloadRepository)
            )

            scheduleProcessMessages(mailProcessor)

            messageProcessor.processSignalMessages()

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

internal fun smtpTransportModule(
    meterRegistry: PrometheusMeterRegistry,
    payloadRepository: PayloadRepository
): Application.() -> Unit {
    return {
        configureMetrics(meterRegistry)
        configureContentNegotiation()
        configureAuthentication()
        configureRoutes(meterRegistry, payloadRepository)
        configureCallLogging()
    }
}

private suspend fun scheduleProcessMessages(mailProcessor: MailProcessor) =
    Schedule
        .spaced<Unit>(config().job.fixedInterval)
        .repeat(mailProcessor::processMessages)

private fun logError(t: Throwable) = log.error("Shutdown smtp-transport due to: ${t.stackTraceToString()}")
