package no.nav.emottak.publisher

import app.cash.turbine.test
import app.cash.turbine.turbineScope
import arrow.fx.coroutines.resourceScope
import io.github.nomisRev.kafka.receiver.KafkaReceiver
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.map
import no.nav.emottak.KafkaSpec
import no.nav.emottak.config
import no.nav.emottak.configuration.Config
import no.nav.emottak.configuration.withKafka
import no.nav.emottak.kafkaPublisher
import no.nav.emottak.model.PayloadMessage
import no.nav.emottak.model.SignalMessage
import no.nav.emottak.util.SENDER_ADDRESS
import no.nav.emottak.util.fakeEventLoggingService
import no.nav.emottak.utils.config.SecurityProtocol
import kotlin.uuid.Uuid

class MailPublisherSpec : KafkaSpec(
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

        "Publish payload message - message is received" {
            resourceScope {
                turbineScope {
                    val publisher = MailPublisher(kafkaPublisher(config.kafka), fakeEventLoggingService())

                    val referenceId = Uuid.random()
                    val content = "payload".toByteArray()
                    val senderAddress = "sender@address"
                    val payloadMessage = PayloadMessage(referenceId, content, emptyList())

                    publisher.publishPayloadMessage(payloadMessage, senderAddress)

                    val receiver = KafkaReceiver(receiverSettings())
                    val consumer = receiver.receive(config.kafkaTopics.payloadInTopic)
                        .map {
                            mapOf(
                                "key" to it.key(),
                                "value" to it.value(),
                                "senderAddress" to it.headers().lastHeader(SENDER_ADDRESS).value()
                            )
                        }

                    consumer.test {
                        val map = awaitItem()
                        map["key"] shouldBe referenceId.toString()
                        map["value"] shouldBe content
                        map["senderAddress"] shouldBe senderAddress.toByteArray()
                    }
                }
            }
        }

        "Publish signal message - message is received" {
            resourceScope {
                turbineScope {
                    val publisher = MailPublisher(kafkaPublisher(config.kafka), fakeEventLoggingService())

                    val referenceId = Uuid.random()
                    val content = "signal".toByteArray()
                    val senderAddress = "sender@address"
                    val signalMessage = SignalMessage(referenceId, content)

                    publisher.publishSignalMessage(signalMessage, senderAddress)

                    val receiver = KafkaReceiver(receiverSettings())
                    val consumer = receiver.receive(config.kafkaTopics.signalInTopic)
                        .map {
                            mapOf(
                                "key" to it.key(),
                                "value" to it.value(),
                                "senderAddress" to it.headers().lastHeader(SENDER_ADDRESS).value()
                            )
                        }

                    consumer.test {
                        val map = awaitItem()
                        map["key"] shouldBe referenceId.toString()
                        map["value"] shouldBe content
                        map["senderAddress"] shouldBe senderAddress.toByteArray()
                    }
                }
            }
        }
    }
)
