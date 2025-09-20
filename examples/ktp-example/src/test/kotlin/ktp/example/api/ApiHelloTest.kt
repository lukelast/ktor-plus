package ktp.example.api

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.*
import io.ktor.client.statement.*
import ktp.example.ktpApp
import net.ghue.ktp.test.testKtpStart

class ApiHelloTest :
    StringSpec({
        "hello endpoint works" {
            testKtpStart(ktpApp) { client.get("/").bodyAsText() shouldBe "KTP is running!" }
        }
    })
