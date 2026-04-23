package no.nav.emottak.model

import kotlinx.serialization.Serializable
import java.util.Collections.emptyList

@Serializable
data class SoapWithAttachments(
    val envelope: ByteArray,
    val attachments: List<Attachment> = emptyList()
) {
    companion object {
        const val MESSAGE_FORMAT_HEADER = "messageFormat"
        const val MESSAGE_FORMAT_VALUE = "SoapWithAttachments"
    }
}

@Serializable
data class Attachment(
    val content: ByteArray,
    val contentId: String,
    val contentType: String
)
