package no.nav.emottak.plugin

import arrow.core.raise.Raise
import arrow.core.raise.catch
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import arrow.core.raise.recover
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.content.TextContent
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.micrometer.prometheus.PrometheusMeterRegistry
import no.nav.emottak.EmptyReferenceId
import no.nav.emottak.InvalidReferenceId
import no.nav.emottak.PayloadDoesNotExist
import no.nav.emottak.PayloadError
import no.nav.emottak.ReferenceIdMissing
import no.nav.emottak.config
import no.nav.emottak.model.Payload
import no.nav.emottak.repository.PayloadRepository
import kotlin.uuid.Uuid

private const val REFERENCE_ID = "referenceId"

fun Application.configureRoutes(registry: PrometheusMeterRegistry, db: PayloadRepository) {
    val config = config()
    routing {
        registerHealthEndpoints(registry)
        authenticate(config.azureAuth.azureAdAuth.value) {
            getPayloads(db)
        }
    }
}

fun Route.registerHealthEndpoints(registry: PrometheusMeterRegistry) {
    get("/internal/health/liveness") {
        call.respondText("I'm alive! :)")
    }
    get("/internal/health/readiness") {
        call.respondText("I'm ready! :)")
    }
    get("/prometheus") {
        call.respond(registry.scrape())
    }
}

fun Route.getPayloads(payloadRepository: PayloadRepository) = get("/payload/{$REFERENCE_ID}") {
    recover({
        val payload = retrievePayload(call, payloadRepository)
        call.respond(payload)
    }) { e: PayloadError -> call.respond(e.toContent()) }
}

private suspend fun Raise<PayloadError>.retrievePayload(
    request: ApplicationCall,
    repository: PayloadRepository
): List<Payload> = with(repository) {
    val referenceId = referenceId(request)
    val nonEmptyReferenceId = notEmpty(referenceId)
    val validReferenceId = valid(nonEmptyReferenceId)

    retrieve(validReferenceId)
}

private fun Raise<ReferenceIdMissing>.referenceId(call: ApplicationCall): String =
    ensureNotNull(call.parameters[REFERENCE_ID]) { ReferenceIdMissing }

private fun Raise<EmptyReferenceId>.notEmpty(referenceId: String): String {
    ensure(referenceId.isNotBlank()) { EmptyReferenceId }
    return referenceId
}

private fun Raise<InvalidReferenceId>.valid(referenceId: String): Uuid {
    ensure(referenceId.isValidUuid()) { InvalidReferenceId(referenceId) }
    return Uuid.parse(referenceId)
}

private fun String.isValidUuid(): Boolean = catch({ Uuid.parse(this); true }) { false }

private fun PayloadError.toContent(): TextContent =
    when (this) {
        is PayloadDoesNotExist ->
            TextContent("Payload does not exist for reference id (${this.referenceId})", NotFound)

        ReferenceIdMissing ->
            TextContent("Reference id missing", BadRequest)

        EmptyReferenceId ->
            TextContent("Empty reference id", BadRequest)

        is InvalidReferenceId ->
            TextContent("Invalid reference id (${this.referenceId})", BadRequest)

        else -> TextContent("Unknown error", InternalServerError)
    }

private fun TextContent(
    content: String,
    statusCode: HttpStatusCode
): TextContent = TextContent(content, ContentType.Text.Plain, statusCode)
