package net.ghue.ktp.test.config

import io.kotest.core.config.AbstractProjectConfig
import io.kotest.core.spec.SpecExecutionOrder
import net.ghue.ktp.log.log

abstract class ProjectConfigBase : AbstractProjectConfig() {
    override val specExecutionOrder = SpecExecutionOrder.Random

    override suspend fun beforeProject() {
        log {}.info { "Using: ${this::class.qualifiedName}" }
    }
}
