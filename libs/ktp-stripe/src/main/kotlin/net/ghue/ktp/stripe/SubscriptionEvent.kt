package net.ghue.ktp.stripe

import com.stripe.model.Event
import com.stripe.model.EventDataObjectDeserializer
import com.stripe.model.Subscription
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import net.ghue.ktp.ktor.error.ktpRspError

fun Event.asSubscription(): Subscription = asSubscription { Subscription.retrieve(it) }

/**
 * Decodes the [Subscription] from this event. When the event's API version matches the SDK, Stripe
 * gives us a high-integrity snapshot we can use directly; otherwise only the (version-stable) id is
 * trusted and [retrieveSubscription] re-fetches a clean object.
 */
internal fun Event.asSubscription(retrieveSubscription: (String) -> Subscription): Subscription {
    val deserializer = dataObjectDeserializer
    val subscription =
        when (val stripeObject = deserializer.`object`.orElse(null)) {
            null -> retrieveSubscription(deserializer.subscriptionId())
            is Subscription -> stripeObject
            else ->
                ktpRspError {
                    status = HttpStatusCode.BadRequest
                    title = "Unexpected Stripe Object"
                    detail = "Stripe event data object is not a subscription"
                }
        }

    return subscription.takeUnless { it.id.isNullOrBlank() } ?: missingSubscriptionId()
}

private fun EventDataObjectDeserializer.subscriptionId(): String {
    val subscriptionId =
        runCatching {
                val dataObject = Json.parseToJsonElement(rawJson).jsonObject
                (dataObject["id"] as? JsonPrimitive)?.contentOrNull
            }
            .getOrNull()

    return subscriptionId?.takeUnless { it.isBlank() } ?: missingSubscriptionId()
}

private fun missingSubscriptionId(): Nothing = ktpRspError {
    status = HttpStatusCode.BadRequest
    title = "Missing Subscription ID"
    detail = "Stripe event contains a subscription with no ID"
}
