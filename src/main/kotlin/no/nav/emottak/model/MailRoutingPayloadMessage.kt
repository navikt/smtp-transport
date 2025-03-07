package no.nav.emottak.model

data class MailRoutingPayloadMessage(
    val mailMetadata: MailMetadata,
    val payloadMessage: PayloadMessage
) : MailRoutingMessage
