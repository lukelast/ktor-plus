package net.ghue.ktp.stripe

import com.stripe.StripeClient
import com.stripe.model.Event
import com.stripe.model.EventDataObjectDeserializer
import com.stripe.model.checkout.Session
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import net.ghue.ktp.ktor.error.ktpRspError

@Deprecated(
    "Depends on the process-global Stripe.apiKey, which nothing in KTP sets, so the " +
        "fallback re-fetch fails with AuthenticationException unless the application set " +
        "the key itself. Pass an authenticated StripeClient instead.",
    ReplaceWith("asCheckoutSession(client)"),
)
fun Event.asCheckoutSession(): Session = asCheckoutSession { Session.retrieve(it) }

fun Event.asCheckoutSession(client: StripeClient): Session = asCheckoutSession {
    client.v1().checkout().sessions().retrieve(it)
}

/**
 * Decodes the checkout [Session] from this event. When the event's API version matches the SDK,
 * Stripe gives us a high-integrity snapshot we can use directly; otherwise only the
 * (version-stable) id is trusted and [retrieveSession] re-fetches a clean object.
 */
internal fun Event.asCheckoutSession(retrieveSession: (String) -> Session): Session {
    val deserializer = dataObjectDeserializer
    val session =
        when (val stripeObject = deserializer.`object`.orElse(null)) {
            null -> retrieveSession(deserializer.checkoutSessionId())
            is Session -> stripeObject
            else ->
                ktpRspError {
                    status = HttpStatusCode.BadRequest
                    title = "Unexpected Stripe Object"
                    detail = "Stripe event data object is not a checkout session"
                }
        }

    return session.takeUnless { it.id.isNullOrBlank() } ?: missingSessionId()
}

private fun EventDataObjectDeserializer.checkoutSessionId(): String {
    val sessionId =
        runCatching {
                val dataObject = Json.parseToJsonElement(rawJson).jsonObject
                (dataObject["id"] as? JsonPrimitive)?.contentOrNull
            }
            .getOrNull()

    return sessionId?.takeUnless { it.isBlank() } ?: missingSessionId()
}

private fun missingSessionId(): Nothing = ktpRspError {
    status = HttpStatusCode.BadRequest
    title = "Missing Checkout Session ID"
    detail = "Stripe event contains a checkout session with no ID"
}

/**
 * [Docs](https://docs.stripe.com/api/checkout/sessions/object#checkout_session_object-payment_status)
 */
enum class PaymentStatus {
    PAID,
    UNPAID,
    NO_PAYMENT_REQUIRED;

    val isPaid: Boolean
        get() = this == PAID || this == NO_PAYMENT_REQUIRED

    override fun toString(): String {
        return name.lowercase()
    }

    companion object {
        fun fromString(value: String): PaymentStatus =
            try {
                valueOf(value.uppercase())
            } catch (_: Exception) {
                ktpRspError {
                    status = HttpStatusCode.BadRequest
                    title = "Unknown Payment Status"
                    detail = "Unknown payment status: $value"
                }
            }
    }
}

val Session.paymentStatusEnum: PaymentStatus
    get() = PaymentStatus.fromString(this.paymentStatus)
