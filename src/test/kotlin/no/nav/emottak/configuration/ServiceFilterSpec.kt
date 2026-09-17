package no.nav.emottak.configuration

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

private const val SERVICE_NAME = "Inntektsforesporsel"
private const val CPA_LIST_FILE = "cpa/test/inntektsforesporsel.txt"

class ServiceFilterSpec : StringSpec({

    "selection uses the explicit value when set" {
        ServiceFilter(name = SERVICE_NAME, selection = "all").selection shouldBe "all"
        ServiceFilter(name = SERVICE_NAME, selection = "none").selection shouldBe "none"
        ServiceFilter(name = SERVICE_NAME, selection = "lastDigit1").selection shouldBe "lastDigit1"
    }

    "selection defaults to NONE when only whitelist is set" {
        ServiceFilter(name = SERVICE_NAME, whitelist = CPA_LIST_FILE).selection shouldBe "NONE"
    }

    "selection defaults to ALL when only blacklist is set" {
        ServiceFilter(name = SERVICE_NAME, blacklist = CPA_LIST_FILE).selection shouldBe "ALL"
    }

    "selection defaults to NONE when both whitelist and blacklist are set (whitelist takes precedence)" {
        ServiceFilter(name = SERVICE_NAME, whitelist = CPA_LIST_FILE, blacklist = CPA_LIST_FILE).selection shouldBe "NONE"
    }

    "constructing ServiceFilter fails when neither selection, whitelist nor blacklist is set" {
        val exception = shouldThrow<IllegalStateException> {
            ServiceFilter(name = SERVICE_NAME)
        }
        exception.message!! shouldContain SERVICE_NAME
        exception.message!! shouldContain "Selection"
    }

    "constructing ServiceFilter fails when whitelist and blacklist are both blank and selection is not set" {
        val exception = shouldThrow<IllegalStateException> {
            ServiceFilter(name = SERVICE_NAME, whitelist = "", blacklist = "  ")
        }
        exception.message!! shouldContain SERVICE_NAME
    }

    "constructing ServiceFilter fails when whitelist alone is blank and selection is not set" {
        shouldThrow<IllegalStateException> {
            ServiceFilter(name = SERVICE_NAME, whitelist = "   ")
        }
    }

    "both flag defaults to false" {
        ServiceFilter(name = SERVICE_NAME, selection = "all").both shouldBe false
    }
})
