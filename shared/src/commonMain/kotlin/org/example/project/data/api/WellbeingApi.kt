package org.example.project.data.api

import io.ktor.client.request.setBody
import org.example.project.data.ApiCaller
import org.example.project.data.ApiResult
import org.example.project.data.HttpMethodKind
import uz.sadora.contract.DailyHealth
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
}
