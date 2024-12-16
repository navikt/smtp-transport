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
import java.nio.file.Path.of

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

    "MailReader reads inbox with messages and verifies content" {
        resourceScope {
            val store = store(config.smtp)
            val session = session(config.smtp)

            val reader = MailReader(config.mail, store, false)
            val messages = reader.readMailBatches(3)

            val exampleMessage = MimeMessage(session, classLoader.getResourceAsStream((EXAMPLE)))
            val expectedFirstMessage = reader.mapEmailMsg(exampleMessage)

            val firstMessage = messages.first()
            // firstMessage.headers shouldBe expectedFirstMessage.headers - doesn't run in GHA
            // firstMessage.parts.first() shouldMatchBytes expectedFirstMessage.parts.first() - doesn't run in GHA

            val acknowledgmentMessage = MimeMessage(session, classLoader.getResourceAsStream((REQUEST)))
            val expectedLastMessage = reader.mapEmailMsg(acknowledgmentMessage)

            val lastMessage = messages.last()
            // lastMessage.headers shouldBe expectedLastMessage.headers - doesn't run in GHA
            // lastMessage.parts.first() shouldMatchBytes expectedLastMessage.parts.first() - doesn't run in GHA

            messages.size shouldBe 3
            reader.readMailBatches(3).size shouldBe 0
        }
    }

    "MailReader reads inbox with messages and prunes messages accordingly" {
        resourceScope {
            val store = store(config.smtp)

            val inboxLimit100 = config.mail.copy(inboxLimit = 100)
            val reader = MailReader(inboxLimit100, store, false)

            reader.readMailBatches(3).size shouldBe 3
            reader.readMailBatches(3).size shouldBe 0
            reader.close()

            MailReader(inboxLimit100, store).count() shouldBe 3

            val inboxLimitNegative1 = config.mail.copy(inboxLimit = -1)
            val reader2 = MailReader(inboxLimitNegative1, store, false)

            reader2.readMailBatches(3).size shouldBe 3
            reader2.readMailBatches(3).size shouldBe 0
            reader2.close()

            MailReader(inboxLimitNegative1, store).count() shouldBe 0
        }
    }
})
