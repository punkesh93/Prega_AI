package com.example.ui.journey

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
 * Prega AI — Journey timeline.
 *
 * All 40 weeks as a single scrollable spine, auto-scrolled to where she is now.
 *
 * Past weeks stay fully legible rather than being greyed out — this doubles as
 * a record of what she's already come through, which is most of its emotional
 * value. Future weeks are dimmed slightly to keep "now" findable, not to lock
 * them; she can read ahead freely, because people do, and pretending otherwise
 * just sends her to Google.
 */
@Composable
fun JourneyScreen(
    currentWeek: Int,
    weekInfoFor: (Int) -> WeekInfo,
    babyName: String,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val weeks = remember { (1..40).toList() }

    LaunchedEffect(currentWeek) {
        // Land a week early so "now" isn't jammed against the top edge.
        listState.scrollToItem((currentWeek - 2).coerceAtLeast(0))
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(
            start = Space.gutter,
            end = Space.gutter,
            top = Space.lg,
            bottom = Space.navClearance,
        ),
    ) {
        items(weeks, key = { it }) { week ->
            WeekRow(
                info = weekInfoFor(week),
                isCurrent = week == currentWeek,
                isPast = week < currentWeek,
                babyName = babyName,
                isLast = week == 40,
            )
        }
    }
}

@Composable
private fun WeekRow(
    info: WeekInfo,
    isCurrent: Boolean,
    isPast: Boolean,
    babyName: String,
    isLast: Boolean,
) {
    val tint = trimesterColor(info.trimester)

    Row(Modifier.height(IntrinsicSize.Min)) {

        // The spine: a filled dot for weeks reached, a hollow one for ahead.
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

        Column(Modifier.padding(bottom = Space.lg)) {
            if (isCurrent) {
                CurrentWeekCard(info, babyName, tint)
            } else {
                Column(
                    Modifier
                        .padding(top = Space.md)
                        // Only future weeks dim, and only slightly.
                        .alpha(if (isPast) 1f else 0.55f)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(info.iconEmoji, fontSize = 22.sp)
                        Spacer(Modifier.width(Space.sm))
                        Text(
                            "Week ${info.week}",
                            style = MaterialTheme.typography.titleMedium,
                            color = PregaTheme.colors.ink,
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
    }
}

@Composable
private fun CurrentWeekCard(info: WeekInfo, babyName: String, tint: Color) {
    GradientCard(
        brush = androidx.compose.ui.graphics.Brush.linearGradient(
            listOf(tint.copy(alpha = 0.92f), tint.copy(alpha = 0.7f))
        ),
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(Space.lg),
    ) {
        Overline("You are here", color = Color.White.copy(alpha = 0.85f))
        Spacer(Modifier.height(Space.sm))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Week ${info.week}",
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White,
                )
                Text(
                    if (babyName.isBlank()) info.sizeName else "$babyName · ${info.sizeName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f),
                )
            }
            Breathing { Text(info.iconEmoji, fontSize = 46.sp) }
        }

        Spacer(Modifier.height(Space.md))
        Text(
            info.description,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.95f),
        )
    }
}
