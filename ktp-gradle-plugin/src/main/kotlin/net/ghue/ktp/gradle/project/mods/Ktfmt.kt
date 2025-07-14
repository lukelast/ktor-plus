package net.ghue.ktp.gradle.project.mods

import com.ncorti.ktfmt.gradle.KtfmtExtension
import com.ncorti.ktfmt.gradle.KtfmtPlugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

fun Project.applyKtfmt() {
    // https://github.com/cortinico/ktfmt-gradle
    pluginManager.apply(KtfmtPlugin::class.java)

    // Configure ktfmt to use 4 spaces.
    extensions.configure<KtfmtExtension> { kotlinLangStyle() }
}
