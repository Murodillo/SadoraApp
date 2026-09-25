package uz.sadora.server.wearable

import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid
import org.slf4j.LoggerFactory
import uz.sadora.contract.ConnectStart
import uz.sadora.contract.ConnectionStatus
import uz.sadora.contract.FeatureKeys
import uz.sadora.contract.HealthMetric
import uz.sadora.contract.HealthProvider
import uz.sadora.contract.HealthSampleInput
import uz.sadora.contract.IngestSamplesRequest
import uz.sadora.contract.ProviderInfo
import uz.sadora.contract.ProviderKind
import uz.sadora.contract.ProviderUnavailable
import uz.sadora.contract.SyncResult
import uz.sadora.contract.WearableConnection
import uz.sadora.server.audit.ActorType
import uz.sadora.server.audit.AuditActions
import uz.sadora.server.audit.AuditEntry
import uz.sadora.server.audit.AuditService
import uz.sadora.server.config.WhoopConfig
import uz.sadora.server.core.NotFoundException
import uz.sadora.server.core.TokenCipher
import uz.sadora.server.core.ValidationException
import uz.sadora.server.core.now
import uz.sadora.server.core.randomToken
import uz.sadora.server.health.HealthAccess
import uz.sadora.server.wearable.oura.OuraClient
import uz.sadora.server.wearable.whoop.WhoopClient

/**
 * Connecting a cloud wearable, and pulling from it.
 *
 * WHOOP and Oura so far. A provider is an OAuth grant and a [CloudProviderClient] that
 * pages through its collections and maps them to [HealthSampleInput]; Garmin would be
 * the same pieces again, not a new design. Everything provider-specific stops at the
 * client; from there the samples go through the same [WearableService.ingest] the phone
 * uses, under the same consent gate and the same mapping table.
 */
class WearableConnectService(
    private val connections: ConnectionRepository,
    private val wearables: WearableService,
    private val access: HealthAccess,
    private val audit: AuditService,
    private val cipher: TokenCipher,
    private val whoopConfig: WhoopConfig,
    private val whoop: WhoopClient?,
    private val oura: OuraClient? = null,
) {
    /** The clients this server has credentials for. A provider missing here is "not configured". */
    private val clients: Map<HealthProvider, CloudProviderClient> =
        listOfNotNull<CloudProviderClient>(whoop, oura).associateBy { it.provider }

    private val logger = LoggerFactory.getLogger(WearableConnectService::class.java)

    // ---------------------------------------------------------------- the list

    /**
     * Every provider the product knows, whether or not it can be used today.
     *
     * The list is the same length for everyone: a provider the server cannot take yet is
     * marked with a reason rather than left out, so the screen never looks like a
     * feature vanished between two phones.
     */
    suspend fun providers(userId: Uuid): List<ProviderInfo> {
        access.requireUser(userId)
        val connected = connections.listOf(userId).associateBy { it.provider }
        return listOf(
            ProviderInfo(
                provider = HealthProvider.WHOOP,
                kind = ProviderKind.CLOUD,
                available = whoop != null,
                unavailableReason = if (whoop == null) ProviderUnavailable.NOT_CONFIGURED else null,
                metrics = WHOOP_METRICS,
                connection = connected[HealthProvider.WHOOP]?.toDto(),
            ),
            // The phone's own stores are read on the phone, so the server always offers
            // both; the app greys out the one its platform cannot read.
            ProviderInfo(
                provider = HealthProvider.APPLE_HEALTH,
                kind = ProviderKind.ON_DEVICE,
                available = true,
                metrics = PLATFORM_METRICS,
            ),
            ProviderInfo(
                provider = HealthProvider.HEALTH_CONNECT,
                kind = ProviderKind.ON_DEVICE,
                available = true,
                metrics = PLATFORM_METRICS,
            ),
            ProviderInfo(
                provider = HealthProvider.OURA,
                kind = ProviderKind.CLOUD,
                available = oura != null,
                unavailableReason = if (oura == null) ProviderUnavailable.NOT_CONFIGURED else null,
                metrics = RING_METRICS,
                connection = connected[HealthProvider.OURA]?.toDto(),
            ),
            ProviderInfo(HealthProvider.GARMIN, ProviderKind.CLOUD, false, ProviderUnavailable.PLANNED, WATCH_METRICS),
            ProviderInfo(HealthProvider.FITBIT, ProviderKind.CLOUD, false, ProviderUnavailable.PLANNED, WATCH_METRICS),
            ProviderInfo(HealthProvider.SAMSUNG_HEALTH, ProviderKind.ON_DEVICE, false, ProviderUnavailable.PLANNED, PLATFORM_METRICS),
        )
    }

    suspend fun connections(userId: Uuid): List<WearableConnection> {
        access.requireUser(userId)
        return connections.listOf(userId).map { it.toDto() }
    }

    // ---------------------------------------------------------------- oauth

    suspend fun startConnect(userId: Uuid, provider: HealthProvider): ConnectStart {
        // Writing samples later needs the storage consent; refusing here is kinder than
        // completing an OAuth dance whose first sync would be refused.
        access.requireWritable(userId, FeatureKeys.WEARABLE_SYNC)
        val client = clientFor(provider)
        val state = randomToken(STATE_BYTES)
        connections.saveState(state, userId, provider, now() + STATE_TTL)
        return ConnectStart(provider, client.authorizeUrl(state))
    }

    /**
     * The app has come back with the provider's code. [caller] is the account that sent
     * it; the state must have been issued to that same account, or nothing is saved.
     *
     * The exchange used to run in the browser callback, trusting the state alone — so a
     * consent link started by one account and approved in someone else's browser put
     * the second person's WHOOP data into the first person's account. The state is spent
     * either way, so a forwarded link cannot be tried twice.
     */
    suspend fun completeConnect(provider: HealthProvider, state: String, code: String, caller: Uuid): Uuid {
        val client = clientFor(provider)
        val userId = connections.consumeState(state, provider)
            ?: throw ValidationException("state", "Noma'lum yoki eskirgan so'rov")
        if (userId != caller) {
            throw ValidationException("state", "Bu ulanish boshqa hisob uchun boshlangan")
        }
        val tokens = try {
            client.exchange(code)
        } catch (e: ProviderUnauthorizedException) {
            throw ValidationException("code", "Ruxsat kodi qabul qilinmadi")
        }
        val externalUserId = runCatching { client.externalUserId(tokens.accessToken) }.getOrNull()
        connections.save(
            userId,
            provider,
            GrantedTokens(
                accessTokenEnc = cipher.encrypt(tokens.accessToken),
                refreshTokenEnc = tokens.refreshToken?.let(cipher::encrypt),
                expiresAt = now() + tokens.expiresIn.seconds,
                scopes = tokens.scope.split(' ').filter { it.isNotBlank() },
                externalUserId = externalUserId,
            ),
        )
        audit.record(
            AuditEntry(
                actorType = ActorType.USER,
                actorId = userId,
                action = AuditActions.WEARABLE_CONNECTED,
                entityType = "wearable_connection",
                entityId = provider.name.lowercase(),
                metadata = mapOf("scopes" to tokens.scope),
            ),
        )
        return userId
    }

    suspend fun disconnect(userId: Uuid, provider: HealthProvider) {
        val record = connections.find(userId, provider) ?: throw NotFoundException("Ulanish topilmadi")
        clients[provider]?.let { client ->
            runCatching { cipher.decrypt(record.accessTokenEnc) }.getOrNull()?.let { client.revoke(it) }
        }
        connections.delete(userId, provider)
        audit.record(
            AuditEntry(
                actorType = ActorType.USER,
                actorId = userId,
                action = AuditActions.WEARABLE_DISCONNECTED,
                entityType = "wearable_connection",
                entityId = provider.name.lowercase(),
            ),
        )
    }

    // ---------------------------------------------------------------- sync

    /** She tapped "sync now". Throttled: a pull is a handful of API calls against a shared budget. */
    suspend fun syncNow(userId: Uuid, provider: HealthProvider): SyncResult {
        val record = connections.find(userId, provider) ?: throw NotFoundException("Ulanish topilmadi")
        val last = record.lastSyncAt
        if (record.status == ConnectionStatus.ACTIVE && last != null && now() - last < MANUAL_SYNC_COOLDOWN) {
            return SyncResult(provider, accepted = 0, updated = 0)
        }
        return sync(record)
    }

    /**
     * One pull for one connection: refresh the token if it is about to lapse, fetch the
     * window since the last sync, map, ingest. Two days of overlap on every run, because
     * WHOOP rescores a night after the fact and the overwrite is the point.
     */
    suspend fun sync(record: ConnectionRecord): SyncResult {
        val client = clientFor(record.provider)
        val at = now()
        val from = record.lastSyncAt?.minus(RESYNC_OVERLAP) ?: (at - FIRST_SYNC_WINDOW)

        val accessToken = try {
            freshAccessToken(record, client)
        } catch (e: ProviderUnauthorizedException) {
            connections.markFailed(record.userId, record.provider, ConnectionStatus.EXPIRED, "token_expired")
            throw e
        }

        val samples: List<HealthSampleInput> = try {
            client.pull(accessToken, from, at, record.externalUserId)
        } catch (e: ProviderUnauthorizedException) {
            connections.markFailed(record.userId, record.provider, ConnectionStatus.EXPIRED, "token_expired")
            throw e
        } catch (e: Exception) {
            connections.markFailed(record.userId, record.provider, ConnectionStatus.ERROR, "provider_error")
            throw e
        }

        val result = try {
            wearables.ingest(record.userId, IngestSamplesRequest(samples))
        } catch (e: Exception) {
            // Her consent lapsed, or the feature is off for her tier: not the provider's fault.
            connections.markFailed(record.userId, record.provider, ConnectionStatus.ERROR, "not_writable")
            throw e
        }
        if (result.unmapped.isNotEmpty()) {
            logger.warn("{} sync for {} had unmapped metrics: {}", record.provider, record.userId, result.unmapped)
        }
        connections.markSynced(record.userId, record.provider, at)
        return SyncResult(record.provider, result.accepted, result.updated, result.daysAffected)
    }

    /** A webhook named a WHOOP user; find her and pull. Unknown users are ignored, not errors. */
    suspend fun syncByExternalUser(provider: HealthProvider, externalUserId: String) {
        val record = connections.findByExternalUser(provider, externalUserId) ?: return
        runCatching { sync(record) }.onFailure { logger.warn("Webhook sync failed for {}", record.userId, it) }
    }

    private suspend fun freshAccessToken(record: ConnectionRecord, client: CloudProviderClient): String {
        val current = runCatching { cipher.decrypt(record.accessTokenEnc) }.getOrNull()
        if (current != null && record.tokenExpiresAt - now() > REFRESH_MARGIN) return current
        val refreshToken = record.refreshTokenEnc?.let { runCatching { cipher.decrypt(it) }.getOrNull() }
            ?: throw ProviderUnauthorizedException("no refresh token")
        val tokens = client.refresh(refreshToken)
        connections.updateTokens(
            record.userId,
            record.provider,
            GrantedTokens(
                accessTokenEnc = cipher.encrypt(tokens.accessToken),
                // Oura's refresh tokens are single-use and come back new; WHOOP's may not.
                refreshTokenEnc = (tokens.refreshToken ?: refreshToken).let(cipher::encrypt),
                expiresAt = now() + tokens.expiresIn.seconds,
                scopes = tokens.scope.split(' ').filter { it.isNotBlank() },
            ),
        )
        return tokens.accessToken
    }

    private fun clientFor(provider: HealthProvider): CloudProviderClient =
        clients[provider] ?: throw ProviderUnavailableException(provider)

    /** Her row for [provider], for the first pull right after the grant. */
    suspend fun dueForSyncOf(userId: Uuid, provider: HealthProvider): ConnectionRecord? = connections.find(userId, provider)

    /** What the job asks for: which connections to pull, how many at once, across every configured provider. */
    suspend fun dueForSync(limit: Int): List<ConnectionRecord> =
        clients.keys.flatMap { connections.dueForSync(it, now() - SYNC_INTERVAL, limit) }.take(limit)

    suspend fun sweepStates() = connections.sweepStates()

    val whoopWebhookSecret: String? get() = whoopConfig.clientSecret

    companion object {
        private const val STATE_BYTES = 24
        private val STATE_TTL = 15.minutes
        private val FIRST_SYNC_WINDOW = 30.days
        private val RESYNC_OVERLAP = 2.days
        private val REFRESH_MARGIN = 5.minutes
        private val MANUAL_SYNC_COOLDOWN = 3.minutes
        val SYNC_INTERVAL = 30.minutes

        val WHOOP_METRICS = listOf(
            HealthMetric.RECOVERY, HealthMetric.STRAIN, HealthMetric.HRV, HealthMetric.RESTING_HEART_RATE,
            HealthMetric.SKIN_TEMPERATURE, HealthMetric.SPO2, HealthMetric.RESPIRATORY_RATE,
            HealthMetric.SLEEP_DURATION, HealthMetric.SLEEP_DEEP, HealthMetric.SLEEP_REM, HealthMetric.SLEEP_LIGHT,
            HealthMetric.SLEEP_AWAKE, HealthMetric.SLEEP_PERFORMANCE, HealthMetric.SLEEP_EFFICIENCY,
            HealthMetric.ACTIVE_ENERGY, HealthMetric.HEART_RATE, HealthMetric.WEIGHT,
        )
        val PLATFORM_METRICS = listOf(
            HealthMetric.STEPS, HealthMetric.SLEEP_DURATION, HealthMetric.SLEEP_DEEP, HealthMetric.SLEEP_REM,
            HealthMetric.HEART_RATE, HealthMetric.RESTING_HEART_RATE, HealthMetric.HRV, HealthMetric.SKIN_TEMPERATURE,
            HealthMetric.BODY_TEMPERATURE, HealthMetric.SPO2, HealthMetric.RESPIRATORY_RATE,
            HealthMetric.ACTIVE_ENERGY, HealthMetric.DISTANCE, HealthMetric.WEIGHT,
        )
        val RING_METRICS = listOf(
            HealthMetric.RECOVERY, HealthMetric.HRV, HealthMetric.RESTING_HEART_RATE, HealthMetric.SKIN_TEMPERATURE,
            HealthMetric.SLEEP_DURATION, HealthMetric.SLEEP_DEEP, HealthMetric.SLEEP_REM, HealthMetric.STEPS,
        )
        val WATCH_METRICS = listOf(
            HealthMetric.STEPS, HealthMetric.HEART_RATE, HealthMetric.RESTING_HEART_RATE, HealthMetric.HRV,
            HealthMetric.SLEEP_DURATION, HealthMetric.SLEEP_DEEP, HealthMetric.SLEEP_REM, HealthMetric.ACTIVE_ENERGY,
        )
    }
}

/** The provider is not wired, or not configured on this server. Said as a 503, not a 500. */
class ProviderUnavailableException(provider: HealthProvider) : uz.sadora.server.core.ApiException(
    io.ktor.http.HttpStatusCode.ServiceUnavailable,
    "provider_unavailable",
    "${provider.name.lowercase()} hozircha ulanmaydi",
)
