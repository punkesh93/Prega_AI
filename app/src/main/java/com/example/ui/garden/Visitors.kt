package com.example.ui.garden

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.core.content.edit
import kotlin.math.cos
import kotlin.math.sin

/**
 * Prega AI — Garden Visitors.
 *
 * The garden's second layer of life: as she keeps showing up, creatures
 * move in. Discovery is driven by TOTAL DAYS ACTIVE (distinct days with any
 * log), not by streaks — a visitor once arrived never leaves, and a broken
 * streak never scares anyone away. Same philosophy as the flowers: the
 * garden only ever gains.
 *
 * "3D" here is the dioramic illusion, built from four honest tricks:
 * two-tone bodies (deep undertone under the lit colour), a specular
 * highlight, an elliptical ground shadow that squashes as the body bobs,
 * and depth scaling — the same visual grammar as the prototype renders
 * that were eyeballed and approved before this file was written (the
 * first dragonfly was rejected as unreadable and redrawn).
 *
 * Every creature idles in its own way: the bee hovers, the snail inches,
 * the robin hops, the dragonfly darts. All of it is [t]-driven from the
 * garden's single animation clock.
 */
data class Visitor(
    val id: String,
    val name: String,
    /** Total active days needed before this visitor moves in. */
    val daysNeeded: Int,
    /** One warm line for the collection shelf. */
    val bio: String,
)

val VISITORS = listOf(
    Visitor("ladybird", "Ladybird", 2, "First to arrive. Good luck, in most languages."),
    Visitor("bee", "Bumblebee", 5, "Works gently, rests often. A role model."),
    Visitor("snail", "Snail", 9, "Slow is also a pace. Right on time, always."),
    Visitor("robin", "Robin", 14, "Sings for the garden every morning you come back."),
    Visitor("butterfly_gold", "Gold butterfly", 20, "Only settles in gardens tended with patience."),
    Visitor("dragonfly", "Dragonfly", 27, "Ancient, quick, and very fond of your flowers."),
    Visitor("frog", "Frog", 35, "Moved in by the flowers. Approves of everything."),
    Visitor("hedgehog", "Hedgehog", 44, "The garden's night guardian. Softer than it looks."),
)

// ─── Discovery persistence ─────────────────────────────────────────────────

object VisitorBook {
    private const val PREFS = "prega_visitors"
    private const val KEY = "seen_ids"

    fun seen(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY, emptySet()) ?: emptySet()

    fun markSeen(context: Context, id: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit { putStringSet(KEY, seen(context) + id) }
    }

    fun unlocked(daysActive: Int): List<Visitor> =
        VISITORS.filter { daysActive >= it.daysNeeded }

    /** The next visitor she hasn't earned yet, for the "coming soon" hint. */
    fun next(daysActive: Int): Visitor? =
        VISITORS.firstOrNull { daysActive < it.daysNeeded }
}

// ─── Drawing ───────────────────────────────────────────────────────────────

private fun Color.deep(f: Float = 0.78f) =
    Color(red * f, green * f, blue * f, alpha)

/** Soft elliptical ground shadow; [lift] 0..1 squashes it as the body rises. */
private fun DrawScope.groundShadow(x: Float, y: Float, w: Float, lift: Float) {
    drawOval(
        color = Color(0xFF5A633A).copy(alpha = 0.16f * (1f - 0.5f * lift)),
        topLeft = Offset(x - w * (1f - 0.25f * lift), y - w * 0.22f),
        size = Size(2 * w * (1f - 0.25f * lift), w * 0.44f),
    )
}

/**
 * Draws one visitor at ([x],[y]) with body size [s], animated by clock [t].
 * Each creature owns its idle: motion is part of its character.
 */
fun DrawScope.drawVisitor(id: String, x: Float, y: Float, s: Float, t: Float) {
    when (id) {
        "ladybird" -> {
            // Crawls slowly left-right along the ground.
            val cx = x + sin(t * 0.5f) * s * 1.2f
            groundShadow(cx, y + s * 0.85f, s * 1.1f, 0f)
            drawOval(Color(0xFFD65A54).deep(), Offset(cx - s, y - s * 0.78f), Size(2 * s, s * 1.56f * 0.9f))
            drawOval(Color(0xFFD65A54), Offset(cx - s * 0.94f, y - s * 0.8f), Size(s * 1.88f, s * 1.44f))
            drawCircle(Color(0xFF3A302A), s * 0.4f, Offset(cx - s * 0.8f, y - s * 0.1f))
            drawLine(Color(0xFF3A302A), Offset(cx, y - s * 0.75f), Offset(cx, y + s * 0.6f), strokeWidth = s * 0.1f)
            listOf(-0.35f to -0.3f, 0.35f to -0.35f, 0.12f to 0.22f, 0.55f to 0.1f).forEach { (dx, dy) ->
                drawCircle(Color(0xFF3A302A), s * 0.14f, Offset(cx + dx * s, y + dy * s))
            }
            drawOval(Color.White.copy(alpha = 0.45f), Offset(cx + s * 0.1f, y - s * 0.62f), Size(s * 0.5f, s * 0.26f))
        }

        "bee" -> {
            // Hovers on a small figure-eight, wings blurring.
            val bx = x + sin(t * 1.3f) * s * 0.9f
            val by = y + sin(t * 2.6f) * s * 0.5f - s * 1.6f
            groundShadow(x, y + s * 0.8f, s * 0.9f, 0.8f)
            val flap = 0.6f + 0.4f * sin(t * 22f)
            listOf(-0.4f to -1.2f, 0.45f to -1.35f).forEach { (dx, dy) ->
                drawOval(
                    Color.White.copy(alpha = 0.55f),
                    Offset(bx + dx * s - s * 0.6f, by + dy * s - s * 0.4f * flap),
                    Size(s * 1.2f, s * 0.8f * flap),
                )
            }
            drawOval(Color(0xFFD9A441).deep(), Offset(bx - s, by - s * 0.7f), Size(2 * s, s * 1.4f))
            drawOval(Color(0xFFD9A441), Offset(bx - s * 0.94f, by - s * 0.72f), Size(s * 1.88f, s * 1.3f))
            listOf(-0.45f, 0f, 0.45f).forEach { fx ->
                drawLine(Color(0xFF463A28), Offset(bx + fx * s, by - s * 0.6f), Offset(bx + fx * s, by + s * 0.6f), strokeWidth = s * 0.22f)
            }
            drawCircle(Color(0xFF463A28), s * 0.38f, Offset(bx - s * 0.9f, by))
            drawOval(Color.White.copy(alpha = 0.4f), Offset(bx + s * 0.1f, by - s * 0.5f), Size(s * 0.5f, s * 0.22f))
        }

        "snail" -> {
            // Inches forward: tiny periodic stretch.
            val stretch = 1f + 0.06f * sin(t * 0.8f)
            groundShadow(x, y + s * 0.7f, s * 1.3f, 0f)
            drawOval(Color(0xFFB0BC84), Offset(x - s * 1.3f * stretch, y + s * 0.1f), Size(s * 2.7f * stretch, s * 0.7f))
            drawOval(Color(0xFFB0BC84), Offset(x - s * 1.5f * stretch, y - s * 0.5f), Size(s * 0.6f, s * 0.9f))
            drawLine(Color(0xFF78825A), Offset(x - s * 1.35f * stretch, y - s * 0.5f), Offset(x - s * 1.5f * stretch, y - s * 0.95f), strokeWidth = s * 0.08f)
            drawLine(Color(0xFF78825A), Offset(x - s * 1.1f * stretch, y - s * 0.5f), Offset(x - s * 1.05f * stretch, y - s * 1f), strokeWidth = s * 0.08f)
            drawCircle(Color(0xFFC59678).deep(), s * 0.85f, Offset(x + s * 0.25f, y - s * 0.35f))
            drawCircle(Color(0xFFC59678), s * 0.78f, Offset(x + s * 0.2f, y - s * 0.4f))
            // Spiral hint.
            var r = s * 0.08f
            var a = 0f
            var prev = Offset(x + s * 0.2f, y - s * 0.4f)
            while (r < s * 0.66f) {
                val p = Offset(x + s * 0.2f + r * cos(a), y - s * 0.4f + r * sin(a))
                drawLine(Color(0xFF7A5A46).copy(alpha = 0.7f), prev, p, strokeWidth = s * 0.06f)
                prev = p; a += 0.55f; r += s * 0.028f
            }
            drawOval(Color.White.copy(alpha = 0.4f), Offset(x + s * 0.35f, y - s * 0.85f), Size(s * 0.5f, s * 0.26f))
        }

        "robin" -> {
            // Hops: brief bounce with rests.
            val hop = (sin(t * 1.4f).coerceAtLeast(0f)) * (sin(t * 8f).coerceAtLeast(0f)) * s * 0.5f
            val by = y - hop
            groundShadow(x, y + s * 1.35f, s * 0.85f, hop / (s * 0.5f + 0.001f))
            drawLine(Color(0xFF785A3C), Offset(x - s * 0.2f, by + s * 0.95f), Offset(x - s * 0.2f, by + s * 1.4f), strokeWidth = s * 0.09f)
            drawLine(Color(0xFF785A3C), Offset(x + s * 0.2f, by + s * 0.95f), Offset(x + s * 0.2f, by + s * 1.4f), strokeWidth = s * 0.09f)
            drawOval(Color(0xFF967860).deep(), Offset(x - s * 0.9f, by - s * 0.95f), Size(s * 1.8f, s * 2f))
            drawOval(Color(0xFF967860), Offset(x - s * 0.85f, by - s), Size(s * 1.7f, s * 1.9f))
            drawOval(Color(0xFFE29478), Offset(x - s * 0.75f, by - s * 0.25f), Size(s * 1.05f, s * 1.15f))
            drawOval(Color(0xFF967860).deep(), Offset(x + s * 0.1f, by - s * 0.45f), Size(s * 1f, s * 0.75f))
            drawCircle(Color(0xFF967860), s * 0.48f, Offset(x - s * 0.05f, by - s * 0.85f))
            drawCircle(Color(0xFF28201A), s * 0.11f, Offset(x - s * 0.22f, by - s * 0.92f))
            val beak = androidx.compose.ui.graphics.Path().apply {
                moveTo(x - s * 0.55f, by - s * 0.92f)
                lineTo(x - s * 1.05f, by - s * 0.8f)
                lineTo(x - s * 0.55f, by - s * 0.72f)
                close()
            }
            drawPath(beak, Color(0xFFD9A441))
        }

        "butterfly_gold" -> {
            val bx = x + sin(t * 0.9f) * s * 1.4f
            val by = y - s * 2f + sin(t * 1.8f) * s * 0.7f
            groundShadow(x, y + s * 0.6f, s * 0.7f, 0.9f)
            val flap = 0.55f + 0.45f * sin(t * 9f)
            listOf(-1f, 1f).forEach { sd ->
                drawOval(Color(0xFFE3B23C).copy(alpha = 0.95f), Offset(bx + sd * s * 0.15f - s * 0.55f * flap * (if (sd < 0) 1f else 0f) - (if (sd > 0) 0f else s * 0.4f), by - s * 0.7f), Size(s * (0.5f + 0.5f * flap), s * 0.9f))
                drawOval(Color(0xFFE29478).copy(alpha = 0.95f), Offset(bx + sd * s * 0.2f - (if (sd > 0) 0f else s * 0.5f), by + s * 0.05f), Size(s * (0.35f + 0.35f * flap), s * 0.6f))
            }
            drawLine(Color(0xFF5A503C), Offset(bx, by - s * 0.7f), Offset(bx, by + s * 0.65f), strokeWidth = s * 0.14f)
        }

        "dragonfly" -> {
            // Darts: fast little relocations between hovers.
            val dx = sin(t * 0.7f) * s * 2.2f + sin(t * 3.1f) * s * 0.4f
            val dyy = y - s * 2.4f + sin(t * 1.9f) * s * 0.5f
            val bx = x + dx
            groundShadow(x, y + s * 0.6f, s * 0.6f, 0.95f)
            listOf(-1f, 1f).forEach { sd ->
                rotate(-22f * sd, pivot = Offset(bx - s * 0.1f, dyy - s * 0.25f)) {
                    drawOval(Color(0xFFE8F1F4).copy(alpha = 0.65f), Offset(bx - s * 1.4f, dyy - s * 0.5f), Size(s * 2.6f, s * 0.5f))
                }
                rotate(-38f * sd, pivot = Offset(bx - s * 0.3f, dyy + s * 0.1f)) {
                    drawOval(Color(0xFFE8F1F4).copy(alpha = 0.5f), Offset(bx - s * 1.3f, dyy - s * 0.15f), Size(s * 2.1f, s * 0.42f))
                }
            }
            drawCircle(Color(0xFF7A9E94), s * 0.36f, Offset(bx - s * 0.7f, dyy))
            drawLine(Color(0xFF608078), Offset(bx - s * 0.4f, dyy), Offset(bx + s * 1.7f, dyy + s * 0.3f), strokeWidth = s * 0.26f)
            drawCircle(Color(0xFF608078), s * 0.26f, Offset(bx - s * 1.1f, dyy))
            drawCircle(Color(0xFF28201A), s * 0.1f, Offset(bx - s * 1.2f, dyy - s * 0.06f))
        }

        "frog" -> {
            // Throat bobs; occasional blink handled by the highlight fading.
            val throat = 1f + 0.05f * sin(t * 2.2f)
            groundShadow(x, y + s * 0.75f, s * 1.15f, 0f)
            drawOval(Color(0xFF8C9E60).deep(), Offset(x - s * 1.05f, y - s * 0.72f), Size(s * 2.1f, s * 1.45f * throat))
            drawOval(Color(0xFF8C9E60), Offset(x - s, y - s * 0.78f), Size(s * 2f, s * 1.35f * throat))
            listOf(-1f, 1f).forEach { sd ->
                drawCircle(Color(0xFF8C9E60), s * 0.3f, Offset(x + sd * s * 0.5f, y - s * 0.68f))
                drawCircle(Color(0xFF28201A), s * 0.12f, Offset(x + sd * s * 0.5f, y - s * 0.72f))
            }
            drawArc(
                Color(0xFF46502D), 25f, 130f, false,
                topLeft = Offset(x - s * 0.45f, y - s * 0.2f),
                size = Size(s * 0.9f, s * 0.55f),
                style = Stroke(width = s * 0.09f),
            )
            drawOval(Color.White.copy(alpha = 0.35f), Offset(x - s * 0.55f, y - s * 0.5f), Size(s * 0.6f, s * 0.28f))
        }

        "hedgehog" -> {
            // Breathes; spikes sway a whisper.
            val br = 1f + 0.02f * sin(t * 1.6f)
            groundShadow(x, y + s * 0.7f, s * 1.25f, 0f)
            for (i in 0..25) {
                val a = (0.15f + 0.7f * i / 25f) * Math.PI.toFloat() + 0.02f * sin(t + i)
                drawLine(
                    Color(0xFF7A6554).deep(),
                    Offset(x + s * 0.1f, y),
                    Offset(x + s * 0.1f + s * 1.25f * cos(a) * br, y - s * 1.15f * sin(a) * br),
                    strokeWidth = s * 0.14f,
                )
            }
            drawOval(Color(0xFF7A6554), Offset(x - s * 0.9f, y - s * 0.65f), Size(s * 2f, s * 1.3f))
            drawOval(Color(0xFFC4A68A), Offset(x - s * 1.25f, y - s * 0.1f), Size(s * 0.8f, s * 0.55f))
            drawCircle(Color(0xFF3A302A), s * 0.11f, Offset(x - s * 1.2f, y + s * 0.16f))
            drawCircle(Color(0xFF28201A), s * 0.09f, Offset(x - s * 0.78f, y - s * 0.02f))
        }
    }
}
