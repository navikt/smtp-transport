package no.nav.emottak.smtp

import arrow.fx.coroutines.parMap
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.statement.HttpResponse
import jakarta.mail.Store
import kotlinx.coroutines.Dispatchers
import net.logstash.logback.marker.Markers
import no.nav.emottak.configuration.Config
import no.nav.emottak.configuration.Ebms
import no.nav.emottak.postEbmsMessageMultiPart
import no.nav.emottak.postEbmsMessageSinglePart
import java.time.Duration
import java.time.Instant
import kotlin.time.toKotlinDuration

class MailService(
    private val config: Config,
    private val store: Store
) {
    private val httpClient = HttpClient(CIO)

    suspend fun processMessages(): List<Result<HttpResponse>> {
        val timeStart = Instant.now()
        val processedMessages = mutableListOf<Result<HttpResponse>>()
        runCatching {
            MailReader(config.mail, store, false).use { reader ->
                log.info("Starting to read ${reader.count()} messages from inbox")
                do {
                    val messages = reader.readMail()
                    val currentProcessedMessages = messages
                        .parMap(concurrency = 64, context = Dispatchers.IO) {
                            postEbmsMessages(config.ebms, it)
                        }

                    processedMessages.addAll(currentProcessedMessages)
                    log.info("Processed a batch of ${messages.size} messages")
                } while (messages.isNotEmpty())

                log.info("Finished reading and processing all ${processedMessages.size} messages")
            }
        }
            .onSuccess {
                val timeToCompletion = Duration.between(timeStart, Instant.now())
                val throughputPerMinute =
                    processedMessages.size / (timeToCompletion.toMillis().toDouble() / 1000 / 60)
                log.info(
                    Markers.appendEntries(mapOf(Pair("MailReaderTPM", throughputPerMinute))),
                    "${processedMessages.size} messages processed in ${timeToCompletion.toKotlinDuration()},($throughputPerMinute tpm)"
                )
            }
            .onFailure {
                log.error(it.message, it)
                log.info(it.localizedMessage)
            }

        return processedMessages
    }

    private suspend fun postEbmsMessages(ebms: Ebms, message: EmailMsg): Result<HttpResponse> {
        return runCatching {
            if (message.parts.size == 1 && message.parts.first().headers.isEmpty()) {
                httpClient.postEbmsMessageSinglePart(ebms, message)
            } else {
                httpClient.postEbmsMessageMultiPart(ebms, message)
            }
        }
            .onFailure {
                log.error("Error posting EBMS message: ${it.message}", it)
            }
    }
}
