package net.ghue.ktp.log

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging

/** A shortcut for creating a logger. */
@Suppress("NOTHING_TO_INLINE")
inline fun log(noinline func: () -> Unit): KLogger = KotlinLogging.logger(func)
