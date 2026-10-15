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

    companion object {
        const val SERVICE = "uz.sadora.companion"
    }
}
