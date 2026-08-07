package no.nav.emottak.util

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.date.shouldBeAfter
import io.kotest.matchers.shouldBe
import no.nav.emottak.firstRunAt
import java.time.LocalDateTime
import java.time.LocalTime

class FirstRunAtSpec : StringSpec({
    val startAtTime = LocalTime.of(0, 0, 0) // Midnight

    "should return a date after now, at same time at startAtTime" {
        val now = LocalDateTime.parse("2026-08-15T10:00:00")
        val (firstRunAt, _) = startAtTime.firstRunAt(now)
        firstRunAt shouldBeAfter now
        firstRunAt.hour shouldBe 0
        firstRunAt.minute shouldBe 0
        firstRunAt.dayOfMonth shouldBe now.dayOfMonth + 1
    }

    "should return duration of 14h when startAtTime is 00:00, and now is 10:00" {
        val now = LocalDateTime.parse("2026-08-15T10:00:00")
        val (_, durationUntil) = startAtTime.firstRunAt(now)
        durationUntil.inWholeHours shouldBe 14
    }

    "should return duration of 23h when startAtTime is 00:00, and now is 01:00" {
        val now = LocalDateTime.parse("2026-08-15T01:00:00")
        val (_, durationUntil) = startAtTime.firstRunAt(now)
        durationUntil.inWholeHours shouldBe 23
    }

    "should return duration of 30min when startAtTime is 00:00, and now is 23:30" {
        val now = LocalDateTime.parse("2026-08-15T23:30:00")
        val (_, durationUntil) = startAtTime.firstRunAt(now)
        durationUntil.inWholeMinutes shouldBe 30
    }
})
