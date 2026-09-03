package org.example.project.model

/**
 * Where local edits go once there is a backend behind the app.
 *
 * [AppState] is edited from a dozen call sites across the screens. Threading a callback
 * through each of them would mean touching every screen and missing one; a single sink
 * on the store wires all of them at once, and a screen added later is wired by writing
 * to the store as it already would.
 *
 * Declared here rather than in the data layer so the model keeps depending on nothing.
 * Implementations are expected to return immediately and do the network work elsewhere:
 * the screens mutate optimistically and the server's answer replaces the guess.
 */
interface AppStateSync {
    fun symptomToggled(label: String, nowSelected: Boolean)
    fun waterAdded(ml: Int)
    fun doseTaken(doseId: String)
    fun mealLogged(meal: Meal)
}
