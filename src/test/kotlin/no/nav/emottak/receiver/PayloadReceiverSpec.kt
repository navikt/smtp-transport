package no.nav.emottak.receiver

import app.cash.turbine.test
import app.cash.turbine.turbineScope
import io.github.nomisRev.kafka.publisher.KafkaPublisher
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
import no.nav.emottak.kafkaReceiver
import no.nav.emottak.util.EbmsProviderClient
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

                val httpClient = httpClient(getFakeEngine(referenceId), config.ebmsProvider)
                val ebmsProviderClient = EbmsProviderClient(httpClient)
                val receiver = PayloadReceiver(kafkaReceiver(config.kafka), ebmsProviderClient)
                val payloadMessages = receiver.receivePayloadMessages()

                payloadMessages.test {
                    val payloadMessage = awaitItem()
                    payloadMessage.messageId shouldBe referenceId
                    payloadMessage.envelope shouldBe content
                }
            }
        }
    }
)

private fun getFakeEngine(referenceId: Uuid): MockEngine =
    MockEngine { _ ->
        respond(
            content = jsonResponse(referenceId),
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
