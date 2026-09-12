package uz.sadora.server.core

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-GCM for the third-party tokens the server has to keep in the clear-readable
 * sense — a refresh token must be replayed, so it cannot be hashed the way our own are.
 *
 * Each value gets a fresh 12-byte nonce, prepended to the ciphertext; the whole thing
 * is base64 in one column. A wrong or rotated key fails to decrypt rather than
 * returning garbage, which is what the sync job needs to mark the connection expired.
 */
class TokenCipher(keyBytes: ByteArray) {

    init {
        require(keyBytes.size == KEY_BYTES) { "Token key must be $KEY_BYTES bytes" }
    }

    private val key = SecretKeySpec(keyBytes, "AES")
    private val random = SecureRandom()

    fun encrypt(plain: String): String {
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
        val sealed = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(nonce + sealed)
    }

    fun decrypt(stored: String): String {
        val bytes = Base64.getDecoder().decode(stored)
        require(bytes.size > NONCE_BYTES) { "Ciphertext too short" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, bytes, 0, NONCE_BYTES))
        return String(cipher.doFinal(bytes, NONCE_BYTES, bytes.size - NONCE_BYTES), Charsets.UTF_8)
    }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_BYTES = 32
        private const val NONCE_BYTES = 12
        private const val TAG_BITS = 128

        /** From the configured base64 key, or derived from a secret when none is set. */
        fun from(configuredKey: String?, fallbackSecret: String): TokenCipher {
            val bytes = configuredKey
                ?.let { runCatching { Base64.getDecoder().decode(it) }.getOrNull() }
                ?.takeIf { it.size == KEY_BYTES }
                ?: MessageDigest.getInstance("SHA-256").digest(fallbackSecret.toByteArray(Charsets.UTF_8))
            return TokenCipher(bytes)
        }
    }
}
