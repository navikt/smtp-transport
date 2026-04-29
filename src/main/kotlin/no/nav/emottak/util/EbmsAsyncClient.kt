package no.nav.emottak.util

import arrow.core.raise.Raise
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.emottak.InvalidReferenceId
import no.nav.emottak.PayloadError
import no.nav.emottak.PayloadNotFound
import no.nav.emottak.UnauthorizedError
import no.nav.emottak.UnknownError
import no.nav.emottak.model.Payload
import kotlin.uuid.Uuid

class EbmsAsyncClient(
    private val httpClient: HttpClient
) {
    suspend fun Raise<PayloadError>.getPayloads(referenceId: Uuid): List<Payload> {
        val httpResponse = retryGetPayload(referenceId)
        return when (httpResponse.status) {
            HttpStatusCode.OK -> httpResponse.body<List<Payload>>()
            HttpStatusCode.NotFound -> raise(PayloadNotFound(referenceId.toString()))
            HttpStatusCode.BadRequest -> raise(InvalidReferenceId(referenceId.toString()))
            HttpStatusCode.Unauthorized -> raise(UnauthorizedError)
            else -> raise(UnknownError(httpResponse.bodyAsText()))
        }
    }

    suspend fun retryGetPayload(referenceId: Uuid): HttpResponse {
        val attemptLimit = 30
        val delayMillis = 5000L
        var attempt = 0
        while (attemptLimit > attempt) {
            try {
                return httpClient.get("$referenceId")
            } catch (e: Exception) {
                withContext(Dispatchers.IO) {
                    Thread.sleep(delayMillis)
                }
            }
            attempt++
        }
        throw Exception("Failed to get payload after $attemptLimit attempts")
    }
}
