package com.example.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.ui.components.*
import com.example.ui.theme.Motion
import com.example.ui.theme.PregaTheme
import com.example.ui.theme.Space
import com.example.ui.theme.trimesterColor
import com.example.viewmodel.WeekInfo

/**
 * Prega AI — Home.
 *
 * Ordering is the whole design here. The old dashboard led with tracking
 * widgets, which framed the app as a chore list. This leads with *her baby this
 * week*, because that's the reason she opened it. Tracking comes after, as
 * something she can do in one tap, not something she's confronted with.
 *
 * Section order, and why:
 *   1. Hero — the emotional payoff, always first.
 *   2. Today's insight — one specific, fresh thing.
 *   3. Quests — three achievable things, the gamification core.
 *   4. Quick log — one-tap tracking, no forms.
 *   5. Next appointment — the highest-utility card when it's relevant.
 *   6. Streak & progress — reward state, deliberately *last* so the app never
 *      opens by reminding her what she owes it.
 */

data class HomeState(
    val profile: UserProfileEntity?,
    val weekInfo: WeekInfo,
    val todayLog: DailyLogEntity?,
    val progress: ProgressEntity,
    val quests: List<QuestEntity>,
    val insight: String?,
    val mood: MoodEntity?,
    val nextAppointment: AppointmentEntity?,
    val daysRemaining: Int,
)

@Composable
fun HomeScreen(
    state: HomeState,
    onWater: () -> Unit,
    onVitamins: () -> Unit,
    onMood: (Int) -> Unit,
    onQuestComplete: (QuestEntity) -> Unit,
    onOpenKicks: () -> Unit,
    onOpenCoach: () -> Unit,
    onOpenJourney: () -> Unit,
    onOpenAppointments: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(
            start = Space.gutter,
            end = Space.gutter,
            top = Space.sm,
            bottom = Space.navClearance,
        ),
        verticalArrangement = Arrangement.spacedBy(Space.lg),
    ) {
        item { Greeting(state.profile?.name.orEmpty()) }

        item {
            WeekHero(
                weekInfo = state.weekInfo,
                babyName = state.profile?.babyNamePlaceholder.orEmpty(),
                daysRemaining = state.daysRemaining,
                onClick = onOpenJourney,
            )
        }

        item { InsightCard(state.insight, onOpenCoach) }

        if (state.quests.isNotEmpty()) {
            item { QuestSection(state.quests, onQuestComplete) }
        }

        item {
            QuickLog(
                log = state.todayLog,
                goal = state.profile?.waterGoalGlasses ?: 8,
                mood = state.mood,
                onWater = onWater,
                onVitamins = onVitamins,
                onMood = onMood,
                onOpenKicks = onOpenKicks,
            )
        }

        state.nextAppointment?.let {
            item { AppointmentCard(it, onOpenAppointments) }
        }

        item { ProgressSection(state.progress) }
    }
}

// ─── Sections ──────────────────────────────────────────────────────────────

@Composable
private fun Greeting(name: String) {
    Column(Modifier.padding(top = Space.md)) {
        Text(
            greetingFor(name),
            style = MaterialTheme.typography.headlineLarge,
            color = PregaTheme.colors.ink,
        )
    }
}

/**
 * Time-of-day greeting. The 3am case matters: someone awake then is usually
 * uncomfortable or anxious, and "Good morning!" would land badly.
 */
private fun greetingFor(name: String): String {
    val hour = java.time.LocalTime.now().hour
    val suffix = if (name.isBlank()) "" else ", $name"
    return when (hour) {
        in 0..4 -> "Still awake$suffix?"
        in 5..11 -> "Good morning$suffix"
        in 12..17 -> "Afternoon$suffix"
        in 18..21 -> "Evening$suffix"
        else -> "Winding down$suffix"
    }
}

@Composable
private fun WeekHero(
    weekInfo: WeekInfo,
    babyName: String,
    daysRemaining: Int,
    onClick: () -> Unit,
) {
    val tint = trimesterColor(weekInfo.trimester)

    GradientCard(
        brush = androidx.compose.ui.graphics.Brush.linearGradient(
            listOf(tint.copy(alpha = 0.9f), tint.copy(alpha = 0.65f))
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Overline("Trimester ${weekInfo.trimester}", color = Color.White.copy(alpha = 0.85f))
                Spacer(Modifier.height(Space.sm))
                Text(
                    "Week ${weekInfo.week}",
                    style = MaterialTheme.typography.displayMedium,
                    color = Color.White,
                )
                Spacer(Modifier.height(Space.xs))
                Text(
                    if (babyName.isBlank()) "About the size of a ${weekInfo.sizeName.lowercase()}"
                    else "$babyName is about the size of a ${weekInfo.sizeName.lowercase()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.92f),
                )
            }
            Breathing { Text(weekInfo.iconEmoji, fontSize = 64.sp) }
        }

        Spacer(Modifier.height(Space.lg))

        // 40 weeks as the denominator, shown as a bar rather than a percentage —
        // "62% pregnant" is a strange thing to tell someone.
        PregaProgressBar(
            progress = weekInfo.week / 40f,
            brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                listOf(Color.White, Color.White.copy(alpha = 0.75f))
            ),
            height = 6.dp,
        )
        Spacer(Modifier.height(Space.sm))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "${weekInfo.lengthCm} cm · ${formatWeight(weekInfo.weightGrams)}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.85f),
            )
            Text(
                if (daysRemaining > 0) "$daysRemaining days to go" else "Any day now",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.85f),
            )
        }

        Spacer(Modifier.height(Space.lg))
        Text(
            weekInfo.description,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.95f),
        )

        Spacer(Modifier.height(Space.md))
        TextButton(onClick = onClick, contentPadding = PaddingValues(0.dp)) {
            Text(
                "See the full journey →",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
            )
        }
    }
}

private fun formatWeight(grams: Double): String =
    if (grams < 1000) "${grams.toInt()} g"
    else String.format("%.1f kg", grams / 1000)

@Composable
private fun InsightCard(insight: String?, onOpenCoach: () -> Unit) {
    PregaCard(onClick = onOpenCoach) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(PregaTheme.colors.lavenderSoft),
                contentAlignment = Alignment.Center,
            ) { Text("✨", fontSize = 16.sp) }

            Spacer(Modifier.width(Space.md))

            Column(Modifier.weight(1f)) {
                Overline("Today")
                Spacer(Modifier.height(Space.sm))
                if (insight == null) {
                    // Skeleton rather than a spinner — a spinner on the home
                    // screen makes the whole app feel like it's waiting.
                    ShimmerLines()
                } else {
                    Text(
                        insight,
                        style = MaterialTheme.typography.bodyLarge,
                        color = PregaTheme.colors.ink,
                    )
                    Spacer(Modifier.height(Space.md))
                    Text(
                        "Ask me anything →",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun ShimmerLines() {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.7f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = tween(900, easing = Motion.EaseBreath),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "shimmerAlpha",
    )
    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        listOf(1f, 0.9f, 0.55f).forEach { widthFraction ->
            Box(
                Modifier
                    .fillMaxWidth(widthFraction)
                    .height(12.dp)
                    .alpha(alpha)
                    .clip(CircleShape)
                    .background(PregaTheme.colors.hairline)
            )
        }
    }
}

@Composable
private fun QuestSection(quests: List<QuestEntity>, onComplete: (QuestEntity) -> Unit) {
    val done = quests.count { it.completed }

    Column {
        SectionHeader(
            title = "Three small things",
            overline = "Today's quests",
            action = {
                Text(
                    "$done of ${quests.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = PregaTheme.colors.inkFaint,
                )
            },
        )
        Spacer(Modifier.height(Space.md))

        Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            quests.forEach { quest ->
                QuestRow(quest, onComplete)
            }
        }

        AnimatedVisibility(visible = done == quests.size, enter = Motion.popIn) {
            Column {
                Spacer(Modifier.height(Space.md))
                PregaCard(containerColor = PregaTheme.colors.successSoft, border = false) {
                    Text(
                        "All three done. That's a good day — whatever else it held.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PregaTheme.colors.ink,
                    )
                }
            }
        }
    }
}

@Composable
private fun QuestRow(quest: QuestEntity, onComplete: (QuestEntity) -> Unit) {
    PregaCard(
        onClick = { if (!quest.completed) onComplete(quest) },
        containerColor = if (quest.completed) PregaTheme.colors.recessed
        else PregaTheme.colors.cardSurface,
        contentPadding = PaddingValues(Space.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CheckCircle(checked = quest.completed)
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text(
                    quest.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (quest.completed) PregaTheme.colors.inkFaint else PregaTheme.colors.ink,
                    textDecoration = if (quest.completed) TextDecoration.LineThrough else null,
                )
                if (quest.rationale.isNotBlank()) {
                    Spacer(Modifier.height(Space.xxs))
                    Text(
                        quest.rationale,
                        style = MaterialTheme.typography.bodySmall,
                        color = PregaTheme.colors.inkFaint,
                    )
                }
            }
        }
    }
}

@Composable
private fun CheckCircle(checked: Boolean) {
    val scale by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = Motion.playful(),
        label = "checkScale",
    )
    Box(
        Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(
                if (checked) MaterialTheme.colorScheme.secondary
                else PregaTheme.colors.recessed
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Text("✓", fontSize = (14 * scale).coerceAtLeast(1f).sp, color = Color.White)
        }
    }
}

@Composable
private fun QuickLog(
    log: DailyLogEntity?,
    goal: Int,
    mood: MoodEntity?,
    onWater: () -> Unit,
    onVitamins: () -> Unit,
    onMood: (Int) -> Unit,
    onOpenKicks: () -> Unit,
) {
    Column {
        SectionHeader(title = "Quick log", overline = "One tap each")
        Spacer(Modifier.height(Space.md))

        Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            // Water — tapping adds a glass. No modal, no number pad.
            QuickTile(
                modifier = Modifier.weight(1f),
                emoji = "💧",
                label = "Water",
                value = "${log?.waterGlasses ?: 0} / $goal",
                progress = ((log?.waterGlasses ?: 0).toFloat() / goal).coerceIn(0f, 1f),
                tint = PregaTheme.colors.lavenderSoft,
                onClick = onWater,
            )
            QuickTile(
                modifier = Modifier.weight(1f),
                emoji = if (log?.tookVitamins == true) "✅" else "💊",
                label = "Vitamins",
                value = if (log?.tookVitamins == true) "Taken" else "Not yet",
                progress = if (log?.tookVitamins == true) 1f else 0f,
                tint = PregaTheme.colors.goldSoft,
                onClick = onVitamins,
            )
        }

        Spacer(Modifier.height(Space.md))

        PregaCard(onClick = onOpenKicks) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("👣", fontSize = 26.sp)
                Spacer(Modifier.width(Space.md))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Count kicks",
                        style = MaterialTheme.typography.titleMedium,
                        color = PregaTheme.colors.ink,
                    )
                    Text(
                        "Knowing your baby's usual pattern is what makes a change noticeable",
                        style = MaterialTheme.typography.bodySmall,
                        color = PregaTheme.colors.inkMuted,
                    )
                }
                Text("→", color = PregaTheme.colors.inkFaint, fontSize = 18.sp)
            }
        }

        Spacer(Modifier.height(Space.md))

        PregaCard {
            Text(
                "How are you feeling?",
                style = MaterialTheme.typography.titleMedium,
                color = PregaTheme.colors.ink,
            )
            Spacer(Modifier.height(Space.md))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                MOODS.forEachIndexed { i, (emoji, label) ->
                    MoodOption(
                        emoji = emoji,
                        label = label,
                        selected = mood?.mood == i + 1,
                        onClick = { onMood(i + 1) },
                    )
                }
            }
        }
    }
}

/** Named, never ranked. There is no "bad" mood to score badly on. */
private val MOODS = listOf(
    "😣" to "Rough",
    "😕" to "Low",
    "😌" to "Okay",
    "🙂" to "Good",
    "😄" to "Great",
)

@Composable
private fun MoodOption(emoji: String, label: String, selected: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.15f else 1f,
        animationSpec = Motion.playful(),
        label = "moodScale",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(if (selected) PregaTheme.colors.recessed else Color.Transparent)
            .padding(horizontal = Space.md, vertical = Space.sm),
    ) {
        Text(emoji, fontSize = (22 * scale).sp)
        Spacer(Modifier.height(Space.xs))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) PregaTheme.colors.ink else PregaTheme.colors.inkFaint,
        )
    }
}

@Composable
private fun QuickTile(
    emoji: String,
    label: String,
    value: String,
    progress: Float,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PregaCard(
        modifier = modifier,
        onClick = onClick,
        containerColor = tint,
        border = false,
        contentPadding = PaddingValues(Space.lg),
    ) {
        Text(emoji, fontSize = 24.sp)
        Spacer(Modifier.height(Space.sm))
        Text(label, style = MaterialTheme.typography.bodySmall, color = PregaTheme.colors.inkMuted)
        Text(value, style = MaterialTheme.typography.titleLarge, color = PregaTheme.colors.ink)
        Spacer(Modifier.height(Space.sm))
        PregaProgressBar(progress = progress, height = 4.dp)
    }
}

@Composable
private fun AppointmentCard(appointment: AppointmentEntity, onOpen: () -> Unit) {
    PregaCard(onClick = onOpen, containerColor = PregaTheme.colors.sageSoft, border = false) {
        Overline("Coming up")
        Spacer(Modifier.height(Space.sm))
        Text(
            appointment.title,
            style = MaterialTheme.typography.titleLarge,
            color = PregaTheme.colors.ink,
        )
        Spacer(Modifier.height(Space.xs))
        Text(
            listOfNotNull(
                appointment.date,
                appointment.time.takeIf { it.isNotBlank() },
                appointment.location.takeIf { it.isNotBlank() },
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = PregaTheme.colors.inkMuted,
        )

        // The genuinely useful bit: the questions she meant to ask and will
        // otherwise forget the moment she's in the room.
        if (appointment.questionsToAsk.isNotBlank()) {
            Spacer(Modifier.height(Space.md))
            Text(
                "Your questions",
                style = MaterialTheme.typography.labelMedium,
                color = PregaTheme.colors.inkMuted,
            )
            Spacer(Modifier.height(Space.xs))
            appointment.questionsToAsk.split("\n").take(3).forEach {
                Text(
                    "· $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = PregaTheme.colors.ink,
                )
            }
        }
    }
}

@Composable
private fun ProgressSection(progress: ProgressEntity) {
    Column {
        SectionHeader(title = "Your progress", overline = "Level ${progress.level}")
        Spacer(Modifier.height(Space.md))

        PregaCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressRing(
                    progress = progress.levelProgress,
                    size = 84.dp,
                    strokeWidth = 8.dp,
                    brush = PregaTheme.colors.goldBrush,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${progress.points}",
                            style = MaterialTheme.typography.titleLarge,
                            color = PregaTheme.colors.ink,
                        )
                        Text(
                            "points",
                            style = MaterialTheme.typography.bodySmall,
                            color = PregaTheme.colors.inkFaint,
                        )
                    }
                }

                Spacer(Modifier.width(Space.lg))

                Column(Modifier.weight(1f)) {
                    Text(
                        progress.levelTitle,
                        style = MaterialTheme.typography.titleLarge,
                        color = PregaTheme.colors.ink,
                    )
                    progress.pointsToNextLevel?.let {
                        Spacer(Modifier.height(Space.xxs))
                        Text(
                            "$it points to the next level",
                            style = MaterialTheme.typography.bodySmall,
                            color = PregaTheme.colors.inkMuted,
                        )
                    }
                }
            }

            Spacer(Modifier.height(Space.lg))
            HorizontalDivider(color = PregaTheme.colors.hairline)
            Spacer(Modifier.height(Space.lg))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Stat("🔥", "${progress.currentStreak}", "day streak")
                Stat("🏅", "${progress.longestStreak}", "best ever")
                // Surfaced deliberately: knowing she has grace days in hand is
                // what stops a streak becoming a source of pressure.
                Stat("🛡️", "${progress.streakFreezes}", "grace days")
            }
        }
    }
}

@Composable
private fun Stat(emoji: String, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 18.sp)
        Spacer(Modifier.height(Space.xs))
        Text(value, style = MaterialTheme.typography.titleLarge, color = PregaTheme.colors.ink)
        Text(label, style = MaterialTheme.typography.bodySmall, color = PregaTheme.colors.inkFaint)
    }
}
