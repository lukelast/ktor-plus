package net.ghue.ktp.gradle.lukestack

import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.kotlin.dsl.withType

/**
 * Test tasks are tuned for agent-driven development: the console output alone must be enough to
 * diagnose a failure without opening the HTML report — full stack traces with causes, streamed
 * application output, and a result line per test.
 */
internal fun Project.configureTesting() {
    tasks.withType<Test>().configureEach {
        // The app boots with its GCP/Stripe SDK stack in-process during tests.
        maxHeapSize = "2g"
        testLogging {
            events("passed", "skipped", "failed")
            // Streams test stdout/stderr; also covers the standard_out/standard_error events.
            showStandardStreams = true
            exceptionFormat = TestExceptionFormat.FULL
        }
    }
}
