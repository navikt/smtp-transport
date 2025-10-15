package no.nav.emottak.publisher

import io.github.nomisRev.kafka.publisher.KafkaPublisher
import no.nav.emottak.config
import no.nav.emottak.log
import no.nav.emottak.model.PayloadMessage
import no.nav.emottak.model.SignalMessage
import no.nav.emottak.util.ScopedEventLoggingService
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

    suspend fun publishPayloadMessage(message: PayloadMessage): Result<RecordMetadata> =
        publishMessage(kafka.payloadInTopic, message.messageId, message.envelope)

    suspend fun publishSignalMessage(message: SignalMessage): Result<RecordMetadata> =
        publishMessage(kafka.signalInTopic, message.messageId, message.envelope)

    private suspend fun publishMessage(topic: String, referenceId: Uuid, content: ByteArray): Result<RecordMetadata> =
        kafkaPublisher.publishScope {
            publishCatching(toProducerRecord(topic, referenceId, content))
        }
            .onSuccess {
                log.info("Published message with reference id $referenceId to: $topic")

                eventLoggingService.registerEvent(
                    MESSAGE_PLACED_IN_QUEUE,
                    referenceId
                )
            }
            .onFailure {
                log.error("Failed to publish message with reference id: $referenceId")

                eventLoggingService.registerEvent(
                    ERROR_WHILE_STORING_MESSAGE_IN_QUEUE,
                    Exception("Failed to publish message with reference id: $referenceId and content: $content"),
                    referenceId
                )
            }

    private fun toProducerRecord(topic: String, referenceId: Uuid, content: ByteArray) =
        ProducerRecord(
            topic,
            referenceId.toString(),
            content
        )
}
