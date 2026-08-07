package com.example.ui.tour

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.example.ui.components.*
import com.example.ui.theme.Motion
import com.example.ui.theme.PregaTheme
import com.example.ui.theme.Space
import kotlinx.coroutines.launch

/**
 * Prega AI — first-launch welcome tour.
 *
 * Shown exactly once, the first time she lands on the home screen after
 * onboarding. Five swipeable pages that answer, in order, the questions a
 * new user actually has: what is this → what does it do for me each day →
 * who am I talking to → what grows if I stay → is my data safe, and what
 * costs money.
 *
 * The privacy page is the one that matters most and is written in plain
 * words: her data stays on the phone, is never uploaded or sold, and the
 * app works fully without any account. The premium page is honest by the
 * same standard: it says what costs money AND that everything that keeps
 * her safe is free forever.
 *
 * Skippable from every page — a tour she can't leave is a hostage
 * situation, not a welcome.
 */
object TourPreference {
    private const val PREFS = "prega_display"
    private const val KEY = "welcome_tour_seen"
    fun seen(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)
    fun markSeen(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(KEY, true) }
}

private data class TourPage(
    val title: String,
    val body: String,
)

private val PAGES = listOf(
    TourPage(
        "Welcome to Prega",
        "A calm companion for the whole journey — one week at a time, at your pace.",
    ),
    TourPage(
        "A little, every day",
        "Log water and vitamins in one tap. Three small quests a day. Count kicks when it matters. Nothing ever nags.",
    ),
    TourPage(
        "Ask Prega AI anything",
        "Day or 3am — food, sleep, what's normal this week. Short, plain answers. And it will always tell you when something needs your midwife, not an app.",
    ),
    TourPage(
        "Your garden grows",
        "Every day you show up, a flower blooms. Your garden never wilts and never resets — it only ever grows.",
    ),
    TourPage(
        "Private, and mostly free",
        "Everything you log stays on this phone — never uploaded, never sold, no account needed. Tracking, kicks, safety guidance: free forever. Premium adds unlimited AI questions and meal plans, through Google Play.",
    ),
)

@Composable
fun WelcomeTour(
    visible: Boolean,
    onDone: () -> Unit,
) {
    AnimatedVisibility(visible = visible, enter = Motion.riseIn(), exit = Motion.riseOut()) {
        val pager = rememberPagerState { PAGES.size }
        val scope = rememberCoroutineScope()
        val isLast = pager.currentPage == PAGES.lastIndex

        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .systemBarsPadding()
                .padding(horizontal = Space.gutter),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (!isLast) {
                    PregaTextButton("Skip", onDone)
                } else {
                    Spacer(Modifier.height(48.dp))
                }
            }

            HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { index ->
                TourPageContent(index)
            }

            // Page dots.
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = Space.lg),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(PAGES.size) { i ->
                    val active = i == pager.currentPage
                    val dotScale by animateFloatAsState(
                        targetValue = if (active) 1f else 0.7f,
                        animationSpec = Motion.playful(),
                        label = "dot$i",
                    )
                    Box(
                        Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (active) 9.dp else 7.dp)
                            .scale(dotScale)
                            .clip(CircleShape)
                            .background(
                                if (active) MaterialTheme.colorScheme.primary
                                else PregaTheme.colors.hairline
                            ),
                    )
                }
            }

            PregaButton(
                text = if (isLast) "Let's begin" else "Next",
                onClick = {
                    if (isLast) onDone()
                    else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                },
            )
            Spacer(Modifier.height(Space.xl))
        }
    }
}

@Composable
private fun TourPageContent(index: Int) {
    val page = PAGES[index]

    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Each page gets its own living illustration, reusing the app's real
        // art so the tour previews the product truthfully.
        Box(Modifier.size(210.dp), contentAlignment = Alignment.Center) {
            when (index) {
                0 -> {
                    PetalDrift(Modifier.fillMaxSize(), petalCount = 8)
                    Breathing(minScale = 0.985f, maxScale = 1.015f) {
                        MotherLineArt(
                            modifier = Modifier.size(180.dp),
                            line = PregaTheme.colors.ink.copy(alpha = 0.85f),
                        )
                    }
                }
                1 -> Breathing { Text("🌸", fontSize = 84.sp) }
                2 -> Breathing(minScale = 0.97f, maxScale = 1.03f) {
                    Box(
                        Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(PregaTheme.colors.lavenderSoft),
                        contentAlignment = Alignment.Center,
                    ) { Text("💬", fontSize = 48.sp) }
                }
                3 -> FootprintTrail(
                    color = PregaTheme.colors.terracotta.copy(alpha = 0.85f),
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(120.dp),
                )
                else -> Box(
                    Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(PregaTheme.colors.sageSoft),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = PregaTheme.colors.sage,
                        modifier = Modifier.size(52.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(Space.xl))
        Text(
            page.title,
            style = MaterialTheme.typography.displaySmall,
            color = PregaTheme.colors.ink,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Space.md))
        Text(
            page.body,
            style = MaterialTheme.typography.bodyLarge,
            color = PregaTheme.colors.inkMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = Space.lg),
        )
    }
}
