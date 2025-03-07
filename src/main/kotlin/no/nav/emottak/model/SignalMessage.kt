package no.nav.emottak.model

import kotlin.uuid.Uuid

data class SignalMessage(
    val messageId: Uuid,
    val envelope: ByteArray
) : MailRoutingMessage
