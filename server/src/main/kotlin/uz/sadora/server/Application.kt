package uz.sadora.server

import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import java.util.TimeZone
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import uz.sadora.contract.API_VERSION
import uz.sadora.server.admin.AdminBootstrap
import uz.sadora.server.admin.adminRoutes
import uz.sadora.server.api.healthRoutes
import uz.sadora.server.auth.authRoutes
import uz.sadora.server.config.AppConfig
import uz.sadora.server.plugins.configureHttp
import uz.sadora.server.plugins.configureMonitoring
import uz.sadora.server.plugins.configureRateLimit
import uz.sadora.server.plugins.configureSecurity
import uz.sadora.server.plugins.configureSerialization
import uz.sadora.server.plugins.configureStatusPages
import uz.sadora.server.user.userRoutes

const val SERVER_VERSION: String = "0.1.0"

fun main() {
    // Every timestamp column is `timestamptz` and the domain works in UTC instants;
    // pinning the JVM removes the deploy host's timezone from the equation entirely.
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

    val config = AppConfig.fromEnvironment()
    val logger = LoggerFactory.getLogger("uz.sadora.server")
    val component = AppComponent(config)

    runBlocking { AdminBootstrap.run() }

    Runtime.getRuntime().addShutdownHook(Thread { component.close() })

    logger.info(
        "SADORA API {} starting on {}:{} [{}]",
        SERVER_VERSION,
        config.http.host,
        config.http.port,
        config.environment,
    )

    embeddedServer(
        factory = Netty,
        port = config.http.port,
        host = config.http.host,
        module = { apiModule(component) },
    ).start(wait = true)
}

fun Application.apiModule(component: AppComponent) {
    val config = component.config

    configureSerialization()
    configureMonitoring()
    configureHttp(config)
    configureStatusPages()
    configureRateLimit()
    configureSecurity(component.jwtService)

    routing {
        healthRoutes(config.environment.name.lowercase(), SERVER_VERSION)

        // The spec is served only where it is useful; production does not publish it.
        if (!config.environment.isProduction) {
            swaggerUI(path = "docs", swaggerFile = "openapi/openapi.yaml")
        }

        route("/$API_VERSION") {
            authRoutes(component.authService, component.otpService)
            userRoutes(
                userService = component.userService,
                entitlementService = component.entitlementService,
                flagService = component.flagService,
                config = config,
            )
            adminRoutes(
                adminAuth = component.adminAuthService,
                adminService = component.adminService,
                auditRepository = component.auditRepository,
                statsRepository = component.statsRepository,
                refreshTokens = component.refreshTokenService,
            )
        }
    }
}
