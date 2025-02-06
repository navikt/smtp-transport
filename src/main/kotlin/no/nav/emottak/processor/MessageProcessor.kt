package no.nav.emottak.processor

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import no.nav.emottak.log
import no.nav.emottak.model.SignalMessage
import no.nav.emottak.receiver.SignalReceiver

class MessageProcessor(
    private val signalReceiver: SignalReceiver
) {
    suspend fun processSignalMessages() = coroutineScope {
        signalReceiver
            .receiveSignalMessages()
            .onEach(::processSignalMessage)
            .launchIn(this)
    }

    private fun processSignalMessage(signalMessage: SignalMessage) {
        log.info("Processed signal message with reference id: ${signalMessage.messageId}")
    }
}
