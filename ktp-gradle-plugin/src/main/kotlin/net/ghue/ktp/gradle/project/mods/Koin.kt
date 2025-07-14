package net.ghue.ktp.gradle.project.mods

import net.ghue.ktp.gradle.common.Version
import com.google.devtools.ksp.gradle.KspGradleSubplugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

fun Project.applyKspGradlePlugin() {
    // https://github.com/google/ksp/blob/d8efe453f331f0568ffc8c3f2f64a1cbbd951a81/gradle-plugin/build.gradle.kts#L55
    pluginManager.apply(KspGradleSubplugin::class.java)
}

fun Project.applyKoinKspCompiler(configName: String = "ksp") {
    // https://kotlinlang.org/docs/ksp-quickstart.html
    // https://insert-koin.io/docs/setup/annotations
    dependencies { add(configName, "io.insert-koin:koin-ksp-compiler:${Version.KOIN_KSP}") }
}

fun Project.applyKoinKspMultiplatform() {
    applyKspGradlePlugin()

    extensions.configure<KotlinMultiplatformExtension> {
        // This is required to init the JVM so the KSP config can use it later.
        jvm {}
        sourceSets.jvmMain.configure {
            // Add the KSP generated sources.
            kotlin.srcDir(layout.buildDirectory.dir("generated/ksp/jvm/jvmMain/kotlin"))
        }
    }

    applyKoinKspCompiler(configName = "kspJvm")
    afterEvaluate { tasks.getByName("kspKotlinJvm").dependsOn("kspCommonMainKotlinMetadata") }
}
