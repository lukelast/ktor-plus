package net.ghue.ktp.stripe

/**
 * Verb suffix of a Stripe event type, e.g. `updated` in `customer.subscription.updated`, for the
 * events of each [StripeObjectType]; Stripe's verb set is open, so other verbs map to [UNKNOWN],
 * with the raw string in [StripeWebhookEvent.actionRaw].
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
