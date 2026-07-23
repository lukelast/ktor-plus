package net.ghue.ktp.stripe

import com.stripe.exception.SignatureVerificationException
import com.stripe.model.Event
import com.stripe.model.Subscription
import com.stripe.model.checkout.Session
import com.stripe.net.Webhook
import com.stripe.param.WebhookEndpointCreateParams.EnabledEvent
import io.github.oshai.kotlinlogging.withLoggingContext
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.post
import net.ghue.ktp.config.KtpConfig
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
                EnabledEvent.CHECKOUT__SESSION__COMPLETED.value -> {
                    processCheckoutSession(event, handler::checkoutSessionCompleted)
                }
                EnabledEvent.CHECKOUT__SESSION__EXPIRED.value -> {
                    processCheckoutSession(event, handler::checkoutSessionExpired)
                }
                EnabledEvent.CUSTOMER__SUBSCRIPTION__UPDATED.value -> {
                    processSubscription(event, handler::subscriptionUpdated)
                }
                EnabledEvent.CUSTOMER__SUBSCRIPTION__DELETED.value -> {
                    processSubscription(event, handler::subscriptionDeleted)
                }
                else -> {
                    handler.otherEvent(event.type)
                }
            }
            call.respond(HttpStatusCode.OK)
        }
    }
}

interface StripeWebhookHandler {
    suspend fun checkoutSessionCompleted(session: Session) {}

    suspend fun checkoutSessionExpired(session: Session) {}

    suspend fun subscriptionUpdated(subscription: Subscription) {}

    suspend fun subscriptionDeleted(subscription: Subscription) {}

    suspend fun otherEvent(eventType: String) {}
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
