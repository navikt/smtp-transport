package no.nav.emottak.repository

import arrow.core.Either.Left
import arrow.core.Either.Right
import arrow.core.raise.either
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.emottak.PayloadAlreadyExists
import no.nav.emottak.PayloadNotFound
import no.nav.emottak.model.Payload
import no.nav.emottak.payloadDatabase
import no.nav.emottak.runMigrations
import no.nav.emottak.util.fakeEventLoggingService
import kotlin.uuid.Uuid

class PayloadRepositorySpec : StringSpec(
    {
        val repository = PayloadRepository(
            payloadDatabase(),
            fakeEventLoggingService()
        )

        beforeSpec { runMigrations() }

        "should insert single payload" {
            val referenceId = Uuid.random()
            val payloads = createSinglePayload(referenceId)

            with(repository) {
                either { insert(payloads) } shouldBe Right(
                    listOf(Pair(referenceId, "content-id"))
                )
            }
        }

        "should insert multiple payloads" {
            val referenceIds = listOf(Uuid.random(), Uuid.random())
            val payloads = createMultiplePayloads(referenceIds)

            with(repository) {
                either { insert(payloads) } shouldBe Right(
                    listOf(
                        Pair(referenceIds.first(), "first-content-id"),
                        Pair(referenceIds.last(), "second-content-id")
                    )
                )
            }
        }

        "should insert and retrieve a single payload" {
            val referenceId = Uuid.random()
            val payload = Payload(
                referenceId,
                "cont",
                "t",
                "d".toByteArray()
            )

            with(repository) {
                either { insert(listOf(payload)) }

                val eitherPayload = either { retrieve(referenceId, "cont") }
                val retrievedPayload = eitherPayload.shouldBeRight()

                retrievedPayload shouldBeEqual payload
            }
        }

        "should insert and retrieve multiple payloads" {
            val referenceId = Uuid.random()
            val firstPayload = Payload(
                referenceId,
                "c1",
                "t",
                "d".toByteArray()
            )
            val secondPayload = Payload(
                referenceId,
                "c2",
                "t",
                "d".toByteArray()
            )

            with(repository) {
                either { insert(listOf(firstPayload, secondPayload)) }

                val eitherPayload = either { retrieve(referenceId) }
                val retrievedPayloads = eitherPayload.shouldBeRight()

                retrievedPayloads shouldHaveSize 2
                retrievedPayloads.first() shouldBeEqual firstPayload
                retrievedPayloads.last() shouldBeEqual secondPayload
            }
        }

        "should fail on non existing payload" {
            val nonExistingReferenceId = Uuid.random()
            with(repository) {
                either { retrieve(nonExistingReferenceId) } shouldBe Left(
                    PayloadNotFound(nonExistingReferenceId.toString())
                )
            }
        }

        "should fail on non existing payloads" {
            val nonExistingReferenceId = Uuid.random()
            with(repository) {
                either { retrieve(nonExistingReferenceId) } shouldBe Left(
                    PayloadNotFound(nonExistingReferenceId.toString())
                )
            }
        }

        "should fail on duplicate payload" {
            val referenceId = Uuid.random()
            val payloads = createDuplicatePayloads(referenceId)

            with(repository) {
                either { insert(payloads) } shouldBe Left(
                    PayloadAlreadyExists(
                        referenceId.toString(),
                        "duplicate-content-id"
                    )
                )
            }
        }
    }
)

private fun createSinglePayload(referenceId: Uuid) = listOf(
    Payload(
        referenceId,
        "content-id",
        "text",
        "data".toByteArray()
    )
)

private fun createMultiplePayloads(referenceIds: List<Uuid>) = listOf(
    Payload(
        referenceIds.first(),
        "first-content-id",
        "text",
        "first".toByteArray()
    ),
    Payload(
        referenceIds.last(),
        "second-content-id",
        "text",
        "second".toByteArray()
    )
)

private fun createDuplicatePayloads(referenceId: Uuid) =
    listOf(
        Payload(
            referenceId,
            "duplicate-content-id",
            "text",
            "duplicate".toByteArray()
        ),
        Payload(
            referenceId,
            "duplicate-content-id",
            "text",
            "duplicate".toByteArray()
        )
    )

private infix fun Payload.shouldBeEqual(payload: Payload) {
    payload.referenceId shouldBe referenceId
    payload.contentId shouldBe contentId
    payload.contentType shouldBe contentType
    payload.content shouldBe content
}
