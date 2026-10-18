package uz.sadora.app

import uz.sadora.app.data.AiController
import uz.sadora.app.data.Analytics
import uz.sadora.app.data.BillingController
import uz.sadora.app.data.CommunityController
import uz.sadora.app.data.DoctorController
import uz.sadora.app.data.FrameController
import uz.sadora.app.data.MessagesController
import uz.sadora.app.data.HealthController
import uz.sadora.app.data.InsightsController
import uz.sadora.app.data.LearnController
import uz.sadora.app.data.NotificationsController
import uz.sadora.app.data.PetController
import uz.sadora.app.data.BabyController
import uz.sadora.app.data.ConditionController
import uz.sadora.app.data.LabController
import uz.sadora.app.data.StageEventsController
import uz.sadora.app.data.PhotoController
import uz.sadora.app.data.RewardsController
import uz.sadora.app.data.SadoraController
import uz.sadora.app.data.SadoraGraph
import uz.sadora.app.data.PartnerController
import uz.sadora.app.data.PromptPrefs
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
    val messages: MessagesController,
    val doctors: DoctorController,
    val ai: AiController,
    val insights: InsightsController,
    val learn: LearnController,
    val billing: BillingController,
    val rewards: RewardsController,
    val share: ShareController,
    val partner: PartnerController,
    val photos: PhotoController,
    val wearables: WearableController,
    val notifications: NotificationsController,
    val stageEvents: StageEventsController,
    /** Her lab sheets. Defaulted so the previews and tests that build this by hand need not know it. */
    val labs: LabController = LabController(null),
    /** Her babies; tied to [stageEvents] so a feed lands on the baby on screen. */
    val babies: BabyController = BabyController(null, StageEventsController(null)),
    /** PCOS and endometriosis: what she marked, the pain diary and the report. */
    val conditions: ConditionController = ConditionController(null),
    /** The doctor's report PDF and her month's free one. */
    val reports: uz.sadora.app.data.ReportController = uz.sadora.app.data.ReportController(null),
    /** Kegel exercises: program, levels, reminders, the leak check. */
    val kegel: uz.sadora.app.data.KegelController = uz.sadora.app.data.KegelController(null),
    /** Cycle Wrapped: per-cycle and yearly recaps. */
    val wrapped: uz.sadora.app.data.WrappedController = uz.sadora.app.data.WrappedController(null),
    val pet: PetController,
    val frames: FrameController,
    val analytics: Analytics,
    val prompts: PromptPrefs = PromptPrefs.InMemory(),
    val widgets: uz.sadora.app.data.widget.WidgetsController = uz.sadora.app.data.widget.WidgetsController(null),
) {
    companion object {
        fun from(graph: SadoraGraph?, state: AppState): AppControllers {
            val stageEvents = graph?.stageEventsController() ?: StageEventsController(null)
            return from(graph, state, stageEvents)
        }

        private fun from(graph: SadoraGraph?, state: AppState, stageEvents: StageEventsController): AppControllers = AppControllers(
            account = SadoraController(graph?.repository, state),
            health = graph?.healthController(state) ?: HealthController(null, null, null, null, state = state),
            community = graph?.communityController(state) ?: CommunityController(null, state),
            messages = graph?.messagesController() ?: MessagesController(null),
            doctors = graph?.doctorController(state) ?: DoctorController(null, state),
            ai = graph?.aiController(state) ?: AiController(null, state),
            insights = graph?.insightsController() ?: InsightsController(null),
            learn = graph?.learnController() ?: LearnController(null),
            billing = graph?.billingController() ?: BillingController(null),
            rewards = graph?.rewardsController(state) ?: RewardsController(null, state),
            share = graph?.shareController() ?: ShareController(null),
            partner = graph?.partnerController() ?: PartnerController(null),
            photos = graph?.photoController(state) ?: PhotoController(null, state),
            wearables = graph?.wearableController() ?: WearableController(null),
            notifications = graph?.notificationsController() ?: NotificationsController(null),
            stageEvents = stageEvents,
            labs = graph?.labController() ?: LabController(null),
            babies = graph?.babyController(stageEvents) ?: BabyController(null, stageEvents),
            conditions = graph?.conditionController() ?: ConditionController(null),
            reports = graph?.reportController() ?: uz.sadora.app.data.ReportController(null),
            kegel = graph?.kegelController() ?: uz.sadora.app.data.KegelController(null),
            wrapped = graph?.wrappedController() ?: uz.sadora.app.data.WrappedController(null),
            pet = graph?.petController(state) ?: PetController(null, state),
            frames = graph?.frameController(state) ?: FrameController(null, state),
            analytics = graph?.analytics ?: Analytics.None,
            prompts = graph?.prompts ?: PromptPrefs.InMemory(),
            widgets = uz.sadora.app.data.widget.WidgetsController(graph?.widgets),
        )
    }
}
