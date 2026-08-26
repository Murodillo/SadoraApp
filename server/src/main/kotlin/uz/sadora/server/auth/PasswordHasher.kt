package uz.sadora.server.auth

import at.favre.lib.crypto.bcrypt.BCrypt
import uz.sadora.server.core.ValidationException

/**
 * Passwords go through bcrypt at cost 12 — deliberately slow, unlike the SHA-256 used
 * for refresh tokens, because a password is low-entropy and guessable.
 */
object PasswordHasher {

    private const val COST = 12
    private const val MIN_LENGTH = 8

    fun hash(rawPassword: String): String {
        validate(rawPassword)
        return BCrypt.withDefaults().hashToString(COST, rawPassword.toCharArray())
    }

    fun verify(rawPassword: String, hash: String): Boolean =
        runCatching {
            BCrypt.verifyer().verify(rawPassword.toCharArray(), hash).verified
        }.getOrDefault(false)

    fun validate(rawPassword: String) {
        if (rawPassword.length < MIN_LENGTH) {
            throw ValidationException("password", "Kamida $MIN_LENGTH ta belgi bo'lishi kerak")
        }
        if (rawPassword.none { it.isDigit() } || rawPassword.none { it.isLetter() }) {
            throw ValidationException("password", "Harf va raqamdan iborat bo'lishi kerak")
        }
    }
}
