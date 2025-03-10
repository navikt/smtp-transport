package no.nav.emottak.model

class MailRoutingSignalMessage(
    val mailMetadata: MailMetadata,
    val signalMessage: SignalMessage
) : MailRoutingMessage
