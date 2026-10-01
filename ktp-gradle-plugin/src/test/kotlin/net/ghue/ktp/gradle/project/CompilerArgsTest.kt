package net.ghue.ktp.gradle.project

import kotlin.test.Test
import kotlin.test.assertContains
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
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

    private fun Project.compileKotlinArgs(): List<String> =
        (tasks.getByName("compileKotlin") as KotlinCompile).compilerOptions.freeCompilerArgs.get()
}
