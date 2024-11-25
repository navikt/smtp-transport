package no.nav.emottak.smtp

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.util.pipeline.PipelineContext
import io.micrometer.prometheus.PrometheusMeterRegistry
import jakarta.mail.Flags
import jakarta.mail.Folder
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO_PARALLELISM_PROPERTY_NAME
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import net.logstash.logback.marker.Markers
import no.nav.emottak.configuration.Config
import no.nav.emottak.configuration.Ebms
import no.nav.emottak.postEbmsMessageMultiPart
import no.nav.emottak.postEbmsMessageSinglePart
import no.nav.emottak.smtp.StoreFactory.createStore
import java.lang.System.getProperty
import java.time.Duration
import java.time.Instant
import kotlin.time.toKotlinDuration

private val httpClient = HttpClient(CIO)

fun Route.mailRead(config: Config): Route = get("/mail/read") {
    call.respond(OK, "Starting to read messages...")
    var messageCount = 0
    val timeStart = Instant.now()
    runCatching {
        MailReader(config.mail, createStore(config.smtp), false).use {
            messageCount = it.count()
            log.info("Read ${it.count()} messages from inbox")
            val asyncJobList: ArrayList<Deferred<Any>> = ArrayList()
            var mailCounter = 0
            do {
                val messages = it.readMail()
                messages.forEach { message -> postEbmsMessagesAsync(config.ebms, asyncJobList, message) }
                mailCounter += 1
                if (mailCounter < (getProperty(IO_PARALLELISM_PROPERTY_NAME) ?: "64").toInt()) {
                    asyncJobList.awaitAll()
                    asyncJobList.clear()
                    mailCounter = 0
                }
                log.info("Inbox has messages ${messages.isNotEmpty()}")
            } while (messages.isNotEmpty())
            asyncJobList.awaitAll()
        }
    }
        .onSuccess {
            val timeToCompletion = Duration.between(timeStart, Instant.now())
            val throughputPerMinute = messageCount / (timeToCompletion.toMillis().toDouble() / 1000 / 60)
            log.info(
                Markers.appendEntries(mapOf(Pair("MailReaderTPM", throughputPerMinute))),
                "$messageCount processed in ${timeToCompletion.toKotlinDuration()},($throughputPerMinute tpm)"
            )
            call.respond(OK, "All messages have been read")
        }
        .onFailure {
            log.error(it.message, it)
            call.respond(it.localizedMessage)
        }
}

private fun PipelineContext<Unit, ApplicationCall>.postEbmsMessagesAsync(
    ebms: Ebms,
    asyncJobList: ArrayList<Deferred<Any>>,
    message: EmailMsg
) {
    asyncJobList.add(
        async(Dispatchers.IO) {
            runCatching {
                if (message.parts.size == 1 && message.parts.first().headers.isEmpty()) {
                    httpClient.postEbmsMessageSinglePart(ebms, message)
                } else {
                    httpClient.postEbmsMessageMultiPart(ebms, message)
                }
            }
                .onFailure { log.error(it.message, it) }
        }
    )
}

fun Folder.batchDelete(batchSize: Int) {
    val totalMessages = messageCount
    var previousMsgNum = 1
    do {
        this.open(Folder.READ_WRITE)
        val end = minOf(batchSize - 1, this.messageCount)
        log.info("Deleting in ${this.fullName} message $previousMsgNum to ${previousMsgNum + end} out of $totalMessages")
        this.getMessages(1, end).forEach { message ->
            message.setFlag(Flags.Flag.DELETED, true)
        }
        this.close(true)
        previousMsgNum += end
    } while (totalMessages > previousMsgNum)
}

fun Routing.registerHealthEndpoints(collectorRegistry: PrometheusMeterRegistry) {
    get("/internal/health/liveness") {
        call.respondText("I'm alive! :)")
    }
    get("/internal/health/readiness") {
        call.respondText("I'm ready! :)")
    }
    get("/prometheus") {
        call.respond(collectorRegistry.scrape())
    }
}
