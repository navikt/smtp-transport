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
import no.nav.emottak.util.ScopedEventLoggingService
import no.nav.emottak.util.toPayloadMessage
import no.nav.emottak.util.toSignalMessage
import kotlin.uuid.Uuid

class MailProcessor(
    private val store: Store,
    private val mailPublisher: MailPublisher,
    private val payloadRepository: PayloadRepository,
    private val eventLoggingService: ScopedEventLoggingService
) {
    fun processMessages(scope: CoroutineScope) =
        readMessages()
            .onEach(::processMessage)
            .flowOn(Dispatchers.IO)
            .launchIn(scope)

    private fun readMessages(): Flow<EmailMsg> = autoCloseScope {
        val mailReader = install(MailReader(config().mail, store, false, eventLoggingService))
        val messageCount = mailReader.count()

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
        val messageId = Uuid.random()
        when (emailMsg.multipart) {
            true -> publishPayloadMessage(emailMsg.toPayloadMessage(messageId))
            false -> publishSignalMessage(emailMsg.toSignalMessage(messageId))
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

    private suspend fun publishSignalMessage(signalMessage: SignalMessage) =
        mailPublisher.publishSignalMessage(signalMessage)
}
