package net.ghue.ktp.core

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class ResourceTest :
    StringSpec({
        val file = "file.txt"
        val fileNotExist = "does-not-exist.txt"

        "read throws when the resource file does not exist" {
            val ex = shouldThrow<IllegalStateException> { Resource.read(fileNotExist) }
            ex.message shouldBe "Unable to find resource file named: $fileNotExist"
        }

        "readOrNull returns null when the resource is missing" {
            Resource.readOrNull(fileNotExist).shouldBeNull()
        }

        "readOrNull returns content when the resource exists" {
            Resource.readOrNull(file).shouldNotBeNull()
        }

        "read loads a resource file" { Resource.read(file).isNotBlank().shouldBeTrue() }
    })
