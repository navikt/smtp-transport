package no.nav.emottak.processor

import arrow.core.raise.fold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import no.nav.emottak.Dependencies
import no.nav.emottak.configuration.Config
import no.nav.emottak.log
import no.nav.emottak.model.PayloadMessage
import no.nav.emottak.model.SignalMessage
import no.nav.emottak.publisher.MailPublisher
import no.nav.emottak.repository.PayloadRepository
import no.nav.emottak.smtp.EmailMsg
import no.nav.emottak.smtp.MailReader
import no.nav.emottak.util.toPayloadMessage
import no.nav.emottak.util.toSignalMessage
import java.util.UUID.randomUUID

class MailProcessor(
    private val config: Config,
    private val deps: Dependencies,
    private val mailPublisher: MailPublisher,
    private val payloadRepository: PayloadRepository
) {
    suspend fun processMessages() = coroutineScope {
        readMessages()
            .onEach(::processMessage)
            .flowOn(Dispatchers.IO)
            .launchIn(this)
    }

    private fun readMessages(): Flow<EmailMsg> =
        MailReader(config.mail, deps.store, false).use { reader ->
            val messageCount = reader.count()

            if (messageCount > 0) {
                log.info("Starting to read $messageCount messages from inbox")
                reader.readMailBatches(messageCount)
                    .asFlow()
                    .also { log.info("Finished reading all messages from inbox") }
            } else {
                log.info("No messages found in inbox")
                emptyFlow()
            }
        }

    private suspend fun processMessage(emailMsg: EmailMsg) {
        val messageId = randomUUID()
        when (emailMsg.multipart) {
            true -> publishPayloadMessage(emailMsg.toPayloadMessage(messageId))
            false -> publishSignalMessage(emailMsg.toSignalMessage(messageId))
        }
    }

    private suspend fun publishPayloadMessage(payloadMessage: PayloadMessage) {
        with(payloadRepository) {
            fold(
                block = { insert(payloadMessage.payloads) },
                recover = { log.error("Could not insert payloads: ${payloadMessage.payloads.map { it }}") },
                transform = { mailPublisher.publishPayloadMessage(payloadMessage) }
            )
        }
    }

    private suspend fun publishSignalMessage(signalMessage: SignalMessage) =
        mailPublisher.publishSignalMessage(signalMessage)
}
