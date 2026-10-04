package uz.sadora.app.ui.components

import org.jetbrains.compose.resources.DrawableResource
import uz.sadora.app.model.Mood
import uz.sadora.app.resources.*
import uz.sadora.contract.MealSlot

/**
 * The colour face for a mood, from the same clay set as every other icon.
 *
 * [Mood.emoji] stays in the model for plain-text places (notifications, shares); the
 * screens draw this instead.
 */
fun Mood.art(): DrawableResource = when (this) {
    Mood.Bad -> Res.drawable.ic3d_mood_bad
    Mood.Low -> Res.drawable.ic3d_mood_low
    Mood.Ok -> Res.drawable.ic3d_mood_ok
    Mood.Good -> Res.drawable.ic3d_mood_good
    Mood.Great -> Res.drawable.ic3d_mood_great
}

/** The dish a meal slot shows until the meal has a photo of its own. */
fun MealSlot.art(): DrawableResource = when (this) {
    MealSlot.BREAKFAST -> Res.drawable.ic3d_meal_breakfast
    MealSlot.LUNCH -> Res.drawable.ic3d_meal_lunch
    MealSlot.DINNER -> Res.drawable.ic3d_meal_dinner
    MealSlot.SNACK -> Res.drawable.ic3d_apple
}
