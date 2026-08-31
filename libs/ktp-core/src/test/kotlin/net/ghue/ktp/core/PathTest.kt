package net.ghue.ktp.core

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlin.io.path.Path

class PathTest :
    StringSpec({
        "removeFirstFolder removes matching first path component" {
            val path = Path("static", "assets", "app.css")
            val result = path.removeFirstFolder("static")

            result shouldBe Path("assets", "app.css")
        }

        "removeFirstFolder returns original path when first component doesn't match" {
            val path = Path("public", "assets", "app.css")
            val result = path.removeFirstFolder("static")

            result shouldBe path
        }

        "removeFirstFolder throws when removing the only path component" {
            val path = Path("static")

            shouldThrow<IllegalArgumentException> { path.removeFirstFolder("static") }
        }

        "removeFirstFolder handles single component path that doesn't match" {
            val path = Path("public")
            val result = path.removeFirstFolder("static")

            result shouldBe path
        }

        "removeFirstFolder handles path with zero name count" {
            val path = Path("")
            val result = path.removeFirstFolder("static")

            result shouldBe path
        }

        "removeFirstFolder is case sensitive" {
            val path = Path("Static", "assets", "app.css")
            val result = path.removeFirstFolder("static")

            result shouldBe path
        }

        "removeFirstFolder handles relative paths with dots" {
            val path = Path("static", "..", "public", "app.css")
            val result = path.removeFirstFolder("static")

            result shouldBe Path("..", "public", "app.css")
        }

        "removeFirstFolder handles paths with current directory references" {
            val path = Path("static", ".", "assets", "app.css")
            val result = path.removeFirstFolder("static")

            result shouldBe Path(".", "assets", "app.css")
        }

        "removeFirstFolder handles deep nested paths" {
            val path = Path("static", "js", "components", "ui", "button.js")
            val result = path.removeFirstFolder("static")

            result shouldBe Path("js", "components", "ui", "button.js")
        }

        "removeFirstFolder handles paths with special characters" {
            val path = Path("static", "file with spaces.txt")
            val result = path.removeFirstFolder("static")

            result shouldBe Path("file with spaces.txt")
        }

        "removeFirstFolder with empty string prefix doesn't match" {
            val path = Path("static", "app.css")
            val result = path.removeFirstFolder("")

            result shouldBe path
        }

        "removeFirstFolder handles absolute paths" {
            val path = Path("/blah", "yo.txt")
            val result = path.removeFirstFolder("blah")

            result shouldBe Path("yo.txt")
        }

        "removeFirstFolder with multi-component prefix only checks first component" {
            val path = Path("static", "assets", "app.css")
            val result = path.removeFirstFolder("static/assets")

            result shouldBe path
        }

        "removeFirstFolder handles paths with numeric components" {
            val path = Path("v1", "api", "users")
            val result = path.removeFirstFolder("v1")

            result shouldBe Path("api", "users")
        }

        "removeFirstFolder preserves path type and structure" {
            val path = Path("static", "subfolder", "file.ext")
            val result = path.removeFirstFolder("static")

            result.nameCount shouldBe 2
            result.getName(0).toString() shouldBe "subfolder"
            result.getName(1).toString() shouldBe "file.ext"
        }

        "removeFirstFolder handles Unicode folder names" {
            val path = Path("静的", "assets", "app.css")
            val result = path.removeFirstFolder("静的")

            result shouldBe Path("assets", "app.css")
        }

        "removeFirstFolder with Unicode mismatch" {
            val path = Path("static", "assets", "app.css")
            val result = path.removeFirstFolder("静的")

            result shouldBe path
        }

        "removeFirstFolder works with absolute path that doesn't match prefix" {
            val path = Path("/", "public", "assets", "app.css")
            val result = path.removeFirstFolder("static")

            result shouldBe path
        }
    })
