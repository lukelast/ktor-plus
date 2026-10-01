package net.ghue.ktp.gcp.auth.firestore.test

import java.time.Instant
import net.ghue.ktp.gcp.auth.Role
import net.ghue.ktp.gcp.auth.TenantId
import net.ghue.ktp.gcp.auth.UserId
import net.ghue.ktp.gcp.auth.UserSession

/** The [testSession] user, for scripting mocks and asserting on what a route did with it. */
val TEST_USER_ID = UserId("test-user")

/** The [testSession] tenant. */
val TEST_TENANT_ID = TenantId("test-tenant")

/**
 * A signed-in user whose account check is fresh, so no request reaches Firebase; [due] makes the
 * next request recheck the account.
 */
fun testSession(
    vararg roles: Role,
    userId: UserId = TEST_USER_ID,
    tenantId: TenantId = TEST_TENANT_ID,
    email: String = "test-user@example.test",
    name: String = "Test User",
    due: Boolean = false,
): UserSession =
    UserSession(
        userId = userId,
        email = email,
        name = name,
        roles = roles.map { it.name }.toSet(),
        tenantId = tenantId,
        lastValidatedAt = if (due) 0 else Instant.now().epochSecond,
    )
