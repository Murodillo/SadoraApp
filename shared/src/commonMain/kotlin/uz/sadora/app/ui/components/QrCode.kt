package uz.sadora.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import io.github.alexzhirkevich.qrose.options.QrBallShape
import io.github.alexzhirkevich.qrose.options.QrBrush
import io.github.alexzhirkevich.qrose.options.QrFrameShape
import io.github.alexzhirkevich.qrose.options.QrPixelShape
import io.github.alexzhirkevich.qrose.options.circle
import io.github.alexzhirkevich.qrose.options.roundCorners
import io.github.alexzhirkevich.qrose.options.solid
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import uz.sadora.app.design.Radius
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.Spacing

/**
 * A QR code on a white card.
 *
 * White whatever the theme: a phone camera reads dark-on-light, and a lavender or navy
 * ground behind the modules costs contrast a scanner at arm's length does not have.
 * The modules take the brand's ink so the code reads as ours, but never a light colour.
 */
@Composable
fun QrCode(
    data: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val ink = Sadora.colors.textAccent
    val painter = rememberQrCodePainter(data) {
        shapes {
            ball = QrBallShape.circle()
            frame = QrFrameShape.roundCorners(0.25f)
            darkPixel = QrPixelShape.roundCorners()
        }
        colors {
            dark = QrBrush.solid(ink)
            light = QrBrush.solid(Color.White)
        }
    }
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(Radius.card)
            .background(Color.White)
            .padding(Spacing.md),
    ) {
        Image(painter, contentDescription = contentDescription, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
    }
}
