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
import kotlin.io.encoding.ExperimentalEncodingApi

private const val CONTENT_TYPE = "application/soap+xml; charset=UTF-8"

@OptIn(ExperimentalEncodingApi::class)
class MailSender(
    private val session: Session,
    private val eventLoggingService: ScopedEventLoggingService
) {
    private val smtp = config().smtp

    suspend fun forwardMessage(emailMsg: EmailMsg) =
        withContext(Dispatchers.IO) {
            catch({
                Transport
                    .send(
                        createForwardableMimeMessage(emailMsg),
                        arrayOf(InternetAddress(config().smtp.smtpT1EmottakAddress))
                    ).also {
                        log.info("Message forwarded to ${config().smtp.smtpT1EmottakAddress}")
                    }
            }) { error: MessagingException ->
                log.error("Failed to forward message: ${error.localizedMessage}", error)
            }
        }

    fun createForwardableMimeMessage(emailMsg: EmailMsg): MimeMessage {
        return MimeMessage(session).apply {
            emailMsg.headers.forEach { (header, value) ->
                setHeader(header, value)
            }
            // subject = "Forwarded from smtp-transport: ${emailMsg.headers["Subject"] ?: "No Subject"}"

            if (emailMsg.multipart) {
                setContent(
                    MimeMultipart("related").apply {
                        emailMsg.parts.forEach { part ->
                            val contentTransferEncoding = part.headers["Content-Transfer-Encoding"]?.lowercase()
                            val mimeBodyPart = MimeBodyPart().apply {
                                part.headers.also {
                                    log.debug("Creating MimeBodyPart with headers: ${it.entries.joinToString(", ") { header -> "${header.key}: ${header.value}" } }")
                                }.forEach { (key, value) ->
                                    this.setHeader(key, value)
                                }
                                this.setContent(
                                    String(part.bytes),
                                    part.headers["Content-Type"]
                                )
                                this.setHeader("Content-Transfer-Encoding", contentTransferEncoding ?: "7bit")
                            }.also {
                                log.debug("Created MimeBodyPart headers: ${it.allHeaders.toList().joinToString(", ") { header -> "${header.name}: ${header.value}" } }")
                            }
                            addBodyPart(mimeBodyPart)
                        }
                    }
                )
            } else { // Singlepart
                setContent(
                    String(emailMsg.parts[0].bytes),
                    emailMsg.headers["Content-Type"]
                )
            }
            saveChanges()
        }.also {
            log.debug("Created forwardable MimeMessage headers: ${it.allHeaders.toList().joinToString(", ") { header -> "${header.name}: ${header.value}" } }")
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

    private suspend fun sendMessage(wrapper: MimeMessageWrapper, messageType: MessageType) =
        withContext(Dispatchers.IO) {
            catch({
                Transport.send(wrapper.mimeMessage)
                eventLoggingService.registerEvent(
                    MESSAGE_SENT_VIA_SMTP,
                    wrapper.mimeMessage,
                    wrapper.requestId
                )
            }) { error: MessagingException ->
                log.error("Failed to send $messageType message: ${error.stackTraceToString()}")
                eventLoggingService.registerEvent(
                    ERROR_WHILE_SENDING_MESSAGE_VIA_SMTP,
                    error,
                    wrapper.requestId
                )
            }
        }

    private fun createMimeMessage(metadata: MailMetadata, signalMessage: SignalMessage): MimeMessageWrapper =
        MimeMessageWrapper(
            MimeMessage(session).apply {
                setFrom(smtp.smtpFromAddress)
                addRecipients(TO, getRecipients(metadata))
                setContent(signalMessage.envelope, CONTENT_TYPE)
            },
            signalMessage.messageId
        )

    private fun createMimeMultipartMessage(metadata: MailMetadata, payloadMessage: PayloadMessage): MimeMessageWrapper =
        MimeMessageWrapper(
            MimeMessage(session).apply {
                setFrom(smtp.smtpFromAddress)
                addRecipients(TO, getRecipients(metadata))

                setContent(
                    MimeMultipart().apply {
                        addBodyPart(createContentPart(payloadMessage))
                        createPayloadParts(payloadMessage).forEach(::addBodyPart)
                    }
                )
            },
            payloadMessage.messageId
        )

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
