package no.nav.emottak.plugin

import arrow.core.raise.Raise
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import arrow.core.raise.recover
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.micrometer.prometheus.PrometheusMeterRegistry
import no.nav.emottak.InvalidReferenceId
import no.nav.emottak.PayloadError
import no.nav.emottak.ReferenceIdEmpty
import no.nav.emottak.ReferenceIdMissing
import no.nav.emottak.config
import no.nav.emottak.model.Payload
import no.nav.emottak.repository.PayloadRepository
import no.nav.emottak.toContent
import no.nav.emottak.util.isValidUuid
import kotlin.uuid.Uuid

private const val REFERENCE_ID = "referenceId"

fun Application.configureRoutes(
    registry: PrometheusMeterRegistry,
    payloadRepository: PayloadRepository
) {
    val config = config()
    routing {
        internalRoutes(registry)
        authenticate(config.azureAuth.azureAdAuth.value) {
            externalRoutes(payloadRepository)
        }
    }
}

fun Route.internalRoutes(registry: PrometheusMeterRegistry) {
    get("/prometheus") {
        call.respond(registry.scrape())
    }
    route("/internal") {
        get("/health/liveness") {
            call.respondText("I'm alive! :)")
        }
        get("/health/readiness") {
            call.respondText("I'm ready! :)")
        }
    }
}

fun Route.externalRoutes(payloadRepository: PayloadRepository) {
    route("/api") {
        get("/payloads/{$REFERENCE_ID}") {
            recover({
                val payload = retrievePayload(call, payloadRepository)
                call.respond(payload)
            }) { e: PayloadError -> call.respond(e.toContent()) }
        }
    }
}

private suspend fun Raise<PayloadError>.retrievePayload(
    request: ApplicationCall,
    payloadRepository: PayloadRepository
): List<Payload> = with(payloadRepository) {
    val referenceId = referenceId(request)
    val nonEmptyReferenceId = notEmpty(referenceId)
    val validReferenceId = valid(nonEmptyReferenceId)

    retrieve(validReferenceId)
}

private fun Raise<ReferenceIdMissing>.referenceId(call: ApplicationCall): String =
    ensureNotNull(call.parameters[REFERENCE_ID]) { ReferenceIdMissing }

private fun Raise<ReferenceIdEmpty>.notEmpty(referenceId: String): String {
    ensure(referenceId.isNotBlank()) { ReferenceIdEmpty }
    return referenceId
}

private fun Raise<InvalidReferenceId>.valid(referenceId: String): Uuid {
    ensure(referenceId.isValidUuid()) { InvalidReferenceId(referenceId) }
    return Uuid.parse(referenceId)
}
