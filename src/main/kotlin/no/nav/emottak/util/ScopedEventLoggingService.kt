package no.nav.emottak.util

import jakarta.mail.internet.MimeMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import no.nav.emottak.log
import no.nav.emottak.model.Payload
import no.nav.emottak.utils.kafka.model.Event
import no.nav.emottak.utils.kafka.model.EventType
import no.nav.emottak.utils.kafka.service.EventLoggingService
import no.nav.emottak.utils.serialization.toEventDataJson
import java.time.Instant
import kotlin.uuid.Uuid

interface ScopedEventLoggingService {
    fun registerEvent(eventType: EventType, messageId: Uuid)
    fun registerEvent(eventType: EventType, payload: Payload)
    fun registerEvent(eventType: EventType, mimeMessage: MimeMessage, requestId: Uuid)
    fun registerEvent(eventType: EventType, error: Exception, requestId: Uuid = Uuid.random())
}

fun eventLoggingService(
    scope: CoroutineScope,
    eventLoggingService: EventLoggingService
): ScopedEventLoggingService = object : ScopedEventLoggingService {
    override fun registerEvent(
        eventType: EventType,
        mimeMessage: MimeMessage,
        requestId: Uuid
    ) {
        publishEvent(
            eventType,
            mimeMessage.contentID ?: "",
            mimeMessage.messageID ?: "",
            "{}",
            requestId = requestId
        )
    }

    override fun registerEvent(
        eventType: EventType,
        payload: Payload
    ) {
        publishEvent(
            eventType,
            payload.contentId,
            payload.referenceId.toString(),
            "{}"
            // TODO: Sende inn payload.referenceId her (key fra kafka)?
        )
    }

    override fun registerEvent(
        eventType: EventType,
        messageId: Uuid
    ) {
        publishEvent(
            eventType,
            "",
            messageId.toString(),
            "{}"
        )
    }

    override fun registerEvent(
        eventType: EventType,
        error: Exception,
        requestId: Uuid
    ) {
        publishEvent(
            eventType,
            "",
            "",
            error.toEventDataJson(),
            requestId = requestId
        )
    }

    private fun publishEvent(
        eventType: EventType,
        contentId: String,
        messageId: String,
        eventData: String,
        requestId: Uuid = Uuid.random()
    ) = scope.launch {
        val event = Event(
            eventType,
            requestId,
            contentId,
            messageId,
            eventData,
            Instant.now()
        )

        eventLoggingService.logEvent(event)
            .onSuccess { log.debug("Event published successfully: {}", event) }
            .onFailure { log.error("Error while publishing event: ${it.stackTraceToString()}") }
    }
}

fun fakeEventLoggingService(): ScopedEventLoggingService =
    object : ScopedEventLoggingService {
        override fun registerEvent(
            eventType: EventType,
            mimeMessage: MimeMessage,
            requestId: Uuid
        ) {
            logEvent(eventType)
        }

        override fun registerEvent(
            eventType: EventType,
            payload: Payload
        ) {
            logEvent(eventType)
        }

        override fun registerEvent(
            eventType: EventType,
            messageId: Uuid
        ) {
            logEvent(eventType)
        }

        override fun registerEvent(
            eventType: EventType,
            error: Exception,
            requestId: Uuid
        ) {
            logEvent(eventType)
        }

        private fun logEvent(eventType: EventType) {
            log.info("Registered event: $eventType")
        }
    }
