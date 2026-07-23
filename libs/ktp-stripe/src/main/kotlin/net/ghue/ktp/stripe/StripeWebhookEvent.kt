package net.ghue.ktp.stripe

import com.stripe.model.Event
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

@JvmInline value class StripeEventId(val value: String)

/**
 * A version-stable view of a Stripe webhook event. Webhook payloads arrive at the API version the
 * webhook endpoint was created with, which may not match this app's pinned SDK version, so only
 * fields that survive version drift are exposed: ids and type strings read from the raw JSON.
 * Handlers that need the full object should re-fetch it at the app's SDK version, e.g. via
 * [CheckoutSessionId.retrieve].
 */
data class StripeWebhookEvent(
    val eventId: StripeEventId,
    /** Full event type, e.g. `customer.subscription.updated`. */
    val type: String,
    /** The raw `data.object.object` field, e.g. `subscription`. Empty if it could not be parsed. */
    val objectTypeRaw: String,
    /** The raw `data.object.id` field. Null for the few objects without an id. */
    val objectIdRaw: String?,
) {
    /** The raw verb suffix of [type], e.g. `updated`, `deleted`, `async_payment_succeeded`. */
    val actionRaw: String
        get() = type.substringAfterLast('.')

    /** [actionRaw] as an enum, or [StripeAction.UNKNOWN] for unmodeled verbs. */
    val action: StripeAction
        get() = StripeAction.fromString(actionRaw)

    /** [objectTypeRaw] as an enum, or [StripeObjectType.UNKNOWN] for unmodeled object types. */
    val objectType: StripeObjectType
        get() = StripeObjectType.fromString(objectTypeRaw)

    /**
     * The strongly typed [objectIdRaw] for object types this library models, or null when the event
     * should be routed to [StripeWebhookHandler.onOther].
     */
    val objectId: StripeId?
        get() = objectIdRaw?.let { id ->
            when (objectType) {
                StripeObjectType.CHECKOUT_SESSION -> CheckoutSessionId(id)
                StripeObjectType.SUBSCRIPTION -> SubscriptionId(id)
                StripeObjectType.INVOICE -> InvoiceId(id)
                StripeObjectType.CUSTOMER -> CustomerId(id)
                StripeObjectType.UNKNOWN -> null
            }
        }
}

internal fun Event.toWebhookEvent(): StripeWebhookEvent {
    val dataObject =
        runCatching { Json.parseToJsonElement(dataObjectDeserializer.rawJson).jsonObject }
            .getOrNull()

    fun field(name: String): String? =
        (dataObject?.get(name) as? JsonPrimitive)?.contentOrNull?.takeUnless { it.isBlank() }

    return StripeWebhookEvent(
        eventId = StripeEventId(id.orEmpty()),
        type = type.orEmpty(),
        objectTypeRaw = field("object").orEmpty(),
        objectIdRaw = field("id"),
    )
}
