package org.example.project.i18n

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.example.project.model.AppLanguage
import org.example.project.model.LifeStage

/**
 * The interface already guarantees that every language answers every string — that is
 * why it is an interface. What it cannot guarantee is that the answer is a translation:
 * a blank, or the Uzbek pasted across, compiles perfectly well.
 */
class StringsTest {

    private val languages = listOf(StringsUz, StringsRu, StringsEn)

    /** Everything a language says, in one list, so a test can look at all of it. */
    private fun everything(t: Strings): List<String> = buildList {
        add(t.languageName.uz)
        add(t.languageName.ru)
        add(t.languageName.en)

        add(t.tabs.today)
        add(t.tabs.mind)
        add(t.tabs.nutrition)
        add(t.tabs.profile)
        LifeStage.entries.forEach { add(t.tabs.journey(it)) }
        LifeStage.entries.forEach { add(t.stages.title(it)) }

        with(t.welcome) {
            addAll(
                listOf(
                    title, subtitle, featureCycle, featureMood, featureNutrition, featureMeds,
                    featureAi, featureInsights, privacyPromise, start, haveAccount, signIn,
                ),
            )
        }
        with(t.profile) {
            addAll(
                listOf(
                    title, unnamed, sleep, medications, secretChat, insights, knowledge,
                    personalDetails, goals, lifeStage, connectedDevices, notifications,
                    privacyAndSecurity, language, theme, themeDark, themeLight, about,
                    signOut, signingOut, premiumBadge, premiumActive, premiumYearly,
                    premiumFeatureAi, premiumFeatureScanner, premiumFeatureInsights,
                    upgradeTitle, upgradeSubtitle,
                ),
            )
        }
        with(t.settings) {
            addAll(listOf(aboutTitle, version("1.0.0"), languageTitle, languageNote, languageSaveFailed))
        }
    }

    @Test
    fun `no language leaves a string blank`() {
        languages.forEach { language ->
            assertTrue(everything(language).none { it.isBlank() })
        }
    }

    @Test
    fun `a language is named in itself everywhere`() {
        languages.forEach {
            assertEquals("O'zbekcha", it.languageName.uz)
            assertEquals("Русский", it.languageName.ru)
            assertEquals("English", it.languageName.en)
        }
    }

    /**
     * Five tabs share one row, so a label that reads well in a file can still break the
     * bar. Twelve characters is what the narrowest supported width fits.
     */
    @Test
    fun `a tab label stays short enough for the bar`() {
        languages.forEach { t ->
            val labels = listOf(t.tabs.today, t.tabs.mind, t.tabs.nutrition, t.tabs.profile) +
                LifeStage.entries.map { t.tabs.journey(it) }
            labels.forEach { assertTrue(it.length <= 12, "too long for the tab bar: $it") }
        }
    }

    /**
     * The sentences, at least, must have been translated. Product names are excluded
     * on purpose: "SADORA AI" is the same word in all three.
     */
    @Test
    fun `the prose is actually translated and not the Uzbek pasted across`() {
        listOf(StringsRu, StringsEn).forEach { t ->
            assertNotEquals(StringsUz.welcome.title, t.welcome.title)
            assertNotEquals(StringsUz.welcome.subtitle, t.welcome.subtitle)
            assertNotEquals(StringsUz.welcome.privacyPromise, t.welcome.privacyPromise)
            assertNotEquals(StringsUz.profile.signOut, t.profile.signOut)
            assertNotEquals(StringsUz.settings.languageNote, t.settings.languageNote)
            assertNotEquals(StringsUz.stages.title(LifeStage.Cycle), t.stages.title(LifeStage.Cycle))
        }
    }

    @Test
    fun `every language the app offers has strings behind it`() {
        assertEquals(StringsUz, stringsFor(AppLanguage.Uz))
        assertEquals(StringsRu, stringsFor(AppLanguage.Ru))
        assertEquals(StringsEn, stringsFor(AppLanguage.En))
        // Whatever is added to the enum next needs a file here; this is what says so.
        assertEquals(3, AppLanguage.entries.size)
    }

    /**
     * The second tab is named after the stage, not after the cycle: a pregnant user
     * never reads "Sikl" — in any language.
     */
    @Test
    fun `the journey tab is named after the stage`() {
        languages.forEach { t ->
            assertNotEquals(t.tabs.journey(LifeStage.Cycle), t.tabs.journey(LifeStage.Pregnancy))
            assertNotEquals(t.tabs.journey(LifeStage.Cycle), t.tabs.journey(LifeStage.Menopause))
        }
    }
}
