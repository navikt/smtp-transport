package no.nav.emottak.util

import no.nav.emottak.config
import no.nav.emottak.log
import no.nav.emottak.smtp.EmailMsg

fun EmailMsg.filterMimeMessage(): ForwardingSystem {
    val from = this.headers["From"]?.extractEmailAddressOnly() ?: "".also { log.warn("No [From] header found") }
    val subject = this.headers["Subject"] ?: "".also { log.warn("No [Subject] header found") }

    if (from.isNotBlank() && subject.isNotBlank()) {
        if (isFromAcceptedAddress(from)) {
            if (isSignalMessage(subject)) return ForwardingSystem.BOTH
            if (isAcceptedType(subject)) return ForwardingSystem.EBMS
        }
    }
    return ForwardingSystem.EMOTTAK
}

private fun String.extractEmailAddressOnly() = if (this.contains("<")) this.substringAfter("<").substringBefore(">").lowercase() else this.lowercase()

private fun isAcceptedType(subject: String) = config().ebmsFilter.ebmsMessageTypeSubjects.contains(subject.lowercase())
private fun isSignalMessage(subject: String) = config().ebmsFilter.signalMessageTypeSubjects.contains(subject.lowercase())
private fun isFromAcceptedAddress(from: String) = config().ebmsFilter.ebmsSenderAddresses.contains(from.lowercase())

enum class ForwardingSystem {
    EBMS, EMOTTAK, BOTH
}
