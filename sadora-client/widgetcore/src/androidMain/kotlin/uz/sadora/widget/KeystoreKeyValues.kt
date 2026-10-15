package uz.sadora.widget

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * A private preferences file whose values are sealed with an AES-GCM key that never leaves
 * the Keystore — the same treatment the app gives its session snapshot, because the
 * widget snapshot names her stage and her medicines and the companion tokens are a key to
 * her account. The widgets run in the app's own process, so this file is the shared place.
 *
 * Reads and writes are small and synchronous: a widget's update and a tap's receiver run
 * off the main thread already.
 */
class KeystoreKeyValues(context: Context, name: String = FILE) : KeyValues {
    private val prefs = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)

    override fun get(key: String): String? {
        val stored = prefs.getString(key, null) ?: return null
        // A key lost to a data wipe or a restore leaves nothing to recover: drop the value.
        return runCatching { decrypt(stored) }.getOrElse {
            prefs.edit().remove(key).apply()
            null
        }
    }

    override fun put(key: String, value: String?) {
        if (value == null) {
            prefs.edit().remove(key).apply()
            return
        }
        runCatching { prefs.edit().putString(key, encrypt(value)).apply() }
            .onFailure { prefs.edit().remove(key).apply() }
    }

    override fun keys(): Set<String> = prefs.all.keys

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
        }.generateKey()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        val sealed = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return "${cipher.iv.encode()}:${sealed.encode()}"
    }

    private fun decrypt(stored: String): String {
        val (iv, sealed) = stored.split(':').also { require(it.size == 2) }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv.decode()))
        }
        return String(cipher.doFinal(sealed.decode()), Charsets.UTF_8)
    }

    private fun ByteArray.encode(): String = Base64.encodeToString(this, Base64.NO_WRAP)
    private fun String.decode(): ByteArray = Base64.decode(this, Base64.NO_WRAP)

    companion object {
        const val FILE = "sadora.widgets"
        private const val KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "sadora.widgets"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
