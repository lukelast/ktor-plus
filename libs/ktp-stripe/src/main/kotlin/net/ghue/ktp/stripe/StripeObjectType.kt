package net.ghue.ktp.stripe

/**
 * Stripe object types this library models, as spelled in a webhook's `data.object.object` field;
 * other objects map to [UNKNOWN], with the raw string in [StripeWebhookEvent.objectTypeRaw].
 */
enum class StripeObjectType(val value: String?) {
    CHECKOUT_SESSION("checkout.session"),
    SUBSCRIPTION("subscription"),
    INVOICE("invoice"),
    CUSTOMER("customer"),
    UNKNOWN(null);

    companion object {
        fun fromString(value: String): StripeObjectType =
            entries.firstOrNull { it.value == value } ?: UNKNOWN
    }
}
