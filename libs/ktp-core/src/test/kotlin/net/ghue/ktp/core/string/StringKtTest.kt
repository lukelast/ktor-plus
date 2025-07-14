package net.ghue.ktp.core.string

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class StringKtTest {

    @Test
    fun decodeUrlEncoded() {
        assertEquals("a b c", "a+b+c".decodeUrlEncoded())
    }

    @Test
    fun remove() {
        assertEquals("abc", "a-b-c".remove('-'))
        assertEquals("ac", "a-b-c".remove('-', 'b'))
    }
}
