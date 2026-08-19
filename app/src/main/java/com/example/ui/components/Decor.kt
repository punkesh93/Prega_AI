package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
 * Abstract line-art mother, side profile: an open head arc, a low bun at the
 * nape, one long back curve to the seat, a chin-to-chest step, and a full
 * round belly with a small heart where the baby is.
 *
 * Second-generation geometry (2026-08): the original app curves had drifted
 * — floating head, colliding bun (user screenshot) — so this is the refined
 * figure designed for the website hero, ported 1:1. Control points live in
 * the same 200x220 art space as the website SVG (docs/index.html) so the two
 * stay in lockstep; the space is uniformly mapped into the canvas square.
 * The port was verified the standard way: the exact draw calls simulated as
 * a raster render and visually inspected before landing (which caught a
 * mis-copied bun coordinate and an oversized heart on the first pass).
 *
 * The head arc's center/angles were derived numerically from the SVG arc
 * "M100 58 A19 19 0 1 0 76 55": center (89.82, 41.96), start 57.6°, sweep
 * -280.9° (counter-clockwise), leaving the gap at the lower back for the bun.
 *
 * [pulseHeart] gives the baby's heart a slow, calm beat — scale 1→1.06 over
 * ~2.6s, nothing bouncy. Pass false for static contexts.
 */
@Composable
fun MotherLineArt(
    modifier: Modifier = Modifier,
    line: Color = Color(0xFF7A6554),
    heart: Color = Color(0xFFD87A84),
    pulseHeart: Boolean = true,
) {
    val heartScale = if (pulseHeart) {
        val transition = rememberInfiniteTransition(label = "motherHeart")
        transition.animateFloat(
            initialValue = 1f,
            targetValue = 1.06f,
            animationSpec = infiniteRepeatable(
                animation = tween(2600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "motherHeartScale",
        ).value
    } else 1f

    Canvas(modifier) {
        val s = size.minDimension
        val ox = (size.width - s) / 2f
        val oy = (size.height - s) / 2f
        // Uniform map of the shared 200x220 art space into the canvas square,
        // content centred (art-space centroid ~ (81, 95)).
        val k = s / 220f
        fun p(x: Float, y: Float) = Offset(ox + (0.5f - 81f / 220f) * s + x * k,
                                           oy + (0.5f - 95f / 220f) * s + y * k)
        val stroke = Stroke(width = (s * 0.021f).coerceAtLeast(3f), cap = StrokeCap.Round)

        // Head: open arc, gap at the lower back where the bun sits.
        val headC = p(89.82f, 41.96f)
        val r = 19f * k
        drawArc(
            color = line,
            startAngle = 57.6f,
            sweepAngle = -280.9f,
            useCenter = false,
            topLeft = Offset(headC.x - r, headC.y - r),
            size = androidx.compose.ui.geometry.Size(2 * r, 2 * r),
            style = stroke,
        )
        // Low bun at the nape.
        drawCircle(
            color = line,
            radius = 8f * k,
            center = p(112f, 69f),
            style = Stroke(width = stroke.width * 0.9f, cap = StrokeCap.Round),
        )

        // Back: one long curve, nape to seat, then rounding forward.
        drawPath(
            Path().apply {
                moveTo(p(104f, 78f).x, p(104f, 78f).y)
                cubicTo(
                    p(113f, 98f).x, p(113f, 98f).y,
                    p(122f, 118f).x, p(122f, 118f).y,
                    p(120f, 140f).x, p(120f, 140f).y,
                )
                cubicTo(
                    p(118f, 158f).x, p(118f, 158f).y,
                    p(106f, 166f).x, p(106f, 166f).y,
                    p(86f, 167f).x, p(86f, 167f).y,
                )
            },
            color = line, style = stroke,
        )

        // Front: chin -> chest, then the belly sweep, then under to the seat.
        drawPath(
            Path().apply {
                moveTo(p(72f, 62f).x, p(72f, 62f).y)
                cubicTo(
                    p(68f, 70f).x, p(68f, 70f).y,
                    p(71f, 78f).x, p(71f, 78f).y,
                    p(69f, 85f).x, p(69f, 85f).y,
                )
                cubicTo(
                    p(42f, 94f).x, p(42f, 94f).y,
                    p(32f, 126f).x, p(32f, 126f).y,
                    p(47f, 150f).x, p(47f, 150f).y,
                )
                cubicTo(
                    p(55f, 160f).x, p(55f, 160f).y,
                    p(68f, 166f).x, p(68f, 166f).y,
                    p(86f, 167f).x, p(86f, 167f).y,
                )
            },
            color = line, style = stroke,
        )

        // The baby: a small filled heart centred in the bump, gently beating.
        val h = p(61f, 114.5f)
        val hs = 13f * k * heartScale
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
