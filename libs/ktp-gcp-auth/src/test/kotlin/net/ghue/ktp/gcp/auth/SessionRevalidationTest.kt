package net.ghue.ktp.gcp.auth

import com.google.firebase.ErrorCode
import com.google.firebase.auth.AuthErrorCode
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseToken
import com.google.firebase.auth.UserInfo
import com.google.firebase.auth.UserRecord
import io.kotest.core.annotation.Isolate
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.CookieEncoding
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.install
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.sessions.defaultSessionSerializer
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import io.ktor.server.testing.testApplication
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.ghue.ktp.config.Env
import net.ghue.ktp.config.KtpConfig
import net.ghue.ktp.config.LOCAL_DEV_ENV_NAME
import org.koin.dsl.module
import org.koin.ktor.plugin.KoinIsolated

private val NOW = Instant.parse("2026-09-05T12:00:00Z")

// Avoid racing Firebase SDK class instrumentation with the other Firebase specs.
@Isolate
class SessionRevalidationTest :
    StringSpec({
        "cookie renewal preserves the check time and a due check refreshes the user and roles" {
            val fixture = RecheckFixture()
            fixture.app { browser ->
                browser.login().status shouldBe HttpStatusCode.OK
                val first = browser.get("/private").session()
                first.lastValidatedAt shouldBe NOW.epochSecond
                fixture.clock.advance(1.days)
                browser.get(AuthUrls.SESSION).status shouldBe HttpStatusCode.OK
                browser.get("/private").session().lastValidatedAt shouldBe first.lastValidatedAt
                verify(exactly = 1) { fixture.firebase.getUser("alice") }

                fixture.roles = setOf("user")
                fixture.name = "Updated Alice"
                fixture.clock.advance(1.days)
                browser.get(AuthUrls.SESSION).status shouldBe HttpStatusCode.OK
                val refreshed = browser.get("/private").session()
                refreshed.roles shouldBe setOf("user")
                refreshed.name shouldBe "Updated Alice"
                refreshed.lastValidatedAt shouldBe fixture.clock.instant().epochSecond
                fixture.loginCalls shouldBe 2
                verify(exactly = 2) { fixture.firebase.getUser("alice") }
                browser.get(AuthUrls.SESSION).status shouldBe HttpStatusCode.OK
                verify(exactly = 2) { fixture.firebase.getUser("alice") }
            }
        }

        "a protected request uses refreshed roles before authorizing the same request" {
            val fixture = RecheckFixture()
            fixture.roles = setOf("user")
            fixture.app { browser ->
                browser.get("/seed")
                val response = browser.get("/admin")
                response.status shouldBe HttpStatusCode.Forbidden
                fixture.protectedCalls shouldBe 0
                (response.headers[HttpHeaders.SetCookie] != null) shouldBe true
                browser.get("/private").session().roles shouldBe setOf("user")
                verify(exactly = 1) { fixture.firebase.getUser("alice") }
            }
        }

        val denials: Map<String, RecheckFixture.() -> Unit> =
            mapOf(
                "disabled account" to { disabled = true },
                "deleted account" to
                    {
                        lookupFailure = firebaseFailure(AuthErrorCode.USER_NOT_FOUND)
                    },
                "unverified email" to { verified = false },
                "application access denial" to { loginFailure = AuthDeniedException() },
            )
        for ((reason, deny) in denials) {
            for (path in listOf(AuthUrls.SESSION, "/private", "/admin")) {
                "$reason clears the cookie and denies $path" {
                    val fixture = RecheckFixture().apply(deny)
                    fixture.app { browser ->
                        browser.get("/seed")
                        val response = browser.get(path)
                        response.status shouldBe HttpStatusCode.Unauthorized
                        response.headers[HttpHeaders.CacheControl] shouldBe "no-store"
                        response.clearsCookie() shouldBe true
                        fixture.protectedCalls shouldBe 0
                        browser.get("/private").status shouldBe HttpStatusCode.Unauthorized
                        verify(exactly = 1) { fixture.firebase.getUser("alice") }
                    }
                }
            }
            "$reason also rejects a fresh login" {
                val fixture = RecheckFixture().apply(deny)
                fixture.app { browser ->
                    browser.login().status shouldBe HttpStatusCode.Unauthorized
                }
            }
        }

        for (path in listOf(AuthUrls.SESSION, "/private", "/admin")) {
            for (dependency in listOf("Firebase", "user store")) {
                "$dependency failure preserves the cookie and retries on $path" {
                    val fixture = RecheckFixture()
                    if (dependency == "Firebase") fixture.lookupFailure = IOException("Unavailable")
                    else fixture.loginFailure = IOException("Unavailable")
                    fixture.app { browser ->
                        browser.get("/seed")
                        val response = browser.get(path)
                        response.status shouldBe HttpStatusCode.ServiceUnavailable
                        response.headers[HttpHeaders.CacheControl] shouldBe "no-store"
                        response.headers[HttpHeaders.SetCookie] shouldBe null
                        fixture.protectedCalls shouldBe 0
                        fixture.lookupFailure = null
                        fixture.loginFailure = null
                        browser.get(path).status shouldBe HttpStatusCode.OK
                        verify(exactly = 2) { fixture.firebase.getUser("alice") }
                    }
                }
            }
        }

        "Firebase infrastructure errors are retryable rather than credential rejections" {
            val fixture = RecheckFixture()
            fixture.lookupFailure = firebaseFailure(AuthErrorCode.CONFIGURATION_NOT_FOUND)
            fixture.app { browser ->
                browser.get("/seed")
                browser.get(AuthUrls.SESSION).status shouldBe HttpStatusCode.ServiceUnavailable
                val response = browser.login()
                response.status shouldBe HttpStatusCode.ServiceUnavailable
                response.headers[HttpHeaders.SetCookie] shouldBe null
            }
        }

        "a revoked token is rejected at login" {
            val fixture = RecheckFixture()
            every { fixture.firebase.verifyIdToken("valid-token", true) } throws
                firebaseFailure(AuthErrorCode.REVOKED_ID_TOKEN)
            fixture.app { browser -> browser.login().status shouldBe HttpStatusCode.Unauthorized }
        }

        "a legacy encrypted cookie receives a full check and an updated cookie" {
            val fixture = RecheckFixture()
            fixture.roles = setOf("user")
            fixture.app { browser ->
                browser.get("/legacy").status shouldBe HttpStatusCode.NoContent
                val response = browser.get(AuthUrls.SESSION)
                response.status shouldBe HttpStatusCode.OK
                (response.headers[HttpHeaders.SetCookie] != null) shouldBe true
                val refreshed = browser.get("/private").session()
                refreshed.lastValidatedAt shouldBe NOW.epochSecond
                refreshed.roles shouldBe setOf("user")
                verify(exactly = 1) { fixture.firebase.getUser("alice") }
                verify(exactly = 0) { fixture.firebase.verifyIdToken(any(), any()) }
            }
        }

        for (stamp in listOf(0L, NOW.plusSeconds(1).epochSecond)) {
            "a missing or future validation timestamp ($stamp) requires a full check" {
                val fixture = RecheckFixture()
                fixture.seed = fixture.seed.copy(lastValidatedAt = stamp)
                fixture.app { browser ->
                    browser.get("/seed")
                    browser.get("/private").session().lastValidatedAt shouldBe NOW.epochSecond
                    verify(exactly = 1) { fixture.firebase.getUser("alice") }
                }
            }
        }

        "anonymous accounts can log in and revalidate until an unverified provider is linked" {
            val fixture = RecheckFixture()
            fixture.anonymous = true
            fixture.verified = false
            fixture.email = ""
            fixture.app { browser ->
                browser.login().status shouldBe HttpStatusCode.OK
                fixture.clock.advance(2.days)
                browser.get("/private").status shouldBe HttpStatusCode.OK
                fixture.email = "alice@example.test"
                fixture.providerData = arrayOf(mockk<UserInfo>())
                fixture.clock.advance(2.days)
                browser.get("/private").status shouldBe HttpStatusCode.Unauthorized
            }
        }

        "local dev sessions preserve their extra roles without loading Firebase after two days" {
            val fixture = RecheckFixture()
            fixture.app(localDev = true) { browser ->
                browser.get("${AuthUrls.DEV_LOGIN}?roles=ops").status shouldBe HttpStatusCode.Found
                fixture.clock.advance(3.days)
                browser.get("/private").session().roles shouldBe setOf("admin", "ops")
                verify(exactly = 0) { fixture.firebase.getUser(any()) }
            }
        }

        "a dev session is rejected outside local dev even with a fresh check timestamp" {
            val fixture = RecheckFixture()
            fixture.seed =
                fixture.seed.copy(userId = UserId(DEV_USER_ID), lastValidatedAt = NOW.epochSecond)
            fixture.app { browser ->
                browser.get("/seed")
                browser.get("/private").status shouldBe HttpStatusCode.Unauthorized
            }
        }
    })

private class RecheckFixture {
    val clock = RecheckClock()
    val firebase = mockk<FirebaseAuth>()
    var disabled = false
    var verified = true
    var name = "Alice"
    var email = "alice@example.test"
    var roles = setOf("admin")
    var anonymous = false
    var providerData = emptyArray<UserInfo>()
    var lookupFailure: Exception? = null
    var loginFailure: Exception? = null
    var loginCalls = 0
    var protectedCalls = 0
    var seed =
        UserSession(
            UserId("alice"),
            "alice@example.test",
            "Alice",
            setOf("admin"),
            TenantId("tenant"),
            NOW.minusSeconds(2.days.inWholeSeconds).epochSecond,
        )

    private val user =
        mockk<UserRecord> {
            every { isDisabled } answers { disabled }
            every { isEmailVerified } answers { verified }
            every { displayName } answers { name }
            every { email } answers { this@RecheckFixture.email }
            every { getProviderData() } answers { this@RecheckFixture.providerData }
        }
    private val token =
        mockk<FirebaseToken> {
            every { uid } returns "alice"
            every { claims } answers
                {
                    if (anonymous) mapOf("provider_id" to "anonymous") else emptyMap()
                }
        }
    private val lifecycle =
        object : AuthLifecycleHandler {
            override suspend fun onLogin(identity: LoginIdentity): UserSession {
                loginCalls++
                loginFailure?.let { throw it }
                return UserSession(
                    identity.userId,
                    identity.email,
                    identity.name,
                    roles,
                    TenantId("tenant"),
                )
            }
        }

    init {
        every { firebase.verifyIdToken("valid-token", true) } returns token
        every { firebase.getUser("alice") } answers
            {
                lookupFailure?.let { throw it }
                user
            }
    }

    fun app(
        localDev: Boolean = false,
        test: suspend (HttpClient) -> Unit,
    ) = testApplication {
        val config = KtpConfig.create {
            if (localDev) env = Env(LOCAL_DEV_ENV_NAME) else setUnitTestEnv()
            overrideValue("app.name", "test.app")
            overrideValue("auth.secureCookies", "false")
        }
        application {
            install(KoinIsolated) {
                modules(
                    firebaseAuthModule(),
                    module {
                        single { config }
                        single { firebase }
                        single<AuthLifecycleHandler> { lifecycle }
                        single<Clock> { clock }
                    },
                )
            }
            install(FirebaseAuthPlugin)
            routing {
                get("/seed") {
                    call.sessions.set(seed)
                    call.respond(HttpStatusCode.NoContent)
                }
                get("/legacy") {
                    val legacy =
                        LegacySession(seed.userId, seed.email, seed.name, seed.roles, seed.tenantId)
                    val serialized = defaultSessionSerializer<LegacySession>().serialize(legacy)
                    call.response.cookies.append(
                        "TEST_APP",
                        config.createSessionTransportTransformer().transformWrite(serialized),
                        encoding = CookieEncoding.URI_ENCODING,
                        path = "/",
                    )
                    call.respond(HttpStatusCode.NoContent)
                }
                authenticateFirebase {
                    get("/private") {
                        protectedCalls++
                        call.respondText(Json.encodeToString(call.principal<UserSession>()!!))
                    }
                    requireRole(Role("admin")) {
                        get("/admin") {
                            protectedCalls++
                            call.respond(HttpStatusCode.OK)
                        }
                    }
                }
            }
        }
        test(
            createClient {
                install(HttpCookies)
                followRedirects = false
            }
        )
    }
}

private class RecheckClock : Clock() {
    private var now = NOW

    override fun instant(): Instant = now

    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock = Clock.fixed(now, zone)

    fun advance(duration: Duration) {
        now = now.plusSeconds(duration.inWholeSeconds)
    }
}

@Serializable
private data class LegacySession(
    override val userId: UserId,
    override val email: String,
    override val name: String,
    override val roles: Set<String>,
    override val tenantId: TenantId,
) : UserPrincipal

private fun firebaseFailure(code: AuthErrorCode) =
    FirebaseAuthException(ErrorCode.UNKNOWN, "Firebase failure", null, null, code)

private suspend fun HttpClient.login() =
    post(AuthUrls.LOGIN) {
        contentType(ContentType.Application.Json)
        setBody("""{"idToken":"valid-token"}""")
    }

private fun HttpResponse.clearsCookie(): Boolean =
    headers.getAll(HttpHeaders.SetCookie).orEmpty().any { "01 Jan 1970" in it }

private suspend fun HttpResponse.session(): UserSession {
    status shouldBe HttpStatusCode.OK
    return Json.decodeFromString(bodyAsText())
}
