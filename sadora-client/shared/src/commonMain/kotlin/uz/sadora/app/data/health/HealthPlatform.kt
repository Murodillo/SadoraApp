package uz.sadora.app.data.health

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import uz.sadora.contract.HealthProvider
import uz.sadora.contract.HealthSampleInput

/** Whether the phone's health store can be used at all, before any permission. */
enum class HealthAvailability {
    AVAILABLE,

    /** Android 13 and older without the Health Connect app. Fixed from the Play Store. */
    NOT_INSTALLED,

    /** Health Connect is there but too old for the client library. Also fixed from Play. */
    UPDATE_REQUIRED,

    /** No store on this device: an Android older than 8, an iPad without Health. */
    UNSUPPORTED,
}

/** One read of the phone's store. */
data class HealthReading(
    /** Already in the provider's own metric names; the server's mapping table does the rest. */
    val samples: List<HealthSampleInput>,
    /** Days with bleeding recorded by any app. [PeriodImport] turns them into periods. */
    val flowDays: Set<LocalDate>,
)

/**
 * The phone's own health store: HealthKit on iOS, Health Connect on Android.
 *
 * Both are read on the device and posted to the server as ordinary samples, so nothing
 * past this interface knows which one it was. The permission sheet is not here: it
 * needs the UI (an Activity result on Android), so it is [rememberHealthAccessRequest]
 * in the components package.
 */
interface HealthPlatform {
    /** The provider this phone speaks, or null where there is none. */
    val provider: HealthProvider?

    suspend fun availability(): HealthAvailability

    /**
     * Whether reading can go ahead.
     *
     * Android answers truthfully: at least one of the read permissions is granted. iOS
     * never tells an app whether it may read — a refusal just returns no samples — so
     * there this is true once the sheet has been shown.
     */
    suspend fun hasAccess(): Boolean

    /**
     * Samples from [samplesFrom] and flow days from [flowFrom], both up to [to].
     *
     * Two starts because the cycle wants a longer look back than the metrics do: one
     * period is not a cycle, and the first import should bring several.
     */
    suspend fun read(samplesFrom: Instant, flowFrom: Instant, to: Instant, zone: TimeZone): HealthReading

    /** Gives the permissions back where the platform allows it (Health Connect does; HealthKit does not). */
    suspend fun revokeAccess()

    /** Where an unavailable store is fixed: Health Connect's Play listing. A no-op on iOS. */
    fun openStore()

    /**
     * Whether an app that writes into this store, and that she would name on its own, is
     * on the phone — Samsung Health on a Galaxy writes into Health Connect.
     */
    suspend fun isWriterInstalled(writer: HealthProvider): Boolean = false

    /** Whether that app has been allowed to write into the store — without it nothing arrives. */
    suspend fun isWriterWriting(writer: HealthProvider): Boolean = false

    /**
     * Where she lets that app write: the store's permission page for it, or the app's
     * store listing when it is not installed.
     */
    fun openWriter(writer: HealthProvider) = Unit

    object None : HealthPlatform {
        override val provider: HealthProvider? = null
        override suspend fun availability() = HealthAvailability.UNSUPPORTED
        override suspend fun hasAccess() = false
        override suspend fun read(samplesFrom: Instant, flowFrom: Instant, to: Instant, zone: TimeZone) =
            HealthReading(emptyList(), emptySet())
        override suspend fun revokeAccess() = Unit
        override fun openStore() = Unit
    }
}

/**
 * What the app remembers about the phone's store between launches.
 *
 * Kept per account: a second person signing in on the same phone has not switched it
 * on, and her first sync must look back the full window rather than start from someone
 * else's last one.
 */
data class HealthSyncState(
    val userId: String? = null,
    val enabled: Boolean = false,
    val lastSyncAt: Instant? = null,
)

/** Plain preferences, not the keychain: nothing here is a secret. */
interface HealthSyncPrefs {
    suspend fun load(): HealthSyncState
    suspend fun save(state: HealthSyncState)
}

class InMemoryHealthSyncPrefs(var state: HealthSyncState = HealthSyncState()) : HealthSyncPrefs {
    override suspend fun load(): HealthSyncState = state
    override suspend fun save(state: HealthSyncState) {
        this.state = state
    }
}
