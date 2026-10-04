package uz.sadora.app.ui.journey

import org.jetbrains.compose.resources.DrawableResource
import uz.sadora.app.resources.*
import uz.sadora.contract.SymptomDefinition

/**
 * The colour icon for a symptom in the server's catalogue, by its key.
 *
 * Null for a key the set has no picture for — a symptom added on the server shows its
 * word alone (or the tile's emoji fallback) until an icon is drawn for it, rather than
 * borrowing one that means something else.
 */
internal fun symptomArt(key: String): DrawableResource? = when (key) {
    "discharge" -> Res.drawable.ic3d_sym_discharge
    "cramps" -> Res.drawable.ic3d_cramps
    "headache" -> Res.drawable.ic3d_headache
    "back_pain" -> Res.drawable.ic3d_back_pain
    "joint_pain" -> Res.drawable.ic3d_sym_joint
    "breast_tender" -> Res.drawable.ic3d_breast
    "nausea" -> Res.drawable.ic3d_sym_nausea
    "bloating" -> Res.drawable.ic3d_bloating
    "swelling" -> Res.drawable.ic3d_sym_swelling
    "acne" -> Res.drawable.ic3d_sym_acne
    "mood_swings" -> Res.drawable.ic3d_sym_mood_swings
    "anxiety" -> Res.drawable.ic3d_sym_anxiety
    "insomnia" -> Res.drawable.ic3d_sleep
    "night_sweats" -> Res.drawable.ic3d_sym_night_sweats
    "hot_flush" -> Res.drawable.ic3d_sym_hot_flush
    "fatigue" -> Res.drawable.ic3d_fatigue
    "cravings" -> Res.drawable.ic3d_sym_cravings
    "heartburn" -> Res.drawable.ic3d_sym_heartburn
    "constipation" -> Res.drawable.ic3d_sym_constipation
    "leg_cramps" -> Res.drawable.ic3d_sym_leg_cramps
    "lochia" -> Res.drawable.ic3d_sym_lochia
    "wound_pain" -> Res.drawable.ic3d_sym_wound
    "vaginal_dryness" -> Res.drawable.ic3d_sym_dryness
    "brain_fog" -> Res.drawable.ic3d_sym_brain_fog
    "palpitations" -> Res.drawable.ic3d_sym_palpitations
    else -> null
}

internal fun SymptomDefinition.art(): DrawableResource? = symptomArt(key)
