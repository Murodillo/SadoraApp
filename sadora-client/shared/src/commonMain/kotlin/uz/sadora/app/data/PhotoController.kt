package uz.sadora.app.data

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import uz.sadora.app.data.api.PhotoApi
import uz.sadora.app.data.api.photoRequestPath
import uz.sadora.app.model.AppState
import uz.sadora.contract.PhotoUpload

/**
 * Profile photos: every face the app draws from a `photoUrl`, and her own.
 *
 * The bytes are kept in memory by URL. The server puts a version on every URL and
 * changes it with every new photo, so a URL never means two pictures and there is
 * nothing to invalidate. Built per session like the other controllers, so the next
 * account on the phone starts with none of this one's faces.
 *
 * Hers is private. It is drawn on her profile and her home header, and a doctor she
 * consults sees it through the consultation; nothing here puts it anywhere in the Chat.
 */
class PhotoController(
    private val api: PhotoApi?,
    private val state: AppState,
) {
    private val calls = ApiCallState()

    /** True while her photo is going up or coming down. Loading a face never sets it. */
    val saving: Boolean get() = calls.busy
    val error: ApiFailure? get() = calls.error
    val isOffline: Boolean get() = api == null

    fun clearError() = calls.clearError()

    private val cache = ImageBytesCache(PhotoCacheSize)

    /**
     * The bytes behind a `photoUrl`, or null when there is no photo or it would not
     * come: the caller then draws initials. A failure is not remembered — the next time
     * the face is drawn it is asked for again.
     */
    suspend fun bytes(photoUrl: String?): ByteArray? {
        val path = photoRequestPath(photoUrl) ?: return null
        val api = api ?: return cache.get(path)
        return cache.getOrFetch(path) { api.image(path).valueOrNull }
    }

    /**
     * Sends her photo, already upright and compressed by the picker. On success the new
     * URL is hers at once, and the bytes she chose are what it draws — the server's copy
     * is the same picture cropped to its centre, which is what a round avatar shows anyway.
     * A picture the server will not take leaves its reason in [error].
     */
    suspend fun setMine(imageBase64: String, mimeType: String): Boolean {
        val api = api ?: return false
        val saved = calls.run { api.setMine(PhotoUpload(imageBase64, mimeType)) } ?: return false
        photoRequestPath(saved.photoUrl)?.let { path -> decodeBase64(imageBase64)?.let { cache.put(path, it) } }
        state.avatarUrl = saved.photoUrl
        return true
    }

    suspend fun removeMine(): Boolean {
        val api = api ?: return false
        calls.run { api.removeMine() } ?: return false
        state.avatarUrl = null
        return true
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun decodeBase64(value: String): ByteArray? = runCatching { Base64.decode(value) }.getOrNull()

    companion object {
        /** A face is 512 px at most, tens of kilobytes; a directory page is a few dozen. */
        const val PhotoCacheSize = 60
    }
}
