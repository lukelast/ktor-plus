package net.ghue.ktp.gcp

import com.google.api.core.ApiFuture
import java.util.concurrent.ExecutionException

/** Blocks until done (fine on ktp's virtual-thread requests), unwrapping [ExecutionException]. */
fun <T> ApiFuture<T>.join(): T =
    try {
        get()
    } catch (e: ExecutionException) {
        throw e.cause ?: e
    }
