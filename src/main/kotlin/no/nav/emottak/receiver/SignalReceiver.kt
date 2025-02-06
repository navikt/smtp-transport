package no.nav.emottak.receiver

import io.github.nomisRev.kafka.receiver.KafkaReceiver
import io.github.nomisRev.kafka.receiver.ReceiverRecord
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import no.nav.emottak.config
import no.nav.emottak.log

class SignalReceiver(
    private val kafkaReceiver: KafkaReceiver<String, ByteArray>
) {
    private val kafka = config().kafka

    suspend fun receiveSignalMessages() = coroutineScope {
        kafkaReceiver
            .receive(kafka.signalOutTopic)
            .onEach(::processSignalMessage)
            .launchIn(this)
    }

    private fun processSignalMessage(
        record: ReceiverRecord<String, ByteArray>
    ) {
        log.info("Received signal message with reference id ${record.key()} from: ${record.topic()}")
    }
}
