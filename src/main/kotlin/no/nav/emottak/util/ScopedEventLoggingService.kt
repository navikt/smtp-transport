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
    fun registerEvent(eventType: EventType, mimeMessage: MimeMessage)
    fun registerEvent(eventType: EventType, error: Exception)
}

fun eventLoggingService(
    scope: CoroutineScope,
    eventLoggingService: EventLoggingService
): ScopedEventLoggingService = object : ScopedEventLoggingService {
    override fun registerEvent(
        eventType: EventType,
        mimeMessage: MimeMessage
    ) {
        publishEvent(
            eventType,
            mimeMessage.contentID ?: "",
            mimeMessage.messageID ?: "",
            "{}"
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
        error: Exception
    ) {
        publishEvent(
            eventType,
            "",
            "",
            error.toEventDataJson()
        )
    }

    private fun publishEvent(
        eventType: EventType,
        contentId: String,
        messageId: String,
        eventData: String
    ) = scope.launch {
        val event = Event(
            eventType,
            Uuid.random(),
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
            mimeMessage: MimeMessage
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
            error: Exception
        ) {
            logEvent(eventType)
        }

        private fun logEvent(eventType: EventType) {
            log.info("Registered event: $eventType")
        }
    }
