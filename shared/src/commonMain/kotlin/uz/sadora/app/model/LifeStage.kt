package uz.sadora.app.model

import uz.sadora.app.design.StagePalette
import uz.sadora.app.design.StagePalettes

/**
 * The life stage chosen during onboarding. This is the single biggest branch in the
 * app: it swaps the whole "Yo'l" tab and its label, and hides screens that do not
 * apply. Notably pregnancy/postpartum/menopause show **no cycle prediction at all**
 * rather than a disabled cycle view.
 */
enum class LifeStage(val glyph: String, val palette: StagePalette) {
    Cycle("◔", StagePalettes.cycle),
    TryingToConceive("◌", StagePalettes.cycle),
    Pregnancy("◕", StagePalettes.pregnancy),
    Postpartum("◑", StagePalettes.postpartum),
    Perimenopause("◒", StagePalettes.perimenopause),
    Menopause("◐", StagePalettes.menopause);

    /** Stages that never show a cycle-day prediction. */
    val predictsCycle: Boolean
        get() = this == Cycle || this == TryingToConceive
}

/** The four phases of a menstrual cycle, used to colour the calendar. */
enum class CyclePhase { Period, Follicular, Fertile, Luteal }
