package net.ghue.ktp.gcp.auth

/** An explicit account/access denial from the login hook; clears the session and returns 401. */
class AuthDeniedException(message: String = "Access denied") : RuntimeException(message)
