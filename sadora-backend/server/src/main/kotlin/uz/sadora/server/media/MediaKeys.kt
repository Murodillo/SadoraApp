package uz.sadora.server.media

import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.uuid.Uuid
import uz.sadora.server.config.Environment
import uz.sadora.server.core.TokenCipher

/**
 * The master key behind every media file, and what is derived from it.
 *
 * Each file has a key of its own, kept in its row wrapped by the master key; the master
 * key itself is only ever in the environment. The signed addresses a video player opens
 * are signed with a second key derived from it, so the two jobs never share a secret.
 *
 * Losing MEDIA_MASTER_KEY loses every photo and video: it belongs in the password
 * manager, apart from the server and from its backups.
 */
class MediaKeys(masterKey: ByteArray) {

    init {
        require(masterKey.size == SegmentedCipher.KEY_BYTES) { "MEDIA_MASTER_KEY must be ${SegmentedCipher.KEY_BYTES} bytes" }
    }

    private val wrapper = TokenCipher(masterKey)
    private val urlKey = hmac(masterKey, "sadora-media-url-v1".toByteArray())

    /** A fresh file key: the cipher to seal with, and the key as the row keeps it. */
    fun newKey(): Pair<SegmentedCipher, String> {
        val key = SegmentedCipher.newKey()
        return SegmentedCipher(key) to wrapper.encrypt(Base64.getEncoder().encodeToString(key))
    }

    fun cipher(wrapped: String): SegmentedCipher = SegmentedCipher(Base64.getDecoder().decode(wrapper.decrypt(wrapped)))

    fun sign(id: Uuid, variant: MediaVariant, expiresAtEpochSeconds: Long): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(hmac(urlKey, "$id:${variant.extension}:$expiresAtEpochSeconds".toByteArray()))

    fun verify(id: Uuid, variant: MediaVariant, expiresAtEpochSeconds: Long, signature: String): Boolean {
        val expected = sign(id, variant, expiresAtEpochSeconds).toByteArray()
        return MessageDigest.isEqual(expected, signature.toByteArray())
    }

    companion object {
        /**
         * From MEDIA_MASTER_KEY (32 bytes, base64). On a laptop a missing key is derived
         * from the JWT secret, so development needs no setup; anywhere else it is null and
         * uploads are refused, because a derived key would quietly change with the secret
         * and take every file with it.
         */
        fun from(configured: String?, environment: Environment, jwtSecret: String): MediaKeys? {
            val bytes = configured
                ?.let { runCatching { Base64.getDecoder().decode(it.trim()) }.getOrNull() }
                ?.takeIf { it.size == SegmentedCipher.KEY_BYTES }
            if (bytes != null) return MediaKeys(bytes)
            if (environment != Environment.DEV) return null
            return MediaKeys(MessageDigest.getInstance("SHA-256").digest("media:$jwtSecret".toByteArray()))
        }

        private fun hmac(key: ByteArray, data: ByteArray): ByteArray =
            Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }.doFinal(data)
    }
}
