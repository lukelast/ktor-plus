package net.ghue.ktp.stripe

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class StripeActionTest :
    StringSpec({
        "fromString parses overdue, overpaid, payment_attempt_required, and will_be_due" {
            mapOf(
                    "overdue" to StripeAction.OVERDUE,
                    "overpaid" to StripeAction.OVERPAID,
                    "payment_attempt_required" to StripeAction.PAYMENT_ATTEMPT_REQUIRED,
                    "will_be_due" to StripeAction.WILL_BE_DUE,
                )
                .forEach { (value, expected) -> StripeAction.fromString(value) shouldBe expected }
        }
    })
