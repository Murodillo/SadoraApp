package uz.sadora.doctor.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The SADORA icon set — section "IKONKA" of the design.
 *
 * Drawn as vectors rather than shipped as images for three reasons that matter on
 * this screen: an icon is tinted from [SadoraColors] so it follows the theme and the
 * selected state, it stays sharp at any density on both platforms, and one stroke
 * width across the whole set is what makes a set look like a set.
 *
 * Every icon is a 24x24 outline on a 1.7dp round-capped stroke. Nothing is filled,
 * so an icon never competes with the content beside it.
 */
object SadoraIcons {
    // The client app's set, cut down to the icons the doctor app draws. A new one is
    // copied over from sadora-client's design/Icons.kt rather than drawn afresh.

    private const val SIZE = 24f
    private const val STROKE = 1.7f

    /** Builds a 24x24 outline icon; [draw] receives a builder already set to stroke. */
    private fun icon(name: String, draw: PathScope.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = SIZE,
            viewportHeight = SIZE,
        ).apply { PathScope(this).draw() }.build()

    /** Thin wrapper so each icon body reads as a list of strokes. */
    class PathScope(private val builder: ImageVector.Builder) {
        /**
         * A filled dot, drawn as a round-capped stroke of almost no length. Two
         * semicircular arcs would be the obvious way, but an exact semicircle is an
         * ambiguous arc and renders as nothing.
         */
        fun dot(x: Float, y: Float, diameter: Float) {
            builder.path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = diameter,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(x, y)
                lineTo(x + 0.01f, y)
            }
        }

        fun stroke(pathData: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit) {
            builder.path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = pathData,
            )
        }
    }

    /** Back. A chevron rather than a full arrow — it sits inside a round button. */
    val ChevronLeft: ImageVector = icon("ChevronLeft") {
        stroke { moveTo(14.6f, 5.4f); lineTo(8.6f, 12f); lineTo(14.6f, 18.6f) }
    }

    /** Send, in the AI composer. */
    val ArrowUp: ImageVector = icon("ArrowUp") {
        stroke { moveTo(12f, 19.4f); lineTo(12f, 5f) }
        stroke { moveTo(5.8f, 11.2f); lineTo(12f, 5f); lineTo(18.2f, 11.2f) }
    }

    /** Add. */
    val Plus: ImageVector = icon("Plus") {
        stroke { moveTo(12f, 5.2f); lineTo(12f, 18.8f) }
        stroke { moveTo(5.2f, 12f); lineTo(18.8f, 12f) }
    }

    /** Done. */
    val Check: ImageVector = icon("Check") {
        stroke { moveTo(4.8f, 12.6f); lineTo(9.8f, 17.4f); lineTo(19.2f, 6.6f) }
    }

    /** Hujjatlar. */
    val Document: ImageVector = icon("Document") {
        stroke {
            moveTo(13.4f, 3.4f)
            horizontalLineTo(7.2f)
            arcToRelative(1.8f, 1.8f, 0f, false, false, -1.8f, 1.8f)
            verticalLineToRelative(13.6f)
            arcToRelative(1.8f, 1.8f, 0f, false, false, 1.8f, 1.8f)
            horizontalLineToRelative(9.6f)
            arcToRelative(1.8f, 1.8f, 0f, false, false, 1.8f, -1.8f)
            verticalLineTo(9f)
            close()
        }
        stroke { moveTo(13.4f, 3.4f); lineTo(13.4f, 9f); lineTo(18.6f, 9f) }
        stroke { moveTo(8.8f, 13.4f); lineTo(15.2f, 13.4f) }
        stroke { moveTo(8.8f, 16.6f); lineTo(13.2f, 16.6f) }
    }

    /** Settings: a gear, for the bell screen's way into its switches. */
    val Settings: ImageVector = icon("Settings") {
        stroke {
            moveTo(12.00f, 5.40f); lineTo(13.68f, 3.57f); lineTo(15.29f, 4.05f); lineTo(15.67f, 6.51f); lineTo(16.67f, 7.33f); lineTo(19.15f, 7.22f); lineTo(19.95f, 8.71f); lineTo(18.47f, 10.71f); lineTo(18.60f, 12.00f); lineTo(20.43f, 13.68f); lineTo(19.95f, 15.29f); lineTo(17.49f, 15.67f); lineTo(16.67f, 16.67f); lineTo(16.78f, 19.15f); lineTo(15.29f, 19.95f); lineTo(13.29f, 18.47f); lineTo(12.00f, 18.60f); lineTo(10.32f, 20.43f); lineTo(8.71f, 19.95f); lineTo(8.33f, 17.49f); lineTo(7.33f, 16.67f); lineTo(4.85f, 16.78f); lineTo(4.05f, 15.29f); lineTo(5.53f, 13.29f); lineTo(5.40f, 12.00f); lineTo(3.57f, 10.32f); lineTo(4.05f, 8.71f); lineTo(6.51f, 8.33f); lineTo(7.33f, 7.33f); lineTo(7.22f, 4.85f); lineTo(8.71f, 4.05f); lineTo(10.71f, 5.53f); close()
        }
        stroke {
            moveTo(9.2f, 12f)
            arcToRelative(2.8f, 2.8f, 0f, false, true, 5.6f, 0f)
            arcToRelative(2.8f, 2.8f, 0f, false, true, -5.6f, 0f)
            close()
        }
    }

    /** Til. */
    val Globe: ImageVector = icon("Globe") {
        stroke {
            moveTo(4.2f, 12f)
            arcToRelative(7.8f, 7.8f, 0f, false, true, 15.6f, 0f)
            arcToRelative(7.8f, 7.8f, 0f, false, true, -15.6f, 0f)
            close()
        }
        stroke { moveTo(4.6f, 9.4f); lineTo(19.4f, 9.4f) }
        stroke { moveTo(4.6f, 14.6f); lineTo(19.4f, 14.6f) }
        stroke {
            moveTo(12f, 4.2f)
            curveToRelative(-4.4f, 4.6f, -4.4f, 11f, 0f, 15.6f)
            curveToRelative(4.4f, -4.6f, 4.4f, -11f, 0f, -15.6f)
            close()
        }
    }

    /** Izoh — a speech bubble with its tail on the lower left. */
    val Message: ImageVector = icon("Message") {
        stroke {
            moveTo(4f, 6.4f)
            quadTo(4f, 4f, 6.4f, 4f)
            lineTo(17.6f, 4f)
            quadTo(20f, 4f, 20f, 6.4f)
            lineTo(20f, 14.4f)
            quadTo(20f, 16.8f, 17.6f, 16.8f)
            lineTo(9.6f, 16.8f)
            lineTo(5.6f, 20.2f)
            lineTo(5.6f, 16.8f)
            quadTo(4f, 16.8f, 4f, 14.4f)
            close()
        }
    }

    /** Empty state — an outline waiting to be filled. */
    val Empty: ImageVector = icon("Empty") {
        stroke {
            moveTo(12f, 3.8f)
            arcToRelative(8.2f, 8.2f, 0f, true, true, 0f, 16.4f)
            arcToRelative(8.2f, 8.2f, 0f, true, true, 0f, -16.4f)
            close()
        }
        stroke {
            moveTo(12f, 8.4f); lineTo(12f, 15.6f)
            moveTo(8.4f, 12f); lineTo(15.6f, 12f)
        }
    }

    /** A document photo, from the gallery. */
    val Camera: ImageVector = icon("Camera") {
        stroke {
            moveTo(5.4f, 8.2f)
            horizontalLineToRelative(2.6f)
            lineTo(9.6f, 5.6f)
            horizontalLineToRelative(4.8f)
            lineTo(16f, 8.2f)
            horizontalLineToRelative(2.6f)
            curveToRelative(1.2f, 0f, 2f, 0.8f, 2f, 2f)
            verticalLineToRelative(7.4f)
            curveToRelative(0f, 1.2f, -0.8f, 2f, -2f, 2f)
            horizontalLineToRelative(-13.2f)
            curveToRelative(-1.2f, 0f, -2f, -0.8f, -2f, -2f)
            verticalLineToRelative(-7.4f)
            curveToRelative(0f, -1.2f, 0.8f, -2f, 2f, -2f)
            close()
        }
        stroke {
            moveTo(12f, 10.6f)
            arcToRelative(3.1f, 3.1f, 0f, true, true, 0f, 6.2f)
            arcToRelative(3.1f, 3.1f, 0f, true, true, 0f, -6.2f)
            close()
        }
    }

    /** Xavfsiz maydon — a shield with a tick. */
    val Shield: ImageVector = icon("Shield") {
        stroke {
            moveTo(12f, 3.6f)
            lineTo(19f, 6.4f)
            verticalLineToRelative(5.2f)
            curveToRelative(0f, 4.4f, -3f, 7.6f, -7f, 8.8f)
            curveToRelative(-4f, -1.2f, -7f, -4.4f, -7f, -8.8f)
            verticalLineToRelative(-5.2f)
            close()
        }
        stroke {
            moveTo(9f, 12.2f); lineTo(11.2f, 14.4f); lineTo(15.2f, 9.8f)
        }
    }
}
