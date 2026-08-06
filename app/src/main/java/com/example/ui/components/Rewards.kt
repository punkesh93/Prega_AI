package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BadgeDef
import com.example.data.BadgeTier
import com.example.ui.theme.Motion
import com.example.ui.theme.PregaTheme
import com.example.ui.theme.Space
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Prega AI — reward presentation.
 *
 * The rule these follow: a reward may **never** block what she was doing.
 * Points and streaks appear as a toast that dismisses itself. Only a badge —
 * which is rare and is a keepsake — gets a full moment, and even that can be
 * dismissed by tapping anywhere.
 *
 * No modal she has to acknowledge. No "Claim!" button. Nothing that turns a
 * congratulation into another task.
 */

// ─── Points / streak toast ─────────────────────────────────────────────────

@Composable
fun RewardToast(
    visible: Boolean,
    emoji: String,
    text: String,
    modifier: Modifier = Modifier,
    accent: Color? = null,
) {
    AnimatedVisibility(
        visible = visible,
        enter = Motion.riseIn(),
        exit = Motion.riseOut(),
        modifier = modifier,
    ) {
        Row(
            Modifier
                .padding(Space.lg)
                .clip(CircleShape)
                .background(accent ?: PregaTheme.colors.ink)
                .padding(horizontal = Space.xl, vertical = Space.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(emoji, fontSize = 18.sp)
            Spacer(Modifier.width(Space.sm))
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
            )
        }
    }
}

// ─── Badge reveal ──────────────────────────────────────────────────────────

/**
 * The one full-screen celebration in the app, reserved for badges.
 *
 * Auto-dismisses after 4 seconds so it never becomes something she has to
 * clear, and taps through immediately.
 */
@Composable
fun BadgeReveal(
    badge: BadgeDef?,
    celebration: String?,
    onDismiss: () -> Unit,
) {
    if (badge == null) return

    LaunchedEffect(badge.id) {
        delay(4000)
        onDismiss()
    }

    var appeared by remember(badge.id) { mutableStateOf(false) }
    LaunchedEffect(badge.id) { appeared = true }

    val interaction = remember { MutableInteractionSource() }

    val scale by animateFloatAsState(
        targetValue = if (appeared) 1f else 0.6f,
        animationSpec = Motion.playful(),
        label = "badgeScale",
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(PregaTheme.colors.ink.copy(alpha = 0.55f))
            .clickable(interactionSource = interaction, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        PetalDrift(Modifier.fillMaxSize(), petalCount = 8, tint = Color.White)
        Confetti(tier = badge.tier)

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(Space.xxl)
                .graphicsLayer { scaleX = scale; scaleY = scale },
        ) {
            Box(
                Modifier
                    .size(132.dp)
                    .clip(CircleShape)
                    .background(tierBrushColor(badge.tier)),
                contentAlignment = Alignment.Center,
            ) {
                Breathing(minScale = 0.96f, maxScale = 1.04f) {
                    Text(badge.emoji, fontSize = 62.sp)
                }
            }

            Spacer(Modifier.height(Space.xl))
            Text(
                badge.name,
                style = MaterialTheme.typography.displaySmall,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Space.sm))
            Text(
                celebration ?: badge.description,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Space.xl))
            Text(
                "Tap anywhere to continue",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
private fun tierBrushColor(tier: BadgeTier): Color = when (tier) {
    BadgeTier.Bronze -> PregaTheme.colors.terracotta
    BadgeTier.Silver -> PregaTheme.colors.lavender
    BadgeTier.Gold -> PregaTheme.colors.gold
    BadgeTier.Keepsake -> MaterialTheme.colorScheme.primary
}

// ─── Confetti ──────────────────────────────────────────────────────────────

private data class Particle(
    val angle: Float,
    val distance: Float,
    val size: Float,
    val color: Color,
    val delay: Float,
)

/**
 * Soft petal-fall rather than hard confetti — circles drifting outward and
 * settling, matching the app's motion language instead of fighting it.
 */
@Composable
private fun Confetti(tier: BadgeTier, count: Int = 34) {
    val palette = listOf(
        PregaTheme.colors.gold,
        MaterialTheme.colorScheme.primary,
        PregaTheme.colors.sage,
        PregaTheme.colors.lavender,
        PregaTheme.colors.terracotta,
    )

    val particles = remember(tier) {
        List(count) {
            Particle(
                angle = Random.nextFloat() * 360f,
                distance = 0.25f + Random.nextFloat() * 0.75f,
                size = 4f + Random.nextFloat() * 8f,
                color = palette[Random.nextInt(palette.size)],
                delay = Random.nextFloat() * 0.3f,
            )
        }
    }

    val transition = rememberInfiniteTransition(label = "confetti")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = Motion.EaseBreath),
            repeatMode = RepeatMode.Restart,
        ),
        label = "confettiT",
    )

    Canvas(Modifier.fillMaxSize()) {
        val centre = Offset(size.width / 2f, size.height / 2f)
        val maxRadius = size.minDimension * 0.55f

        particles.forEach { p ->
            val progress = ((t - p.delay) / (1f - p.delay)).coerceIn(0f, 1f)
            if (progress <= 0f) return@forEach

            val radians = Math.toRadians(p.angle.toDouble())
            val radius = maxRadius * p.distance * progress
            // Slight downward drift so they settle rather than fly.
            val drift = 60f * progress * progress

            drawCircle(
                color = p.color.copy(alpha = (1f - progress) * 0.85f),
                radius = p.size * (1f - progress * 0.4f),
                center = Offset(
                    centre.x + (cos(radians) * radius).toFloat(),
                    centre.y + (sin(radians) * radius).toFloat() + drift,
                ),
            )
        }
    }
}

// ─── Level up ──────────────────────────────────────────────────────────────

@Composable
fun LevelUpBanner(
    visible: Boolean,
    level: Int,
    title: String,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(Motion.Standard)) + Motion.popIn,
        exit = fadeOut(tween(Motion.Quick)),
        modifier = modifier,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(Space.lg)
                .clip(MaterialTheme.shapes.large)
                .background(PregaTheme.colors.goldBrush)
                .padding(Space.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("👑", fontSize = 26.sp)
            Spacer(Modifier.width(Space.md))
            Column {
                Text(
                    "Level $level",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.85f),
                )
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                )
            }
        }
    }
}
