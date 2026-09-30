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

    // ---- the tab bar and the patient record, copied from the client set

    /** Bugun tab — the house from the deck's tab bar. */
    val Home: ImageVector = icon("Home") {
        stroke {
            moveTo(3.6f, 11.2f); lineTo(12f, 4.2f); lineTo(20.4f, 11.2f)
        }
        stroke {
            moveTo(5.8f, 9.6f); lineTo(5.8f, 19.4f)
            lineTo(18.2f, 19.4f); lineTo(18.2f, 9.6f)
        }
        stroke {
            moveTo(10f, 19.4f); lineTo(10f, 14.6f); lineTo(14f, 14.6f); lineTo(14f, 19.4f)
        }
    }

    /** Chat — two overlapping speech bubbles: a room, not a private line. */
    val Chats: ImageVector = icon("Chats") {
        stroke {
            moveTo(3.6f, 7.6f)
            quadTo(3.6f, 5.2f, 6f, 5.2f)
            lineTo(13.2f, 5.2f)
            quadTo(15.6f, 5.2f, 15.6f, 7.6f)
            lineTo(15.6f, 12.4f)
            quadTo(15.6f, 14.8f, 13.2f, 14.8f)
            lineTo(8.4f, 14.8f)
            lineTo(5.2f, 17.6f)
            lineTo(5.2f, 14.8f)
            quadTo(3.6f, 14.6f, 3.6f, 12.4f)
            close()
        }
        stroke {
            moveTo(15.6f, 9.2f)
            lineTo(18f, 9.2f)
            quadTo(20.4f, 9.2f, 20.4f, 11.6f)
            lineTo(20.4f, 16f)
            quadTo(20.4f, 18.2f, 18.8f, 18.4f)
            lineTo(18.8f, 21f)
            lineTo(15.8f, 18.4f)
            lineTo(11.6f, 18.4f)
            quadTo(9.6f, 18.4f, 9.4f, 16.6f)
        }
    }

    /** Profil — head and shoulders. */
    val Profile: ImageVector = icon("Profile") {
        stroke {
            moveTo(12f, 4.6f)
            arcToRelative(3.7f, 3.7f, 0f, true, true, 0f, 7.4f)
            arcToRelative(3.7f, 3.7f, 0f, true, true, 0f, -7.4f)
            close()
        }
        stroke {
            moveTo(4.8f, 20.2f)
            curveToRelative(1.4f, -3.9f, 4f, -5.9f, 7.2f, -5.9f)
            reflectiveCurveToRelative(5.8f, 2f, 7.2f, 5.9f)
        }
    }

    /** Forward, and the "opens a screen" mark at the end of a settings row. */
    val ChevronRight: ImageVector = icon("ChevronRight") {
        stroke { moveTo(9.4f, 5.4f); lineTo(15.4f, 12f); lineTo(9.4f, 18.6f) }
    }

    /** Saved article. */
    val Heart: ImageVector = icon("Heart") {
        stroke {
            moveTo(12f, 20.2f)
            curveToRelative(-6.4f, -3.9f, -9f, -7.5f, -9f, -11f)
            arcToRelative(4.6f, 4.6f, 0f, false, true, 9f, -1.9f)
            arcToRelative(4.6f, 4.6f, 0f, false, true, 9f, 1.9f)
            curveToRelative(0f, 3.5f, -2.6f, 7.1f, -9f, 11f)
            close()
        }
    }

    /** Suv. */
    val Drop: ImageVector = icon("Drop") {
        stroke {
            moveTo(12f, 3.4f)
            curveToRelative(4.2f, 4.6f, 6.3f, 8.1f, 6.3f, 10.6f)
            arcToRelative(6.3f, 6.3f, 0f, false, true, -12.6f, 0f)
            curveToRelative(0f, -2.5f, 2.1f, -6f, 6.3f, -10.6f)
            close()
        }
    }

    /** Dorilar — a capsule split across the middle. */
    val Pill: ImageVector = icon("Pill") {
        stroke {
            moveTo(5.2f, 13.2f)
            lineTo(13.2f, 5.2f)
            arcToRelative(4f, 4f, 0f, false, true, 5.6f, 5.6f)
            lineTo(10.8f, 18.8f)
            arcToRelative(4f, 4f, 0f, false, true, -5.6f, -5.6f)
            close()
        }
        stroke { moveTo(9.2f, 9.2f); lineTo(14.8f, 14.8f) }
    }

    /** Kalendar. */
    val Calendar: ImageVector = icon("Calendar") {
        stroke {
            moveTo(6f, 5.6f)
            horizontalLineToRelative(12f)
            curveToRelative(1.2f, 0f, 2f, 0.8f, 2f, 2f)
            verticalLineToRelative(10.6f)
            curveToRelative(0f, 1.2f, -0.8f, 2f, -2f, 2f)
            horizontalLineToRelative(-12f)
            curveToRelative(-1.2f, 0f, -2f, -0.8f, -2f, -2f)
            verticalLineToRelative(-10.6f)
            curveToRelative(0f, -1.2f, 0.8f, -2f, 2f, -2f)
            close()
        }
        stroke {
            moveTo(4f, 9.8f); lineTo(20f, 9.8f)
            moveTo(8.4f, 3.6f); lineTo(8.4f, 7f)
            moveTo(15.6f, 3.6f); lineTo(15.6f, 7f)
        }
        dot(8.6f, 13.6f, 2f)
        dot(12f, 13.6f, 2f)
        dot(15.4f, 13.6f, 2f)
    }

    /** Ulangan qurilmalar. */
    val Watch: ImageVector = icon("Watch") {
        stroke {
            moveTo(6.4f, 8.6f)
            arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
            horizontalLineToRelative(7.2f)
            arcToRelative(2f, 2f, 0f, false, true, 2f, 2f)
            verticalLineToRelative(6.8f)
            arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
            horizontalLineToRelative(-7.2f)
            arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
            close()
        }
        stroke { moveTo(9f, 6.6f); lineTo(9.4f, 3.4f); lineTo(14.6f, 3.4f); lineTo(15f, 6.6f) }
        stroke { moveTo(9f, 17.4f); lineTo(9.4f, 20.6f); lineTo(14.6f, 20.6f); lineTo(15f, 17.4f) }
    }

    /** Skan — a viewfinder's four corners around a QR code's finder squares. */
    val Scan: ImageVector = icon("Scan") {
        stroke { moveTo(3.8f, 8.2f); lineTo(3.8f, 5.4f); quadTo(3.8f, 3.8f, 5.4f, 3.8f); lineTo(8.2f, 3.8f) }
        stroke { moveTo(15.8f, 3.8f); lineTo(18.6f, 3.8f); quadTo(20.2f, 3.8f, 20.2f, 5.4f); lineTo(20.2f, 8.2f) }
        stroke { moveTo(20.2f, 15.8f); lineTo(20.2f, 18.6f); quadTo(20.2f, 20.2f, 18.6f, 20.2f); lineTo(15.8f, 20.2f) }
        stroke { moveTo(8.2f, 20.2f); lineTo(5.4f, 20.2f); quadTo(3.8f, 20.2f, 3.8f, 18.6f); lineTo(3.8f, 15.8f) }
        stroke { moveTo(7.6f, 7.6f); lineTo(10.6f, 7.6f); lineTo(10.6f, 10.6f); lineTo(7.6f, 10.6f); close() }
        stroke { moveTo(13.4f, 7.6f); lineTo(16.4f, 7.6f); lineTo(16.4f, 10.6f); lineTo(13.4f, 10.6f); close() }
        stroke { moveTo(7.6f, 13.4f); lineTo(10.6f, 13.4f); lineTo(10.6f, 16.4f); lineTo(7.6f, 16.4f); close() }
        dot(14.2f, 14.2f, 1.8f)
        dot(16.2f, 16.2f, 1.8f)
    }

    /** Ma'lumot — a circle with an "i": the patient's page from a consultation. */
    val Info: ImageVector = icon("Info") {
        stroke {
            moveTo(3.6f, 12f)
            arcToRelative(8.41f, 8.41f, 0f, false, true, 16.8f, 0f)
            arcToRelative(8.41f, 8.41f, 0f, false, true, -16.8f, 0f)
            close()
        }
        stroke { moveTo(12f, 11f); lineTo(12f, 16.4f) }
        dot(12f, 7.8f, 2f)
    }

    /** Tayyor javob — a lightning bolt: an answer in one tap. */
    val Bolt: ImageVector = icon("Bolt") {
        stroke {
            moveTo(13.2f, 3.4f)
            lineTo(5.6f, 13.4f)
            lineTo(11.6f, 13.4f)
            lineTo(10.8f, 20.6f)
            lineTo(18.4f, 10.6f)
            lineTo(12.4f, 10.6f)
            close()
        }
    }

    /** Daromad — a wallet with its clasp. */
    val Wallet: ImageVector = icon("Wallet") {
        stroke {
            moveTo(5.6f, 6.6f)
            horizontalLineToRelative(12.8f)
            arcToRelative(1.8f, 1.8f, 0f, false, true, 1.8f, 1.8f)
            verticalLineToRelative(9.2f)
            arcToRelative(1.8f, 1.8f, 0f, false, true, -1.8f, 1.8f)
            horizontalLineToRelative(-12.8f)
            arcToRelative(1.8f, 1.8f, 0f, false, true, -1.8f, -1.8f)
            verticalLineToRelative(-9.2f)
            arcToRelative(1.8f, 1.8f, 0f, false, true, 1.8f, -1.8f)
            close()
        }
        stroke { moveTo(6.4f, 6.6f); lineTo(15.4f, 3.8f); lineTo(16.2f, 6.6f) }
        stroke { moveTo(20.2f, 10.6f); lineTo(15.8f, 10.6f); arcToRelative(2.5f, 2.5f, 0f, false, false, 0f, 4.8f); lineTo(20.2f, 15.4f) }
        dot(15.9f, 13f, 1.6f)
    }

    /** Uch nuqta — "yana" menyusi. */
    val More: ImageVector = icon("More") {
        dot(6f, 12f, 2.6f)
        dot(12f, 12f, 2.6f)
        dot(18f, 12f, 2.6f)
    }
}
