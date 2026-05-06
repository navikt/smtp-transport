package no.nav.emottak.model

data class MailMetadata(
    val recipientAddress: String,
    val subject: String = "",
    val senderAddress: String,
    val service: String = "",
    val action: String = ""
)
