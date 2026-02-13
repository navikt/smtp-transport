package no.nav.emottak.util

import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import no.nav.emottak.smtp.EmailMsg
import no.nav.emottak.smtp.MimeMessageWrapper
import no.nav.emottak.smtp.Part

internal fun MimeMessageWrapper.mapEmailMsg(): EmailMsg {
    val multipartMessage = this.mimeMessage.isMimeMultipart()
    val bodyparts: List<Part> = when (multipartMessage) {
        true -> createMimeBodyParts(this.mimeMessage.content as MimeMultipart)
        else -> createEmptyMimeBodyParts(this.mimeMessage)
    }
    val headers: Map<String, String> = this.mimeMessage.allHeaders
        .toList()
        .groupBy({ it.name }, { it.value })
        .mapValues { it.value.joinToString(",") }
    return EmailMsg(
        multipart = multipartMessage,
        headers = headers,
        parts = bodyparts,
        requestId = this.requestId,
        originalMimeMessage = this.mimeMessage,
        senderAddress = headers.getOrDefault("From", "").extractEmailAddressOnly()
    )
}

fun String.extractEmailAddressOnly() = if (this.contains("<")) this.substringAfter("<").substringBefore(">").lowercase() else this.lowercase()

private fun MimeMessage.isMimeMultipart(): Boolean = content is MimeMultipart

private fun createEmptyMimeBodyParts(message: MimeMessage) = listOf(
    Part(
        emptyMap(),
        message.inputStream.readAllBytes()
    )
)

private fun createMimeBodyParts(messageContent: MimeMultipart) = mutableListOf<MimeBodyPart>()
    .apply {
        for (i in 0 until messageContent.count) {
            add(messageContent.getBodyPart(i) as MimeBodyPart)
        }
    }
    .map(mapBodyPart())

private fun mapBodyPart(): (MimeBodyPart) -> Part = { bodyPart ->
    Part(
        bodyPart.allHeaders
            .toList()
            .groupBy({ it.name }, { it.value })
            .mapValues { it.value.joinToString(",") },
        bodyPart.inputStream.readAllBytes()
    )
}
