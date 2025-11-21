package no.nav.emottak.receiver

import arrow.core.raise.recover
import io.github.nomisRev.kafka.receiver.KafkaReceiver
import io.github.nomisRev.kafka.receiver.ReceiverRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import no.nav.emottak.PayloadError
import no.nav.emottak.config
import no.nav.emottak.log
import no.nav.emottak.model.MailMetadata
import no.nav.emottak.model.MailRoutingPayloadMessage
import no.nav.emottak.model.Payload
import no.nav.emottak.model.PayloadMessage
import no.nav.emottak.util.EBXML_SERVICE
import no.nav.emottak.util.EMAIL_ADDRESSES
import no.nav.emottak.util.EbmsAsyncClient
import no.nav.emottak.util.ScopedEventLoggingService
import no.nav.emottak.util.getHeaderValueAsString
import no.nav.emottak.utils.kafka.model.EventType.ERROR_WHILE_READING_MESSAGE_FROM_QUEUE
import no.nav.emottak.utils.kafka.model.EventType.ERROR_WHILE_RECEIVING_PAYLOAD_VIA_HTTP
import no.nav.emottak.utils.kafka.model.EventType.MESSAGE_READ_FROM_QUEUE
import no.nav.emottak.utils.kafka.model.EventType.PAYLOAD_RECEIVED_VIA_HTTP
import kotlin.uuid.Uuid

class PayloadReceiver(
    private val kafkaReceiver: KafkaReceiver<String, ByteArray>,
    private val ebmsAsyncClient: EbmsAsyncClient,
    private val eventLoggingService: ScopedEventLoggingService
) {
    private val kafka = config().kafkaTopics

    fun receiveMailRoutingMessages(): Flow<MailRoutingPayloadMessage> = kafkaReceiver
        .receive(kafka.payloadOutTopic)
        .catch { error ->
            eventLoggingService.registerEvent(
                ERROR_WHILE_READING_MESSAGE_FROM_QUEUE,
                Exception(error)
            )

            throw error
        }
        .map(::toMailRoutingMessage)

    private suspend fun toMailRoutingMessage(record: ReceiverRecord<String, ByteArray>): MailRoutingPayloadMessage {
        val mailMetadata = MailMetadata(
            addresses = record.getHeaderValueAsString(EMAIL_ADDRESSES),
            subject = record.getHeaderValueAsString(EBXML_SERVICE)
        )

        val referenceId = Uuid.parse(record.key())
        val payloadMessage = PayloadMessage(
            referenceId,
            record.value(),
            getPayloads(referenceId)
        )

        eventLoggingService.registerEvent(
            MESSAGE_READ_FROM_QUEUE,
            referenceId
        )

        return MailRoutingPayloadMessage(mailMetadata, payloadMessage)
    }

    private suspend fun getPayloads(referenceId: Uuid): List<Payload> =
        with(ebmsAsyncClient) {
            recover({
                val payloads = getPayloads(referenceId)
                    .also { log.info("Retrieved ${it.size} payload(s) for reference id: $referenceId") }

                payloads.map {
                    eventLoggingService.registerEvent(
                        PAYLOAD_RECEIVED_VIA_HTTP,
                        it
                    )
                }

                payloads
            }) { error: PayloadError ->
                eventLoggingService.registerEvent(
                    ERROR_WHILE_RECEIVING_PAYLOAD_VIA_HTTP,
                    Exception(error.toString()),
                    referenceId
                )
                emptyList<Payload>().also { log.error("$error") }
            }
        }
}
