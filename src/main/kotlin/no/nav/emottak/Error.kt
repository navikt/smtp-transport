package no.nav.emottak

import kotlin.Error

sealed class Error {
    data class PayloadAlreadyExist(
        val referenceId: String,
        val contentId: String
    ) : Error()
}
