package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.LocalFlorist
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.*
import kotlinx.coroutines.launch
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.example.ui.onboarding.GoogleSignInStatus
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

/**
 * Tab icons are real vectors, not emoji. Emoji render differently on every
 * manufacturer's phone and can't be tinted to reflect selection state; icons
 * from the Material set are consistent everywhere and take theme colour.
 * Filled variant when selected, outlined when not — the standard affordance.
 */
private enum class Tab(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val iconSelected: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Today(
        "Today",
        Icons.Outlined.LocalFlorist,
        Icons.Filled.LocalFlorist,
    ),
    Journey(
        "Journey",
        Icons.Outlined.Timeline,
        Icons.Filled.Timeline,
    ),
    Kicks(
        "Kicks",
        Icons.Outlined.FavoriteBorder,
        Icons.Filled.Favorite,
    ),
    Coach(
        "Prega AI",
        Icons.Outlined.AutoAwesome,
        Icons.Filled.AutoAwesome,
    ),
    You(
        "You",
        Icons.Outlined.Person,
        Icons.Filled.Person,
    ),
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
    googleSignInStatus: GoogleSignInStatus = GoogleSignInStatus.Idle,
    onGoogleSignIn: () -> Unit = {},
    themeMode: com.example.ui.theme.ThemeMode = com.example.ui.theme.ThemeMode.System,
    onThemeModeChange: (com.example.ui.theme.ThemeMode) -> Unit = {},
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
            googleSignInStatus = googleSignInStatus,
            onGoogleSignIn = onGoogleSignIn,
            // If she signed in with Google before reaching the name step,
            // don't make her type it again.
            prefillName = current?.name.orEmpty(),
        )
        return
    }

    // First-launch tour: exactly once, on first arrival at the home screen.
    val context = androidx.compose.ui.platform.LocalContext.current
    var showTour by remember {
        mutableStateOf(!com.example.ui.tour.TourPreference.seen(context))
    }

    Box(Modifier.fillMaxSize()) {
    MainScaffold(
        viewModel = viewModel,
        profile = current,
        billingState = billingState,
        premiumPrice = premiumPrice,
        billingError = billingError,
        onSubscribe = onSubscribe,
        onManageSubscription = onManageSubscription,
        onNotificationsToggled = onNotificationsToggled,
        themeMode = themeMode,
        onThemeModeChange = onThemeModeChange,
    )

    com.example.ui.tour.WelcomeTour(
        visible = showTour,
        onDone = {
            showTour = false
            com.example.ui.tour.TourPreference.markSeen(context)
        },
    )
    }
}


/**
 * The slide-out quick menu: her profile at the top, then every destination
 * one tap away — asked for directly ("explore is at the bottom; give me a
 * slide with all quick menus and profile"). Reached from the avatar chip in
 * the home header or an edge swipe.
 */
@androidx.compose.runtime.Composable
private fun AppDrawer(
    name: String,
    week: Int,
    onGo: (String) -> Unit,
) {
    androidx.compose.material3.ModalDrawerSheet(
        drawerContainerColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
    ) {
        Column(Modifier.padding(Space.gutter)) {
            Spacer(Modifier.height(Space.xl))
            Box(
                Modifier
                    .size(64.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(PregaTheme.colors.sageSoft),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    name.take(1).uppercase().ifBlank { "P" },
                    style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
                    color = PregaTheme.colors.sage,
                )
            }
            Spacer(Modifier.height(Space.md))
            Text(
                name.ifBlank { "You" },
                style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
                color = PregaTheme.colors.ink,
            )
            Text(
                "Week $week",
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = PregaTheme.colors.inkMuted,
            )
            Spacer(Modifier.height(Space.xl))

            listOf(
                Triple("journal", "My journal", "📔"),
                Triple("garden", "My garden", "🌷"),
                Triple("appointments", "Appointments", "🗓️"),
                Triple("kicks", "Kick counter", "👣"),
                Triple("coach", "Ask Prega AI", "✨"),
                Triple("badges", "Badges", "🏅"),
                Triple("premium", "Premium", "🌸"),
                Triple("settings", "Settings", "⚙️"),
            ).forEach { (id, label, emoji) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(androidx.compose.material3.MaterialTheme.shapes.medium)
                        .clickable { onGo(id) }
                        .padding(vertical = Space.md, horizontal = Space.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(emoji, fontSize = 20.sp)
                    Spacer(Modifier.width(Space.md))
                    Text(
                        label,
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                        color = PregaTheme.colors.ink,
                    )
                }
            }
        }
    }
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
    themeMode: com.example.ui.theme.ThemeMode,
    onThemeModeChange: (com.example.ui.theme.ThemeMode) -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(Tab.Today) }
    var showPaywall by rememberSaveable { mutableStateOf(false) }
    var showJournal by rememberSaveable { mutableStateOf(false) }
    var showAppointments by rememberSaveable { mutableStateOf(false) }

    // Slide-out quick menu. Declared HERE — beside the tab/overlay state it
    // drives — after a scope bug: the first draft placed the drawer a level
    // up in PregnancyApp, where none of these symbols exist.
    val drawerState = androidx.compose.material3.rememberDrawerState(
        androidx.compose.material3.DrawerValue.Closed
    )
    val drawerScope = rememberCoroutineScope()

    val todayLog by viewModel.todayLog.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val quests by viewModel.todayQuests.collectAsStateWithLifecycle()
    val badges by viewModel.badges.collectAsStateWithLifecycle()
    val mood by viewModel.moodToday.collectAsStateWithLifecycle()
    val insight by viewModel.dailyInsight.collectAsStateWithLifecycle()
    val affirmation by viewModel.dailyAffirmation.collectAsStateWithLifecycle()
    val nextAppointment by viewModel.nextAppointment.collectAsStateWithLifecycle()
    val kickLogs by viewModel.kickLogs.collectAsStateWithLifecycle()
    val isCounting by viewModel.isCountingKicks.collectAsStateWithLifecycle()
    val sessionKicks by viewModel.currentSessionKicks.collectAsStateWithLifecycle()
    val sessionSeconds by viewModel.currentSessionSeconds.collectAsStateWithLifecycle()
    val messages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val journalEntries by viewModel.journalEntries.collectAsStateWithLifecycle()
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val isDarkResolved = themeMode == com.example.ui.theme.ThemeMode.Dark ||
        (themeMode == com.example.ui.theme.ThemeMode.System && systemDark)
    val checkedInToday by viewModel.checkedInToday.collectAsStateWithLifecycle()
    val chatLoading by viewModel.chatLoading.collectAsStateWithLifecycle()

    val weekInfo = remember(profile.currentWeek) { viewModel.getWeekInfo(profile.currentWeek) }

    // ── Reward overlays ──
    var toast by remember { mutableStateOf<Pair<String, String>?>(null) }
    var revealBadge by remember { mutableStateOf<BadgeDef?>(null) }

    // The system back gesture was closing the whole app from anywhere.
    // Back now means "one step out": overlay -> close it; non-home tab ->
    // home; home -> the system exits as normal.
    androidx.activity.compose.BackHandler(
        enabled = showJournal || showAppointments || showPaywall || tab != Tab.Today,
    ) {
        when {
            showJournal -> showJournal = false
            showAppointments -> showAppointments = false
            showPaywall -> showPaywall = false
            tab != Tab.Today -> tab = Tab.Today
        }
    }

    LaunchedEffect(Unit) { viewModel.refreshCheckInState() }

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

    androidx.compose.material3.ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawer(
                name = profile.name,
                week = profile.currentWeek,
                onGo = { destination ->
                    drawerScope.launch { drawerState.close() }
                    when (destination) {
                        "journal" -> { viewModel.refreshCheckInState(); showJournal = true }
                        "garden" -> tab = Tab.You
                        "appointments" -> showAppointments = true
                        "badges" -> tab = Tab.You
                        "settings" -> tab = Tab.You
                        "premium" -> showPaywall = true
                        "kicks" -> tab = Tab.Kicks
                        "coach" -> tab = Tab.Coach
                    }
                },
            )
        },
    ) {
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
                        // Resolve System to what she actually SEES right now,
                        // then toggle from that. The old check compared the
                        // mode enum, so in System-mode-while-dark the button
                        // set Dark — no visible change, "doesn't work".
                        isDark = isDarkResolved,
                        onToggleTheme = {
                            onThemeModeChange(
                                if (isDarkResolved) com.example.ui.theme.ThemeMode.Light
                                else com.example.ui.theme.ThemeMode.Dark
                            )
                        },
                        state = HomeState(
                            profile = profile,
                            weekInfo = weekInfo,
                            todayLog = todayLog,
                            progress = progress,
                            quests = quests,
                            insight = insight,
                            affirmation = affirmation,
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
                        onOpenAppointments = { showAppointments = true },
                        onOpenGarden = { tab = Tab.You },
                        onStepsGoal = viewModel::onStepsGoalReached,
                        onOpenMenu = { drawerScope.launch { drawerState.open() } },
                        onOpenJournal = {
                            viewModel.refreshCheckInState()
                            showJournal = true
                        },
                        checkedInToday = checkedInToday,
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
                        themeMode = themeMode,
                        onThemeModeChange = onThemeModeChange,
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
            visible = showAppointments,
            enter = Motion.riseIn(),
            exit = Motion.riseOut(),
        ) {
            val appts by viewModel.appointments.collectAsStateWithLifecycle()
            val today by viewModel.todayDate.collectAsStateWithLifecycle()
            com.example.ui.appointments.AppointmentsScreen(
                appointments = appts,
                todayDate = today,
                onSave = viewModel::saveAppointment,
                onDelete = viewModel::deleteAppointment,
                onBack = { showAppointments = false },
            )
        }

        AnimatedVisibility(
            visible = showJournal,
            enter = Motion.riseIn(),
            exit = Motion.riseOut(),
        ) {
            com.example.ui.journal.JournalScreen(
                entries = journalEntries,
                checkedInToday = checkedInToday,
                onSave = viewModel::saveJournalEntry,
                onDelete = viewModel::deleteJournalEntry,
                onBack = { showJournal = false },
            )
        }

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
    themeMode: com.example.ui.theme.ThemeMode,
    onThemeModeChange: (com.example.ui.theme.ThemeMode) -> Unit,
) {
    // Garden first: the game is the tab's front door.
    var section by rememberSaveable { mutableStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.gutter, vertical = Space.md),
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            com.example.ui.components.PregaChip("Garden", section == 0, { section = 0 })
            com.example.ui.components.PregaChip("Badges", section == 1, { section = 1 })
            com.example.ui.components.PregaChip("Settings", section == 2, { section = 2 })
        }

        if (section == 0) {
            com.example.ui.garden.GardenScreen(
                progress = progress,
                badgeCount = badges.size,
            )
        } else if (section == 1) {
            BadgesScreen(earned = badges, progress = progress)
        } else {
            SettingsScreen(
                profile = profile,
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
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
                    Icon(
                        imageVector = if (isSelected) tab.iconSelected else tab.icon,
                        contentDescription = tab.label,
                        modifier = Modifier.size(24.dp),
                    )
                },
                label = {
                    Text(
                        tab.label,
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                // The reference designs' nav: the active tab sits in a
                // solid ink pill with the icon knocked out in cream —
                // unmistakable at a glance, and it inverts correctly in
                // dark mode (cream pill, dark icon) because ink/cardSurface
                // swap roles there.
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = PregaTheme.colors.cardSurface,
                    unselectedIconColor = PregaTheme.colors.inkFaint,
                    selectedTextColor = PregaTheme.colors.ink,
                    unselectedTextColor = PregaTheme.colors.inkFaint,
                    indicatorColor = PregaTheme.colors.ink,
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
