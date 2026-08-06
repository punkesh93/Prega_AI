package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import com.example.ui.theme.PregaTheme
import kotlin.math.sin
import kotlin.random.Random

/**
 * Prega AI — ambient petal drift.
 *
 * A sparse layer of soft petals falling very slowly behind hero content. This
 * is the app's signature ambient moment — used on the onboarding welcome and
 * the badge reveal, and nowhere else. Ambience that appears on every screen
 * stops being ambience and starts being noise.
 *
 * Tuning principles:
 *  - SLOW. A petal takes ~18s to cross the screen. Anything faster reads as
 *    weather, not calm.
 *  - SPARSE. Twelve petals, not fifty.
 *  - QUIET. Alpha tops out at 0.35 so text always wins.
 *  - The sway is a gentle sine, phase-offset per petal so no two move in step.
 */
@Composable
fun PetalDrift(
    modifier: Modifier = Modifier,
    petalCount: Int = 12,
    tint: Color? = null,
) {
    val color = tint ?: PregaTheme.colors.gradientBloom.first()

    val petals = remember {
        List(petalCount) {
            Petal(
                x = Random.nextFloat(),
                // Stagger starting heights so the sky is never empty and never
                // starts with a synchronised curtain-drop.
                phase = Random.nextFloat(),
                size = 8f + Random.nextFloat() * 10f,
                swayAmplitude = 14f + Random.nextFloat() * 22f,
                swaySpeed = 0.6f + Random.nextFloat() * 0.8f,
                spin = Random.nextFloat() * 360f,
                alpha = 0.16f + Random.nextFloat() * 0.19f,
            )
        }
    }

    val transition = rememberInfiniteTransition(label = "petals")
    // One shared clock; each petal derives its own position from its phase.
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18_000, easing = LinearEasing),
        ),
        label = "petalClock",
    )

    Canvas(modifier.fillMaxSize()) {
        petals.forEach { p ->
            val progress = (t + p.phase) % 1f
            val y = progress * (size.height + 60f) - 30f
            val sway = sin(progress * 6.283f * p.swaySpeed * 3f) * p.swayAmplitude
            val x = p.x * size.width + sway

            translate(left = x, top = y) {
                rotate(degrees = p.spin + progress * 180f, pivot = Offset.Zero) {
                    // A petal: two overlapping soft ovals, cheaper than a path
                    // and reads correctly at this size and alpha.
                    drawOval(
                        color = color.copy(alpha = p.alpha),
                        topLeft = Offset(-p.size / 2f, -p.size / 3.2f),
                        size = androidx.compose.ui.geometry.Size(p.size, p.size / 1.6f),
                    )
                    drawOval(
                        color = color.copy(alpha = p.alpha * 0.7f),
                        topLeft = Offset(-p.size / 3.2f, -p.size / 2f),
                        size = androidx.compose.ui.geometry.Size(p.size / 1.6f, p.size),
                    )
                }
            }
        }
    }
}

private data class Petal(
    val x: Float,
    val phase: Float,
    val size: Float,
    val swayAmplitude: Float,
    val swaySpeed: Float,
    val spin: Float,
    val alpha: Float,
)
