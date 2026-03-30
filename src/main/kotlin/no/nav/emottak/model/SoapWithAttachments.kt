package no.nav.emottak.model

import kotlinx.serialization.Serializable

@Serializable
data class SoapWithAttachments(
    val envelope: ByteArray,
    val attachment: ByteArray? = null
) {
    companion object {
        const val MESSAGE_FORMAT_HEADER = "messageFormat"
        const val MESSAGE_FORMAT_VALUE = "SoapWithAttachments"
    }
}
