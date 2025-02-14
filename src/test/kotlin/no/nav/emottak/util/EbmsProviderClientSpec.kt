package no.nav.emottak.util

import arrow.core.raise.either
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headers
import no.nav.emottak.InvalidReferenceId
import no.nav.emottak.PayloadNotFound
import no.nav.emottak.UnauthorizedError
import no.nav.emottak.UnknownError
import no.nav.emottak.config
import no.nav.emottak.httpClient
import kotlin.uuid.Uuid

class EbmsProviderClientSpec : StringSpec({

    val config = config().ebmsProvider

    "Get payloads - retrieve list of single payload" {
        val fakeEngine = getFakeEngine(HttpStatusCode.OK, jsonResponse())
        val client = EbmsProviderClient(httpClient(fakeEngine, config))

        val uuid = Uuid.parse("a86bd780-c345-4be3-876b-fefc4b7a8777")

        with(client) {
            val payloads = either { getPayloads(uuid) }.shouldBeRight()
            payloads shouldHaveSize 1

            val payload = payloads.first()
            payload.referenceId shouldBe uuid
            payload.contentId shouldBe "content"
            payload.contentType shouldBe "contentType"
            payload.content contentEquals "data".toByteArray()
        }
    }

    "Get payloads - fail with payload not found" {
        val fakeEngine = getFakeEngine(HttpStatusCode.NotFound)
        val client = EbmsProviderClient(httpClient(fakeEngine, config))
        val referenceId = Uuid.random()

        with(client) {
            either { getPayloads(referenceId) } shouldBeLeft
                PayloadNotFound(referenceId.toString())
        }
    }

    "Get payloads - fail with invalid reference id" {
        val fakeEngine = getFakeEngine(HttpStatusCode.BadRequest)
        val client = EbmsProviderClient(httpClient(fakeEngine, config))
        val referenceId = Uuid.random()

        with(client) {
            either { getPayloads(referenceId) } shouldBeLeft
                InvalidReferenceId(referenceId.toString())
        }
    }

    "Get payloads - fail with unauthorized" {
        val fakeEngine = getFakeEngine(HttpStatusCode.Unauthorized)
        val client = EbmsProviderClient(httpClient(fakeEngine, config))
        val referenceId = Uuid.random()

        with(client) {
            either { getPayloads(referenceId) } shouldBeLeft
                UnauthorizedError
        }
    }

    "Get payloads - fail with unknown error" {
        val fakeEngine = getFakeEngine(HttpStatusCode.InternalServerError, "unknown error")
        val client = EbmsProviderClient(httpClient(fakeEngine, config))
        val referenceId = Uuid.random()

        with(client) {
            either { getPayloads(referenceId) } shouldBeLeft
                UnknownError("unknown error")
        }
    }
})

private fun getFakeEngine(status: HttpStatusCode, content: String = ""): MockEngine =
    MockEngine { _ ->
        respond(
            content = content,
            status = status,
            headers = headers {
                append(HttpHeaders.ContentType, "application/json")
            }
        )
    }

private fun jsonResponse(): String =
    """
            [
                {
                    "referenceId": "a86bd780-c345-4be3-876b-fefc4b7a8777",
                    "contentId": "content",
                    "contentType": "contentType",
                    "content": [100, 97, 116, 97]
                }
            ]
    """
        .trimIndent()
