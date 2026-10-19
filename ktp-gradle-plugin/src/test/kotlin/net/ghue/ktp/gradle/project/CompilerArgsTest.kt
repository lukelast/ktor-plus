package net.ghue.ktp.gradle.project

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.tasks.Kotlin2JsCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

class CompilerArgsTest {

    @Test
    fun `an app compile keeps both the jdk-release and jsr305 flags`() {
        val project = ProjectBuilder.builder().build()
        project.applyKtor()
        val args = project.compileKotlinArgs()
        assertContains(args, "-Xjdk-release=25")
        assertContains(args, "-Xjsr305=strict")
    }

    @Test
    fun `a library compile keeps both the jdk-release and jsr305 flags`() {
        val project = ProjectBuilder.builder().build()
        project.applyLibrary()
        val args = project.compileKotlinArgs()
        assertContains(args, "-Xjdk-release=21")
        assertContains(args, "-Xjsr305=strict")
    }

    @Test
    fun `a multiplatform jvm target compiles library bytecode with both flags`() {
        val project = ProjectBuilder.builder().build()
        project.applyKmp()
        project.extensions.getByType(KotlinMultiplatformExtension::class.java).jvm()
        val compile = project.tasks.getByName("compileKotlinJvm") as KotlinCompile
        assertEquals(JvmTarget.JVM_21, compile.compilerOptions.jvmTarget.get())
        val args = compile.compilerOptions.freeCompilerArgs.get()
        assertContains(args, "-Xjdk-release=21")
        assertContains(args, "-Xjsr305=strict")
    }

    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    @Test
    fun `a multiplatform wasm target opts into JS interop`() {
        val project = ProjectBuilder.builder().build()
        project.applyKmp()
        project.extensions.getByType(KotlinMultiplatformExtension::class.java).wasmJs()
        val compile = project.tasks.getByName("compileKotlinWasmJs") as Kotlin2JsCompile
        val args = compile.compilerOptions.freeCompilerArgs.get()
        assertContains(args, "-opt-in=kotlin.js.ExperimentalWasmJsInterop")
    }

    @Test
    fun `compose mode applies both Compose plugins and the Compose detekt rules`() {
        val project = ProjectBuilder.builder().build()
        project.applyCompose()
        assertTrue(project.plugins.hasPlugin("org.jetbrains.compose"))
        assertTrue(project.plugins.hasPlugin("org.jetbrains.kotlin.plugin.compose"))
        val rules = project.configurations.getByName("detektPlugins").dependencies.map { it.name }
        assertContains(rules, "detekt")
    }

    private fun Project.compileKotlinArgs(): List<String> =
        (tasks.getByName("compileKotlin") as KotlinCompile).compilerOptions.freeCompilerArgs.get()
}
