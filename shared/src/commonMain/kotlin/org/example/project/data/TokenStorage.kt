package org.example.project.data

/**
 * Where the refresh token lives between launches.
 *
 * A refresh token is a thirty-day key to the account, so implementations put it in the
 * platform's encrypted store — Keychain on iOS, Keystore-wrapped preferences on Android
 * — never in plain preferences. The access token is short-lived and kept in memory only,
 * which is why there is nothing to store for it here.
 *
 * An interface rather than an `expect class`: each platform needs different construction
 * arguments, and the fake below makes the session logic testable without either.
 */
interface TokenStorage {
    suspend fun readRefreshToken(): String?
    suspend fun writeRefreshToken(token: String)
    suspend fun clear()

    /** Stable per install, generated on first use. Never a hardware identifier. */
    suspend fun installationId(): String
}

/** In-memory stand-in for tests and previews. */
class InMemoryTokenStorage(
    private var token: String? = null,
    private val installationId: String = "test-device",
) : TokenStorage {
    override suspend fun readRefreshToken(): String? = token
    override suspend fun writeRefreshToken(token: String) { this.token = token }
    override suspend fun clear() { token = null }
    override suspend fun installationId(): String = installationId
}
