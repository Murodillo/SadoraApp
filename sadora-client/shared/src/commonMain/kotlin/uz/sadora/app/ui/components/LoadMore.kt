package uz.sadora.app.ui.components

import uz.sadora.app.i18n.strings
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.Spacing

/**
 * The end of a list that has more on the server: a small spinner that asks for the next
 * page as soon as the list draws it.
 *
 * Asks again whenever [loadedKey] changes — the count, or the edge item's id — so a page
 * too short to push it off screen brings the one after it, and a list that is scrolled
 * nowhere near its end never asks at all: a lazy list does not compose what it does not show.
 */
/**
 * Names a spinner for a screen reader: a bare progress indicator is announced as nothing,
 * so she could not tell that the app was busy rather than empty (WCAG 4.1.3).
 */
@Composable
fun Modifier.loadingSemantics(): Modifier {
    val label = strings.common.loading
    return semantics { contentDescription = label }
}

@Composable
fun LoadMoreRow(loadedKey: Any, onLoadMore: suspend () -> Unit, modifier: Modifier = Modifier) {
    LaunchedEffect(loadedKey) { onLoadMore() }
    Box(modifier.fillMaxWidth().padding(vertical = Spacing.xs), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(20.dp).loadingSemantics(), color = Sadora.colors.primary, strokeWidth = 2.dp)
    }
}
