package no.nav.emottak

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.content.TextContent

sealed interface PayloadError

sealed interface CreatePayloadError : PayloadError

data class PayloadAlreadyExists(val referenceId: String, val contentId: String) : CreatePayloadError

sealed interface RetrievePayloadError : PayloadError

data object ReferenceIdMissing : RetrievePayloadError
data object ReferenceIdEmpty : RetrievePayloadError
data class InvalidReferenceId(val referenceId: String) : RetrievePayloadError
data class PayloadNotFound(val referenceId: String) : RetrievePayloadError

fun RetrievePayloadError.toContent(): TextContent =
    when (this) {
        is ReferenceIdMissing ->
            TextContent("Reference id missing", BadRequest)

        is ReferenceIdEmpty ->
            TextContent("Empty reference id", BadRequest)

        is InvalidReferenceId ->
            TextContent("Invalid reference id (${this.referenceId})", BadRequest)

        is PayloadNotFound ->
            TextContent("Payload not found for reference id (${this.referenceId})", NotFound)
    }

private fun TextContent(
    content: String,
    statusCode: HttpStatusCode
): TextContent = TextContent(content, ContentType.Text.Plain, statusCode)
