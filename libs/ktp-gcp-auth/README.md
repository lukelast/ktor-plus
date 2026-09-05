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
}
```

Inside `authenticateFirebase {}` the principal is a `UserPrincipal` (`userId`, `tenantId`,
`email`, `name`, `roles`) from `call.userOrNull()` or `call.userOrError()`. `requireRole` nests
inside it and answers 403 to anyone without the role.

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
| `GET /auth/session`   | The signed-in user from the cookie alone, re-issued to slide expiry; 401 without one |
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
A page load calls `/auth/session`, which touches neither Firebase nor storage, so the login hook
runs once per session, never per page load. An app that installs `Sessions` itself must register
`cookie<UserSession>` or login fails.

## Login hook

Every login, Firebase or dev, produces a `LoginIdentity` (user id, email, name) for the app's
`AuthLifecycleHandler.onLogin`, which returns the `UserInfo` the cookie carries (tenant, roles).
An app using `ktp-gcp-auth-firestore` implements nothing: its `FirestoreUserStore` is the stock
handler. An app wanting more defines its own handler (app definitions load after library modules,
so theirs wins) and can wrap the store, calling `login` for the record.

## Local-dev login

`GET /auth/dev/login?user=alice&roles=admin&redirect=/p/home` signs the browser in as `dev-alice`
(`alice@dev.test`) through the normal login hook, so it gets a real record and tenant in the local
database. Every `user` is its own set of test data; leaving it out signs in as the user `dev`.
`roles` are added on top of the stored ones and `redirect` must be a same-origin path. The route is
registered only when the env is local dev, so elsewhere it does not exist. It is for browsers
without the developer's Firebase state, such as coding agents driving a local instance.

## User records (ktp-gcp-auth-firestore)

`FirestoreUserStore` keeps one `KtpUser` document per user id in the `user` collection
(`firestoreUserStoreModule(collectionName)` to rename). The first login is a create-only write that
mints a tenant id, and a lost create race adopts the winner's record, so concurrent first logins
never mint two tenants; later logins merge only `name`, `email`, and `lastLogin`, so fields other
writers own (roles, app additions) survive. `KtpUser` is a `DocTimes` type: `createTime` is the
first login and `updateTime` the last write, read from Firestore's metadata rather than stored.
The module binds the store as both itself and the `AuthLifecycleHandler`, and uses the app's
`java.time.Clock` when bound. Apps wanting the stored record call `login` or `get`.
