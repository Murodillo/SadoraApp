package uz.sadora.widget

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import platform.Foundation.NSUUID

/**
 * Everything the Swift side of a widget or the watch needs, behind one object, so Swift
 * never builds a Kotlin type it does not have to.
 *
 * [keychainGroup] is the full access group (`TEAMID.uz.sadora.shared`); the app, the
 * widget extension and the watch app all list it in their entitlements.
 */
class WidgetHost(appGroup: String, keychainGroup: String?) {
    val store = WidgetStore(AppGroupKeyValues(appGroup))
    val tokens = CompanionTokens(KeychainKeyValues(SERVICE, keychainGroup))

    private val client = HttpClient(Darwin) {
        // A widget's refresh has seconds, not minutes, before iOS ends it.
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 8_000
        }
    }
    val sync = WidgetSync(CompanionApi(store::baseUrl, tokens, client), store, { Clock.System.now() }, { NSUUID().UUIDString() })

    /** What a widget with [config] shows at [epochSeconds], in the phone's own timezone. */
    fun contentAt(config: WidgetConfig, epochSeconds: Long): WidgetContent? {
        val snapshot = store.snapshot() ?: return null
        val now = Instant.fromEpochSeconds(epochSeconds).toLocalDateTime(TimeZone.currentSystemDefault())
        return WidgetContent.build(snapshot, config, now)
    }

    fun signedIn(): Boolean = tokens.refresh() != null && store.snapshot() != null

    /** The language the widget's own chrome speaks (placeholder, sign-in prompt). */
    fun signInText(): String = WidgetStrings.of(store.snapshot()?.summary?.language ?: uz.sadora.contract.Language.UZ).signIn

    /** Today's date as the snapshot's own type, for the water button. */
    fun today(): kotlinx.datetime.LocalDate =
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

    /**
     * A widget's settings, built from the plain values an App Intent holds. Swift cannot
     * use Kotlin's default arguments, and a list of Kotlin enums is awkward there.
     */
    fun config(
        size: WidgetSize,
        water: Boolean,
        doses: Boolean,
        health: Boolean,
        waterStepMl: Int,
        lockScreen: Boolean,
        revealOnLockScreen: Boolean,
    ): WidgetConfig = WidgetConfig(
        size = size,
        blocks = buildList {
            if (water) add(WidgetBlock.WATER)
            if (doses) add(WidgetBlock.DOSES)
            if (health) add(WidgetBlock.HEALTH)
        },
        waterStepMl = waterStepMl,
        lockScreen = lockScreen,
        revealOnLockScreen = revealOnLockScreen,
    )

    private val ops = WidgetOps(store, tokens, sync, ::today)

    /** A water button: queued and drawn at once, then sent. */
    suspend fun tapWater(ml: Int): WidgetSync.Outcome = ops.tapWater(ml)

    /** A dose button or a notification's answer. */
    suspend fun tapDose(medicationId: String, day: String, time: String, taken: Boolean): WidgetSync.Outcome =
        ops.tapDose(medicationId, day, time, taken)

    /** The snapshot is older than [minutes], or there is none. */
    fun isStale(minutes: Int): Boolean {
        val saved = store.snapshot()?.savedAt ?: return true
        return Clock.System.now() - saved > kotlin.time.Duration.parse("${minutes}m")
    }

    /** The app's choice for a new widget (the "Widgets and watch" page), when it made one. */
    fun defaultConfig(): WidgetConfig? = store.config("default")

    /** What the phone sent the watch over WatchConnectivity; see [WidgetOps.importFromPhone]. */
    fun importFromPhone(tokensJson: String?, snapshotJson: String?, baseUrl: String) =
        ops.importFromPhone(tokensJson, snapshotJson, baseUrl)

    companion object {
        const val SERVICE = "uz.sadora.companion"
    }
}
