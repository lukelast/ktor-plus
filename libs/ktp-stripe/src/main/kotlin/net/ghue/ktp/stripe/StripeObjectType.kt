package net.ghue.ktp.stripe

/**
 * The Stripe object types this library models, as they appear in the `data.object.object` field of
 * a webhook event. Webhooks can carry any Stripe object, so values without a dedicated entry map to
 * [UNKNOWN] rather than failing; the raw string remains available via
 * [StripeWebhookEvent.objectTypeRaw].
 */
enum class StripeObjectType(val value: String?) {
    CHECKOUT_SESSION("checkout.session"),
    SUBSCRIPTION("subscription"),
    INVOICE("invoice"),
    CUSTOMER("customer"),
    /** Any object type without a dedicated entry. */
    UNKNOWN(null);

    companion object {
        fun fromString(value: String): StripeObjectType =
            entries.firstOrNull { it.value == value } ?: UNKNOWN
    }
}
