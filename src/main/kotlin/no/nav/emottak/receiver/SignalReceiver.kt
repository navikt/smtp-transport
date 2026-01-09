package no.nav.emottak.receiver

import io.github.nomisRev.kafka.receiver.KafkaReceiver
import io.github.nomisRev.kafka.receiver.ReceiverRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import no.nav.emottak.config
import no.nav.emottak.model.MailMetadata
import no.nav.emottak.model.MailRoutingSignalMessage
import no.nav.emottak.model.SignalMessage
import no.nav.emottak.util.EBXML_ACTION
import no.nav.emottak.util.EMAIL_ADDRESSES
import no.nav.emottak.util.EMOTTAK_EBXML_SENDER
import no.nav.emottak.util.ScopedEventLoggingService
import no.nav.emottak.util.getHeaderValueAsString
import no.nav.emottak.utils.kafka.model.EventType.ERROR_WHILE_READING_MESSAGE_FROM_QUEUE
import no.nav.emottak.utils.kafka.model.EventType.MESSAGE_READ_FROM_QUEUE
import kotlin.uuid.Uuid

class SignalReceiver(
    private val kafkaReceiver: KafkaReceiver<String, ByteArray>,
    private val eventLoggingService: ScopedEventLoggingService
) {
    private val kafka = config().kafkaTopics

    fun receiveMailRoutingMessages(): Flow<MailRoutingSignalMessage> = kafkaReceiver
        .receive(kafka.signalOutTopic)
        .catch { error ->
            eventLoggingService.registerEvent(
                ERROR_WHILE_READING_MESSAGE_FROM_QUEUE,
                Exception(error)
            )

            throw error
        }
        .map(::toMailRoutingMessage)

    private fun toMailRoutingMessage(record: ReceiverRecord<String, ByteArray>): MailRoutingSignalMessage {
        val mailMetadata = MailMetadata(
            recipientAddress = record.getHeaderValueAsString(EMAIL_ADDRESSES),
            subject = record.getHeaderValueAsString(EBXML_ACTION),
            senderAddress = record.getHeaderValueAsString(EMOTTAK_EBXML_SENDER)
        )
        val signalMessage = SignalMessage(
            Uuid.parse(record.key()),
            record.value()
        )

        eventLoggingService.registerEvent(
            MESSAGE_READ_FROM_QUEUE,
            signalMessage.messageId
        )

        return MailRoutingSignalMessage(mailMetadata, signalMessage)
    }
}
