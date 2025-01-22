package no.nav.emottak.plugin

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.raise.either
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.micrometer.prometheus.PrometheusMeterRegistry
import no.nav.emottak.AZURE_AD_AUTH
import no.nav.emottak.PayloadRequestValidationError
import no.nav.emottak.log
import no.nav.emottak.repository.PayloadRepository
import no.nav.emottak.repository.PayloadRequest

private const val REFERENCE_ID = "referenceId"

fun Application.configureRoutes(registry: PrometheusMeterRegistry, db: PayloadRepository) {
    routing {
        registerHealthEndpoints(registry)
        authenticate(AZURE_AD_AUTH) {
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

fun Route.getPayloads(payloadRepository: PayloadRepository): Route = get("/payload/{$REFERENCE_ID}") {
    val request: Either<NonEmptyList<PayloadRequestValidationError>, PayloadRequest> = PayloadRequest(call.parameters[REFERENCE_ID])
    when (request) {
        is Either.Left -> { // Valideringsfeil:
            val msg = request.value.joinToString()
            log.warn("Validation failed:\n$msg")
            call.respond(HttpStatusCode.BadRequest, msg)
        }
        is Either.Right -> { // OK request:
            val referenceId = request.value.referenceId
            val result = with(payloadRepository) { either { retrieve(referenceId) } }
            when (result) {
                is Either.Right -> call.respond(HttpStatusCode.OK, result.value)
                is Either.Left -> {
                    log.warn("Did not find Payload.reference_id '$referenceId'")
                    call.respond(HttpStatusCode.NotFound, result.value)
                }
            }
        }
        else -> { // Teknisk feil:
            call.respond(HttpStatusCode.InternalServerError, request)
        }
    }
}
