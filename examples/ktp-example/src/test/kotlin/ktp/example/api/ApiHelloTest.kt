package ktp.example.api

import io.kotest.assertions.ktor.client.shouldBeOK
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import ktp.example.ktpApp
import net.ghue.ktp.test.testKtpStart

class ApiHelloTest :
    StringSpec({
        "hello endpoint works" {
            testKtpStart(ktpApp) {
                val rsp = client.get("/")
                rsp.shouldBeOK()
                rsp.bodyAsText() shouldContain "KTP"
            }
        }
    })
