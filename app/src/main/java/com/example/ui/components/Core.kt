package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.Elevate
import com.example.ui.theme.Motion
import com.example.ui.theme.PregaTheme
import com.example.ui.theme.Space

/**
 * Prega AI — shared UI primitives.
 *
 * Everything visual in the v2 app is composed from these, so spacing, radius,
 * motion and colour stay consistent without every screen re-deciding them.
 */

// ─── Surfaces ──────────────────────────────────────────────────────────────

/**
 * The default container. Soft, barely-elevated, hairline-bordered — depth here
 * comes from colour and space rather than drop shadows.
 */
@Composable
fun PregaCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = PregaTheme.colors.cardSurface,
    border: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(Space.lg),
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // Presses settle rather than snap — 0.98 is felt but not seen.
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = Motion.soft(),
        label = "cardPress",
    )

    val shape = MaterialTheme.shapes.large
    val base = modifier
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clip(shape)
        .background(containerColor)
        .then(
            if (border) Modifier.border(1.dp, PregaTheme.colors.hairline, shape)
            else Modifier
        )

    Column(
        modifier = if (onClick != null) {
            base.clickableNoRipple(interaction, onClick = onClick)
        } else base,
    ) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}

/** A card that carries a gradient — used for hero and reward surfaces. */
@Composable
fun GradientCard(
    brush: Brush,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(Space.xl),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.extraLarge)
            .background(brush)
            .padding(contentPadding),
        content = content,
    )
}

// ─── Buttons ───────────────────────────────────────────────────────────────

/**
 * Primary action. Full-width by default because the main action on a screen
 * should never be a small target — she may be one-handed, tired, or both.
 */
@Composable
fun PregaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    brush: Brush? = null,
    fillWidth: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.97f else 1f,
        animationSpec = Motion.tactile(),
        label = "buttonPress",
    )
    val haptics = LocalHapticFeedback.current
    val shape = RoundedCornerShape(percent = 50)
    val active = enabled && !loading
    val fill = brush ?: PregaTheme.colors.bloomBrush

    Box(
        modifier = modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .heightIn(min = 56.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(
                if (active) fill
                else Brush.horizontalGradient(
                    listOf(PregaTheme.colors.hairline, PregaTheme.colors.hairline)
                )
            )
            .clickableNoRipple(interaction, enabled = active) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = Color.White,
            )
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = if (active) Color.White else PregaTheme.colors.inkFaint,
                modifier = Modifier.padding(horizontal = Space.xl),
            )
        }
    }
}

/** Secondary action. Outlined, never competes with the primary. */
@Composable
fun PregaTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    // Wrap-content by default. The old unconditional fillMaxWidth() made a
    // TEXT button behave like a banner: inside any Row it swallowed the full
    // width and crushed its siblings to zero — which is precisely how the
    // journal's save button "disappeared" and appointment titles vanished on
    // a real device. A text button is a word, not a slab.
    fillWidth: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(percent = 50))
            .border(1.5.dp, PregaTheme.colors.hairline, RoundedCornerShape(percent = 50))
            .clickableNoRipple(interaction, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.xs),
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) PregaTheme.colors.inkMuted else PregaTheme.colors.inkFaint,
        )
    }
}

// ─── Progress ──────────────────────────────────────────────────────────────

/**
 * The ring used for level progress and the week counter.
 *
 * Animates from its previous value rather than jumping, so gaining points
 * reads as growth rather than a state change.
 */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    strokeWidth: Dp = 10.dp,
    trackColor: Color = PregaTheme.colors.hairline,
    brush: Brush? = null,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(Motion.Slow, easing = Motion.EaseBreath),
        label = "ringProgress",
    )
    val fill = brush ?: PregaTheme.colors.bloomBrush

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            val inset = strokeWidth.toPx() / 2
            val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)

            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = stroke,
            )
            if (animated > 0f) {
                drawArc(
                    brush = fill,
                    startAngle = -90f,
                    sweepAngle = 360f * animated,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = stroke,
                )
            }
        }
        content()
    }
}

/** Slim linear bar for quest and goal progress. */
@Composable
fun PregaProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    brush: Brush? = null,
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(Motion.Gentle, easing = Motion.EaseBreath),
        label = "barProgress",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(PregaTheme.colors.hairline),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(animated)
                .clip(CircleShape)
                .background(brush ?: PregaTheme.colors.bloomBrush)
        )
    }
}

// ─── Ambient motion ────────────────────────────────────────────────────────

/**
 * A slow, continuous breath. Wraps the hero belly illustration and the AI
 * thinking state so the app never feels frozen.
 *
 * Deliberately 4s and only ±2% — enough to feel alive, not enough to distract.
 */
@Composable
fun Breathing(
    modifier: Modifier = Modifier,
    minScale: Float = 0.98f,
    maxScale: Float = 1.02f,
    content: @Composable BoxScope.() -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "breathing")
    val scale by transition.animateFloat(
        initialValue = minScale,
        targetValue = maxScale,
        animationSpec = infiniteRepeatable(
            animation = tween(Motion.Ambient, easing = Motion.EaseBreath),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathScale",
    )
    Box(modifier.scale(scale), content = content)
}

// ─── Labels & chips ────────────────────────────────────────────────────────

/** Small uppercase eyebrow above a section title. */
@Composable
fun Overline(text: String, modifier: Modifier = Modifier, color: Color? = null) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = color ?: PregaTheme.colors.inkFaint,
        modifier = modifier,
    )
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    overline: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            overline?.let {
                Overline(it)
                Spacer(Modifier.height(Space.xs))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = PregaTheme.colors.ink,
            )
        }
        action?.invoke()
    }
}

@Composable
fun PregaChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingEmoji: String? = null,
) {
    val bg by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary
        else PregaTheme.colors.recessed,
        animationSpec = tween(Motion.Quick),
        label = "chipBg",
    )
    val fg by animateColorAsState(
        targetValue = if (selected) Color.White else PregaTheme.colors.inkMuted,
        animationSpec = tween(Motion.Quick),
        label = "chipFg",
    )
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.95f else 1f, Motion.tactile(), label = "chipScale"
    )

    Row(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(bg)
            .clickableNoRipple(interaction, onClick = onClick)
            .padding(horizontal = Space.lg, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leadingEmoji?.let {
            Text(it, fontSize = 15.sp)
            Spacer(Modifier.width(Space.sm))
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = fg)
    }
}

// ─── Empty & safety states ─────────────────────────────────────────────────

@Composable
fun EmptyState(
    emoji: String,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(Space.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(emoji, fontSize = 44.sp)
        Spacer(Modifier.height(Space.md))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = PregaTheme.colors.ink,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Space.sm))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = PregaTheme.colors.inkMuted,
            textAlign = TextAlign.Center,
        )
        action?.let { Spacer(Modifier.height(Space.lg)); it() }
    }
}

/**
 * The medical red-flag banner. Visually distinct from every other surface in
 * the app — this is the one component allowed to interrupt, and it must never
 * be mistaken for a tip or a promotion.
 */
@Composable
fun SafetyNotice(
    text: String,
    modifier: Modifier = Modifier,
    title: String = "Please contact your midwife or maternity unit",
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(PregaTheme.colors.alertSoft)
            .border(1.dp, PregaTheme.colors.alert.copy(alpha = 0.35f), MaterialTheme.shapes.medium)
            .padding(Space.lg),
    ) {
        Text("⚠️", fontSize = 18.sp)
        Spacer(Modifier.width(Space.md))
        Column {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = PregaTheme.colors.alert,
            )
            Spacer(Modifier.height(Space.xs))
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = PregaTheme.colors.ink,
            )
        }
    }
}

// ─── Utility ───────────────────────────────────────────────────────────────

/**
 * Click without Material's ripple. The ripple fights the soft aesthetic; scale
 * and haptics carry the feedback instead.
 */
private fun Modifier.clickableNoRipple(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = this.clickable(
    interactionSource = interactionSource,
    indication = null,
    enabled = enabled,
    onClick = onClick,
)
