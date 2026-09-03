package org.example.project.data

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin

internal actual fun platformHttpEngine(): HttpClientEngine = Darwin.create()

/** The simulator shares the host's loopback, so localhost is the development machine. */
internal actual fun developmentBaseUrl(): String = "http://127.0.0.1:8080"
