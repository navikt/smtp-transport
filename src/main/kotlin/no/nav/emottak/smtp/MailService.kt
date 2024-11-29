package no.nav.emottak.smtp

import arrow.fx.coroutines.parMap
import io.ktor.client.HttpClient
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
    private val store: Store,
    private val httpClient: HttpClient
) {
    suspend fun processMessages() {
        val timeStart = Instant.now()
        runCatching {
            MailReader(config.mail, store, false).use { reader ->
                val countedMessages = reader.count()
                log.info("Starting to read $countedMessages messages from inbox")
                reader.readMailBatches(countedMessages)
                    .also { log.info("Finished reading all messages from inbox. Starting to process all messages") }
                    .parMap(concurrency = countedMessages, context = Dispatchers.IO) {
                        postEbmsMessages(config.ebms, it)
                    }
                    .also { log.info("Finished processing all messages") }
            }
        }
            .onSuccess {
                val timeToCompletion = Duration.between(timeStart, Instant.now())
                val throughputPerMinute =
                    it.size / (timeToCompletion.toMillis().toDouble() / 1000 / 60)
                log.info(
                    Markers.appendEntries(mapOf(Pair("MailReaderTPM", throughputPerMinute))),
                    "${it.size} messages processed in ${timeToCompletion.toKotlinDuration()},($throughputPerMinute tpm)"
                )
            }
            .onFailure {
                log.error(it.message, it)
                log.info(it.localizedMessage)
            }
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
