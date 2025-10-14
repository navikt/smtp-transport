package no.nav.emottak.util

import no.nav.emottak.config
import no.nav.emottak.smtp.EmailMsg

fun EmailMsg.filterMimeMessage(): ForwardingSystem {
    val from = this.headers["From"] ?: ""
    val subject = this.headers["Subject"] ?: ""
    return if (checkFromAddress(from)) {
        if (checkSignalMessage(subject)) {
            ForwardingSystem.BOTH
        } else if (checkMessageType(subject)) {
            ForwardingSystem.EBMS
        } else {
            ForwardingSystem.EMOTTAK
        }
    } else {
        ForwardingSystem.EMOTTAK
    }
}

private fun checkMessageType(subject: String) = config().ebmsFilter.ebmsMessageTypeSubjects.contains(subject.lowercase())
private fun checkSignalMessage(subject: String) = config().ebmsFilter.signalMessageTypeSubjects.contains(subject.lowercase())
private fun checkFromAddress(from: String) = config().ebmsFilter.ebmsSenderAddresses.contains(from.lowercase())

enum class ForwardingSystem {
    EBMS, EMOTTAK, BOTH
}
