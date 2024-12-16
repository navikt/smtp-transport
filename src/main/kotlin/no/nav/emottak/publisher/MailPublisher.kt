package no.nav.emottak.publisher

import io.github.nomisRev.kafka.publisher.KafkaPublisher
import no.nav.emottak.configuration.Kafka
import no.nav.emottak.log
import org.apache.kafka.clients.producer.ProducerRecord
import java.util.UUID

class MailPublisher(
    private val kafka: Kafka,
    private val kafkaPublisher: KafkaPublisher<String, ByteArray>
) {
    suspend fun publishPayloadMessage(referenceId: UUID, content: ByteArray) =
        publishMessage(kafka.payloadTopic, referenceId, content)

    suspend fun publishSignalMessage(referenceId: UUID, content: ByteArray) =
        publishMessage(kafka.signalTopic, referenceId, content)

    private suspend fun publishMessage(topic: String, referenceId: UUID, content: ByteArray) =
        kafkaPublisher.publishScope {
            publishCatching(toProducerRecord(topic, referenceId, content))
        }
            .onSuccess { log.info("Published message with reference id $referenceId to: $topic") }
            .onFailure { log.error("Failed to publish message with reference id: $referenceId") }

    private fun toProducerRecord(topic: String, referenceId: UUID, content: ByteArray) =
        ProducerRecord(
            topic,
            referenceId.toString(),
            content
        )
}
