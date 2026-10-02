package uz.sadora.doctor.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import uz.sadora.contract.CommunityComment
import uz.sadora.contract.CommunityPost
import uz.sadora.contract.DoctorAccount
import uz.sadora.contract.DoctorStatus

// Profile photos: hers, her patients', and the other doctors' in the room.
//
// Every photoUrl the server sends is a path under the API with a version on it
// (`/v1/doctors/<id>/photo?v=…`); a new photo is a new URL, so a URL is safe to cache
// for as long as the app runs. The image routes want the usual bearer token.

/**
 * The address a photo is fetched from, or null when there is nothing to fetch.
 *
 * A path is put under [baseUrl]. A whole URL is accepted only on [baseUrl]'s own
 * origin: the request carries her token, and a URL naming another host — a server bug,
 * a tampered response — must not be where that token goes.
 */
fun photoRequestUrl(baseUrl: String, photoUrl: String?): String? {
    val raw = photoUrl?.trim().orEmpty()
    if (raw.isEmpty()) return null
    val base = baseUrl.trim().trimEnd('/')
    if (raw.startsWith("//")) return null
    if (raw.contains("://")) {
        val origin = originOf(base) ?: return null
        return raw.takeIf { originOf(it) == origin }
    }
    return "$base/${raw.trimStart('/')}"
}

/** `scheme://host[:port]`, lower-cased; null when [url] has no scheme. */
private fun originOf(url: String): String? {
    val scheme = url.substringBefore("://", missingDelimiterValue = "").takeIf { it.isNotEmpty() } ?: return null
    val rest = url.substringAfter("://")
    val authority = rest.substringBefore('/').substringBefore('?').substringBefore('#')
    if (authority.isEmpty() || '@' in authority) return null
    return "${scheme.lowercase()}://${authority.lowercase()}"
}

/**
 * The most recently used [maxEntries] values by key. A feed of fifty posts from five
 * doctors is five entries; the cap is for a long session, not a normal one.
 */
class PhotoCache<V : Any>(private val maxEntries: Int = 64) {
    init {
        require(maxEntries > 0) { "maxEntries must be positive" }
    }

    // Insertion-ordered; a read moves the key to the end, so the first key is the oldest.
    private val entries = LinkedHashMap<String, V>()

    val size: Int get() = entries.size

    operator fun get(key: String): V? {
        val value = entries.remove(key) ?: return null
        entries[key] = value
        return value
    }

    operator fun set(key: String, value: V) {
        entries.remove(key)
        entries[key] = value
        while (entries.size > maxEntries) entries.remove(entries.keys.first())
    }

    operator fun contains(key: String): Boolean = key in entries

    fun clear() = entries.clear()
}

/**
 * Photos by URL, fetched once and decoded once.
 *
 * Twenty rows by the same doctor ask for the same URL at the same moment; the first
 * starts the fetch and the rest wait for it. A failure is not remembered, so the next
 * screen to show the photo tries again — a dropped connection is not a missing photo.
 */
class RemoteImages<T : Any>(
    private val fetch: suspend (url: String) -> ApiResult<ByteArray>,
    private val decode: (ByteArray) -> T?,
    maxEntries: Int = 64,
    private val decodeOn: CoroutineDispatcher = Dispatchers.Default,
) {
    private val cache = PhotoCache<T>(maxEntries)
    private val inFlight = mutableMapOf<String, CompletableDeferred<T?>>()
    private val lock = Mutex()

    /** What is already in memory — for the first frame, before anything is fetched. */
    fun cached(url: String): T? = cache[url]

    suspend fun load(url: String): T? {
        val (waiting, mine) = lock.withLock {
            cache[url]?.let { return it }
            val existing = inFlight[url]
            if (existing != null) {
                existing to false
            } else {
                CompletableDeferred<T?>().also { inFlight[url] = it } to true
            }
        }
        if (!mine) return waiting.await()

        val value = try {
            when (val result = fetch(url)) {
                is ApiResult.Success -> withContext(decodeOn) { runCatching { decode(result.value) }.getOrNull() }
                is ApiResult.Failure -> null
            }
        } catch (failure: Throwable) {
            lock.withLock { inFlight.remove(url) }
            waiting.complete(null)
            throw failure
        }
        lock.withLock {
            if (value != null) cache[url] = value
            inFlight.remove(url)
        }
        waiting.complete(value)
        return value
    }

    /** Signed out: the next account's photos are not this one's to see. */
    suspend fun clear() = lock.withLock { cache.clear() }
}

/**
 * The one-time sheet after sign-in: an approved doctor, with no photo, who has not been
 * asked yet in this launch of the app. The Home card keeps asking; the sheet does not.
 */
fun photoNudgeDue(account: DoctorAccount?, alreadyAsked: Boolean): Boolean =
    !alreadyAsked && account?.status == DoctorStatus.APPROVED && account.photoUrl == null

/** Whether the application form should ask for a photo once it has been sent. */
fun photoStepAfterApply(account: DoctorAccount?): Boolean =
    account != null && account.status != DoctorStatus.NONE && account.photoUrl == null

/** Her posts, with her byline carrying [photoUrl]; everyone else's as they were. */
fun List<CommunityPost>.withDoctorPhoto(doctorId: String, photoUrl: String?): List<CommunityPost> = map { post ->
    val doctor = post.doctor
    if (doctor?.id == doctorId && doctor.photoUrl != photoUrl) post.copy(doctor = doctor.copy(photoUrl = photoUrl)) else post
}

/** Her answers, likewise. */
fun List<CommunityComment>.withDoctorCommentPhoto(doctorId: String, photoUrl: String?): List<CommunityComment> = map { comment ->
    val doctor = comment.doctor
    if (doctor?.id == doctorId && doctor.photoUrl != photoUrl) comment.copy(doctor = doctor.copy(photoUrl = photoUrl)) else comment
}
