package no.nav.emottak.repository

import arrow.core.Either
import arrow.core.NonEmptyList
import no.nav.emottak.PayloadRequestValidationError
import org.junit.jupiter.api.BeforeAll
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PayloadRequestTest {

    companion object {
        private lateinit var uuid1: String
        private lateinit var uuid2: String

        @JvmStatic
        @BeforeAll
        fun setUpAll() {
            uuid1 = UUID.randomUUID().toString()
            uuid2 = UUID.randomUUID().toString()
        }
    }

    @Test
    fun `Kun referenceId - gyldig UUID`() {
        val request: Either<NonEmptyList<PayloadRequestValidationError>, PayloadRequest> =
            PayloadRequest(uuid1)
        assertTrue(request.isRight())
    }

    @Test
    fun `Kun referenceId - ugyldig UUID`() {
        val request: Either<NonEmptyList<PayloadRequestValidationError>, PayloadRequest> =
            PayloadRequest("referenceId")
        assertTrue(request.isLeft())
        val list: NonEmptyList<PayloadRequestValidationError>? = request.leftOrNull()
        assertNotNull(list)
        assertTrue(list.size == 1)
        assertEquals("ReferenceId is not a valid UUID: 'referenceId'", list[0].toString())
    }

    @Test
    fun `Kun referenceId - som blank`() {
        val request: Either<NonEmptyList<PayloadRequestValidationError>, PayloadRequest> =
            PayloadRequest("")
        assertTrue(request.isLeft())
        val list: NonEmptyList<PayloadRequestValidationError>? = request.leftOrNull()
        assertNotNull(list)
        assertTrue(list.size == 1)
        assertEquals("ReferenceId cannot be empty", list[0].toString())
    }

    @Test
    fun `Både referenceId og contentId - begge gyldig UUID`() {
        val request: Either<NonEmptyList<PayloadRequestValidationError>, PayloadRequest> =
            PayloadRequest(uuid1, uuid2)
        assertTrue(request.isRight())
    }

    @Test
    fun `Både referenceId og contentId - begge ugyldig UUID`() {
        val request: Either<NonEmptyList<PayloadRequestValidationError>, PayloadRequest> =
            PayloadRequest("referenceId", "contentId")
        assertTrue(request.isLeft())
        val list: NonEmptyList<PayloadRequestValidationError>? = request.leftOrNull()
        assertNotNull(list)
        assertTrue(list.size == 1)
        assertEquals("ReferenceId is not a valid UUID: 'referenceId'", list[0].toString())
    }

    @Test
    fun `Både referenceId og contentId - referenceId ugyldig UUID`() {
        val request: Either<NonEmptyList<PayloadRequestValidationError>, PayloadRequest> =
            PayloadRequest("referenceId", uuid2)
        assertTrue(request.isLeft())
        val list: NonEmptyList<PayloadRequestValidationError>? = request.leftOrNull()
        assertNotNull(list)
        assertTrue(list.size == 1)
        assertEquals("ReferenceId is not a valid UUID: 'referenceId'", list[0].toString())
    }

    @Test
    fun `Både referenceId og contentId - contentId ugyldig UUID`() {
        val request: Either<NonEmptyList<PayloadRequestValidationError>, PayloadRequest> =
            PayloadRequest(uuid1, "contentId")
        assertTrue(request.isRight()) // ContentId trenger ikke være UUID
    }

    @Test
    fun `Både referenceId og contentId - referenceId ugyldig UUID, contentId kun spaces`() {
        val request: Either<NonEmptyList<PayloadRequestValidationError>, PayloadRequest> =
            PayloadRequest("referenceId", "   ")
        assertTrue(request.isLeft())
        val list: NonEmptyList<PayloadRequestValidationError>? = request.leftOrNull()
        assertNotNull(list)
        assertTrue(list.size == 2)
        assertEquals("ReferenceId is not a valid UUID: 'referenceId'", list[0].toString())
        assertEquals("ContentId cannot be empty", list[1].toString())
    }
}
