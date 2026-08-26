package org.example.project.model

import org.example.project.design.StagePalette
import org.example.project.design.StagePalettes

/**
 * The life stage chosen during onboarding. This is the single biggest branch in the
 * app: it swaps the whole "Yo'l" tab and its label, and hides screens that do not
 * apply. Notably pregnancy/postpartum/menopause show **no cycle prediction at all**
 * rather than a disabled cycle view.
 */
enum class LifeStage(
    val title: String,
    val subtitle: String,
    val glyph: String,
    /** Label shown under the second tab for this stage. */
    val tabLabel: String,
    val palette: StagePalette,
) {
    Cycle(
        title = "Sikl kuzatuvi",
        subtitle = "Hayz, ovulyatsiya, simptomlar",
        glyph = "◔",
        tabLabel = "Sikl",
        palette = StagePalettes.cycle,
    ),
    TryingToConceive(
        title = "Homiladorlikni rejalashtirish",
        subtitle = "Unumdor kunlar, tayyorgarlik",
        glyph = "◌",
        tabLabel = "Reja",
        palette = StagePalettes.cycle,
    ),
    Pregnancy(
        title = "Homiladorlik",
        subtitle = "Haftalar, rivojlanish, uchrashuvlar",
        glyph = "◕",
        tabLabel = "Homilador",
        palette = StagePalettes.pregnancy,
    ),
    Postpartum(
        title = "Tug'ruqdan keyin",
        subtitle = "Tiklanish, emizish, kayfiyat",
        glyph = "◑",
        tabLabel = "Tiklanish",
        palette = StagePalettes.postpartum,
    ),
    Perimenopause(
        title = "Perimenopauza",
        subtitle = "Simptomlar, uyqu, energiya",
        glyph = "◒",
        tabLabel = "Bosqich",
        palette = StagePalettes.perimenopause,
    ),
    Menopause(
        title = "Menopauza",
        subtitle = "Salomatlik maqsadlari",
        glyph = "◐",
        tabLabel = "Salomatlik",
        palette = StagePalettes.menopause,
    );

    /** Stages that never show a cycle-day prediction. */
    val predictsCycle: Boolean
        get() = this == Cycle || this == TryingToConceive
}

/** The four phases of a menstrual cycle, used to colour the calendar. */
enum class CyclePhase { Period, Follicular, Fertile, Luteal }
