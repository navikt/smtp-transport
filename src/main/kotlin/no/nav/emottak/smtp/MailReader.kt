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

data class EmailMsg(
    val headers: Map<String, String>,
    val parts: List<Part>
)

data class Part(
    val headers: Map<String, String>,
    val bytes: ByteArray
)

class MailReader(
    private val store: Store,
    private val expunge: Boolean = true
) : AutoCloseable {
    private val inbox: Folder = getInbox()

    companion object {
        fun mapEmailMsg(): (MimeMessage) -> EmailMsg = { message ->
            val messageContent = message.content
            val bodyparts: List<Part> = when (messageContent is MimeMultipart) {
                true -> createMimeBodyParts(messageContent)
                else -> createEmptyMimeBodyParts(message)
            }
            EmailMsg(
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
                message.rawInputStream.readAllBytes()
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
                bodyPart.rawInputStream.readAllBytes()
            )
        }
    }

    private val takeN = 1
    private var start = 1
    private val inboxLimit: Int = getEnvVar("INBOX_LIMIT", "2000").toInt()

    fun count() = inbox.messageCount

    private fun expunge(): Boolean = (expunge || count() > inboxLimit)

    override fun close() {
        inbox.close(
            expunge().also {
                if (expunge != it) {
                    log.warn("Inbox limit [$inboxLimit] exceeded. Expunge forced $it")
                }
            }
        )
    }

    @Throws(Exception::class)
    fun readMail(): List<EmailMsg> {
        try {
            val messageCount = inbox.messageCount
            return if (messageCount != 0) {
                val endIndex = (takeN + start - 1).takeIf { it < messageCount } ?: messageCount
                val result = inbox.getMessages(start, endIndex)
                    .map { it as MimeMessage }
                    .toList()
                    .onEach(::processMimeMessage)
                start += takeN
                result.map(mapEmailMsg())
            } else {
                emptyList<EmailMsg>().also { log.info("No email messages found") }
            }
        } catch (e: Exception) {
            log.error("Error connecting to mail server", e)
            throw e
        }
    }

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
        .run { store.getFolder("INBOX").apply { open(READ_WRITE) } }

    private fun createHeaderMarker(xMailer: String?): LogstashMarker = Markers
        .appendEntries(
            mutableMapOf("system source" to (xMailer ?: "-"))
        )
}
