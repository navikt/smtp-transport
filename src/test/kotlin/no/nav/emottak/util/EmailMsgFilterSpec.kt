package no.nav.emottak.util

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class EmailMsgFilterSpec : StringSpec({

    "Returns BOTH when From and Subject are valid and signal message" {
        val headers = mapOf("From" to "no-reply@nav.no", "Subject" to "Acknowledgment")
        headers.filterMimeMessage() shouldBe ForwardingSystem.BOTH
    }

    "Returns EBMS when From and Subject are valid and accepted type" {
        val headers = mapOf("From" to "no-reply@nav.no", "Subject" to "Inntektsforesporsel")
        headers.filterMimeMessage() shouldBe ForwardingSystem.EBMS
    }

    "Returns EMOTTAK when From is missing" {
        val headers = mapOf("Subject" to "Acknowledgment")
        headers.filterMimeMessage() shouldBe ForwardingSystem.EMOTTAK
    }

    "Returns EMOTTAK when Subject is missing" {
        val headers = mapOf("From" to "no-reply@nav.no")
        headers.filterMimeMessage() shouldBe ForwardingSystem.EMOTTAK
    }

    "Returns EMOTTAK when From is not accepted" {
        val headers = mapOf("From" to "invalid@nav.no", "Subject" to "Acknowledgment")
        headers.filterMimeMessage() shouldBe ForwardingSystem.EMOTTAK
    }

    "extracts email address only when From contains angle brackets" {
        "Kari Nordmann <no-reply@nav.no>".extractEmailAddressOnly() shouldBe "no-reply@nav.no"
    }

    "Returns lowercase email when From does not contain angle brackets" {
        "NO-REPLY@NAV.NO".extractEmailAddressOnly() shouldBe "no-reply@nav.no"
    }
})
