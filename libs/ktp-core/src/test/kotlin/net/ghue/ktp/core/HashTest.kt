package net.ghue.ktp.core

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class HashTest {

    @Test
    fun sha512ReturnsCorrectSize() {
        val input = "hello world"
        val result = input.sha512()

        assertEquals(64, result.size)
    }

    @Test
    fun sha512EmptyString() {
        val input = ""
        val result = input.sha512()

        assertEquals(64, result.size)
        assertNotNull(result)
    }

    @Test
    fun sha512UnicodeString() {
        val input = "こんにちは世界"
        val result = input.sha512()

        assertEquals(64, result.size)
        assertNotNull(result)
    }

    @Test
    fun sha256ReturnsCorrectSize() {
        val input = "hello world"
        val result = input.sha256()

        assertEquals(32, result.size)
    }

    @Test
    fun sha256EmptyString() {
        val input = ""
        val result = input.sha256()

        assertEquals(32, result.size)
        assertNotNull(result)
    }

    @Test
    fun sha256UnicodeString() {
        val input = "こんにちは世界"
        val result = input.sha256()

        assertEquals(32, result.size)
        assertNotNull(result)
    }

    @Test
    fun sha512ConsistentResults() {
        val input = "test input"
        val result1 = input.sha512()
        val result2 = input.sha512()

        assertArrayEquals(result1, result2)
    }

    @Test
    fun sha256ConsistentResults() {
        val input = "test input"
        val result1 = input.sha256()
        val result2 = input.sha256()

        assertArrayEquals(result1, result2)
    }

    @Test
    fun sha512KnownValues() {
        val input = "hello world"
        val result = input.sha512()
        val hexString = result.joinToString("") { "%02x".format(it) }

        assertEquals(
            "309ecc489c12d6eb4cc40f50c902f2b4d0ed77ee511a7c7a9bcd3ca86d4cd86f989dd35bc5ff499670da34255b45b0cfd830e81f605dcf7dc5542e93ae9cd76f",
            hexString,
        )
    }

    @Test
    fun sha256KnownValues() {
        val input = "hello world"
        val result = input.sha256()
        val hexString = result.joinToString("") { "%02x".format(it) }

        assertEquals("b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9", hexString)
    }

    @Test
    fun sha512EmptyStringKnownValue() {
        val input = ""
        val result = input.sha512()
        val hexString = result.joinToString("") { "%02x".format(it) }

        assertEquals(
            "cf83e1357eefb8bdf1542850d66d8007d620e4050b5715dc83f4a921d36ce9ce47d0d13c5d85f2b0ff8318d2877eec2f63b931bd47417a81a538327af927da3e",
            hexString,
        )
    }

    @Test
    fun sha256EmptyStringKnownValue() {
        val input = ""
        val result = input.sha256()
        val hexString = result.joinToString("") { "%02x".format(it) }

        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", hexString)
    }
}
