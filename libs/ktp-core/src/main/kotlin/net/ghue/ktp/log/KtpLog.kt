package net.ghue.ktp.log

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.atomic.AtomicBoolean
import org.slf4j.bridge.SLF4JBridgeHandler

/** A shortcut for creating a logger. */
@Suppress("NOTHING_TO_INLINE")
inline fun log(noinline func: () -> Unit): KLogger = KotlinLogging.logger(func)

private val slf4jBridgeInstalled = AtomicBoolean(false)

/** Routes java.util.logging through SLF4J. Safe to call multiple times; installs only once. */
fun installSlf4jBridge() {
    if (slf4jBridgeInstalled.compareAndSet(false, true)) {
        SLF4JBridgeHandler.removeHandlersForRootLogger()
        SLF4JBridgeHandler.install()
    }
}
