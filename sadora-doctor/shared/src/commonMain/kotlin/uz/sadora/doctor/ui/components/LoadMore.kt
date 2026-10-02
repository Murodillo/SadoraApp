package uz.sadora.doctor.ui.components

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
import uz.sadora.doctor.design.Sadora
import uz.sadora.doctor.design.Spacing

/**
 * The end of a list that has more on the server: a small spinner that asks for the next
 * page as soon as the list draws it.
 *
 * Asks again whenever [loadedKey] changes — the count of what is loaded — so a page too
 * short to push it off screen brings the one after it, and a list scrolled nowhere near
 * its end never asks: a lazy list does not compose what it does not show.
 */
@Composable
fun LoadMoreRow(loadedKey: Any, onLoadMore: suspend () -> Unit, modifier: Modifier = Modifier) {
    LaunchedEffect(loadedKey) { onLoadMore() }
    Box(modifier.fillMaxWidth().padding(vertical = Spacing.xs), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(20.dp), color = Sadora.colors.primary, strokeWidth = 2.dp)
    }
}
