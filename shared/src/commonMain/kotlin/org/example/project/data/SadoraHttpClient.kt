package org.example.project.data

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * JSON settings that mirror the server's.
 *
 * `ignoreUnknownKeys` is what lets a shipped app survive a server that has grown a
 * field — without it, every additive backend change would crash older installs.
 */
val SadoraJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
    isLenient = false
}

/** The platform's HTTP engine: OkHttp on Android, NSURLSession on iOS. */
internal expect fun platformHttpEngine(): HttpClientEngine

internal fun createSadoraHttpClient(
    environment: SadoraEnvironment,
    engine: HttpClientEngine = platformHttpEngine(),
): HttpClient = HttpClient(engine) { configureSadoraClient(environment) }

internal fun HttpClientConfig<*>.configureSadoraClient(environment: SadoraEnvironment) {
    // Errors are read from the body rather than raised as exceptions, so the client
    // never throws a status at a caller that only wanted the parsed error.
    expectSuccess = false

    install(ContentNegotiation) { json(SadoraJson) }

    install(HttpTimeout) {
        requestTimeoutMillis = 30_000
        connectTimeoutMillis = 15_000
        socketTimeoutMillis = 30_000
    }

    if (environment.verboseLogging) {
        install(Logging) {
            // HEADERS, not ALL: request bodies carry health data and sign-in codes, and
            // a debug log is the easiest place for those to end up somewhere unaudited.
            level = LogLevel.HEADERS
        }
    }

    defaultRequest {
        url(environment.baseUrl.trimEnd('/') + "/")
        contentType(ContentType.Application.Json)
    }
}
