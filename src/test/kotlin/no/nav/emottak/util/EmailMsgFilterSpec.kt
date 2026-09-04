package no.nav.emottak.util

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import jakarta.mail.internet.MimeMessage
import no.nav.emottak.config
import no.nav.emottak.configuration.ForwardingSystem
import no.nav.emottak.configuration.ServiceFilter
import no.nav.emottak.session
import no.nav.emottak.smtp.EmailMsg
import no.nav.emottak.smtp.MimeMessageWrapper
import kotlin.uuid.Uuid

private const val PAYLOAD_MESSAGE = "testmail/inntektsforesporsel.eml"
private const val PAYLOAD_MESSAGE_INVALID_CPAID = "testmail/inntektsforesporsel_invalid_cpaid.eml"
private const val PAYLOAD_MESSAGE_OTHER_SERVICE = "testmail/egenandelforesporsel.eml"
private const val SIGNAL_MESSAGE = "testmail/acknowledgment.eml"
private const val NOT_EBXML_MESSAGE = "testmail/not_ebxml.eml"
private const val EBXML_NO_SERVICE = "testmail/ebxml_no_service.eml"
private const val EBXML_NO_CPAID = "testmail/ebxml_no_cpaid.eml"

private const val INNTEKTSFORESPORSEL = "Inntektsforesporsel"
private const val TEST_CPA_IDS_FILE = "cpa/test/inntektsforesporsel.txt"
private const val CPAID_IN_TESTFILE = "nav:qass:34961"

class EmailMsgFilterSpec : StringSpec({
    val config = config()
    val classLoader = this::class.java.classLoader

    fun String.emlToEmailMsg(): EmailMsg =
        MimeMessageWrapper(
            mimeMessage = MimeMessage(session(config.smtp), classLoader.getResourceAsStream(this)),
            requestId = Uuid.random()
        ).mapEmailMsg()

    fun rulesOf(vararg services: ServiceFilter) = services.toList().toServiceRules()

    fun String.forwardingSystem(rules: Map<String, ServiceRule> = filterRules()): ForwardingSystem =
        emlToEmailMsg().filterMessageForwarding(rules).forwardingSystem

    "filterMessageForwarding returns BOTH when signal message service is configured as BOTH" {
        SIGNAL_MESSAGE.forwardingSystem() shouldBe ForwardingSystem.BOTH
    }

    "filterMessageForwarding returns EBMS when service is configured as EBMS" {
        PAYLOAD_MESSAGE.forwardingSystem() shouldBe ForwardingSystem.EBMS
    }

    "filterMessageForwarding returns EMOTTAK when service is not configured" {
        EBXML_NO_SERVICE.forwardingSystem() shouldBe ForwardingSystem.EMOTTAK
    }

    "filterMessageForwarding returns EMOTTAK when document is unparsable" {
        NOT_EBXML_MESSAGE.forwardingSystem() shouldBe ForwardingSystem.EMOTTAK
    }

    "filterMessageForwarding returns configured system when service CPA id is in the list" {
        val rules = rulesOf(ServiceFilter(INNTEKTSFORESPORSEL, false, "all", TEST_CPA_IDS_FILE))
        PAYLOAD_MESSAGE.forwardingSystem(rules) shouldBe ForwardingSystem.EBMS
    }

    "filterMessageForwarding matches CPA ids case-insensitively" {
        val rules = rulesOf(ServiceFilter("urn:oasis:names:tc:ebxml-msg:service", true, "all", TEST_CPA_IDS_FILE))
        SIGNAL_MESSAGE.forwardingSystem(rules) shouldBe ForwardingSystem.BOTH
    }

    "filterMessageForwarding returns EMOTTAK when service CPA id is not in the list" {
        val rules = rulesOf(ServiceFilter(INNTEKTSFORESPORSEL, false, "lastDigit9", TEST_CPA_IDS_FILE))
        PAYLOAD_MESSAGE_INVALID_CPAID.forwardingSystem(rules) shouldBe ForwardingSystem.EMOTTAK
    }

    "filterMessageForwarding returns EMOTTAK when CPA id is missing and service has an explicit list" {
        val rules = rulesOf(ServiceFilter(INNTEKTSFORESPORSEL, false, "lastDigit9", TEST_CPA_IDS_FILE))
        EBXML_NO_CPAID.forwardingSystem(rules) shouldBe ForwardingSystem.EMOTTAK
    }

    "filterMessageForwarding returns configured system for any CPA id when service is configured with all" {
        val rules = rulesOf(ServiceFilter(INNTEKTSFORESPORSEL, true, "all"))
        PAYLOAD_MESSAGE_INVALID_CPAID.forwardingSystem(rules) shouldBe ForwardingSystem.BOTH
    }

    "filterMessageForwarding returns EMOTTAK when another service is configured" {
        val rules = rulesOf(ServiceFilter(INNTEKTSFORESPORSEL, true, "all"))
        PAYLOAD_MESSAGE_OTHER_SERVICE.forwardingSystem(rules) shouldBe ForwardingSystem.EMOTTAK
    }

    "filterMessageForwarding matches service names case-sensitively" {
        val rules = rulesOf(ServiceFilter(INNTEKTSFORESPORSEL.uppercase(), true, "all"))
        PAYLOAD_MESSAGE.forwardingSystem(rules) shouldBe ForwardingSystem.EMOTTAK
    }

    "filterMessageForwarding drops the original message when forwarding to EBMS only" {
        val rules = rulesOf(ServiceFilter(INNTEKTSFORESPORSEL, false, "all", TEST_CPA_IDS_FILE))
        val forwardable = PAYLOAD_MESSAGE.emlToEmailMsg().filterMessageForwarding(rules)
        forwardable.forwardingSystem shouldBe ForwardingSystem.EBMS
        forwardable.forwardableMimeMessage.shouldBeNull()
    }

    "filterMessageForwarding keeps the original message when forwarding to EMOTTAK" {
        val rules = rulesOf(ServiceFilter(INNTEKTSFORESPORSEL, both = true, "none", TEST_CPA_IDS_FILE))
        val forwardable = PAYLOAD_MESSAGE_INVALID_CPAID.emlToEmailMsg().filterMessageForwarding(rules)
        forwardable.forwardingSystem shouldBe ForwardingSystem.EMOTTAK
        forwardable.forwardableMimeMessage.shouldNotBeNull()
        forwardable.service shouldBe INNTEKTSFORESPORSEL
        forwardable.cpaId shouldBe "nav:12345"
    }

    "envelope, payload and total sizes are reported for a multipart payload message" {
        val emailMsg = PAYLOAD_MESSAGE.emlToEmailMsg()
        emailMsg.multipart shouldBe true
        emailMsg.parts.size shouldBeGreaterThan 1
        emailMsg.envelopeSizeBytes shouldBeGreaterThan 0
        emailMsg.payloadSizeBytes shouldBeGreaterThan 0
        emailMsg.totalSizeBytes shouldBe emailMsg.envelopeSizeBytes + emailMsg.payloadSizeBytes
    }

    "payload size is zero for a singlepart signal message" {
        val emailMsg = SIGNAL_MESSAGE.emlToEmailMsg()
        emailMsg.multipart shouldBe false
        emailMsg.parts.size shouldBe 1
        emailMsg.payloadSizeBytes shouldBe 0
        emailMsg.envelopeSizeBytes shouldBeGreaterThan 0
        emailMsg.totalSizeBytes shouldBe emailMsg.envelopeSizeBytes
    }

    "building rules fails when the CPA id file is missing" {
        val exception = shouldThrow<IllegalStateException> {
            rulesOf(ServiceFilter(INNTEKTSFORESPORSEL, true, "all", "cpa/test/does-not-exist.txt"))
        }
        exception.message!! shouldContain "cpa/test/does-not-exist.txt"
    }

    "building rules fails when selection is invalid" {
        val exception = shouldThrow<IllegalStateException> {
            rulesOf(ServiceFilter(INNTEKTSFORESPORSEL, true, selection = "firstdigit1"))
        }
        exception.message!! shouldContain "selection"
    }

    "building rules fails when percentage is invalid" {
        val exception = shouldThrow<NumberFormatException> {
            rulesOf(ServiceFilter(INNTEKTSFORESPORSEL, true, selection = "percentageXX"))
        }
        exception.message!! shouldContain "XX"
    }

    "building rules fails when last digit setting is invalid" {
        val exception = shouldThrow<IllegalArgumentException> {
            rulesOf(ServiceFilter(INNTEKTSFORESPORSEL, true, selection = "lastDigitXX"))
        }
        exception.message!! shouldContain "digit"
    }

    "building rules fails on duplicate service names" {
        val exception = shouldThrow<IllegalStateException> {
            rulesOf(
                ServiceFilter(INNTEKTSFORESPORSEL, true, "all"),
                ServiceFilter(INNTEKTSFORESPORSEL, true, "all")
            )
        }
        exception.message!! shouldContain INNTEKTSFORESPORSEL
    }

/* Test-caser:
service (name) ikke konfigurert: gamle emottak
none: både split og both gir gamle emottak
all: split gir kun nye, both gir begge
lastdigit, hvis cpa ikke matcher: gir gamle uansett split/both
lastdigit, hvis cpa matcher: split/both gir nye/begge
percentage, som for lastdigit
whitelist match: alle ovenfor som ga gamle gir nye/begge
blacklist match: alle ovenfor som ga nye/begge gir gamle.
 */

    "filterMessageForwarding returns EMOTTAK when service is NOT configured" {
        val rules = rulesOf(ServiceFilter("someOtherService", true, "all"))
        PAYLOAD_MESSAGE.forwardingSystem(rules) shouldBe ForwardingSystem.EMOTTAK
    }

    "filterMessageForwarding returns EMOTTAK when service is configured with NONE + BOTH/SPLIT" {
        val rules = rulesOf(ServiceFilter(INNTEKTSFORESPORSEL, true, "none"))
        PAYLOAD_MESSAGE.forwardingSystem(rules) shouldBe ForwardingSystem.EMOTTAK
        val rules2 = rulesOf(ServiceFilter(INNTEKTSFORESPORSEL, false, "none"))
        PAYLOAD_MESSAGE.forwardingSystem(rules2) shouldBe ForwardingSystem.EMOTTAK
    }

    "filterMessageForwarding returns EBMS/BOTH when service is configured with ALL + SPLIT/BOTH" {
        val rules = rulesOf(ServiceFilter(INNTEKTSFORESPORSEL, false, "all"))
        PAYLOAD_MESSAGE.forwardingSystem(rules) shouldBe ForwardingSystem.EBMS
        val rules2 = rulesOf(ServiceFilter(INNTEKTSFORESPORSEL, true, "all"))
        PAYLOAD_MESSAGE.forwardingSystem(rules2) shouldBe ForwardingSystem.BOTH
    }

    "filterMessageForwarding returns EMOTTAK when service is configured with UNmatching CPA-ID lastdigit + BOTH/SPLIT" {
        val rules = rulesOf(ServiceFilter(INNTEKTSFORESPORSEL, true, "lastDigit234567890")) // 1 would match
        PAYLOAD_MESSAGE.forwardingSystem(rules) shouldBe ForwardingSystem.EMOTTAK
        val rules2 = rulesOf(ServiceFilter(INNTEKTSFORESPORSEL, false, "lastDigit234567890"))
        PAYLOAD_MESSAGE.forwardingSystem(rules2) shouldBe ForwardingSystem.EMOTTAK
    }

    "filterMessageForwarding returns EBMS/BOTH when service is configured with matching CPA-ID lastdigit + SPLIT/BOTH" {
        val rules = rulesOf(ServiceFilter(INNTEKTSFORESPORSEL, false, "lastDigit1"))
        PAYLOAD_MESSAGE.forwardingSystem(rules) shouldBe ForwardingSystem.EBMS
        val rules2 = rulesOf(ServiceFilter(INNTEKTSFORESPORSEL, true, "lastDigit1"))
        PAYLOAD_MESSAGE.forwardingSystem(rules2) shouldBe ForwardingSystem.BOTH
    }

    "filterMessageForwarding returns EMOTTAK when service is configured with UNmatching percentage + BOTH/SPLIT" {
        val rules = rulesOf(ServiceFilter(INNTEKTSFORESPORSEL, true, "percentage0")) // 0 percent will be routed
        PAYLOAD_MESSAGE.forwardingSystem(rules) shouldBe ForwardingSystem.EMOTTAK
        val rules2 = rulesOf(ServiceFilter(INNTEKTSFORESPORSEL, false, "percentage0"))
        PAYLOAD_MESSAGE.forwardingSystem(rules2) shouldBe ForwardingSystem.EMOTTAK
    }

    "filterMessageForwarding returns EBMS/BOTH when service is configured with matching percentage + SPLIT/BOTH" {
        val rules = rulesOf(ServiceFilter(INNTEKTSFORESPORSEL, false, "percentage101")) // 100 percent will be routed
        PAYLOAD_MESSAGE.forwardingSystem(rules) shouldBe ForwardingSystem.EBMS
        val rules2 = rulesOf(ServiceFilter(INNTEKTSFORESPORSEL, true, "percentage101"))
        PAYLOAD_MESSAGE.forwardingSystem(rules2) shouldBe ForwardingSystem.BOTH
    }

    "filterMessageForwarding returns EBMS/BOTH in all cases that normally give EMOTTAK when whitelist includes the CPA-ID" {
        val rules = rulesOf(ServiceFilter(INNTEKTSFORESPORSEL, true, "none", whitelist = TEST_CPA_IDS_FILE))
        PAYLOAD_MESSAGE.forwardingSystem(rules) shouldBe ForwardingSystem.BOTH
        val rules2 = rulesOf(ServiceFilter(INNTEKTSFORESPORSEL, false, "none", whitelist = TEST_CPA_IDS_FILE))
        PAYLOAD_MESSAGE.forwardingSystem(rules2) shouldBe ForwardingSystem.EBMS
        val rules3 = rulesOf(ServiceFilter(INNTEKTSFORESPORSEL, true, "lastDigit234567890", whitelist = TEST_CPA_IDS_FILE)) // 1 would match
        PAYLOAD_MESSAGE.forwardingSystem(rules3) shouldBe ForwardingSystem.BOTH
        val rules4 = rulesOf(ServiceFilter(INNTEKTSFORESPORSEL, false, "lastDigit234567890", whitelist = TEST_CPA_IDS_FILE))
        PAYLOAD_MESSAGE.forwardingSystem(rules4) shouldBe ForwardingSystem.EBMS
        val rule5 = rulesOf(ServiceFilter(INNTEKTSFORESPORSEL, true, "percentage0", whitelist = TEST_CPA_IDS_FILE))
        PAYLOAD_MESSAGE.forwardingSystem(rules) shouldBe ForwardingSystem.BOTH
        val rules6 = rulesOf(ServiceFilter(INNTEKTSFORESPORSEL, false, "percentage0", whitelist = TEST_CPA_IDS_FILE))
        PAYLOAD_MESSAGE.forwardingSystem(rules2) shouldBe ForwardingSystem.EBMS
    }

    "filterMessageForwarding returns EMOTTAK in all cases that normally give EBMS/BOTH when blacklist includes the CPA-ID" {
        val rules = rulesOf(ServiceFilter(INNTEKTSFORESPORSEL, false, "all", blacklist = TEST_CPA_IDS_FILE))
        PAYLOAD_MESSAGE.forwardingSystem(rules) shouldBe ForwardingSystem.EMOTTAK
        val rules2 = rulesOf(ServiceFilter(INNTEKTSFORESPORSEL, true, "all", blacklist = TEST_CPA_IDS_FILE))
        PAYLOAD_MESSAGE.forwardingSystem(rules2) shouldBe ForwardingSystem.EMOTTAK
        val rules3 = rulesOf(ServiceFilter(INNTEKTSFORESPORSEL, false, "lastDigit1", blacklist = TEST_CPA_IDS_FILE))
        PAYLOAD_MESSAGE.forwardingSystem(rules3) shouldBe ForwardingSystem.EMOTTAK
        val rules4 = rulesOf(ServiceFilter(INNTEKTSFORESPORSEL, true, "lastDigit1", blacklist = TEST_CPA_IDS_FILE))
        PAYLOAD_MESSAGE.forwardingSystem(rules4) shouldBe ForwardingSystem.EMOTTAK
        val rule5 = rulesOf(ServiceFilter(INNTEKTSFORESPORSEL, true, "percentage101", blacklist = TEST_CPA_IDS_FILE))
        PAYLOAD_MESSAGE.forwardingSystem(rules) shouldBe ForwardingSystem.EMOTTAK
        val rules6 = rulesOf(ServiceFilter(INNTEKTSFORESPORSEL, false, "percentage101", blacklist = TEST_CPA_IDS_FILE))
        PAYLOAD_MESSAGE.forwardingSystem(rules2) shouldBe ForwardingSystem.EMOTTAK
    }

    "resolveForwarding reports UNKNOWN_SERVICE when the service is not configured" {
        val rules = rulesOf(ServiceFilter(INNTEKTSFORESPORSEL, true, "all"))
        rules.resolveForwarding("UkonfigurertTjeneste", CPAID_IN_TESTFILE) shouldBe
            ForwardingDecision(ForwardingSystem.EMOTTAK, FilterMatch.UNKNOWN_SERVICE)
    }

    "resolveForwarding reports BOTH when last digit in CPA-ID matches, EMOTTAK otherwise" {
        val rules = rulesOf(ServiceFilter(INNTEKTSFORESPORSEL, true, "lastDigit24680"))
        rules.resolveForwarding(INNTEKTSFORESPORSEL, "cpaId1") shouldBe
            ForwardingDecision(ForwardingSystem.EMOTTAK, FilterMatch.BY_CPAID_MATCH)
        rules.resolveForwarding(INNTEKTSFORESPORSEL, "cpaId2") shouldBe
            ForwardingDecision(ForwardingSystem.BOTH, FilterMatch.BY_CPAID_MATCH)
        rules.resolveForwarding(INNTEKTSFORESPORSEL, "cpaId3") shouldBe
            ForwardingDecision(ForwardingSystem.EMOTTAK, FilterMatch.BY_CPAID_MATCH)
        rules.resolveForwarding(INNTEKTSFORESPORSEL, "cpaId4") shouldBe
            ForwardingDecision(ForwardingSystem.BOTH, FilterMatch.BY_CPAID_MATCH)
        rules.resolveForwarding(INNTEKTSFORESPORSEL, "cpaId5") shouldBe
            ForwardingDecision(ForwardingSystem.EMOTTAK, FilterMatch.BY_CPAID_MATCH)
        rules.resolveForwarding(INNTEKTSFORESPORSEL, "cpaId6") shouldBe
            ForwardingDecision(ForwardingSystem.BOTH, FilterMatch.BY_CPAID_MATCH)
        rules.resolveForwarding(INNTEKTSFORESPORSEL, "cpaId7") shouldBe
            ForwardingDecision(ForwardingSystem.EMOTTAK, FilterMatch.BY_CPAID_MATCH)
        rules.resolveForwarding(INNTEKTSFORESPORSEL, "cpaId8") shouldBe
            ForwardingDecision(ForwardingSystem.BOTH, FilterMatch.BY_CPAID_MATCH)
        rules.resolveForwarding(INNTEKTSFORESPORSEL, "cpaId9") shouldBe
            ForwardingDecision(ForwardingSystem.EMOTTAK, FilterMatch.BY_CPAID_MATCH)
        rules.resolveForwarding(INNTEKTSFORESPORSEL, "cpaId0") shouldBe
            ForwardingDecision(ForwardingSystem.BOTH, FilterMatch.BY_CPAID_MATCH)
    }

    "resolveForwarding reports BOTH when percentage setting gets hit, EMOTTAK otherwise" {
        val rules = rulesOf(ServiceFilter(INNTEKTSFORESPORSEL, true, "percentage50"))
        var count_both = 0
        var count_emottak = 0
        for (i in 1..1000) {
            val fwd = rules.resolveForwarding(INNTEKTSFORESPORSEL, "cpaId1").forwardTo
            if (fwd == ForwardingSystem.BOTH) count_both++
            if (fwd == ForwardingSystem.EMOTTAK) count_emottak++
        }
        println("count_both=$count_both, count_emottak=$count_emottak")
        count_both + count_emottak shouldBe 1000
        // Ideelt skal vi få 500 + 500 her, men random kan gi mye rart. Men hvis det er mindre enn 10 av den ene, funker ikke prosent-varianten særlig bra
        count_both shouldBeGreaterThan 1
        count_emottak shouldBeGreaterThan 1
    }

    "extracts email address only when From contains angle brackets" {
        "Kari Nordmann <no-reply@nav.no>".extractEmailAddressOnly() shouldBe "no-reply@nav.no"
    }

    "Returns lowercase email when From does not contain angle brackets" {
        "NO-REPLY@NAV.NO".extractEmailAddressOnly() shouldBe "no-reply@nav.no"
    }
})
