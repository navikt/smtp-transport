package no.nav.emottak

sealed interface PayloadError

data class PayloadAlreadyExist(val referenceId: String, val contentId: String) : PayloadError
data class PayloadDoesNotExist(val referenceId: String) : PayloadError

sealed interface PayloadRequestError : PayloadError

data object ReferenceIdMissing : PayloadRequestError
data object EmptyReferenceId : PayloadRequestError
data class InvalidReferenceId(val referenceId: String) : PayloadRequestError
