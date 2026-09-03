package org.example.project.data

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp

internal actual fun platformHttpEngine(): HttpClientEngine = OkHttp.create()

/** The emulator reaches the host machine at 10.0.2.2, not at localhost. */
internal actual fun developmentBaseUrl(): String = "http://10.0.2.2:8080"
