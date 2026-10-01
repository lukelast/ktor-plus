package net.ghue.ktp.gcp.auth.firestore.test

import com.google.cloud.firestore.Firestore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserRecord
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import net.ghue.ktp.gcp.auth.AuthLifecycleHandler
import net.ghue.ktp.gcp.auth.AuthUrls
import net.ghue.ktp.gcp.auth.LoginIdentity
import net.ghue.ktp.gcp.auth.Role
import net.ghue.ktp.gcp.auth.TenantId
import net.ghue.ktp.test.ktpTestApp

class KtpAuthTestAppTest :
    StringSpec({
        "the client starts signed in as the test user and decodes JSON bodies" {
            ktpAuthTestApp(lukestackApp.withGcpStubs()) {
                client.get("/api/me").body<RpcMe>() shouldBe
                    RpcMe("test-user@example.test", TEST_TENANT_ID.value)
            }
        }

        "a null session starts signed out, and signIn switches the user" {
            ktpAuthTestApp(lukestackApp.withGcpStubs(), session = null) {
                client.get("/api/me").status shouldBe HttpStatusCode.Unauthorized

                client.signIn(testSession(email = "ada@example.test"))
                client.get("/api/me").body<RpcMe>().email shouldBe "ada@example.test"

                client.signIn(testSession(email = "bob@example.test", tenantId = TenantId("b")))
                client.get("/api/me").body<RpcMe>() shouldBe RpcMe("bob@example.test", "b")

                client.post(AuthUrls.LOGOUT).status shouldBe HttpStatusCode.NoContent
                client.get("/api/me").status shouldBe HttpStatusCode.Unauthorized
            }
        }

        "signIn refuses a client that cannot keep the cookie" {
            ktpAuthTestApp(lukestackApp.withGcpStubs(), session = null) {
                shouldThrow<IllegalStateException> { createClient {}.signIn() }
            }
        }

        "session roles reach the role check" {
            ktpAuthTestApp(lukestackApp.withGcpStubs()) {
                client.get("/debug").status shouldBe HttpStatusCode.Forbidden
            }
            ktpAuthTestApp(lukestackApp.withGcpStubs(), session = testSession(Role.ADMIN)) {
                client.get("/debug").status shouldBe HttpStatusCode.OK
            }
        }

        "a fresh session is fresh by the app's own clock" {
            val longAgo = Clock.fixed(Instant.parse("2020-01-01T00:00:00Z"), ZoneOffset.UTC)

            ktpAuthTestApp(lukestackApp.withGcpStubs { single<Clock> { longAgo } }) {
                client.get("/api/me").status shouldBe HttpStatusCode.OK
            }
        }

        "a due session is refused when the scripted account is disabled" {
            val disabled = mockk<UserRecord> { every { isDisabled } returns true }
            val firebase =
                mockk<FirebaseAuth> { every { getUser(TEST_USER_ID.value) } returns disabled }

            ktpAuthTestApp(lukestackApp.withGcpStubs(firebase), testSession(due = true)) {
                client.get("/api/me").status shouldBe HttpStatusCode.Unauthorized
            }
            verify(exactly = 1) { firebase.getUser(TEST_USER_ID.value) }
        }

        "a due session that passes its recheck takes the login hook's answer" {
            val account =
                mockk<UserRecord> {
                    every { isDisabled } returns false
                    every { isEmailVerified } returns true
                    every { email } returns "renamed@example.test"
                    every { displayName } returns "Renamed"
                }
            val firebase =
                mockk<FirebaseAuth> { every { getUser(TEST_USER_ID.value) } returns account }
            val loginHook =
                mockk<AuthLifecycleHandler> {
                    coEvery { onLogin(any()) } answers
                        {
                            testSession(email = firstArg<LoginIdentity>().email)
                        }
                }
            val app =
                lukestackApp.withGcpStubs(firebase) { single<AuthLifecycleHandler> { loginHook } }

            ktpAuthTestApp(app, testSession(due = true)) {
                client.get("/api/me").body<RpcMe>().email shouldBe "renamed@example.test"
            }
        }

        "a Firestore passed in stays open when the app stops" {
            val firestore = mockk<Firestore>(relaxed = true)

            ktpAuthTestApp(lukestackApp.withGcpStubs(firestore = firestore)) {
                client.get("/api/me").status shouldBe HttpStatusCode.OK
            }
            verify(exactly = 0) { firestore.close() }
        }

        "the stubs alone let a visitor reach the session check without credentials" {
            ktpTestApp(lukestackApp.withGcpStubs()) {
                client.get("/api/me").status shouldBe HttpStatusCode.Unauthorized
            }
        }

        "config overrides reach the app" {
            ktpAuthTestApp(
                lukestackApp.withGcpStubs(),
                configOverrides = mapOf("app.version" to "69"),
            ) {
                client.get("/debug/version").body<String>() shouldBe "69"
            }
        }
    })
