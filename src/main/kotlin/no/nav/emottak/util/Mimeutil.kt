package no.nav.emottak.util

import no.nav.emottak.model.Payload
import no.nav.emottak.model.PayloadMessage
import no.nav.emottak.model.SignalMessage
import no.nav.emottak.smtp.EmailMsg
import no.nav.emottak.smtp.Part
import java.util.UUID

private const val CONTENT_ID = "Content-Id"
private const val CONTENT_TYPE = "Content-Type"

fun EmailMsg.toSignalMessage(messageId: UUID): SignalMessage = SignalMessage(
    messageId,
    getEnvelope()
)

fun EmailMsg.toPayloadMessage(messageId: UUID): PayloadMessage = PayloadMessage(
    messageId,
    getEnvelope(),
    getPayloads(messageId)
)

private fun EmailMsg.getEnvelope() = parts
    .first()
    .bytes

private fun EmailMsg.getPayloads(messageId: UUID) = parts
    // drop the envelope
    .drop(1)
    .map { it.toPayload(messageId) }

private fun Part.getContentId() = getContent(CONTENT_ID)

private fun Part.getContentType() = getContent(CONTENT_TYPE)

private fun Part.getContent(type: String) = headers
    .entries
    .first { (key, _) -> key.equals(type, ignoreCase = true) }
    .value
    .stripAngleBrackets()

private fun Part.toPayload(referenceId: UUID) = Payload(
    referenceId,
    getContentId(),
    getContentType(),
    bytes
)

private fun String.stripAngleBrackets() = removePrefix("<").removeSuffix(">")
