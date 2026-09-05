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
    val palette: StagePalette,
) {
    Cycle(
        title = "Sikl kuzatuvi",
        subtitle = "Hayz, ovulyatsiya, simptomlar",
        glyph = "◔",
        palette = StagePalettes.cycle,
    ),
    TryingToConceive(
        title = "Homiladorlikni rejalashtirish",
        subtitle = "Unumdor kunlar, tayyorgarlik",
        glyph = "◌",
        palette = StagePalettes.cycle,
    ),
    Pregnancy(
        title = "Homiladorlik",
        subtitle = "Haftalar, rivojlanish, uchrashuvlar",
        glyph = "◕",
        palette = StagePalettes.pregnancy,
    ),
    Postpartum(
        title = "Tug'ruqdan keyin",
        subtitle = "Tiklanish, emizish, kayfiyat",
        glyph = "◑",
        palette = StagePalettes.postpartum,
    ),
    Perimenopause(
        title = "Perimenopauza",
        subtitle = "Simptomlar, uyqu, energiya",
        glyph = "◒",
        palette = StagePalettes.perimenopause,
    ),
    Menopause(
        title = "Menopauza",
        subtitle = "Salomatlik maqsadlari",
        glyph = "◐",
        palette = StagePalettes.menopause,
    );

    /** Stages that never show a cycle-day prediction. */
    val predictsCycle: Boolean
        get() = this == Cycle || this == TryingToConceive
}

/** The four phases of a menstrual cycle, used to colour the calendar. */
enum class CyclePhase(
    val label: String,
    /** The "Bugun" card's first line: how likely conception is in this phase. */
    val fertilityNote: String,
    /** The card's second line: what the body is usually doing. */
    val energyNote: String,
) {
    Period(
        "Hayz",
        "Homiladorlik ehtimoli past",
        "Tanangiz dam olmoqda — o'zingizga yumshoq bo'ling.",
    ),
    Follicular(
        "Follikulyar faza",
        "Homiladorlik ehtimoli past",
        "Energiya oshmoqda — yangi boshlanishlar uchun ajoyib vaqt.",
    ),
    Fertile(
        "Ovulyatsiya davri",
        "Homiladorlik ehtimoli yuqori",
        "Energiya cho'qqisida — faol kunlar uchun foydalaning.",
    ),
    Luteal(
        "Lyuteal faza",
        "Homiladorlik ehtimoli past",
        "Energiya asta pasayadi — dam olishga vaqt ajrating.",
    ),
}
