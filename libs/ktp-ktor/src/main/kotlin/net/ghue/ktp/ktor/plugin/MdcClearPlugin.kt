package net.ghue.ktp.ktor.plugin

import io.ktor.server.application.*
import io.ktor.util.pipeline.*
import org.slf4j.MDC

/**
 * Makes sure MDC is always cleared before and after requests. Note you still need to use
 * [kotlinx.coroutines.slf4j.MDCContext]
 */
val MdcClearPlugin =
    createApplicationPlugin(name = "MdcClearPlugin") {
        val mdcPhase = PipelinePhase("MdcClear")

        application.insertPhaseBefore(ApplicationCallPipeline.Setup, mdcPhase)

        application.intercept(mdcPhase) {
            MDC.clear()
            try {
                proceed()
            } finally {
                MDC.clear()
            }
        }
    }
