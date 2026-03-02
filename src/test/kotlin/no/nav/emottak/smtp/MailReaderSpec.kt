package no.nav.emottak.smtp

import arrow.fx.coroutines.resourceScope
import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.ServerSetupTest.SMTP_POP3
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.shouldBe
import jakarta.mail.internet.MimeMessage
import no.nav.emottak.config
import no.nav.emottak.session
import no.nav.emottak.store
import no.nav.emottak.util.fakeEventLoggingService
import no.nav.emottak.util.mapEmailMsg
import java.io.ByteArrayOutputStream
import java.nio.file.Path.of
import kotlin.uuid.Uuid

private const val REQUEST = "mails/test@test.test/INBOX/EgenAndelForespoersel.eml"
private const val EXAMPLE = "mails/test@test.test/INBOX/example.eml"

class MailReaderSpec : StringSpec({
    val config = config()

    val classLoader = this::class.java.classLoader

    val greenMail = GreenMail(SMTP_POP3)

    infix fun Part.shouldMatchBytes(expected: Part) {
        this.bytes.decodeToString() shouldBeEqual expected.bytes.decodeToString()
    }

    beforeEach {
        val smtp = config.smtp
        greenMail.setUser(smtp.username.value, smtp.username.value, smtp.password.value)
        greenMail.start()
        greenMail.loadEmails(of(classLoader.getResource("mails")!!.toURI()))
    }

    afterEach {
        greenMail.purgeEmailFromAllMailboxes()
        greenMail.stop()
    }

    "MailReader forwards inbox" {
        resourceScope {
            val store = store(config.smtp)
            val session = session(config.smtp)
            val eventLoggingService = fakeEventLoggingService()

            val reader = MailReader(config.mail, store, false, eventLoggingService)
            val messages = reader.readMailBatches(3)

            greenMail.receivedMessages.size shouldBe 3

            val mailSender = MailSender(session, fakeEventLoggingService())

            mailSender.rawForward(greenMail.receivedMessages[0])

            greenMail.receivedMessages.size shouldBe 4
            val bos = ByteArrayOutputStream()
            greenMail.receivedMessages[3].writeTo(bos)
            println("RAW Forwarded Message:")
            println(String(bos.toByteArray()))
        }
    }

    "MailReader reads inbox with messages and verifies content" {
        resourceScope {
            val store = store(config.smtp)
            val session = session(config.smtp)
            val eventLoggingService = fakeEventLoggingService()

            val reader = MailReader(config.mail, store, false, eventLoggingService)
            val messages = reader.readMailBatches(3)
            val multipartMessages = messages.filter { it.multipart }.sortedBy { it.headers.size }
            multipartMessages.size shouldBe 2

            val requestMessage = MimeMessage(session, classLoader.getResourceAsStream(REQUEST))
            var wrapper = MimeMessageWrapper(requestMessage, Uuid.random())
            val expectedFirstMessage = wrapper.mapEmailMsg()

            val firstMultipartMessage = multipartMessages.first()
            firstMultipartMessage.headers shouldBe expectedFirstMessage.headers
            firstMultipartMessage.parts.first() shouldMatchBytes expectedFirstMessage.parts.first()

            val exampleMessage = MimeMessage(session, classLoader.getResourceAsStream(EXAMPLE))
            wrapper = MimeMessageWrapper(exampleMessage, Uuid.random())
            val expectedLastMessage = wrapper.mapEmailMsg()

            val lastMultipartMessage = multipartMessages.last()
            lastMultipartMessage.headers shouldBe expectedLastMessage.headers
            lastMultipartMessage.parts.first() shouldMatchBytes expectedLastMessage.parts.first()

            messages.size shouldBe 3
            reader.readMailBatches(3).size shouldBe 0
        }
    }

    "MailReader reads inbox with messages and prunes messages accordingly" {
        resourceScope {
            val store = store(config.smtp)
            val eventLoggingService = fakeEventLoggingService()

            val inboxLimit100 = config.mail.copy(inboxLimit = 100)
            val reader = MailReader(inboxLimit100, store, false, eventLoggingService)

            val batch1 = reader.readMailBatches(3)
            batch1.size shouldBe 3
            batch1.forEach { reader.markDeleted(it.originalMimeMessage) }
            reader.readMailBatches(3).size shouldBe 0
            reader.close()

            MailReader(inboxLimit100, store, false, eventLoggingService).count() shouldBe 3

            val inboxLimitNegative1 = config.mail.copy(inboxLimit = -1)
            val reader2 = MailReader(inboxLimitNegative1, store, false, eventLoggingService)

            val batch2 = reader2.readMailBatches(3)
            batch2.size shouldBe 3
            batch2.forEach { reader2.markDeleted(it.originalMimeMessage) }
            reader2.readMailBatches(3).size shouldBe 0
            reader2.close()

            MailReader(inboxLimitNegative1, store, false, eventLoggingService).count() shouldBe 0
        }
    }

    "MailReader reads inbox with messages and filters on multipart messages" {
        resourceScope {
            val store = store(config.smtp)
            val session = session(config.smtp)
            val eventLoggingService = fakeEventLoggingService()

            val reader = MailReader(config.mail, store, false, eventLoggingService)
            val messages = reader.readMailBatches(4)

            val multipartMessages = messages.filter { it.multipart }.sortedBy { it.headers.size }
            multipartMessages.size shouldBe 2

            val requestMessage = MimeMessage(session, classLoader.getResourceAsStream(REQUEST))
            var wrapper = MimeMessageWrapper(requestMessage, Uuid.random())
            val mappedRequestMessage = wrapper.mapEmailMsg()

            val exampleMessage = MimeMessage(session, classLoader.getResourceAsStream(EXAMPLE))
            wrapper = MimeMessageWrapper(exampleMessage, Uuid.random())
            val mappedExampleMessage = wrapper.mapEmailMsg()

            val firstMultipartMessage = multipartMessages.first()
            val lastMultipartMessage = multipartMessages.last()

            firstMultipartMessage.headers.size shouldBeEqual mappedRequestMessage.headers.size
            firstMultipartMessage.parts.size shouldBeEqual mappedRequestMessage.parts.size

            lastMultipartMessage.headers.size shouldBeEqual mappedExampleMessage.headers.size
            lastMultipartMessage.parts.size shouldBeEqual mappedExampleMessage.parts.size
        }
    }

    "MailReader reads inbox with batch size set" {
        resourceScope {
            val store = store(config.smtp)
            val eventLoggingService = fakeEventLoggingService()

            val reader = MailReader(config.mail, store, false, eventLoggingService)
            val messages = reader.readMailBatches(1)
            reader.count() shouldBe 3
            messages.size shouldBe 1
        }
    }

    "MailReader expunges 'batchSize' messages accordingly" {
        resourceScope {
            val store = store(config.smtp)
            val eventLoggingService = fakeEventLoggingService()

            val mailConfig = config.mail

            // Read 1 email per batch. Should be one less email per batch retrieval
            // Inbox starts with 3 messages
            with(MailReader(mailConfig, store, true, eventLoggingService)) {
                this.count() shouldBe 3
                val batch1 = this.readMailBatches(1)
                batch1.size shouldBe 1
                batch1.forEach { this.markDeleted(it.originalMimeMessage) }
                this.close()
            }

            with(MailReader(mailConfig, store, true, eventLoggingService)) {
                this.count() shouldBe 2
                val batch2 = this.readMailBatches(1)
                batch2.size shouldBe 1
                batch2.forEach { this.markDeleted(it.originalMimeMessage) }
                this.close()
            }

            with(MailReader(mailConfig, store, true, eventLoggingService)) {
                this.count() shouldBe 1
                val batch3 = this.readMailBatches(1)
                batch3.size shouldBe 1
                batch3.forEach { this.markDeleted(it.originalMimeMessage) }
                this.close()
            }

            with(MailReader(mailConfig, store, true, eventLoggingService)) {
                this.count() shouldBe 0
                val batch4 = this.readMailBatches(1)
                batch4.size shouldBe 0
                batch4.forEach { this.markDeleted(it.originalMimeMessage) }
                this.close()
            }
        }
    }

    "MailReader expunges only messages marked for deletion" {
        resourceScope {
            val store = store(config.smtp)
            val eventLoggingService = fakeEventLoggingService()

            val mailConfig = config.mail

            val reader1 = MailReader(mailConfig, store, true, eventLoggingService)
            reader1.count() shouldBe 3
            val batch1 = reader1.readMailBatches(3)
            batch1.size shouldBe 3
            batch1[0].headers["Message-ID"] shouldBe "<f3da7fd6-5262-4383-9ed8-ec68936e8f55@link.visma.no>"
            batch1[1].headers["Message-ID"] shouldBe "<20231121144547.5CB191829F67@a01drvl071.adeo.no>"
            batch1[2].headers["Message-ID"] shouldBe "f3d09378-4f14-4ab9-abea-bd415606283f"
            reader1.markDeleted(batch1[1].originalMimeMessage)
            reader1.close()

            val reader2 = MailReader(mailConfig, store, true, eventLoggingService)
            reader2.count() shouldBe 2
            val batch2 = reader2.readMailBatches(3)
            batch2.size shouldBe 2
            batch2[0].headers["Message-ID"] shouldBe "<f3da7fd6-5262-4383-9ed8-ec68936e8f55@link.visma.no>"
            batch2[1].headers["Message-ID"] shouldBe "f3d09378-4f14-4ab9-abea-bd415606283f"
        }
    }
})
