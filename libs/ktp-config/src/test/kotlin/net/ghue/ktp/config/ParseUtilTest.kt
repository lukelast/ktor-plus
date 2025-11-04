package net.ghue.ktp.config

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class ParseUtilTest :
    StringSpec({
        "parseDuration parses days" {
            parseDuration("7d") shouldBe 7.days
            parseDuration("1d") shouldBe 1.days
            parseDuration("30d") shouldBe 30.days
            parseDuration("365d") shouldBe 365.days
        }

        "parseDuration parses hours" {
            parseDuration("24h") shouldBe 24.hours
            parseDuration("1h") shouldBe 1.hours
            parseDuration("168h") shouldBe 168.hours
            parseDuration("720h") shouldBe 720.hours
        }

        "parseDuration parses minutes" {
            parseDuration("60m") shouldBe 60.minutes
            parseDuration("1m") shouldBe 1.minutes
            parseDuration("1440m") shouldBe 1440.minutes
            parseDuration("10080m") shouldBe 10080.minutes
        }

        "parseDuration parses seconds" {
            parseDuration("60s") shouldBe 60.seconds
            parseDuration("1s") shouldBe 1.seconds
            parseDuration("3600s") shouldBe 3600.seconds
            parseDuration("604800s") shouldBe 604800.seconds
        }

        "parseDuration handles whitespace" {
            parseDuration("  7d  ") shouldBe 7.days
            parseDuration(" 24h ") shouldBe 24.hours
            parseDuration("  60m") shouldBe 60.minutes
            parseDuration("30s  ") shouldBe 30.seconds
        }

        "parseDuration throws on empty string" {
            val exception = shouldThrow<IllegalArgumentException> { parseDuration("") }
            exception.message shouldContain "Duration string cannot be empty"
        }

        "parseDuration throws on whitespace-only string" {
            val exception = shouldThrow<IllegalArgumentException> { parseDuration("   ") }
            exception.message shouldContain "Duration string cannot be empty"
        }

        "parseDuration throws on invalid unit" {
            val exception = shouldThrow<IllegalArgumentException> { parseDuration("7x") }
            exception.message shouldContain "Invalid duration unit"
            exception.message shouldContain "x"
        }

        "parseDuration throws on missing value" {
            val exception = shouldThrow<IllegalArgumentException> { parseDuration("d") }
            exception.message shouldContain "Invalid duration format"
        }

        "parseDuration throws on non-numeric value" {
            val exception = shouldThrow<IllegalArgumentException> { parseDuration("abcd") }
            exception.message shouldContain "Invalid duration format"
        }

        "parseDuration throws on negative value" {
            val exception = shouldThrow<IllegalArgumentException> { parseDuration("-7d") }
            exception.message shouldContain "negative"
        }

        "parseDuration throws on decimal value" {
            val exception = shouldThrow<IllegalArgumentException> { parseDuration("7.5d") }
            exception.message shouldContain "Invalid duration format"
        }

        "parseDuration parses large values" {
            parseDuration("999999d") shouldBe 999999.days
            parseDuration("999999h") shouldBe 999999.hours
        }

        "parseDuration parses zero values" {
            parseDuration("0d") shouldBe 0.days
            parseDuration("0h") shouldBe 0.hours
            parseDuration("0m") shouldBe 0.minutes
            parseDuration("0s") shouldBe 0.seconds
        }
    })
