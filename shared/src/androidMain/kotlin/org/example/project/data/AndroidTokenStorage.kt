package org.example.project.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Refresh token storage backed by the Android Keystore.
 *
 * The token is encrypted with an AES-GCM key that never leaves the Keystore — the
 * preferences file holds only ciphertext, so a rooted-device backup or a `run-as` dump
 * of the app's data directory yields nothing usable. Plain `SharedPreferences` would
 * hand over a thirty-day key to the account.
 *
 * `androidx.security:security-crypto` does the same job, but it is deprecated and this
 * is eighty lines of platform API with no dependency to keep current.
 */
class AndroidTokenStorage(context: Context) : TokenStorage {

    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override suspend fun readRefreshToken(): String? = withContext(Dispatchers.IO) {
        val stored = preferences.getString(KEY_REFRESH_TOKEN, null) ?: return@withContext null
        runCatching { decrypt(stored) }.getOrElse {
            // The key is gone — the user cleared app data, restored a backup onto a new
            // device, or the Keystore was reset. There is nothing to recover, so drop
            // the unreadable value and let the app sign in again.
            preferences.edit().remove(KEY_REFRESH_TOKEN).apply()
            null
        }
    }

    override suspend fun writeRefreshToken(token: String) = withContext(Dispatchers.IO) {
        preferences.edit().putString(KEY_REFRESH_TOKEN, encrypt(token)).apply()
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        preferences.edit().remove(KEY_REFRESH_TOKEN).apply()
    }

    override suspend fun installationId(): String = withContext(Dispatchers.IO) {
        preferences.getString(KEY_INSTALLATION_ID, null)
            ?: java.util.UUID.randomUUID().toString()
                .also { preferences.edit().putString(KEY_INSTALLATION_ID, it).apply() }
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
        }.generateKey()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        // GCM generates a fresh IV per encryption; it is not secret but must be kept.
        return "${cipher.iv.encode()}$SEPARATOR${ciphertext.encode()}"
    }

    private fun decrypt(stored: String): String {
        val (iv, ciphertext) = stored.split(SEPARATOR).also { require(it.size == 2) }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv.decode()))
        }
        return String(cipher.doFinal(ciphertext.decode()), Charsets.UTF_8)
    }

    private fun ByteArray.encode(): String = Base64.encodeToString(this, Base64.NO_WRAP)
    private fun String.decode(): ByteArray = Base64.decode(this, Base64.NO_WRAP)

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "sadora.refresh_token"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
        const val PREFERENCES_NAME = "sadora.secure"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_INSTALLATION_ID = "installation_id"
        const val SEPARATOR = ":"
    }
}
