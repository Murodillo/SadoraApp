package uz.sadora.widget

import android.content.Context
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import java.util.UUID
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * The Android widgets' and the Wear OS app's way in: the store, the companion session and
 * the sync, built once per process.
 */
class WidgetHost private constructor(context: Context) {
    private val values = KeystoreKeyValues(context)
    val store = WidgetStore(values)
    val tokens = CompanionTokens(values)

    private val client = HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = 20_000
            connectTimeoutMillis = 10_000
        }
    }
    val sync = WidgetSync(CompanionApi(store::baseUrl, tokens, client), store, { Clock.System.now() }, { UUID.randomUUID().toString() })

    private val ops = WidgetOps(store, tokens, sync, ::today)

    suspend fun tapWater(ml: Int): WidgetSync.Outcome = ops.tapWater(ml)

    suspend fun tapDose(medicationId: String, day: String, time: String, taken: Boolean): WidgetSync.Outcome =
        ops.tapDose(medicationId, day, time, taken)

    /** What the phone sent the watch over the Data Layer; see [WidgetOps.importFromPhone]. */
    fun importFromPhone(tokensJson: String?, snapshotJson: String?, baseUrl: String) =
        ops.importFromPhone(tokensJson, snapshotJson, baseUrl)

    fun content(config: WidgetConfig): WidgetContent? {
        val snapshot = store.snapshot() ?: return null
        return WidgetContent.build(snapshot, config, Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()))
    }

    fun today(): kotlinx.datetime.LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

    fun signInText(): String =
        WidgetStrings.of(store.snapshot()?.summary?.language ?: uz.sadora.contract.Language.UZ).signIn

    companion object {
        @Volatile private var instance: WidgetHost? = null

        fun get(context: Context): WidgetHost =
            instance ?: synchronized(this) { instance ?: WidgetHost(context).also { instance = it } }
    }
}
