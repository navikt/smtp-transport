package no.nav.emottak.repository

import arrow.core.raise.Raise
import arrow.core.raise.catch
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext
import no.nav.emottak.PayloadAlreadyExist
import no.nav.emottak.PayloadDoesNotExist
import no.nav.emottak.queries.PayloadDatabase
import no.nav.emottak.util.Payload
import org.postgresql.util.PSQLException
import org.postgresql.util.PSQLState.UNIQUE_VIOLATION
import java.sql.SQLException

class PayloadRepository(payloadDatabase: PayloadDatabase) {
    private val payloadQueries = payloadDatabase.payloadQueries

    suspend fun Raise<PayloadAlreadyExist>.insert(payloads: List<Payload>): List<Pair<String, String>> =
        withContext(IO) { payloads.map { payload -> insertPayload(payload) } }

    suspend fun Raise<PayloadDoesNotExist>.retrieve(referenceId: String): List<Payload> =
        withContext(IO) { retrievePayloads(referenceId) }

    suspend fun Raise<PayloadDoesNotExist>.retrieve(referenceId: String, contentId: String): Payload =
        withContext(IO) { retrievePayload(referenceId, contentId) }

    private fun Raise<PayloadDoesNotExist>.retrievePayloads(referenceId: String): List<Payload> {
        val payloads = payloadQueries.retrievePayloads(referenceId).executeAsList()
        return when (payloads.isEmpty()) {
            true -> raise(PayloadDoesNotExist(referenceId))
            else -> payloads.map {
                Payload(
                    it.reference_id,
                    it.content_id,
                    it.content_type,
                    it.content
                )
            }
        }
    }

    private fun Raise<PayloadDoesNotExist>.retrievePayload(referenceId: String, contentId: String) =
        when (val payload = payloadQueries.retrievePayload(referenceId, contentId).executeAsOneOrNull()) {
            null -> raise(PayloadDoesNotExist(referenceId, contentId))
            else -> Payload(
                payload.reference_id,
                payload.content_id,
                payload.content_type,
                payload.content
            )
        }

    private fun Raise<PayloadAlreadyExist>.insertPayload(payload: Payload): Pair<String, String> =
        catch({
            val inserted = payloadQueries.insertPayload(
                reference_id = payload.referenceId,
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
                        payload.referenceId,
                        payload.contentId
                    )
                )
            } else {
                throw e
            }
        }
}
