package net.ghue.ktp.gradle.project

import net.ghue.ktp.gradle.project.mods.registerVerifyTask
import org.gradle.api.Project
import org.gradle.api.plugins.BasePlugin

fun Project.applyFrontend() {
    // Only the lifecycle tasks (build/check/clean) plus the verify dev-loop entry point. The
    // toolchain-specific tasks come from the stack plugin layered on top; the base plugin does
    // not know what a frontend is built with.
    pluginManager.apply(BasePlugin::class.java)
    registerVerifyTask()
}
