package org.example.project.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import org.example.project.data.api.CycleApi
import org.example.project.data.api.MedicationApi
import org.example.project.data.api.MindApi
import org.example.project.data.api.NotificationApi
import org.example.project.data.api.NutritionApi
import org.example.project.data.api.WearableApi

/**
 * The data layer, assembled.
 *
 * Built by each platform's entry point — Android has a `Context` to hand and iOS does
 * not — and passed into the UI, so no screen reaches for a singleton and a test can
 * substitute the whole graph.
 */
class SadoraGraph(
    val tokenStorage: TokenStorage,
    val device: DeviceIdentity,
    val environment: SadoraEnvironment = SadoraEnvironment.Production,
    val appVersion: String? = null,
    engine: HttpClientEngine? = null,
) {
    private val client: HttpClient =
        engine?.let { createSadoraHttpClient(environment, it) } ?: createSadoraHttpClient(environment)

    val session: SessionStore = SessionStore(tokenStorage)
    private val caller: ApiCaller = ApiCaller(client, session)
    val api: SadoraApi = SadoraApi(caller, session)

    // One API class per area, all over the same caller, so a new domain never grows
    // SadoraApi or copies its refresh-and-retry logic.
    val cycleApi: CycleApi = CycleApi(caller)
    val mindApi: MindApi = MindApi(caller)
    val nutritionApi: NutritionApi = NutritionApi(caller)
    val medicationApi: MedicationApi = MedicationApi(caller)
    val notificationApi: NotificationApi = NotificationApi(caller)
    val wearableApi: WearableApi = WearableApi(caller)
    val repository: SadoraRepository = SadoraRepository(api, session, device, appVersion)

    /**
     * Built per session rather than eagerly, because it mirrors into the [AppState] the
     * UI owns and there is exactly one of those.
     */
    fun healthController(state: org.example.project.model.AppState): HealthController =
        HealthController(cycleApi, mindApi, nutritionApi, medicationApi, wearableApi, state)

    fun close() = client.close()
}
