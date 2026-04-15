package no.nav.emottak.receiver

import arrow.core.raise.recover
import io.github.nomisRev.kafka.receiver.KafkaReceiver
import io.github.nomisRev.kafka.receiver.ReceiverRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import no.nav.emottak.PayloadError
import no.nav.emottak.config
import no.nav.emottak.log
import no.nav.emottak.model.Attachment
import no.nav.emottak.model.MailMetadata
import no.nav.emottak.model.MailRoutingPayloadMessage
import no.nav.emottak.model.Payload
import no.nav.emottak.model.PayloadMessage
import no.nav.emottak.model.SoapWithAttachments
import no.nav.emottak.util.EBXML_SERVICE
import no.nav.emottak.util.EMAIL_ADDRESSES
import no.nav.emottak.util.EbmsAsyncClient
import no.nav.emottak.util.SENDER_ADDRESS
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
            recipientAddress = record.getHeaderValueAsString(EMAIL_ADDRESSES),
            subject = record.getHeaderValueAsString(EBXML_SERVICE),
            senderAddress = record.getHeaderValueAsString(SENDER_ADDRESS)
        )

        val referenceId = Uuid.parse(record.key())
        val payloadMessage = parseSoapWithAttachments(record)
            ?: PayloadMessage(
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

    private fun parseSoapWithAttachments(record: ReceiverRecord<String, ByteArray>): PayloadMessage? {
        val referenceId = Uuid.parse(record.key())
        val formatHeaders = record.headers().headers(SoapWithAttachments.MESSAGE_FORMAT_HEADER).toList()
        if (formatHeaders.isEmpty()) return null
        require(formatHeaders.size == 1) {
            "Expected exactly one ${SoapWithAttachments.MESSAGE_FORMAT_HEADER} header for reference id $referenceId"
        }

        val formatValue = String(formatHeaders.single().value())
        require(formatValue == SoapWithAttachments.MESSAGE_FORMAT_VALUE) {
            "Unsupported ${SoapWithAttachments.MESSAGE_FORMAT_HEADER} '$formatValue' for reference id $referenceId"
        }

        val soapWithAttachments = try {
            Json.decodeFromString<SoapWithAttachments>(String(record.value()))
        } catch (error: SerializationException) {
            throw IllegalArgumentException(
                "Invalid ${SoapWithAttachments.MESSAGE_FORMAT_VALUE} payload for reference id $referenceId",
                error
            )
        }

        validateSoapWithAttachments(soapWithAttachments, referenceId)

        val payloads = soapWithAttachments.attachments.map { attachment ->
            Payload(
                referenceId = referenceId,
                contentId = attachment.contentId,
                contentType = attachment.contentType,
                content = attachment.content
            )
        }

        log.info("Parsed SoapWithAttachments message for reference id: $referenceId")
        return PayloadMessage(referenceId, soapWithAttachments.envelope, payloads)
    }

    private fun validateSoapWithAttachments(soapWithAttachments: SoapWithAttachments, referenceId: Uuid) {
        require(soapWithAttachments.envelope.isNotEmpty()) {
            "Envelope cannot be empty for reference id $referenceId"
        }

        soapWithAttachments.attachments.forEachIndexed { index, attachment ->
            validateAttachment(attachment, referenceId, index)
        }

        val duplicateContentIds = soapWithAttachments.attachments
            .groupingBy(Attachment::contentId)
            .eachCount()
            .filterValues { it > 1 }
            .keys
        require(duplicateContentIds.isEmpty()) {
            "Attachment contentId values must be unique for reference id $referenceId: ${duplicateContentIds.joinToString()}"
        }
    }

    private fun validateAttachment(attachment: Attachment, referenceId: Uuid, index: Int) {
        require(attachment.contentId.isNotBlank()) {
            "Attachment at index $index is missing contentId for reference id $referenceId"
        }
        require(attachment.contentType.isNotBlank()) {
            "Attachment ${attachment.contentId} is missing contentType for reference id $referenceId"
        }
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
