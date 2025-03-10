package no.nav.emottak.receiver

import io.github.nomisRev.kafka.receiver.KafkaReceiver
import io.github.nomisRev.kafka.receiver.ReceiverRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import no.nav.emottak.config
import no.nav.emottak.model.MailMetadata
import no.nav.emottak.model.MailRoutingSignalMessage
import no.nav.emottak.model.SignalMessage
import no.nav.emottak.util.getHeaderValueAsString
import kotlin.uuid.Uuid

class SignalReceiver(
    private val kafkaReceiver: KafkaReceiver<String, ByteArray>
) {
    private val kafka = config().kafka

    fun receiveMailRoutingMessages(): Flow<MailRoutingSignalMessage> = kafkaReceiver
        .receive(kafka.signalOutTopic)
        .map(::toMailRoutingMessage)

    private fun toMailRoutingMessage(record: ReceiverRecord<String, ByteArray>): MailRoutingSignalMessage {
        val mailAddresses = record.getHeaderValueAsString("mailAddresses")
        val mailMetadata = MailMetadata(mailAddresses)

        val signalMessage = SignalMessage(
            Uuid.parse(record.key()),
            record.value()
        )

        return MailRoutingSignalMessage(mailMetadata, signalMessage)
    }
}
