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
    fun doseSkipped(doseId: String)
    fun mealLogged(meal: Meal)

    /** The Mind check-in: mood, energy and stress go up together as one record. */
    fun checkInChanged(mood: Mood, energy: Int, stress: Int)

    /** A finished breathing or meditation session. */
    fun practiceLogged(kind: PracticeKind, seconds: Int)
}

/** What the Mind tab can start. Mirrors the wire enum without depending on it. */
enum class PracticeKind { Breathing, Meditation }

/**
 * Where the secret chat's edits go. Its own sink rather than more methods on
 * [AppStateSync] because the feed has its own controller and its own lifetime — it is
 * loaded when the room is opened, not with the health tabs.
 */
interface CommunitySync {
    fun postLiked(postId: String, liked: Boolean)
    fun postSaved(postId: String, saved: Boolean)
    fun commentAdded(postId: String, body: String)
    fun postCreated(topic: CommunityTopic, body: String)
    fun postDeleted(postId: String)
    fun postReported(postId: String, reason: ReportReason, note: String?)
}
