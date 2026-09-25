package uz.sadora.app.data.api

import io.ktor.client.request.setBody
import uz.sadora.app.data.ApiCaller
import uz.sadora.app.data.ApiResult
import uz.sadora.app.data.HttpMethodKind
import uz.sadora.contract.Ack
import uz.sadora.contract.ConnectStart
import uz.sadora.contract.DailyHealth
import uz.sadora.contract.HealthProvider
import uz.sadora.contract.ProviderInfo
import uz.sadora.contract.SyncResult
import uz.sadora.contract.WearableConnection
import uz.sadora.contract.DailyHealthRange
import uz.sadora.contract.IngestResult
import uz.sadora.contract.IngestSamplesRequest
import uz.sadora.contract.NotificationMessage
import uz.sadora.contract.NotificationSettings
import uz.sadora.contract.ProviderStatus
import uz.sadora.contract.UpdateNotificationSettingsRequest

class NotificationApi(private val caller: ApiCaller) {

    suspend fun settings(): ApiResult<NotificationSettings> =
        caller.authenticated("v1/notifications/settings", HttpMethodKind.GET)

    suspend fun updateSettings(
        request: UpdateNotificationSettingsRequest,
    ): ApiResult<NotificationSettings> =
        caller.authenticated("v1/notifications/settings", HttpMethodKind.PUT) { setBody(request) }

    suspend fun history(limit: Int = 50): ApiResult<List<NotificationMessage>> =
        caller.authenticated("v1/notifications/history?limit=$limit", HttpMethodKind.GET)
}

/**
 * Wearable data. The reading itself is native — HealthKit and Health Connect are
 * platform APIs — and this is where the batch is posted afterwards.
 */
class WearableApi(private val caller: ApiCaller) {

    suspend fun ingest(request: IngestSamplesRequest): ApiResult<IngestResult> =
        caller.authenticated("v1/health-data/samples", HttpMethodKind.POST) { setBody(request) }

    suspend fun today(): ApiResult<DailyHealth> =
        caller.authenticated("v1/health-data/today", HttpMethodKind.GET)

    suspend fun daily(from: kotlinx.datetime.LocalDate, to: kotlinx.datetime.LocalDate): ApiResult<DailyHealthRange> =
        caller.authenticated("v1/health-data/daily?from=$from&to=$to", HttpMethodKind.GET)

    suspend fun sources(): ApiResult<List<ProviderStatus>> =
        caller.authenticated("v1/health-data/sources", HttpMethodKind.GET)

    // ---- cloud providers: an OAuth grant the server keeps, and pulls on

    suspend fun providers(): ApiResult<List<ProviderInfo>> =
        caller.authenticated("v1/wearables/providers", HttpMethodKind.GET)

    suspend fun connections(): ApiResult<List<WearableConnection>> =
        caller.authenticated("v1/wearables/connections", HttpMethodKind.GET)

    /** The URL to open in the browser. The state inside it is the server's, checked on return. */
    suspend fun connect(provider: HealthProvider): ApiResult<ConnectStart> =
        caller.authenticated("v1/wearables/${provider.wirePath()}/connect", HttpMethodKind.POST)

    /** Hands the consent page's code and state back; the server checks they are hers. */
    suspend fun complete(provider: HealthProvider, state: String, code: String): ApiResult<Ack> =
        caller.authenticated("v1/wearables/${provider.wirePath()}/complete", HttpMethodKind.POST) {
            setBody(uz.sadora.contract.CompleteConnectRequest(state, code))
        }

    suspend fun disconnect(provider: HealthProvider): ApiResult<Ack> =
        caller.authenticated("v1/wearables/${provider.wirePath()}", HttpMethodKind.DELETE)

    suspend fun sync(provider: HealthProvider): ApiResult<SyncResult> =
        caller.authenticated("v1/wearables/${provider.wirePath()}/sync", HttpMethodKind.POST)

    private fun HealthProvider.wirePath(): String = name.lowercase()
}
