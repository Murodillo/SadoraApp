package uz.sadora.app.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Image bytes in memory, by key, the least recently used out first.
 *
 * Chat photos and profile photos both use one: an authenticated image is fetched through
 * the API rather than by a URL an image library could cache, so this is the cache. Never
 * written to disk — a private photo in a cache directory is one backup away from
 * somewhere she never sent it — and dropped with the controller that owns it, which is
 * dropped with the session.
 */
class ImageBytesCache(private val capacity: Int) {
    private val entries = LinkedHashMap<String, ByteArray>()
    private val lock = Mutex()

    suspend fun get(key: String): ByteArray? = lock.withLock {
        // Taken out and put back, so a face on screen is the last one to be dropped.
        entries.remove(key)?.also { entries[key] = it }
    }

    suspend fun put(key: String, bytes: ByteArray) = lock.withLock {
        entries.remove(key)
        entries[key] = bytes
        while (entries.size > capacity) entries.remove(entries.keys.first())
    }

    /**
     * The cached bytes, or [fetch]'s, remembered. A null from [fetch] is not remembered:
     * the next ask tries again.
     */
    suspend fun getOrFetch(key: String, fetch: suspend () -> ByteArray?): ByteArray? {
        get(key)?.let { return it }
        return fetch()?.also { put(key, it) }
    }
}
