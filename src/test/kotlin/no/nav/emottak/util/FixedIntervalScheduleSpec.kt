package no.nav.emottak.util

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import no.nav.emottak.FixedIntervalSchedule
import no.nav.emottak.firstRunAt
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.time.Duration

class FixedIntervalScheduleSpec : StringSpec({
    val startAtTime = LocalTime.of(0, 0, 0) // Midnight
    val start = LocalDateTime.parse("2026-08-15T10:00:00")
    val (firstRunAt, _) = startAtTime.firstRunAt(start) // "2026-08-16T00:00:00"
    val now = start.plusDays(1) // "2026-08-16T10:00:00"

    "should return 2h when fixedInterval is 6h, 'now' is 10:00 and startAtTime is 00:00" {
        val schedule = FixedIntervalSchedule(firstRunAt, Duration.parse("6h"))
        val duration = schedule.durationUntilNextRun(now)
        duration.inWholeHours shouldBe 2
    }

    "should return 90min when fixedInterval is 6h, 'now' is 10:30 and startAtTime is 00:00" {
        val schedule = FixedIntervalSchedule(firstRunAt, Duration.parse("6h"))
        val duration = schedule.durationUntilNextRun(now.plusMinutes(30))
        duration.inWholeMinutes shouldBe 90
    }

    "should return 6h when fixedInterval is 6h, 'now' is 12:00 and startAtTime is 00:00" {
        val schedule = FixedIntervalSchedule(firstRunAt, Duration.parse("6h"))
        val duration = schedule.durationUntilNextRun(now.plusHours(2))
        duration.inWholeHours shouldBe 6
    }

    "should return 14h when fixedInterval is 24h, 'now' is 10:00 and startAtTime is 00:00" {
        val schedule = FixedIntervalSchedule(firstRunAt, Duration.parse("24h"))
        val duration = schedule.durationUntilNextRun(now)
        duration.inWholeHours shouldBe 14
    }

    "should return 38h when fixedInterval is 48h, 'now' is 10:00 same day as startAtTime, and startAtTime is 00:00" {
        val schedule = FixedIntervalSchedule(firstRunAt, Duration.parse("48h"))
        val duration = schedule.durationUntilNextRun(now)
        duration.inWholeHours shouldBe 38
    }

    "should return 14h when fixedInterval is 48h, 'now' is 10:00 the day after startAtTime, and startAtTime is 00:00" {
        val schedule = FixedIntervalSchedule(firstRunAt, Duration.parse("48h"))
        val duration = schedule.durationUntilNextRun(now.plusDays(1))
        duration.inWholeHours shouldBe 14
    }

    "should return 27h when fixedInterval is 48h, 'now' is 21:00 4 days after startAtTime, and startAtTime is 00:00" {
        val schedule = FixedIntervalSchedule(firstRunAt, Duration.parse("48h"))
        val duration = schedule.durationUntilNextRun(now.plusDays(4).plusHours(11))
        duration.inWholeHours shouldBe 27
    }

    "should return 3h when fixedInterval is 48h, 'now' is 21:00 5 days after startAtTime, and startAtTime is 00:00" {
        val schedule = FixedIntervalSchedule(firstRunAt, Duration.parse("48h"))
        val duration = schedule.durationUntilNextRun(now.plusDays(5).plusHours(11))
        duration.inWholeHours shouldBe 3
    }
})
