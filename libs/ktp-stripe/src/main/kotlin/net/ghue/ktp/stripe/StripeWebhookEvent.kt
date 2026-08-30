package net.ghue.ktp.stripe

import com.stripe.model.Event
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

@JvmInline value class StripeEventId(val value: String)

/**
 * Version-stable view of a Stripe webhook event: payloads arrive at the endpoint's API version, not
 * the SDK's, so only raw ids and type strings are exposed; re-fetch full objects via
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

    /** Typed [objectIdRaw] for modeled types; null routes to [StripeWebhookHandler.onOther]. */
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
    // Not dataObjectDeserializer.object: it's an empty Optional when api_version != SDK version.
    val dataObject = runCatching {
        Json.parseToJsonElement(dataObjectDeserializer.rawJson).jsonObject
    }
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
