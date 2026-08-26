package uz.sadora.server.user

import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import uz.sadora.contract.Ack
import uz.sadora.contract.ConsentGrants
import uz.sadora.contract.DeleteAccountRequest
import uz.sadora.contract.OnboardingRequest
import uz.sadora.contract.Platform
import uz.sadora.contract.RegisterDeviceRequest
import uz.sadora.contract.UpdateProfileRequest
import uz.sadora.server.api.enumParameter
import uz.sadora.server.api.requestContext
import uz.sadora.server.api.requireUserId
import uz.sadora.server.entitlement.EntitlementService
import uz.sadora.server.flags.FeatureFlagService
import uz.sadora.server.flags.FlagContext
import uz.sadora.server.config.AppConfig
import uz.sadora.server.plugins.USER_AUTH

fun Route.userRoutes(
    userService: UserService,
    entitlementService: EntitlementService,
    flagService: FeatureFlagService,
    config: AppConfig,
) {
    authenticate(USER_AUTH) {

        get("/bootstrap") {
            call.respond(
                userService.bootstrap(call.requireUserId(), call.enumParameter<Platform>("platform")),
            )
        }

        route("/me") {
            get {
                call.respond(userService.profile(call.requireUserId()))
            }

            patch {
                val request = call.receive<UpdateProfileRequest>()
                call.respond(
                    userService.updateProfile(call.requireUserId(), request, call.requestContext()),
                )
            }

            post("/onboarding") {
                val request = call.receive<OnboardingRequest>()
                call.respond(
                    userService.completeOnboarding(
                        call.requireUserId(),
                        request,
                        call.requestContext(),
                    ),
                )
            }

            get("/consents") {
                call.respond(userService.consents(call.requireUserId()))
            }

            put("/consents") {
                val request = call.receive<ConsentGrants>()
                call.respond(
                    userService.updateConsents(call.requireUserId(), request, call.requestContext()),
                )
            }

            post("/devices") {
                val request = call.receive<RegisterDeviceRequest>()
                userService.registerDevice(call.requireUserId(), request.device)
                call.respond(Ack())
            }

            delete {
                val request = call.receive<DeleteAccountRequest>()
                userService.requestDeletion(call.requireUserId(), request, call.requestContext())
                call.respond(Ack())
            }
        }

        get("/entitlements") {
            call.respond(userService.entitlements(call.requireUserId()))
        }

        get("/subscription") {
            call.respond(entitlementService.subscriptionStatus(call.requireUserId()))
        }

        get("/feature-flags") {
            val userId = call.requireUserId()
            val user = userService.requireUser(userId)
            call.respond(
                flagService.evaluate(
                    FlagContext(
                        userId = userId,
                        environment = config.environment,
                        language = user.language,
                        lifeStage = user.lifeStage,
                        platform = call.enumParameter<Platform>("platform"),
                    ),
                ),
            )
        }
    }
}
