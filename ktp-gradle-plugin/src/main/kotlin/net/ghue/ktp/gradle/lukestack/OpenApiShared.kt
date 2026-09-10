package net.ghue.ktp.gradle.lukestack

import net.ghue.ktp.gradle.project.booleanProperty
import org.gradle.api.Project
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider

internal const val OPEN_API_EXPORT_TASK = "openApiExport"

internal fun Project.openApiEnabled(): Boolean = booleanProperty("ktp.openapi") ?: false

/** Where ktp-test's `OpenApiExportSpec` writes, relative to the backend project directory. */
internal val Project.openApiSpec: Provider<RegularFile>
    get() = layout.buildDirectory.file("openapi/openapi.json")
