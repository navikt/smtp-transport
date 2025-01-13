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
import no.nav.emottak.payloadDatabase
import no.nav.emottak.runMigrations
import no.nav.emottak.util.Payload

class PayloadRepositorySpec : StringSpec(
    {
        val repository = PayloadRepository(payloadDatabase())

        beforeSpec { runMigrations() }

        "should insert single payload" {
            val payloads = createSinglePayload()

            with(repository) {
                either { insert(payloads) } shouldBe Right(
                    listOf(Pair("reference-id", "content-id"))
                )
            }
        }

        "should insert multiple payloads" {
            val payloads = createMultiplePayloads()

            with(repository) {
                either { insert(payloads) } shouldBe Right(
                    listOf(
                        Pair("first-reference-id", "first-content-id"),
                        Pair("second-reference-id", "second-content-id")
                    )
                )
            }
        }

        "should insert and retrieve a single payload" {
            val payload = Payload(
                "ref",
                "cont",
                "t",
                "d".toByteArray()
            )

            with(repository) {
                either { insert(listOf(payload)) }

                val eitherPayload = either { retrieve("ref", "cont") }
                val retrievedPayload = eitherPayload.shouldBeRight()

                retrievedPayload shouldBeEqual payload
            }
        }

        "should insert and retrieve multiple payloads" {
            val firstPayload = Payload(
                "r",
                "c1",
                "t",
                "d".toByteArray()
            )
            val secondPayload = Payload(
                "r",
                "c2",
                "t",
                "d".toByteArray()
            )

            with(repository) {
                either { insert(listOf(firstPayload, secondPayload)) }

                val eitherPayload = either { retrieve("r") }
                val retrievedPayloads = eitherPayload.shouldBeRight()

                retrievedPayloads shouldHaveSize 2
                retrievedPayloads.first() shouldBeEqual firstPayload
                retrievedPayloads.last() shouldBeEqual secondPayload
            }
        }

        "should fail on non existing payload" {
            with(repository) {
                either { retrieve("no-ref-id", "no-content-id") } shouldBe Left(
                    PayloadDoesNotExist(
                        "no-ref-id",
                        "no-content-id"
                    )
                )
            }
        }

        "should fail on non existing payloads" {
            with(repository) {
                either { retrieve("no-ref-id") } shouldBe Left(
                    PayloadDoesNotExist("no-ref-id")
                )
            }
        }

        "should fail on duplicate payload" {
            val payloads = createDuplicatePayloads()

            with(repository) {
                either { insert(payloads) } shouldBe Left(
                    PayloadAlreadyExist(
                        "duplicate-reference-id",
                        "duplicate-content-id"
                    )
                )
            }
        }
    }
)

private fun createSinglePayload() = listOf(
    Payload(
        "reference-id",
        "content-id",
        "text",
        "data".toByteArray()
    )
)

private fun createMultiplePayloads() = listOf(
    Payload(
        "first-reference-id",
        "first-content-id",
        "text",
        "first".toByteArray()
    ),
    Payload(
        "second-reference-id",
        "second-content-id",
        "text",
        "second".toByteArray()
    )
)

private fun createDuplicatePayloads() = listOf(
    Payload(
        "duplicate-reference-id",
        "duplicate-content-id",
        "text",
        "duplicate".toByteArray()
    ),
    Payload(
        "duplicate-reference-id",
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
