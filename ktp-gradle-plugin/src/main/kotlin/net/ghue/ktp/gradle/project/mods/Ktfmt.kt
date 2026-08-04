package net.ghue.ktp.gradle.project.mods

import com.ncorti.ktfmt.gradle.KtfmtExtension
import com.ncorti.ktfmt.gradle.KtfmtPlugin
import net.ghue.ktp.lib.KtpVersion
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

fun Project.applyKtfmt() {
    // https://github.com/cortinico/ktfmt-gradle
    pluginManager.apply(KtfmtPlugin::class.java)

    // The Gradle plugin can lag behind the formatter, so keep the engine version current.
    configurations.named("ktfmt") {
        resolutionStrategy.force("com.facebook:ktfmt:${KtpVersion.KTFMT}")
    }

    // Configure ktfmt to use 4 spaces.
    extensions.configure<KtfmtExtension> { kotlinLangStyle() }
}
