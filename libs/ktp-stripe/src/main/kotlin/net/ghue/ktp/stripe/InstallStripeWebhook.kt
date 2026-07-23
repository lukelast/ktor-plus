package net.ghue.ktp.stripe

import com.stripe.exception.SignatureVerificationException
import com.stripe.net.Webhook
import io.github.oshai.kotlinlogging.withLoggingContext
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.post
import net.ghue.ktp.config.KtpConfig
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
        val webhookEvent = event.toWebhookEvent()
        withLoggingContext(
            "stripe-event-id" to webhookEvent.eventId.value,
            "event-type" to webhookEvent.type,
            "stripe-object-id" to webhookEvent.objectIdRaw,
        ) {
            log {}.info { "Processing stripe event" }
            try {
                when (val id = webhookEvent.objectId) {
                    is CheckoutSessionId -> handler.onCheckoutSession(webhookEvent, id)
                    is SubscriptionId -> handler.onSubscription(webhookEvent, id)
                    is InvoiceId -> handler.onInvoice(webhookEvent, id)
                    is CustomerId -> handler.onCustomer(webhookEvent, id)
                    null -> {
                        // An empty object type means the data object could not be parsed, so the
                        // event is acked without any typed handler seeing it.
                        if (webhookEvent.objectTypeRaw.isEmpty()) {
                            log {}.warn { "Stripe event data object is unreadable" }
                        }
                        handler.onOther(webhookEvent)
                    }
                }
            } catch (ex: Exception) {
                log {}.warn(ex) { ex.message }
                throw ex
            }
            call.respond(HttpStatusCode.OK)
        }
    }
}

/**
 * Webhook handlers grouped by Stripe object type. Use [StripeWebhookEvent.type] or
 * [StripeWebhookEvent.action] to distinguish e.g. `completed` from `expired`. Dedicated handlers
 * guarantee a non-null object id. The few Stripe events whose objects intentionally have no id,
 * notably `invoice.upcoming`, arrive at [onOther]; this keeps the common handler API non-null while
 * preserving those special cases through the raw event fields.
 *
 * Handlers receive only version-stable ids. To read the full object, re-fetch it with the app's
 * [com.stripe.StripeClient], e.g. `id.retrieve(client)`.
 */
interface StripeWebhookHandler {
    suspend fun onCheckoutSession(event: StripeWebhookEvent, id: CheckoutSessionId) {}

    suspend fun onSubscription(event: StripeWebhookEvent, id: SubscriptionId) {}

    suspend fun onInvoice(event: StripeWebhookEvent, id: InvoiceId) {}

    suspend fun onCustomer(event: StripeWebhookEvent, id: CustomerId) {}

    suspend fun onOther(event: StripeWebhookEvent) {}
}
