package com.example.ui.rewards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BadgeDef
import com.example.data.BadgeEntity
import com.example.data.BadgeTier
import com.example.data.Badges
import com.example.data.ProgressEntity
import com.example.ui.components.*
import com.example.ui.theme.PregaTheme
import com.example.ui.theme.Space

/**
 * Prega AI — Badges.
 *
 * Framed as a keepsake shelf, not a completion checklist. Unearned badges are
 * shown dimmed rather than hidden, but the copy never says "locked" or
 * "0/22" — the point is what she has, not what she's missing.
 *
 * Keepsake-tier badges are listed first and separately, because those are the
 * ones tied to reaching a week of pregnancy. Nobody can fail to earn them, and
 * they are the ones she'll actually care about later.
 */
@Composable
fun BadgesScreen(
    earned: List<BadgeEntity>,
    progress: ProgressEntity,
    modifier: Modifier = Modifier,
) {
    val earnedById = earned.associateBy { it.id }
    val keepsakes = Badges.ALL.filter { it.tier == BadgeTier.Keepsake }
    val achievements = Badges.ALL.filter { it.tier != BadgeTier.Keepsake }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(
            start = Space.gutter,
            end = Space.gutter,
            top = Space.lg,
            bottom = Space.navClearance,
        ),
        horizontalArrangement = Arrangement.spacedBy(Space.md),
        verticalArrangement = Arrangement.spacedBy(Space.lg),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                SummaryCard(progress, earnedCount = earned.size)
                Spacer(Modifier.height(Space.xl))
                SectionHeader(
                    title = "Your journey",
                    overline = "Keepsakes",
                )
                Spacer(Modifier.height(Space.xs))
                Text(
                    "Earned simply by getting there.",
                    style = MaterialTheme.typography.bodySmall,
                    color = PregaTheme.colors.inkMuted,
                )
                Spacer(Modifier.height(Space.md))
            }
        }

        items(keepsakes, key = { it.id }) { badge ->
            BadgeTile(badge, earnedById[badge.id])
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Spacer(Modifier.height(Space.lg))
                SectionHeader(title = "Along the way", overline = "Badges")
                Spacer(Modifier.height(Space.md))
            }
        }

        items(achievements, key = { it.id }) { badge ->
            BadgeTile(badge, earnedById[badge.id])
        }
    }
}

@Composable
private fun SummaryCard(progress: ProgressEntity, earnedCount: Int) {
    PregaCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(
                progress = progress.levelProgress,
                size = 76.dp,
                strokeWidth = 8.dp,
                brush = PregaTheme.colors.goldBrush,
            ) {
                Text(
                    "${progress.level}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = PregaTheme.colors.ink,
                )
            }
            Spacer(Modifier.width(Space.lg))
            Column(Modifier.weight(1f)) {
                Text(
                    progress.levelTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    color = PregaTheme.colors.ink,
                )
                Spacer(Modifier.height(Space.xxs))
                Text(
                    "$earnedCount ${if (earnedCount == 1) "badge" else "badges"} · " +
                        "${progress.points} points",
                    style = MaterialTheme.typography.bodySmall,
                    color = PregaTheme.colors.inkMuted,
                )
            }
        }
    }
}

@Composable
private fun BadgeTile(badge: BadgeDef, earned: BadgeEntity?) {
    val isEarned = earned != null

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(
                    if (isEarned) tierColor(badge.tier).copy(alpha = 0.18f)
                    else PregaTheme.colors.recessed
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (isEarned) {
                // Earned badges breathe; unearned ones sit still. A small
                // difference that makes the shelf feel alive where it counts.
                Breathing(minScale = 0.97f, maxScale = 1.03f) {
                    Text(badge.emoji, fontSize = 30.sp)
                }
            } else {
                Text(
                    badge.emoji,
                    fontSize = 30.sp,
                    modifier = Modifier.alpha(0.22f),
                )
            }
        }

        Spacer(Modifier.height(Space.sm))
        Text(
            badge.name,
            style = MaterialTheme.typography.titleSmall,
            color = if (isEarned) PregaTheme.colors.ink else PregaTheme.colors.inkFaint,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Space.xxs))
        Text(
            // Earned badges show *when* — that's what makes them a keepsake
            // rather than a score.
            if (isEarned) "Week ${earned!!.earnedWeek}" else badge.description,
            style = MaterialTheme.typography.bodySmall,
            color = PregaTheme.colors.inkFaint,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun tierColor(tier: BadgeTier): Color = when (tier) {
    BadgeTier.Bronze -> PregaTheme.colors.terracotta
    BadgeTier.Silver -> PregaTheme.colors.lavender
    BadgeTier.Gold -> PregaTheme.colors.gold
    BadgeTier.Keepsake -> MaterialTheme.colorScheme.primary
}
