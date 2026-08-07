package no.nav.emottak

import arrow.continuations.SuspendApp
import arrow.continuations.ktor.server
import arrow.core.raise.result
import arrow.fx.coroutines.ResourceScope
import arrow.fx.coroutines.autoCloseable
import arrow.fx.coroutines.resourceScope
import arrow.resilience.Schedule
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.plugins.retry
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.application.Application
import io.ktor.server.netty.Netty
import io.ktor.utils.io.CancellationException
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toKotlinDuration

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
            log.info("Flyway migration successful")
            if (!config().smtp.smtpStopUrl.contains("localhost")) {
                log.info("Deactivating old pod process...")
                runCatching {
                    deps.httpClient.get(config().smtp.smtpStopUrl) {
                        timeout { requestTimeoutMillis = 10000 }
                        retry { noRetry() }
                        expectSuccess = true
                    }
                }.onSuccess {
                    log.info("Deactivation successful: " + it.bodyAsText())
                }.onFailure {
                    when (it) {
                        is CancellationException -> throw it
                        is HttpRequestTimeoutException -> log.warn("Deactivation timed out after 10 seconds, continuing startup")
                        else -> log.warn("Deactivation failed: ${it.message}, continuing startup")
                    }
                }
            }
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
            val server = config().server

            server(
                Netty,
                port = server.port.value,
                preWait = server.preWait,
                module = smtpTransportModule(deps.meterRegistry, payloadRepository)
            )

            val inboxSize = AtomicInteger(0)
            deps.meterRegistry.registerInboxSizeGauge(inboxSize)

            val mailSender = autoCloseable { MailSender(deps.session, eventLoggingService, config().smtp, deps.meterRegistry) }
            val mailProcessor = MailProcessor(
                deps.store,
                mailPublisher,
                payloadRepository,
                eventLoggingService,
                mailSender,
                config().mail,
                deps.meterRegistry,
                inboxSize
            )
            val messageProcessor = MessageProcessor(payloadReceiver, signalReceiver, mailSender)

            messageProcessor.processMailRoutingMessages(scope)

            if (config().cleanupPayloadsJob.enabled) {
                scope.launch { scheduleCleanupPayloads(payloadRepository) }
            }
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
    val initialDelay = config().mailJob.initialDelay
    if (initialDelay > Duration.ZERO) {
        log.info("Delaying initial mail processing by $initialDelay")
        delay(initialDelay)
    }
    return Schedule
        .spaced<Unit>(config().mailJob.fixedInterval)
        .repeat {
            if (mailReaderActive.get()) {
                when (processor.processMessages(scope).await()) {
                    MailProcessor.InboxStatus.CRITICAL,
                    MailProcessor.InboxStatus.STILL_LESS_THAN_WARNING_THRESHOLD -> {
                        return@repeat
                    }
                    else -> { }
                }
            } else {
                log.info("Mail reading is disabled, reactivate to process messages")
            }
            delay(30.seconds)
        }
}

private suspend fun scheduleCleanupPayloads(payloadRepository: PayloadRepository) {
    val cleanupPayloadsJob = config().cleanupPayloadsJob
    val readableInterval = cleanupPayloadsJob.fixedInterval.readableInterval()
    val (firstRunAt, initialDelay) = cleanupPayloadsJob.startAtTime.value.firstRunAt()
    val schedule = FixedIntervalSchedule(firstRunAt, cleanupPayloadsJob.fixedInterval)
    log.info("Delaying initial payload cleanup by ${initialDelay.readableInterval()}, running every $readableInterval after that")
    delay(initialDelay)
    while (true) {
        log.info("Starting job to delete payloads older than ${cleanupPayloadsJob.keepPayloadsDays.value} days")
        try {
            val deleted = payloadRepository.cleanupOldPayloads(
                cleanupPayloadsJob.keepPayloadsDays.value,
                cleanupPayloadsJob.batchSize.value
            )
            log.info("Cleaned up $deleted payload(s) older than ${cleanupPayloadsJob.keepPayloadsDays.value} days")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error("Failed to clean up old payloads", e)
        }
        // Kalkulerer når neste kjøretid er, basert på første kjøretid
        val nextDelay = schedule.durationUntilNextRun()
        log.info("Next payload cleanup in ${nextDelay.readableInterval()}")
        delay(nextDelay)
    }
}

/**
 * Returnerer neste LocalDateTime denne LocalTime-en inntreffer på (i dag hvis klokkeslettet
 * ikke allerede er passert, ellers i morgen). Brukes til å regne ut ankeret (første kjøretidspunkt)
 * for en [FixedIntervalSchedule].
 */
internal fun LocalTime.firstRunAt(now: LocalDateTime = LocalDateTime.now()): Pair<LocalDateTime, Duration> {
    val today = now.toLocalDate().atTime(this)
    val firstRunAt = if (now.isBefore(today)) today else today.plusDays(1)
    return Pair(firstRunAt, java.time.Duration.between(now, firstRunAt).toKotlinDuration())
}

/**
 * Holder styr på et fast, klokkeslett-forankret kjøreskjema.
 *
 * Eksempel 1:
 * firstRunAt = 00:00, fixedInterval = 12 timer -> kjøring skjer kl 00:00, 12:00, 00:00, osv.
 * Er klokka 10:00 akkurat nå, blir neste kjøretid kl 12:00 (om 2 timer).
 *
 * Eksempel 2:
 * firstRunAt = 00:00, fixedInterval = 48 timer -> kjøring skjer kl 00:00 annenhver dag.
 * Er klokka 10:00 samme dag som firstRunAt, blir neste kjøretid kl 00:00 (om 38 timer).
 */
internal class FixedIntervalSchedule(private val firstRunAt: LocalDateTime, private val fixedInterval: Duration) {
    fun durationUntilNextRun(now: LocalDateTime = LocalDateTime.now()): Duration {
        val intervalMillis = fixedInterval.inWholeMilliseconds
        val elapsedSinceFirstRun = java.time.Duration.between(firstRunAt, now).toMillis()
        val stepsToNextRun = Math.floorDiv(elapsedSinceFirstRun, intervalMillis) + 1
        val nextRun = firstRunAt.plus(java.time.Duration.ofMillis(intervalMillis * stepsToNextRun))
        return java.time.Duration.between(now, nextRun).toKotlinDuration()
    }
}

/** Lesbar presentasjon av en Duration, slik som "1 day, 3 hours, 30 minutes". */
internal fun Duration.readableInterval(): String {
    this.toComponents { days, hours, minutes, seconds, nanoseconds ->
        var readable = ""
        if (days > 0) readable = "$days days"
        if (hours > 0) readable = if (readable != "") "$readable, $hours hours" else "$hours hours"
        if (minutes > 0) readable = if (readable != "") "$readable, $minutes minutes" else "$minutes minutes"
        if (readable == "") readable = "$seconds seconds"
        return readable
    }
}

private fun logError(t: Throwable) = log.error("Shutdown smtp-transport due to: ${t.stackTraceToString()}")
