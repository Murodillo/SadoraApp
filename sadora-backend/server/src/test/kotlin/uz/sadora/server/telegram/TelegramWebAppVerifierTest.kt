package uz.sadora.server.telegram

import java.net.URLEncoder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import uz.sadora.server.core.UnauthorizedException

class TelegramWebAppVerifierTest {
    private val token = "123456:test-token"
    private val signedAt = Instant.fromEpochSeconds(1_800_000_000)
    private val verifier = TelegramWebAppVerifier(token, clock = { signedAt + 1.hours })

    @Test
    fun `accepts initData signed with the bot's token and reads the user`() {
        val user = verifier.verify(signed(token))
        assertEquals(42L, user.id)
        assertEquals("malika", user.username)
        assertEquals("uz", user.languageCode)
    }

    @Test
    fun `refuses another bot's signature — a changed field — an old one — a missing user`() {
        assertFailsWith<UnauthorizedException> { verifier.verify(signed("999:other")) }
        assertFailsWith<UnauthorizedException> { verifier.verify(signed(token).replace("%22id%22%3A42", "%22id%22%3A43")) }
        assertFailsWith<UnauthorizedException> {
            TelegramWebAppVerifier(token, clock = { signedAt + 2.days }).verify(signed(token))
        }
        assertFailsWith<UnauthorizedException> { verifier.verify(signed(token, user = null)) }
        assertFailsWith<UnauthorizedException> { verifier.verify("") }
    }

    companion object {
        /** initData the way Telegram builds it, for tests here and in the API test. */
        fun signed(
            token: String,
            user: String? = """{"id":42,"first_name":"Malika","username":"malika","language_code":"uz"}""",
            authDate: Long = 1_800_000_000,
        ): String {
            val fields = buildMap {
                put("auth_date", authDate.toString())
                put("query_id", "AAH")
                user?.let { put("user", it) }
            }
            val check = fields.toSortedMap().entries.joinToString("\n") { "${it.key}=${it.value}" }
            val secret = TelegramWebAppVerifier.hmac("WebAppData".toByteArray(), token.toByteArray())
            val hash = TelegramWebAppVerifier.hmac(secret, check.toByteArray()).joinToString("") { "%02x".format(it) }
            return (fields + ("hash" to hash)).entries.joinToString("&") { "${it.key}=${URLEncoder.encode(it.value, Charsets.UTF_8).replace("+", "%20")}" }
        }
    }
}
