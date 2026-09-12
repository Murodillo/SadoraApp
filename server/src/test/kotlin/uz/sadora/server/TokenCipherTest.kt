package uz.sadora.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import uz.sadora.server.core.TokenCipher
import uz.sadora.server.wearable.WebhookSignature

class TokenCipherTest {

    private val cipher = TokenCipher.from(configuredKey = null, fallbackSecret = "integration-test-secret-0123456789abcdef")

    @Test
    fun `a token comes back as it went in, and never twice the same on the wire`() {
        val token = "eyJhbGciOi.refresh.token"
        val first = cipher.encrypt(token)
        val second = cipher.encrypt(token)
        assertNotEquals(first, second, "a fresh nonce every time")
        assertEquals(token, cipher.decrypt(first))
        assertEquals(token, cipher.decrypt(second))
    }

    @Test
    fun `another key refuses rather than returning garbage`() {
        val other = TokenCipher.from(configuredKey = null, fallbackSecret = "a-different-secret-entirely")
        val sealed = cipher.encrypt("secret")
        assertFailsWith<Exception> { other.decrypt(sealed) }
    }

    @Test
    fun `a configured base64 key of the right size is used as is`() {
        val key = java.util.Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() })
        val a = TokenCipher.from(key, "ignored")
        val b = TokenCipher.from(key, "also ignored")
        assertEquals("x", b.decrypt(a.encrypt("x")))
    }

    @Test
    fun `the webhook signature is the documented recipe and compares exactly`() {
        val secret = "client-secret"
        val body = """{"user_id":1,"id":"abc","type":"sleep.updated"}"""
        val signature = WebhookSignature.sign(secret, "1725400000000", body)
        assertEquals(true, WebhookSignature.matches(secret, "1725400000000", body, signature))
        assertEquals(false, WebhookSignature.matches(secret, "1725400000001", body, signature))
        assertEquals(false, WebhookSignature.matches("other", "1725400000000", body, signature))
        assertEquals(false, WebhookSignature.matches(secret, "", body, ""))
    }
}
