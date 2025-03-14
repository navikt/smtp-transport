package no.nav.emottak.receiver

import arrow.core.raise.recover
import io.github.nomisRev.kafka.receiver.KafkaReceiver
import io.github.nomisRev.kafka.receiver.ReceiverRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import no.nav.emottak.PayloadError
import no.nav.emottak.config
import no.nav.emottak.log
import no.nav.emottak.model.MailMetadata
import no.nav.emottak.model.MailRoutingPayloadMessage
import no.nav.emottak.model.Payload
import no.nav.emottak.model.PayloadMessage
import no.nav.emottak.util.EbmsAsyncClient
import no.nav.emottak.util.getHeaderValueAsString
import kotlin.uuid.Uuid

class PayloadReceiver(
    private val kafkaReceiver: KafkaReceiver<String, ByteArray>,
    private val ebmsAsyncClient: EbmsAsyncClient
) {
    private val kafka = config().kafkaTopics

    fun receiveMailRoutingMessages(): Flow<MailRoutingPayloadMessage> = kafkaReceiver
        .receive(kafka.payloadOutTopic)
        .map(::toMailRoutingMessage)

    private suspend fun toMailRoutingMessage(record: ReceiverRecord<String, ByteArray>): MailRoutingPayloadMessage {
        val mailAddresses = record.getHeaderValueAsString("mailAddresses")
        val mailMetadata = MailMetadata(mailAddresses)

        val referenceId = Uuid.parse(record.key())
        val payloadMessage = PayloadMessage(
            referenceId,
            record.value(),
            getPayloads(referenceId)
        )

        return MailRoutingPayloadMessage(mailMetadata, payloadMessage)
    }

    private suspend fun getPayloads(uuid: Uuid): List<Payload> =
        with(ebmsAsyncClient) {
            recover({
                getPayloads(uuid).also { log.info("Retrieved ${it.size} payload(s) for reference id: $uuid") }
            }) { e: PayloadError -> emptyList<Payload>().also { log.error("$e") } }
        }
}
