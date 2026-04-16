package net.ghue.ktp.gradle.project.mods

import org.gradle.api.Project
import org.koin.compiler.plugin.KoinGradlePlugin

fun Project.applyKoinCompilerPlugin() {
    pluginManager.apply(KoinGradlePlugin::class.java)
}
