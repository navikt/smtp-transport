package no.nav.emottak

sealed interface Error
data class PayloadAlreadyExist(
    val referenceId: String,
    val contentId: String
) : Error

data class PayloadDoesNotExist(
    val referenceId: String,
    val contentId: String? = null
) : Error
