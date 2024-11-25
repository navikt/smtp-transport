package no.nav.emottak.smtp

import com.icegreen.greenmail.junit5.GreenMailExtension
import com.icegreen.greenmail.util.ServerSetupTest.SMTP_POP3
import jakarta.mail.Store
import no.nav.emottak.config
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.nio.file.Path
import kotlin.test.assertEquals

class GreenmailIT {
    private val config = config()

    @Test
    fun `MailReader reads correct inbox`() {
        val store = mockStore()
        val reader = MailReader(config.mail, store, false)
        assertEquals(1, reader.readMail().size)
        assertEquals(1, reader.readMail().size)
        assertEquals(1, reader.readMail().size)
        assertEquals(0, reader.readMail().size)
    }

    @Test
    fun `Verify that mail pruning works`() {
        // TODO Can't assert that inbox limit works
        val store = mockStore()

        val inboxLimit100 = config.mail.copy(inboxLimit = 100)
        val reader = MailReader(inboxLimit100, store, false)
        assertEquals(1, reader.readMail().size)
        assertEquals(1, reader.readMail().size)
        assertEquals(1, reader.readMail().size)
        assertEquals(0, reader.readMail().size)
        reader.close()
        assertEquals(3, MailReader(inboxLimit100, store).count())

        val inboxLimitNegative1 = config.mail.copy(inboxLimit = -1)
        val reader2 = MailReader(inboxLimitNegative1, store, false)

        assertEquals(1, reader2.readMail().size)
        assertEquals(1, reader2.readMail().size)
        assertEquals(1, reader2.readMail().size)
        assertEquals(0, reader2.readMail().size)
        reader2.close()
        assertEquals(0, MailReader(inboxLimitNegative1, store).count())
    }

    companion object {
        @JvmStatic
        @RegisterExtension
        var greenMail: GreenMailExtension = GreenMailExtension(SMTP_POP3)
    }

    private fun mockStore(): Store {
        greenMail.setUser("nyebmstest@test-es.nav.no", "nyebmstest@test-es.nav.no", "test1234")
        greenMail.loadEmails(Path.of(this::class.java.classLoader.getResource("mails")!!.toURI()))
        val store = greenMail.pop3.createStore()
        store.connect("nyebmstest@test-es.nav.no", "test1234")
        return store
    }
}
