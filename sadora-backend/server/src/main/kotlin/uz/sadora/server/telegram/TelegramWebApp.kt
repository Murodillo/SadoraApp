package uz.sadora.server.telegram

import java.net.URLDecoder
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.serialization.json.Json
import uz.sadora.server.core.UnauthorizedException
import uz.sadora.server.core.now

/**
 * Checks the `initData` a Telegram Mini App is opened with
 * (core.telegram.org/bots/webapps#validating-data-received-via-the-mini-app):
 * the fields but `hash`, sorted and joined by newlines, signed with HMAC-SHA256 under a key
 * that is itself HMAC-SHA256("WebAppData", bot token). Only Telegram and this server know it.
 */
class TelegramWebAppVerifier(
    private val token: String,
    private val maxAge: Duration = 1.days,
    private val clock: () -> Instant = { now() },
) {
    fun verify(initData: String): TgUser {
        val fields = initData.split('&').filter { it.isNotEmpty() }.associate { pair ->
            val key = pair.substringBefore('=')
            decode(key) to decode(pair.substringAfter('=', ""))
        }
        val hash = fields["hash"] ?: refuse()
        val checkString = fields.filterKeys { it != "hash" }.toSortedMap().entries.joinToString("\n") { "${it.key}=${it.value}" }
        val secret = hmac("WebAppData".toByteArray(), token.toByteArray())
        val expected = hmac(secret, checkString.toByteArray()).joinToString("") { "%02x".format(it) }
        if (!MessageDigest.isEqual(expected.toByteArray(), hash.lowercase().toByteArray())) refuse()

        val signedAt = fields["auth_date"]?.toLongOrNull()?.let { Instant.fromEpochSeconds(it) } ?: refuse()
        if (clock() - signedAt > maxAge) refuse()

        val user = fields["user"]?.let { runCatching { JSON.decodeFromString(TgUser.serializer(), it) }.getOrNull() }
        return user ?: refuse()
    }

    private fun refuse(): Nothing = throw UnauthorizedException(message = REFUSED)

    companion object {
        const val REFUSED = "Telegram ma'lumoti tasdiqlanmadi. Mini ilovani botdan qayta oching."
        private val JSON = Json { ignoreUnknownKeys = true }

        private fun decode(value: String) = URLDecoder.decode(value, Charsets.UTF_8)

        fun hmac(key: ByteArray, data: ByteArray): ByteArray =
            Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }.doFinal(data)
    }
}
