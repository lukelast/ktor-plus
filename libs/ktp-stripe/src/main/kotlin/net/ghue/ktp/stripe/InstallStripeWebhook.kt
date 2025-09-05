package net.ghue.ktp.stripe

import com.stripe.exception.SignatureVerificationException
import com.stripe.model.checkout.Session
import com.stripe.net.Webhook
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.ghue.ktp.config.KtpConfigManager
import net.ghue.ktp.log.log
import org.koin.ktor.ext.inject

interface StripeWebhookHandler {
    fun checkoutSessionCompleted(session: Session)

    fun checkoutSessionExpired(session: Session)
}

fun Routing.installStripeWebhook() {
    val config: KtpConfigManager by inject()
    val handler: StripeWebhookHandler by inject()
    val webhookSecret = config.stripe.webhookSecret
    post("/api/stripe/event") {
        val payload: String = call.receive()
        val stripeSigHeaderName = "Stripe-Signature"
        val signature = call.request.headers[stripeSigHeaderName]
        if (signature == null) {
            call.respond(HttpStatusCode.BadRequest, "Missing $stripeSigHeaderName header")
            return@post
        }
        val event =
            try {
                Webhook.constructEvent(payload, signature, webhookSecret)
            } catch (ex: SignatureVerificationException) {
                call.respond(HttpStatusCode.Unauthorized, "Invalid signature")
                return@post
            }

        try {
            when (event.type) {
                "checkout.session.completed" -> {
                    val session = event.asCheckoutSession()
                    handler.checkoutSessionCompleted(session)
                }

                "checkout.session.expired" -> {
                    val session = event.asCheckoutSession()
                    handler.checkoutSessionExpired(session)
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
