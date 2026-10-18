package net.ghue.ktp.gradle.project

import net.ghue.ktp.gradle.project.features.applyDetekt
import net.ghue.ktp.gradle.project.features.applyKotlinMultiplatform
import net.ghue.ktp.gradle.project.features.applyKtfmt
import net.ghue.ktp.gradle.project.features.registerVerifyTask
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget

/**
 * A Kotlin Multiplatform library. The build script declares the targets; this supplies the plugins,
 * repositories, the JVM toolchain and bytecode level, formatting, static analysis and `verify`.
 * Tests are the per-target `kotlin("test")` runs that `check` already aggregates, so the JVM-only
 * kotest and fat-jar setup of the other modes stays out.
 */
internal fun Project.applyKmp(composeRules: Boolean = false) {
    // Compose and some kotlinx artifacts resolve only from Google's repository.
    repositories.google()
    applyDetekt(allSources = true, composeRules = composeRules)
    applyKotlinMultiplatform()
    extensions.configure<KotlinMultiplatformExtension> {
        // Compile on the JDK the backend already uses, emit library-level bytecode: a Compose
        // desktop target on an older runtime, and the backend alike, can consume the module.
        jvmToolchain(buildToolchainJdk())
        targets.withType<KotlinJvmTarget>().configureEach {
            compilerOptions {
                jvmTarget.set(JvmTarget.fromTarget(LIBRARY_JDK.toString()))
                freeCompilerArgs.add("-Xjdk-release=$LIBRARY_JDK")
            }
        }
    }
    applyKtfmt()
    registerVerifyTask()
}
