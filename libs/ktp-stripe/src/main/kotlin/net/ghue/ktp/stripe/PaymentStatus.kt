package net.ghue.ktp.stripe

import com.stripe.model.checkout.Session
import io.ktor.http.HttpStatusCode
import net.ghue.ktp.ktor.error.ktpRspError

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
