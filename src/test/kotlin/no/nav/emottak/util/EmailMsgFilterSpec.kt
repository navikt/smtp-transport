package no.nav.emottak.util

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
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
        val rules = rulesOf(ServiceFilter(INNTEKTSFORESPORSEL, ForwardingSystem.EBMS, TEST_CPA_IDS_FILE))
        PAYLOAD_MESSAGE.forwardingSystem(rules) shouldBe ForwardingSystem.EBMS
    }

    "filterMessageForwarding matches CPA ids case-insensitively" {
        val rules = rulesOf(ServiceFilter("urn:oasis:names:tc:ebxml-msg:service", ForwardingSystem.BOTH, TEST_CPA_IDS_FILE))
        SIGNAL_MESSAGE.forwardingSystem(rules) shouldBe ForwardingSystem.BOTH
    }

    "filterMessageForwarding returns EMOTTAK when service CPA id is not in the list" {
        val rules = rulesOf(ServiceFilter(INNTEKTSFORESPORSEL, ForwardingSystem.EBMS, TEST_CPA_IDS_FILE))
        PAYLOAD_MESSAGE_INVALID_CPAID.forwardingSystem(rules) shouldBe ForwardingSystem.EMOTTAK
    }

    "filterMessageForwarding returns EMOTTAK when CPA id is missing and service has an explicit list" {
        val rules = rulesOf(ServiceFilter(INNTEKTSFORESPORSEL, ForwardingSystem.EBMS, TEST_CPA_IDS_FILE))
        EBXML_NO_CPAID.forwardingSystem(rules) shouldBe ForwardingSystem.EMOTTAK
    }

    "filterMessageForwarding returns configured system for any CPA id when service is configured with all" {
        val rules = rulesOf(ServiceFilter(INNTEKTSFORESPORSEL, ForwardingSystem.BOTH, "all"))
        PAYLOAD_MESSAGE_INVALID_CPAID.forwardingSystem(rules) shouldBe ForwardingSystem.BOTH
    }

    "filterMessageForwarding returns EMOTTAK when another service is configured" {
        val rules = rulesOf(ServiceFilter(INNTEKTSFORESPORSEL, ForwardingSystem.EBMS, "all"))
        PAYLOAD_MESSAGE_OTHER_SERVICE.forwardingSystem(rules) shouldBe ForwardingSystem.EMOTTAK
    }

    "filterMessageForwarding matches service names case-sensitively" {
        val rules = rulesOf(ServiceFilter(INNTEKTSFORESPORSEL.uppercase(), ForwardingSystem.EBMS, "all"))
        PAYLOAD_MESSAGE.forwardingSystem(rules) shouldBe ForwardingSystem.EMOTTAK
    }

    "filterMessageForwarding drops the original message when forwarding to EBMS only" {
        val rules = rulesOf(ServiceFilter(INNTEKTSFORESPORSEL, ForwardingSystem.EBMS, TEST_CPA_IDS_FILE))
        val forwardable = PAYLOAD_MESSAGE.emlToEmailMsg().filterMessageForwarding(rules)
        forwardable.forwardingSystem shouldBe ForwardingSystem.EBMS
        forwardable.forwardableMimeMessage.shouldBeNull()
    }

    "filterMessageForwarding keeps the original message when forwarding to EMOTTAK" {
        val rules = rulesOf(ServiceFilter(INNTEKTSFORESPORSEL, ForwardingSystem.EBMS, TEST_CPA_IDS_FILE))
        val forwardable = PAYLOAD_MESSAGE_INVALID_CPAID.emlToEmailMsg().filterMessageForwarding(rules)
        forwardable.forwardingSystem shouldBe ForwardingSystem.EMOTTAK
        forwardable.forwardableMimeMessage.shouldNotBeNull()
        forwardable.service shouldBe INNTEKTSFORESPORSEL
        forwardable.cpaId shouldBe "nav:12345"
    }

    "building rules fails when the CPA id file is missing" {
        val exception = shouldThrow<IllegalStateException> {
            rulesOf(ServiceFilter(INNTEKTSFORESPORSEL, ForwardingSystem.EBMS, "cpa/test/does-not-exist.txt"))
        }
        exception.message!! shouldContain "cpa/test/does-not-exist.txt"
    }

    "building rules fails on duplicate service names" {
        val exception = shouldThrow<IllegalStateException> {
            rulesOf(
                ServiceFilter(INNTEKTSFORESPORSEL, ForwardingSystem.EBMS, "all"),
                ServiceFilter(INNTEKTSFORESPORSEL, ForwardingSystem.BOTH, "all")
            )
        }
        exception.message!! shouldContain INNTEKTSFORESPORSEL
    }

    "building rules allows an explicit EMOTTAK service when all CPA ids are accepted" {
        val rules = rulesOf(ServiceFilter(INNTEKTSFORESPORSEL, ForwardingSystem.EMOTTAK, "all"))
        rules[INNTEKTSFORESPORSEL].shouldNotBeNull().forwardTo shouldBe ForwardingSystem.EMOTTAK
    }

    "filterMessageForwarding returns EMOTTAK when service is explicitly configured as EMOTTAK" {
        val rules = rulesOf(ServiceFilter(INNTEKTSFORESPORSEL, ForwardingSystem.EMOTTAK, "all"))
        PAYLOAD_MESSAGE.forwardingSystem(rules) shouldBe ForwardingSystem.EMOTTAK
    }

    "building rules fails when an EMOTTAK service declares an explicit CPA id list" {
        val exception = shouldThrow<IllegalStateException> {
            rulesOf(ServiceFilter(INNTEKTSFORESPORSEL, ForwardingSystem.EMOTTAK, TEST_CPA_IDS_FILE))
        }
        exception.message!! shouldContain "EMOTTAK"
    }

    "resolveForwarding reports CONFIGURED when the service and CPA id both match" {
        val rules = rulesOf(ServiceFilter(INNTEKTSFORESPORSEL, ForwardingSystem.EBMS, TEST_CPA_IDS_FILE))
        rules.resolveForwarding(INNTEKTSFORESPORSEL, "nav:qass:34961") shouldBe
            ForwardingDecision(ForwardingSystem.EBMS, FilterMatch.CONFIGURED)
    }

    "resolveForwarding reports CONFIGURED for an explicit EMOTTAK service" {
        val rules = rulesOf(ServiceFilter(INNTEKTSFORESPORSEL, ForwardingSystem.EMOTTAK, "all"))
        rules.resolveForwarding(INNTEKTSFORESPORSEL, "nav:whatever") shouldBe
            ForwardingDecision(ForwardingSystem.EMOTTAK, FilterMatch.CONFIGURED)
    }

    "resolveForwarding reports CPA_ID_NOT_IN_LIST when the CPA id is not accepted" {
        val rules = rulesOf(ServiceFilter(INNTEKTSFORESPORSEL, ForwardingSystem.EBMS, TEST_CPA_IDS_FILE))
        rules.resolveForwarding(INNTEKTSFORESPORSEL, "nav:12345") shouldBe
            ForwardingDecision(ForwardingSystem.EMOTTAK, FilterMatch.CPA_ID_NOT_IN_LIST)
    }

    "resolveForwarding reports UNKNOWN_SERVICE when the service is not configured" {
        val rules = rulesOf(ServiceFilter(INNTEKTSFORESPORSEL, ForwardingSystem.EBMS, "all"))
        rules.resolveForwarding("UkonfigurertTjeneste", "nav:qass:34961") shouldBe
            ForwardingDecision(ForwardingSystem.EMOTTAK, FilterMatch.UNKNOWN_SERVICE)
    }

    "extracts email address only when From contains angle brackets" {
        "Kari Nordmann <no-reply@nav.no>".extractEmailAddressOnly() shouldBe "no-reply@nav.no"
    }

    "Returns lowercase email when From does not contain angle brackets" {
        "NO-REPLY@NAV.NO".extractEmailAddressOnly() shouldBe "no-reply@nav.no"
    }
})
