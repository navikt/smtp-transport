package no.nav.emottak.processor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import no.nav.emottak.log
import no.nav.emottak.model.MailMetadata
import no.nav.emottak.model.MailRoutingMessage
import no.nav.emottak.model.MailRoutingPayloadMessage
import no.nav.emottak.model.MailRoutingSignalMessage
import no.nav.emottak.model.PayloadMessage
import no.nav.emottak.model.SignalMessage
import no.nav.emottak.receiver.PayloadReceiver
import no.nav.emottak.receiver.SignalReceiver
import no.nav.emottak.smtp.MailSender

class MessageProcessor(
    private val payloadReceiver: PayloadReceiver,
    private val signalReceiver: SignalReceiver,
    private val mailSender: MailSender
) {
    fun processMailRoutingMessages(scope: CoroutineScope) =
        mailRoutingMessageFlow()
            .onEach { message -> processAndSendMessage(scope, message) }
            .flowOn(Dispatchers.IO)
            .launchIn(scope)

    private fun mailRoutingMessageFlow(): Flow<MailRoutingMessage> =
        merge(
            payloadReceiver.receiveMailRoutingMessages(),
            signalReceiver.receiveMailRoutingMessages()
        )

    private fun processAndSendMessage(scope: CoroutineScope, message: MailRoutingMessage) {
        when (message) {
            is MailRoutingSignalMessage -> processAndSendSignalMessage(
                scope,
                message.mailMetadata,
                message.signalMessage
            )

            is MailRoutingPayloadMessage -> processAndSendPayloadMessage(
                scope,
                message.mailMetadata,
                message.payloadMessage
            )
        }
    }

    private fun processAndSendSignalMessage(
        scope: CoroutineScope,
        mailMetadata: MailMetadata,
        signalMessage: SignalMessage
    ) = scope.launch {
        mailSender.sendSignalMessage(mailMetadata, signalMessage)
    }
        .also { log.info("Processed and sent signal message with reference id: ${signalMessage.messageId}") }

    private fun processAndSendPayloadMessage(
        scope: CoroutineScope,
        mailMetadata: MailMetadata,
        payloadMessage: PayloadMessage
    ) = scope.launch {
        mailSender.sendPayloadMessage(mailMetadata, payloadMessage)
    }
        .also { log.info("Processed and sent payload message with reference id: ${payloadMessage.messageId}") }
}
