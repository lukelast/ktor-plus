package net.ghue.ktp.gradle.project.mods

import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.koin.compiler.plugin.KoinGradleExtension
import org.koin.compiler.plugin.KoinGradlePlugin

fun Project.applyKoinCompilerPlugin() {
    pluginManager.apply(KoinGradlePlugin::class.java)
    configure<KoinGradleExtension> { compileSafety.set(false) }
}
