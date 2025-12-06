package net.ghue.ktp.gcp

import com.google.api.core.ApiFuture
import com.google.api.core.ApiFutureCallback
import com.google.api.core.ApiFutures
import com.google.common.util.concurrent.MoreExecutors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** Awaits the completion of the ApiFuture without blocking the current thread. */
suspend fun <T> ApiFuture<T>.await(): T = suspendCancellableCoroutine { cont ->
    // Add a callback to the Future to resume the coroutine upon completion
    ApiFutures.addCallback(
        this,
        object : ApiFutureCallback<T> {
            override fun onSuccess(result: T?) {
                // Firestore results can sometimes be null, but the type system
                // generally handles it. If T is non-nullable, result won't be null.
                cont.resume(result as T)
            }

            override fun onFailure(t: Throwable) {
                cont.resumeWithException(t)
            }
        },
        MoreExecutors.directExecutor(),
    )

    // If the coroutine is cancelled, cancel the future
    cont.invokeOnCancellation { this.cancel(true) }
}
