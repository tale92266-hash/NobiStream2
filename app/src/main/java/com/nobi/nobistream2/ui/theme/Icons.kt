package com.nobi.nobistream2.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * The HTML redesign's EXACT inline SVG paths, ported as Compose
 * ImageVectors (24x24 viewport). Every path is drawn in white; callers
 * tint via Icon(tint = ...). No emoji icons anywhere, no other icon set
 * for these shapes. Stroke icons use stroke width 2, round caps/joins
 * unless the HTML says otherwise.
 */
object NsIcons {

    private fun strokeIcon(
        name: String,
        strokeWidth: Float = 2f,
        vararg paths: String
    ): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        for (d in paths) {
            addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                pathFillType = PathFillType.NonZero,
                stroke = SolidColor(Color.White),
                strokeLineWidth = strokeWidth,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
        }
    }.build()

    private fun fillIcon(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            for (d in paths) {
                addPath(
                    pathData = PathParser().parsePathString(d).toNodes(),
                    pathFillType = PathFillType.NonZero,
                    fill = SolidColor(Color.White)
                )
            }
        }.build()

    /** White play triangle (logo mark, Resume, play FAB). */
    val Play: ImageVector = fillIcon(
        "NsPlay",
        "M7 4.8v14.4c0 .9 1 1.5 1.8 1L20 13a1.2 1.2 0 0 0 0-2L8.8 3.9C8 3.4 7 4 7 4.8z"
    )

    /** Pause bars. */
    val Pause: ImageVector = fillIcon(
        "NsPause",
        "M6 4.5h4.4v15H6zM13.6 4.5H18v15h-4.4z"
    )

    val Plus: ImageVector = strokeIcon("NsPlus", 2.4f, "M12 5v14M5 12h14")

    val Refresh: ImageVector = strokeIcon(
        "NsRefresh", 2f,
        "M21 12a9 9 0 1 1-2.6-6.3M21 4v5h-5"
    )

    val Link: ImageVector = strokeIcon(
        "NsLink", 2f,
        "M10 14a5 5 0 0 0 7 0l3-3a5 5 0 0 0-7-7l-1.7 1.7",
        "M14 10a5 5 0 0 0-7 0l-3 3a5 5 0 0 0 7 7l1.7-1.7"
    )

    val ChevronRight: ImageVector = strokeIcon("NsChevronRight", 2.2f, "M9 5l7 7-7 7")

    val Home: ImageVector = strokeIcon(
        "NsHome", 2f,
        "M4 11.5L12 4l8 7.5V20a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 20z",
        "M9.5 21v-6h5v6"
    )

    val Search: ImageVector = strokeIcon(
        "NsSearch", 2.2f,
        "M18 11a7 7 0 1 1-14 0 7 7 0 0 1 14 0z",
        "M20 20l-3.8-3.8"
    )

    /** Library tab: rounded rect + play triangle (HTML nav icon). */
    val Library: ImageVector = strokeIcon(
        "NsLibrary", 2f,
        "M6 5h12a3 3 0 0 1 3 3v8a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V8a3 3 0 0 1 3-3z",
        "M10.5 9.7l4.5 2.3-4.5 2.3z"
    )

    /** Settings gear (HTML nav icon, filled). */
    val Settings: ImageVector = fillIcon(
        "NsSettings",
        "M19.14 12.94c.04-.3.06-.61.06-.94s-.02-.64-.07-.94l2.03-1.58a.49.49 0 0 0 .12-.61l-1.92-3.32a.488.488 0 0 0-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54a.484.484 0 0 0-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58a.49.49 0 0 0-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6a3.6 3.6 0 1 1 0-7.2 3.6 3.6 0 0 1 0 7.2z"
    )

    /** Sort sliders icon (Library header). */
    val Sliders: ImageVector = strokeIcon(
        "NsSliders", 2f,
        "M4 7h10M18 7h2M4 12h4M12 12h8M4 17h13M20 17h0",
        "M18 7a2 2 0 1 1-4 0 2 2 0 0 1 4 0z",
        "M12 12a2 2 0 1 1-4 0 2 2 0 0 1 4 0z",
        "M20.5 17a2 2 0 1 1-4 0 2 2 0 0 1 4 0z"
    )

    val Check: ImageVector = strokeIcon("NsCheck", 3f, "M4 12.5l5 5L20 6.5")

    /** Option-sheet tick (12px, stroke 3.2). */
    val Tick: ImageVector = strokeIcon("NsTick", 3.2f, "M4 12.5l5 5L20 6.5")

    val Close: ImageVector = strokeIcon("NsClose", 2.4f, "M6 6l12 12M18 6L6 18")

    val SkipBack10: ImageVector = fillIcon(
        "NsSkipBack10",
        "M12 5V1.8L6.8 6 12 10.2V7a5.5 5.5 0 1 1-5.5 5.5H4A8 8 0 1 0 12 5z"
    )

    val SkipForward10: ImageVector = fillIcon(
        "NsSkipForward10",
        "M12 5V1.8L17.2 6 12 10.2V7a5.5 5.5 0 1 0 5.5 5.5H20A8 8 0 1 1 12 5z"
    )

    /** Previous episode (filled). */
    val Previous: ImageVector = fillIcon(
        "NsPrevious",
        "M6 5v14M19 5l-9.5 7L19 19z"
    )

    /** Subtitles (CC) — rect + two arcs. */
    val Captions: ImageVector = strokeIcon(
        "NsCaptions", 2f,
        "M6 5h12a3 3 0 0 1 3 3v8a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V8a3 3 0 0 1 3-3z",
        "M10.5 10.2a2.4 2.4 0 1 0 0 3.6M17.5 10.2a2.4 2.4 0 1 0 0 3.6"
    )

    val Lock: ImageVector = strokeIcon(
        "NsLock", 2f,
        "M7 11h10a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2z",
        "M8 11V8a4 4 0 0 1 7.8-1.3"
    )

    val Fullscreen: ImageVector = strokeIcon(
        "NsFullscreen", 2f,
        "M4 9V4h5M20 9V4h-5M4 15v5h5M20 15v5h-5"
    )

    /** Empty-state art: play inside a rounded rect. */
    val PlayInRect: ImageVector = strokeIcon(
        "NsPlayInRect", 1.7f,
        "M6 5h12a3 3 0 0 1 3 3v8a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V8a3 3 0 0 1 3-3z",
        "M10 9.5l5 2.5-5 2.5z"
    )

    /** Filter lines (Smart Filter / Default quality rows). */
    val FilterLines: ImageVector = strokeIcon("NsFilterLines", 2f, "M4 6h16M7 12h10M10 18h4")

    /** Subtitle size (text-size icon). */
    val TextSize: ImageVector = strokeIcon(
        "NsTextSize", 2f,
        "M5 18V7l4 7 4-7v11M16 18h4M18 14v4"
    )

    /** Subtitle colour (half-filled circle). */
    val ColourHalf: ImageVector = strokeIcon(
        "NsColourHalf", 2f,
        "M20 12a8 8 0 1 1-16 0 8 8 0 0 1 16 0z",
        "M12 4a8 8 0 0 1 0 16z"
    )

    /** Subtitle background (rect + underline). */
    val SubBackground: ImageVector = strokeIcon(
        "NsSubBackground", 2f,
        "M6 6h12a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2z",
        "M7 15h10"
    )
}
