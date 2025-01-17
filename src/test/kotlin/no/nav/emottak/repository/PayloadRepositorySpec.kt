package no.nav.emottak.repository

import arrow.core.Either.Left
import arrow.core.Either.Right
import arrow.core.raise.either
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.emottak.PayloadAlreadyExist
import no.nav.emottak.PayloadDoesNotExist
import no.nav.emottak.model.Payload
import no.nav.emottak.payloadDatabase
import no.nav.emottak.runMigrations
import java.util.UUID

class PayloadRepositorySpec : StringSpec(
    {
        val repository = PayloadRepository(payloadDatabase())

        beforeSpec { runMigrations() }

        "should insert single payload" {
            val referenceId = UUID.randomUUID()
            val payloads = createSinglePayload(referenceId)

            with(repository) {
                either { insert(payloads) } shouldBe Right(
                    listOf(Pair(referenceId.toString(), "content-id"))
                )
            }
        }

        "should insert multiple payloads" {
            val referenceIds = listOf(UUID.randomUUID(), UUID.randomUUID())
            val payloads = createMultiplePayloads(referenceIds)

            with(repository) {
                either { insert(payloads) } shouldBe Right(
                    listOf(
                        Pair(referenceIds.first().toString(), "first-content-id"),
                        Pair(referenceIds.last().toString(), "second-content-id")
                    )
                )
            }
        }

        "should insert and retrieve a single payload" {
            val referenceId = UUID.randomUUID()
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
            val referenceId = UUID.randomUUID()
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
            val nonExistingReferenceId = UUID.randomUUID()
            with(repository) {
                either { retrieve(nonExistingReferenceId, "no-content-id") } shouldBe Left(
                    PayloadDoesNotExist(
                        nonExistingReferenceId.toString(),
                        "no-content-id"
                    )
                )
            }
        }

        "should fail on non existing payloads" {
            val nonExistingReferenceId = UUID.randomUUID()
            with(repository) {
                either { retrieve(nonExistingReferenceId) } shouldBe Left(
                    PayloadDoesNotExist(nonExistingReferenceId.toString())
                )
            }
        }

        "should fail on duplicate payload" {
            val referenceId = UUID.randomUUID()
            val payloads = createDuplicatePayloads(referenceId)

            with(repository) {
                either { insert(payloads) } shouldBe Left(
                    PayloadAlreadyExist(
                        referenceId.toString(),
                        "duplicate-content-id"
                    )
                )
            }
        }
    }
)

private fun createSinglePayload(referenceId: UUID) = listOf(
    Payload(
        referenceId,
        "content-id",
        "text",
        "data".toByteArray()
    )
)

private fun createMultiplePayloads(referenceIds: List<UUID>) = listOf(
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

private fun createDuplicatePayloads(referenceId: UUID) =
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
