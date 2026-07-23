package net.ghue.ktp.stripe

import com.stripe.Stripe
import com.stripe.model.Event
import com.stripe.model.StripeObject
import com.stripe.model.Subscription
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import net.ghue.ktp.ktor.error.KtpRspEx

class SubscriptionEventTest :
    StringSpec({
        beforeTest { mockkStatic(Subscription::class) }
        afterTest { unmockkStatic(Subscription::class) }

        "asSubscription returns safely decoded subscription" {
            val event = subscriptionEvent(apiVersion = Stripe.API_VERSION)

            val subscription = event.asSubscription()

            subscription.id shouldBe "sub_test_123"
            subscription.status shouldBe "canceled"
            verify(exactly = 0) { Subscription.retrieve(any<String>()) }
        }

        "asSubscription retrieves subscription when safe decoding is unavailable" {
            val event = subscriptionEvent(apiVersion = "2019-01-01")
            val retrievedSubscription = Subscription().apply { id = "sub_test_retrieved" }
            every { Subscription.retrieve("sub_test_123") } returns retrievedSubscription

            val subscription = event.asSubscription()

            subscription shouldBe retrievedSubscription
            verify(exactly = 1) { Subscription.retrieve("sub_test_123") }
        }

        "asSubscription rejects fallback events without a subscription id" {
            val event =
                subscriptionEvent(
                    apiVersion = "2019-01-01",
                    subscriptionJson =
                        """
                        {
                          "object": "subscription",
                          "status": "canceled"
                        }
                        """
                            .trimIndent(),
                )

            val ex = shouldThrow<KtpRspEx> { event.asSubscription() }

            ex.status shouldBe HttpStatusCode.BadRequest
            ex.title shouldBe "Missing Subscription ID"
        }

        "asSubscription rejects safely decoded non-subscription objects" {
            val event =
                subscriptionEvent(
                    apiVersion = Stripe.API_VERSION,
                    subscriptionJson =
                        """
                        {
                          "id": "cus_test_123",
                          "object": "customer"
                        }
                        """
                            .trimIndent(),
                )

            val ex = shouldThrow<KtpRspEx> { event.asSubscription() }

            ex.status shouldBe HttpStatusCode.BadRequest
            ex.title shouldBe "Unexpected Stripe Object"
        }
    })

private fun subscriptionEvent(
    apiVersion: String,
    subscriptionJson: String =
        """
        {
          "id": "sub_test_123",
          "object": "subscription",
          "status": "canceled"
        }
        """
            .trimIndent(),
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
          "type": "customer.subscription.deleted",
          "data": {
            "object": $subscriptionJson
          }
        }
        """
            .trimIndent(),
        Event::class.java,
        null,
    )
