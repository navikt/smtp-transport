package no.nav.emottak.model

import java.util.UUID

data class Payload(
    val referenceId: UUID,
    val contentId: String,
    val contentType: String,
    val content: ByteArray
)
