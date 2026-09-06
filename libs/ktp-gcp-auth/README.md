# KTP Auth (ktp-gcp-auth)

Firebase login on the way in, an encrypted stateless cookie as the session, roles on routes, and a
local-dev login for browsers without Firebase state. `ktp-gcp-auth-firestore` adds the user records
(below); [ktp-login-react](https://github.com/lukelast/ktp-login-react) is the matching browser
client.

## Setup

```kotlin
val app = ktpAppCreate {
    addModule(firebaseAuthModule())
    addModule(firestoreModule())
    addModule(firestoreUserStoreModule()) // ktp-gcp-auth-firestore
    addAppInit { config ->
        installDefaultPlugins(config)
        install(FirebaseAuthPlugin)
        installDebugRoutes() // /debug/* behind Role.ADMIN
    }
}
```

```kotlin
routing {
    authenticateFirebase {
        get("/api/me") { call.respond(call.userOrError()) }
        requireRole(Role.ADMIN) { get("/api/admin") { ... } }
    }
    // Signed-out visitors too; a cookie that is present is still fully checked.
    authenticateFirebase(optional = true) {
        post("/api/roast") { val user = call.userOrNull() ... }
    }
}
```

Inside `authenticateFirebase {}` the principal is a `UserPrincipal` (`userId`, `tenantId`,
`email`, `name`, `roles`) from `call.userOrNull()` or `call.userOrError()`. `requireRole` nests
inside it and answers 403 to anyone without the role. With `optional = true` the block also serves
requests with no cookie, where `call.userOrNull()` is null; a cookie that is present passes the
same checks as on a required route, including the periodic recheck below, and a failed check gets
the same 401 or 503, so a disabled or deleted account cannot keep its signed-in benefits by holding
on to its cookie. Never read `UserSession` from `call.sessions` on a route: that skips the recheck.

`UserPrincipal.isAnonymous` is true when the session carries no email, which is what a Firebase
anonymous sign-in produces (its `LoginIdentity.email` is empty). It cannot mean anything else in
practice: every other sign-in must have a verified email, so a provider that supplies none (a
phone-only account, say) is refused at login with 401.

`Role` is a name compared against the session's roles; `Role.ADMIN` (`admin`) is the stock one,
granted to every local-dev login and guarding `installDebugRoutes` by default, so apps need no
`Role("admin")` of their own.

Config (`9.auth.conf`, override in the app's own layer):

```hocon
auth {
  sessionTimeout = "7d"
  # FirebaseAuthPlugin forces this off in local dev and in the test envs regardless of this value.
  secureCookies = true
}
```

## Routes

`FirebaseAuthPlugin` registers fixed routes (`AuthUrls`) that browser clients hardcode:

| Route                 | Purpose                                                                              |
|-----------------------|--------------------------------------------------------------------------------------|
| `GET /auth/session`   | Restores and renews the cookie; periodically refreshes account status and roles      |
| `GET /auth/config`    | Browser Firebase keys, enabled sign-in methods, and the `devLogin` flag              |
| `POST /auth/login`    | Verifies a Firebase ID token, runs the login hook, sets the cookie                   |
| `POST /auth/logout`   | Clears the cookie                                                                    |
| `GET /auth/dev/login` | Local dev only: signs in as a dev user and redirects                                 |

`/auth/config` reads the project's Identity Platform settings via Application Default Credentials
(the service account needs `roles/firebaseauth.viewer`), caches them for 10 minutes on the server
and in the browser, and keeps serving the last good value if a refresh fails.

## Sessions

The cookie is the session; Firebase only establishes it. It is a `UserSession` encrypted and
signed with `app.secret` mixed with the env name (so a cookie from one environment cannot replay
in another), named after `app.name`, `HttpOnly`, `SameSite=Lax`, and `Secure` outside local dev
and the test environments (`setUnitTestEnv()` / `setIntegrationTestEnv()`, where the server speaks
plain HTTP and a client would never send a `Secure` cookie back).
A page load calls `/auth/session`, which reads the cookie without Firebase or storage until the
session's last check is two days old. Then the next request, on `/auth/session` or inside
`authenticateFirebase`, checks the Firebase account (exists, enabled, email verified unless still
anonymous) and runs the login hook again for current roles; the refreshed roles apply to that same
request and the cookie is re-issued. A deleted or disabled account, or a login hook throwing
`AuthDeniedException`, clears the cookie and returns 401. A Firebase or storage failure returns 503
without clearing or renewing it, and the next request retries. Login verifies the ID token with
Firebase's revocation check; revalidation checks the account, not the token.

The cookie's `lastValidatedAt` is separate from its sliding expiry and advances only on a
successful check, so a cookie issued before this field existed is checked on its next request.
Local-dev users (`dev` / `dev-<name>`) have no Firebase account, so they skip the check, keep their
injected roles, and are only accepted in local dev. The module registers a system UTC `Clock`,
which an app can override in its module. An app that installs `Sessions` itself must register
`cookie<UserSession>` or login fails.

## Login hook

Every login, Firebase or dev, produces a `LoginIdentity` (user id, email, name) for the app's
`AuthLifecycleHandler.onLogin`, which returns the `UserSession` the cookie carries (tenant, roles).
This hook also runs during periodic revalidation; it must be safe to repeat. Throw
`AuthDeniedException` when the user is no longer allowed in. Other exceptions indicate a retryable
failure. Always load current roles and apply the same access rules on every invocation.
An app using `ktp-gcp-auth-firestore` implements nothing: its `FirestoreUserStore` is the stock
handler. An app wanting more defines its own handler (app definitions load after library modules,
so theirs wins) and can wrap the store, calling `login` for the record.

## Local-dev login

`GET /auth/dev/login?user=alice&redirect=/p/home` signs the browser in as `dev-alice`
(`alice@dev.test`) through the normal login hook, so it gets a real record and tenant in the local
database. Every `user` is its own set of test data; leaving it out signs in as the user `dev`.
Every dev session includes `Role.ADMIN`. Optional `roles` are added on top of the stored ones
and `redirect` must be a same-origin path. The route is
registered only when the env is local dev, so elsewhere it does not exist. It is for browsers
without the developer's Firebase state, such as coding agents driving a local instance.

## Testing an app

`firebaseAuthModule()` builds the Firebase client from Application Default Credentials and the
ambient GCP project, and `FirebaseAuthPlugin` resolves it, together with the app's login hook, on
the first request that touches the session (the 401 for a missing cookie included). An app test on
a machine without either (CI) therefore replaces it, and the Firestore client with it when the
login hook needs one:

```kotlin
val testApp = app.update {
    addModule {
        single<FirebaseAuth> { mockk() }
        single<Firestore> { mockk(relaxed = true) }
    }
    // The app's own @Singleton FirestoreService is defined by its Koin config, which loads after
    // addModule; only an override module loads later still.
    addOverrideModule { single<FirestoreService> { db } }
}
ktpTestApp(testApp) {
    val browser = createClient { install(HttpCookies) }
    // Seed a session through a test-only route (call.sessions.set(session)); the cookie is not
    // Secure in the test env, so the plain-http test client sends it back.
    ...
}
```

A `UserSession` with a recent `lastValidatedAt` is served from the cookie alone; one with
`lastValidatedAt = 0` is due for the recheck and exercises the mocked `FirebaseAuth.getUser`.

A route test that does not install `FirebaseAuthPlugin` at all can register
`authentication { dummy(principal = session) }` instead: it accepts every request inside
`authenticateFirebase {}` as that principal, with no cookie and no Firebase client.

## User records (ktp-gcp-auth-firestore)

`FirestoreUserStore` keeps one `KtpUser` document per user id in the `user` collection
(`firestoreUserStoreModule(collectionName)` to rename). The first login is a create-only write that
mints a tenant id, and a lost create race adopts the winner's record, so concurrent first logins
never mint two tenants; later logins merge only `name`, `email`, and `lastLogin`, so fields other
writers own (roles, app additions) survive. Periodic revalidation uses this same hook and updates
`lastLogin`; a missing Firestore profile is recreated, just as at login, and is not itself an
account ban. `KtpUser` is a `DocTimes` type: `createTime` is the
first login and `updateTime` the last write, read from Firestore's metadata rather than stored.
The module binds the store as both itself and the `AuthLifecycleHandler`, and uses the app's
`java.time.Clock` when bound. Apps wanting the stored record call `login` or `get`.
