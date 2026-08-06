package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate

/**
 * Prega AI — abstract calm decor.
 *
 * The reference designs get their softness from two devices: overlapping
 * organic pastel blobs, and thin line-art plant sprigs. Both are pure
 * geometry, which means they can ship as code — resolution-independent,
 * theme-aware, zero asset files — unlike the figurative watercolor
 * illustrations (week fruits) which genuinely need image assets.
 *
 * Rules of use:
 *  - Decor sits BEHIND content, never between her and a tap target.
 *  - Alpha stays low (≤ 0.5 for blobs, the sprig is hairline) so text
 *    contrast is never in question.
 *  - At most one decorated card per screen region — decoration everywhere
 *    is wallpaper, not calm.
 */

/**
 * Two overlapping soft circles bleeding off a card corner — the references'
 * background-blob move. Draw inside a clipped card, sized to the card.
 */
@Composable
fun CornerBlobs(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        drawCircle(
            color = tint.copy(alpha = 0.35f),
            radius = size.minDimension * 0.55f,
            center = Offset(size.width * 0.92f, size.height * 0.12f),
        )
        drawCircle(
            color = tint.copy(alpha = 0.22f),
            radius = size.minDimension * 0.38f,
            center = Offset(size.width * 0.74f, size.height * 0.34f),
        )
    }
}

/**
 * A thin line-art sprig: one curved stem with small filled leaves — a
 * eucalyptus gesture, same as the references' hand-drawn plants. Geometry was
 * prototyped and visually verified as a raster render before porting here
 * (stroke-only leaves read as barbs; filled ovals read as leaves).
 */
@Composable
fun LeafSprig(
    color: Color,
    modifier: Modifier = Modifier,
    rotationDegrees: Float = 0f,
) {
    Canvas(modifier) {
        rotate(rotationDegrees, pivot = Offset(size.width / 2f, size.height / 2f)) {
            val w = size.width
            val h = size.height
            val stroke = Stroke(width = (h * 0.022f).coerceAtLeast(2f), cap = StrokeCap.Round)

            // Stem: a single gentle S-curve from bottom-left to top-right.
            val stem = Path().apply {
                moveTo(w * 0.18f, h * 0.92f)
                cubicTo(w * 0.30f, h * 0.55f, w * 0.55f, h * 0.45f, w * 0.80f, h * 0.10f)
            }
            drawPath(stem, color = color, style = stroke)

            // Leaves: small filled ovals sweeping up and out from the stem,
            // alternating sides.
            val anchors = listOf(
                Triple(0.24f, 0.76f, -1f),
                Triple(0.34f, 0.60f, 1f),
                Triple(0.46f, 0.49f, -1f),
                Triple(0.60f, 0.37f, 1f),
                Triple(0.71f, 0.24f, -1f),
            )
            anchors.forEach { (ax, ay, side) ->
                val cx = ax * w + side * 0.07f * w
                val cy = ay * h - 0.05f * h
                val angle = if (side > 0) -125f else -35f
                rotate(angle, pivot = Offset(cx, cy)) {
                    drawOval(
                        color = color,
                        topLeft = Offset(cx - 0.08f * w, cy - 0.028f * w),
                        size = androidx.compose.ui.geometry.Size(0.16f * w, 0.055f * w),
                    )
                }
            }
        }
    }
}
