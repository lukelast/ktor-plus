package net.ghue.ktp.stripe

/**
 * The verb suffix of a Stripe event type, e.g. the `updated` in `customer.subscription.updated`.
 * Entries cover the event types documented for the object types in [StripeObjectType]. The full set
 * of Stripe verbs is open, so unrecognized values map to [UNKNOWN] rather than failing; the raw
 * string remains available via [StripeWebhookEvent.actionRaw].
 */
enum class StripeAction {
    ASYNC_PAYMENT_FAILED,
    ASYNC_PAYMENT_SUCCEEDED,
    COMPLETED,
    CREATED,
    DELETED,
    EXPIRED,
    FINALIZATION_FAILED,
    FINALIZED,
    MARKED_UNCOLLECTIBLE,
    OVERDUE,
    OVERPAID,
    PAID,
    PAUSED,
    PAYMENT_ACTION_REQUIRED,
    PAYMENT_ATTEMPT_REQUIRED,
    PAYMENT_FAILED,
    PAYMENT_SUCCEEDED,
    PENDING_UPDATE_APPLIED,
    PENDING_UPDATE_EXPIRED,
    RESUMED,
    SENT,
    TRIAL_WILL_END,
    UPCOMING,
    UPDATED,
    VOIDED,
    WILL_BE_DUE,
    /** Any verb without a dedicated entry. */
    UNKNOWN;

    override fun toString(): String {
        return name.lowercase()
    }

    companion object {
        fun fromString(value: String): StripeAction =
            try {
                valueOf(value.uppercase())
            } catch (_: IllegalArgumentException) {
                UNKNOWN
            }
    }
}
