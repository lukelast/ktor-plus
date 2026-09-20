# KTP Auth

Firebase authentication, encrypted session cookies, route authorization, and local-dev login.

## Setup

Depend on `KtpLibs.gcpAuth`. Firebase requires a GCP project and Application Default Credentials.

```kotlin
val app = ktpAppCreate {
    addKoinConfig(koinConfiguration<MyApp>()) // Provides AuthLifecycleHandler.
    addModule(firebaseAuthModule())
    addAppInit { config ->
        installDefaultPlugins(config)
        install(FirebaseAuthPlugin)
    }
}
```

Provide an `AuthLifecycleHandler` through Koin. Its
`onLogin(LoginIdentity): UserSession` supplies the user's tenant and current roles.
It runs at login and periodic revalidation: keep it repeatable and apply access rules each time.
Throw `AuthDeniedException` to deny access.

## Protected routes

Inside `routing`:

```kotlin
authenticateFirebase {
    get("/api/me") { call.respond(call.userOrError()) }
    requireRole(Role.ADMIN) { get("/api/admin") { /* admin handler */ } }
}
```

Use `call.userOrError()` or `call.userOrNull()` for the validated principal
(`userId`, `tenantId`, `email`, `name`, `roles`). Reading the session cookie directly bypasses
revalidation. Missing roles return 403.

`authenticateFirebase(optional = true)` allows visitors without a cookie but still validates any
cookie present. A Firebase anonymous account is a signed-in principal with `isAnonymous = true`;
a visitor without a session has a null principal.

## Auth endpoints

| Method | Path | Purpose |
|---|---|---|
| GET | `/auth/session` | Restore or renew the session |
| GET | `/auth/config` | Browser Firebase settings and enabled providers |
| POST | `/auth/login` | Exchange a Firebase ID token for a session cookie |
| POST | `/auth/logout` | Clear the session |
| GET | `/auth/dev/login` | Local-dev sign-in |

`/auth/config` requires `roles/firebaseauth.viewer` on the service account.
Results cache for 10 minutes; failed refreshes retain the last good value.

## Sessions

Cookies use `app.secret` and the environment name, with `HttpOnly` and `SameSite=Lax`.
Defaults: `auth.sessionTimeout = "7d"` and `auth.secureCookies = true`; secure cookies are
disabled in local dev and tests. If the app installs `Sessions` itself, register `cookie<UserSession>`.

After two days, the next authenticated request rechecks the Firebase account and calls the login
hook for current roles. Non-anonymous accounts require verified email, which excludes phone-only
sign-ins. Rejected accounts get 401 and lose the cookie; Firebase or storage failures return 503
and preserve it for retry.

## Local-dev login

`GET /auth/dev/login?user=alice&redirect=/p/home` signs in as `dev-alice` through the login hook.
Omitting `user` signs in as `dev`.
Dev sessions include `Role.ADMIN`; optional `roles` add more. Redirects must be same-origin.
This route and dev sessions are accepted only in local dev.

## Testing an app

Use `addOverrideModule` in the test's app builder; it loads after library and app bindings:

```kotlin
addOverrideModule {
    single<FirebaseAuth> { mockk() }
    single<AuthLifecycleHandler> { mockk() }
}
```

Stub the operations exercised, including the login hook.
Set `UserSession.lastValidatedAt = 0` to exercise revalidation.
Route-only tests can use `authentication { dummy(principal = session) }` without the auth plugin.
