package no.nav.emottak.processor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import no.nav.emottak.log
import no.nav.emottak.model.Message
import no.nav.emottak.model.PayloadMessage
import no.nav.emottak.model.SignalMessage
import no.nav.emottak.receiver.PayloadReceiver
import no.nav.emottak.receiver.SignalReceiver

class MessageProcessor(
    private val payloadReceiver: PayloadReceiver,
    private val signalReceiver: SignalReceiver
) {
    fun processPayloadAndSignalMessages(scope: CoroutineScope) =
        merge(
            payloadReceiver.receivePayloadMessages(),
            signalReceiver.receiveSignalMessages()
        )
            .onEach(::processMessage)
            .flowOn(Dispatchers.IO)
            .launchIn(scope)

    private fun processMessage(message: Message) {
        when (message) {
            is PayloadMessage -> log.info("Processed payload message with reference id: ${message.messageId}")
            is SignalMessage -> log.info("Processed signal message with reference id: ${message.messageId}")
        }
    }
}
