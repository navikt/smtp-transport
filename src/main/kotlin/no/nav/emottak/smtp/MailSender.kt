package no.nav.emottak.smtp

import arrow.core.raise.catch
import jakarta.activation.DataHandler
import jakarta.mail.Message.RecipientType.TO
import jakarta.mail.MessagingException
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import jakarta.mail.util.ByteArrayDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.emottak.config
import no.nav.emottak.log
import no.nav.emottak.model.MailMetadata
import no.nav.emottak.model.MessageType
import no.nav.emottak.model.MessageType.PAYLOAD
import no.nav.emottak.model.MessageType.SIGNAL
import no.nav.emottak.model.PayloadMessage
import no.nav.emottak.model.SignalMessage
import no.nav.emottak.util.ScopedEventLoggingService
import no.nav.emottak.utils.kafka.model.EventType.ERROR_WHILE_SENDING_MESSAGE_VIA_SMTP
import no.nav.emottak.utils.kafka.model.EventType.MESSAGE_SENT_VIA_SMTP

private const val CONTENT_TYPE = "application/soap+xml; charset=UTF-8"

class MailSender(
    private val session: Session,
    private val eventLoggingService: ScopedEventLoggingService
) {
    private val smtp = config().smtp

    suspend fun forwardMessage(emailMsg: EmailMsg) =
        withContext(Dispatchers.IO) {
            catch({
                Transport.send(
                    createForwardableMimeMessage(emailMsg)
                )
            }) { error: MessagingException ->
                log.error("Failed to forward message: ${error.localizedMessage}", error)
            }
        }

    private fun createForwardableMimeMessage(emailMsg: EmailMsg): MimeMessage {
        val mimeParts = emailMsg.parts.map { part ->
            MimeBodyPart().apply {
                part.headers.forEach { (header, value) ->
                    setHeader(header, value)
                }
                val dataSource = ByteArrayDataSource(part.bytes, contentType)
                dataHandler = DataHandler(dataSource)
            }
        }

        return MimeMessage(session).apply {
            emailMsg.headers.forEach { (header, value) ->
                setHeader(header, value)
            }
            subject = "Forwarded from smtp-transport: ${emailMsg.headers["Subject"] ?: "No Subject"}"
            setRecipient(TO, InternetAddress(smtp.smtpT1EmottakAddress))

            setContent(
                MimeMultipart().apply {
                    mimeParts.forEach { part ->
                        addBodyPart(part)
                    }
                }
            )
            saveChanges()
        }
    }

    suspend fun sendSignalMessage(metadata: MailMetadata, signalMessage: SignalMessage) =
        sendMessage(
            createMimeMessage(metadata, signalMessage),
            SIGNAL
        )

    suspend fun sendPayloadMessage(metadata: MailMetadata, payloadMessage: PayloadMessage) =
        sendMessage(
            createMimeMultipartMessage(metadata, payloadMessage),
            PAYLOAD
        )

    private suspend fun sendMessage(message: MimeMessage, messageType: MessageType) =
        withContext(Dispatchers.IO) {
            catch({
                Transport.send(message)
                eventLoggingService.registerEvent(
                    MESSAGE_SENT_VIA_SMTP,
                    message
                )
            }) { error: MessagingException ->
                log.error("Failed to send $messageType message: ${error.stackTraceToString()}")
                eventLoggingService.registerEvent(
                    ERROR_WHILE_SENDING_MESSAGE_VIA_SMTP,
                    error
                )
            }
        }

    private fun createMimeMessage(metadata: MailMetadata, signalMessage: SignalMessage): MimeMessage =
        MimeMessage(session).apply {
            setFrom(smtp.smtpFromAddress)
            addRecipients(TO, getRecipients(metadata))
            setContent(signalMessage.envelope, CONTENT_TYPE)
        }

    private fun createMimeMultipartMessage(metadata: MailMetadata, payloadMessage: PayloadMessage): MimeMessage =
        MimeMessage(session).apply {
            setFrom(smtp.smtpFromAddress)
            addRecipients(TO, getRecipients(metadata))

            setContent(
                MimeMultipart().apply {
                    addBodyPart(createContentPart(payloadMessage))
                    createPayloadParts(payloadMessage).forEach(::addBodyPart)
                }
            )
        }

    private fun createContentPart(payloadMessage: PayloadMessage): MimeBodyPart =
        MimeBodyPart()
            .apply { setContent(payloadMessage.envelope, CONTENT_TYPE) }

    private fun createPayloadParts(payloadMessage: PayloadMessage): List<MimeBodyPart> =
        payloadMessage.payloads.map { payload ->
            MimeBodyPart().apply {
                setContent(String(payload.content), payload.contentType)
                contentID = payload.contentId
            }
        }

    private fun getRecipients(metadata: MailMetadata): String =
        smtp
            .smtpRedirectAddress
            .takeIf { it.isNotBlank() } ?: metadata.addresses
}
