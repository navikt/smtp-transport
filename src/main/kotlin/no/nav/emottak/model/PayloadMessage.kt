package no.nav.emottak.model

import java.util.UUID

data class PayloadMessage(
    val messageId: UUID,
    val envelope: ByteArray,
    val payloads: List<Payload>
)
