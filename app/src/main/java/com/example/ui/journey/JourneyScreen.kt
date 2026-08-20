package com.example.ui.journey

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.*
import com.example.ui.theme.PregaTheme
import com.example.ui.theme.Space
import com.example.ui.theme.trimesterColor
import com.example.viewmodel.WeekInfo

/**
 * Prega AI — the Journey tab, redesigned around what the sparse first
 * version was missing (driven by the user's reference mockups):
 *
 *  - a week-chip carousel so any week is one tap away, not forty scrolls
 *  - a rich "you are here" hero that crossfades as chips are tapped, with
 *    the stats strip (length / weight / countdown)
 *  - this week's highlights: baby development AND, at last, the mother —
 *    what she might notice, and one gentle tip (WeeklyExtras)
 *  - compare-by-week: last week vs the selected week at a glance
 *  - trimester progress, then the Bloom Ring, then the full 40-week
 *    timeline for the readers
 *
 * The chips select any week; "you are here" styling follows the CURRENT
 * week only, so exploration never lies about where she actually is.
 */
@Composable
fun JourneyScreen(
    currentWeek: Int,
    weekInfoFor: (Int) -> WeekInfo,
    babyName: String,
    modifier: Modifier = Modifier,
) {
    var selectedWeek by rememberSaveable { mutableStateOf(0) }
    val shownWeek = if (selectedWeek == 0) currentWeek else selectedWeek
    LaunchedEffect(currentWeek) { if (selectedWeek == 0) selectedWeek = currentWeek }

    val chipState = rememberLazyListState()
    LaunchedEffect(currentWeek) {
        chipState.scrollToItem((currentWeek - 2).coerceAtLeast(0))
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(top = Space.lg, bottom = Space.navClearance),
    ) {
        item(key = "header") {
            Column(Modifier.padding(horizontal = Space.gutter)) {
                Text(
                    "Your journey",
                    style = MaterialTheme.typography.headlineLarge,
                    color = PregaTheme.colors.ink,
                )
                Text(
                    "Week by week, for both of you",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PregaTheme.colors.inkMuted,
                )
                Spacer(Modifier.height(Space.md))
            }
        }

        item(key = "chips") {
            LazyRow(
                state = chipState,
                contentPadding = PaddingValues(horizontal = Space.gutter),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                items((1..40).toList(), key = { it }) { week ->
                    WeekChip(
                        info = weekInfoFor(week),
                        selected = week == shownWeek,
                        isCurrent = week == currentWeek,
                        onClick = { selectedWeek = week },
                    )
                }
            }
            Spacer(Modifier.height(Space.md))
        }

        item(key = "hero") {
            Box(Modifier.padding(horizontal = Space.gutter)) {
                AnimatedContent(
                    targetState = shownWeek,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "weekHero",
                ) { week ->
                    SelectedWeekHero(
                        info = weekInfoFor(week),
                        isCurrent = week == currentWeek,
                        babyName = babyName,
                        daysToGo = ((40 - week) * 7),
                    )
                }
            }
            Spacer(Modifier.height(Space.md))
        }

        item(key = "highlights") {
            Column(Modifier.padding(horizontal = Space.gutter)) {
                HighlightsCard(weekInfoFor(shownWeek))
                Spacer(Modifier.height(Space.md))
            }
        }

        item(key = "body_this_week") {
            Column(Modifier.padding(horizontal = Space.gutter)) {
                BodyThisWeekCard(shownWeek)
                Spacer(Modifier.height(Space.md))
            }
        }

        item(key = "compare") {
            if (shownWeek > 1) {
                Column(Modifier.padding(horizontal = Space.gutter)) {
                    CompareCard(
                        prev = weekInfoFor(shownWeek - 1),
                        curr = weekInfoFor(shownWeek),
                    )
                    Spacer(Modifier.height(Space.md))
                }
            }
        }

        item(key = "trimester_progress") {
            Column(Modifier.padding(horizontal = Space.gutter)) {
                TrimesterProgress(currentWeek)
                Spacer(Modifier.height(Space.lg))
            }
        }

        item(key = "bloom_ring") {
            Column(Modifier.padding(horizontal = Space.gutter)) {
                RingHeader(currentWeek)
            }
        }

        item(key = "timeline_header") {
            Column(Modifier.padding(horizontal = Space.gutter)) {
                SectionHeader(title = "All forty weeks", overline = "The long view")
                Spacer(Modifier.height(Space.md))
            }
        }

        items((1..40).toList(), key = { "w$it" }) { week ->
            Box(Modifier.padding(horizontal = Space.gutter)) {
                WeekRow(
                    info = weekInfoFor(week),
                    isCurrent = week == currentWeek,
                    isPast = week < currentWeek,
                    isLast = week == 40,
                )
            }
        }
    }
}

// ─── Week chips ────────────────────────────────────────────────────────────

@Composable
private fun WeekChip(
    info: WeekInfo,
    selected: Boolean,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    val borderWidth by animateDpAsState(
        targetValue = if (selected) 2.dp else 1.dp,
        label = "chipBorder",
    )
    val bg = when {
        selected -> PregaTheme.colors.sageSoft
        else -> PregaTheme.colors.cardSurface
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .background(bg)
            .border(
                width = borderWidth,
                color = if (selected) PregaTheme.colors.sage else PregaTheme.colors.hairline,
                shape = MaterialTheme.shapes.medium,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = Space.md, vertical = Space.sm)
            .width(64.dp),
    ) {
        Text(info.iconEmoji, fontSize = 24.sp)
        Spacer(Modifier.height(2.dp))
        Text(
            if (isCurrent) "Now" else "Wk ${info.week}",
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) PregaTheme.colors.ink else PregaTheme.colors.inkMuted,
        )
    }
}

// ─── Hero ──────────────────────────────────────────────────────────────────

@Composable
private fun SelectedWeekHero(
    info: WeekInfo,
    isCurrent: Boolean,
    babyName: String,
    daysToGo: Int,
) {
    val tint = trimesterColor(info.trimester)

    PregaCard(
        containerColor = if (isCurrent) PregaTheme.colors.terracottaSoft
        else PregaTheme.colors.cardSurface,
        border = !isCurrent,
        contentPadding = PaddingValues(Space.lg),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (isCurrent) {
            Overline("You are here")
            Spacer(Modifier.height(Space.xs))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Week ${info.week}",
                    style = MaterialTheme.typography.displaySmall,
                    color = PregaTheme.colors.ink,
                )
                Text(
                    if (babyName.isBlank() || !isCurrent) info.sizeName
                    else "$babyName · ${info.sizeName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PregaTheme.colors.inkMuted,
                )
            }
            Box(
                Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(PregaTheme.colors.cardSurface.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center,
            ) {
                Breathing { Text(info.iconEmoji, fontSize = 34.sp) }
            }
        }

        Spacer(Modifier.height(Space.md))
        Text(
            info.description,
            style = MaterialTheme.typography.bodyMedium,
            color = PregaTheme.colors.ink,
        )

        Spacer(Modifier.height(Space.md))
        // The mockups' stats strip.
        Row(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(PregaTheme.colors.cardSurface.copy(alpha = 0.7f))
                .padding(vertical = Space.sm),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            StatCell("${info.lengthCm} cm", "length")
            StatDivider(tint)
            StatCell(formatWeight(info.weightGrams), "weight")
            StatDivider(tint)
            StatCell(if (daysToGo > 0) "$daysToGo" else "0", "days to go")
        }
    }
}

@Composable
private fun StatCell(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = PregaTheme.colors.ink,
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = PregaTheme.colors.inkFaint,
        )
    }
}

@Composable
private fun StatDivider(tint: Color) {
    Box(
        Modifier
            .width(1.dp)
            .height(34.dp)
            .background(tint.copy(alpha = 0.25f)),
    )
}

// ─── Highlights ────────────────────────────────────────────────────────────

@Composable
private fun HighlightsCard(info: WeekInfo) {
    val extras = WeeklyExtras.forWeek(info.week)

    PregaCard(
        containerColor = PregaTheme.colors.sageSoft,
        border = false,
    ) {
        Overline("This week's highlights")
        Spacer(Modifier.height(Space.sm))
        // The "Growing" row used to repeat info.description — the exact
        // sentence sitting in the hero directly above it, twice on one
        // screen. The hero owns growth; highlights now add only what's new.
        HighlightRow("💛", "You might notice", extras.notice)
        Spacer(Modifier.height(Space.sm))
        HighlightRow("🕊️", "A gentle tip", extras.tip)
    }
}

@Composable
private fun HighlightRow(emoji: String, title: String, body: String) {
    Row {
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(PregaTheme.colors.cardSurface.copy(alpha = 0.8f)),
            contentAlignment = Alignment.Center,
        ) { Text(emoji, fontSize = 17.sp) }
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = PregaTheme.colors.ink,
            )
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = PregaTheme.colors.inkMuted,
            )
        }
    }
}

// ─── Your body this week ───────────────────────────────────────────────────

/**
 * The likely-symptoms card: three things many women feel at this stage, each
 * with what gently helps and one plain sentence of why. Deliberately capped
 * at three — enough to feel understood and equipped, never enough to feel
 * diagnosed — and it always closes with the same honest line about when a
 * feeling stops being an app's business.
 */
@Composable
private fun BodyThisWeekCard(week: Int) {
    val symptoms = WeeklyExtras.symptomsForWeek(week)

    PregaCard(
        containerColor = PregaTheme.colors.lavenderSoft,
        border = false,
    ) {
        Overline("Your body this week")
        Spacer(Modifier.height(Space.sm))
        symptoms.forEachIndexed { i, sy ->
            if (i > 0) Spacer(Modifier.height(Space.md))
            Text(
                sy.name,
                style = MaterialTheme.typography.titleSmall,
                color = PregaTheme.colors.ink,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                sy.doThis,
                style = MaterialTheme.typography.bodySmall,
                color = PregaTheme.colors.ink,
            )
            Text(
                "Why: ${sy.why}",
                style = MaterialTheme.typography.bodySmall,
                color = PregaTheme.colors.inkMuted,
            )
        }
        Spacer(Modifier.height(Space.md))
        Text(
            "Every body does this differently. Anything sharp, sudden, or that " +
                "simply feels wrong is a midwife question, not an app question.",
            style = MaterialTheme.typography.bodySmall,
            color = PregaTheme.colors.inkFaint,
        )
    }
}

// ─── Compare ───────────────────────────────────────────────────────────────

@Composable
private fun CompareCard(prev: WeekInfo, curr: WeekInfo) {
    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
        CompareCell(prev, Modifier.weight(1f))
        Column(
            Modifier.align(Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "vs",
                style = MaterialTheme.typography.labelMedium,
                color = PregaTheme.colors.inkFaint,
            )
        }
        CompareCell(curr, Modifier.weight(1f), emphasised = true)
    }
}

@Composable
private fun CompareCell(info: WeekInfo, modifier: Modifier = Modifier, emphasised: Boolean = false) {
    PregaCard(
        containerColor = if (emphasised) PregaTheme.colors.goldSoft
        else PregaTheme.colors.cardSurface,
        border = !emphasised,
        contentPadding = PaddingValues(Space.md),
        modifier = modifier,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(info.iconEmoji, fontSize = 26.sp)
            Spacer(Modifier.height(Space.xs))
            Text(
                "Wk ${info.week}",
                style = MaterialTheme.typography.titleSmall,
                color = PregaTheme.colors.ink,
            )
            Text(
                "${info.lengthCm} cm · ${formatWeight(info.weightGrams)}",
                style = MaterialTheme.typography.bodySmall,
                color = PregaTheme.colors.inkMuted,
            )
        }
    }
}

// ─── Trimester progress ────────────────────────────────────────────────────

@Composable
private fun TrimesterProgress(currentWeek: Int) {
    val trimester = when {
        currentWeek <= 13 -> 1
        currentWeek <= 27 -> 2
        else -> 3
    }
    val (start, end) = when (trimester) {
        1 -> 1 to 13
        2 -> 14 to 27
        else -> 28 to 40
    }
    val fraction = ((currentWeek - start + 1).toFloat() / (end - start + 1)).coerceIn(0f, 1f)
    val animated by animateFloatAsState(fraction, label = "triProgress")

    PregaCard(border = true) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "You're in trimester $trimester",
                style = MaterialTheme.typography.titleMedium,
                color = PregaTheme.colors.ink,
            )
            Text(
                "${(fraction * 100).toInt()}%",
                style = MaterialTheme.typography.titleMedium,
                color = PregaTheme.colors.sage,
            )
        }
        Spacer(Modifier.height(Space.sm))
        PregaProgressBar(progress = animated, height = 6.dp)
        Spacer(Modifier.height(Space.xs))
        Text(
            if (end - currentWeek > 0) "Ends in ${end - currentWeek + 1} weeks" else "Final stretch",
            style = MaterialTheme.typography.bodySmall,
            color = PregaTheme.colors.inkMuted,
        )
    }
}

// ─── Bloom ring (kept from the original design) ────────────────────────────

@Composable
private fun RingHeader(currentWeek: Int) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val ringSize = (maxWidth * 0.62f).coerceIn(160.dp, 232.dp)
        Column(
            Modifier
                .fillMaxWidth()
                .padding(bottom = Space.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BloomRing(currentWeek = currentWeek, size = ringSize) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "$currentWeek",
                        style = MaterialTheme.typography.displayMedium,
                        color = PregaTheme.colors.ink,
                    )
                    Text(
                        "of 40 weeks",
                        style = MaterialTheme.typography.bodySmall,
                        color = PregaTheme.colors.inkMuted,
                    )
                }
            }

            Spacer(Modifier.height(Space.md))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.lg)) {
                LegendDot(BloomRingColors.first, "1st")
                LegendDot(BloomRingColors.second, "2nd")
                LegendDot(BloomRingColors.third, "3rd")
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(Space.xs))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = PregaTheme.colors.inkFaint,
        )
    }
}

// ─── Timeline rows ─────────────────────────────────────────────────────────

@Composable
private fun WeekRow(
    info: WeekInfo,
    isCurrent: Boolean,
    isPast: Boolean,
    isLast: Boolean,
) {
    val tint = trimesterColor(info.trimester)

    Row(Modifier.height(IntrinsicSize.Min)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(40.dp),
        ) {
            Box(
                Modifier
                    .padding(top = Space.lg)
                    .size(if (isCurrent) 18.dp else 11.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isCurrent -> tint
                            isPast -> tint.copy(alpha = 0.55f)
                            else -> PregaTheme.colors.hairline
                        }
                    ),
            )
            if (!isLast) {
                Box(
                    Modifier
                        .width(2.dp)
                        .weight(1f)
                        .background(
                            if (isPast || isCurrent) tint.copy(alpha = 0.28f)
                            else PregaTheme.colors.hairline
                        )
                )
            }
        }

        Spacer(Modifier.width(Space.md))

        Column(
            Modifier
                .padding(bottom = Space.lg, top = Space.md)
                .alpha(if (isPast || isCurrent) 1f else 0.55f)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(info.iconEmoji, fontSize = 22.sp)
                Spacer(Modifier.width(Space.sm))
                Text(
                    "Week ${info.week}",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isCurrent) tint else PregaTheme.colors.ink,
                )
                Spacer(Modifier.width(Space.sm))
                Text(
                    info.sizeName,
                    style = MaterialTheme.typography.bodySmall,
                    color = PregaTheme.colors.inkFaint,
                )
            }
            Spacer(Modifier.height(Space.xs))
            Text(
                info.description,
                style = MaterialTheme.typography.bodySmall,
                color = PregaTheme.colors.inkMuted,
            )
        }
    }
}

private fun formatWeight(grams: Double): String =
    if (grams < 1000) "${grams.toInt()} g"
    else String.format("%.1f kg", grams / 1000)
