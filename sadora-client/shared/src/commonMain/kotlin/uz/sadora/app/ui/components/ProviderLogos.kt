package uz.sadora.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import uz.sadora.app.design.Radius
import uz.sadora.app.design.Sadora
import uz.sadora.contract.HealthProvider

/**
 * A provider's mark on the devices screen: its brand colour with its initial.
 *
 * The vendors' own logo files take this place once they are in the project; until then
 * the colour is what tells the tiles apart at a glance.
 */
@Composable
fun ProviderLogo(provider: HealthProvider, size: Dp, modifier: Modifier = Modifier) {
    val (background, initial) = brandOf(provider)
    Box(
        modifier
            .size(size)
            .clip(Radius.chip)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initial,
            style = Sadora.type.h2.copy(fontWeight = FontWeight.Bold, fontSize = (size.value * 0.42f).sp),
            color = Color.White,
        )
    }
}

private fun brandOf(provider: HealthProvider): Pair<Color, String> = when (provider) {
    HealthProvider.WHOOP -> Color(0xFF111111) to "W"
    HealthProvider.OURA -> Color(0xFF2B2B2E) to "O"
    HealthProvider.GARMIN -> Color(0xFF007CC3) to "G"
    HealthProvider.FITBIT -> Color(0xFF00B0B9) to "F"
    HealthProvider.SAMSUNG_HEALTH -> Color(0xFF1428A0) to "S"
    HealthProvider.APPLE_HEALTH -> Color(0xFFFF2D55) to "♥"
    HealthProvider.HEALTH_CONNECT -> Color(0xFF3A7BEA) to "H"
    else -> Color(0xFF7B61FF) to provider.name.take(1)
}
