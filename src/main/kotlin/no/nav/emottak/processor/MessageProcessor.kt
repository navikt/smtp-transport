package no.nav.emottak.processor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import no.nav.emottak.log
import no.nav.emottak.model.SignalMessage
import no.nav.emottak.receiver.SignalReceiver

class MessageProcessor(
    private val signalReceiver: SignalReceiver
) {
    fun processSignalMessages(scope: CoroutineScope) =
        signalReceiver
            .receiveSignalMessages()
            .onEach(::processSignalMessage)
            .flowOn(Dispatchers.IO)
            .launchIn(scope)
}

private fun processSignalMessage(signalMessage: SignalMessage) {
    log.info("Processed signal message with reference id: ${signalMessage.messageId}")
}
