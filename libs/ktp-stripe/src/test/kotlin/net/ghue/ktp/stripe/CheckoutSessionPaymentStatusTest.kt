package net.ghue.ktp.stripe

import com.stripe.model.checkout.Session
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class CheckoutSessionPaymentStatusTest :
    StringSpec({
        "toString returns lowercase name" {
            CheckoutSessionPaymentStatus.PAID.toString() shouldBe "paid"
            CheckoutSessionPaymentStatus.UNPAID.toString() shouldBe "unpaid"
            CheckoutSessionPaymentStatus.NO_PAYMENT_REQUIRED.toString() shouldBe
                "no_payment_required"
        }

        "fromString parses lowercase strings" {
            CheckoutSessionPaymentStatus.fromString("paid") shouldBe
                CheckoutSessionPaymentStatus.PAID
            CheckoutSessionPaymentStatus.fromString("unpaid") shouldBe
                CheckoutSessionPaymentStatus.UNPAID
            CheckoutSessionPaymentStatus.fromString("no_payment_required") shouldBe
                CheckoutSessionPaymentStatus.NO_PAYMENT_REQUIRED
        }

        "fromString parses uppercase strings" {
            CheckoutSessionPaymentStatus.fromString("PAID") shouldBe
                CheckoutSessionPaymentStatus.PAID
            CheckoutSessionPaymentStatus.fromString("UNPAID") shouldBe
                CheckoutSessionPaymentStatus.UNPAID
            CheckoutSessionPaymentStatus.fromString("NO_PAYMENT_REQUIRED") shouldBe
                CheckoutSessionPaymentStatus.NO_PAYMENT_REQUIRED
        }

        "toString and fromString are symmetric" {
            CheckoutSessionPaymentStatus.entries.forEach { status ->
                CheckoutSessionPaymentStatus.fromString(status.toString()) shouldBe status
            }
        }

        "fromString throws IllegalArgumentException for unknown values" {
            shouldThrow<IllegalArgumentException> {
                CheckoutSessionPaymentStatus.fromString("bogus")
            }
        }

        "isPaid returns true for PAID and NO_PAYMENT_REQUIRED" {
            CheckoutSessionPaymentStatus.PAID.isPaid shouldBe true
            CheckoutSessionPaymentStatus.NO_PAYMENT_REQUIRED.isPaid shouldBe true
            CheckoutSessionPaymentStatus.UNPAID.isPaid shouldBe false
        }

        "paymentStatusEnum parses the Stripe checkout session payment status string" {
            val session = Session().apply { paymentStatus = "paid" }

            session.paymentStatusEnum shouldBe CheckoutSessionPaymentStatus.PAID
        }
    })
