package no.nav.emottak.receiver

import io.github.nomisRev.kafka.receiver.KafkaReceiver
import io.github.nomisRev.kafka.receiver.ReceiverRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import no.nav.emottak.config
import no.nav.emottak.model.PayloadMessage
import kotlin.uuid.Uuid

class PayloadReceiver(
    private val kafkaReceiver: KafkaReceiver<String, ByteArray>
) {
    private val kafka = config().kafka

    fun receivePayloadMessages(): Flow<PayloadMessage> = kafkaReceiver
        .receive(kafka.payloadOutTopic)
        .map(::toPayloadMessage)

    private fun toPayloadMessage(record: ReceiverRecord<String, ByteArray>) =
        PayloadMessage(
            Uuid.parse(record.key()),
            record.value(),
            // will be populated by data retrieved from REST-call
            emptyList()
        )
}
