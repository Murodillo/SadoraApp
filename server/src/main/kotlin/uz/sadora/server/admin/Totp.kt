package uz.sadora.server.admin

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.experimental.and
import kotlin.math.abs
import uz.sadora.server.core.now

/**
 * RFC 6238 time-based one-time passwords, for the admin panel's mandatory 2FA.
 *
 * Written out rather than pulled in as a dependency: it is forty lines, it has no moving
 * parts, and an auth primitive is worth being able to read end to end.
 */
object Totp {

    private const val TIME_STEP_SECONDS = 30L
    private const val DIGITS = 6

    /** Accepts the neighbouring windows so a slow phone or a skewed clock still works. */
    private const val ALLOWED_DRIFT_STEPS = 1

    /**
     * A fresh 160-bit secret, base32-encoded — what an authenticator app expects, and
     * the size RFC 4226 asks for.
     */
    fun newSecret(): String {
        val bytes = ByteArray(20)
        java.security.SecureRandom().nextBytes(bytes)
        return encodeBase32(bytes)
    }

    /**
     * The otpauth:// URI an authenticator reads from a QR code.
     *
     * [account] is the operator's email and [issuer] the product name, which is what the
     * app then shows in its list — "SADORA (owner@sadora.uz)" rather than six anonymous
     * digits next to five others.
     */
    fun provisioningUri(secret: String, account: String, issuer: String = "SADORA"): String {
        val label = "${issuer.urlEncoded()}:${account.urlEncoded()}"
        return "otpauth://totp/$label?secret=$secret&issuer=${issuer.urlEncoded()}" +
            "&algorithm=SHA1&digits=$DIGITS&period=$TIME_STEP_SECONDS"
    }

    fun verify(base32Secret: String, code: String): Boolean {
        val trimmed = code.filter { it.isDigit() }
        if (trimmed.length != DIGITS) return false
        val key = runCatching { decodeBase32(base32Secret) }.getOrNull() ?: return false
        val counter = now().epochSeconds / TIME_STEP_SECONDS
        return (-ALLOWED_DRIFT_STEPS..ALLOWED_DRIFT_STEPS).any { drift ->
            // Constant-time comparison: a timing oracle on a 6-digit code is worth having.
            generate(key, counter + drift).constantTimeEquals(trimmed)
        }
    }

    fun generate(key: ByteArray, counter: Long): String {
        val message = ByteArray(8)
        var value = counter
        for (index in 7 downTo 0) {
            message[index] = (value and 0xFF).toByte()
            value = value shr 8
        }
        val mac = Mac.getInstance("HmacSHA1").apply {
            init(SecretKeySpec(key, "HmacSHA1"))
        }
        val hash = mac.doFinal(message)
        val offset = (hash[hash.size - 1] and 0x0F).toInt()
        val binary = ((hash[offset].toInt() and 0x7F) shl 24) or
            ((hash[offset + 1].toInt() and 0xFF) shl 16) or
            ((hash[offset + 2].toInt() and 0xFF) shl 8) or
            (hash[offset + 3].toInt() and 0xFF)
        return (binary % 1_000_000).toString().padStart(DIGITS, '0')
    }

    fun encodeBase32(bytes: ByteArray): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val output = StringBuilder()
        var buffer = 0
        var bitsLeft = 0
        bytes.forEach { byte ->
            buffer = (buffer shl 8) or (byte.toInt() and 0xFF)
            bitsLeft += 8
            while (bitsLeft >= 5) {
                output.append(alphabet[(buffer shr (bitsLeft - 5)) and 0x1F])
                bitsLeft -= 5
            }
        }
        if (bitsLeft > 0) output.append(alphabet[(buffer shl (5 - bitsLeft)) and 0x1F])
        return output.toString()
    }

    fun decodeBase32(secret: String): ByteArray {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val cleaned = secret.trim().replace("=", "").replace(" ", "").uppercase()
        var buffer = 0
        var bitsLeft = 0
        val output = ArrayList<Byte>(cleaned.length * 5 / 8)
        cleaned.forEach { character ->
            val index = alphabet.indexOf(character)
            require(index >= 0) { "Base32 emas: $character" }
            buffer = (buffer shl 5) or index
            bitsLeft += 5
            if (bitsLeft >= 8) {
                output.add(((buffer shr (bitsLeft - 8)) and 0xFF).toByte())
                bitsLeft -= 8
            }
        }
        return output.toByteArray()
    }

    private fun String.urlEncoded(): String =
        java.net.URLEncoder.encode(this, Charsets.UTF_8).replace("+", "%20")

    private fun String.constantTimeEquals(other: String): Boolean {
        if (length != other.length) return false
        var difference = 0
        for (index in indices) difference = difference or (this[index].code xor other[index].code)
        return difference == 0
    }
}
