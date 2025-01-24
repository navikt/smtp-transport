package no.nav.emottak.repository

import arrow.core.Either
import arrow.core.NonEmptyList
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.emottak.PayloadRequestValidationError
import java.util.UUID

// Krever kotest-plugin installert i IntelliJ for å kjøre
class PayloadRequestTest : StringSpec(
    {
        lateinit var uuid1: String
        lateinit var uuid2: String

        beforeSpec {
            uuid1 = UUID.randomUUID().toString()
            uuid2 = UUID.randomUUID().toString()
        }

        "Kun referenceId - gyldig UUID" {
            val request: Either<NonEmptyList<PayloadRequestValidationError>, PayloadRequest> =
                PayloadRequest(uuid1)
            request.isRight() shouldBe true
        }

        "Kun referenceId - ugyldig UUID" {
            val request: Either<NonEmptyList<PayloadRequestValidationError>, PayloadRequest> =
                PayloadRequest("referenceId")
            request.isLeft() shouldBe true
            val list: NonEmptyList<PayloadRequestValidationError>? = request.leftOrNull()
            list.shouldNotBeNull()
            list.size shouldBe 1
            list[0].toString() shouldBe "ReferenceId is not a valid UUID: 'referenceId'."
        }

        "Kun referenceId - som blank" {
            val request: Either<NonEmptyList<PayloadRequestValidationError>, PayloadRequest> =
                PayloadRequest("")
            request.isLeft() shouldBe true
            val list: NonEmptyList<PayloadRequestValidationError>? = request.leftOrNull()
            list.shouldNotBeNull()
            list.size shouldBe 1
            list[0].toString() shouldBe "ReferenceId cannot be empty."
        }

        "Både referenceId og contentId - begge gyldig UUID" {
            val request: Either<NonEmptyList<PayloadRequestValidationError>, PayloadRequest> =
                PayloadRequest(uuid1, uuid2)
            request.isRight() shouldBe true
        }

        "Både referenceId og contentId - begge ugyldig UUID" {
            val request: Either<NonEmptyList<PayloadRequestValidationError>, PayloadRequest> =
                PayloadRequest("referenceId", "contentId")
            request.isLeft() shouldBe true
            val list: NonEmptyList<PayloadRequestValidationError>? = request.leftOrNull()
            list.shouldNotBeNull()
            list.size shouldBe 1
            list[0].toString() shouldBe "ReferenceId is not a valid UUID: 'referenceId'."
        }

        "Både referenceId og contentId - referenceId ugyldig UUID" {
            val request: Either<NonEmptyList<PayloadRequestValidationError>, PayloadRequest> =
                PayloadRequest("referenceId", uuid2)
            request.isLeft() shouldBe true
            val list: NonEmptyList<PayloadRequestValidationError>? = request.leftOrNull()
            list.shouldNotBeNull()
            list.size shouldBe 1
            list[0].toString() shouldBe "ReferenceId is not a valid UUID: 'referenceId'."
        }

        "Både referenceId og contentId - contentId ugyldig UUID"() {
            val request: Either<NonEmptyList<PayloadRequestValidationError>, PayloadRequest> =
                PayloadRequest(uuid1, "contentId")
            request.isRight() shouldBe true // ContentId trenger ikke være UUID
        }

        "Både referenceId og contentId - referenceId ugyldig UUID, contentId kun spaces"() {
            val request: Either<NonEmptyList<PayloadRequestValidationError>, PayloadRequest> =
                PayloadRequest("referenceId", "   ")
            request.isLeft() shouldBe true
            val list: NonEmptyList<PayloadRequestValidationError>? = request.leftOrNull()
            list.shouldNotBeNull()
            list.size shouldBe 2
            list[0].toString() shouldBe "ReferenceId is not a valid UUID: 'referenceId'."
            list[1].toString() shouldBe "ContentId cannot be empty."
        }
    }
)
