package org.example.project.design

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

    // ---------------------------------------------------------------- tabs

    /**
     * Bugun — a sun just clear of the horizon.
     *
     * The screen greets the user by time of day, so the tab is the day itself
     * rather than a house or a calendar page.
     */
    val Today: ImageVector = icon("Today") {
        stroke {
            // horizon
            moveTo(3.2f, 17.6f); lineTo(20.8f, 17.6f)
        }
        stroke {
            // sun
            moveTo(8.2f, 17.6f)
            arcToRelative(3.8f, 3.8f, 0f, true, true, 7.6f, 0f)
        }
        stroke {
            moveTo(12f, 4.2f); lineTo(12f, 6.1f)
            moveTo(4.9f, 7.3f); lineTo(6.2f, 8.6f)
            moveTo(19.1f, 7.3f); lineTo(17.8f, 8.6f)
            moveTo(2.9f, 13.6f); lineTo(4.7f, 13.6f)
            moveTo(21.1f, 13.6f); lineTo(19.3f, 13.6f)
        }
    }

    /**
     * Yo'l — a ring that does not quite close, with the moon's crescent inside.
     *
     * A closed circle would read as "complete"; the cycle is continuous, and the
     * gap is where the next one begins.
     */
    val Journey: ImageVector = icon("Journey") {
        stroke {
            moveTo(4f, 12f)
            arcToRelative(8f, 8f, 0f, false, true, 16f, 0f)
            arcToRelative(8f, 8f, 0f, false, true, -16f, 0f)
            close()
        }
        // The bead on the ring is where today sits in the cycle.
        dot(16f, 5.1f, 4.2f)
    }

    /** AI — a four-point sparkle with a smaller one trailing it. */
    val Sparkle: ImageVector = icon("Sparkle") {
        stroke {
            moveTo(10.3f, 3.6f)
            curveToRelative(0f, 3.4f, 1.6f, 5.1f, 4.8f, 5.1f)
            curveToRelative(-3.2f, 0f, -4.8f, 1.7f, -4.8f, 5.1f)
            curveToRelative(0f, -3.4f, -1.6f, -5.1f, -4.8f, -5.1f)
            curveToRelative(3.2f, 0f, 4.8f, -1.7f, 4.8f, -5.1f)
            close()
        }
        stroke {
            moveTo(17.2f, 13.4f)
            curveToRelative(0f, 2.1f, 1f, 3.1f, 3f, 3.1f)
            curveToRelative(-2f, 0f, -3f, 1f, -3f, 3.1f)
            curveToRelative(0f, -2.1f, -1f, -3.1f, -3f, -3.1f)
            curveToRelative(2f, 0f, 3f, -1f, 3f, -3.1f)
            close()
        }
    }

    /** Ovqat — a leaf with its midrib. */
    val Nutrition: ImageVector = icon("Nutrition") {
        stroke {
            moveTo(20f, 4f)
            curveToRelative(0.9f, 8.2f, -4.2f, 13.4f, -11.4f, 12.6f)
            curveToRelative(-3.6f, -0.4f, -4.8f, -3.5f, -3.3f, -6.6f)
            curveToRelative(1.7f, -3.6f, 7.4f, -6.6f, 14.7f, -6f)
            close()
        }
        stroke {
            moveTo(4.6f, 20.4f)
            curveToRelative(1.6f, -4.4f, 4.6f, -7.7f, 9f, -9.9f)
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

    // ---------------------------------------------------------------- content

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

    /** Uyqu. */
    val Moon: ImageVector = icon("Moon") {
        stroke {
            moveTo(20.2f, 14.4f)
            arcToRelative(8.6f, 8.6f, 0f, true, true, -10.6f, -10.6f)
            arcToRelative(7f, 7f, 0f, false, false, 10.6f, 10.6f)
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

    /** Ong — a bloom, four petals around a still centre. */
    val Bloom: ImageVector = icon("Bloom") {
        stroke {
            moveTo(12f, 3.6f)
            curveToRelative(2.6f, 1.9f, 3.5f, 4.3f, 2.6f, 7.2f)
            curveToRelative(2.9f, -0.9f, 5.3f, 0f, 7.2f, 2.6f)
            curveToRelative(-2.6f, 1.9f, -5f, 1.9f, -7.2f, 0f)
            curveToRelative(0.9f, 2.9f, 0f, 5.3f, -2.6f, 7.2f)
            curveToRelative(-2.6f, -1.9f, -3.5f, -4.3f, -2.6f, -7.2f)
            curveToRelative(-2.2f, 1.9f, -4.6f, 1.9f, -7.2f, 0f)
            curveToRelative(1.9f, -2.6f, 4.3f, -3.5f, 7.2f, -2.6f)
            curveToRelative(-0.9f, -2.9f, 0f, -5.3f, 2.6f, -7.2f)
            close()
        }
    }

    /** Taxminiy — a clock, for anything the app predicts rather than records. */
    val Clock: ImageVector = icon("Clock") {
        stroke {
            moveTo(4.2f, 12f)
            arcToRelative(7.8f, 7.8f, 0f, false, true, 15.6f, 0f)
            arcToRelative(7.8f, 7.8f, 0f, false, true, -15.6f, 0f)
            close()
        }
        stroke {
            moveTo(12f, 7.4f); lineTo(12f, 12f); lineTo(15.2f, 13.8f)
        }
    }


    // ---------------------------------------------------------------- navigation

    /** Back. A chevron rather than a full arrow — it sits inside a round button. */
    val ChevronLeft: ImageVector = icon("ChevronLeft") {
        stroke { moveTo(14.6f, 5.4f); lineTo(8.6f, 12f); lineTo(14.6f, 18.6f) }
    }

    /** Forward, and the "opens a screen" mark at the end of a settings row. */
    val ChevronRight: ImageVector = icon("ChevronRight") {
        stroke { moveTo(9.4f, 5.4f); lineTo(15.4f, 12f); lineTo(9.4f, 18.6f) }
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

    /** Edit, log by hand. */
    val Pencil: ImageVector = icon("Pencil") {
        stroke {
            moveTo(16.4f, 3.9f)
            lineTo(20.1f, 7.6f)
            lineTo(8.5f, 19.2f)
            lineTo(4f, 20f)
            lineTo(4.8f, 15.5f)
            close()
        }
        stroke { moveTo(14.2f, 6.1f); lineTo(17.9f, 9.8f) }
    }

    /** Search. */
    val Search: ImageVector = icon("Search") {
        stroke {
            moveTo(4.6f, 10.9f)
            arcToRelative(6.3f, 6.3f, 0f, false, true, 12.6f, 0f)
            arcToRelative(6.3f, 6.3f, 0f, false, true, -12.6f, 0f)
            close()
        }
        stroke { moveTo(15.5f, 15.5f); lineTo(19.8f, 19.8f) }
    }

    // ---------------------------------------------------------------- profile

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

    /** Tahlillar — three bars, tallest last. */
    val Chart: ImageVector = icon("Chart") {
        stroke { moveTo(4.4f, 19.6f); lineTo(19.6f, 19.6f) }
        stroke { moveTo(7.6f, 19.6f); lineTo(7.6f, 13.4f) }
        stroke { moveTo(12f, 19.6f); lineTo(12f, 9f) }
        stroke { moveTo(16.4f, 19.6f); lineTo(16.4f, 5.4f) }
    }

    /** Bilim. */
    val Book: ImageVector = icon("Book") {
        stroke {
            moveTo(12f, 6.6f)
            curveToRelative(-1.8f, -1.6f, -4f, -2.4f, -6.6f, -2.4f)
            verticalLineToRelative(13f)
            curveToRelative(2.6f, 0f, 4.8f, 0.8f, 6.6f, 2.4f)
            curveToRelative(1.8f, -1.6f, 4f, -2.4f, 6.6f, -2.4f)
            verticalLineToRelative(-13f)
            curveToRelative(-2.6f, 0f, -4.8f, 0.8f, -6.6f, 2.4f)
            close()
        }
        stroke { moveTo(12f, 6.6f); lineTo(12f, 19.6f) }
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

    /** Maqsadlar. */
    val Target: ImageVector = icon("Target") {
        stroke {
            moveTo(4.2f, 12f)
            arcToRelative(7.8f, 7.8f, 0f, false, true, 15.6f, 0f)
            arcToRelative(7.8f, 7.8f, 0f, false, true, -15.6f, 0f)
            close()
        }
        stroke {
            moveTo(8.6f, 12f)
            arcToRelative(3.4f, 3.4f, 0f, false, true, 6.8f, 0f)
            arcToRelative(3.4f, 3.4f, 0f, false, true, -6.8f, 0f)
            close()
        }
        dot(12f, 12f, 2.6f)
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

    /** Bildirishnomalar. */
    val Bell: ImageVector = icon("Bell") {
        stroke {
            moveTo(18.2f, 16.6f)
            horizontalLineTo(5.8f)
            curveToRelative(1.2f, -1.2f, 1.8f, -2.4f, 1.8f, -3.6f)
            verticalLineToRelative(-2.6f)
            arcToRelative(4.4f, 4.4f, 0f, false, true, 8.8f, 0f)
            verticalLineToRelative(2.6f)
            curveToRelative(0f, 1.2f, 0.6f, 2.4f, 1.8f, 3.6f)
            close()
        }
        stroke { moveTo(10.2f, 19.4f); arcToRelative(1.9f, 1.9f, 0f, false, false, 3.6f, 0f) }
    }

    /** Maxfiylik va xavfsizlik. */
    val Lock: ImageVector = icon("Lock") {
        stroke {
            moveTo(5.6f, 12.4f)
            arcToRelative(1.6f, 1.6f, 0f, false, true, 1.6f, -1.6f)
            horizontalLineToRelative(9.6f)
            arcToRelative(1.6f, 1.6f, 0f, false, true, 1.6f, 1.6f)
            verticalLineToRelative(6.4f)
            arcToRelative(1.6f, 1.6f, 0f, false, true, -1.6f, 1.6f)
            horizontalLineToRelative(-9.6f)
            arcToRelative(1.6f, 1.6f, 0f, false, true, -1.6f, -1.6f)
            close()
        }
        stroke {
            moveTo(8.4f, 10.8f)
            verticalLineToRelative(-2.8f)
            arcToRelative(3.6f, 3.6f, 0f, false, true, 7.2f, 0f)
            verticalLineToRelative(2.8f)
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

    /** Saqlangan — a bookmark ribbon with the usual notch. */
    val Bookmark: ImageVector = icon("Bookmark") {
        stroke {
            moveTo(6.4f, 3.6f)
            lineTo(17.6f, 3.6f)
            lineTo(17.6f, 20.4f)
            lineTo(12f, 15.8f)
            lineTo(6.4f, 20.4f)
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

    /** SADORA haqida. */
    val Info: ImageVector = icon("Info") {
        stroke {
            moveTo(4.2f, 12f)
            arcToRelative(7.8f, 7.8f, 0f, false, true, 15.6f, 0f)
            arcToRelative(7.8f, 7.8f, 0f, false, true, -15.6f, 0f)
            close()
        }
        stroke { moveTo(12f, 11f); lineTo(12f, 16.4f) }
        dot(12f, 7.9f, 2f)
    }

    /** Ulashish. */
    val Share: ImageVector = icon("Share") {
        stroke {
            moveTo(12f, 15.4f); lineTo(12f, 3.6f)
            moveTo(8.2f, 7.4f); lineTo(12f, 3.6f); lineTo(15.8f, 7.4f)
        }
        stroke {
            moveTo(5.6f, 11.6f)
            curveToRelative(-0.9f, 0f, -1.4f, 0.6f, -1.4f, 1.5f)
            verticalLineToRelative(6.2f)
            curveToRelative(0f, 0.9f, 0.5f, 1.5f, 1.4f, 1.5f)
            horizontalLineToRelative(12.8f)
            curveToRelative(0.9f, 0f, 1.4f, -0.6f, 1.4f, -1.5f)
            verticalLineToRelative(-6.2f)
            curveToRelative(0f, -0.9f, -0.5f, -1.5f, -1.4f, -1.5f)
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
}
