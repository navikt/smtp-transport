package no.nav.emottak.repository

import arrow.core.raise.Raise
import arrow.core.raise.catch
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext
import no.nav.emottak.PayloadAlreadyExist
import no.nav.emottak.PayloadDoesNotExist
import no.nav.emottak.model.Payload
import no.nav.emottak.queries.PayloadDatabase
import org.postgresql.util.PSQLException
import org.postgresql.util.PSQLState.UNIQUE_VIOLATION
import java.sql.SQLException
import java.util.UUID

class PayloadRepository(payloadDatabase: PayloadDatabase) {
    private val payloadQueries = payloadDatabase.payloadQueries

    suspend fun Raise<PayloadAlreadyExist>.insert(payloads: List<Payload>): List<Pair<String, String>> =
        withContext(IO) { payloads.map { payload -> insertPayload(payload) } }

    suspend fun Raise<PayloadDoesNotExist>.retrieve(referenceId: UUID): List<Payload> =
        withContext(IO) { retrievePayloads(referenceId) }

    suspend fun Raise<PayloadDoesNotExist>.retrieve(referenceId: UUID, contentId: String): Payload =
        withContext(IO) { retrievePayload(referenceId, contentId) }

    private fun Raise<PayloadDoesNotExist>.retrievePayloads(referenceId: UUID): List<Payload> {
        val payloads = payloadQueries.retrievePayloads(referenceId.toString()).executeAsList()
        return when (payloads.isEmpty()) {
            true -> raise(PayloadDoesNotExist(referenceId.toString()))
            else -> payloads.map {
                Payload(
                    UUID.fromString(it.reference_id),
                    it.content_id,
                    it.content_type,
                    it.content
                )
            }
        }
    }

    private fun Raise<PayloadDoesNotExist>.retrievePayload(referenceId: UUID, contentId: String) =
        when (val payload = payloadQueries.retrievePayload(referenceId.toString(), contentId).executeAsOneOrNull()) {
            null -> raise(PayloadDoesNotExist(referenceId.toString(), contentId))
            else -> Payload(
                UUID.fromString(payload.reference_id),
                payload.content_id,
                payload.content_type,
                payload.content
            )
        }

    private fun Raise<PayloadAlreadyExist>.insertPayload(payload: Payload): Pair<String, String> =
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
                    PayloadAlreadyExist(
                        payload.referenceId.toString(),
                        payload.contentId
                    )
                )
            } else {
                throw e
            }
        }
}
