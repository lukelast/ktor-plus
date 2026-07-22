package net.ghue.ktp.stripe

import com.stripe.exception.SignatureVerificationException
import com.stripe.model.Event
import com.stripe.model.Subscription
import com.stripe.model.checkout.Session
import com.stripe.net.Webhook
import io.github.oshai.kotlinlogging.withLoggingContext
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.post
import net.ghue.ktp.config.KtpConfig
import net.ghue.ktp.ktor.error.ktpRspError
import net.ghue.ktp.ktor.plugin.withIoContext
import net.ghue.ktp.log.log
import org.koin.ktor.ext.inject

fun Routing.installStripeWebhook() {
    val config: KtpConfig by inject()
    val handler: StripeWebhookHandler by inject()
    val webhookSecret = config.stripe.webhookSecret
    post("/api/stripe/event") {
        val payload: String = call.receive()
        val stripeSigHeaderName = "Stripe-Signature"
        val signature = call.request.headers[stripeSigHeaderName]
        if (signature == null) {
            log {}.warn { "Missing $stripeSigHeaderName header" }
            call.respond(HttpStatusCode.BadRequest, "Missing $stripeSigHeaderName header")
            return@post
        }
        val event =
            try {
                Webhook.constructEvent(payload, signature, webhookSecret)
            } catch (ex: SignatureVerificationException) {
                log {}.warn(ex) { "Invalid signature" }
                call.respond(HttpStatusCode.Unauthorized, "Invalid signature")
                return@post
            }
        withLoggingContext("event-type" to event.type) {
            log {}.info { "Processing stripe event" }
            when (event.type) {
                "checkout.session.completed" -> {
                    processCheckoutSession(event, handler::checkoutSessionCompleted)
                }
                "checkout.session.expired" -> {
                    processCheckoutSession(event, handler::checkoutSessionExpired)
                }
                "customer.subscription.updated" -> {
                    processSubscription(event, handler::subscriptionUpdated)
                }
                "customer.subscription.deleted" -> {
                    processSubscription(event, handler::subscriptionDeleted)
                }
                else -> {
                    ktpRspError {
                        status = HttpStatusCode.BadRequest
                        title = "Unhandled Event Type"
                        detail = "Unhandled event type: ${event.type}"
                    }
                }
            }
            call.respond(HttpStatusCode.OK)
        }
    }
}

interface StripeWebhookHandler {
    suspend fun checkoutSessionCompleted(session: Session)

    suspend fun checkoutSessionExpired(session: Session)

    /**
     * Fires on `customer.subscription.updated` — e.g. a scheduled cancellation
     * (cancel_at_period_end), a quantity change, or a status transition. Default no-op so existing
     * handlers keep compiling; the event type only arrives if the Stripe webhook endpoint
     * subscribes to it.
     */
    suspend fun subscriptionUpdated(subscription: Subscription) {}

    /**
     * Fires on `customer.subscription.deleted` — the subscription has actually ended (period-end
     * cancellation taking effect, immediate cancellation, or final payment failure). Default no-op
     * so existing handlers keep compiling; the event type only arrives if the Stripe webhook
     * endpoint subscribes to it.
     */
    suspend fun subscriptionDeleted(subscription: Subscription) {}
}

private suspend fun processCheckoutSession(event: Event, body: suspend (Session) -> Unit) {
    // asCheckoutSession may issue a blocking Stripe API call on the fallback path, so resolve it
    // (and run the handler) on the IO dispatcher. It also guarantees a non-blank session id.
    withIoContext {
        val session = event.asCheckoutSession()
        withLoggingContext("checkout-session-id" to session.id) {
            try {
                body(session)
            } catch (ex: Exception) {
                log {}.warn(ex) { ex.message }
                throw ex
            }
        }
    }
}

private suspend fun processSubscription(event: Event, body: suspend (Subscription) -> Unit) {
    // asSubscription may issue a blocking Stripe API call on the fallback path, so resolve it
    // (and run the handler) on the IO dispatcher. It also guarantees a non-blank subscription id.
    withIoContext {
        val subscription = event.asSubscription()
        withLoggingContext("subscription-id" to subscription.id) {
            try {
                body(subscription)
            } catch (ex: Exception) {
                log {}.warn(ex) { ex.message }
                throw ex
            }
        }
    }
}
