package ktp.example.api

import io.kotest.assertions.ktor.client.shouldBeOK
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import ktp.example.appFactory
import net.ghue.ktp.test.ktpTestApp

class ApiHelloTest :
    StringSpec({
        "hello endpoint works" {
            ktpTestApp(appFactory) {
                val rsp = client.get("/")
                rsp.shouldBeOK()
                rsp.bodyAsText() shouldContain "KTP"
            }
        }
    })
