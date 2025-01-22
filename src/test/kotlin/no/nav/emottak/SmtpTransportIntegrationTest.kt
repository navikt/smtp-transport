package no.nav.emottak

import com.nimbusds.jwt.SignedJWT
import com.zaxxer.hikari.HikariDataSource
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
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.testcontainers.containers.PostgreSQLContainer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class SmtpTransportIntegrationTest {

    companion object {
        lateinit var mockOAuth2Server: MockOAuth2Server
        private val dbContainer: PostgreSQLContainer<Nothing> = smtpTransportPostgres("testDb/db.sql")
        lateinit var dbRepository: PayloadRepository

        @JvmStatic
        @BeforeAll
        fun setUpAll() {
            println("=== Initializing MockOAuth2Server ===")
            mockOAuth2Server = MockOAuth2Server().also { it.start(port = 3344) }

            println("=== Initializing Database ===")
            dbContainer.start()
            dbRepository = PayloadRepository(HikariDataSource(dbContainer.testConfiguration()).asPayloadDatabase())
        }

        @JvmStatic
        @AfterAll
        fun tearDownAll() {
            println("=== Stopping MockOAuth2Server ===")
            mockOAuth2Server.shutdown()
            println("=== Stopping Database ===")
            dbContainer.stop()
        }
    }

    private fun <T> smtpTransportTestApp(testBlock: suspend ApplicationTestBuilder.() -> T) = testApplication {
        application(smtpTransportModule(PrometheusMeterRegistry(PrometheusConfig.DEFAULT), dbRepository))
        testBlock()
    }

    @Test
    fun `Hent payload - ett treff`() = smtpTransportTestApp {
        val httpClient = createClient {
            install(ContentNegotiation) {
                json()
            }
        }
        val httpResponse: HttpResponse = httpClient.get("/payload/99819a74-3f1d-453b-b1d3-735d900cfc5d") {
            headers {
                append(HttpHeaders.Authorization, "Bearer ${getToken().serialize()}")
            }
        }
        assertEquals(HttpStatusCode.OK, httpResponse.status)
        val payloads: List<Payload> = httpResponse.body()
        assertEquals(1, payloads.size)

        assertEquals("99819a74-3f1d-453b-b1d3-735d900cfc5d", payloads[0].referenceId.toString())
        assertEquals("f7aeef95-afca-4355-b6f7-1692e58c61cc", payloads[0].contentId)
        assertEquals("text/xml", payloads[0].contentType)
        assertEquals("<?xml version=\"1.0\" encoding=\"utf-8\"?><dummy>xml 1</dummy>", payloads[0].content.decodeToString())
    }

    @Test
    fun `Hent payload - flere treff`() = smtpTransportTestApp {
        val httpClient = createClient {
            install(ContentNegotiation) {
                json()
            }
        }
        val httpResponse: HttpResponse = httpClient.get("/payload/df68056e-5cba-4351-9085-c37b925b8ddd") {
            headers {
                append(HttpHeaders.Authorization, "Bearer ${getToken().serialize()}")
            }
        }
        assertEquals(HttpStatusCode.OK, httpResponse.status)
        val payloads: List<Payload> = httpResponse.body()
        assertEquals(2, payloads.size)

        assertEquals("df68056e-5cba-4351-9085-c37b925b8ddd", payloads[0].referenceId.toString())
        assert(listOf("tKV9FS_cSMy7IsQ41SHIUQ", "test").contains(payloads[0].contentId))
        assert(listOf("tKV9FS_cSMy7IsQ41SHIUQ", "test").contains(payloads[1].contentId))
        assertNotEquals(payloads[0].contentId, payloads[1].contentId)
        assertEquals("text/xml", payloads[0].contentType)
        assertEquals("text/xml", payloads[1].contentType)
        assert(
            listOf(
                "<?xml version=\"1.0\" encoding=\"utf-8\"?><dummy>xml 2</dummy>",
                "<?xml version=\"1.0\" encoding=\"utf-8\"?><dummy>xml 3</dummy>"
            ).contains(payloads[0].content.decodeToString())
        )
        assertNotEquals(payloads[0].content.decodeToString(), payloads[1].content.decodeToString())
    }

    @Test
    fun `Hent payload - ingen treff`() = smtpTransportTestApp {
        val httpClient = createClient {
            install(ContentNegotiation) {
                json()
            }
        }
        val httpResponse: HttpResponse = httpClient.get("/payload/f7aeef95-afca-4355-b6f7-1692e58c61cc") {
            headers {
                append(HttpHeaders.Authorization, "Bearer ${getToken().serialize()}")
            }
        }
        assertEquals(HttpStatusCode.NotFound, httpResponse.status)
        val payloadNotFound: PayloadDoesNotExist = httpResponse.body()
        assertEquals("f7aeef95-afca-4355-b6f7-1692e58c61cc", payloadNotFound.referenceId)
    }

    @Test
    fun `Hent payload - ugyldig id`() = smtpTransportTestApp {
        val httpClient = createClient {
            install(ContentNegotiation) {
                json()
            }
        }
        val httpResponse: HttpResponse = httpClient.get("/payload/ugyldig-reference-id") {
            headers {
                append(HttpHeaders.Authorization, "Bearer ${getToken().serialize()}")
            }
        }
        assertEquals(HttpStatusCode.BadRequest, httpResponse.status)
        assertEquals("ReferenceId is not a valid UUID: 'ugyldig-reference-id'", httpResponse.bodyAsText())
    }

    private fun getToken(audience: String = AuthConfig.getScope()): SignedJWT = mockOAuth2Server.issueToken(
        issuerId = AZURE_AD_AUTH,
        audience = audience,
        subject = "testUser"
    )
}
