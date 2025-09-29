package net.ghue.ktp.test.config

import io.kotest.engine.concurrency.SpecExecutionMode
import io.kotest.engine.concurrency.TestExecutionMode

class ProjectConfigUnit : ProjectConfigBase() {
    override val testExecutionMode = TestExecutionMode.Concurrent
    override val specExecutionMode = SpecExecutionMode.Concurrent
}
