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
        installDebugRoutes(Role("admin")) // /debug/* behind a role
    }
}
```

```kotlin
routing {
    authenticateFirebase {
        get("/api/me") { call.respond(call.userOrError()) }
        requireRole(Role("admin")) { get("/api/admin") { ... } }
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

Config (`9.auth.conf`, override in the app's own layer):

```hocon
auth {
  sessionTimeout = "7d"
  # FirebaseAuthPlugin forces this off in local dev regardless of the value here.
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
in another), named after `app.name`, `HttpOnly`, `SameSite=Lax`, and `Secure` outside local dev.
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
Every dev session includes the `admin` role. Optional `roles` are added on top of the stored ones
and `redirect` must be a same-origin path. The route is
registered only when the env is local dev, so elsewhere it does not exist. It is for browsers
without the developer's Firebase state, such as coding agents driving a local instance.

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
