package org.example.project.data

/**
 * Where the app talks to. The emulator and the simulator disagree about what
 * "localhost" means, so the development host is chosen per platform rather than
 * hard-coded here.
 */
data class SadoraEnvironment(
    val baseUrl: String,
    /** Full request and response logging. Never enabled in a release build. */
    val verboseLogging: Boolean = false,
) {
    companion object {
        fun development(): SadoraEnvironment =
            SadoraEnvironment(baseUrl = developmentBaseUrl(), verboseLogging = true)

        val Stage: SadoraEnvironment = SadoraEnvironment("https://api.stage.sadora.uz")
        val Production: SadoraEnvironment = SadoraEnvironment("https://api.sadora.uz")
    }
}

/** `10.0.2.2` on the Android emulator, `127.0.0.1` in the iOS simulator. */
internal expect fun developmentBaseUrl(): String
