package net.ghue.ktp.ktor.plugin

import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.createApplicationPlugin
import io.ktor.util.pipeline.PipelinePhase
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Runs each request on its own dedicated virtual thread for the entire call pipeline. Every
 * resumption after a suspension point dispatches back to the same thread, so blocking calls are
 * safe anywhere in a handler without a dispatcher hop, and ThreadLocal state (including SLF4J MDC)
 * behaves like classic thread-per-request: values set anywhere in the call are visible for the rest
 * of the call and die with the thread, so they cannot leak between requests.
 *
 * Do not wrap request code in kotlinx-coroutines-slf4j's MDCContext; it re-installs its snapshot on
 * every resumption, wiping MDC values set after it was created.
 *
 * Requires JDK 24+ at runtime; on older JDKs a virtual thread blocking inside a synchronized block
 * pins its carrier thread (fixed by JEP 491).
 */
val RequestVirtualThreadPlugin =
    createApplicationPlugin(name = "RequestVirtualThreadPlugin") {
        val threadFactory = Thread.ofVirtual().name("req-", 0).factory()
        val vtPhase = PipelinePhase("RequestVirtualThread")

        application.insertPhaseBefore(ApplicationCallPipeline.Setup, vtPhase)

        application.intercept(vtPhase) {
            Executors.newSingleThreadExecutor(threadFactory).asCoroutineDispatcher().use { vt ->
                withContext(vt) { proceed() }
            }
        }
    }
