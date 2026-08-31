package net.ghue.ktp.stripe

import com.stripe.model.checkout.Session

/**
 * [Docs](https://docs.stripe.com/api/checkout/sessions/object#checkout_session_object-payment_status)
 */
enum class CheckoutSessionPaymentStatus {
    PAID,
    UNPAID,
    NO_PAYMENT_REQUIRED;

    val isPaid: Boolean
        get() = this == PAID || this == NO_PAYMENT_REQUIRED

    override fun toString(): String {
        return name.lowercase()
    }

    companion object {
        /** Throws [IllegalArgumentException] for unknown values, like its sibling enums. */
        fun fromString(value: String): CheckoutSessionPaymentStatus =
            try {
                valueOf(value.uppercase())
            } catch (ex: IllegalArgumentException) {
                throw IllegalArgumentException(
                    "Unknown checkout session payment status: '$value'",
                    ex,
                )
            }
    }
}

val Session.paymentStatusEnum: CheckoutSessionPaymentStatus
    get() = CheckoutSessionPaymentStatus.fromString(this.paymentStatus)
