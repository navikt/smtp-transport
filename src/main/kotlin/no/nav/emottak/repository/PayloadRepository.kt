package no.nav.emottak.repository

import arrow.core.raise.Raise
import arrow.core.raise.catch
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext
import no.nav.emottak.PayloadAlreadyExists
import no.nav.emottak.PayloadNotFound
import no.nav.emottak.model.Payload
import no.nav.emottak.queries.PayloadDatabase
import no.nav.emottak.util.ScopedEventLoggingService
import no.nav.emottak.utils.kafka.model.EventType.ERROR_WHILE_READING_PAYLOAD_FROM_DATABASE
import no.nav.emottak.utils.kafka.model.EventType.ERROR_WHILE_SAVING_PAYLOAD_INTO_DATABASE
import no.nav.emottak.utils.kafka.model.EventType.PAYLOAD_READ_FROM_DATABASE
import no.nav.emottak.utils.kafka.model.EventType.PAYLOAD_SAVED_INTO_DATABASE
import org.postgresql.util.PSQLException
import org.postgresql.util.PSQLState.UNIQUE_VIOLATION
import java.sql.SQLException
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

class PayloadRepository(
    payloadDatabase: PayloadDatabase,
    private val eventLoggingService: ScopedEventLoggingService
) {
    private val payloadQueries = payloadDatabase.payloadQueries

    suspend fun Raise<PayloadAlreadyExists>.insert(payloads: List<Payload>): List<Pair<Uuid, String>> =
        withContext(IO) { payloads.map { payload -> insertPayload(payload) } }

    suspend fun Raise<PayloadNotFound>.retrieve(referenceId: Uuid): List<Payload> =
        withContext(IO) { retrievePayloads(referenceId) }

    suspend fun Raise<PayloadNotFound>.retrieve(referenceId: Uuid, contentId: String): Payload =
        withContext(IO) { retrievePayload(referenceId, contentId) }

    private fun Raise<PayloadNotFound>.retrievePayloads(referenceId: Uuid): List<Payload> {
        val payloads = payloadQueries.retrievePayloads(referenceId.toJavaUuid()).executeAsList()
        return when (payloads.isEmpty()) {
            true -> {
                eventLoggingService.registerEvent(
                    ERROR_WHILE_READING_PAYLOAD_FROM_DATABASE,
                    Exception("Payload not found for reference id: $referenceId")
                )
                raise(PayloadNotFound(referenceId.toString()))
            }

            else -> payloads.map {
                val payload = Payload(
                    it.reference_id.toKotlinUuid(),
                    it.content_id,
                    it.content_type,
                    it.content
                )

                eventLoggingService.registerEvent(
                    PAYLOAD_READ_FROM_DATABASE,
                    payload
                )

                payload
            }
        }
    }

    private fun Raise<PayloadNotFound>.retrievePayload(referenceId: Uuid, contentId: String) =
        when (val payload = payloadQueries.retrievePayload(referenceId.toJavaUuid(), contentId).executeAsOneOrNull()) {
            null -> raise(PayloadNotFound(referenceId.toString()))
            else -> Payload(
                payload.reference_id.toKotlinUuid(),
                payload.content_id,
                payload.content_type,
                payload.content
            )
        }

    private fun Raise<PayloadAlreadyExists>.insertPayload(payload: Payload): Pair<Uuid, String> =
        catch({
            val inserted = payloadQueries.insertPayload(
                reference_id = payload.referenceId.toJavaUuid(),
                content_id = payload.contentId,
                content_type = payload.contentType,
                content = payload.content
            )
                .executeAsOne()

            eventLoggingService.registerEvent(
                PAYLOAD_SAVED_INTO_DATABASE,
                payload
            )

            return Pair(
                inserted.reference_id.toKotlinUuid(),
                inserted.content_id
            )
        }) { error: SQLException ->
            if (error is PSQLException && UNIQUE_VIOLATION.state == error.sqlState) {
                eventLoggingService.registerEvent(
                    ERROR_WHILE_SAVING_PAYLOAD_INTO_DATABASE,
                    error
                )

                raise(
                    PayloadAlreadyExists(
                        payload.referenceId.toString(),
                        payload.contentId
                    )
                )
            } else {
                throw error
            }
        }
}
