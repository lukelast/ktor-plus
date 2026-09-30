package net.ghue.ktp.gradle.lukestack

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.gradle.testfixtures.ProjectBuilder

class GcloudInfraSetupTest {
    @Test
    fun `the setup task replaces the three single-command tasks`() {
        val project = ProjectBuilder.builder().withName("demo").build()

        project.registerGcloudTasks()

        val inputs = project.tasks.getByName("gcloudInfraSetup").inputs.properties
        assertEquals("demo", inputs["projectId"])
        assertEquals("infra-manager@demo.iam.gserviceaccount.com", inputs["serviceAccountEmail"])
        for (old in listOf("gcloudInfraIam", "gcloudInfraBind", "gcloudInfraEnable")) {
            assertFalse(project.tasks.names.contains(old), old)
        }
        assertTrue(project.tasks.names.contains("gcloudInfraDeploy"))
    }
}
