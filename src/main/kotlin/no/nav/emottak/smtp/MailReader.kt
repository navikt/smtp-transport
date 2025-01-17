package no.nav.emottak.smtp

import jakarta.mail.BodyPart
import jakarta.mail.Flags.Flag.DELETED
import jakarta.mail.Folder
import jakarta.mail.Folder.READ_WRITE
import jakarta.mail.Store
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import net.logstash.logback.marker.LogstashMarker
import net.logstash.logback.marker.Markers
import no.nav.emottak.configuration.Mail
import no.nav.emottak.log

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
    private val expunge: Boolean = true
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
                    .map { it as MimeMessage }
                    .toList()
                    .onEach(::processMimeMessage)
                start += batchSize // Update start index
                result.map(::mapEmailMsg) // Return all mapped emails
            } else {
                emptyList<EmailMsg>().also { log.info("No email messages found") }
            }
        } catch (e: Exception) {
            log.error("Error connecting to mail server", e)
            throw e
        }
    }

    private fun expunge(): Boolean = (expunge || count() > mail.inboxLimit)

    private fun processMimeMessage(mimeMessage: MimeMessage) {
        log.info("Reading emails startIndex $start")
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
        log.info(headerMarker, "From: <${mimeMessage.from[0]}> Subject: <${mimeMessage.subject}>")
        mimeMessage.setFlag(DELETED, expunge())
    }

    private fun logMimeMessage(mimeMessage: MimeMessage) {
        val mimeHeaders = getMimeMessageHeaders(mimeMessage)
        val mimeMessageAsString = String(mimeMessage.inputStream.readAllBytes())

        log.info("Incoming single part request with headers $mimeHeaders and body $mimeMessageAsString")
    }

    private fun logMimeMultipartMessage(mimeMessage: MimeMessage) {
        val content = mimeMessage.content as MimeMultipart
        runCatching { content.getBodyPart(0) }
            .onSuccess {
                val messageHeaders = getMimeMessageHeaders(mimeMessage)
                val bodyPartHeaders = getBodyPartHeaders(it)
                val bodyAsString = String(it.inputStream.readAllBytes())
                log.info("Incoming multipart request with headers $messageHeaders body part headers $bodyPartHeaders and body $bodyAsString")
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
        val messageContent = message.content
        val multiPartMessage = messageContent is MimeMultipart
        val bodyparts: List<Part> = when (multiPartMessage) {
            true -> createMimeBodyParts(messageContent as MimeMultipart)
            else -> createEmptyMimeBodyParts(message)
        }
        return EmailMsg(
            multiPartMessage,
            message.allHeaders
                .toList()
                .groupBy({ it.name }, { it.value })
                .mapValues { it.value.joinToString(",") },
            bodyparts
        )
    }

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
}
