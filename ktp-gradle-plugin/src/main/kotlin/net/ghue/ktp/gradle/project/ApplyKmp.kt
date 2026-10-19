package net.ghue.ktp.gradle.project

import net.ghue.ktp.gradle.project.features.applyDetekt
import net.ghue.ktp.gradle.project.features.applyKotlinMultiplatform
import net.ghue.ktp.gradle.project.features.applyKtfmt
import net.ghue.ktp.gradle.project.features.registerVerifyTask
import net.ghue.ktp.lib.KtpVersion
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.targets.js.KotlinWasmTargetType
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrTarget
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget

private val KMP_BOMS =
    listOf(
        "io.ktor:ktor-bom:${KtpVersion.KTOR}",
        "org.jetbrains.kotlinx:kotlinx-serialization-bom:${KtpVersion.KOTLINX_SERIALIZATION}",
        "org.jetbrains.kotlinx:kotlinx-coroutines-bom:${KtpVersion.KOTLINX_COROUTINES}",
    )

/**
 * A Kotlin Multiplatform library. The build script declares the targets; this supplies the plugins,
 * repositories, the JVM toolchain and bytecode level, the Ktor and kotlinx BOMs the KTP server is
 * built on (so Ktor and kotlinx coordinates need no version), formatting, static analysis and
 * `verify`.
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
        // Kotlin 2.4 marks Wasm/JS interop experimental, and a web module cannot avoid it
        // (DOM, storage, fetch), so the opt-in is a module convention, not a per-file annotation.
        targets.withType<KotlinJsIrTarget>().configureEach {
            if (wasmTargetType == KotlinWasmTargetType.JS) {
                compilerOptions { freeCompilerArgs.add("-opt-in=kotlin.js.ExperimentalWasmJsInterop") }
            }
        }
        // `api` so consumers (the backend, the UI) resolve this module's versionless deps the same way.
        sourceSets.named { it == "commonMain" }.configureEach {
            dependencies {
                KMP_BOMS.forEach { api(project.dependencies.platform(it)) }
            }
        }
    }
    applyKtfmt()
    registerVerifyTask()
}
