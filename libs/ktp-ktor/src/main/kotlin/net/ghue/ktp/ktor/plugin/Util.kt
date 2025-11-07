package net.ghue.ktp.ktor.plugin

import io.ktor.http.*
import io.ktor.server.plugins.origin
import io.ktor.server.routing.RoutingCall
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.slf4j.MDCContext
import kotlinx.coroutines.withContext
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.time.Duration

fun cacheControlMaxAge(
    maxAge: Duration,
    visibility: CacheControl.Visibility = CacheControl.Visibility.Public,
): CacheControl {
    return CacheControl.MaxAge(
        maxAgeSeconds = maxAge.inWholeSeconds.toInt(),
        visibility = visibility,
    )
}

fun RoutingCall.originUrl(path: List<String> = emptyList()): Url = buildUrl {
    protocol = URLProtocol.createOrDefault(request.origin.scheme)
    host = request.origin.serverHost
    port = request.origin.serverPort
    pathSegments = path
}

@OptIn(ExperimentalContracts::class)
suspend inline fun <T> withIoContext(noinline block: suspend CoroutineScope.() -> T): T {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return withContext(context = Dispatchers.IO + MDCContext(), block = block)
}



