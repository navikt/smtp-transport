package no.nav.emottak.publisher

import io.github.nomisRev.kafka.publisher.KafkaPublisher
import kotlinx.serialization.json.Json
import no.nav.emottak.config
import no.nav.emottak.log
import no.nav.emottak.model.PayloadMessage
import no.nav.emottak.model.SignalMessage
import no.nav.emottak.util.SENDER_ADDRESS
import no.nav.emottak.util.ScopedEventLoggingService
import no.nav.emottak.utils.kafka.model.EventDataType
import no.nav.emottak.utils.kafka.model.EventType.ERROR_WHILE_STORING_MESSAGE_IN_QUEUE
import no.nav.emottak.utils.kafka.model.EventType.MESSAGE_PLACED_IN_QUEUE
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import kotlin.uuid.Uuid

class MailPublisher(
    private val kafkaPublisher: KafkaPublisher<String, ByteArray>,
    private val eventLoggingService: ScopedEventLoggingService
) {
    private val kafka = config().kafkaTopics

    suspend fun publishPayloadMessage(message: PayloadMessage, senderAddress: String): Result<RecordMetadata> =
        publishMessage(kafka.payloadInTopic, message.messageId, message.envelope, senderAddress)

    suspend fun publishSignalMessage(message: SignalMessage, senderAddress: String): Result<RecordMetadata> =
        publishMessage(kafka.signalInTopic, message.messageId, message.envelope, senderAddress)

    private suspend fun publishMessage(topic: String, referenceId: Uuid, content: ByteArray, senderAddress: String): Result<RecordMetadata> =
        kafkaPublisher.publishScope {
            publishCatching(
                toProducerRecord(topic, referenceId, content).apply {
                    headers().add(SENDER_ADDRESS, senderAddress.toByteArray())
                }
            )
        }
            .onSuccess {
                log.info("Published message with reference id $referenceId to: $topic")

                eventLoggingService.registerEvent(
                    eventType = MESSAGE_PLACED_IN_QUEUE,
                    messageId = referenceId,
                    referenceId = referenceId,
                    eventData = Json.encodeToString(
                        mapOf(EventDataType.QUEUE_NAME.value to topic)
                    )
                )
            }
            .onFailure {
                log.error("Failed to publish message with reference id: $referenceId", it)

                eventLoggingService.registerEvent(
                    eventType = ERROR_WHILE_STORING_MESSAGE_IN_QUEUE,
                    messageId = referenceId,
                    referenceId = referenceId,
                    eventData = Json.encodeToString(
                        mapOf(
                            EventDataType.ERROR_MESSAGE.value to it.localizedMessage,
                            EventDataType.QUEUE_NAME.value to topic
                        )
                    )
                )
            }

    private fun toProducerRecord(topic: String, referenceId: Uuid, content: ByteArray) =
        ProducerRecord(
            topic,
            referenceId.toString(),
            content
        )
}
