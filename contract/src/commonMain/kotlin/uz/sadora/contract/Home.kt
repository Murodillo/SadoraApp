package uz.sadora.contract

import kotlinx.serialization.Serializable

/**
 * How the Today screen is arranged — the layout she chose, kept on the server.
 *
 * The point of storing it rather than leaving it on the device is that a new phone
 * should open on the home screen she built, not on the default one. It is a preference
 * and not health data, so it needs no consent and survives a sign-out.
 *
 * The app owns the *catalogue* and the server owns the *arrangement*: a release that
 * adds a widget does not need a migration, and a widget key the app no longer knows is
 * ignored rather than rejected — an older phone must not lose its layout because a
 * newer one arranged a card it has never heard of.
 */
object HomeWidgets {
    /** The assistant's read on today. Free accounts see the prompt version. */
    const val AI = "ai"

    /** The health score ring with its four signals. */
    const val SCORE = "score"

    /** The streak and the Gul balance. */
    const val STREAK = "streak"

    /** Cycle day, pregnancy week, recovery week — whatever the stage counts in. */
    const val STAGE = "stage"

    /** What today still asks for: doses, then water. */
    const val PLAN = "plan"

    /** Last night, as the sleep module has it. */
    const val SLEEP = "sleep"

    /** Today's doses, with the grid. */
    const val MEDICATIONS = "medications"

    /** The most recent finding from Tahlillar. */
    const val INSIGHTS = "insights"

    /** One piece from the Bilim library, picked for her stage. */
    const val KNOWLEDGE = "knowledge"

    /** The four shortcuts. */
    const val QUICK_ACTIONS = "quick_actions"

    /** The plain-arithmetic summary of the day. */
    const val SUMMARY = "summary"

    /**
     * The default arrangement, in order.
     *
     * Everything ships visible except the four that would make a first run long before
     * there is anything in them to show.
     */
    val defaults: List<HomeWidget> = listOf(
        HomeWidget(AI, 0, true),
        HomeWidget(STREAK, 1, true),
        HomeWidget(SCORE, 2, true),
        HomeWidget(STAGE, 3, true),
        HomeWidget(PLAN, 4, true),
        HomeWidget(SLEEP, 5, false),
        HomeWidget(MEDICATIONS, 6, false),
        HomeWidget(INSIGHTS, 7, false),
        HomeWidget(KNOWLEDGE, 8, false),
        HomeWidget(QUICK_ACTIONS, 9, true),
        HomeWidget(SUMMARY, 10, true),
    )

    /** Widgets that must stay on screen, whatever the layout says. */
    val required: Set<String> = setOf(AI)

    val keys: List<String> get() = defaults.map { it.key }
}

/** One card on Today: where it sits and whether it is drawn at all. */
@Serializable
data class HomeWidget(
    val key: String,
    val position: Int = 0,
    val visible: Boolean = true,
)

/**
 * The whole arrangement.
 *
 * Sent back in full on every save rather than as a patch: reordering is a rearrangement
 * of the set, and two phones sending overlapping patches would interleave into a layout
 * neither of them asked for.
 */
@Serializable
data class HomeLayout(
    val widgets: List<HomeWidget> = HomeWidgets.defaults,
) {
    /** The layout as the screen draws it: visible cards only, in position order. */
    fun visible(): List<String> =
        widgets.filter { it.visible }.sortedBy { it.position }.map { it.key }

    /**
     * The stored layout merged with the catalogue.
     *
     * Keys the store has never heard of are dropped and keys it has gained since are
     * appended with their shipped default, so an app release can add a widget without
     * anyone's saved layout having to be migrated.
     */
    fun reconciled(): HomeLayout {
        val stored = widgets.associateBy { it.key }
        val merged = HomeWidgets.defaults.map { fallback ->
            val saved = stored[fallback.key] ?: return@map fallback
            saved.copy(visible = saved.visible || fallback.key in HomeWidgets.required)
        }
        return HomeLayout(
            merged.sortedBy { it.position }.mapIndexed { index, widget -> widget.copy(position = index) },
        )
    }
}

@Serializable
data class SaveHomeLayoutRequest(val widgets: List<HomeWidget>)
