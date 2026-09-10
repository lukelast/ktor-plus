package net.ghue.ktp.gradle.project

import org.gradle.api.Project

/** A `true`/`false` Gradle property; null when unset, an error for any other value. */
internal fun Project.booleanProperty(name: String): Boolean? =
    when (val value = findProperty(name)?.toString()) {
        null -> null
        "true" -> true
        "false" -> false
        else ->
            error(
                "File 'gradle.properties', field '$name', has invalid value '$value'. " +
                    "Valid values are: true, false."
            )
    }
