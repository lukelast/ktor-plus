package net.ghue.ktp.core

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class HashTest :
    StringSpec({
        "sha512 returns 64 bytes" {
            val input = "hello world"
            val result = input.sha512()

            result.size shouldBe 64
        }

        "sha512 handles empty string" {
            val result = "".sha512()

            result.size shouldBe 64
            result shouldNotBe null
        }

        "sha512 handles unicode content" {
            val result = "こんにちは世界".sha512()

            result.size shouldBe 64
            result shouldNotBe null
        }

        "sha256 returns 32 bytes" {
            val input = "hello world"
            val result = input.sha256()

            result.size shouldBe 32
        }

        "sha256 handles empty string" {
            val result = "".sha256()

            result.size shouldBe 32
            result shouldNotBe null
        }

        "sha256 handles unicode content" {
            val result = "こんにちは世界".sha256()

            result.size shouldBe 32
            result shouldNotBe null
        }

        "sha512 produces stable results" {
            val input = "test input"
            val result1 = input.sha512()
            val result2 = input.sha512()

            result1.toList().shouldContainExactly(result2.toList())
        }

        "sha256 produces stable results" {
            val input = "test input"
            val result1 = input.sha256()
            val result2 = input.sha256()

            result1.toList().shouldContainExactly(result2.toList())
        }

        "sha512 matches known value" {
            val hexString = "hello world".sha512().joinToString("") { "%02x".format(it) }

            hexString shouldBe
                "309ecc489c12d6eb4cc40f50c902f2b4d0ed77ee511a7c7a9bcd3ca86d4cd86f989dd35bc5ff499670da34255b45b0cfd830e81f605dcf7dc5542e93ae9cd76f"
        }

        "sha256 matches known value" {
            val hexString = "hello world".sha256().joinToString("") { "%02x".format(it) }

            hexString shouldBe "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9"
        }

        "sha512 empty string matches known value" {
            val hexString = "".sha512().joinToString("") { "%02x".format(it) }

            hexString shouldBe
                "cf83e1357eefb8bdf1542850d66d8007d620e4050b5715dc83f4a921d36ce9ce47d0d13c5d85f2b0ff8318d2877eec2f63b931bd47417a81a538327af927da3e"
        }

        "sha256 empty string matches known value" {
            val hexString = "".sha256().joinToString("") { "%02x".format(it) }

            hexString shouldBe "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        }
    })
