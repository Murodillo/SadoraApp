package uz.sadora.server.cache

import io.lettuce.core.RedisClient
import io.lettuce.core.SetArgs
import io.lettuce.core.api.StatefulRedisConnection
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import uz.sadora.server.config.RedisConfig
import uz.sadora.server.core.now

/**
 * The cache is a performance and rate-limiting aid, never a source of truth: a wiped
 * Redis costs a few extra queries and resets some counters, and nothing else. That is
 * why the in-memory fallback below is an acceptable substitute in dev.
 */
interface Cache : AutoCloseable {
    suspend fun get(key: String): String?
    suspend fun set(key: String, value: String, ttl: Duration)
    suspend fun delete(key: String)
    /** Atomic increment with a TTL applied on first write. Returns the new count. */
    suspend fun increment(key: String, ttl: Duration): Long
}

object Caches {
    private val logger = LoggerFactory.getLogger(Caches::class.java)

    fun create(config: RedisConfig): Cache {
        val url = config.url
        if (url == null) {
            logger.warn("REDIS_URL is not set — using the in-memory cache. Single node only.")
            return InMemoryCache()
        }
        return runCatching { RedisCache(url) }.getOrElse { failure ->
            logger.error("Redis at {} is unreachable, falling back to in-memory cache", url, failure)
            InMemoryCache()
        }
    }
}

class RedisCache(url: String) : Cache {
    private val client: RedisClient = RedisClient.create(url)
    private val connection: StatefulRedisConnection<String, String> = client.connect()
    private val commands = connection.sync()

    override suspend fun get(key: String): String? =
        withContext(Dispatchers.IO) { commands.get(key) }

    override suspend fun set(key: String, value: String, ttl: Duration) {
        withContext(Dispatchers.IO) {
            commands.set(key, value, SetArgs.Builder.px(ttl.inWholeMilliseconds))
        }
    }

    override suspend fun delete(key: String) {
        withContext(Dispatchers.IO) { commands.del(key) }
    }

    override suspend fun increment(key: String, ttl: Duration): Long =
        withContext(Dispatchers.IO) {
            val count = commands.incr(key)
            if (count == 1L) commands.pexpire(key, ttl.inWholeMilliseconds)
            count
        }

    override fun close() {
        connection.close()
        client.shutdown()
    }
}

/**
 * Process-local stand-in for Redis. Correct for a single node, which is what dev and the
 * test suite run; with several instances the counters diverge, so production sets
 * `REDIS_URL`.
 */
class InMemoryCache : Cache {
    private data class Entry(val value: String, val expiresAtMillis: Long)

    private val entries = ConcurrentHashMap<String, Entry>()

    private fun live(key: String): Entry? {
        val entry = entries[key] ?: return null
        if (entry.expiresAtMillis <= now().toEpochMilliseconds()) {
            entries.remove(key, entry)
            return null
        }
        return entry
    }

    override suspend fun get(key: String): String? = live(key)?.value

    override suspend fun set(key: String, value: String, ttl: Duration) {
        entries[key] = Entry(value, now().toEpochMilliseconds() + ttl.inWholeMilliseconds)
    }

    override suspend fun delete(key: String) {
        entries.remove(key)
    }

    override suspend fun increment(key: String, ttl: Duration): Long {
        val expiry = live(key)?.expiresAtMillis
            ?: (now().toEpochMilliseconds() + ttl.inWholeMilliseconds)
        val updated = entries.compute(key) { _, existing ->
            val current = existing?.takeIf { it.expiresAtMillis > now().toEpochMilliseconds() }
            Entry(((current?.value?.toLongOrNull() ?: 0L) + 1).toString(), expiry)
        }
        return updated!!.value.toLong()
    }

    override fun close() = entries.clear()
}
