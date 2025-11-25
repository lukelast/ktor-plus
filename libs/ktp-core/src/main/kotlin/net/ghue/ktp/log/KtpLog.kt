package net.ghue.ktp.log

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import org.slf4j.bridge.SLF4JBridgeHandler

/** A shortcut for creating a logger. */
@Suppress("NOTHING_TO_INLINE")
inline fun log(noinline func: () -> Unit): KLogger = KotlinLogging.logger(func)

object Slf4jBridgeInstall {
    init {
        SLF4JBridgeHandler.removeHandlersForRootLogger()
        SLF4JBridgeHandler.install()
    }

    operator fun invoke() {}
}
