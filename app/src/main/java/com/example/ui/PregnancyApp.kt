package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.BadgeDef
import com.example.data.UserProfileEntity
import com.example.ui.coach.CoachScreen
import com.example.ui.components.BadgeReveal
import com.example.ui.components.RewardToast
import com.example.ui.home.HomeScreen
import com.example.ui.home.HomeState
import com.example.ui.journey.JourneyScreen
import com.example.ui.kicks.KickCounterScreen
import com.example.ui.onboarding.OnboardingScreen
import com.example.ui.paywall.PaywallScreen
import com.example.ui.rewards.BadgesScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.theme.Motion
import com.example.ui.theme.PregaTheme
import com.example.ui.theme.Space
import com.example.viewmodel.PregnancyViewModel
import com.example.viewmodel.RewardEvent
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Prega AI — application shell.
 *
 * This file previously held every screen and dialog in the app across 4,800
 * lines. It is now purely navigation and cross-cutting overlays; each screen
 * lives in its own package under ui/.
 *
 * Five tabs, chosen so nothing important is more than one tap away: Today,
 * Journey, Kicks, Coach, You. Kicks gets its own tab despite being "just" a
 * tracker because it's the feature someone may need to reach quickly and while
 * distracted.
 */

private enum class Tab(val label: String, val emoji: String) {
    Today("Today", "🌸"),
    Journey("Journey", "🗓"),
    Kicks("Kicks", "👣"),
    Coach("Coach", "💬"),
    You("You", "👤"),
}

@Composable
fun PregnancyApp(
    viewModel: PregnancyViewModel,
    billingState: com.example.billing.BillingManager.State =
        com.example.billing.BillingManager.State.Unavailable,
    premiumPrice: String? = null,
    billingError: String? = null,
    onSubscribe: () -> Unit = {},
    onManageSubscription: () -> Unit = {},
    onNotificationsToggled: (Boolean) -> Unit = {},
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val current = profile

    // Onboarding owns the whole screen until it's done — no chrome, no tabs.
    if (current == null || !current.onboardingComplete) {
        OnboardingScreen(
            weekDescriber = { week ->
                val info = viewModel.getWeekInfo(week)
                Triple(info.sizeName, info.iconEmoji, info.description)
            },
            onComplete = { result ->
                viewModel.completeOnboarding(result)
                onNotificationsToggled(result.notificationsEnabled)
            },
        )
        return
    }

    MainScaffold(
        viewModel = viewModel,
        profile = current,
        billingState = billingState,
        premiumPrice = premiumPrice,
        billingError = billingError,
        onSubscribe = onSubscribe,
        onManageSubscription = onManageSubscription,
        onNotificationsToggled = onNotificationsToggled,
    )
}

@Composable
private fun MainScaffold(
    viewModel: PregnancyViewModel,
    profile: UserProfileEntity,
    billingState: com.example.billing.BillingManager.State,
    premiumPrice: String?,
    billingError: String?,
    onSubscribe: () -> Unit,
    onManageSubscription: () -> Unit,
    onNotificationsToggled: (Boolean) -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(Tab.Today) }
    var showPaywall by rememberSaveable { mutableStateOf(false) }

    val todayLog by viewModel.todayLog.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val quests by viewModel.todayQuests.collectAsStateWithLifecycle()
    val badges by viewModel.badges.collectAsStateWithLifecycle()
    val mood by viewModel.moodToday.collectAsStateWithLifecycle()
    val insight by viewModel.dailyInsight.collectAsStateWithLifecycle()
    val nextAppointment by viewModel.nextAppointment.collectAsStateWithLifecycle()
    val kickLogs by viewModel.kickLogs.collectAsStateWithLifecycle()
    val isCounting by viewModel.isCountingKicks.collectAsStateWithLifecycle()
    val sessionKicks by viewModel.currentSessionKicks.collectAsStateWithLifecycle()
    val sessionSeconds by viewModel.currentSessionSeconds.collectAsStateWithLifecycle()
    val messages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val chatLoading by viewModel.chatLoading.collectAsStateWithLifecycle()

    val weekInfo = remember(profile.currentWeek) { viewModel.getWeekInfo(profile.currentWeek) }

    // ── Reward overlays ──
    var toast by remember { mutableStateOf<Pair<String, String>?>(null) }
    var revealBadge by remember { mutableStateOf<BadgeDef?>(null) }

    LaunchedEffect(Unit) {
        viewModel.rewards.collect { event ->
            when (event) {
                is RewardEvent.Points -> toast = "✨" to "+${event.amount} · ${event.reason}"
                is RewardEvent.StreakExtended -> toast = "🔥" to "${event.days} day streak"
                // Framed as protection, never as a loss.
                is RewardEvent.StreakProtected ->
                    toast = "🛡️" to "Streak protected · ${event.remaining} grace days left"
                is RewardEvent.LevelUp -> toast = "👑" to "Level ${event.level} · ${event.title}"
                is RewardEvent.BadgeEarned -> revealBadge = event.badge
            }
        }
    }

    LaunchedEffect(toast) {
        if (toast != null) {
            delay(2600)
            toast = null
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = { BottomBar(tab) { tab = it } },
        ) { padding ->
            Crossfade(
                targetState = tab,
                animationSpec = Motion.crossfade(),
                modifier = Modifier.padding(padding),
                label = "tab",
            ) { selected ->
                when (selected) {
                    Tab.Today -> HomeScreen(
                        state = HomeState(
                            profile = profile,
                            weekInfo = weekInfo,
                            todayLog = todayLog,
                            progress = progress,
                            quests = quests,
                            insight = insight,
                            mood = mood,
                            nextAppointment = nextAppointment,
                            daysRemaining = daysUntil(profile.eddDate),
                        ),
                        onWater = viewModel::incrementWater,
                        onVitamins = viewModel::toggleVitamins,
                        onMood = { viewModel.logMood(it) },
                        onQuestComplete = viewModel::completeQuest,
                        onOpenKicks = { tab = Tab.Kicks },
                        onOpenCoach = { tab = Tab.Coach },
                        onOpenJourney = { tab = Tab.Journey },
                        onOpenAppointments = { tab = Tab.You },
                    )

                    Tab.Journey -> JourneyScreen(
                        currentWeek = profile.currentWeek,
                        weekInfoFor = viewModel::getWeekInfo,
                        babyName = profile.babyNamePlaceholder,
                    )

                    Tab.Kicks -> KickCounterScreen(
                        isCounting = isCounting,
                        sessionKicks = sessionKicks,
                        sessionSeconds = sessionSeconds,
                        history = kickLogs,
                        onStart = viewModel::startKickSession,
                        onKick = viewModel::logKick,
                        onStop = viewModel::stopAndSaveKickSession,
                        onCancel = viewModel::cancelKickSession,
                    )

                    Tab.Coach -> CoachScreen(
                        messages = messages,
                        loading = chatLoading,
                        week = profile.currentWeek,
                        isPremium = profile.isPremium,
                        questionsRemaining = profile.freeQuestionsRemaining,
                        onSend = viewModel::askCoach,
                        onUpgrade = { showPaywall = true },
                    )

                    Tab.You -> YouTab(
                        profile = profile,
                        badges = badges,
                        progress = progress,
                        viewModel = viewModel,
                        onUpgrade = { showPaywall = true },
                        onManageSubscription = onManageSubscription,
                        onNotificationsToggled = onNotificationsToggled,
                    )
                }
            }
        }

        // Toasts sit above the nav bar so they never cover the active tab.
        RewardToast(
            visible = toast != null,
            emoji = toast?.first.orEmpty(),
            text = toast?.second.orEmpty(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 96.dp),
        )

        BadgeReveal(
            badge = revealBadge,
            celebration = null,
            onDismiss = { revealBadge = null },
        )

        AnimatedVisibility(
            visible = showPaywall,
            enter = Motion.riseIn(),
            exit = Motion.riseOut(),
        ) {
            PaywallScreen(
                state = billingState,
                price = premiumPrice,
                error = billingError,
                onSubscribe = onSubscribe,
                onDismiss = { showPaywall = false },
            )
        }
    }
}

/** Badges and settings share a tab; a sub-toggle keeps the tab bar at five. */
@Composable
private fun YouTab(
    profile: UserProfileEntity,
    badges: List<com.example.data.BadgeEntity>,
    progress: com.example.data.ProgressEntity,
    viewModel: PregnancyViewModel,
    onUpgrade: () -> Unit,
    onManageSubscription: () -> Unit,
    onNotificationsToggled: (Boolean) -> Unit,
) {
    var showBadges by rememberSaveable { mutableStateOf(true) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.gutter, vertical = Space.md),
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            com.example.ui.components.PregaChip("Badges", showBadges, { showBadges = true })
            com.example.ui.components.PregaChip("Settings", !showBadges, { showBadges = false })
        }

        if (showBadges) {
            BadgesScreen(earned = badges, progress = progress)
        } else {
            SettingsScreen(
                profile = profile,
                onUpdateProfile = viewModel::updateProfileDetails,
                onNotificationsChanged = {
                    viewModel.setNotificationsEnabled(it)
                    onNotificationsToggled(it)
                },
                onWaterGoalChanged = viewModel::setWaterGoal,
                onManageSubscription = onManageSubscription,
                onUpgrade = onUpgrade,
                onExportData = viewModel::exportData,
                onDeleteEverything = viewModel::deleteAllUserData,
            )
        }
    }
}

@Composable
private fun BottomBar(selected: Tab, onSelect: (Tab) -> Unit) {
    NavigationBar(
        containerColor = PregaTheme.colors.cardSurface,
        tonalElevation = 0.dp,
    ) {
        Tab.entries.forEach { tab ->
            val isSelected = tab == selected
            NavigationBarItem(
                selected = isSelected,
                onClick = { onSelect(tab) },
                icon = {
                    Text(
                        tab.emoji,
                        fontSize = if (isSelected) 21.sp else 19.sp,
                    )
                },
                label = {
                    Text(
                        tab.label,
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedTextColor = PregaTheme.colors.inkFaint,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        }
    }
}

/** Days until the stored due date, or 0 if it isn't parseable. */
private fun daysUntil(eddDate: String): Int {
    if (eddDate.isBlank()) return 0
    val formats = listOf("d MMM yyyy", "MMM d, yyyy", "yyyy-MM-dd")
    for (pattern in formats) {
        val parsed = runCatching {
            LocalDate.parse(eddDate, DateTimeFormatter.ofPattern(pattern))
        }.getOrNull()
        if (parsed != null) {
            return ChronoUnit.DAYS.between(LocalDate.now(), parsed).toInt().coerceAtLeast(0)
        }
    }
    return 0
}
