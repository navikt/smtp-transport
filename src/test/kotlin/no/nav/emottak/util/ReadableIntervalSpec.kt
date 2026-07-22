package no.nav.emottak.util

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import no.nav.emottak.readableInterval
import kotlin.time.Duration

class ReadableIntervalSpec : StringSpec({

    "should return '1 days' when Duration is '1d'" {
        val interval = Duration.parse("1d")
        interval.readableInterval() shouldBe "1 days"
    }

    "should return '2 days' when Duration is '2d'" {
        val interval = Duration.parse("2d")
        interval.readableInterval() shouldBe "2 days"
    }

    "should return '12 hours' when Duration is '12h'" {
        val interval = Duration.parse("12h")
        interval.readableInterval() shouldBe "12 hours"
    }

    "should return '1 days, 12 hours' when Duration is '1d 12h'" {
        val interval = Duration.parse("1d 12h")
        interval.readableInterval() shouldBe "1 days, 12 hours"
    }

    "should return '2 hours, 30 minutes' when Duration is '2h 30m'" {
        val interval = Duration.parse("2h 30m")
        interval.readableInterval() shouldBe "2 hours, 30 minutes"
    }

    "should return '45 minutes' when Duration is '45m'" {
        val interval = Duration.parse("45m")
        interval.readableInterval() shouldBe "45 minutes"
    }

    "should return '3 days, 15 hours, 20 minutes' when Duration is '3d 15h 20m'" {
        val interval = Duration.parse("3d 15h 20m")
        interval.readableInterval() shouldBe "3 days, 15 hours, 20 minutes"
    }

    "should return '1 days, 20 minutes' when Duration is '1d 30m'" {
        val interval = Duration.parse("1d 20m")
        interval.readableInterval() shouldBe "1 days, 20 minutes"
    }

    "should return '1 days' when Duration is '24h'" {
        val interval = Duration.parse("24h")
        interval.readableInterval() shouldBe "1 days"
    }

    "should return '2 days' when Duration is '48h'" {
        val interval = Duration.parse("48h")
        interval.readableInterval() shouldBe "2 days"
    }

    "should return '2 days, 2 hours' when Duration is '50h'" {
        val interval = Duration.parse("50h")
        interval.readableInterval() shouldBe "2 days, 2 hours"
    }

    "should return '1 hours, 30 minutes' when Duration is '90h'" {
        val interval = Duration.parse("90m")
        interval.readableInterval() shouldBe "1 hours, 30 minutes"
    }

    "should return seconds when less than one minute" {
        val interval = Duration.parse("30s")
        interval.readableInterval() shouldBe "30 seconds"
    }

    "should not return seconds when more than one minute" {
        val interval = Duration.parse("90s")
        interval.readableInterval() shouldBe "1 minutes"
    }
})
