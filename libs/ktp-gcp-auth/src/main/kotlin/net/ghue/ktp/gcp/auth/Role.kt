package net.ghue.ktp.gcp.auth

/** A role a user may hold; membership is by [name] against [HasRoles.roles]. */
class Role(val name: String) {
    override fun toString(): String = name

    companion object {
        /**
         * The stock administrator role: every local-dev login carries it and `installDebugRoutes`
         * guards the debug pages with it by default. Apps need no `Role("admin")` of their own.
         */
        val ADMIN = Role("admin")
    }
}

val emptyRole = Role("")
