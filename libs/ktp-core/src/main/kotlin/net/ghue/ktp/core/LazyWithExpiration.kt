package net.ghue.ktp.core

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import java.util.concurrent.Executors
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty
import kotlin.time.Duration
import kotlin.time.toJavaDuration

private val reloadExecutor = Executors.newVirtualThreadPerTaskExecutor()

/** A lazy property delegate, but with an expiration time. */
class LazyWithExpiration<T : Any> internal constructor(refreshAfter: Duration, loader: () -> T) :
    ReadOnlyProperty<Any?, T> {

    private val cache: LoadingCache<Unit, T> =
        Caffeine.newBuilder()
            .refreshAfterWrite(refreshAfter.toJavaDuration())
            .expireAfterWrite((refreshAfter * 2).toJavaDuration())
            .executor(reloadExecutor)
            .build { loader() }

    override fun getValue(thisRef: Any?, property: KProperty<*>): T = cache.get(Unit)
}

/**
 * Creates a [LazyWithExpiration] delegate:
 * ```
 * val config by lazyWithExpiration(5.minutes) { fetchConfig() }
 * ```
 *
 * @param refreshAfter How long a loaded value stays fresh. A read past this age returns the stale
 *   value immediately and starts one background reload on a virtual thread. Past twice this age the
 *   value is gone and reads block until one of them reloads it.
 * @param loader Produces the value, on the calling thread for the first read and for any expired
 *   read, on a virtual thread for background reloads. A failure in a blocking load propagates to
 *   that caller and the next read retries; a failed background reload is logged by Caffeine and the
 *   stale value is served until it expires.
 */
fun <T : Any> lazyWithExpiration(refreshAfter: Duration, loader: () -> T): LazyWithExpiration<T> =
    LazyWithExpiration(refreshAfter, loader)
