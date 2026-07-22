package net.ghue.ktp.stripe

import com.stripe.Stripe
import com.stripe.model.Event
import com.stripe.model.StripeObject
import com.stripe.model.Subscription
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import net.ghue.ktp.ktor.error.KtpRspEx

class SubscriptionEventTest :
    StringSpec({
        "asSubscription returns safely decoded subscription" {
            var retrieved = false
            val event = subscriptionEvent(apiVersion = Stripe.API_VERSION)

            val subscription = event.asSubscription {
                retrieved = true
                Subscription()
            }

            subscription.id shouldBe "sub_test_123"
            subscription.status shouldBe "canceled"
            retrieved shouldBe false
        }

        "asSubscription retrieves subscription when safe decoding is unavailable" {
            val event = subscriptionEvent(apiVersion = "2019-01-01")
            val retrievedSubscription = Subscription().apply { id = "sub_test_retrieved" }

            val subscription = event.asSubscription { subscriptionId ->
                subscriptionId shouldBe "sub_test_123"
                retrievedSubscription
            }

            subscription shouldBe retrievedSubscription
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

            val ex =
                shouldThrow<KtpRspEx> {
                    event.asSubscription { error("retrieve should not be called") }
                }

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

            val ex =
                shouldThrow<KtpRspEx> {
                    event.asSubscription { error("retrieve should not be called") }
                }

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
