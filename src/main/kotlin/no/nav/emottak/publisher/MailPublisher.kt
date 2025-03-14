package no.nav.emottak.publisher

import io.github.nomisRev.kafka.publisher.KafkaPublisher
import no.nav.emottak.config
import no.nav.emottak.log
import no.nav.emottak.model.PayloadMessage
import no.nav.emottak.model.SignalMessage
import org.apache.kafka.clients.producer.ProducerRecord
import kotlin.uuid.Uuid

class MailPublisher(
    private val kafkaPublisher: KafkaPublisher<String, ByteArray>
) {
    private val kafka = config().kafkaTopics

    suspend fun publishPayloadMessage(message: PayloadMessage) =
        publishMessage(kafka.payloadInTopic, message.messageId, message.envelope)

    suspend fun publishSignalMessage(message: SignalMessage) =
        publishMessage(kafka.signalInTopic, message.messageId, message.envelope)

    private suspend fun publishMessage(topic: String, referenceId: Uuid, content: ByteArray) =
        kafkaPublisher.publishScope {
            publishCatching(toProducerRecord(topic, referenceId, content))
        }
            .onSuccess { log.info("Published message with reference id $referenceId to: $topic") }
            .onFailure { log.error("Failed to publish message with reference id: $referenceId") }

    private fun toProducerRecord(topic: String, referenceId: Uuid, content: ByteArray) =
        ProducerRecord(
            topic,
            referenceId.toString(),
            content
        )
}
