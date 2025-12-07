package net.ghue.ktp.stripe

import com.stripe.exception.SignatureVerificationException
import com.stripe.model.Event
import com.stripe.model.checkout.Session
import com.stripe.net.Webhook
import io.github.oshai.kotlinlogging.withLoggingContext
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
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
            try {
                log {}.info { "Processing stripe event" }
                when (event.type) {
                    "checkout.session.completed" -> {
                        processCheckoutSession(event, handler::checkoutSessionCompleted)
                    }
                    "checkout.session.expired" -> {
                        processCheckoutSession(event, handler::checkoutSessionExpired)
                    }
                    else -> {
                        error("Unhandled event type: ${event.type}")
                    }
                }
                call.respond(HttpStatusCode.OK)
            } catch (ex: Exception) {
                log {}.warn(ex) { "Error handling stripe event" }
                call.respond(HttpStatusCode.InternalServerError, "Error handling stripe event")
            }
        }
    }
}

interface StripeWebhookHandler {
    suspend fun checkoutSessionCompleted(session: Session)

    suspend fun checkoutSessionExpired(session: Session)
}

private suspend fun processCheckoutSession(event: Event, body: suspend (Session) -> Unit) {
    val session = event.asCheckoutSession()
    if (session.id.isNullOrBlank()) {
        error("Missing checkout session ID")
    }
    withLoggingContext("checkout-session-id" to session.id) {
        withIoContext {
            try {
                body(session)
            } catch (ex: Exception) {
                log {}.warn(ex) { ex.message }
                throw ex
            }
        }
    }
}
