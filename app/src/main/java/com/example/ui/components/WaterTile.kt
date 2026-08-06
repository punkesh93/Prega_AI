package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.example.ui.theme.Motion
import com.example.ui.theme.PregaTheme
import com.example.ui.theme.Space
import kotlin.math.sin

/**
 * Prega AI — the water tile.
 *
 * The whole tile is a vessel: each tap raises an actual liquid level with a
 * gently undulating surface, so progress toward the day's water goal is felt
 * rather than read. This is deliberately the most playful tracker in the app —
 * hydration is the one habit with zero anxiety attached, so it can afford
 * delight that the kick counter, for instance, must not have.
 *
 * The wave is two sine layers moving at different speeds, which is the cheapest
 * thing that reads as liquid rather than as a rising rectangle.
 */
@Composable
fun WaterTile(
    glasses: Int,
    goal: Int,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = Motion.tactile(),
        label = "waterPress",
    )

    // The level rises with a slow settle, like water actually poured in.
    val level by animateFloatAsState(
        targetValue = (glasses.toFloat() / goal).coerceIn(0f, 1f),
        animationSpec = tween(Motion.Slow, easing = Motion.EaseBreath),
        label = "waterLevel",
    )

    // Two wave phases; the surface only moves while there is water to move.
    val transition = rememberInfiniteTransition(label = "waves")
    val phase1 by transition.animateFloat(
        initialValue = 0f, targetValue = 6.283f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing)),
        label = "wave1",
    )
    val phase2 by transition.animateFloat(
        initialValue = 0f, targetValue = 6.283f,
        animationSpec = infiniteRepeatable(tween(5100, easing = LinearEasing)),
        label = "wave2",
    )

    val waterDeep = PregaTheme.colors.lavender
    val waterLight = PregaTheme.colors.lavenderSoft
    val goalMet = glasses >= goal

    Box(
        modifier
            .scale(pressScale)
            .clip(MaterialTheme.shapes.large)
            .background(PregaTheme.colors.recessed)
            .clickable(interactionSource = interaction, indication = null) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onTap()
            }
            .heightIn(min = 128.dp),
    ) {
        // ── The water ──
        Canvas(Modifier.fillMaxSize()) {
            if (level <= 0f) return@Canvas
            val surfaceY = size.height * (1f - level)
            val amp1 = 5f * (1f - level * 0.5f)   // calmer as it fills
            val amp2 = 3f

            fun wavePath(phase: Float, amplitude: Float, lift: Float): Path =
                Path().apply {
                    moveTo(0f, surfaceY + lift)
                    var x = 0f
                    while (x <= size.width) {
                        lineTo(
                            x,
                            surfaceY + lift +
                                sin(phase + (x / size.width) * 6.283f * 1.5f) * amplitude,
                        )
                        x += 12f
                    }
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }

            // Back layer: lighter, slower, slightly higher — gives depth.
            drawPath(wavePath(phase2, amp2, -4f), color = waterLight.copy(alpha = 0.8f))
            drawPath(wavePath(phase1, amp1, 0f), color = waterDeep.copy(alpha = 0.45f))
        }

        // ── The reading ──
        Column(Modifier.padding(Space.lg)) {
            Text(
                "Water",
                style = MaterialTheme.typography.bodySmall,
                color = PregaTheme.colors.inkMuted,
            )
            Spacer(Modifier.height(Space.xxs))
            Text(
                "$glasses / $goal",
                style = MaterialTheme.typography.headlineMedium,
                color = PregaTheme.colors.ink,
            )
            Spacer(Modifier.weight(1f))
            Text(
                when {
                    goalMet -> "Goal reached"
                    glasses == 0 -> "Tap to log a glass"
                    else -> "Tap for another",
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (goalMet) PregaTheme.colors.sage else PregaTheme.colors.inkFaint,
            )
        }
    }
}
