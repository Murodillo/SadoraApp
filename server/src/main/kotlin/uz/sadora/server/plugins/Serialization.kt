package uz.sadora.server.plugins

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import kotlinx.serialization.json.Json

/**
 * One JSON configuration for the whole API.
 *
 * `ignoreUnknownKeys` is on so an older app build does not break when the server adds a
 * field; `encodeDefaults` is on so a client never has to guess what an absent field
 * meant. `explicitNulls = false` keeps optional fields out of the payload entirely.
 */
val ApiJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
    isLenient = false
}

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(ApiJson)
    }
}
