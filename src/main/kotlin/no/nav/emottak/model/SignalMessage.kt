package no.nav.emottak.model

import java.util.UUID

data class SignalMessage(
    val messageId: UUID,
    val envelope: ByteArray
)
