package net.ghue.ktp.stripe

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class PaymentStatusTest :
    StringSpec({
        "toString returns lowercase name" {
            PaymentStatus.PAID.toString() shouldBe "paid"
            PaymentStatus.UNPAID.toString() shouldBe "unpaid"
            PaymentStatus.NO_PAYMENT_REQUIRED.toString() shouldBe "no_payment_required"
        }

        "fromString parses lowercase strings" {
            PaymentStatus.fromString("paid") shouldBe PaymentStatus.PAID
            PaymentStatus.fromString("unpaid") shouldBe PaymentStatus.UNPAID
            PaymentStatus.fromString("no_payment_required") shouldBe
                PaymentStatus.NO_PAYMENT_REQUIRED
        }

        "fromString parses uppercase strings" {
            PaymentStatus.fromString("PAID") shouldBe PaymentStatus.PAID
            PaymentStatus.fromString("UNPAID") shouldBe PaymentStatus.UNPAID
            PaymentStatus.fromString("NO_PAYMENT_REQUIRED") shouldBe
                PaymentStatus.NO_PAYMENT_REQUIRED
        }

        "toString and fromString are symmetric" {
            PaymentStatus.entries.forEach { status ->
                PaymentStatus.fromString(status.toString()) shouldBe status
            }
        }

        "isPaid returns true for PAID and NO_PAYMENT_REQUIRED" {
            PaymentStatus.PAID.isPaid shouldBe true
            PaymentStatus.NO_PAYMENT_REQUIRED.isPaid shouldBe true
            PaymentStatus.UNPAID.isPaid shouldBe false
        }
    })
