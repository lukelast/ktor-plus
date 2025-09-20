package net.ghue.ktp.stripe

import com.stripe.model.Subscription

/** @see https://docs.stripe.com/api/subscriptions/object#subscription_object-status */
enum class SubscriptionStatus {
    INCOMPLETE,
    INCOMPLETE_EXPIRED,
    TRIALING,
    ACTIVE,
    PAST_DUE,
    CANCELED,
    UNPAID,
    PAUSED;

    override fun toString(): String {
        return name.lowercase()
    }

    companion object {
        fun fromString(value: String): SubscriptionStatus = valueOf(value.uppercase())
    }
}

val Subscription.statusTyped: SubscriptionStatus
    get() = SubscriptionStatus.fromString(this.status)
