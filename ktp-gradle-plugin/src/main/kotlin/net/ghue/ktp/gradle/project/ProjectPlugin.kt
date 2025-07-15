package net.ghue.ktp.gradle.project

import net.ghue.ktp.lib.KtpVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.create
import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion

class ProjectPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val mode = project.findProjectMode()
        project.logger.lifecycle(
            "Applying KTP gradle project plugin to ${project.name}, " +
                "mode: $mode, " +
                "KTP: ${KtpVersion.VERSION}" +
                "Kotlin: ${project.getKotlinPluginVersion()}"
        )
        @Suppress("UNUSED_VARIABLE")
        val ktpExt: KtpGradlePluginExtension = project.extensions.create("ktp")

        project.repositories.mavenCentral()
        project.repositories.maven { url = project.uri("https://jitpack.io") }
        project.repositories.mavenLocal()

        project.version = System.getenv("VERSION") ?: "0-SNAPSHOT"

        when (mode) {
            ProjectMode.LIBRARY -> project.applyLibrary()
            ProjectMode.KTOR -> project.applyKtor()
        }
    }
}
