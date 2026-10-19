package uz.sadora.app.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import uz.sadora.app.data.api.AiApi
import uz.sadora.app.data.api.PetApi
import uz.sadora.app.data.api.CommunityApi
import uz.sadora.app.data.api.DoctorApi
import uz.sadora.app.data.api.CycleApi
import uz.sadora.app.data.api.BillingApi
import uz.sadora.app.data.api.InsightsApi
import uz.sadora.app.data.api.LearnApi
import uz.sadora.app.data.api.MedicationApi
import uz.sadora.app.data.api.AppointmentApi
import uz.sadora.app.data.api.StageEventApi
import uz.sadora.app.data.api.MindApi
import uz.sadora.app.data.api.NotificationApi
import uz.sadora.app.data.api.NutritionApi
import uz.sadora.app.data.api.PhotoApi
import uz.sadora.app.data.api.RewardsApi
import uz.sadora.app.data.api.PartnerApi
import uz.sadora.app.data.api.ShareApi
import uz.sadora.app.data.api.WearableApi
import uz.sadora.app.data.health.ApiDeviceHealthBackend
import uz.sadora.app.data.health.DeviceHealthSync
import uz.sadora.app.data.health.HealthPlatform
import uz.sadora.app.data.health.HealthSyncPrefs
import uz.sadora.app.data.health.InMemoryHealthSyncPrefs

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
    /** When the app may next ask its occasional questions; plain preferences on a phone. */
    val prompts: PromptPrefs = PromptPrefs.InMemory(),
    /**
     * HealthKit or Health Connect, built by the platform because Health Connect needs a
     * `Context`. The no-op by default, so a test or a preview reads nothing.
     */
    healthPlatform: HealthPlatform = HealthPlatform.None,
    /**
     * The store's purchase sheet, when the app was installed from a store. Null for a
     * direct build, which keeps Payme and Click.
     */
    private val storeBilling: StoreBilling? = null,
    healthPrefs: HealthSyncPrefs = InMemoryHealthSyncPrefs(),
    /** The home-screen widgets and the watch; none in tests and previews. */
    widgetPlatform: uz.sadora.app.data.widget.WidgetPlatform = uz.sadora.app.data.widget.WidgetPlatform.None,
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
    val stageEventApi: StageEventApi = StageEventApi(caller)
    val labApi: uz.sadora.app.data.api.LabApi = uz.sadora.app.data.api.LabApi(caller)
    val communityApi: CommunityApi = CommunityApi(caller)
    val doctorApi: DoctorApi = DoctorApi(caller)
    val aiApi: AiApi = AiApi(caller)
    val insightsApi: InsightsApi = InsightsApi(caller)
    val learnApi: LearnApi = LearnApi(caller)
    val billingApi: BillingApi = BillingApi(caller)
    val rewardsApi: RewardsApi = RewardsApi(caller)
    val shareApi: ShareApi = ShareApi(caller)
    val partnerApi: PartnerApi = PartnerApi(caller)
    val photoApi: PhotoApi = PhotoApi(caller)
    val petApi: PetApi = PetApi(caller)
    val frameApi: uz.sadora.app.data.api.FrameApi = uz.sadora.app.data.api.FrameApi(caller)
    val widgetSummaryApi: uz.sadora.app.data.api.WidgetSummaryApi = uz.sadora.app.data.api.WidgetSummaryApi(caller)
    val repository: SadoraRepository = SadoraRepository(api, session, device, appVersion)

    private val widgetScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)

    /** The widgets' and the watch's side of the app; see [uz.sadora.app.data.widget.WidgetBridge]. */
    val widgets: uz.sadora.app.data.widget.WidgetBridge = uz.sadora.app.data.widget.WidgetBridge(
        platform = widgetPlatform,
        appApi = uz.sadora.app.data.widget.AppWidgetApi(this),
        api = api,
        session = session,
        baseUrl = environment.baseUrl,
        scope = widgetScope,
    ).also { bridge ->
        caller.onWrite = { path -> if (uz.sadora.app.data.widget.WidgetBridge.affects(path)) bridge.changed() }
    }

    /** One per process: its lock is what keeps a resume and a tap from reading twice at once. */
    val deviceHealth: DeviceHealthSync =
        DeviceHealthSync(healthPlatform, healthPrefs, ApiDeviceHealthBackend(wearableApi, cycleApi))

    /**
     * Built per session rather than eagerly, because it mirrors into the [AppState] the
     * UI owns and there is exactly one of those.
     */
    fun healthController(state: uz.sadora.app.model.AppState): HealthController =
        HealthController(cycleApi, mindApi, nutritionApi, medicationApi, wearableApi, appointmentApi, state)

    fun communityController(state: uz.sadora.app.model.AppState): CommunityController =
        CommunityController(communityApi, state)

    /** [clock] is for tests: the typing throttle is measured on it. */
    fun messagesController(clock: kotlin.time.Clock = kotlin.time.Clock.System): MessagesController =
        MessagesController(communityApi, clock)

    fun doctorController(state: uz.sadora.app.model.AppState): DoctorController = DoctorController(doctorApi, state)

    fun aiController(state: uz.sadora.app.model.AppState): AiController =
        AiController(aiApi, state)

    fun insightsController(): InsightsController = InsightsController(insightsApi)

    fun learnController(): LearnController = LearnController(learnApi)

    fun billingController(): BillingController = BillingController(billingApi, storeBilling)

    fun rewardsController(state: uz.sadora.app.model.AppState): RewardsController =
        RewardsController(rewardsApi, state, icons)

    fun photoController(state: uz.sadora.app.model.AppState): PhotoController = PhotoController(photoApi, state)

    fun petController(state: uz.sadora.app.model.AppState): PetController = PetController(
        petApi,
        state,
        prompts,
        store = storeBilling,
        currentUserId = { (session.state.value as? SessionState.SignedIn)?.user?.id },
    )

    fun frameController(state: uz.sadora.app.model.AppState): FrameController = FrameController(
        frameApi,
        state,
        store = storeBilling,
        currentUserId = { (session.state.value as? SessionState.SignedIn)?.user?.id },
    )

    fun shareController(): ShareController = ShareController(shareApi, analytics)

    fun partnerController(): PartnerController = PartnerController(
        api = partnerApi,
        analytics = analytics,
        store = storeBilling,
        currentUserId = { (session.state.value as? SessionState.SignedIn)?.user?.id },
    )

    fun wearableController(): WearableController = WearableController(
        api = wearableApi,
        analytics = analytics,
        device = deviceHealth,
        currentUserId = { (session.state.value as? SessionState.SignedIn)?.user?.id },
    )

    fun notificationsController(): NotificationsController = NotificationsController(notificationApi)

    fun stageEventsController(): StageEventsController = StageEventsController(stageEventApi)

    fun labController(): LabController = LabController(labApi)

    fun conditionController(): ConditionController =
        ConditionController(uz.sadora.app.data.api.ConditionApi(caller), wearableApi)

    fun sosController(): SosController = SosController(uz.sadora.app.data.api.SosApi(caller))

    fun telegramController(): TelegramController = TelegramController(uz.sadora.app.data.api.TelegramApi(caller))

    fun wrappedController(): WrappedController = WrappedController(uz.sadora.app.data.api.WrappedApi(caller))

    fun kegelController(): KegelController = KegelController(uz.sadora.app.data.api.KegelApi(caller))

    fun reportController(): ReportController = ReportController(uz.sadora.app.data.api.ReportApi(caller))

    fun babyController(tools: StageEventsController): BabyController =
        BabyController(uz.sadora.app.data.api.BabyApi(caller, stageEventApi), tools)

    fun close() = client.close()
}
