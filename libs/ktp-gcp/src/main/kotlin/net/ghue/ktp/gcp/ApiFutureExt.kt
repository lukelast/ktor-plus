package net.ghue.ktp.gcp

import com.google.api.core.ApiFuture
import com.google.api.core.ApiFutureCallback
import com.google.api.core.ApiFutures
import com.google.common.util.concurrent.MoreExecutors
import java.util.concurrent.ExecutionException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** Awaits the completion of the ApiFuture without blocking the current thread. */
suspend fun <T> ApiFuture<T>.await(): T = suspendCancellableCoroutine { cont ->
    ApiFutures.addCallback(
        this,
        object : ApiFutureCallback<T> {
            override fun onSuccess(result: T?) {
                // APIs that can complete with null must be awaited as ApiFuture<T?>.
                cont.resume(result as T)
            }

            override fun onFailure(t: Throwable) {
                cont.resumeWithException(t)
            }
        },
        MoreExecutors.directExecutor(),
    )

    cont.invokeOnCancellation { this.cancel(true) }
}

/** Blocks until done (fine on ktp's virtual-thread requests), unwrapping [ExecutionException]. */
fun <T> ApiFuture<T>.join(): T =
    try {
        get()
    } catch (e: ExecutionException) {
        throw e.cause ?: e
    }
