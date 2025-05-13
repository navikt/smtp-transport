package no.nav.emottak.smtp

import jakarta.mail.BodyPart
import jakarta.mail.Flags.Flag.DELETED
import jakarta.mail.Folder
import jakarta.mail.Folder.READ_WRITE
import jakarta.mail.MessagingException
import jakarta.mail.Store
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import net.logstash.logback.marker.LogstashMarker
import net.logstash.logback.marker.Markers
import no.nav.emottak.configuration.Mail
import no.nav.emottak.log
import no.nav.emottak.util.ScopedEventLoggingService
import no.nav.emottak.utils.kafka.model.EventType.ERROR_WHILE_RECEIVING_MESSAGE_VIA_SMTP
import no.nav.emottak.utils.kafka.model.EventType.MESSAGE_RECEIVED_VIA_SMTP
import java.io.ByteArrayOutputStream
import kotlin.text.Charsets.UTF_8

data class EmailMsg(
    val multipart: Boolean,
    val headers: Map<String, String>,
    val parts: List<Part>
)

data class Part(
    val headers: Map<String, String>,
    val bytes: ByteArray
)

class MailReader(
    private val mail: Mail,
    private val store: Store,
    private val expunge: Boolean = true,
    private val eventLoggingService: ScopedEventLoggingService
) : AutoCloseable {
    private var start = 1
    private val inbox: Folder = getInbox()

    fun count() = inbox.messageCount

    override fun close() {
        inbox.close(
            expunge().also {
                if (expunge != it) {
                    log.warn("Inbox limit [${mail.inboxLimit}] exceeded. Expunge forced $it")
                }
            }
        )
    }

    @Throws(Exception::class)
    fun readMailBatches(batchSize: Int): List<EmailMsg> {
        try {
            val messageCount = count()
            return if (messageCount != 0) {
                val endIndex = (batchSize + start - 1).takeIf { it < messageCount } ?: messageCount
                val result = inbox.getMessages(start, endIndex)
                    .map { message -> (message as MimeMessage).also { registerEvent(it) } }
                    .toList()
                    .onEach(::processMimeMessage)
                start += batchSize // Update start index
                result.map(::mapEmailMsg) // Return all mapped emails
            } else {
                emptyList<EmailMsg>().also { log.info("No email messages found") }
            }
        } catch (e: Exception) {
            if (e is MessagingException) registerEvent(e)
            log.error("Error connecting to mail server", e)
            throw e
        }
    }

    private fun expunge(): Boolean = (expunge || count() > mail.inboxLimit)

    private fun processMimeMessage(mimeMessage: MimeMessage) {
        log.debug("Reading emails startIndex $start")
        when (mimeMessage.content) {
            is MimeMultipart -> logMimeMultipartMessage(mimeMessage)
            else -> logMimeMessage(mimeMessage)
        }
        setDeletedFlagOnMimeMessage(mimeMessage)
    }

    private fun setDeletedFlagOnMimeMessage(mimeMessage: MimeMessage) {
        val headerXMailer = mimeMessage.getHeader("X-Mailer")
            ?.toList()
            ?.firstOrNull()
        val headerMarker = createHeaderMarker(headerXMailer)
        log.debug(headerMarker, "From: <{}> Subject: <{}>", mimeMessage.from[0], mimeMessage.subject)
        mimeMessage.setFlag(DELETED, expunge())
    }

    private fun logMimeMessage(mimeMessage: MimeMessage) {
        val mimeHeaders = getMimeMessageHeaders(mimeMessage)
        val mimeMessageAsString = String(mimeMessage.inputStream.readAllBytes())

        log.debug("Incoming single part request with headers {} and body {}", mimeHeaders, mimeMessageAsString)
    }

    private fun logMimeMultipartMessage(mimeMessage: MimeMessage) {
        log.debug("Mime message as eml: {}", mimeMessageToEML(mimeMessage))
        val content = mimeMessage.content as MimeMultipart
        runCatching { content.getBodyPart(0) }
            .onSuccess {
                val messageHeaders = getMimeMessageHeaders(mimeMessage)
                val bodyPartHeaders = getBodyPartHeaders(it)
                val bodyAsString = String(it.inputStream.readAllBytes())
                log.debug(
                    "Incoming multipart message with headers: {}, body part headers: {}, and body: {}",
                    messageHeaders,
                    bodyPartHeaders,
                    bodyAsString
                )
            }
    }

    private fun getBodyPartHeaders(bodyPart: BodyPart) = bodyPart.allHeaders
        .toList()
        .map { it.name + ":" + it.value }

    private fun getMimeMessageHeaders(mimeMessage: MimeMessage) = mimeMessage.allHeaders
        .toList()
        .map { it.name + ":" + it.value }

    private fun getInbox() = store.isConnected
        .let { connected -> if (!connected) store.connect() }
        .run { store.getFolder("INBOX").apply { if (!isOpen) open(READ_WRITE) } }

    private fun createHeaderMarker(xMailer: String?): LogstashMarker = Markers
        .appendEntries(
            mutableMapOf("system source" to (xMailer ?: "-"))
        )

    internal fun mapEmailMsg(message: MimeMessage): EmailMsg {
        val multipartMessage = message.isMimeMultipart()
        val bodyparts: List<Part> = when (multipartMessage) {
            true -> createMimeBodyParts(message.content as MimeMultipart)
            else -> createEmptyMimeBodyParts(message)
        }
        return EmailMsg(
            multipartMessage,
            message.allHeaders
                .toList()
                .groupBy({ it.name }, { it.value })
                .mapValues { it.value.joinToString(",") },
            bodyparts
        )
    }

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

    private fun mimeMessageToEML(mimeMessage: MimeMessage): String =
        ByteArrayOutputStream()
            .apply { mimeMessage.writeTo(this) }
            .toString(UTF_8.name())

    private fun registerEvent(message: MimeMessage) = eventLoggingService
        .registerEvent(
            MESSAGE_RECEIVED_VIA_SMTP,
            message
        )

    private fun registerEvent(error: MessagingException) = eventLoggingService
        .registerEvent(
            ERROR_WHILE_RECEIVING_MESSAGE_VIA_SMTP,
            error
        )
}
