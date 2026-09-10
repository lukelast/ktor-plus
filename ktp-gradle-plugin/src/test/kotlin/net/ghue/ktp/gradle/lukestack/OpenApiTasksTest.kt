package net.ghue.ktp.gradle.lukestack

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.testfixtures.ProjectBuilder

class OpenApiTasksTest {
    @Test
    fun `missing generated files do not disable generation or verification`() {
        val root = ProjectBuilder.builder().build()
        root.extensions.extraProperties["ktp.openapi"] = "true"
        val backend = ProjectBuilder.builder().withName("backend").withParent(root).build()
        val export = backend.tasks.register(OPEN_API_EXPORT_TASK).get()
        val frontend = frontend(root)
        frontend.registerOpenApiTypesTasks()

        val generate = frontend.tasks.getByName("apiGenerate")
        val check = frontend.tasks.getByName("apiCheck")
        assertContains(generate.dependencies(), export)
        assertContains(check.dependencies(), export)
        assertContains(frontend.tasks.getByName("check").dependencies(), check)
        assertFalse(check.dependencies().contains(generate))
        assertTrue(generate.outputs.files.files.contains(frontend.file("src/api/schema.d.ts")))
    }

    @Test
    fun `apps are unaffected until they opt in`() {
        val root = ProjectBuilder.builder().build()
        val frontend = frontend(root)
        frontend.registerOpenApiTypesTasks()
        assertFalse(frontend.tasks.names.contains("apiCheck"))
        assertFalse(frontend.tasks.names.contains("apiGenerate"))
    }

    private fun Task.dependencies(): Set<Task> = taskDependencies.getDependencies(this)

    private fun frontend(root: Project): Project =
        ProjectBuilder.builder().withName("frontend").withParent(root).build().also { project ->
            listOf("format", "lint", "test", "bundle", "check").forEach {
                project.tasks.register(it)
            }
        }
}
