package no.nav.emottak.model

import net.logstash.logback.marker.LogstashMarker
import net.logstash.logback.marker.Markers

data class MailMetadata(
    val recipientAddress: String,
    val subject: String = "",
    val senderAddress: String,
    val service: String = "",
    val action: String = ""
) {
    val marker: LogstashMarker = Markers.appendEntries(
        mapOf(
            "smtpSender" to senderAddress,
            "smtpReceiver" to recipientAddress,
            "service" to service,
            "action" to action
        )
    )
}
