package no.nav.emottak.receiver

import app.cash.turbine.test
import app.cash.turbine.turbineScope
import arrow.fx.coroutines.resourceScope
import io.github.nomisRev.kafka.publisher.KafkaPublisher
import io.github.nomisRev.kafka.receiver.AutoOffsetReset
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headers
import no.nav.emottak.KafkaSpec
import no.nav.emottak.config
import no.nav.emottak.configuration.Config
import no.nav.emottak.configuration.SecurityProtocol
import no.nav.emottak.configuration.withKafka
import no.nav.emottak.httpClient
import no.nav.emottak.httpTokenClient
import no.nav.emottak.kafkaReceiver
import no.nav.emottak.util.EbmsAsyncClient
import org.apache.kafka.clients.producer.ProducerRecord
import kotlin.uuid.Uuid

class PayloadReceiverSpec : KafkaSpec(
    {
        lateinit var config: Config

        beforeSpec {
            config = config()
                .withKafka {
                    copy(
                        bootstrapServers = container.bootstrapServers,
                        securityProtocol = SecurityProtocol("PLAINTEXT")
                    )
                }
        }

        "Receive payload messages - one published message" {
            resourceScope {
                turbineScope {
                    val publisher = KafkaPublisher(publisherSettings())

                    val referenceId = Uuid.random()
                    val content = "data".toByteArray()

                    publisher.publishScope {
                        publish(
                            ProducerRecord(
                                config.kafka.payloadOutTopic,
                                referenceId.toString(),
                                content
                            )
                        )
                    }

                    val clientEngine = getFakeEngine(jsonResponse(referenceId))
                    val tokenClientEngine = getFakeEngine(jsonTokenResponse())
                    val httpTokenClient = httpTokenClient(tokenClientEngine, config)
                    val httpClient = httpClient(clientEngine, httpTokenClient, config)
                    val ebmsAsyncClient = EbmsAsyncClient(httpClient)
                    val receiver = PayloadReceiver(
                        kafkaReceiver(config.kafka, AutoOffsetReset.Earliest),
                        ebmsAsyncClient
                    )
                    val mailRoutingMessages = receiver.receiveMailRoutingMessages()

                    mailRoutingMessages.test {
                        val mailRoutingMessage = awaitItem()
                        val payloadMessage = mailRoutingMessage.payloadMessage
                        payloadMessage.messageId shouldBe referenceId
                        payloadMessage.envelope shouldBe content
                        payloadMessage.payloads shouldHaveSize 1

                        val payload = payloadMessage.payloads.first()
                        payload.referenceId shouldBe referenceId
                        payload.contentId shouldBe "content"
                        payload.contentType shouldBe "contentType"
                        payload.content contentEquals "data".toByteArray()
                    }
                }
            }
        }
    }
)

private fun getFakeEngine(content: String = ""): MockEngine =
    MockEngine { _ ->
        respond(
            content = content,
            status = HttpStatusCode.OK,
            headers = headers {
                append(HttpHeaders.ContentType, "application/json")
            }
        )
    }

private fun jsonResponse(referenceId: Uuid): String =
    """
            [
                {
                    "referenceId": $referenceId,
                    "contentId": "content",
                    "contentType": "contentType",
                    "content": [100, 97, 116, 97]
                }
            ]
    """
        .trimIndent()

private fun jsonTokenResponse(): String =
    """
            {
                "access_token": "token",
                "expires_in": 0,
                "token_type": "Bearer"
            }
    """
        .trimIndent()
