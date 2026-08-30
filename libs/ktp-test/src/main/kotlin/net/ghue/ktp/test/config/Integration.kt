package net.ghue.ktp.test.config

import io.kotest.core.Tag

/**
 * Marks integration tests; the KTP Gradle plugin's `test`/`integrationTest` split keys on this
 * tag's name, so renaming it silently breaks the split.
 */
object Integration : Tag()
