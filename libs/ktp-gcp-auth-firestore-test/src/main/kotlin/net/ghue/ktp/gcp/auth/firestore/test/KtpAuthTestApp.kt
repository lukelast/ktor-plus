package net.ghue.ktp.gcp.auth.firestore.test

import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.pluginOrNull
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import io.ktor.server.testing.ApplicationTestBuilder
import java.time.Clock
import kotlinx.serialization.json.Json
import net.ghue.ktp.gcp.auth.UserSession
import net.ghue.ktp.ktor.start.KtpAppBuilderFactory
import net.ghue.ktp.test.ktpTestApp
import net.ghue.ktp.test.ktpTestClient
import org.koin.ktor.ext.getKoin

private const val SIGN_IN_PATH = "/ktp-test/sign-in"

/**
 * [ktpTestApp] for an app that installs `FirebaseAuthPlugin`: `client` keeps cookies and starts
 * signed in as [session] (null for a visitor), and [signIn] switches user mid-test. Pass a factory
 * whose GCP clients are stubbed, e.g. `app.withGcpStubs()`.
 */
fun ktpAuthTestApp(
    appFactory: KtpAppBuilderFactory,
    session: UserSession? = testSession(),
    configOverrides: Map<String, Any> = emptyMap(),
    test: suspend ApplicationTestBuilder.() -> Unit,
) {
    ktpTestApp(appFactory, start = false, configOverrides = configOverrides) {
        // A route rather than a hand-built cookie, so the app's own session plugin mints it.
        application {
            routing {
                post(SIGN_IN_PATH) {
                    val signedIn = Json.decodeFromString<UserSession>(call.receiveText())
                    // Fresh by the app's own clock, which a test may have fixed; 0 stays due.
                    val now = call.application.getKoin().get<Clock>().instant().epochSecond
                    val fresh = signedIn.lastValidatedAt != 0L
                    call.sessions.set(if (fresh) signedIn.copy(lastValidatedAt = now) else signedIn)
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }
        startApplication()
        client = ktpTestClient { install(HttpCookies) }
        if (session != null) {
            client.signIn(session)
        }
        test()
    }
}

/** Replaces this client's session cookie with one for [session]; only inside [ktpAuthTestApp]. */
suspend fun HttpClient.signIn(session: UserSession = testSession()) {
    checkNotNull(pluginOrNull(HttpCookies)) {
        "signIn needs a client that keeps cookies, such as ktpAuthTestApp's `client`."
    }
    val status = post(SIGN_IN_PATH) { setBody(Json.encodeToString(session)) }.status
    check(status == HttpStatusCode.NoContent) {
        "Test sign-in answered $status: the app must install FirebaseAuthPlugin and run in " +
            "ktpAuthTestApp."
    }
}
