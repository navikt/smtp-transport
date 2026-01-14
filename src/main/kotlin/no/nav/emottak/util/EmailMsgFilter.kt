package no.nav.emottak.util

import no.nav.emottak.config
import no.nav.emottak.log

fun Map<String, String>.filterMimeMessage(): ForwardingSystem {
    val from = this["From"]?.extractEmailAddressOnly() ?: "".also { log.warn("No [From] header found") }
    val subject = this["Subject"] ?: "".also { log.warn("No [Subject] header found") }

    if (from.isNotBlank() && subject.isNotBlank()) {
        if (isFromAcceptedAddress(from)) {
            if (isSignalMessage(subject)) return ForwardingSystem.BOTH
            if (isAcceptedType(subject)) return ForwardingSystem.EBMS
            if (config().clusterName.value == "dev-fss") return ForwardingSystem.EBMS
        }
    }
    return ForwardingSystem.EMOTTAK
}

fun String.extractEmailAddressOnly() = if (this.contains("<")) this.substringAfter("<").substringBefore(">").lowercase() else this.lowercase()

private fun isAcceptedType(subject: String) = config().ebmsFilter.ebmsMessageTypeSubjects.any { subject.contains(it, ignoreCase = true) }
private fun isSignalMessage(subject: String) = config().ebmsFilter.signalMessageTypeSubjects.any { subject.contains(it, ignoreCase = true) }
private fun isFromAcceptedAddress(from: String) = config().ebmsFilter.senderAddresses.any { it.equals(from, ignoreCase = true) }

enum class ForwardingSystem {
    EBMS, EMOTTAK, BOTH
}
