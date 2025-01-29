package no.nav.emottak.repository

import arrow.core.raise.Raise
import arrow.core.raise.catch
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext
import no.nav.emottak.PayloadAlreadyExists
import no.nav.emottak.PayloadNotFound
import no.nav.emottak.model.Payload
import no.nav.emottak.queries.PayloadDatabase
import org.postgresql.util.PSQLException
import org.postgresql.util.PSQLState.UNIQUE_VIOLATION
import java.sql.SQLException
import kotlin.uuid.Uuid

class PayloadRepository(payloadDatabase: PayloadDatabase) {
    private val payloadQueries = payloadDatabase.payloadQueries

    suspend fun Raise<PayloadAlreadyExists>.insert(payloads: List<Payload>): List<Pair<String, String>> =
        withContext(IO) { payloads.map { payload -> insertPayload(payload) } }

    suspend fun Raise<PayloadNotFound>.retrieve(referenceId: Uuid): List<Payload> =
        withContext(IO) { retrievePayloads(referenceId) }

    suspend fun Raise<PayloadNotFound>.retrieve(referenceId: Uuid, contentId: String): Payload =
        withContext(IO) { retrievePayload(referenceId, contentId) }

    private fun Raise<PayloadNotFound>.retrievePayloads(referenceId: Uuid): List<Payload> {
        val payloads = payloadQueries.retrievePayloads(referenceId.toString()).executeAsList()
        return when (payloads.isEmpty()) {
            true -> raise(PayloadNotFound(referenceId.toString()))
            else -> payloads.map {
                Payload(
                    Uuid.parse(it.reference_id),
                    it.content_id,
                    it.content_type,
                    it.content
                )
            }
        }
    }

    private fun Raise<PayloadNotFound>.retrievePayload(referenceId: Uuid, contentId: String) =
        when (val payload = payloadQueries.retrievePayload(referenceId.toString(), contentId).executeAsOneOrNull()) {
            null -> raise(PayloadNotFound(referenceId.toString()))
            else -> Payload(
                Uuid.parse(payload.reference_id),
                payload.content_id,
                payload.content_type,
                payload.content
            )
        }

    private fun Raise<PayloadAlreadyExists>.insertPayload(payload: Payload): Pair<String, String> =
        catch({
            val inserted = payloadQueries.insertPayload(
                reference_id = payload.referenceId.toString(),
                content_id = payload.contentId,
                content_type = payload.contentType,
                content = payload.content
            )
                .executeAsOne()

            return Pair(
                inserted.reference_id,
                inserted.content_id
            )
        }) { e: SQLException ->
            if (e is PSQLException && UNIQUE_VIOLATION.state == e.sqlState) {
                raise(
                    PayloadAlreadyExists(
                        payload.referenceId.toString(),
                        payload.contentId
                    )
                )
            } else {
                throw e
            }
        }
}
