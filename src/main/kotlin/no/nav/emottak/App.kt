package no.nav.emottak

import arrow.continuations.SuspendApp
import arrow.continuations.ktor.server
import arrow.core.raise.result
import arrow.fx.coroutines.ResourceScope
import arrow.fx.coroutines.autoCloseable
import arrow.fx.coroutines.resourceScope
import arrow.resilience.Schedule
import io.ktor.server.application.Application
import io.ktor.server.netty.Netty
import io.ktor.utils.io.CancellationException
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
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
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration

internal val log = LoggerFactory.getLogger("no.nav.emottak.smtp")
val mailReaderActive = AtomicBoolean(config().mail.inboxReadActive)

fun main() = SuspendApp {
    result {
        resourceScope {
            log.info("Starting application, initializing dependencies...")
            val deps = initDependencies()
            log.info("Dependencies initialized.")
            log.info("Starting flyway migrations...")
            deps.migrationService.migrate()
            log.info("Flyway migration successfully.")

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
            val mailSender = autoCloseable { MailSender(deps.session, eventLoggingService, config().smtp) }
            val mailProcessor = MailProcessor(deps.store, mailPublisher, payloadRepository, eventLoggingService, mailSender, config().mail)
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
    val initialDelay = config().job.initialDelay
    if (initialDelay > Duration.ZERO) {
        log.info("Delaying initial mail processing by $initialDelay")
        delay(initialDelay)
    }
    return Schedule
        .spaced<Unit>(config().job.fixedInterval)
        .repeat {
            if (mailReaderActive.get()) {
                processor.processMessages(scope)
            } else {
                log.info("Mail reading is disabled, reactivate to process messages")
            }
        }
}

private fun logError(t: Throwable) = log.error("Shutdown smtp-transport due to: ${t.stackTraceToString()}")
