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
import no.nav.emottak.util.fakeEventLoggingService
import no.nav.security.mock.oauth2.MockOAuth2Server

// Krever kotest-plugin installert i IntelliJ for å kjøre
class SmtpTransportSpec : StringSpec(
    {
        lateinit var mockOAuth2Server: MockOAuth2Server
        lateinit var payloadRepository: PayloadRepository

        beforeSpec {
            println("=== Initializing MockOAuth2Server ===")
            mockOAuth2Server = MockOAuth2Server().also { it.start(port = config().azureAuth.port.value) }

            println("=== Initializing Database ===")
            payloadRepository = PayloadRepository(
                payloadDatabase(),
                fakeEventLoggingService()
            )
            runMigrations()
        }

        afterSpec {
            println("=== Stopping MockOAuth2Server ===")
            mockOAuth2Server.shutdown()
        }

        "Verify Kafka Hoplite-config from both emottak-utils and this module" {
            smtpTransportTestApp(payloadRepository) {
                val kafka = config().kafka
                kafka.securityProtocol.value shouldBe "SSL" // From emottak-utils
                kafka.keystoreType.value shouldBe "PKCS12" // From emottak-utils
                kafka.truststoreType.value shouldBe "JKS" // From emottak-utils
                kafka.groupId shouldBe "smtp-transport" // From smtp-transport
            }
        }

        "Get payloads - One payload" {
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
                val payload = payloads.first()
                payload.referenceId.toString() shouldBe "99819a74-3f1d-453b-b1d3-735d900cfc5d"
                payload.contentId shouldBe "f7aeef95-afca-4355-b6f7-1692e58c61cc"
                payload.contentType shouldBe "text/xml"
                payload.content.decodeToString() shouldBe "<?xml version=\"1.0\" encoding=\"utf-8\"?><dummy>xml 1</dummy>"
            }
        }

        "Get payloads - Several payloads" {
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
                val firstPayload = payloads.first()
                val lastPayload = payloads.last()
                firstPayload.referenceId.toString() shouldBe "df68056e-5cba-4351-9085-c37b925b8ddd"
                assert(listOf("tKV9FS_cSMy7IsQ41SHIUQ", "test").contains(payloads[0].contentId))
                assert(listOf("tKV9FS_cSMy7IsQ41SHIUQ", "test").contains(payloads[1].contentId))
                lastPayload.contentId shouldNotBe payloads[0].contentId
                firstPayload.contentType shouldBe "text/xml"
                lastPayload.contentType shouldBe "text/xml"
                firstPayload.content.decodeToString() shouldBeIn listOf(
                    "<?xml version=\"1.0\" encoding=\"utf-8\"?><dummy>xml 2</dummy>",
                    "<?xml version=\"1.0\" encoding=\"utf-8\"?><dummy>xml 3</dummy>"
                )
                lastPayload.content.decodeToString() shouldNotBe payloads[0].content.decodeToString()
            }
        }

        "Get payloads - Non found" {
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

        "Get payload - Invalid reference id" {
            smtpTransportTestApp(payloadRepository) {
                val httpClient = createClient {
                    install(ContentNegotiation) {
                        json()
                    }
                }
                val httpResponse: HttpResponse = httpClient.get("/api/payloads/invalid-reference-id") {
                    headers {
                        append(HttpHeaders.Authorization, "Bearer ${getToken(mockOAuth2Server).serialize()}")
                    }
                }
                httpResponse.status shouldBe HttpStatusCode.BadRequest
                httpResponse.bodyAsText() shouldBe "Invalid reference id (invalid-reference-id)"
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
    audience: String = config().azureAuth.azureAppClientId.value
): SignedJWT = mockOAuth2Server.issueToken(
    issuerId = config().azureAuth.azureAdAuth.value,
    audience = audience,
    subject = "testUser"
)
