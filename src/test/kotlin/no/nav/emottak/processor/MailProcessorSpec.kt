package no.nav.emottak.processor

import arrow.fx.coroutines.autoCloseable
import arrow.fx.coroutines.resourceScope
import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.ServerSetup
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import jakarta.mail.Folder
import jakarta.mail.Message.RecipientType.TO
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import no.nav.emottak.KafkaSpec
import no.nav.emottak.config
import no.nav.emottak.configuration.Config
import no.nav.emottak.configuration.ForwardingSystem
import no.nav.emottak.configuration.Host
import no.nav.emottak.configuration.Username
import no.nav.emottak.configuration.withKafka
import no.nav.emottak.kafkaPublisher
import no.nav.emottak.metricsRegistry
import no.nav.emottak.payloadDatabase
import no.nav.emottak.publisher.MailPublisher
import no.nav.emottak.repository.PayloadRepository
import no.nav.emottak.session
import no.nav.emottak.smtp.MailSender
import no.nav.emottak.store
import no.nav.emottak.util.SENDER_ADDRESS
import no.nav.emottak.util.fakeEventLoggingService
import no.nav.emottak.utils.config.Port
import no.nav.emottak.utils.config.SecurityProtocol
import org.apache.kafka.clients.admin.Admin
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.StringDeserializer
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

private const val LEGACY_ADDRESS = "legacy@test.test"
private const val SENDER_ADDRESS_VALUE = "sender@example.com"

class MailProcessorSpec : KafkaSpec({
    lateinit var testConfig: Config
    lateinit var greenMail: GreenMail
    lateinit var payloadRepository: PayloadRepository
    val classLoader = this::class.java.classLoader

    fun fixture(name: String): String =
        requireNotNull(classLoader.getResourceAsStream("xml/$name.xml"))
            .bufferedReader().use { it.readText() }

    beforeSpec {
        testConfig = config().withKafka {
            copy(
                bootstrapServers = container.bootstrapServers,
                securityProtocol = SecurityProtocol("PLAINTEXT")
            )
        }
        payloadRepository = PayloadRepository(payloadDatabase(), fakeEventLoggingService())
        Admin.create(mapOf(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to container.bootstrapServers)).use { admin ->
            admin.createTopics(
                listOf(testConfig.kafkaTopics.signalInTopic, testConfig.kafkaTopics.payloadInTopic)
                    .map { NewTopic(it, 1, 1.toShort()) }
            ).all().get(10, TimeUnit.SECONDS)
        }
    }

    beforeEach {
        greenMail = GreenMail(
            arrayOf(
                ServerSetup(0, "127.0.0.1", ServerSetup.PROTOCOL_SMTP),
                ServerSetup(0, "127.0.0.1", ServerSetup.PROTOCOL_POP3)
            )
        )
        greenMail.setUser(testConfig.smtp.username.value, testConfig.smtp.username.value, testConfig.smtp.password.value)
        greenMail.setUser(LEGACY_ADDRESS, LEGACY_ADDRESS, testConfig.smtp.password.value)
        greenMail.start()
    }

    afterEach {
        greenMail.stop()
    }

    val cases = listOf(
        Triple("acknowledgment_emottak", fixture("acknowledgment_emottak"), ForwardingSystem.EMOTTAK),
        Triple("acknowledgment_ebms", fixture("acknowledgment_ebms"), ForwardingSystem.EBMS),
        Triple("messageerror_emottak", fixture("messageerror_emottak"), ForwardingSystem.EMOTTAK),
        Triple("messageerror_ebms", fixture("messageerror_ebms"), ForwardingSystem.EBMS)
    ) + listOf("acknowledgment_ebms", "messageerror_ebms").flatMap { name ->
        listOf(null, "not-a-message-id").map { reference ->
            Triple(
                "$name with ${if (reference == null) "missing" else "unknown"} reference",
                fixture(name).replace(
                    Regex("<eb:RefToMessageId>[^<]*</eb:RefToMessageId>"),
                    reference?.let { "<eb:RefToMessageId>$it</eb:RefToMessageId>" } ?: ""
                ),
                ForwardingSystem.BOTH
            )
        }
    }

    for ((name, xml, forwardingSystem) in cases) {
        "MailProcessor delivers $name only to $forwardingSystem" {
            withTimeout(30.seconds) {
                resourceScope {
                    val smtp = testConfig.smtp.copy(
                        smtpHost = Host("127.0.0.1"),
                        smtpPort = Port(greenMail.smtp.port),
                        pop3Host = Host("127.0.0.1"),
                        pop3Port = Port(greenMail.pop3.port),
                        pop3FactoryPort = Port(greenMail.pop3.port),
                        smtpT1EmottakAddress = LEGACY_ADDRESS
                    )
                    val eventLoggingService = fakeEventLoggingService()
                    val registry = metricsRegistry()
                    val publisher = MailPublisher(kafkaPublisher(testConfig.kafka), eventLoggingService)
                    val sender = autoCloseable { MailSender(session(smtp), eventLoggingService, smtp, registry) }
                    val inboxStore = store(smtp)
                    val processor = MailProcessor(
                        inboxStore,
                        publisher,
                        payloadRepository,
                        eventLoggingService,
                        sender,
                        testConfig.mail.copy(inboxExpunge = true),
                        registry,
                        AtomicInteger(0)
                    )
                    val consumer = autoCloseable {
                        KafkaConsumer(
                            mapOf(
                                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to container.bootstrapServers,
                                ConsumerConfig.GROUP_ID_CONFIG to "mail-processor-${Uuid.random()}",
                                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false
                            ),
                            StringDeserializer(),
                            ByteArrayDeserializer()
                        )
                    }
                    val partitions = listOf(testConfig.kafkaTopics.signalInTopic, testConfig.kafkaTopics.payloadInTopic)
                        .map { TopicPartition(it, 0) }
                    consumer.assign(partitions)
                    consumer.seekToEnd(partitions)
                    // Resolve the end offsets before sending, so records from earlier cases are excluded.
                    partitions.forEach { consumer.position(it, Duration.ofSeconds(10)) }

                    Transport.send(
                        MimeMessage(session(smtp)).apply {
                            setFrom(InternetAddress(SENDER_ADDRESS_VALUE))
                            setRecipients(TO, smtp.username.value)
                            subject = name
                            setText(xml, "UTF-8", "xml")
                        }
                    )
                    // Capture the POP3 representation, including transport-added line endings.
                    val incomingEnvelope = inboxStore.getFolder("INBOX").apply {
                        open(Folder.READ_ONLY)
                    }.use { inbox ->
                        inbox.messageCount shouldBe 1
                        inbox.getMessage(1).inputStream.use { it.readAllBytes().decodeToString() }
                    }
                    coroutineScope {
                        processor.processMessages(this).await()
                    }

                    // Observe both topics even when no publication is expected.
                    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
                    val records = buildList {
                        do {
                            addAll(consumer.poll(Duration.ofMillis(100)).toList())
                        } while (System.nanoTime() < deadline)
                    }
                    val kafkaExpected = forwardingSystem != ForwardingSystem.EMOTTAK
                    records shouldHaveSize if (kafkaExpected) 1 else 0
                    if (kafkaExpected) {
                        val record = records.single()
                        record.topic() shouldBe testConfig.kafkaTopics.signalInTopic
                        record.value().decodeToString() shouldBe incomingEnvelope
                        record.headers().lastHeader(SENDER_ADDRESS).value().decodeToString() shouldBe SENDER_ADDRESS_VALUE
                        Uuid.parse(record.key()).toString() shouldBe record.key()
                    }

                    val smtpExpected = forwardingSystem != ForwardingSystem.EBMS
                    val legacyStore = store(smtp.copy(username = Username(LEGACY_ADDRESS)))
                    val legacyInbox = autoCloseable {
                        legacyStore.getFolder("INBOX").apply { open(Folder.READ_ONLY) }
                    }
                    legacyInbox.messageCount shouldBe if (smtpExpected) 1 else 0
                    if (smtpExpected) {
                        val forwarded = legacyInbox.getMessage(1)
                        forwarded.subject shouldBe name
                        forwarded.inputStream.use { it.readAllBytes().decodeToString() } shouldBe incomingEnvelope
                    }

                    val processedInbox = autoCloseable {
                        inboxStore.getFolder("INBOX").apply { open(Folder.READ_ONLY) }
                    }
                    processedInbox.messageCount shouldBe 0
                }
            }
        }
    }
})
