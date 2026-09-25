package uz.sadora.app.push

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import uz.sadora.app.data.SadoraRepository

/**
 * Hands this phone's FCM token to the backend for whoever is signed in.
 *
 * The server keeps the token on the device row, so sending the same one again on every
 * start is an update, not a duplicate — and it is what carries a token that rotated
 * while the app was closed. A failed call is not retried here; the next start sends it.
 */
class PushRegistration(
    private val context: Context,
    private val repository: SadoraRepository,
) {

    suspend fun register() {
        val token = currentToken() ?: return
        repository.registerPushToken(token)
    }

    private suspend fun currentToken(): String? {
        // A build without google-services.json has no default Firebase app, and
        // FirebaseMessaging.getInstance() would throw rather than return nothing.
        if (FirebaseApp.getApps(context).isEmpty()) return null
        return suspendCancellableCoroutine { continuation ->
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token -> continuation.resume(token) }
                // No Play services (a Huawei phone), or no network yet.
                .addOnFailureListener { continuation.resume(null) }
        }
    }
}
