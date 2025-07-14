package net.ghue.ktp.core

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ResourceTest {
    private val file = "file.txt"
    private val fileNotExist = "does-not-exist.txt"

    @Test
    fun `the resource file does not exist`() {
        val ex = assertThrows<IllegalStateException> { Resource.read(fileNotExist) }
        assertEquals("Unable to find resource file named: $fileNotExist", ex.message)
    }

    @Test
    fun `readOrNull and is null`() {
        assertEquals(null, Resource.readOrNull(fileNotExist))
    }

    @Test
    fun `readOrNull and is not null`() {
        assertNotNull(Resource.readOrNull(file))
    }

    @Test
    fun `read a resource file`() {
        assertTrue(Resource.read(file).isNotBlank())
    }
}
