package net.ghue.ktp.core

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import com.github.benmanes.caffeine.cache.Ticker
import java.util.concurrent.Executors
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty
import kotlin.time.Duration
import kotlin.time.toJavaDuration
import net.ghue.ktp.log.log

private val refreshExecutor = Executors.newVirtualThreadPerTaskExecutor()

/** A lazy property delegate whose value is refreshed in the background after [refreshAfter]. */
class LazyWithRefresh<T : Any>
internal constructor(
    refreshAfter: Duration,
    ticker: Ticker = Ticker.systemTicker(),
    private val loader: () -> T,
) : ReadOnlyProperty<Any?, T> {

    /** Retained past Caffeine's eviction so a failed reload can still serve the old value. */
    @Volatile private var lastGood: T? = null

    private val cache: LoadingCache<Unit, T> =
        Caffeine.newBuilder()
            .refreshAfterWrite(refreshAfter.toJavaDuration())
            .expireAfterWrite((refreshAfter * 2).toJavaDuration())
            .executor(refreshExecutor)
            .ticker(ticker)
            .build { load() }

    private fun load(): T =
        try {
            loader().also { lastGood = it }
        } catch (ex: Exception) {
            val fallback = lastGood ?: throw ex
            log {}.warn(ex) { "Refresh failed, serving the previous value" }
            fallback
        }

    override fun getValue(thisRef: Any?, property: KProperty<*>): T = cache.get(Unit)
}

/**
 * Creates a [LazyWithRefresh] delegate whose value stays fresh for [refreshAfter]. A read past that
 * age starts one background refresh on a virtual thread and returns the stale value (or the fresh
 * one, when the refresh happens to complete immediately); past twice that age the entry is evicted,
 * and the next read blocks on a fresh load. Once a value has loaded successfully, the delegate
 * never throws again: a failed load (blocking or background) is logged, and the last good value is
 * served — and re-cached, so retries are paced by [refreshAfter]. Failures before the first success
 * propagate to the reader and the next read retries.
 */
fun <T : Any> lazyWithRefresh(refreshAfter: Duration, loader: () -> T): LazyWithRefresh<T> =
    LazyWithRefresh(refreshAfter, loader = loader)
