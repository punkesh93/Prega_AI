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

/**
 * Abstract line-art mother, side profile: an open head arc, a low bun, one
 * back curve, and a generous belly sweep with a small heart where the baby
 * is. Six curves total — the references' single-line hand-drawn language,
 * not anatomy.
 *
 * The exact control points were prototyped as raster renders and visually
 * judged across two iterations before landing here (v1's belly was too
 * small to read "pregnant"; v2 fixed it). Draw at 160dp+ on cream; [line]
 * defaults to warm umber, the heart to rose.
 */
@Composable
fun MotherLineArt(
    modifier: Modifier = Modifier,
    line: Color = Color(0xFF7A6554),
    heart: Color = Color(0xFFD87A84),
) {
    Canvas(modifier) {
        val s = size.minDimension
        val ox = (size.width - s) / 2f
        val oy = (size.height - s) / 2f
        fun p(x: Float, y: Float) = Offset(ox + x * s, oy + y * s)
        val stroke = Stroke(width = (s * 0.019f).coerceAtLeast(3f), cap = StrokeCap.Round)

        // Head: open arc facing right, gap at the chin-front.
        val headC = p(0.46f, 0.14f)
        val r = 0.095f * s
        drawArc(
            color = line,
            startAngle = -60f,
            sweepAngle = 295f,
            useCenter = false,
            topLeft = Offset(headC.x - r, headC.y - r),
            size = androidx.compose.ui.geometry.Size(2 * r, 2 * r),
            style = stroke,
        )
        // Low bun nestled at the back of the head.
        val bunC = Offset(headC.x - r * 1.12f, headC.y + r * 0.15f)
        val br = r * 0.38f
        drawCircle(color = line, radius = br, center = bunC, style = stroke)

        // Back: nape flowing down, then the seat curve forward.
        drawPath(
            Path().apply {
                moveTo(p(0.375f, 0.225f).x, p(0.375f, 0.225f).y)
                cubicTo(
                    p(0.335f, 0.36f).x, p(0.335f, 0.36f).y,
                    p(0.355f, 0.50f).x, p(0.355f, 0.50f).y,
                    p(0.335f, 0.62f).x, p(0.335f, 0.62f).y,
                )
                cubicTo(
                    p(0.32f, 0.76f).x, p(0.32f, 0.76f).y,
                    p(0.42f, 0.84f).x, p(0.42f, 0.84f).y,
                    p(0.56f, 0.85f).x, p(0.56f, 0.85f).y,
                )
            },
            color = line, style = stroke,
        )

        // Front: chin -> chest dip -> the belly sweep -> under-belly.
        drawPath(
            Path().apply {
                moveTo(p(0.515f, 0.235f).x, p(0.515f, 0.235f).y)
                cubicTo(
                    p(0.545f, 0.29f).x, p(0.545f, 0.29f).y,
                    p(0.50f, 0.325f).x, p(0.50f, 0.325f).y,
                    p(0.505f, 0.365f).x, p(0.505f, 0.365f).y,
                )
                cubicTo(
                    p(0.70f, 0.40f).x, p(0.70f, 0.40f).y,
                    p(0.76f, 0.58f).x, p(0.76f, 0.58f).y,
                    p(0.63f, 0.70f).x, p(0.63f, 0.70f).y,
                )
                cubicTo(
                    p(0.565f, 0.76f).x, p(0.565f, 0.76f).y,
                    p(0.575f, 0.79f).x, p(0.575f, 0.79f).y,
                    p(0.56f, 0.85f).x, p(0.56f, 0.85f).y,
                )
            },
            color = line, style = stroke,
        )

        // The baby: a small filled heart inside the bump.
        val h = p(0.615f, 0.53f)
        val hs = 0.036f * s
        val heartPath = Path()
        for (i in 0..59) {
            val t = i / 59f * 2f * Math.PI.toFloat()
            val x = 16f * Math.sin(t.toDouble()).toFloat().let { it * it * it }
            val y = (13f * Math.cos(t.toDouble()) - 5f * Math.cos(2.0 * t) -
                2f * Math.cos(3.0 * t) - Math.cos(4.0 * t)).toFloat()
            val px = h.x + x * hs / 16f
            val py = h.y - y * hs / 16f
            if (i == 0) heartPath.moveTo(px, py) else heartPath.lineTo(px, py)
        }
        heartPath.close()
        drawPath(heartPath, color = heart)
    }
}

/**
 * A short trail of baby footprints — two pairs, alternating and slightly
 * rotated, each print a sole plus three toes. For the Kicks screen's empty
 * state, where "no sessions yet" can be an invitation instead of a blank.
 */
@Composable
fun FootprintTrail(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        fun print(cx: Float, cy: Float, scale: Float, angle: Float, mirror: Float) {
            rotate(angle, pivot = Offset(cx, cy)) {
                drawOval(
                    color = color,
                    topLeft = Offset(cx - 0.055f * w * scale, cy - 0.085f * w * scale),
                    size = androidx.compose.ui.geometry.Size(0.11f * w * scale, 0.17f * w * scale),
                )
                for (i in 0..2) {
                    drawCircle(
                        color = color,
                        radius = 0.016f * w * scale,
                        center = Offset(
                            cx + mirror * (i - 1) * 0.038f * w * scale,
                            cy - 0.115f * w * scale - (if (i == 1) 0.012f * w * scale else 0f),
                        ),
                    )
                }
            }
        }
        print(w * 0.22f, h * 0.72f, 1f, -14f, 1f)
        print(w * 0.44f, h * 0.42f, 1f, -6f, -1f)
        print(w * 0.66f, h * 0.66f, 0.92f, 8f, 1f)
        print(w * 0.86f, h * 0.34f, 0.92f, 14f, -1f)
    }
}
