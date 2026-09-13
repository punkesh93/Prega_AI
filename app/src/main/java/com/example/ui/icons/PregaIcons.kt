package com.example.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Prega AI — an icon language of its own.
 *
 * Sixteen surfaces plus two drawer rows, drawn on a 24-grid with a 1.75
 * round stroke in the same hand as the logo's line-art mother. They
 * replace the stock Material icons (florist, timeline, heart, sparkle,
 * person) and the emoji the drawer used: a brand you can recognise at
 * 24 px, in both themes, without a picture.
 *
 * Built from SVG path data at first use (PathParser) so the source of
 * truth is one string per shape — the same strings the approved design
 * sheet was rendered from. `bold` is the selected-tab weight: the same
 * drawing at 2.25 rather than a filled twin, because these shapes are
 * lines, not glyphs. Paths are black; Icon() tints them.
 */
object PregaIcons {
    val Today: ImageVector by lazy {
        icon("Today", 1.75f,
            Seg("M3 16h18", filled = false),
            Seg("M6.5 16a5.5 5.5 0 0 1 11 0", filled = false),
            Seg("M12 5v2M5.5 8.5l1.4 1.4M18.5 8.5l-1.4 1.4", filled = false),
        )
    }

    val TodayBold: ImageVector by lazy {
        icon("TodayBold", 2.25f,
            Seg("M3 16h18", filled = false),
            Seg("M6.5 16a5.5 5.5 0 0 1 11 0", filled = false),
            Seg("M12 5v2M5.5 8.5l1.4 1.4M18.5 8.5l-1.4 1.4", filled = false),
        )
    }

    val Journey: ImageVector by lazy {
        icon("Journey", 1.75f,
            Seg("M4 19c4 0 4-5 8-5s4-5 8-5", filled = false),
            Seg("M4 12c3 0 3-3 6-3", filled = false),
            Seg("M10.4 14a1.6 1.6 0 1 0 3.2 0a1.6 1.6 0 1 0 -3.2 0", filled = true),
        )
    }

    val JourneyBold: ImageVector by lazy {
        icon("JourneyBold", 2.25f,
            Seg("M4 19c4 0 4-5 8-5s4-5 8-5", filled = false),
            Seg("M4 12c3 0 3-3 6-3", filled = false),
            Seg("M10.4 14a1.6 1.6 0 1 0 3.2 0a1.6 1.6 0 1 0 -3.2 0", filled = true),
        )
    }

    val Kicks: ImageVector by lazy {
        icon("Kicks", 1.75f,
            Seg("M9.5 20c-2.6 0-4-2-4-4.5 0-3 1.8-5.5 4-5.5s4 2.5 4 5.5c0 2.5-1.4 4.5-4 4.5z", filled = false),
            Seg("M6.1 7.4a1.1 1.1 0 1 0 2.2 0a1.1 1.1 0 1 0 -2.2 0", filled = true),
            Seg("M8.9 6a1.1 1.1 0 1 0 2.2 0a1.1 1.1 0 1 0 -2.2 0", filled = true),
            Seg("M12 6.6a1 1 0 1 0 2 0a1 1 0 1 0 -2 0", filled = true),
            Seg("M16.5 4c1.5 1 2.5 2.5 3 4", filled = false),
        )
    }

    val KicksBold: ImageVector by lazy {
        icon("KicksBold", 2.25f,
            Seg("M9.5 20c-2.6 0-4-2-4-4.5 0-3 1.8-5.5 4-5.5s4 2.5 4 5.5c0 2.5-1.4 4.5-4 4.5z", filled = false),
            Seg("M6.1 7.4a1.1 1.1 0 1 0 2.2 0a1.1 1.1 0 1 0 -2.2 0", filled = true),
            Seg("M8.9 6a1.1 1.1 0 1 0 2.2 0a1.1 1.1 0 1 0 -2.2 0", filled = true),
            Seg("M12 6.6a1 1 0 1 0 2 0a1 1 0 1 0 -2 0", filled = true),
            Seg("M16.5 4c1.5 1 2.5 2.5 3 4", filled = false),
        )
    }

    val Coach: ImageVector by lazy {
        icon("Coach", 1.75f,
            Seg("M5 6.5A2.5 2.5 0 0 1 7.5 4h9A2.5 2.5 0 0 1 19 6.5v7a2.5 2.5 0 0 1-2.5 2.5H11l-4 4v-4H7.5A2.5 2.5 0 0 1 5 13.5z", filled = false),
            Seg("M9.5 12.5c0-3 2-5 5.5-5 0 3-2 5-5.5 5z", filled = false),
            Seg("M9.5 12.5l4-3.5", filled = false),
        )
    }

    val CoachBold: ImageVector by lazy {
        icon("CoachBold", 2.25f,
            Seg("M5 6.5A2.5 2.5 0 0 1 7.5 4h9A2.5 2.5 0 0 1 19 6.5v7a2.5 2.5 0 0 1-2.5 2.5H11l-4 4v-4H7.5A2.5 2.5 0 0 1 5 13.5z", filled = false),
            Seg("M9.5 12.5c0-3 2-5 5.5-5 0 3-2 5-5.5 5z", filled = false),
            Seg("M9.5 12.5l4-3.5", filled = false),
        )
    }

    val You: ImageVector by lazy {
        icon("You", 1.75f,
            Seg("M12 21V11", filled = false),
            Seg("M12 14c-4 0-6-2-6-5.5 3.5 0 6 2 6 5.5z", filled = false),
            Seg("M12 11c0-4 2.5-6.5 6-6.5 0 4-2.5 6.5-6 6.5z", filled = false),
        )
    }

    val YouBold: ImageVector by lazy {
        icon("YouBold", 2.25f,
            Seg("M12 21V11", filled = false),
            Seg("M12 14c-4 0-6-2-6-5.5 3.5 0 6 2 6 5.5z", filled = false),
            Seg("M12 11c0-4 2.5-6.5 6-6.5 0 4-2.5 6.5-6 6.5z", filled = false),
        )
    }

    val Garden: ImageVector by lazy {
        icon("Garden", 1.75f,
            Seg("M9.8 12a2.2 2.2 0 1 0 4.4 0a2.2 2.2 0 1 0 -4.4 0", filled = false),
            Seg("M12 9.8c-1.5-1.5-1.5-4.5 0-6 1.5 1.5 1.5 4.5 0 6z", filled = false),
            Seg("M14.1 10.8c.5-2.1 3-3.7 5.2-3.1-.6 2.1-3 3.7-5.2 3.1z", filled = false),
            Seg("M13.3 14c2 .7 3.2 3.5 2.4 5.6-2-.7-3.2-3.5-2.4-5.6z", filled = false),
            Seg("M10.7 14c-2 .7-3.2 3.5-2.4 5.6 2-.7 3.2-3.5 2.4-5.6z", filled = false),
            Seg("M9.9 10.8c-.5-2.1-3-3.7-5.2-3.1.6 2.1 3 3.7 5.2 3.1z", filled = false),
        )
    }

    val Journal: ImageVector by lazy {
        icon("Journal", 1.75f,
            Seg("M6 3.5h8.5L19 8v12.5H6z", filled = false),
            Seg("M14.5 3.5V8H19", filled = false),
            Seg("M9 12h6M9 15.5h4", filled = false),
        )
    }

    val Appointments: ImageVector by lazy {
        icon("Appointments", 1.75f,
            Seg("M6.5 5.5h11a2.5 2.5 0 0 1 2.5 2.5v10a2.5 2.5 0 0 1-2.5 2.5h-11A2.5 2.5 0 0 1 4 18V8a2.5 2.5 0 0 1 2.5-2.5z", filled = false),
            Seg("M4 10h16M8 3.5v4M16 3.5v4", filled = false),
            Seg("M9.5 15.5c0-2 1.5-3.5 4-3.5 0 2-1.5 3.5-4 3.5z", filled = false),
        )
    }

    val Club: ImageVector by lazy {
        icon("Club", 1.75f,
            Seg("M5 10a4 4 0 1 0 8 0a4 4 0 1 0 -8 0", filled = false),
            Seg("M11 10a4 4 0 1 0 8 0a4 4 0 1 0 -8 0", filled = false),
            Seg("M12 13.4c-1.8 1.2-3 3.1-3 5.6h6c0-2.5-1.2-4.4-3-5.6z", filled = false),
        )
    }

    val Labor: ImageVector by lazy {
        icon("Labor", 1.75f,
            Seg("M14.5 3.5a7 7 0 1 0 6 10.5 6 6 0 0 1-6-10.5z", filled = false),
            Seg("M3 19c2 0 2-1.5 4-1.5s2 1.5 4 1.5 2-1.5 4-1.5", filled = false),
        )
    }

    val Contractions: ImageVector by lazy {
        icon("Contractions", 1.75f,
            Seg("M3 15c3 0 3-6 6-6s3 6 6 6 3-6 6-6", filled = false),
            Seg("M3 19h1.5M7.5 19H9M12 19h1.5M16.5 19H18M21 19h0", filled = false),
        )
    }

    val Bag: ImageVector by lazy {
        icon("Bag", 1.75f,
            Seg("M4 9.5h16l-1.2 10a2 2 0 0 1-2 1.7H7.2a2 2 0 0 1-2-1.7z", filled = false),
            Seg("M9 9.5V7a3 3 0 0 1 6 0v2.5", filled = false),
            Seg("M12 13v4M10 15h4", filled = false),
        )
    }

    val Preferences: ImageVector by lazy {
        icon("Preferences", 1.75f,
            Seg("M20 4c-6 0-11 4-13 10l-3 6", filled = false),
            Seg("M20 4c0 6-3 11-9 12", filled = false),
            Seg("M11.5 12.5l4-1M9.5 15.5l4.5-.5", filled = false),
        )
    }

    val Water: ImageVector by lazy {
        icon("Water", 1.75f,
            Seg("M12 3.5s6 6.5 6 11a6 6 0 0 1-12 0c0-4.5 6-11 6-11z", filled = false),
            Seg("M9 14.5a3 3 0 0 0 2 2.8", filled = false),
        )
    }

    val Rest: ImageVector by lazy {
        icon("Rest", 1.75f,
            Seg("M7 17.5h10a3.5 3.5 0 0 0 .5-7 5 5 0 0 0-9.6-1.3A4.2 4.2 0 0 0 7 17.5z", filled = false),
            Seg("M15 6l.6 1.4L17 8l-1.4.6L15 10l-.6-1.4L13 8l1.4-.6z", filled = true),
        )
    }

    val LittleOne: ImageVector by lazy {
        icon("LittleOne", 1.75f,
            Seg("M12 20c-4 0-6.5-2.5-6.5-6 0-3 2.5-5 5.5-5 2.3 0 4 1.6 4 3.7 0 1.7-1.3 3-3 3-1.3 0-2.2-.9-2.2-2 0-.9.7-1.5 1.5-1.5", filled = false),
            Seg("M12 9V4.5", filled = false),
            Seg("M12 6c1.5-1 3-1 4.5-.3", filled = false),
        )
    }

    val Badge: ImageVector by lazy {
        icon("Badge", 1.75f,
            Seg("M6.5 9a5.5 5.5 0 1 0 11 0a5.5 5.5 0 1 0 -11 0", filled = false),
            Seg("M8.5 13.5L7 21l5-2.5 5 2.5-1.5-7.5", filled = false),
            Seg("M10.6 9a1.4 1.4 0 1 0 2.8 0a1.4 1.4 0 1 0 -2.8 0", filled = true),
        )
    }

    val Settings: ImageVector by lazy {
        icon("Settings", 1.75f,
            Seg("M4 7h9M17 7h3M4 12h3M11 12h9M4 17h11M19 17h1", filled = false),
            Seg("M13 7a2 2 0 1 0 4 0a2 2 0 1 0 -4 0", filled = false),
            Seg("M7 12a2 2 0 1 0 4 0a2 2 0 1 0 -4 0", filled = false),
            Seg("M15 17a2 2 0 1 0 4 0a2 2 0 1 0 -4 0", filled = false),
        )
    }

    private class Seg(val d: String, val filled: Boolean)

    private fun icon(name: String, stroke: Float, vararg segs: Seg): ImageVector =
        ImageVector.Builder(
            name = "Prega.$name",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            for (s in segs) {
                val nodes = PathParser().parsePathString(s.d).toNodes()
                if (s.filled) {
                    addPath(pathData = nodes, fill = SolidColor(Color.Black))
                } else {
                    addPath(
                        pathData = nodes,
                        fill = null,
                        stroke = SolidColor(Color.Black),
                        strokeLineWidth = stroke,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round,
                    )
                }
            }
        }.build()
}
