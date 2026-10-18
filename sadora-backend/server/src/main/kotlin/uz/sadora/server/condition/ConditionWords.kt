package uz.sadora.server.condition

import uz.sadora.contract.ConditionInfo
import uz.sadora.contract.HealthCondition
import uz.sadora.contract.Language

/**
 * What each condition is, in three languages. Server-side so the wording is one place —
 * the app and the doctor's page read the same sentences.
 *
 * The prevalence figures are WHO's (PCOS: 8–13% of women of reproductive age; endometriosis:
 * about 10%). Nothing here interprets her data or suggests a treatment.
 */
object ConditionWords {

    fun info(condition: HealthCondition, language: Language): ConditionInfo = when (condition) {
        HealthCondition.PCOS -> ConditionInfo(
            condition = condition,
            name = pick(language, "Polikistoz tuxumdon sindromi (PCOS)", "Синдром поликистозных яичников (СПКЯ)", "Polycystic ovary syndrome (PCOS)"),
            about = pick(
                language,
                "Gormonal holat: tsikl tartibsiz yoki uzun bo'ladi, ovulyatsiya kam bo'ladi, ba'zan ortiqcha tuk, husnbuzar yoki vazn ortishi bilan kechadi. WHO ma'lumotiga ko'ra, reproduktiv yoshdagi ayollarning 8–13 foizida uchraydi.",
                "Гормональное состояние: цикл нерегулярный или длинный, овуляция редкая, иногда избыточный рост волос, акне или прибавка веса. По данным ВОЗ, встречается у 8–13% женщин репродуктивного возраста.",
                "A hormonal condition: cycles are irregular or long, ovulation is infrequent, and there may be extra hair growth, acne or weight gain. WHO puts it at 8–13% of women of reproductive age.",
            ),
            tracks = pick(
                language,
                "Tartibsiz tsikl uchun prognoz sana oralig'i bilan beriladi. Kundalikda ortiqcha tuk, soch to'kilishi va husnbuzarni, alohida vazningizni qayd qilasiz.",
                "Для нерегулярного цикла прогноз даётся диапазоном дат. В дневнике отмечаете избыточный рост волос, выпадение волос и акне, отдельно — вес.",
                "The forecast becomes a date range for an irregular cycle. The daily log takes excess hair, hair loss and acne, and your weight is tracked too.",
            ),
            suspectedNote = pick(
                language,
                "PCOS tashxisini faqat shifokor qo'yadi — odatda tsikl, gormon tahlillari va UZI asosida. Ilova belgilaringizni yig'ib, shifokorga ko'rsatishga yordam beradi.",
                "Диагноз СПКЯ ставит только врач — обычно по циклу, гормональным анализам и УЗИ. Приложение собирает ваши признаки, чтобы их было удобно показать врачу.",
                "Only a doctor diagnoses PCOS — usually from the cycle, hormone tests and an ultrasound. The app gathers your signs so they are easy to show one.",
            ),
        )
        HealthCondition.ENDOMETRIOSIS -> ConditionInfo(
            condition = condition,
            name = pick(language, "Endometrioz", "Эндометриоз", "Endometriosis"),
            about = pick(
                language,
                "Bachadon ichki qavatiga o'xshash to'qima undan tashqarida o'sadi. Ko'pincha kuchli hayz og'rig'i, jinsiy aloqada, hojatxonada yoki doimiy tos og'rig'i bilan kechadi. WHO ma'lumotiga ko'ra, reproduktiv yoshdagi ayollarning taxminan 10 foizida uchraydi.",
                "Ткань, похожая на внутренний слой матки, растёт за её пределами. Часто это сильные боли при месячных, при близости, в туалете или постоянная тазовая боль. По данным ВОЗ, встречается примерно у 10% женщин репродуктивного возраста.",
                "Tissue like the lining of the womb grows outside it. It often means severe period pain, pain during sex or on the toilet, or constant pelvic pain. WHO puts it at about 10% of women of reproductive age.",
            ),
            tracks = pick(
                language,
                "Og'riq kundaligi: kuchi, joyi, qachon og'rigani, dori va kundalik ishlaringizga ta'siri — shifokorga eng kerakli ma'lumot.",
                "Дневник боли: сила, место, когда болело, лекарства и влияние на обычные дела — то, что важнее всего врачу.",
                "A pain diary: how bad, where, when it hurt, painkillers, and what it stopped you doing — what a doctor needs most.",
            ),
            suspectedNote = pick(
                language,
                "Endometrioz tashxisi ko'pincha yillab kechikadi. Og'riq kundaligini 2–3 tsikl yuriting va ginekologga ko'rsating.",
                "Диагноз эндометриоза часто ставят с опозданием на годы. Ведите дневник боли 2–3 цикла и покажите его гинекологу.",
                "Endometriosis is often diagnosed years late. Keep the pain diary for 2–3 cycles and show it to a gynaecologist.",
            ),
        )
    }

    private fun pick(language: Language, uz: String, ru: String, en: String) = when (language) {
        Language.UZ -> uz
        Language.RU -> ru
        Language.EN -> en
    }
}
