package uz.sadora.server.wearable

import kotlin.time.Duration
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
import uz.sadora.server.wearable.fitbit.FitbitClient
import uz.sadora.server.wearable.garmin.GarminClient
import uz.sadora.server.wearable.huawei.HuaweiClient
import uz.sadora.server.wearable.huawei.HuaweiNotification
import uz.sadora.server.wearable.oura.OuraClient
import uz.sadora.server.wearable.whoop.WhoopClient

/**
 * Connecting a cloud wearable, and pulling from it.
 *
 * WHOOP, Oura, Garmin, Fitbit and Huawei so far. A provider is an OAuth grant and a [CloudProviderClient]
 * that pages through its collections and maps them to [HealthSampleInput]. Garmin differs
 * in one thing: it sends the data itself, so most of hers arrives through [ingestPushed]
 * and the pull is only the floor under it. Everything provider-specific stops at the
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
    private val garmin: GarminClient? = null,
    private val garminWebhookToken: String? = null,
    private val fitbit: FitbitClient? = null,
    private val fitbitWebhookSecret: String? = null,
    private val huawei: HuaweiClient? = null,
    private val huaweiSubscriberSecret: String? = null,
) {
    /** The clients this server has credentials for. A provider missing here is "not configured". */
    private val clients: Map<HealthProvider, CloudProviderClient> =
        listOfNotNull<CloudProviderClient>(whoop, oura, garmin, fitbit, huawei).associateBy { it.provider }

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
            ProviderInfo(
                provider = HealthProvider.GARMIN,
                kind = ProviderKind.CLOUD,
                available = garmin != null,
                unavailableReason = if (garmin == null) ProviderUnavailable.NOT_CONFIGURED else null,
                metrics = GARMIN_METRICS,
                connection = connected[HealthProvider.GARMIN]?.toDto(),
            ),
            ProviderInfo(
                provider = HealthProvider.FITBIT,
                kind = ProviderKind.CLOUD,
                available = fitbit != null,
                unavailableReason = if (fitbit == null) ProviderUnavailable.NOT_CONFIGURED else null,
                metrics = FITBIT_METRICS,
                connection = connected[HealthProvider.FITBIT]?.toDto(),
            ),
            ProviderInfo(
                provider = HealthProvider.HUAWEI,
                kind = ProviderKind.CLOUD,
                available = huawei != null,
                unavailableReason = if (huawei == null) ProviderUnavailable.NOT_CONFIGURED else null,
                metrics = HUAWEI_METRICS,
                connection = connected[HealthProvider.HUAWEI]?.toDto(),
            ),
            // Samsung Health writes into Health Connect, and that is how the app reads it;
            // the app shows it on Android phones only.
            ProviderInfo(
                provider = HealthProvider.SAMSUNG_HEALTH,
                kind = ProviderKind.ON_DEVICE,
                available = true,
                metrics = PLATFORM_METRICS,
            ),
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
            client.exchange(code, state)
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
     * window since the last sync, map, ingest. Every run overlaps the last by the
     * client's [CloudProviderClient.resyncOverlap].
     */
    suspend fun sync(record: ConnectionRecord): SyncResult {
        val client = clientFor(record.provider)
        val at = now()
        val from = record.lastSyncAt?.minus(client.resyncOverlap) ?: (at - FIRST_SYNC_WINDOW)

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

    /**
     * A webhook named a provider's user; find her and pull. Unknown users are ignored, not
     * errors. [minGap] is for a provider that announces every data type separately: a
     * pull that has only just run is not run again, and the scheduled one picks up the rest.
     */
    suspend fun syncByExternalUser(provider: HealthProvider, externalUserId: String, minGap: Duration = Duration.ZERO) {
        val record = connections.findByExternalUser(provider, externalUserId) ?: return
        if (record.lastSyncAt?.let { now() - it < minGap } == true) return
        runCatching { sync(record) }.onFailure { logger.warn("Webhook sync failed for {}", record.userId, it) }
    }

    /**
     * Garmin sent her data rather than waiting to be asked. Unknown users are ignored:
     * a push can outlive the connection it was for.
     */
    suspend fun ingestPushed(provider: HealthProvider, externalUserId: String, samples: List<HealthSampleInput>) {
        val record = connections.findByExternalUser(provider, externalUserId) ?: return
        if (samples.isEmpty()) return
        runCatching {
            samples.chunked(PUSH_BATCH).forEach { wearables.ingest(record.userId, IngestSamplesRequest(it)) }
            connections.markSynced(record.userId, provider, now())
        }.onFailure { logger.warn("Pushed {} samples for {} were not taken: {}", provider, record.userId, it.message) }
    }

    /**
     * She withdrew the consent on the provider's side. The row stays, marked as needing
     * a reconnect, so the screen says what happened instead of the device vanishing.
     */
    suspend fun revokedByExternalUser(provider: HealthProvider, externalUserId: String) {
        val record = connections.findByExternalUser(provider, externalUserId) ?: return
        connections.markFailed(record.userId, provider, ConnectionStatus.EXPIRED, "revoked")
    }

    /** Whether [token] is the secret path segment Garmin's webhooks were registered with. */
    fun isGarminWebhookToken(token: String): Boolean? {
        val expected = garminWebhookToken ?: return null
        return java.security.MessageDigest.isEqual(expected.toByteArray(), token.toByteArray())
    }

    /** Whether [authorization] is the header value Google Health was told to send. Null when none is set. */
    fun isFitbitWebhookSecret(authorization: String?): Boolean? {
        val expected = fitbitWebhookSecret ?: return null
        return java.security.MessageDigest.isEqual(expected.toByteArray(), authorization.orEmpty().toByteArray())
    }

    /**
     * Whether [signature] is Huawei's over [notification], made with the subscriber's secret.
     * Null when no subscriber is registered here.
     */
    fun isHuaweiSignature(notification: HuaweiNotification, signature: String?): Boolean? {
        val secret = huaweiSubscriberSecret ?: return null
        return notification.signedWith(secret, signature.orEmpty())
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
                // Oura's and Garmin's refresh tokens are single-use and come back new; WHOOP's
                // may not, and Google's and Huawei's never do: the first one stays good.
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
        private val REFRESH_MARGIN = 5.minutes
        private val MANUAL_SYNC_COOLDOWN = 3.minutes
        val SYNC_INTERVAL = 30.minutes
        /** Under the ingest's own cap; a backfilled month can arrive in one post. */
        private const val PUSH_BATCH = 1_000

        // Each list is what that provider's mapper actually produces, in the order the
        // card names them — the card says what connecting it is for, so a metric the
        // mapper never takes must not be promised there.
        val WHOOP_METRICS = listOf(
            HealthMetric.RECOVERY, HealthMetric.STRAIN, HealthMetric.HRV, HealthMetric.RESTING_HEART_RATE,
            HealthMetric.SKIN_TEMPERATURE, HealthMetric.SPO2, HealthMetric.RESPIRATORY_RATE,
            HealthMetric.SLEEP_DURATION, HealthMetric.SLEEP_DEEP, HealthMetric.SLEEP_REM, HealthMetric.SLEEP_LIGHT,
            HealthMetric.SLEEP_AWAKE, HealthMetric.SLEEP_PERFORMANCE, HealthMetric.SLEEP_EFFICIENCY,
            HealthMetric.ACTIVE_ENERGY, HealthMetric.HEART_RATE, HealthMetric.WEIGHT,
        )
        /** What both phone readers take today; the rest of the mapping table is not read. */
        val PLATFORM_METRICS = listOf(
            HealthMetric.STEPS, HealthMetric.SLEEP_DURATION, HealthMetric.SLEEP_DEEP, HealthMetric.SLEEP_REM,
            HealthMetric.SLEEP_LIGHT, HealthMetric.SLEEP_AWAKE,
            HealthMetric.HEART_RATE, HealthMetric.RESTING_HEART_RATE, HealthMetric.HRV, HealthMetric.SKIN_TEMPERATURE,
            HealthMetric.BODY_TEMPERATURE, HealthMetric.SPO2, HealthMetric.RESPIRATORY_RATE,
            HealthMetric.ACTIVE_ENERGY, HealthMetric.DISTANCE, HealthMetric.WEIGHT,
        )
        /** Oura's temperature is a deviation from her baseline, so it is not among these. */
        val RING_METRICS = listOf(
            HealthMetric.RECOVERY, HealthMetric.HRV, HealthMetric.RESTING_HEART_RATE, HealthMetric.SPO2,
            HealthMetric.RESPIRATORY_RATE, HealthMetric.SLEEP_DURATION, HealthMetric.SLEEP_DEEP, HealthMetric.SLEEP_REM,
            HealthMetric.SLEEP_LIGHT, HealthMetric.SLEEP_AWAKE, HealthMetric.SLEEP_EFFICIENCY,
            HealthMetric.STEPS, HealthMetric.ACTIVE_ENERGY, HealthMetric.DISTANCE, HealthMetric.WEIGHT,
        )
        val GARMIN_METRICS = listOf(
            HealthMetric.STEPS, HealthMetric.DISTANCE, HealthMetric.ACTIVE_ENERGY, HealthMetric.FLOORS,
            HealthMetric.HEART_RATE, HealthMetric.RESTING_HEART_RATE, HealthMetric.HRV,
            HealthMetric.SLEEP_DURATION, HealthMetric.SLEEP_DEEP, HealthMetric.SLEEP_REM, HealthMetric.SLEEP_LIGHT,
            HealthMetric.SLEEP_AWAKE, HealthMetric.SLEEP_PERFORMANCE, HealthMetric.SPO2, HealthMetric.RESPIRATORY_RATE,
            HealthMetric.WEIGHT, HealthMetric.BODY_FAT, HealthMetric.VO2_MAX,
        )
        val FITBIT_METRICS = listOf(
            HealthMetric.STEPS, HealthMetric.DISTANCE, HealthMetric.ACTIVE_ENERGY, HealthMetric.FLOORS,
            HealthMetric.HEART_RATE, HealthMetric.RESTING_HEART_RATE, HealthMetric.HRV,
            HealthMetric.SLEEP_DURATION, HealthMetric.SLEEP_DEEP, HealthMetric.SLEEP_REM, HealthMetric.SLEEP_LIGHT,
            HealthMetric.SLEEP_AWAKE, HealthMetric.SPO2, HealthMetric.RESPIRATORY_RATE,
            HealthMetric.WEIGHT, HealthMetric.BODY_FAT, HealthMetric.VO2_MAX,
        )
        val HUAWEI_METRICS = listOf(
            HealthMetric.STEPS, HealthMetric.DISTANCE, HealthMetric.ACTIVE_ENERGY,
            HealthMetric.HEART_RATE, HealthMetric.RESTING_HEART_RATE, HealthMetric.HRV,
            HealthMetric.SLEEP_DURATION, HealthMetric.SLEEP_DEEP, HealthMetric.SLEEP_REM, HealthMetric.SLEEP_LIGHT,
            HealthMetric.SLEEP_AWAKE, HealthMetric.SLEEP_PERFORMANCE, HealthMetric.SPO2,
            HealthMetric.SKIN_TEMPERATURE, HealthMetric.BODY_TEMPERATURE,
            HealthMetric.WEIGHT, HealthMetric.BODY_FAT, HealthMetric.VO2_MAX,
        )
    }
}

/** The provider is not wired, or not configured on this server. Said as a 503, not a 500. */
class ProviderUnavailableException(provider: HealthProvider) : uz.sadora.server.core.ApiException(
    io.ktor.http.HttpStatusCode.ServiceUnavailable,
    "provider_unavailable",
    "${provider.name.lowercase()} hozircha ulanmaydi",
)
