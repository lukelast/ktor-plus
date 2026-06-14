package net.ghue.ktp.stripe

import com.stripe.Stripe
import com.stripe.model.Event
import com.stripe.model.StripeObject
import com.stripe.model.checkout.Session
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import net.ghue.ktp.ktor.error.KtpRspEx

class CheckoutSessionTest :
    StringSpec({
        "asCheckoutSession returns safely decoded checkout session" {
            var retrieved = false
            val event = checkoutSessionEvent(apiVersion = Stripe.API_VERSION)

            val session = event.asCheckoutSession {
                retrieved = true
                Session()
            }

            session.id shouldBe "cs_test_123"
            session.paymentStatus shouldBe "paid"
            retrieved shouldBe false
        }

        "asCheckoutSession retrieves checkout session when safe decoding is unavailable" {
            val event = checkoutSessionEvent(apiVersion = "2019-01-01")
            val retrievedSession = Session().apply { id = "cs_test_retrieved" }

            val session = event.asCheckoutSession { sessionId ->
                sessionId shouldBe "cs_test_123"
                retrievedSession
            }

            session shouldBe retrievedSession
        }

        "asCheckoutSession rejects fallback events without a session id" {
            val event =
                checkoutSessionEvent(
                    apiVersion = "2019-01-01",
                    checkoutSessionJson =
                        """
                        {
                          "object": "checkout.session",
                          "payment_status": "paid"
                        }
                        """
                            .trimIndent(),
                )

            val ex =
                shouldThrow<KtpRspEx> {
                    event.asCheckoutSession { error("retrieve should not be called") }
                }

            ex.status shouldBe HttpStatusCode.BadRequest
            ex.title shouldBe "Missing Checkout Session ID"
        }

        "asCheckoutSession rejects safely decoded non-session objects" {
            val event =
                checkoutSessionEvent(
                    apiVersion = Stripe.API_VERSION,
                    checkoutSessionJson =
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
                    event.asCheckoutSession { error("retrieve should not be called") }
                }

            ex.status shouldBe HttpStatusCode.BadRequest
            ex.title shouldBe "Unexpected Stripe Object"
        }
    })

private fun checkoutSessionEvent(
    apiVersion: String,
    checkoutSessionJson: String =
        """
        {
          "id": "cs_test_123",
          "object": "checkout.session",
          "payment_status": "paid"
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
          "type": "checkout.session.completed",
          "data": {
            "object": $checkoutSessionJson
          }
        }
        """
            .trimIndent(),
        Event::class.java,
        null,
    )
