package net.ghue.ktp.ktor.error

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode

class KtpRspExTest :
    StringSpec({
        "KtpRspExNotFound populates RFC-friendly fields" {
            val ex = KtpRspExNotFound(name = "User", id = "123")

            ex.status shouldBe HttpStatusCode.NotFound
            ex.title shouldBe "User Not Found"
            ex.detail shouldBe "The User with ID '123' does not exist."
            ex.id shouldBe "123"
        }

        "ktpRspError builds and throws KtpRspEx with all configured fields" {
            val cause = IllegalStateException("boom")

            val ex =
                shouldThrow<KtpRspEx> {
                    ktpRspError {
                        status = HttpStatusCode.Conflict
                        type = "https://example.com/problems/conflict"
                        title = "Conflict"
                        detail = "Resource version mismatch"
                        internalMessage = "version check failed"
                        this.cause = cause
                        extra("attempt", 3)
                        extra("retryable", true)
                        extra("correlationId", "abc-123")
                    }
                }

            ex.status shouldBe HttpStatusCode.Conflict
            ex.type shouldBe "https://example.com/problems/conflict"
            ex.title shouldBe "Conflict"
            ex.detail shouldBe "Resource version mismatch"
            ex.internalMessage shouldBe "version check failed"
            ex.cause shouldBe cause
            ex.extraFields shouldBe
                mapOf("attempt" to 3, "retryable" to true, "correlationId" to "abc-123")
        }

        "ErrorBuilder.buildExtraFields returns a copy" {
            val builder = ErrorBuilder()
            builder.extra("initial", "one")

            val snapshot = builder.buildExtraFields()
            builder.extra("added", 2)

            snapshot shouldBe mapOf("initial" to "one")
            builder.buildExtraFields() shouldBe mapOf("initial" to "one", "added" to 2)
        }
    })
