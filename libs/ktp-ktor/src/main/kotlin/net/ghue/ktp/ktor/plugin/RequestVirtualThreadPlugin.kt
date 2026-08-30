package net.ghue.ktp.ktor.plugin

import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.createApplicationPlugin
import io.ktor.util.pipeline.PipelinePhase
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Runs each request on its own virtual thread for the whole call pipeline, resuming on that same
 * thread after every suspension: blocking calls are safe anywhere in a handler, and ThreadLocal
 * state (including SLF4J MDC) lives for the call and dies with the thread, so it cannot leak.
 *
 * Do not wrap request code in kotlinx-coroutines-slf4j's MDCContext; it re-installs its snapshot on
 * every resumption, wiping MDC values set after it was created.
 *
 * Requires JDK 24+; on older JDKs a virtual thread blocking inside `synchronized` pins its carrier
 * thread (fixed by JEP 491).
 */
val RequestVirtualThreadPlugin =
    createApplicationPlugin(name = "RequestVirtualThreadPlugin") {
        val threadFactory = Thread.ofVirtual().name("req-", 0).factory()
        val vtPhase = PipelinePhase("RequestVirtualThread")

        application.insertPhaseBefore(ApplicationCallPipeline.Setup, vtPhase)

        application.intercept(vtPhase) {
            // A per-call single-thread executor (not a shared per-task one) keeps every resumption
            // on
            // one thread so ThreadLocal/MDC survives; `use` ends that thread with the call.
            Executors.newSingleThreadExecutor(threadFactory).asCoroutineDispatcher().use { vt ->
                withContext(vt) { proceed() }
            }
        }
    }
