package no.nav.emottak.processor

import arrow.autoCloseScope
import arrow.core.raise.fold
import jakarta.mail.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import no.nav.emottak.config
import no.nav.emottak.log
import no.nav.emottak.model.PayloadMessage
import no.nav.emottak.model.SignalMessage
import no.nav.emottak.publisher.MailPublisher
import no.nav.emottak.repository.PayloadRepository
import no.nav.emottak.smtp.EmailMsg
import no.nav.emottak.smtp.MailReader
import no.nav.emottak.smtp.MailSender
import no.nav.emottak.util.ForwardingSystem
import no.nav.emottak.util.ScopedEventLoggingService
import no.nav.emottak.util.toPayloadMessage
import no.nav.emottak.util.toSignalMessage
import org.apache.kafka.clients.producer.RecordMetadata

class MailProcessor(
    private val store: Store,
    private val mailPublisher: MailPublisher,
    private val payloadRepository: PayloadRepository,
    private val eventLoggingService: ScopedEventLoggingService,
    private val mailSender: MailSender
) {
    fun processMessages(scope: CoroutineScope) =
        readMessages()
            .onEach(::processMessage)
            .flowOn(Dispatchers.IO)
            .launchIn(scope)

    private fun readMessages(): Flow<EmailMsg> = autoCloseScope {
        val mailReader = install(
            MailReader(
                config().mail,
                store,
                false,
                eventLoggingService
            )
        )
        val messageCount = mailReader.count()
        // val maxBatchSize = max(10, messageCount)

        if (messageCount > 0) {
            log.info("Starting to read $messageCount messages from inbox")
            mailReader.readMailBatches(messageCount)
                .asFlow()
                .also { log.info("Finished reading all messages from inbox") }
        } else {
            log.info("No messages found in inbox")
            emptyFlow()
        }
    }

    private suspend fun processMessage(emailMsg: EmailMsg) {
        when (
            emailMsg.forwardableMimeMessage.forwardingSystem.also {
                log.info("Sending message to ${it.name}. Sender <${emailMsg.headers["From"]}> and subject <${emailMsg.headers["Subject"]}>")
            }
        ) {
            ForwardingSystem.EBMS -> publishToKafka(emailMsg)
            ForwardingSystem.EMOTTAK -> forwardToT1(emailMsg)
            ForwardingSystem.BOTH -> {
                publishToKafka(emailMsg)
                forwardToT1(emailMsg)
            }
        }
    }

    private suspend fun forwardToT1(emailMsg: EmailMsg) {
        // mailSender.forwardMessage(emailMsg)
        mailSender.rawForward(emailMsg.forwardableMimeMessage.forwardableMimeMessage!!)
    }

    private suspend fun publishToKafka(emailMsg: EmailMsg) {
        when (emailMsg.multipart) {
            true -> publishPayloadMessage(emailMsg.toPayloadMessage(emailMsg.requestId))
            false -> publishSignalMessage(emailMsg.toSignalMessage(emailMsg.requestId))
        }
    }

    private suspend fun publishPayloadMessage(payloadMessage: PayloadMessage) {
        with(payloadRepository) {
            fold(
                { insert(payloadMessage.payloads) },
                { log.error("Could not insert payloads: ${payloadMessage.payloads.map { it }}") }
            ) { mailPublisher.publishPayloadMessage(payloadMessage) }
        }
    }

    private suspend fun publishSignalMessage(signalMessage: SignalMessage): Result<RecordMetadata> =
        mailPublisher.publishSignalMessage(signalMessage)
}
