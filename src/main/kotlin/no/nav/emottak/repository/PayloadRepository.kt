package no.nav.emottak.repository

import arrow.core.raise.Raise
import arrow.core.raise.catch
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext
import no.nav.emottak.Error.PayloadAlreadyExist
import no.nav.emottak.smtp.PayloadDatabase
import no.nav.emottak.util.Payload
import java.sql.SQLException
import java.sql.SQLIntegrityConstraintViolationException

private const val INCOMING = "IN"

private typealias UniqueViolation = SQLIntegrityConstraintViolationException

class PayloadRepository(payloadDatabase: PayloadDatabase) {
    private val payloadQueries = payloadDatabase.payloadQueries

    suspend fun Raise<PayloadAlreadyExist>.insert(payloads: List<Payload>): List<Pair<String, String>> =
        withContext(IO) {
            payloadQueries.transactionWithResult {
                payloads.map { payload -> insertPayload(payload) }
            }
        }

    private fun Raise<PayloadAlreadyExist>.insertPayload(payload: Payload): Pair<String, String> =
        catch({
            val inserted = payloadQueries.insertPayload(
                reference_id = payload.referenceId,
                content_id = payload.contentId,
                content_type = payload.contentType,
                content = payload.content,
                direction = INCOMING
            )
                .executeAsOne()

            return Pair(
                inserted.reference_id,
                inserted.content_id
            )
        }) { e: SQLException ->
            if (e is UniqueViolation) {
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
