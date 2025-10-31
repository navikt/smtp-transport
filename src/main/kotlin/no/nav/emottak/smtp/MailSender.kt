package no.nav.emottak.smtp

import arrow.core.raise.catch
import jakarta.activation.DataHandler
import jakarta.mail.Message.RecipientType.TO
import jakarta.mail.MessagingException
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.ContentType
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
import no.nav.emottak.util.addEbXMLMimeHeaders
import no.nav.emottak.utils.kafka.model.EventType.ERROR_WHILE_SENDING_MESSAGE_VIA_SMTP
import no.nav.emottak.utils.kafka.model.EventType.MESSAGE_SENT_VIA_SMTP
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security
import kotlin.uuid.Uuid

private const val CONTENT_TYPE = "text/xml; charset=UTF-8"

class MailSender(
    private val session: Session,
    private val eventLoggingService: ScopedEventLoggingService
) {
    private val smtp = config().smtp

    init {
        Security.addProvider(BouncyCastleProvider())
    }

    suspend fun rawForward(mimeMessage: MimeMessage, address: InternetAddress = InternetAddress(config().smtp.smtpT1EmottakAddress)) =
        withContext(Dispatchers.IO) {
            catch({
                Transport
                    .send(
                        mimeMessage,
                        arrayOf(address)
                    ).also {
                        log.info("Message forwarded to ${config().smtp.smtpT1EmottakAddress}")
                    }
            }) { error: MessagingException ->
                log.error("Failed to forward message: ${error.localizedMessage}", error)
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
                addEbXMLMimeHeaders()
                setFrom(smtp.smtpFromAddress)
                addRecipients(TO, getRecipients(metadata))
                setContent(signalMessage.envelope, CONTENT_TYPE)
            },
            signalMessage.messageId
        )

    private fun createMimeMultipartMessage(metadata: MailMetadata, payloadMessage: PayloadMessage): MimeMessageWrapper =
        MimeMessageWrapper(
            MimeMessage(session).apply {
                addEbXMLMimeHeaders()
                setFrom(smtp.smtpFromAddress)
                addRecipients(TO, getRecipients(metadata))
                val mainContentId = Uuid.random().toString()

                setContent(
                    MimeMultipart("related").apply {
                        addBodyPart(
                            createMimeBodyPart(
                                mainContentId,
                                CONTENT_TYPE,
                                payloadMessage.envelope
                            )
                        )
                        createPayloadParts(payloadMessage).forEach(::addBodyPart)
                        setHeader(
                            "Content-Type",
                            ContentType(contentType).apply {
                                setParameter("type", "\"$CONTENT_TYPE\"")
                                setParameter("start", "\"<$mainContentId>\"")
                            }.toString()
                        )
                    }
                )
            },
            payloadMessage.messageId
        )

    private fun createPayloadParts(payloadMessage: PayloadMessage): List<MimeBodyPart> =
        payloadMessage.payloads.map { payload ->
            createMimeBodyPart(
                payload.contentId,
                payload.contentType,
                payload.content
            )
        }

    private fun createMimeBodyPart(contentId: String, contentType: String, content: ByteArray): MimeBodyPart =
        MimeBodyPart().apply {
            setDataHandler(
                DataHandler(
                    ByteArrayDataSource(content, contentType)
                )
            )
            this.contentID = "<$contentId>"
            setHeader("Content-Transfer-Encoding", "base64")
        }

    private fun getRecipients(metadata: MailMetadata): String =
        (
            smtp
                .smtpRedirectAddress
                .takeIf { it.isNotBlank() } ?: metadata.addresses
            )
            .replace("mailto://", "")
            .also {
                log.debug("Sending message to <$it> in place of <${metadata.addresses}>")
            }
}
