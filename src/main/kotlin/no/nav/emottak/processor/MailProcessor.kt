package no.nav.emottak.processor

import arrow.autoCloseScope
import arrow.core.raise.fold
import jakarta.mail.Store
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.datetime.Clock
import net.logstash.logback.marker.LogstashMarker
import net.logstash.logback.marker.Markers
import no.nav.emottak.configuration.Mail
import no.nav.emottak.log
import no.nav.emottak.model.PayloadMessage
import no.nav.emottak.model.SignalMessage
import no.nav.emottak.publisher.MailPublisher
import no.nav.emottak.repository.PayloadRepository
import no.nav.emottak.smtp.EmailMsg
import no.nav.emottak.smtp.MailReader
import no.nav.emottak.smtp.MailSender
import no.nav.emottak.util.ForwardingSystem
import no.nav.emottak.util.ScopedEventLoggingService
import no.nav.emottak.util.toPayloadMessage
import no.nav.emottak.util.toSignalMessage
import kotlin.math.min

class MailProcessor(
    private val store: Store,
    private val mailPublisher: MailPublisher,
    private val payloadRepository: PayloadRepository,
    private val eventLoggingService: ScopedEventLoggingService,
    private val mailSender: MailSender,
    private val mail: Mail
) {

    enum class InboxStatus {
        EMPTY,
        STILL_LESS_THAN_WARNING_THRESHOLD,
        CRITICAL,
        UNKNOWN
    }

    fun processMessages(scope: CoroutineScope): Deferred<InboxStatus> = scope.async(Dispatchers.IO) {
        var inboxState = InboxStatus.UNKNOWN
        autoCloseScope {
            install(object : AutoCloseable {
                val starTime = Clock.System.now()
                override fun close() {
                    log.info("Scheduled message read batch executed in ${(Clock.System.now() - starTime).inWholeMilliseconds} ms")
                }
            })
            val mailReader = install(
                MailReader(
                    mail,
                    store,
                    mail.inboxExpunge,
                    eventLoggingService
                )
            )
            val messageCount = mailReader.count()
            val batchSize = min(mail.inboxBatchReadLimit, messageCount)
            if (messageCount > mail.inboxBatchReadLimit) {
                inboxState = InboxStatus.STILL_LESS_THAN_WARNING_THRESHOLD
            }
            if (messageCount > mail.inboxWarningThreshold) {
                inboxState = InboxStatus.CRITICAL
                log.warn("Inbox size above critical threshold: $messageCount")
            }

            if (messageCount > 0) {
                log.info("Starting to read $batchSize of $messageCount messages from inbox")
                mailReader.readMailBatches(batchSize).forEach { emailMsg ->
                    try {
                        processMessage(emailMsg)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        log.error("Failed to process message, leaving in inbox for retry", e)
                        return@forEach
                    }
                    try {
                        mailReader.markDeleted(emailMsg.originalMimeMessage)
                    } catch (e: Exception) {
                        log.warn("Message processed successfully but failed to mark as deleted", e)
                    }
                }
                log.info("Finished processing $batchSize of $messageCount messages from inbox")
            } else {
                inboxState = InboxStatus.EMPTY
                log.debug("No messages found in inbox")
            }
        }
        return@async inboxState
    }

    private suspend fun processMessage(emailMsg: EmailMsg) {
        val start = Clock.System.now()
        when (emailMsg.forwardableMimeMessage.forwardingSystem) {
            ForwardingSystem.EBMS -> publishToKafka(emailMsg)
            ForwardingSystem.EMOTTAK -> forwardToT1(emailMsg)
            ForwardingSystem.BOTH -> {
                publishToKafka(emailMsg)
                forwardToT1(emailMsg)
            }
        }
        val marker: LogstashMarker = Markers.appendEntries(
            mapOf(
                "requestId" to emailMsg.requestId.toString(),
                "smtpSender" to emailMsg.senderAddress,
                "smtpSubject" to (emailMsg.headers["Subject"] ?: "-"),
                "forwardingSystem" to emailMsg.forwardableMimeMessage.forwardingSystem,
                "sourceSystem" to (emailMsg.headers["X-Mailer"] ?: "-")
            )
        )
        log.info(marker, "Forwarded message in ${(Clock.System.now() - start).inWholeMilliseconds} ms")
    }

    private suspend fun forwardToT1(emailMsg: EmailMsg) {
        mailSender.rawForward(emailMsg.forwardableMimeMessage.forwardableMimeMessage!!)
    }

    private suspend fun publishToKafka(emailMsg: EmailMsg) {
        when (emailMsg.multipart) {
            true -> publishPayloadMessage(emailMsg.toPayloadMessage(emailMsg.requestId), emailMsg.senderAddress)
            false -> publishSignalMessage(emailMsg.toSignalMessage(emailMsg.requestId), emailMsg.senderAddress)
        }
    }

    private suspend fun publishPayloadMessage(payloadMessage: PayloadMessage, senderAddress: String) {
        with(payloadRepository) {
            fold(
                { insert(payloadMessage.payloads) },
                { log.error("Could not insert payloads: ${payloadMessage.payloads.map { it }}") }
            ) { mailPublisher.publishPayloadMessage(payloadMessage, senderAddress).getOrThrow() }
        }
    }

    private suspend fun publishSignalMessage(signalMessage: SignalMessage, senderAddress: String) {
        mailPublisher.publishSignalMessage(signalMessage, senderAddress).getOrThrow()
    }
}
