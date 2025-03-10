package no.nav.emottak.smtp

import arrow.core.raise.catch
import jakarta.mail.Message.RecipientType.TO
import jakarta.mail.MessagingException
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.emottak.log
import no.nav.emottak.model.MailMetadata
import no.nav.emottak.model.MessageType
import no.nav.emottak.model.MessageType.PAYLOAD
import no.nav.emottak.model.MessageType.SIGNAL
import no.nav.emottak.model.PayloadMessage
import no.nav.emottak.model.SignalMessage

class MailSender(private val session: Session) {

    suspend fun sendSignalMessage(metadata: MailMetadata, signalMessage: SignalMessage) =
        sendMessage(
            metadata,
            createMimeMessage(metadata, signalMessage),
            SIGNAL
        )

    suspend fun sendPayloadMessage(metadata: MailMetadata, payloadMessage: PayloadMessage) =
        sendMessage(
            metadata,
            createMimeMultipartMessage(metadata, payloadMessage),
            PAYLOAD
        )

    private suspend fun sendMessage(metadata: MailMetadata, message: MimeMessage, messageType: MessageType) =
        withContext(Dispatchers.IO) {
            catch({ Transport.send(message) }) { e: MessagingException ->
                log.error("Failed to send $messageType message: ${e.stackTraceToString()}")
            }
        }

    private fun createMimeMessage(metadata: MailMetadata, signalMessage: SignalMessage): MimeMessage =
        MimeMessage(session).apply {
            setFrom(InternetAddress("smtp-transport@nav.no"))
            addRecipient(TO, InternetAddress("kristian.frohlich@nav.no"))
            setContent(signalMessage.envelope, "application/soap+xml; charset=UTF-8")
        }

    private fun createMimeMultipartMessage(metadata: MailMetadata, payloadMessage: PayloadMessage): MimeMessage =
        MimeMessage(session).apply {
            setFrom(InternetAddress("smtp-transport@nav.no"))
            addRecipient(TO, InternetAddress("kristian.frohlich@nav.no"))

            setContent(
                MimeMultipart().apply {
                    addBodyPart(createContentPart(payloadMessage))
                    createPayloadParts(payloadMessage).forEach(::addBodyPart)
                }
            )
        }

    private fun createContentPart(payloadMessage: PayloadMessage): MimeBodyPart =
        MimeBodyPart().apply { setContent(payloadMessage.envelope, "application/soap+xml; charset=UTF-8") }

    private fun createPayloadParts(payloadMessage: PayloadMessage): List<MimeBodyPart> =
        payloadMessage.payloads.map { payload ->
            MimeBodyPart().apply {
                setContent(String(payload.content), payload.contentType)
                contentID = payload.contentId
            }
        }
}
