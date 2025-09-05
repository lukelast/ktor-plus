package net.ghue.ktp.stripe

import com.stripe.model.Event
import com.stripe.model.checkout.Session
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

fun Event.asCheckoutSession(): Session = this.dataObjectDeserializer.`object`.get() as Session

@Serializable
enum class PaymentStatus {
    @SerialName("paid") PAID,
    @SerialName("unpaid") UNPAID,
    @SerialName("no_payment_required") NO_PAYMENT_REQUIRED,
    @SerialName("unknown") UNKNOWN;

    override fun toString(): String = Json.encodeToString(this)

    companion object {
        fun fromString(value: String): PaymentStatus =
            try {
                Json.decodeFromString(value)
            } catch (_: Exception) {
                UNKNOWN
            }
    }
}

val Session.paymentStatusTyped: PaymentStatus
    get() = PaymentStatus.fromString(this.paymentStatus)

val Session.isPaid: Boolean
    get() = paymentStatusTyped in setOf(PaymentStatus.PAID, PaymentStatus.NO_PAYMENT_REQUIRED)
