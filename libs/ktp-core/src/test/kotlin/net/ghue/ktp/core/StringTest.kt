package net.ghue.ktp.core

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class StringTest :
    StringSpec({
        "decodeUrlEncoded replaces plus with spaces" { "a+b+c".decodeUrlEncoded() shouldBe "a b c" }

        "remove drops characters from a string" {
            "a-b-c".remove('-') shouldBe "abc"
            "a-b-c".remove('-', 'b') shouldBe "ac"
        }

        "toGzippedBytes and ungzipToString round trip content" {
            val original = "hello compressed world"
            val compressed = original.toGzippedBytes()

            compressed.ungzipToString() shouldBe original
        }
    })
