package uz.sadora.doctor.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine

/**
 * The data layer, assembled.
 *
 * Built by each platform's entry point — Android has a `Context` to hand and iOS does
 * not — and passed into the UI, so no screen reaches for a singleton and a test can
 * substitute the whole graph, down to the HTTP engine.
 */
class DoctorGraph(
    val tokenStorage: TokenStorage,
    val device: DeviceIdentity,
    val environment: SadoraEnvironment = SadoraEnvironment.Production,
    val appVersion: String? = null,
    val prefs: AppPrefs = InMemoryAppPrefs(),
    engine: HttpClientEngine? = null,
) {
    private val client: HttpClient =
        engine?.let { createSadoraHttpClient(environment, it) } ?: createSadoraHttpClient(environment)

    val session: SessionStore = SessionStore(tokenStorage)
    private val caller: ApiCaller = ApiCaller(client, session)

    // One API class per area, all over the same caller, so none of them copies the
    // refresh-and-retry logic.
    val authApi: AuthApi = AuthApi(caller, session)
    val doctorApi: DoctorApi = DoctorApi(caller)
    val communityApi: CommunityApi = CommunityApi(caller)
    val patientApi: PatientApi = PatientApi(caller)

    val repository: AuthRepository = AuthRepository(authApi, session, device, appVersion)

    fun authController(): AuthController = AuthController(repository)

    /**
     * A `photoUrl` from any response, fetched with her token. One that does not point at
     * this app's own server is refused here, before a request is made.
     */
    suspend fun photo(photoUrl: String): ApiResult<ByteArray> =
        photoRequestUrl(environment.baseUrl, photoUrl)?.let { doctorApi.photo(it) }
            ?: ApiResult.Failure(ApiFailure.NotFound("not a photo on this server: $photoUrl"))

    fun doctorController(): DoctorController = DoctorController(doctorApi, communityApi, patientApi)

    fun workController(): WorkController = WorkController(doctorApi)

    fun close() = client.close()
}
