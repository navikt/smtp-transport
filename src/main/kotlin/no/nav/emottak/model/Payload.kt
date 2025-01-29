package no.nav.emottak.model

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Serializable
@ExperimentalUuidApi
data class Payload(
    @Contextual
    val referenceId: Uuid,
    val contentId: String,
    val contentType: String,
    val content: ByteArray
)
