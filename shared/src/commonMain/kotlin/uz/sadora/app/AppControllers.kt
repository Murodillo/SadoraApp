package uz.sadora.app

import uz.sadora.app.data.AiController
import uz.sadora.app.data.Analytics
import uz.sadora.app.data.BillingController
import uz.sadora.app.data.CommunityController
import uz.sadora.app.data.HealthController
import uz.sadora.app.data.InsightsController
import uz.sadora.app.data.LearnController
import uz.sadora.app.data.NotificationsController
import uz.sadora.app.data.RewardsController
import uz.sadora.app.data.SadoraController
import uz.sadora.app.data.SadoraGraph
import uz.sadora.app.data.ShareController
import uz.sadora.app.data.WearableController
import uz.sadora.app.model.AppState

/**
 * Every controller the shell hands to its screens, built once.
 *
 * Before this, `MainShell` and `PushedScreen` took ten controllers as ten parameters,
 * and adding an eleventh meant editing four signatures. A bundle keeps the root and the
 * route table readable, and it is still the same objects — nothing here is a service
 * locator; screens receive the controller they need, not the bundle.
 *
 * With no graph — previews, tests — every controller runs locally, exactly as before.
 */
class AppControllers(
    val account: SadoraController,
    val health: HealthController,
    val community: CommunityController,
    val ai: AiController,
    val insights: InsightsController,
    val learn: LearnController,
    val billing: BillingController,
    val rewards: RewardsController,
    val share: ShareController,
    val wearables: WearableController,
    val notifications: NotificationsController,
    val analytics: Analytics,
) {
    companion object {
        fun from(graph: SadoraGraph?, state: AppState): AppControllers = AppControllers(
            account = SadoraController(graph?.repository, state),
            health = graph?.healthController(state) ?: HealthController(null, null, null, null, state = state),
            community = graph?.communityController(state) ?: CommunityController(null, state),
            ai = graph?.aiController(state) ?: AiController(null, state),
            insights = graph?.insightsController() ?: InsightsController(null),
            learn = graph?.learnController() ?: LearnController(null),
            billing = graph?.billingController() ?: BillingController(null),
            rewards = graph?.rewardsController(state) ?: RewardsController(null, state),
            share = graph?.shareController() ?: ShareController(null),
            wearables = graph?.wearableController() ?: WearableController(null),
            notifications = graph?.notificationsController() ?: NotificationsController(null),
            analytics = graph?.analytics ?: Analytics.None,
        )
    }
}
