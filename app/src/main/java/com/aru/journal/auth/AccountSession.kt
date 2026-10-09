package com.aru.journal.auth

data class AccountSession(val accountId: String, val expiresAtEpochMillis: Long) {
    init { require(accountId.isNotBlank()) }
}

/** Implement with the chosen real authentication SDK. Never manufacture a session in production. */
fun interface SessionProvider { fun currentSession(): AccountSession? }

class AccountRequiredException : IllegalStateException("A valid account session is required")

/** Deliberately fails closed until real authentication is configured. */
object UnconfiguredSessionProvider : SessionProvider {
    override fun currentSession(): AccountSession? = null
}
