package no.nav.emottak.util

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import no.nav.emottak.durationUntil
import java.time.LocalDateTime
import java.time.LocalTime

class DurationUntilSpec : StringSpec({
    val now = LocalDateTime.parse("2026-08-15T10:00:00")

    "should return Duration of 2 hours when 'now' is 10:00 and LocalTime is 12:00" {
        val time = LocalTime.parse("12:00")
        val duration = time.durationUntil(now)
        duration.inWholeHours shouldBe 2
    }

    "should return Duration of 90 minutes when 'now' is 10:00 and LocalTime is 11:30" {
        val time = LocalTime.parse("11:30")
        val duration = time.durationUntil(now)
        duration.inWholeMinutes shouldBe 90
    }

    "should return Duration of 90 minutes when 'now' is 10:00 and LocalTime is 20:00" {
        val time = LocalTime.parse("20:00")
        val duration = time.durationUntil(now)
        duration.inWholeHours shouldBe 10
    }

    "should return Duration of 23 hours when 'now' is 10:00 and LocalTime is 09:00" {
        val time = LocalTime.parse("09:00")
        val duration = time.durationUntil(now)
        duration.inWholeHours shouldBe 23
    }
})
