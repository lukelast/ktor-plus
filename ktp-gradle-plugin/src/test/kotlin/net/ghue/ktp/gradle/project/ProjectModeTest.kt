package net.ghue.ktp.gradle.project

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.gradle.testfixtures.ProjectBuilder

class ProjectModeTest {

    @Test
    fun `defaults to KTOR`() {
        val project = ProjectBuilder.builder().build()
        assertEquals(ProjectMode.KTOR, project.resolveProjectMode())
    }

    @Test
    fun `a package json means FRONTEND`() {
        val project = ProjectBuilder.builder().build()
        project.projectDir.resolve("package.json").writeText("{}")
        assertEquals(ProjectMode.FRONTEND, project.resolveProjectMode())
    }

    @Test
    fun `the root of a multi-project build means ROOT`() {
        val root = ProjectBuilder.builder().build()
        ProjectBuilder.builder().withParent(root).build()
        assertEquals(ProjectMode.ROOT, root.resolveProjectMode())
    }

    @Test
    fun `a subproject is not ROOT`() {
        val root = ProjectBuilder.builder().build()
        val child = ProjectBuilder.builder().withParent(root).build()
        assertEquals(ProjectMode.KTOR, child.resolveProjectMode())
    }

    @Test
    fun `an explicit property beats auto-detection`() {
        val project = ProjectBuilder.builder().build()
        project.projectDir.resolve("package.json").writeText("{}")
        project.extensions.extraProperties["ktp.mode"] = "library"
        assertEquals(ProjectMode.LIBRARY, project.resolveProjectMode())
    }

    @Test
    fun `an invalid property value fails naming the bad value`() {
        val project = ProjectBuilder.builder().build()
        project.extensions.extraProperties["ktp.mode"] = "banana"
        val error = assertFailsWith<IllegalStateException> { project.resolveProjectMode() }
        assertContains(error.message.orEmpty(), "banana")
    }

    @Test
    fun `explicit root mode on a subproject fails`() {
        val root = ProjectBuilder.builder().build()
        val child = ProjectBuilder.builder().withParent(root).build()
        child.extensions.extraProperties["ktp.mode"] = "root"
        val error = assertFailsWith<IllegalStateException> { child.resolveProjectMode() }
        assertContains(error.message.orEmpty(), "detected automatically")
    }
}
