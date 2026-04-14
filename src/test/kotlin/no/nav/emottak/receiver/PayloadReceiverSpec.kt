package no.nav.emottak.receiver

import app.cash.turbine.test
import app.cash.turbine.turbineScope
import arrow.fx.coroutines.resourceScope
import io.github.nomisRev.kafka.publisher.KafkaPublisher
import io.github.nomisRev.kafka.receiver.AutoOffsetReset
import io.github.nomisRev.kafka.receiver.KafkaReceiver
import io.github.nomisRev.kafka.receiver.Offset
import io.github.nomisRev.kafka.receiver.ReceiverRecord
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.json.Json
import no.nav.emottak.KafkaSpec
import no.nav.emottak.config
import no.nav.emottak.configuration.Config
import no.nav.emottak.configuration.withKafka
import no.nav.emottak.httpClient
import no.nav.emottak.httpTokenClient
import no.nav.emottak.kafkaReceiver
import no.nav.emottak.model.Attachment
import no.nav.emottak.model.SoapWithAttachments
import no.nav.emottak.util.EbmsAsyncClient
import no.nav.emottak.util.fakeEventLoggingService
import no.nav.emottak.utils.config.SecurityProtocol
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.header.internals.RecordHeaders
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
                                config.kafkaTopics.payloadOutTopic,
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
                        ebmsAsyncClient,
                        fakeEventLoggingService()
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
                        (payload.content contentEquals "data".toByteArray()) shouldBe true
                    }
                }
            }
        }

        "Receive payload messages from SoapWithAttachments header without HTTP payload lookup" {
            val referenceId = Uuid.random()
            val envelope = "envelope".toByteArray()
            val firstAttachmentContent = "first".toByteArray()
            val secondAttachmentContent = "second".toByteArray()
            val content = soapWithAttachmentsJson(
                SoapWithAttachments(
                    envelope = envelope,
                    attachments = listOf(
                        Attachment(
                            content = firstAttachmentContent,
                            contentId = "attachment-1",
                            contentType = "application/pdf"
                        ),
                        Attachment(
                            content = secondAttachmentContent,
                            contentId = "attachment-2",
                            contentType = "text/plain"
                        )
                    )
                )
            ).toByteArray()

            val ebmsAsyncClient = EbmsAsyncClient(HttpClient(getFailingEngine()))
            val receiver = PayloadReceiver(
                kafkaReceiver(receiverRecord(referenceId, content, withSoapWithAttachmentsHeader = true)),
                ebmsAsyncClient,
                fakeEventLoggingService()
            )

            receiver.receiveMailRoutingMessages().test {
                val payloadMessage = awaitItem().payloadMessage
                payloadMessage.messageId shouldBe referenceId
                payloadMessage.envelope shouldBe envelope
                payloadMessage.payloads shouldHaveSize 2

                payloadMessage.payloads[0].referenceId shouldBe referenceId
                payloadMessage.payloads[0].contentId shouldBe "attachment-1"
                payloadMessage.payloads[0].contentType shouldBe "application/pdf"
                (payloadMessage.payloads[0].content contentEquals firstAttachmentContent) shouldBe true

                payloadMessage.payloads[1].referenceId shouldBe referenceId
                payloadMessage.payloads[1].contentId shouldBe "attachment-2"
                payloadMessage.payloads[1].contentType shouldBe "text/plain"
                (payloadMessage.payloads[1].content contentEquals secondAttachmentContent) shouldBe true
                awaitComplete()
            }
        }

        "Receive payload messages from SoapWithAttachments header with no attachments" {
            val referenceId = Uuid.random()
            val envelope = "envelope".toByteArray()
            val content = soapWithAttachmentsJson(
                SoapWithAttachments(
                    envelope = envelope
                )
            ).toByteArray()

            val ebmsAsyncClient = EbmsAsyncClient(HttpClient(getFailingEngine()))
            val receiver = PayloadReceiver(
                kafkaReceiver(receiverRecord(referenceId, content, withSoapWithAttachmentsHeader = true)),
                ebmsAsyncClient,
                fakeEventLoggingService()
            )

            receiver.receiveMailRoutingMessages().test {
                val payloadMessage = awaitItem().payloadMessage
                payloadMessage.messageId shouldBe referenceId
                payloadMessage.envelope shouldBe envelope
                payloadMessage.payloads.shouldBeEmpty()
                awaitComplete()
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

private fun getFailingEngine(): MockEngine =
    MockEngine { request ->
        error("Unexpected HTTP request in SoapWithAttachments path: ${request.url}")
    }

private fun soapWithAttachmentsJson(soapWithAttachments: SoapWithAttachments): String =
    Json.encodeToString(soapWithAttachments)

private fun kafkaReceiver(record: ReceiverRecord<String, ByteArray>): KafkaReceiver<String, ByteArray> =
    object : KafkaReceiver<String, ByteArray> {
        override fun receive(topicNames: Collection<String>): Flow<ReceiverRecord<String, ByteArray>> =
            flowOf(record)

        override fun receiveAutoAck(topicNames: Collection<String>): Flow<Flow<ConsumerRecord<String, ByteArray>>> =
            flowOf(emptyFlow())

        override suspend fun <A> withConsumer(action: suspend KafkaConsumer<String, ByteArray>.(KafkaConsumer<String, ByteArray>) -> A): A =
            error("withConsumer is not used in this test")
    }

private fun receiverRecord(
    referenceId: Uuid,
    content: ByteArray,
    withSoapWithAttachmentsHeader: Boolean
): ReceiverRecord<String, ByteArray> {
    val headers = RecordHeaders().apply {
        if (withSoapWithAttachmentsHeader) {
            add(
                SoapWithAttachments.MESSAGE_FORMAT_HEADER,
                SoapWithAttachments.MESSAGE_FORMAT_VALUE.toByteArray()
            )
        }
    }

    return ReceiverRecord(
        ConsumerRecord(
            "payload-out-topic",
            0,
            0L,
            referenceId.toString(),
            content
        ).also { record ->
            headers.forEach { header -> record.headers().add(header) }
        },
        object : Offset {
            override val topicPartition: TopicPartition = TopicPartition("payload-out-topic", 0)
            override val offset: Long = 0L

            override suspend fun acknowledge() = Unit

            override suspend fun commit() = Unit
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
