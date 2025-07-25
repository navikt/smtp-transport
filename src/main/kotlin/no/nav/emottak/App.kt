package no.nav.emottak

import arrow.continuations.SuspendApp
import arrow.continuations.ktor.server
import arrow.core.raise.result
import arrow.fx.coroutines.ResourceScope
import arrow.fx.coroutines.resourceScope
import arrow.resilience.Schedule
import io.ktor.server.application.Application
import io.ktor.server.netty.Netty
import io.ktor.utils.io.CancellationException
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import no.nav.emottak.plugin.configureAuthentication
import no.nav.emottak.plugin.configureCallLogging
import no.nav.emottak.plugin.configureContentNegotiation
import no.nav.emottak.plugin.configureMetrics
import no.nav.emottak.plugin.configureRoutes
import no.nav.emottak.processor.MailProcessor
import no.nav.emottak.processor.MessageProcessor
import no.nav.emottak.publisher.MailPublisher
import no.nav.emottak.receiver.PayloadReceiver
import no.nav.emottak.receiver.SignalReceiver
import no.nav.emottak.repository.PayloadRepository
import no.nav.emottak.smtp.MailSender
import no.nav.emottak.util.EbmsAsyncClient
import no.nav.emottak.util.coroutineScope
import no.nav.emottak.util.eventLoggingService
import no.nav.emottak.utils.kafka.client.EventPublisherClient
import no.nav.emottak.utils.kafka.service.EventLoggingService
import org.slf4j.LoggerFactory
import kotlin.coroutines.coroutineContext

internal val log = LoggerFactory.getLogger("no.nav.emottak.smtp")

fun main() = SuspendApp {
    result {
        resourceScope {
            val deps = initDependencies()
            deps.migrationService.migrate()

            val scope = coroutineScope(coroutineContext)
            val eventScope = coroutineScope(Dispatchers.IO)
            val eventLoggingService = eventLoggingService(
                eventScope,
                EventLoggingService(config().eventLogging, EventPublisherClient(config().kafka))
            )

            val mailPublisher = MailPublisher(deps.kafkaPublisher, eventLoggingService)
            val ebmsAsyncClient = EbmsAsyncClient(deps.httpClient)
            val payloadReceiver = PayloadReceiver(deps.kafkaReceiver, ebmsAsyncClient, eventLoggingService)
            val signalReceiver = SignalReceiver(deps.kafkaReceiver, eventLoggingService)
            val payloadRepository = PayloadRepository(deps.payloadDatabase, eventLoggingService)
            val mailProcessor = MailProcessor(deps.store, mailPublisher, payloadRepository, eventLoggingService)
            val mailSender = MailSender(deps.session, eventLoggingService)
            val messageProcessor = MessageProcessor(payloadReceiver, signalReceiver, mailSender)

            val server = config().server

            server(
                Netty,
                port = server.port.value,
                preWait = server.preWait,
                module = smtpTransportModule(deps.meterRegistry, payloadRepository)
            )

            messageProcessor.processMailRoutingMessages(scope)

            scheduleProcessMailMessages(mailProcessor)

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

private suspend fun ResourceScope.scheduleProcessMailMessages(processor: MailProcessor): Long {
    val scope = coroutineScope(coroutineContext)
    return Schedule
        .spaced<Unit>(config().job.fixedInterval)
        .repeat { processor.processMessages(scope) }
}

private fun logError(t: Throwable) = log.error("Shutdown smtp-transport due to: ${t.stackTraceToString()}")
