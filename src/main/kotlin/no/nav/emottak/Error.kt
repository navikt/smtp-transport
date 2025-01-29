package no.nav.emottak

sealed interface PayloadError

sealed interface CreatePayloadError : PayloadError

data class PayloadAlreadyExists(val referenceId: String, val contentId: String) : CreatePayloadError

sealed interface RetrievePayloadError : PayloadError

data object ReferenceIdMissing : RetrievePayloadError
data object ReferenceIdEmpty : RetrievePayloadError
data class InvalidReferenceId(val referenceId: String) : RetrievePayloadError
data class PayloadNotFound(val referenceId: String) : RetrievePayloadError
