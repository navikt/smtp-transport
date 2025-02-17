package no.nav.emottak.receiver

import arrow.core.raise.recover
import io.github.nomisRev.kafka.receiver.KafkaReceiver
import io.github.nomisRev.kafka.receiver.ReceiverRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import no.nav.emottak.PayloadError
import no.nav.emottak.config
import no.nav.emottak.log
import no.nav.emottak.model.Payload
import no.nav.emottak.model.PayloadMessage
import no.nav.emottak.util.EbmsProviderClient
import kotlin.uuid.Uuid

class PayloadReceiver(
    private val kafkaReceiver: KafkaReceiver<String, ByteArray>,
    private val ebmsProviderClient: EbmsProviderClient
) {
    private val kafka = config().kafka

    fun receivePayloadMessages(): Flow<PayloadMessage> = kafkaReceiver
        .receive(kafka.payloadOutTopic)
        .map(::toPayloadMessage)

    private suspend fun toPayloadMessage(record: ReceiverRecord<String, ByteArray>): PayloadMessage {
        val referenceId = Uuid.parse(record.key())
        return PayloadMessage(
            referenceId,
            record.value(),
            getPayloads(referenceId)
        )
    }

    private suspend fun getPayloads(uuid: Uuid): List<Payload> =
        with(ebmsProviderClient) {
            recover({
                getPayloads(uuid).also { log.info("Retrieved payloads for reference id $uuid: $it") }
            }) { e: PayloadError -> emptyList<Payload>().also { log.error("$e") } }
        }
}
