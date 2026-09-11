package uz.sadora.server.core

import kotlin.uuid.Uuid

/**
 * The one line the health services know about the reward scheme.
 *
 * The dependency points this way on purpose. A dose being recorded is the product; the
 * coin on top of it is a decoration, and the medication service should not have to
 * import a package about coins to write one. It calls [logged] and forgets about it.
 *
 * Implementations must never throw and never block the caller's outcome: a reward that
 * could not be written is a missing decoration, while a dose that was not recorded
 * because of one would be a bug in a medical log.
 */
interface RewardHooks {

    /**
     * Something worth a coin just happened.
     *
     * [reference] makes a repeatable action countable — a dose id, a meal id — so the
     * scheme can cap it per day. Actions that can only happen once a day pass null.
     */
    suspend fun logged(userId: Uuid, reason: String, reference: String? = null)

    /** For a server with the scheme switched off, and for every test that does not use it. */
    object None : RewardHooks {
        override suspend fun logged(userId: Uuid, reason: String, reference: String?) = Unit
    }
}
