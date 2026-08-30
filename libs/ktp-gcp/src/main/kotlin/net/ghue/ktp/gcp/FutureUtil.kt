package net.ghue.ktp.gcp

import com.google.api.core.ApiFuture
import java.util.concurrent.ExecutionException

/**
 * Blocks until the future completes and returns its value, rethrowing the operation's original
 * exception instead of the [ExecutionException] wrapper. Blocking is the intended model in ktp
 * apps: requests run on virtual threads, so waiting parks the virtual thread without tying up a
 * carrier thread.
 */
fun <T> ApiFuture<T>.join(): T =
    try {
        get()
    } catch (e: ExecutionException) {
        throw e.cause ?: e
    }
