package uz.sadora.server.core

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import kotlin.uuid.Uuid

private val secureRandom = SecureRandom()
private val urlEncoder: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()

/** Opaque, URL-safe random token. Used for refresh tokens and OTP challenge handles. */
fun randomToken(bytes: Int = 32): String {
    val buffer = ByteArray(bytes)
    secureRandom.nextBytes(buffer)
    return urlEncoder.encodeToString(buffer)
}

/** Numeric OTP code, zero-padded so a leading zero is never dropped. */
fun randomNumericCode(length: Int): String =
    (1..length).map { secureRandom.nextInt(10) }.joinToString("")

/**
 * Refresh tokens and OTP codes are stored as SHA-256 digests. They are already
 * high-entropy random values, so a fast digest is the right tool — bcrypt is reserved
 * for passwords, which are not.
 */
fun sha256(value: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

fun parseUuidOrNull(value: String): Uuid? = runCatching { Uuid.parse(value) }.getOrNull()

fun parseUuid(value: String, field: String = "id"): Uuid =
    parseUuidOrNull(value) ?: throw ValidationException(field, "UUID formatida bo'lishi kerak")
