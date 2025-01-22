package no.nav.emottak.repository

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import arrow.core.raise.zipOrAccumulate
import no.nav.emottak.EmptyContentId
import no.nav.emottak.EmptyReferenceId
import no.nav.emottak.InvalidReferenceId
import no.nav.emottak.PayloadRequestValidationError
import java.util.UUID

data class PayloadRequest private constructor(val referenceId: UUID, val contentId: String? = null) {
    companion object {
        operator fun invoke(
            referenceId: String?,
            contentId: String? = null
        ): Either<NonEmptyList<PayloadRequestValidationError>, PayloadRequest> = either {
            zipOrAccumulate(
                { // Validering av referenceId:
                    ensureNotNull(referenceId) { EmptyReferenceId }
                    ensure(referenceId.isNotBlank()) { EmptyReferenceId }
                    ensure(referenceId.isValidUuid()) { InvalidReferenceId(referenceId) }
                },
                { // Validering av contentId hvis den ikke er null:
                    if (contentId != null) {
                        ensure(contentId.isNotBlank()) { EmptyContentId }
                    }
                }
            ) { _, _ -> }
            PayloadRequest(UUID.fromString(referenceId!!), contentId)
        }
    }
}

fun String.isValidUuid(): Boolean {
    try {
        UUID.fromString(this)
    } catch (ex: IllegalArgumentException) { return false }
    return true
}
