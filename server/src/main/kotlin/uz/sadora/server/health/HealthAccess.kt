package uz.sadora.server.health

import kotlin.uuid.Uuid
import uz.sadora.server.core.ConsentRequiredException
import uz.sadora.server.core.NotFoundException
import uz.sadora.server.entitlement.EntitlementService
import uz.sadora.server.user.UserRecord
import uz.sadora.server.user.UserRepository

/**
 * The two gates in front of every health write, in one place.
 *
 * Consent is checked first on purpose: a user who has not agreed to storage should be
 * sent to the privacy screen, not told about her subscription. Reads deliberately go
 * through [requireUser] instead — withdrawing permission to store data does not withdraw
 * her right to see what is already there.
 *
 * Shared by the cycle, Mind and Nutrition services so the rule is written once; adding a
 * fourth health domain means taking this dependency, not remembering a convention.
 */
class HealthAccess(
    private val users: UserRepository,
    private val entitlements: EntitlementService,
) {
    suspend fun requireUser(userId: Uuid): UserRecord =
        users.findById(userId) ?: throw NotFoundException("Foydalanuvchi topilmadi")

    suspend fun requireWritable(userId: Uuid, featureKey: String): UserRecord {
        val user = requireUser(userId)
        val consents = users.consentsOf(userId)
        if (consents?.storeHealth != true) throw ConsentRequiredException("store_health")
        entitlements.requireAvailable(userId, featureKey, user.timezone)
        return user
    }
}
