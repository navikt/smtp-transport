package no.nav.emottak.util

import no.nav.emottak.smtp.EmailMsg
import no.nav.emottak.smtp.Part
import java.util.UUID

private const val CONTENT_ID = "Content-Id"
private const val CONTENT_TYPE = "Content-Type"

data class Payload(
    val referenceId: String,
    val contentId: String,
    val contentType: String,
    val content: ByteArray
)

fun EmailMsg.getContent() = parts.first().bytes

fun EmailMsg.toPayloads(referenceId: UUID) = parts.map { it.toPayload(referenceId) }

private fun Part.getContentId() = "${headers[CONTENT_ID]}"

private fun Part.getContentType() = "${headers[CONTENT_TYPE]}"

private fun Part.toPayload(referenceId: UUID) = Payload(
    referenceId.toString(),
    getContentId(),
    getContentType(),
    bytes
)
