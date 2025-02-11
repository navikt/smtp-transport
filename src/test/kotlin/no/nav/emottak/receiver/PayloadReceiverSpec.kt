package no.nav.emottak.receiver

import app.cash.turbine.test
import app.cash.turbine.turbineScope
import io.github.nomisRev.kafka.publisher.KafkaPublisher
import io.kotest.matchers.shouldBe
import no.nav.emottak.KafkaSpec
import no.nav.emottak.config
import no.nav.emottak.configuration.Config
import no.nav.emottak.configuration.SecurityProtocol
import no.nav.emottak.configuration.withKafka
import no.nav.emottak.kafkaReceiver
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
                val content = "payload".toByteArray()

                publisher.publishScope {
                    publish(
                        ProducerRecord(
                            config.kafka.payloadOutTopic,
                            referenceId.toString(),
                            content
                        )
                    )
                }

                val receiver = PayloadReceiver(kafkaReceiver(config.kafka))
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
