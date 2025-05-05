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
                    val payloadMessage = PayloadMessage(referenceId, content, emptyList())

                    publisher.publishPayloadMessage(payloadMessage)

                    val receiver = KafkaReceiver(receiverSettings())
                    val consumer = receiver.receive(config.kafkaTopics.payloadInTopic)
                        .map { Pair(it.key(), it.value()) }

                    consumer.test {
                        val (key, value) = awaitItem()
                        key shouldBe referenceId.toString()
                        value shouldBe content
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
                    val signalMessage = SignalMessage(referenceId, content)

                    publisher.publishSignalMessage(signalMessage)

                    val receiver = KafkaReceiver(receiverSettings())
                    val consumer = receiver.receive(config.kafkaTopics.signalInTopic)
                        .map { Pair(it.key(), it.value()) }

                    consumer.test {
                        val (key, value) = awaitItem()
                        key shouldBe referenceId.toString()
                        value shouldBe content
                    }
                }
            }
        }
    }
)
