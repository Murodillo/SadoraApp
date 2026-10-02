package uz.sadora.app.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import uz.sadora.app.model.AppLanguage

/** The strings for one language. Uzbek is the fallback, and the reference translation. */
fun stringsFor(language: AppLanguage): Strings = when (language) {
    AppLanguage.Uz -> StringsUz
    AppLanguage.Ru -> StringsRu
    AppLanguage.En -> StringsEn
}

/**
 * The language the screens read from.
 *
 * Static rather than dynamic: the language changes a handful of times in a lifetime,
 * and when it does every screen should recompose anyway.
 *
 * The default is Uzbek so a `@Preview` or a test that renders a screen on its own still
 * gets words rather than a crash.
 */
val LocalStrings = staticCompositionLocalOf<Strings> { StringsUz }

/** `val t = strings` at the top of a screen, the way `val c = Sadora.colors` reads. */
val strings: Strings
    @Composable @ReadOnlyComposable get() = LocalStrings.current

/** Wraps [content] in one language. [App] does this once, around the whole tree. */
@Composable
fun ProvideStrings(language: AppLanguage, content: @Composable () -> Unit) {
    SideEffect { RequestLanguage.tag = language.code.lowercase() }
    CompositionLocalProvider(LocalStrings provides stringsFor(language), content = content)
}

/**
 * The language every API request asks the server to answer in, as `Accept-Language`.
 *
 * The HTTP client lives outside composition, so [ProvideStrings] copies the screens'
 * language here whenever it changes; a refusal the server words — a field's reason, a
 * wrong code — then arrives in the language she is reading, not in Uzbek.
 */
object RequestLanguage {
    var tag: String = "uz"
        internal set
}
