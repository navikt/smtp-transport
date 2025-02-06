package no.nav.emottak.receiver

import io.github.nomisRev.kafka.receiver.KafkaReceiver
import io.github.nomisRev.kafka.receiver.ReceiverRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import no.nav.emottak.config
import no.nav.emottak.model.SignalMessage
import kotlin.uuid.Uuid

class SignalReceiver(
    private val kafkaReceiver: KafkaReceiver<String, ByteArray>
) {
    private val kafka = config().kafka

    fun receiveSignalMessages(): Flow<SignalMessage> = kafkaReceiver
        .receive(kafka.signalOutTopic)
        .map(::toSignalMessage)

    private fun toSignalMessage(record: ReceiverRecord<String, ByteArray>) =
        SignalMessage(
            Uuid.parse(record.key()),
            record.value()
        )
}
