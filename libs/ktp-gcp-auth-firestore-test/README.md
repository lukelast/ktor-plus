# Testing an app on the GCP auth stack

Add it next to the auth library:

```kotlin
dependencies {
    implementation(KtpLibs.gcpAuthFirestore)
    testImplementation(KtpLibs.gcpAuthFirestoreTest)
}
```

## Signed-in route tests

```kotlin
ktpAuthTestApp(app.withGcpStubs()) {
    client.get("/api/me").body<RpcMe>().email shouldBe "test-user@example.test"
}
```

- `app.withGcpStubs(firebase, firestore)` replaces the GCP clients, so the real app serves requests
  with no project or credentials. `FirebaseAuth` is a strict mock and `Firestore` a relaxed one;
  pass your own to script account checks or to use an emulator. Give the result to
  `OpenApiExportSpec` and to `ktpTestApp` for tests that need no session.
- A trailing block swaps app services: `app.withGcpStubs { single<FirestoreService> { db } }`.
- `ktpAuthTestApp(appFactory, session)` is `ktpTestApp` whose `client` keeps cookies and starts
  signed in as `session`; `session = null` starts as a visitor.
- `testSession(Role.ADMIN)` builds the session for `TEST_USER_ID` in `TEST_TENANT_ID`. Its account
  check is fresh by the app's clock, so no request reaches Firebase.
- `client.signIn(session)` switches user mid-test; `client.post(AuthUrls.LOGOUT)` signs out.

## Account rechecks

`testSession(due = true)` makes the next request recheck the account. The `FirebaseAuth` mock
decides whether the account still exists and is enabled:

```kotlin
val firebase = mockk<FirebaseAuth> { every { getUser(TEST_USER_ID.value) } returns disabled }
ktpAuthTestApp(app.withGcpStubs(firebase), testSession(due = true)) {
    client.get("/api/me").status shouldBe HttpStatusCode.Unauthorized
}
```

A recheck that passes then runs the login hook, as `/auth/login` does, so those tests also replace
`AuthLifecycleHandler` in the trailing block or script the user document on the `Firestore` mock.
