package com.example.ui.kicks

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.KickLogEntity
import com.example.ui.components.*
import com.example.ui.theme.Motion
import com.example.ui.theme.PregaTheme
import com.example.ui.theme.Space
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Prega AI — Kick counter.
 *
 * This is the most clinically meaningful screen in the app, so it is the most
 * restrained. Reduced fetal movement is a genuine warning sign, and an app that
 * gamifies kick counts — streaks, targets, "great job, 12 today!" — risks
 * teaching someone to feel good about a number instead of noticing a change.
 *
 * So, deliberately:
 *  - No target to hit, no score, no comparison against other users.
 *  - The app never interprets the count or tells her it looks fine.
 *  - Her *own* recent average is shown, because a change from her baby's normal
 *    pattern is the thing that matters — not any absolute figure.
 *  - Guidance to call the maternity unit is permanent and unconditional, not
 *    triggered by a threshold we'd be unqualified to set.
 */
@Composable
fun KickCounterScreen(
    isCounting: Boolean,
    sessionKicks: Int,
    sessionSeconds: Int,
    history: List<KickLogEntity>,
    onStart: () -> Unit,
    onKick: () -> Unit,
    onStop: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(
            start = Space.gutter,
            end = Space.gutter,
            top = Space.lg,
            bottom = Space.navClearance,
        ),
        verticalArrangement = Arrangement.spacedBy(Space.lg),
    ) {
        item {
            if (isCounting) {
                ActiveSession(
                    kicks = sessionKicks,
                    seconds = sessionSeconds,
                    onKick = onKick,
                    onStop = onStop,
                    onCancel = onCancel,
                )
            } else {
                IdleState(history = history, onStart = onStart)
            }
        }

        if (history.isNotEmpty()) {
            item {
                Spacer(Modifier.height(Space.sm))
                SectionHeader(title = "Your sessions", overline = "History")
            }
            items(history.take(30), key = { it.id }) { HistoryRow(it) }
        }

        item { GuidanceCard() }
    }
}

private val PregaColorRipple = androidx.compose.ui.graphics.Color(0xFF8FA063)

// ─── Active session ────────────────────────────────────────────────────────

@Composable
private fun ActiveSession(
    kicks: Int,
    seconds: Int,
    onKick: () -> Unit,
    onStop: () -> Unit,
    onCancel: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }

    // A one-shot bump on each tap so the button confirms the count physically
    // as well as visually — she may not be looking at the screen.
    var tapped by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (tapped) 0.94f else 1f,
        animationSpec = Motion.tactile(),
        label = "kickBump",
    )
    // Release the squash a beat after the tap so each count is felt distinctly
    // even when she taps rapidly.
    LaunchedEffect(kicks) {
        if (kicks > 0) {
            tapped = true
            kotlinx.coroutines.delay(90)
            tapped = false
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            formatDuration(seconds),
            style = MaterialTheme.typography.titleLarge,
            color = PregaTheme.colors.inkMuted,
        )

        Spacer(Modifier.height(Space.xl))

        Box(contentAlignment = Alignment.Center) {
            // A soft ring expands outward from the button on every kick —
            // the moment reads as a pulse of life, not a counter click.
            val ripple = remember { androidx.compose.animation.core.Animatable(0f) }
            LaunchedEffect(kicks) {
                if (kicks > 0) {
                    ripple.snapTo(0f)
                    ripple.animateTo(1f, animationSpec = tween(650))
                }
            }
            if (ripple.value > 0f && ripple.value < 1f) {
                androidx.compose.foundation.Canvas(Modifier.size(320.dp)) {
                    drawCircle(
                        color = PregaColorRipple.copy(alpha = (1f - ripple.value) * 0.45f),
                        radius = size.minDimension / 2f * (0.75f + 0.25f * ripple.value),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 10f * (1f - ripple.value) + 2f,
                        ),
                    )
                }
            }

            // Session progress: ten felt kicks is the classic count-to target;
            // the ring quietly fills so she can feel progress without reading.
            ProgressRing(
                progress = (kicks / 10f).coerceAtMost(1f),
                size = 268.dp,
                strokeWidth = 6.dp,
            )

            Breathing(minScale = 0.99f, maxScale = 1.01f) {
                Box(
                    Modifier
                        .size(240.dp)
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .clip(CircleShape)
                        .background(PregaTheme.colors.bloomBrush)
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                        ) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onKick()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "$kicks",
                            style = MaterialTheme.typography.displayLarge,
                            color = Color.White,
                        )
                        Text(
                            if (kicks == 1) "movement" else "movements",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(Space.xl))
        Text(
            "Tap the circle each time you feel a kick, roll, or flutter.",
            style = MaterialTheme.typography.bodyMedium,
            color = PregaTheme.colors.inkMuted,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(Space.xl))
        PregaButton("Finish session", onStop, enabled = kicks > 0)
        Spacer(Modifier.height(Space.sm))
        PregaTextButton("Discard", onCancel, fillWidth = true)
    }
}

// ─── Idle ──────────────────────────────────────────────────────────────────

@Composable
private fun IdleState(history: List<KickLogEntity>, onStart: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Our own footprint trail replaces the emoji — theme-tinted, drawn,
        // and consistent on every device.
        FootprintTrail(
            color = PregaTheme.colors.terracotta.copy(alpha = 0.8f),
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .height(84.dp),
        )

        Spacer(Modifier.height(Space.lg))
        Text(
            "Kick counter",
            style = MaterialTheme.typography.displaySmall,
            color = PregaTheme.colors.ink,
        )
        Spacer(Modifier.height(Space.sm))
        Text(
            "Find a quiet moment, sit or lie on your side, and count what you feel.",
            style = MaterialTheme.typography.bodyLarge,
            color = PregaTheme.colors.inkMuted,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(Space.xl))
        PregaButton("Start counting", onStart)

        if (history.isNotEmpty()) {
            Spacer(Modifier.height(Space.xl))
            PersonalPattern(history)
        }
    }
}

/**
 * Her own baseline — never a population figure or a target.
 *
 * The number here is only useful as something to compare *today* against, so
 * the copy says exactly that and stops. The app does not tell her whether the
 * number is good.
 */
@Composable
private fun PersonalPattern(history: List<KickLogEntity>) {
    val recent = history.take(10)
    val avgKicks = recent.map { it.count }.average()
    val avgMinutes = recent.map { it.durationSeconds }.average() / 60.0

    PregaCard(containerColor = PregaTheme.colors.recessed, border = false) {
        Overline("Your baby's usual pattern")
        Spacer(Modifier.height(Space.md))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            PatternStat("${avgKicks.toInt()}", "movements", "typical session")
            PatternStat("${avgMinutes.toInt()}", "minutes", "typical length")
        }
        Spacer(Modifier.height(Space.md))
        Text(
            "Based on your last ${recent.size} sessions. What matters is a change " +
                "from what's normal for your baby — not any particular number.",
            style = MaterialTheme.typography.bodySmall,
            color = PregaTheme.colors.inkMuted,
        )
    }
}

@Composable
private fun PatternStat(value: String, unit: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.displaySmall, color = PregaTheme.colors.ink)
        Text(unit, style = MaterialTheme.typography.bodySmall, color = PregaTheme.colors.inkMuted)
        Spacer(Modifier.height(Space.xxs))
        Text(label, style = MaterialTheme.typography.bodySmall, color = PregaTheme.colors.inkFaint)
    }
}

// ─── History ───────────────────────────────────────────────────────────────

@Composable
private fun HistoryRow(kick: KickLogEntity) {
    PregaCard(contentPadding = PaddingValues(Space.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "${kick.count}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text(
                    "${kick.count} ${if (kick.count == 1) "movement" else "movements"} " +
                        "in ${formatDuration(kick.durationSeconds)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = PregaTheme.colors.ink,
                )
                Text(
                    formatTimestamp(kick.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = PregaTheme.colors.inkFaint,
                )
            }
        }
    }
}

// ─── Safety ────────────────────────────────────────────────────────────────

/**
 * Permanent, unconditional. Not shown only when a count looks low — we are not
 * qualified to decide what "low" is, and a warning that appears conditionally
 * implies its absence is reassurance.
 */
@Composable
private fun GuidanceCard() {
    Column {
        Spacer(Modifier.height(Space.sm))
        SafetyNotice(
            title = "If your baby's movements change",
            text = "Contact your maternity unit straight away if you notice reduced or " +
                "different movement — at any hour, on any day. They would always rather " +
                "you called. Never wait to see if it improves, and don't rely on this app " +
                "to tell you whether something is wrong.",
        )
    }
}

// ─── Formatting ────────────────────────────────────────────────────────────

private fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return if (m > 0) "${m}m ${s}s" else "${s}s"
}

private fun formatTimestamp(millis: Long): String =
    SimpleDateFormat("d MMM · HH:mm", Locale.getDefault()).format(Date(millis))
