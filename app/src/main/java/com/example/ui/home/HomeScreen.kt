package com.example.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.Share
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.material3.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
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
    val affirmation: String?,
    val mood: MoodEntity?,
    val nextAppointment: AppointmentEntity?,
    val daysRemaining: Int,
)

@Composable
fun HomeScreen(
    state: HomeState,
    isDark: Boolean = false,
    onToggleTheme: () -> Unit = {},
    onOpenJournal: () -> Unit = {},
    onOpenGarden: () -> Unit = {},
    onOpenCommunity: () -> Unit = {},
    onOpenMenu: () -> Unit = {},
    onStepsGoal: () -> Unit = {},
    checkedInToday: Boolean = true,
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
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        item {
            Reveal(0) {
                HomeHeader(
                    name = state.profile?.name.orEmpty(),
                    state = state,
                    isDark = isDark,
                    onToggleTheme = onToggleTheme,
                    onOpenMenu = onOpenMenu,
                )
            }
        }

        item {
          Reveal(1) {
            // "Little One" living portrait: shown only when the bundled
            // week image exists (weekNN in drawable-nodpi). Until the image
            // pack is generated and pushed, or for any missing week, the
            // classic WeekHero renders instead — the app never blanks.
            val context = LocalContext.current
            val weekImageId = remember(state.weekInfo.week) {
                context.resources.getIdentifier(
                    "week%02d".format(state.weekInfo.week),
                    "drawable",
                    context.packageName,
                )
            }
            if (weekImageId != 0) {
                LittleOneHero(
                    imageResId = weekImageId,
                    weekInfo = state.weekInfo,
                    babyName = state.profile?.babyNamePlaceholder.orEmpty(),
                    onClick = onOpenJourney,
                )
            } else {
                WeekHero(
                    weekInfo = state.weekInfo,
                    babyName = state.profile?.babyNamePlaceholder.orEmpty(),
                    daysRemaining = state.daysRemaining,
                    onClick = onOpenJourney,
                )
            }
          }
        }

        if (!checkedInToday) {
            item { Reveal(2) { CheckInCard(onOpenJournal) } }
        }

        item { Reveal(3) { InsightCard(state.insight, onOpenCoach) } }

        state.affirmation?.let {
            item { AffirmationCard(it) }
        }

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
                onStepsGoal = onStepsGoal,
            )
        }

        item {
            ExploreGrid(
                onOpenKicks = onOpenKicks,
                onOpenCoach = onOpenCoach,
                onOpenJourney = onOpenJourney,
                onOpenAppointments = onOpenAppointments,
                onOpenJournal = onOpenJournal,
                onOpenGarden = onOpenGarden,
            )
        }

        item {
            // Birth Club entry — a full-width invitation rather than a grid
            // cell: community is new and opt-in, so it earns one warm banner.
            // Personalized with HER club (derived from her due date, same
            // logic as the community screen) so it reads as a real place
            // that exists for her, not a generic feature ad.
            val dueMonthLabel = remember(state.profile?.dueDate, state.weekInfo.week) {
                dueMonthLabelFor(state.profile?.dueDate, state.weekInfo.week)
            }
            PregaCard(
                onClick = onOpenCommunity,
                containerColor = PregaTheme.colors.lavenderSoft,
                border = false,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("\uD83E\uDEC2", fontSize = 26.sp)
                    Spacer(Modifier.width(Space.md))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (dueMonthLabel != null) "Your $dueMonthLabel birth club"
                            else "Your birth club",
                            style = MaterialTheme.typography.titleMedium,
                            color = PregaTheme.colors.ink,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Mothers due the same month as you — chat, share, and meet on video circles.",
                            style = MaterialTheme.typography.bodySmall,
                            color = PregaTheme.colors.inkMuted,
                        )
                    }
                }
            }
        }

        state.nextAppointment?.let {
            item { AppointmentCard(it, onOpenAppointments) }
        }

        item { ProgressSection(state.progress) }
    }
}


/**
 * Entrance choreography: each home section fades and rises in, one beat
 * after the previous — the screen assembles itself like a page settling,
 * instead of appearing as a wall. Runs once per composition of the tab.
 */
@Composable
private fun Reveal(index: Int, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(60L * index)
        shown = true
    }
    AnimatedVisibility(
        visible = shown,
        enter = fadeIn(tween(320)) + slideInVertically(tween(320)) { it / 6 },
    ) { content() }
}

// ─── Sections ──────────────────────────────────────────────────────────────

/**
 * The reference designs' 2x2 pastel grid — four soft colour blocks, each with
 * a corner arrow chip. This replaces two full-width nudge cards (kicks,
 * appointments prompt) so the same destinations now cost half the scroll,
 * which was the explicit ask. Kick counting keeps its context line inside
 * the Kicks screen itself, where it's read at the moment it matters.
 */
@Composable
private fun ExploreGrid(
    onOpenKicks: () -> Unit,
    onOpenCoach: () -> Unit,
    onOpenJourney: () -> Unit,
    onOpenAppointments: () -> Unit,
    onOpenJournal: () -> Unit,
    onOpenGarden: () -> Unit,
) {
    Column {
        SectionHeader(title = "Explore", overline = "Everything, one tap")
        Spacer(Modifier.height(Space.md))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            ExploreCell("Count kicks", PregaTheme.colors.sageSoft, onOpenKicks, Modifier.weight(1f))
            ExploreCell("Ask Prega AI", PregaTheme.colors.lavenderSoft, onOpenCoach, Modifier.weight(1f))
        }
        Spacer(Modifier.height(Space.md))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            ExploreCell("Your journey", PregaTheme.colors.terracottaSoft, onOpenJourney, Modifier.weight(1f))
            ExploreCell("Appointments", PregaTheme.colors.goldSoft, onOpenAppointments, Modifier.weight(1f))
        }
        Spacer(Modifier.height(Space.md))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            ExploreCell("My journal", PregaTheme.colors.lavenderSoft, onOpenJournal, Modifier.weight(1f))
            ExploreCell("My garden", PregaTheme.colors.sageSoft, onOpenGarden, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ExploreCell(
    title: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PregaCard(
        onClick = onClick,
        containerColor = tint,
        border = false,
        contentPadding = PaddingValues(Space.md),
        modifier = modifier.heightIn(min = 96.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = PregaTheme.colors.ink,
            )
            Spacer(Modifier.weight(1f))
            // The reference's corner arrow chip, bottom-right.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(PregaTheme.colors.cardSurface.copy(alpha = 0.75f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowForward,
                        contentDescription = null,
                        tint = PregaTheme.colors.ink,
                        modifier = Modifier
                            .size(15.dp)
                            .graphicsLayer { rotationZ = -45f },
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(
    name: String,
    state: HomeState,
    isDark: Boolean,
    onToggleTheme: () -> Unit,
    onOpenMenu: () -> Unit,
) {
    val context = LocalContext.current

    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Avatar chip: her photo (or initial), opens the slide-out menu.
        ProfileAvatar(
            name = name,
            size = 42.dp,
            onClick = onOpenMenu,
        )
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Text(
                greetingFor(name),
                style = MaterialTheme.typography.headlineLarge,
                color = PregaTheme.colors.ink,
            )
            if (state.progress.currentStreak > 0 || state.progress.points > 0) {
                Spacer(Modifier.height(2.dp))
                Text(
                    buildString {
                        if (state.progress.currentStreak > 0)
                            append("\uD83C\uDF31 ${state.progress.currentStreak}-day streak")
                        if (state.progress.currentStreak > 0 && state.progress.points > 0)
                            append("  ·  ")
                        if (state.progress.points > 0)
                            append("${state.progress.points} bloom points")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = PregaTheme.colors.sage,
                )
            }
        }

        // Day/night, one tap from the home screen. The icon shows what
        // tapping GIVES her (moon = "switch to dark"), not the current state.
        HeaderIcon(
            icon = if (isDark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
            contentDescription = "Switch theme",
            onClick = onToggleTheme,
        )
        Spacer(Modifier.width(Space.sm))
        HeaderIcon(
            icon = Icons.Outlined.Share,
            contentDescription = "Share my journey",
            onClick = { shareJourney(context, state) },
        )
    }
}

@Composable
private fun HeaderIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(PregaTheme.colors.cardSurface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = PregaTheme.colors.inkMuted,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * "Share my journey" — a warm, ready-to-send update for WhatsApp or anywhere.
 * ACTION_SEND with a chooser rather than deep-linking WhatsApp specifically:
 * the chooser shows WhatsApp first for most Indian users anyway, and this way
 * dads on Telegram or grandmothers on SMS aren't excluded.
 *
 * Privacy line held on purpose: the message shares the joyful facts (week,
 * size, countdown) and NEVER auto-includes mood, symptoms, weight, or kick
 * counts — those are hers to tell, not the app's to broadcast.
 */
private fun shareJourney(context: android.content.Context, state: HomeState) {
    val week = state.weekInfo.week
    val baby = state.profile?.babyNamePlaceholder?.ifBlank { "our little one" } ?: "our little one"
    val size = state.weekInfo.sizeName.lowercase()
    val days = state.daysRemaining

    val text = buildString {
        append("Week $week of our journey 🌸\n")
        append("$baby is about the size of a $size ${state.weekInfo.iconEmoji}\n")
        if (days > 0) append("$days days to go!\n")
        append("\nShared from Prega AI")
    }

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, "Share your journey"))
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

/**
 * Redesigned to the reference language after a real-device screenshot showed
 * the problem plainly: the old hero was a full-bleed trimester-gold gradient
 * slab sitting directly above the gold affirmation card — the whole top of
 * the page read as one monotone amber block, and the week description
 * paragraph made it enormous.
 *
 * Now: a soft flat blush card with ink serif text (the references never put
 * white text on saturated slabs), the baby-size emoji in a cream circle
 * chip, and no paragraph — the full week description lives on Journey where
 * she goes to read, not scan. Trimester colour survives as a small tinted
 * chip instead of painting the whole card. Gold now appears exactly once on
 * this screen: the affirmation.
 */
/**
 * "Little One" — the living portrait hero. A bundled Pixar-style render of
 * the baby at this week, treated so it feels alive rather than printed:
 * a very slow Ken Burns drift (scale 1.0->1.05 over 14s), a warm glow that
 * breathes behind the text scrim, and a few drifting petal motes. Nothing
 * fast, nothing loud — a window, not a slideshow.
 *
 * The image pack lives in drawable-nodpi as week01..week40 (generated once
 * via the Colab image cell; see repo docs). This composable is only reached
 * when the current week's resource exists — HomeScreen falls back to the
 * classic WeekHero otherwise.
 */
@Composable
private fun LittleOneHero(
    imageResId: Int,
    weekInfo: WeekInfo,
    babyName: String,
    onClick: () -> Unit,
) {
    val drift = rememberInfiniteTransition(label = "littleOne")
    val zoom by drift.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(14000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "kenBurns",
    )
    val glow by drift.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(3600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow",
    )

    PregaCard(
        onClick = onClick,
        border = false,
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(300.dp)
                .clip(MaterialTheme.shapes.large),
        ) {
            Image(
                painter = painterResource(imageResId),
                contentDescription = "Your baby at week ${weekInfo.week}",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        scaleX = zoom
                        scaleY = zoom
                    },
            )
            // Breathing warm glow, bottom-anchored, doubles as a text scrim.
            // Starts higher (45%) and lands darker than v1: the orb art is
            // brightest exactly where the text sits, and the QA crop
            // simulation showed white-on-glow going marginal without this.
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.45f to Color.Transparent,
                            0.75f to Color(0xFF2B1E14).copy(alpha = 0.34f),
                            1f to Color(0xFF2B1E14).copy(alpha = 0.68f + glow * 0.14f),
                        )
                    ),
            )
            PetalDrift(
                petalCount = 5,
                tint = Color.White.copy(alpha = 0.35f),
                modifier = Modifier.matchParentSize(),
            )
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(Space.lg),
            ) {
                // Soft drop shadows on all three lines: the 40 bundled
                // images vary in brightness where text lands, and legible-
                // everywhere beats trusting each render.
                val glowShadow = Shadow(
                    color = Color(0x59000000),
                    offset = Offset(0f, 2f),
                    blurRadius = 10f,
                )
                Overline("Your little one", color = Color.White.copy(alpha = 0.85f))
                Spacer(Modifier.height(Space.xs))
                Text(
                    "Week ${weekInfo.week}",
                    style = MaterialTheme.typography.displayMedium.copy(shadow = glowShadow),
                    color = Color.White,
                )
                Spacer(Modifier.height(Space.xxs))
                Text(
                    if (babyName.isBlank())
                        "About the size of a ${weekInfo.sizeName.lowercase()} ${weekInfo.iconEmoji}"
                    else
                        "$babyName is about the size of a ${weekInfo.sizeName.lowercase()} ${weekInfo.iconEmoji}",
                    style = MaterialTheme.typography.bodyMedium.copy(shadow = glowShadow),
                    color = Color.White.copy(alpha = 0.95f),
                )
            }
        }
    }
}

@Composable
private fun WeekHero(
    weekInfo: WeekInfo,
    babyName: String,
    daysRemaining: Int,
    onClick: () -> Unit,
) {
    val trimesterTint = trimesterColor(weekInfo.trimester)

    PregaCard(
        onClick = onClick,
        containerColor = PregaTheme.colors.terracottaSoft,
        border = false,
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
      Box {
        // Abstract calm: soft cream blobs bleeding off the top-right corner.
        CornerBlobs(
            tint = PregaTheme.colors.cardSurface,
            modifier = Modifier.matchParentSize(),
        )
        LeafSprig(
            color = PregaTheme.colors.ink.copy(alpha = 0.10f),
            rotationDegrees = -8f,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(120.dp)
                .padding(end = Space.sm, bottom = Space.xs),
        )
        Column(Modifier.padding(Space.lg)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                // Trimester as a small tinted chip, not a card-wide colour.
                Box(
                    Modifier
                        .clip(MaterialTheme.shapes.small)
                        .background(trimesterTint.copy(alpha = 0.22f))
                        .padding(horizontal = Space.sm, vertical = Space.xxs),
                ) {
                    Text(
                        "Trimester ${weekInfo.trimester}",
                        style = MaterialTheme.typography.labelMedium,
                        color = PregaTheme.colors.ink,
                    )
                }
                Spacer(Modifier.height(Space.sm))
                Text(
                    "Week ${weekInfo.week}",
                    style = MaterialTheme.typography.displayMedium,
                    color = PregaTheme.colors.ink,
                )
                Spacer(Modifier.height(Space.xs))
                Text(
                    if (babyName.isBlank()) "About the size of a ${weekInfo.sizeName.lowercase()}"
                    else "$babyName is about the size of a ${weekInfo.sizeName.lowercase()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PregaTheme.colors.inkMuted,
                )
            }

            // Emoji sits in a cream circle chip, the references' framing move.
            Box(
                Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(PregaTheme.colors.cardSurface.copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center,
            ) {
                Breathing { Text(weekInfo.iconEmoji, fontSize = 38.sp) }
            }
        }

        Spacer(Modifier.height(Space.lg))

        // 40 weeks as the denominator, shown as a bar rather than a percentage —
        // "62% pregnant" is a strange thing to tell someone.
        PregaProgressBar(
            progress = weekInfo.week / 40f,
            brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                listOf(PregaTheme.colors.terracotta, trimesterTint)
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
                color = PregaTheme.colors.inkMuted,
            )
            Text(
                if (daysRemaining > 0) "$daysRemaining days to go" else "Any day now",
                style = MaterialTheme.typography.bodySmall,
                color = PregaTheme.colors.inkMuted,
            )
        }

        Spacer(Modifier.height(Space.md))
        // The whole card is tappable; this line is the affordance hint, in
        // ink — the old white would be invisible on the new light card.
        Text(
            "See the full journey →",
            style = MaterialTheme.typography.labelMedium,
            color = PregaTheme.colors.terracotta,
        )
        }
      }
    }
}

private fun formatWeight(grams: Double): String =
    if (grams < 1000) "${grams.toInt()} g"
    else String.format("%.1f kg", grams / 1000)

/**
 * The once-a-day ask, and only once: mood, a line, maybe a photo. Appears
 * only until she's checked in, then leaves the screen for the day — a
 * standing card would become furniture she scrolls past.
 */
@Composable
private fun CheckInCard(onOpenJournal: () -> Unit) {
    PregaCard(
        onClick = onOpenJournal,
        containerColor = PregaTheme.colors.lavenderSoft,
        border = false,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "How was today?",
                    style = MaterialTheme.typography.titleMedium,
                    color = PregaTheme.colors.ink,
                )
                Spacer(Modifier.height(Space.xxs))
                Text(
                    "A mood, a line, a photo if you like — thirty seconds, kept forever.",
                    style = MaterialTheme.typography.bodySmall,
                    color = PregaTheme.colors.inkMuted,
                )
            }
            Text("→", color = PregaTheme.colors.inkFaint, fontSize = 18.sp)
        }
    }
}

@Composable
private fun InsightCard(insight: String?, onOpenCoach: () -> Unit) {
    // Sage block, per the reference designs — the calm-green "content" tint,
    // distinct from the pastel quest stack below it.
    PregaCard(
        onClick = onOpenCoach,
        containerColor = PregaTheme.colors.sageSoft,
        border = false,
    ) {
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
                    Spacer(Modifier.height(Space.sm))
                    ListenChip(insight)
                    Spacer(Modifier.height(Space.sm))
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

/**
 * The day's affirmation. Sits right after the insight, styled as the one gold
 * moment on the home screen — gold is reserved for reward surfaces, and this
 * is a small daily gift rather than information. Deliberately not a card she
 * can tap: there is nothing to do here except read it, which is the point.
 */
@Composable
private fun AffirmationCard(text: String) {
    GradientCard(
        brush = PregaTheme.colors.goldBrush,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(0.dp),
    ) {
      Box {
        LeafSprig(
            color = Color.White.copy(alpha = 0.35f),
            rotationDegrees = 12f,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(84.dp)
                .padding(end = Space.sm),
        )
        Column(Modifier.padding(Space.lg)) {
        Overline("Today's affirmation", color = Color.White.copy(alpha = 0.85f))
        Spacer(Modifier.height(Space.sm))
        Text(
            "\u201C$text\u201D",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
        )
        Spacer(Modifier.height(Space.sm))
        // White-tinted listen chip — the one place the default faint ink
        // would vanish against the gold.
        ListenChip(text, tint = Color.White.copy(alpha = 0.85f))
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
            quests.forEachIndexed { index, quest ->
                QuestRow(quest, index, onComplete)
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

/**
 * The reference designs' signature move: adjacent cards in different soft
 * pastels — blush, butter, sage — rather than a column of identical white
 * cards. Each quest takes its tint from its position; completing one fades it
 * to the recessed neutral, so colour literally drains from what's done and
 * the remaining colour shows her what's left at a glance.
 */
@Composable
private fun QuestRow(quest: QuestEntity, index: Int, onComplete: (QuestEntity) -> Unit) {
    val tints = listOf(
        PregaTheme.colors.terracottaSoft,
        PregaTheme.colors.goldSoft,
        PregaTheme.colors.sageSoft,
        PregaTheme.colors.lavenderSoft,
    )

    PregaCard(
        onClick = { if (!quest.completed) onComplete(quest) },
        containerColor = if (quest.completed) PregaTheme.colors.recessed
        else tints[index % tints.size],
        border = false,
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
                        color = if (quest.completed) PregaTheme.colors.inkFaint
                        else PregaTheme.colors.inkMuted,
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

    // Six tiny petals burst outward once, on the transition to checked.
    var burst by remember { mutableStateOf(false) }
    LaunchedEffect(checked) { burst = checked }
    val burstT by animateFloatAsState(
        targetValue = if (burst) 1f else 0f,
        animationSpec = tween(Motion.Slow, easing = Motion.EaseEnter),
        label = "burstT",
    )

    Box(contentAlignment = Alignment.Center) {
        if (checked && burstT > 0f && burstT < 1f) {
            androidx.compose.foundation.Canvas(Modifier.size(52.dp)) {
                val centre = androidx.compose.ui.geometry.Offset(
                    size.width / 2f, size.height / 2f
                )
                repeat(6) { i ->
                    val angle = Math.toRadians(i * 60.0 - 90.0)
                    val dist = 22f * burstT
                    drawCircle(
                        color = Color(0xFF7A9E7E).copy(alpha = (1f - burstT) * 0.8f),
                        radius = 3f * (1f - burstT * 0.5f),
                        center = androidx.compose.ui.geometry.Offset(
                            centre.x + (kotlin.math.cos(angle) * dist).toFloat(),
                            centre.y + (kotlin.math.sin(angle) * dist).toFloat(),
                        ),
                    )
                }
            }
        }

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
}

@Composable
private fun QuickLog(
    log: DailyLogEntity?,
    goal: Int,
    mood: MoodEntity?,
    onWater: () -> Unit,
    onVitamins: () -> Unit,
    onMood: (Int) -> Unit,
    onStepsGoal: () -> Unit,
) {
    Column {
        SectionHeader(title = "Quick log", overline = "One tap each")
        Spacer(Modifier.height(Space.md))

        Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            // Water — the tile is a vessel that visibly fills as she taps.
            WaterTile(
                glasses = log?.waterGlasses ?: 0,
                goal = goal,
                onTap = onWater,
                modifier = Modifier.weight(1f),
            )
            VitaminsTile(
                taken = log?.tookVitamins == true,
                onTap = onVitamins,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(Space.md))
        // Steps on its own row: three tiles across was cramped on real
        // devices, and the step tile's progress bar earns the width.
        StepsTile(
            onGoalReached = onStepsGoal,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(Space.md))

        // "How are you feeling?" — modernized: the card itself takes on the
        // colour of her chosen mood, the title becomes a soft acknowledgement,
        // and the picked face springs forward with a glow. One tap still does
        // everything; the redesign is entirely in how it responds.
        val moodTints = listOf(
            PregaTheme.colors.terracottaSoft,  // rough
            PregaTheme.colors.goldSoft,        // low
            PregaTheme.colors.lavenderSoft,    // okay
            PregaTheme.colors.sageSoft,        // good
            PregaTheme.colors.successSoft,     // great
        )
        // takeIf guards a latent IndexOutOfBounds: Kotlin's % keeps negatives
        // negative, so a mood of 0 would index position -1. Mood values are
        // 1..5 by construction, but a crash-on-render is too high a price to
        // bet on "by construction".
        val selectedIndex = mood?.mood?.takeIf { it in 1..MOODS.size }?.minus(1)
        val cardTint by animateColorAsState(
            targetValue = selectedIndex?.let { moodTints[it % moodTints.size] }
                ?: PregaTheme.colors.cardSurface,
            animationSpec = tween(500),
            label = "moodCardTint",
        )

        PregaCard(containerColor = cardTint, border = selectedIndex == null) {
            Crossfade(targetState = selectedIndex, label = "moodTitle") { sel ->
                if (sel == null) {
                    Text(
                        "How are you feeling?",
                        style = MaterialTheme.typography.titleMedium,
                        color = PregaTheme.colors.ink,
                    )
                } else {
                    Text(
                        "Feeling ${MOODS[sel].second.lowercase()} today — noted 🌸",
                        style = MaterialTheme.typography.titleMedium,
                        color = PregaTheme.colors.ink,
                    )
                }
            }
            Spacer(Modifier.height(Space.md))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                MOODS.forEachIndexed { i, (emoji, label) ->
                    MoodOption(
                        emoji = emoji,
                        label = label,
                        index = i,
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

/**
 * The reference designs' mood row: each feeling is a face in its own softly
 * tinted circle, and the chosen one wears a ring — selection you can see
 * from across the room, no filled backgrounds or checkmarks needed. Tints
 * run warm at the hard end to cool-calm at the good end, but no mood is
 * styled as a wrong answer.
 */
@Composable
private fun MoodOption(
    emoji: String,
    label: String,
    index: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val circleTints = listOf(
        PregaTheme.colors.terracottaSoft,  // rough
        PregaTheme.colors.goldSoft,        // low
        PregaTheme.colors.lavenderSoft,    // okay
        PregaTheme.colors.sageSoft,        // good
        PregaTheme.colors.successSoft,     // great
    )
    val ring by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = Motion.playful(),
        label = "moodRing",
    )
    // Springs OVERSHOOT: on deselect (1 -> 0) the value briefly dips BELOW
    // zero before settling, and a negative number fed into padding() or
    // border() throws IllegalArgumentException and takes the whole Today
    // screen down — the exact crash captured by the flight recorder
    // (HomeScreen.kt MoodOption: "Padding must be non-negative"). Clamp
    // once, use everywhere below.
    val ringSafe = ring.coerceIn(0f, 1.5f)
    // The chosen face springs forward — a small, alive moment, not a bounce
    // house. Unselected faces step back slightly so the choice reads at a
    // glance even before the ring lands.
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.18f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "moodScale",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = Space.xs, vertical = Space.sm),
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Soft radial glow behind the chosen face.
            if (ringSafe > 0f) {
                Box(
                    Modifier
                        .size(58.dp)
                        .graphicsLayer { alpha = (ringSafe * 0.8f).coerceIn(0f, 1f) }
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    circleTints[index % circleTints.size],
                                    Color.Transparent,
                                ),
                            ),
                            shape = CircleShape,
                        ),
                )
            }
            Box(
                Modifier
                    .size(46.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .then(
                        if (ringSafe > 0f) Modifier.border(
                            width = (2 * ringSafe).dp,
                            color = PregaTheme.colors.sage,
                            shape = CircleShape,
                        ) else Modifier
                    )
                    .padding((3 * ringSafe).dp)
                    .clip(CircleShape)
                    .background(circleTints[index % circleTints.size]),
                contentAlignment = Alignment.Center,
            ) {
                Text(emoji, fontSize = 20.sp)
            }
        }
        Spacer(Modifier.height(Space.xs))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) PregaTheme.colors.ink else PregaTheme.colors.inkFaint,
        )
    }
}

@Composable
private fun VitaminsTile(
    taken: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The icon swap (pill -> tick) plus a colour shift is the state change;
    // the pop spring makes ticking it feel like an accomplishment.
    val iconScale by animateFloatAsState(
        targetValue = if (taken) 1f else 0.92f,
        animationSpec = Motion.playful(),
        label = "vitaminScale",
    )

    PregaCard(
        modifier = modifier.heightIn(min = 112.dp),
        onClick = onTap,
        containerColor = if (taken) PregaTheme.colors.successSoft
        else PregaTheme.colors.goldSoft,
        border = false,
        contentPadding = PaddingValues(Space.lg),
    ) {
        Icon(
            imageVector = if (taken) Icons.Filled.CheckCircle
            else Icons.Outlined.Medication,
            contentDescription = null,
            tint = if (taken) PregaTheme.colors.success else PregaTheme.colors.gold,
            modifier = Modifier
                .size(26.dp)
                .graphicsLayer { scaleX = iconScale; scaleY = iconScale },
        )
        Spacer(Modifier.height(Space.sm))
        Text("Vitamins", style = MaterialTheme.typography.bodySmall, color = PregaTheme.colors.inkMuted)
        Text(
            if (taken) "Taken" else "Not yet",
            style = MaterialTheme.typography.titleLarge,
            color = PregaTheme.colors.ink,
        )
        Spacer(Modifier.weight(1f))
        Text(
            if (taken) "Nice one" else "Tap when you have",
            style = MaterialTheme.typography.bodySmall,
            color = PregaTheme.colors.inkFaint,
        )
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
                // A live streak breathes; a broken one sits still. The flame
                // only animates when there is something to celebrate.
                if (progress.currentStreak > 0) {
                    Breathing(minScale = 0.94f, maxScale = 1.06f) {
                        Stat("🔥", "${progress.currentStreak}", "day streak")
                    }
                } else {
                    Stat("🔥", "${progress.currentStreak}", "day streak")
                }
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

/**
 * "March 2027" from her due date — or derived from the current week when the
 * stored date isn't clean ISO (formats drifted across onboarding versions).
 * Mirrors the derivation the community overlay uses, so the banner and the
 * club she actually lands in always agree. Null only if everything fails.
 */
private fun dueMonthLabelFor(dueDate: String?, currentWeek: Int): String? = runCatching {
    val iso = dueDate?.takeIf { Regex("""\d{4}-\d{2}-\d{2}""").matches(it) }
        ?: java.time.LocalDate.now()
            .plusWeeks((40 - currentWeek).coerceAtLeast(0).toLong())
            .toString()
    val date = java.time.LocalDate.parse(iso)
    date.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault()) +
        " " + date.year
}.getOrNull()
