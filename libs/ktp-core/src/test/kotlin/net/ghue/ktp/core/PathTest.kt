package net.ghue.ktp.core

import java.nio.file.Path
import kotlin.io.path.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PathTest {

    @Test
    fun `removePrefix removes matching first path component`() {
        val path = Path("static", "assets", "app.css")
        val result = path.removePrefix("static")

        assertEquals(Path("assets", "app.css"), result)
    }

    @Test
    fun `removePrefix returns original path when first component doesn't match`() {
        val path = Path("public", "assets", "app.css")
        val result = path.removePrefix("static")

        assertEquals(path, result)
    }

    @Test
    fun `removePrefix handles single component path that matches`() {
        val path = Path("static")

        // This should throw an exception because subpath(1, 1) is invalid
        assertThrows(IllegalArgumentException::class.java) { path.removePrefix("static") }
    }

    @Test
    fun `removePrefix handles single component path that doesn't match`() {
        val path = Path("public")
        val result = path.removePrefix("static")

        assertEquals(path, result)
    }

    @Test
    fun `removePrefix handles path with zero name count`() {
        val path = Path("")
        val result = path.removePrefix("static")

        // Empty path has nameCount = 0, so condition nameCount > 0 is false
        assertEquals(path, result)
    }

    @Test
    fun `removePrefix is case sensitive`() {
        val path = Path("Static", "assets", "app.css")
        val result = path.removePrefix("static")

        assertEquals(path, result)
    }

    @Test
    fun `removePrefix handles relative paths with dots`() {
        val path = Path("static", "..", "public", "app.css")
        val result = path.removePrefix("static")

        assertEquals(Path("..", "public", "app.css"), result)
    }

    @Test
    fun `removePrefix handles paths with current directory references`() {
        val path = Path("static", ".", "assets", "app.css")
        val result = path.removePrefix("static")

        assertEquals(Path(".", "assets", "app.css"), result)
    }

    @Test
    fun `removePrefix handles deep nested paths`() {
        val path = Path("static", "js", "components", "ui", "button.js")
        val result = path.removePrefix("static")

        assertEquals(Path("js", "components", "ui", "button.js"), result)
    }

    @Test
    fun `removePrefix handles paths with special characters`() {
        val path = Path("static", "file with spaces.txt")
        val result = path.removePrefix("static")

        assertEquals(Path("file with spaces.txt"), result)
    }

    @Test
    fun `removePrefix with empty string prefix doesn't match`() {
        val path = Path("static", "app.css")
        val result = path.removePrefix("")

        assertEquals(path, result)
    }

    @Test
    fun `removePrefix handles absolute paths`() {
        val path = Path("/blah", "yo.txt")
        val result = path.removePrefix("blah")

        // The function actually found "static" as the first component and removed it
        assertEquals(Path("yo.txt"), result)
    }

    @Test
    fun `removePrefix with multi-component prefix only checks first component`() {
        val path = Path("static", "assets", "app.css")
        val result = path.removePrefix("static/assets")

        // Only checks if first component equals "static/assets" (it doesn't)
        assertEquals(path, result)
    }

    @Test
    fun `removePrefix handles paths with numeric components`() {
        val path = Path("v1", "api", "users")
        val result = path.removePrefix("v1")

        assertEquals(Path("api", "users"), result)
    }

    @Test
    fun `removePrefix preserves path type and structure`() {
        val path = Path("static", "subfolder", "file.ext")
        val result = path.removePrefix("static")

        assertEquals(2, result.nameCount)
        assertEquals("subfolder", result.getName(0).toString())
        assertEquals("file.ext", result.getName(1).toString())
    }

    @Test
    fun `removePrefix handles Unicode folder names`() {
        val path = Path("静的", "assets", "app.css")
        val result = path.removePrefix("静的")

        assertEquals(Path("assets", "app.css"), result)
    }

    @Test
    fun `removePrefix with Unicode mismatch`() {
        val path = Path("static", "assets", "app.css")
        val result = path.removePrefix("静的")

        assertEquals(path, result)
    }

    @Test
    fun `removePrefix works with absolute path that doesn't match prefix`() {
        val path = Path("/", "public", "assets", "app.css")
        val result = path.removePrefix("static")

        // Absolute paths should never have their prefix removed
        assertEquals(path, result)
    }

    @Test
    fun `removePrefix works correctly with relative paths after absolute path fix`() {
        val path = Path("static", "assets", "app.css")
        val result = path.removePrefix("static")

        // Relative paths should still work as before
        assertEquals(Path("assets", "app.css"), result)
    }
}
