package no.nav.emottak

import com.nimbusds.jwt.SignedJWT
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import no.nav.emottak.model.Payload
import no.nav.emottak.repository.PayloadRepository
import no.nav.security.mock.oauth2.MockOAuth2Server

// Krever kotest-plugin installert i IntelliJ for å kjøre
class SmtpTransportIntegrationTest : StringSpec(
    {
        lateinit var mockOAuth2Server: MockOAuth2Server
        lateinit var payloadRepository: PayloadRepository

        beforeSpec {
            println("=== Initializing MockOAuth2Server ===")
            mockOAuth2Server = MockOAuth2Server().also { it.start(port = config().azureAuth.mockPort.value) }

            println("=== Initializing Database ===")
            payloadRepository = PayloadRepository(payloadDatabase())
            runMigrations()
        }

        afterSpec {
            println("=== Stopping MockOAuth2Server ===")
            mockOAuth2Server.shutdown()

            println("=== Stopping Database ===")
            stopContainer()
        }

        "Hent payload - ett treff" {
            smtpTransportTestApp(payloadRepository) {
                val httpClient = createClient {
                    install(ContentNegotiation) {
                        json()
                    }
                }
                val httpResponse: HttpResponse = httpClient.get("/api/payloads/99819a74-3f1d-453b-b1d3-735d900cfc5d") {
                    headers {
                        append(HttpHeaders.Authorization, "Bearer ${getToken(mockOAuth2Server).serialize()}")
                    }
                }
                httpResponse.status shouldBe HttpStatusCode.OK
                val payloads: List<Payload> = httpResponse.body()
                payloads.size shouldBe 1

                payloads[0].referenceId.toString() shouldBe "99819a74-3f1d-453b-b1d3-735d900cfc5d"
                payloads[0].contentId shouldBe "f7aeef95-afca-4355-b6f7-1692e58c61cc"
                payloads[0].contentType shouldBe "text/xml"
                payloads[0].content.decodeToString() shouldBe "<?xml version=\"1.0\" encoding=\"utf-8\"?><dummy>xml 1</dummy>"
            }
        }

        "Hent payload - flere treff" {
            smtpTransportTestApp(payloadRepository) {
                val httpClient = createClient {
                    install(ContentNegotiation) {
                        json()
                    }
                }
                val httpResponse: HttpResponse = httpClient.get("/api/payloads/df68056e-5cba-4351-9085-c37b925b8ddd") {
                    headers {
                        append(HttpHeaders.Authorization, "Bearer ${getToken(mockOAuth2Server).serialize()}")
                    }
                }
                httpResponse.status shouldBe HttpStatusCode.OK
                val payloads: List<Payload> = httpResponse.body()
                payloads.size shouldBe 2

                payloads[0].referenceId.toString() shouldBe "df68056e-5cba-4351-9085-c37b925b8ddd"
                assert(listOf("tKV9FS_cSMy7IsQ41SHIUQ", "test").contains(payloads[0].contentId))
                assert(listOf("tKV9FS_cSMy7IsQ41SHIUQ", "test").contains(payloads[1].contentId))
                payloads[1].contentId shouldNotBe payloads[0].contentId
                payloads[0].contentType shouldBe "text/xml"
                payloads[1].contentType shouldBe "text/xml"
                payloads[0].content.decodeToString() shouldBeIn listOf(
                    "<?xml version=\"1.0\" encoding=\"utf-8\"?><dummy>xml 2</dummy>",
                    "<?xml version=\"1.0\" encoding=\"utf-8\"?><dummy>xml 3</dummy>"
                )
                payloads[1].content.decodeToString() shouldNotBe payloads[0].content.decodeToString()
            }
        }

        "Hent payload - ingen treff" {
            smtpTransportTestApp(payloadRepository) {
                val httpClient = createClient {
                    install(ContentNegotiation) {
                        json()
                    }
                }
                val httpResponse: HttpResponse = httpClient.get("/api/payloads/f7aeef95-afca-4355-b6f7-1692e58c61cc") {
                    headers {
                        append(HttpHeaders.Authorization, "Bearer ${getToken(mockOAuth2Server).serialize()}")
                    }
                }
                httpResponse.status shouldBe HttpStatusCode.NotFound
            }
        }

        "Hent payload - ugyldig id" {
            smtpTransportTestApp(payloadRepository) {
                val httpClient = createClient {
                    install(ContentNegotiation) {
                        json()
                    }
                }
                val httpResponse: HttpResponse = httpClient.get("/api/payloads/ugyldig-reference-id") {
                    headers {
                        append(HttpHeaders.Authorization, "Bearer ${getToken(mockOAuth2Server).serialize()}")
                    }
                }
                httpResponse.status shouldBe HttpStatusCode.BadRequest
                httpResponse.bodyAsText() shouldBe "Invalid reference id (ugyldig-reference-id)"
            }
        }
    }
)

private fun <T> smtpTransportTestApp(
    payloadRepository: PayloadRepository,
    testBlock: suspend ApplicationTestBuilder.() -> T
) = testApplication {
    application(smtpTransportModule(PrometheusMeterRegistry(PrometheusConfig.DEFAULT), payloadRepository))
    testBlock()
}

private fun getToken(
    mockOAuth2Server: MockOAuth2Server,
    audience: String = config().azureAuth.appScope.value
): SignedJWT = mockOAuth2Server.issueToken(
    issuerId = config().azureAuth.azureAdAuth.value,
    audience = audience,
    subject = "testUser"
)
