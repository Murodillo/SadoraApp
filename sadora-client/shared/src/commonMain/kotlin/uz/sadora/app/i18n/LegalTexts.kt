package uz.sadora.app.i18n

import uz.sadora.app.model.AppLanguage

/** A heading followed by its paragraphs. */
data class LegalSection(val heading: String, val body: List<String>)

/**
 * The Terms of Use and the Privacy Policy, in one language.
 *
 * A separate interface from [Strings] rather than forty more lines inside it: these are
 * documents, they change as documents (all at once, with a date), and they are read on
 * exactly one screen. The shape is the same, so a section added here is still a compile
 * error in the languages that have not answered it yet.
 *
 * **The Uzbek text governs.** The other two are provided so a user can read what she is
 * agreeing to in her own language, and [translationNotice] says so on the screen — a
 * translated clause that drifts must not quietly become the obligation.
 */
interface LegalTexts {

    /** Stated, not computed: a date that moves on its own claims a change that never happened. */
    val effectiveDate: String

    /** Shown under the date in every language except Uzbek. */
    val translationNotice: String?

    val termsTitle: String
    val privacyTitle: String
    val terms: List<LegalSection>
    val privacy: List<LegalSection>

    companion object {
        fun of(language: AppLanguage): LegalTexts = when (language) {
            AppLanguage.Uz -> LegalTextsUz
            AppLanguage.Ru -> LegalTextsRu
            AppLanguage.En -> LegalTextsEn
        }
    }
}

/**
 * The date the copy below took effect.
 *
 * The server's `POLICY_VERSION` is the same day in ISO form, and it has to stay that way:
 * the consent row records the version, so a screen dated later than the version stored
 * against it would make the record say she agreed to something she never saw.
 */
const val LEGAL_EFFECTIVE_DATE: String = "2026-09-03"
