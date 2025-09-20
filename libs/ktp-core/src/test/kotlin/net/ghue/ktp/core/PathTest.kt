package net.ghue.ktp.core

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Path
import kotlin.io.path.Path

class PathTest :
    StringSpec({
        "removePrefix removes matching first path component" {
            val path = Path("static", "assets", "app.css")
            val result = path.removePrefix("static")

            result shouldBe Path("assets", "app.css")
        }

        "removePrefix returns original path when first component doesn't match" {
            val path = Path("public", "assets", "app.css")
            val result = path.removePrefix("static")

            result shouldBe path
        }

        "removePrefix handles single component path that matches" {
            val path = Path("static")

            shouldThrow<IllegalArgumentException> { path.removePrefix("static") }
        }

        "removePrefix handles single component path that doesn't match" {
            val path = Path("public")
            val result = path.removePrefix("static")

            result shouldBe path
        }

        "removePrefix handles path with zero name count" {
            val path = Path("")
            val result = path.removePrefix("static")

            result shouldBe path
        }

        "removePrefix is case sensitive" {
            val path = Path("Static", "assets", "app.css")
            val result = path.removePrefix("static")

            result shouldBe path
        }

        "removePrefix handles relative paths with dots" {
            val path = Path("static", "..", "public", "app.css")
            val result = path.removePrefix("static")

            result shouldBe Path("..", "public", "app.css")
        }

        "removePrefix handles paths with current directory references" {
            val path = Path("static", ".", "assets", "app.css")
            val result = path.removePrefix("static")

            result shouldBe Path(".", "assets", "app.css")
        }

        "removePrefix handles deep nested paths" {
            val path = Path("static", "js", "components", "ui", "button.js")
            val result = path.removePrefix("static")

            result shouldBe Path("js", "components", "ui", "button.js")
        }

        "removePrefix handles paths with special characters" {
            val path = Path("static", "file with spaces.txt")
            val result = path.removePrefix("static")

            result shouldBe Path("file with spaces.txt")
        }

        "removePrefix with empty string prefix doesn't match" {
            val path = Path("static", "app.css")
            val result = path.removePrefix("")

            result shouldBe path
        }

        "removePrefix handles absolute paths" {
            val path = Path("/blah", "yo.txt")
            val result = path.removePrefix("blah")

            result shouldBe Path("yo.txt")
        }

        "removePrefix with multi-component prefix only checks first component" {
            val path = Path("static", "assets", "app.css")
            val result = path.removePrefix("static/assets")

            result shouldBe path
        }

        "removePrefix handles paths with numeric components" {
            val path = Path("v1", "api", "users")
            val result = path.removePrefix("v1")

            result shouldBe Path("api", "users")
        }

        "removePrefix preserves path type and structure" {
            val path = Path("static", "subfolder", "file.ext")
            val result = path.removePrefix("static")

            result.nameCount shouldBe 2
            result.getName(0).toString() shouldBe "subfolder"
            result.getName(1).toString() shouldBe "file.ext"
        }

        "removePrefix handles Unicode folder names" {
            val path = Path("静的", "assets", "app.css")
            val result = path.removePrefix("静的")

            result shouldBe Path("assets", "app.css")
        }

        "removePrefix with Unicode mismatch" {
            val path = Path("static", "assets", "app.css")
            val result = path.removePrefix("静的")

            result shouldBe path
        }

        "removePrefix works with absolute path that doesn't match prefix" {
            val path = Path("/", "public", "assets", "app.css")
            val result = path.removePrefix("static")

            result shouldBe path
        }

        "removePrefix works correctly with relative paths after absolute path fix" {
            val path = Path("static", "assets", "app.css")
            val result = path.removePrefix("static")

            result shouldBe Path("assets", "app.css")
        }
    })
