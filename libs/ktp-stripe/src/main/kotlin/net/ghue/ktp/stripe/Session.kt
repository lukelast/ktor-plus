package net.ghue.ktp.stripe

import com.stripe.model.Event
import com.stripe.model.checkout.Session

fun Event.asCheckoutSession(): Session = this.dataObjectDeserializer.`object`.get() as Session

/**
 * [Docs](https://docs.stripe.com/api/checkout/sessions/object#checkout_session_object-payment_status)
 */
enum class PaymentStatus {
    PAID,
    UNPAID,
    NO_PAYMENT_REQUIRED;

    val isPaid: Boolean
        get() = this in setOf(PAID, NO_PAYMENT_REQUIRED)

    override fun toString(): String {
        return name.lowercase()
    }

    companion object {
        fun fromString(value: String): PaymentStatus =
            try {
                valueOf(value.uppercase())
            } catch (_: Exception) {
                error("Unknown payment status: $value")
            }
    }
}

val Session.paymentStatusTyped: PaymentStatus
    get() = PaymentStatus.fromString(this.paymentStatus)
