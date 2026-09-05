package net.ghue.ktp.gcp.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseToken
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.install
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.ghue.ktp.config.Env
import net.ghue.ktp.config.KtpConfig
import net.ghue.ktp.config.KtpConfigBuilder
import net.ghue.ktp.config.LOCAL_DEV_ENV_NAME
import org.koin.dsl.module
import org.koin.ktor.plugin.KoinIsolated

/** `/auth/session` and the local-dev login; both mint or read the cookie, never Firebase. */
class SessionRoutesTest :
    StringSpec({
        "session endpoint is 401 with no cookie and never cacheable" {
            authApp(unitTestConfig()) {
                val response = client.get(AuthUrls.SESSION)

                response.status shouldBe HttpStatusCode.Unauthorized
                response.headers[HttpHeaders.CacheControl] shouldBe "no-store"
                response.bodyAsText() shouldBe "{}"
            }
        }

        "session endpoint restores the user from the cookie and re-issues it" {
            val firebaseAuth = mockk<FirebaseAuth>()
            every { firebaseAuth.verifyIdToken(any(), any()) } returns firebaseToken()

            authApp(unitTestConfig(), firebaseAuth = firebaseAuth) {
                val browser = cookieClient()
                browser.firebaseLogin().status shouldBe HttpStatusCode.OK

                val response = browser.get(AuthUrls.SESSION)

                response.status shouldBe HttpStatusCode.OK
                response.headers[HttpHeaders.CacheControl] shouldBe "no-store"
                val user = response.bodyAsText().sessionUser()
                user.string("userId") shouldBe "test-user-id"
                user.string("email") shouldBe "test@example.com"
                user.string("nameFull") shouldBe "Test User"
                user.string("nameFirst") shouldBe "Test"
                // Sliding expiry: every restore pushes the cookie's max-age out again.
                response.headers.getAll(HttpHeaders.SetCookie).shouldNotBeNull().any {
                    it.startsWith("TEST_APP=") && it.contains("Max-Age")
                } shouldBe true
            }
        }

        "session endpoint never verifies a Firebase token or touches the login hook" {
            val firebaseAuth = mockk<FirebaseAuth>()
            every { firebaseAuth.verifyIdToken(any(), any()) } returns firebaseToken()
            val lifecycle = loginPassthrough()

            authApp(unitTestConfig(), firebaseAuth = firebaseAuth, lifecycle = lifecycle) {
                val browser = cookieClient()
                browser.firebaseLogin()
                browser.get(AuthUrls.SESSION).status shouldBe HttpStatusCode.OK
                browser.get(AuthUrls.SESSION).status shouldBe HttpStatusCode.OK

                coVerify(exactly = 1) { lifecycle.onLogin(any()) }
            }
        }

        "dev login route does not exist outside local dev" {
            authApp(unitTestConfig()) {
                client.get(AuthUrls.DEV_LOGIN).status shouldBe HttpStatusCode.NotFound
            }
        }

        "dev login mints a session through the login hook and redirects" {
            val lifecycle = loginPassthrough()

            authApp(localDevConfig(), lifecycle = lifecycle) {
                val browser = cookieClient()
                val response =
                    browser.get(
                        "${AuthUrls.DEV_LOGIN}?user=alice-b&roles=admin,%20ops&redirect=/p/x"
                    )

                response.status shouldBe HttpStatusCode.Found
                response.headers[HttpHeaders.Location] shouldBe "/p/x"
                response.headers[HttpHeaders.CacheControl] shouldBe "no-store"
                coVerify(exactly = 1) {
                    lifecycle.onLogin(
                        LoginIdentity(UserId("dev-alice-b"), "alice-b@dev.test", "Alice B")
                    )
                }

                val user = browser.get(AuthUrls.SESSION).bodyAsText().sessionUser()
                user.string("userId") shouldBe "dev-alice-b"
                user.string("nameFull") shouldBe "Alice B"
                user.getValue("roles").jsonArray.map { it.jsonPrimitive.content } shouldContainAll
                    listOf("user", "admin", "ops")
            }
        }

        "dev login without a name is the unnamed dev user landing on the root" {
            val lifecycle = loginPassthrough()

            authApp(localDevConfig(), lifecycle = lifecycle) {
                val browser = cookieClient()
                for (url in listOf(AuthUrls.DEV_LOGIN, "${AuthUrls.DEV_LOGIN}?user=")) {
                    val response = browser.get(url)

                    response.status shouldBe HttpStatusCode.Found
                    response.headers[HttpHeaders.Location] shouldBe "/"
                }
                coVerify(exactly = 2) {
                    lifecycle.onLogin(LoginIdentity(UserId("dev"), "dev@dev.test", "Dev"))
                }
            }
        }

        "dev login rejects bad user names and off-origin redirects" {
            val lifecycle = loginPassthrough()

            authApp(localDevConfig(), lifecycle = lifecycle) {
                val browser = cookieClient()
                val rejected =
                    listOf(
                        "user=Alice",
                        "user=a.b",
                        "redirect=https://evil.test",
                        "redirect=//evil.test",
                    )
                for (query in rejected) {
                    browser.get("${AuthUrls.DEV_LOGIN}?$query").status shouldBe
                        HttpStatusCode.BadRequest
                }
                browser.get(AuthUrls.SESSION).status shouldBe HttpStatusCode.Unauthorized
                coVerify(exactly = 0) { lifecycle.onLogin(any()) }
            }
        }
    })

private fun unitTestConfig(): KtpConfig = KtpConfig.create {
    setUnitTestEnv()
    cookieFriendly()
}

private fun localDevConfig(): KtpConfig = KtpConfig.create {
    env = Env(LOCAL_DEV_ENV_NAME)
    cookieFriendly()
}

/** The cookie is named after the app, and the test client talks plain HTTP. */
private fun KtpConfigBuilder.cookieFriendly() {
    overrideValue("app.name", "test.app")
    overrideValue("auth.secureCookies", "false")
}

/** A login hook that stores nothing and grants the `user` role, keyed on the identity. */
private fun loginPassthrough(): AuthLifecycleHandler {
    val lifecycle = mockk<AuthLifecycleHandler>()
    coEvery { lifecycle.onLogin(any()) } answers
        {
            val identity = firstArg<LoginIdentity>()
            UserSession(
                userId = identity.userId,
                tenantId = TenantId("tenant-${identity.userId.value}"),
                email = identity.email,
                name = identity.name,
                roles = setOf("user"),
            )
        }
    return lifecycle
}

private fun firebaseToken(): FirebaseToken {
    val token = mockk<FirebaseToken>()
    every { token.uid } returns "test-user-id"
    every { token.email } returns "test@example.com"
    every { token.name } returns "Test User"
    every { token.isEmailVerified } returns true
    every { token.claims } returns emptyMap()
    return token
}

private suspend fun HttpClient.firebaseLogin() =
    post(AuthUrls.LOGIN) {
        contentType(ContentType.Application.Json)
        setBody("""{"idToken":"valid-token"}""")
    }

private fun String.sessionUser(): JsonObject =
    Json.parseToJsonElement(this).jsonObject.getValue("user").jsonObject

private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content

private fun ApplicationTestBuilder.cookieClient(): HttpClient = createClient {
    install(HttpCookies)
    followRedirects = false
}

private fun authApp(
    config: KtpConfig,
    firebaseAuth: FirebaseAuth = mockk(relaxed = true),
    lifecycle: AuthLifecycleHandler = loginPassthrough(),
    test: suspend ApplicationTestBuilder.() -> Unit,
) {
    testApplication {
        application {
            install(KoinIsolated) {
                modules(
                    module {
                        single { config }
                        single { FirebaseAuthService(firebaseAuth, lifecycle) }
                        single { DevLoginService(lifecycle) }
                        single {
                            FirebaseAuthClientConfigService(
                                firebaseApp = mockFirebaseApp(),
                                identityToolkit = mockk(),
                            )
                        }
                    }
                )
            }
            install(FirebaseAuthPlugin)
        }
        test()
    }
}
