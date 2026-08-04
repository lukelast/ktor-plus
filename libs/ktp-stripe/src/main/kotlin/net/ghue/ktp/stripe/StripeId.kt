package net.ghue.ktp.stripe

import com.stripe.StripeClient
import com.stripe.model.Customer
import com.stripe.model.Invoice
import com.stripe.model.Subscription
import com.stripe.model.checkout.Session

/**
 * A Stripe object id extracted from a webhook event. Ids are stable across Stripe API versions, so
 * they are safe to read from any event regardless of the SDK version the app is pinned to.
 *
 * Adding support for a new object type: add a value class here, add a [StripeObjectType] entry, map
 * it in [StripeWebhookEvent.objectId], and add a default method to [StripeWebhookHandler].
 */
sealed interface StripeId {
    val value: String
}

@JvmInline value class CheckoutSessionId(override val value: String) : StripeId

@JvmInline value class SubscriptionId(override val value: String) : StripeId

@JvmInline value class InvoiceId(override val value: String) : StripeId

@JvmInline value class CustomerId(override val value: String) : StripeId

/** Fetches the current [Session] from the Stripe API at the app's pinned SDK version. */
fun CheckoutSessionId.retrieve(client: StripeClient): Session =
    client.v1().checkout().sessions().retrieve(value)

/** Fetches the current [Subscription] from the Stripe API at the app's pinned SDK version. */
fun SubscriptionId.retrieve(client: StripeClient): Subscription =
    client.v1().subscriptions().retrieve(value)

/** Fetches the current [Invoice] from the Stripe API at the app's pinned SDK version. */
fun InvoiceId.retrieve(client: StripeClient): Invoice = client.v1().invoices().retrieve(value)

/** Fetches the current [Customer] from the Stripe API at the app's pinned SDK version. */
fun CustomerId.retrieve(client: StripeClient): Customer = client.v1().customers().retrieve(value)
