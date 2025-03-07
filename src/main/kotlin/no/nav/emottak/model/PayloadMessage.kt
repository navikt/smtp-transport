package no.nav.emottak.model

import kotlin.uuid.Uuid

data class PayloadMessage(
    val messageId: Uuid,
    val envelope: ByteArray,
    val payloads: List<Payload>
) : MailRoutingMessage
