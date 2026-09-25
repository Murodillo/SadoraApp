package uz.sadora.doctor.data

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

        /**
         * Development against a host reachable over the network.
         *
         * A physical phone cannot resolve [developmentBaseUrl] — 10.0.2.2 exists only
         * inside the emulator — so a build meant for a real device is pointed at the
         * development machine's address on the local network instead.
         *
         * [host] may also be a whole URL. A tunnel that publishes the development
         * machine to the internet answers on 443 under its own name, so a build aimed
         * at one has no port to append and no say in the scheme.
         */
        fun development(host: String): SadoraEnvironment {
            val url = if (host.startsWith("http://") || host.startsWith("https://")) {
                host.trimEnd('/')
            } else {
                "http://$host:8080"
            }
            return SadoraEnvironment(baseUrl = url, verboseLogging = true)
        }

        val Stage: SadoraEnvironment = SadoraEnvironment("https://dev-api.sadora.app")
        val Production: SadoraEnvironment = SadoraEnvironment("https://api.sadora.app")
    }
}

/** `10.0.2.2` on the Android emulator, `127.0.0.1` in the iOS simulator. */
internal expect fun developmentBaseUrl(): String
