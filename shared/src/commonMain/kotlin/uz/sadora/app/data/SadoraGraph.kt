package uz.sadora.app.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import uz.sadora.app.data.api.AiApi
import uz.sadora.app.data.api.CommunityApi
import uz.sadora.app.data.api.CycleApi
import uz.sadora.app.data.api.BillingApi
import uz.sadora.app.data.api.InsightsApi
import uz.sadora.app.data.api.LearnApi
import uz.sadora.app.data.api.MedicationApi
import uz.sadora.app.data.api.AppointmentApi
import uz.sadora.app.data.api.MindApi
import uz.sadora.app.data.api.NotificationApi
import uz.sadora.app.data.api.NutritionApi
import uz.sadora.app.data.api.RewardsApi
import uz.sadora.app.data.api.ShareApi
import uz.sadora.app.data.api.WearableApi

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
    /**
     * Changes the launcher icon with the streak.
     *
     * Built by the platform like the token storage is, and defaulting to the no-op so
     * a test or a preview never touches the home screen.
     */
    val icons: AppIcons = AppIcons.None,
    /**
     * Product analytics, off until she consents. Built by the platform because the
     * SDK is; the shared code only decides what is worth recording.
     */
    val analytics: Analytics = Analytics.None,
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
    val appointmentApi: AppointmentApi = AppointmentApi(caller)
    val communityApi: CommunityApi = CommunityApi(caller)
    val aiApi: AiApi = AiApi(caller)
    val insightsApi: InsightsApi = InsightsApi(caller)
    val learnApi: LearnApi = LearnApi(caller)
    val billingApi: BillingApi = BillingApi(caller)
    val rewardsApi: RewardsApi = RewardsApi(caller)
    val shareApi: ShareApi = ShareApi(caller)
    val repository: SadoraRepository = SadoraRepository(api, session, device, appVersion)

    /**
     * Built per session rather than eagerly, because it mirrors into the [AppState] the
     * UI owns and there is exactly one of those.
     */
    fun healthController(state: uz.sadora.app.model.AppState): HealthController =
        HealthController(cycleApi, mindApi, nutritionApi, medicationApi, wearableApi, appointmentApi, state)

    fun communityController(state: uz.sadora.app.model.AppState): CommunityController =
        CommunityController(communityApi, state)

    fun aiController(state: uz.sadora.app.model.AppState): AiController =
        AiController(aiApi, state)

    fun insightsController(): InsightsController = InsightsController(insightsApi)

    fun learnController(): LearnController = LearnController(learnApi)

    fun billingController(): BillingController = BillingController(billingApi)

    fun rewardsController(state: uz.sadora.app.model.AppState): RewardsController =
        RewardsController(rewardsApi, state, icons)

    fun shareController(): ShareController = ShareController(shareApi, analytics)

    fun wearableController(): WearableController = WearableController(wearableApi, analytics)

    fun notificationsController(): NotificationsController = NotificationsController(notificationApi)

    fun close() = client.close()
}
