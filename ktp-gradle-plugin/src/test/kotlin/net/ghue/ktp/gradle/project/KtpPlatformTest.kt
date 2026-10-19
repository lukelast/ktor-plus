package net.ghue.ktp.gradle.project

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.ghue.ktp.lib.KtpLibs
import org.gradle.api.Project
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.attributes.Category
import org.gradle.api.plugins.JavaPlugin
import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet

class KtpPlatformTest {

    private val bom = KtpLibs.bom.substringBeforeLast(':')

    private fun projectInMode(mode: ProjectMode, configure: Project.() -> Unit = {}): Project {
        val project = ProjectBuilder.builder().build()
        project.extensions.extraProperties[MODE_KEY] = mode.name.lowercase()
        project.configure()
        project.pluginManager.apply(ProjectPlugin::class.java)
        return project
    }

    /** `group:name` of every platform dependency declared on [configuration]. */
    private fun Project.platformsOn(configuration: String): List<String> =
        configurations
            .getByName(configuration)
            .dependencies
            .filterIsInstance<ModuleDependency>()
            .filter {
                it.attributes.getAttribute(Category.CATEGORY_ATTRIBUTE)?.name ==
                    Category.REGULAR_PLATFORM
            }
            .map { "${it.group}:${it.name}" }

    /** Every dependency on ktp-bom in any configuration, platform or not. */
    private fun Project.ktpBomDependencies(): List<String> =
        configurations.flatMap { conf ->
            conf.dependencies
                .filter { "${it.group}:${it.name}" == bom }
                .map { conf.name }
        }

    @Test
    fun `an app module gets ktp-bom on implementation`() {
        val project = projectInMode(ProjectMode.KTOR)
        assertTrue(bom in project.platformsOn(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME))
    }

    @Test
    fun `a library module exports ktp-bom on api, so its published apiElements carry it`() {
        val project = projectInMode(ProjectMode.LIBRARY)
        assertTrue(bom in project.platformsOn(JavaPlugin.API_CONFIGURATION_NAME))
    }

    @Test
    fun `a multiplatform module exports ktp-bom from commonMain`() {
        val project = projectInMode(ProjectMode.KMP)
        val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
        kotlin.jvm()
        val commonMain = kotlin.sourceSets.getByName(KotlinSourceSet.COMMON_MAIN_SOURCE_SET_NAME)
        assertEquals(listOf(bom), project.platformsOn(commonMain.apiConfigurationName))
    }

    @Test
    fun `a project without Kotlin gets no platform`() {
        val project = projectInMode(ProjectMode.FRONTEND) { file(PACKAGE_JSON).writeText("{}") }
        assertEquals(emptyList(), project.ktpBomDependencies())
    }
}
