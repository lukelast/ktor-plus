package net.ghue.ktp.stripe

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class SubscriptionStatusTest :
    StringSpec({
        "toString returns lowercase name" {
            SubscriptionStatus.ACTIVE.toString() shouldBe "active"
            SubscriptionStatus.INCOMPLETE_EXPIRED.toString() shouldBe "incomplete_expired"
            SubscriptionStatus.TRIALING.toString() shouldBe "trialing"
            SubscriptionStatus.PAST_DUE.toString() shouldBe "past_due"
            SubscriptionStatus.CANCELED.toString() shouldBe "canceled"
            SubscriptionStatus.UNPAID.toString() shouldBe "unpaid"
            SubscriptionStatus.PAUSED.toString() shouldBe "paused"
        }

        "fromString parses lowercase strings" {
            SubscriptionStatus.fromString("active") shouldBe SubscriptionStatus.ACTIVE
            SubscriptionStatus.fromString("incomplete_expired") shouldBe
                SubscriptionStatus.INCOMPLETE_EXPIRED
            SubscriptionStatus.fromString("trialing") shouldBe SubscriptionStatus.TRIALING
            SubscriptionStatus.fromString("past_due") shouldBe SubscriptionStatus.PAST_DUE
            SubscriptionStatus.fromString("canceled") shouldBe SubscriptionStatus.CANCELED
            SubscriptionStatus.fromString("unpaid") shouldBe SubscriptionStatus.UNPAID
            SubscriptionStatus.fromString("paused") shouldBe SubscriptionStatus.PAUSED
        }

        "fromString parses uppercase strings" {
            SubscriptionStatus.fromString("ACTIVE") shouldBe SubscriptionStatus.ACTIVE
            SubscriptionStatus.fromString("INCOMPLETE_EXPIRED") shouldBe
                SubscriptionStatus.INCOMPLETE_EXPIRED
        }

        "fromString throws on invalid value" {
            shouldThrow<IllegalArgumentException> { SubscriptionStatus.fromString("invalid") }
        }

        "toString and fromString are symmetric" {
            SubscriptionStatus.entries.forEach { status ->
                SubscriptionStatus.fromString(status.toString()) shouldBe status
            }
        }
    })
