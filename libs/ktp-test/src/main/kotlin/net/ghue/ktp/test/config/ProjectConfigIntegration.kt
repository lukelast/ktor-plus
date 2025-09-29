package net.ghue.ktp.test.config

import io.kotest.engine.concurrency.SpecExecutionMode
import io.kotest.engine.concurrency.TestExecutionMode
import kotlin.time.Duration.Companion.seconds

class ProjectConfigIntegration : ProjectConfigBase() {
    override val retries = 2
    override val retryDelay = 1.seconds
    override val testExecutionMode = TestExecutionMode.LimitedConcurrency(4)
    override val specExecutionMode = SpecExecutionMode.LimitedConcurrency(8)
}
