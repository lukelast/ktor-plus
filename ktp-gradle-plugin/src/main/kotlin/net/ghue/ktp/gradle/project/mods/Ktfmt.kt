package net.ghue.ktp.gradle.project.mods

import com.ncorti.ktfmt.gradle.KtfmtExtension
import com.ncorti.ktfmt.gradle.KtfmtPlugin
import com.ncorti.ktfmt.gradle.tasks.KtfmtCheckTask
import com.ncorti.ktfmt.gradle.tasks.KtfmtFormatTask
import dev.detekt.gradle.Detekt
import net.ghue.ktp.lib.KtpVersion
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType

fun Project.applyKtfmt() {
    // https://github.com/cortinico/ktfmt-gradle
    pluginManager.apply(KtfmtPlugin::class.java)

    // The Gradle plugin can lag behind the formatter, so keep the engine version current.
    configurations.named("ktfmt") {
        resolutionStrategy.force("com.facebook:ktfmt:${KtpVersion.KTFMT}")
    }

    // Configure ktfmt to use 4 spaces.
    extensions.configure<KtfmtExtension> { kotlinLangStyle() }

    // Order source-reading verifiers after the formatter so `verify` (format + check in one
    // task graph) is race-free. Ordering only: plain `check` never pulls in the format tasks
    // and stays a strict verifier that fails on unformatted code, which is what CI runs.
    val formatTasks = tasks.withType<KtfmtFormatTask>()
    tasks.withType<KtfmtCheckTask>().configureEach { mustRunAfter(formatTasks) }
    tasks.withType<Detekt>().configureEach { mustRunAfter(formatTasks) }
}
