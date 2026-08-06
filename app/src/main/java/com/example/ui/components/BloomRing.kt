package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.Motion
import com.example.ui.theme.PregaTheme
import com.example.ui.theme.Trimester1
import com.example.ui.theme.Trimester2
import com.example.ui.theme.Trimester3
import kotlin.math.cos
import kotlin.math.sin

/**
 * Prega AI — the Bloom Ring.
 *
 * Forty petals arranged in a circle, one per week of pregnancy, coloured by
 * trimester and filling as she progresses. This replaces a plain progress bar
 * as the signature representation of the journey: a bar says "62% complete",
 * which is a strange thing to tell a pregnant woman — a ring of petals says
 * "look how much has already grown".
 *
 * Weeks reached bloom at full size and colour. The current week is drawn
 * larger with a soft halo. Future weeks sit as small neutral buds — present,
 * so the shape of the whole journey is always visible, but quiet.
 */
@Composable
fun BloomRing(
    currentWeek: Int,
    modifier: Modifier = Modifier,
    size: Dp = 220.dp,
    content: @Composable () -> Unit = {},
) {
    // Sweep the bloom in on first composition rather than popping fully drawn.
    val reveal by animateFloatAsState(
        targetValue = currentWeek.coerceIn(0, 40).toFloat(),
        animationSpec = tween(Motion.Slow, easing = Motion.EaseBreath),
        label = "bloomReveal",
    )
    val budColor = PregaTheme.colors.hairline

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val centre = Offset(this.size.width / 2f, this.size.height / 2f)
            val radius = this.size.minDimension / 2f * 0.86f

            for (week in 1..40) {
                // Week 1 at the top, clockwise.
                val angle = Math.toRadians((week - 1) / 40.0 * 360.0 - 90.0)
                val pos = Offset(
                    centre.x + (cos(angle) * radius).toFloat(),
                    centre.y + (sin(angle) * radius).toFloat(),
                )

                val isCurrent = week == currentWeek
                val bloomed = week <= reveal

                val petalColor = when {
                    !bloomed -> budColor
                    week <= 13 -> Trimester1
                    week <= 27 -> Trimester2
                    else -> Trimester3
                }

                // Petals that have bloomed grow smoothly as the reveal sweeps
                // past them, so the ring appears to blossom clockwise.
                val growth = ((reveal - week + 1f).coerceIn(0f, 1f))
                val petalRadius = when {
                    isCurrent -> 7.5f
                    bloomed -> 3.5f + 1.5f * growth
                    else -> 2.2f
                }

                if (isCurrent) {
                    // Halo behind the current week.
                    drawCircle(
                        color = petalColor.copy(alpha = 0.25f),
                        radius = 14f,
                        center = pos,
                    )
                }

                drawCircle(
                    color = if (bloomed) petalColor else budColor,
                    radius = petalRadius,
                    center = pos,
                )
            }
        }

        content()
    }
}

/** Trimester boundary colours exposed for legends drawn next to the ring. */
object BloomRingColors {
    val first: Color get() = Trimester1
    val second: Color get() = Trimester2
    val third: Color get() = Trimester3
}
