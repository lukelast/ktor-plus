package net.ghue.ktp.stripe

import com.stripe.model.Event
import com.stripe.model.StripeObject
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class StripeWebhookEventTest :
    StringSpec({
        "parses a checkout session event into a typed id" {
            val event =
                stripeEvent(
                    type = "checkout.session.completed",
                    objectJson = """{"id": "cs_test_123", "object": "checkout.session"}""",
                )

            val webhookEvent = event.toWebhookEvent()

            webhookEvent.eventId shouldBe StripeEventId("evt_test_123")
            webhookEvent.type shouldBe "checkout.session.completed"
            webhookEvent.actionRaw shouldBe "completed"
            webhookEvent.action shouldBe StripeAction.COMPLETED
            webhookEvent.objectTypeRaw shouldBe "checkout.session"
            webhookEvent.objectType shouldBe StripeObjectType.CHECKOUT_SESSION
            webhookEvent.objectId shouldBe CheckoutSessionId("cs_test_123")
        }

        "parses a subscription event even when the api version predates the sdk" {
            val event =
                stripeEvent(
                    type = "customer.subscription.updated",
                    objectJson = """{"id": "sub_test_123", "object": "subscription"}""",
                    apiVersion = "2015-01-01",
                )

            val webhookEvent = event.toWebhookEvent()

            webhookEvent.action shouldBe StripeAction.UPDATED
            webhookEvent.objectId shouldBe SubscriptionId("sub_test_123")
        }

        "maps invoice and customer object types" {
            stripeEvent(
                    type = "invoice.paid",
                    objectJson = """{"id": "in_test_123", "object": "invoice"}""",
                )
                .toWebhookEvent()
                .objectId shouldBe InvoiceId("in_test_123")

            stripeEvent(
                    type = "customer.updated",
                    objectJson = """{"id": "cus_test_123", "object": "customer"}""",
                )
                .toWebhookEvent()
                .objectId shouldBe CustomerId("cus_test_123")
        }

        "maps all documented invoice actions" {
            mapOf(
                    "overdue" to StripeAction.OVERDUE,
                    "overpaid" to StripeAction.OVERPAID,
                    "payment_attempt_required" to StripeAction.PAYMENT_ATTEMPT_REQUIRED,
                    "will_be_due" to StripeAction.WILL_BE_DUE,
                )
                .forEach { (value, expected) -> StripeAction.fromString(value) shouldBe expected }
        }

        "unmodeled object types have no typed id but keep the raw fields" {
            val event =
                stripeEvent(
                    type = "charge.refunded",
                    objectJson = """{"id": "ch_test_123", "object": "charge"}""",
                )

            val webhookEvent = event.toWebhookEvent()

            webhookEvent.objectId shouldBe null
            webhookEvent.objectTypeRaw shouldBe "charge"
            webhookEvent.objectType shouldBe StripeObjectType.UNKNOWN
            webhookEvent.objectIdRaw shouldBe "ch_test_123"
            webhookEvent.actionRaw shouldBe "refunded"
            webhookEvent.action shouldBe StripeAction.UNKNOWN
        }

        "events with a missing data object keep raw fields empty" {
            val event =
                stripeEvent(type = "checkout.session.completed", objectJson = "", dataJson = "{}")

            val webhookEvent = event.toWebhookEvent()

            webhookEvent.eventId shouldBe StripeEventId("evt_test_123")
            webhookEvent.type shouldBe "checkout.session.completed"
            webhookEvent.objectTypeRaw shouldBe ""
            webhookEvent.objectType shouldBe StripeObjectType.UNKNOWN
            webhookEvent.objectIdRaw shouldBe null
            webhookEvent.objectId shouldBe null
        }

        "objects without an id have no typed id" {
            val event =
                stripeEvent(
                    type = "checkout.session.completed",
                    objectJson = """{"object": "checkout.session"}""",
                )

            val webhookEvent = event.toWebhookEvent()

            webhookEvent.objectIdRaw shouldBe null
            webhookEvent.objectId shouldBe null
        }
    })

private fun stripeEvent(
    type: String,
    objectJson: String,
    apiVersion: String = "2019-01-01",
    dataJson: String = """{"object": $objectJson}""",
): Event =
    StripeObject.deserializeStripeObject(
        """
        {
          "id": "evt_test_123",
          "object": "event",
          "api_version": "$apiVersion",
          "created": 1234567890,
          "livemode": false,
          "pending_webhooks": 1,
          "type": "$type",
          "data": $dataJson
        }
        """
            .trimIndent(),
        Event::class.java,
        null,
    )
