package org.example.project.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine

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
    val api: SadoraApi = SadoraApi(client, session)
    val repository: SadoraRepository = SadoraRepository(api, session, device, appVersion)

    fun close() = client.close()
}
