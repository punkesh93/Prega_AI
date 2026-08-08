@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.*
import com.example.ui.theme.Motion
import com.example.ui.theme.PregaTheme
import com.example.ui.theme.Space
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Prega AI — Onboarding.
 *
 * What the old SetupScreen got wrong: it opened with a form asking her to type
 * her current pregnancy week. Most people don't know their week off the top of
 * their head — they know their last period or their due date — so the very
 * first interaction was one she could get wrong or abandon.
 *
 * This flow instead:
 *  - Opens with why the app exists, not with a form.
 *  - Asks for the date she actually knows, and calculates the week itself.
 *  - Asks one question per screen. Nothing is required except the date.
 *  - Shows her something real about her week before asking for anything else,
 *    so she gets value before she gives data.
 */

data class OnboardingResult(
    val name: String,
    val week: Int,
    val babyName: String,
    val lmpDate: String,
    val eddDate: String,
    val dietaryPreferences: String,
    val isFirstPregnancy: Boolean,
    val notificationsEnabled: Boolean,
    val billingRegion: String,
)

/** Mirrors [com.example.auth.GoogleAuthResult] without importing the auth
 *  package into the UI layer — the screen only needs to know what to show. */
enum class GoogleSignInStatus { Idle, InProgress, NotConfigured, Failed }

private enum class Step { Welcome, DateEntry, Confirm, AboutYou, Personalise, Notifications }

@Composable
fun OnboardingScreen(
    weekDescriber: (Int) -> Triple<String, String, String>, // sizeName, emoji, description
    onComplete: (OnboardingResult) -> Unit,
    // Google sign-in is entirely optional. Everything below defaults to "not
    // offered" so the screen works standalone (previews, tests) without a
    // credential manager wired up behind it.
    googleSignInStatus: GoogleSignInStatus = GoogleSignInStatus.Idle,
    onGoogleSignIn: () -> Unit = {},
    prefillName: String = "",
) {
    var step by remember { mutableStateOf(Step.Welcome) }

    // Collected state
    var dateMode by remember { mutableStateOf(DateMode.DueDate) }
    var dateInput by remember { mutableStateOf("") }
    var name by remember(prefillName) { mutableStateOf(prefillName) }
    var babyName by remember { mutableStateOf("") }
    var firstPregnancy by remember { mutableStateOf(true) }
    var diet by remember { mutableStateOf(setOf<String>()) }
    var dietNotes by remember { mutableStateOf("") }
    var notifications by remember { mutableStateOf(true) }

    val computed = remember(dateInput, dateMode) { computeWeek(dateInput, dateMode) }

    Box(
        Modifier
            .fillMaxSize()
            .background(PregaTheme.colors.dawnBrush)
    ) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {

            // Progress dots — only shown once she's committed past the welcome.
            AnimatedVisibility(visible = step != Step.Welcome, enter = fadeIn(), exit = fadeOut()) {
                StepDots(
                    current = Step.entries.indexOf(step),
                    total = Step.entries.size,
                    modifier = Modifier.padding(top = Space.xl, bottom = Space.sm),
                )
            }

            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    val forward = Step.entries.indexOf(targetState) >
                        Step.entries.indexOf(initialState)
                    val dir = if (forward) 1 else -1
                    (
                        slideInHorizontally(tween(Motion.Gentle, easing = Motion.EaseEnter)) { it / 3 * dir } +
                            fadeIn(tween(Motion.Gentle))
                        ) togetherWith (
                        slideOutHorizontally(tween(Motion.Quick, easing = Motion.EaseExit)) { -it / 3 * dir } +
                            fadeOut(tween(Motion.Quick))
                        )
                },
                modifier = Modifier.weight(1f),
                label = "onboardingStep",
            ) { current ->
                when (current) {
                    Step.Welcome -> WelcomePage(
                        onNext = { step = Step.DateEntry },
                        googleStatus = googleSignInStatus,
                        onGoogleSignIn = onGoogleSignIn,
                    )

                    Step.DateEntry -> DatePage(
                        mode = dateMode,
                        onModeChange = { dateMode = it; dateInput = "" },
                        value = dateInput,
                        onValueChange = { dateInput = it },
                        computed = computed,
                        onNext = { step = Step.Confirm },
                    )

                    // The payoff screen. She gets something real before we ask
                    // for anything else — this is what earns the next four taps.
                    Step.Confirm -> ConfirmPage(
                        week = computed?.week ?: 0,
                        dueDate = computed?.dueDate,
                        describe = weekDescriber,
                        onNext = { step = Step.AboutYou },
                        onBack = { step = Step.DateEntry },
                    )

                    Step.AboutYou -> AboutYouPage(
                        name = name,
                        onNameChange = { name = it },
                        firstPregnancy = firstPregnancy,
                        onFirstPregnancyChange = { firstPregnancy = it },
                        onNext = { step = Step.Personalise },
                    )

                    Step.Personalise -> PersonalisePage(
                        babyName = babyName,
                        onBabyNameChange = { babyName = it },
                        diet = diet,
                        onDietToggle = { tag ->
                            diet = if (tag in diet) diet - tag else diet + tag
                        },
                        dietNotes = dietNotes,
                        onDietNotesChange = { dietNotes = it },
                        onNext = { step = Step.Notifications },
                    )

                    Step.Notifications -> NotificationsPage(
                        enabled = notifications,
                        onEnabledChange = { notifications = it },
                        onFinish = {
                            val c = computed
                            onComplete(
                                OnboardingResult(
                                    name = name.trim(),
                                    week = c?.week ?: 12,
                                    babyName = babyName.trim(),
                                    lmpDate = if (dateMode == DateMode.LastPeriod) dateInput else "",
                                    eddDate = c?.dueDate?.format(DISPLAY_FMT).orEmpty(),
                                    dietaryPreferences = (diet + dietNotes.trim())
                                        .filter { it.isNotBlank() }
                                        .joinToString(", "),
                                    isFirstPregnancy = firstPregnancy,
                                    notificationsEnabled = notifications,
                                    billingRegion = "GLOBAL",
                                )
                            )
                        },
                    )
                }
            }
        }
    }
}

// ─── Pages ─────────────────────────────────────────────────────────────────

@Composable
private fun PageScaffold(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    footer: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = Space.gutter)
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(Space.xl))
            Text(
                title,
                style = MaterialTheme.typography.displaySmall,
                color = PregaTheme.colors.ink,
            )
            subtitle?.let {
                Spacer(Modifier.height(Space.md))
                Text(
                    it,
                    style = MaterialTheme.typography.bodyLarge,
                    color = PregaTheme.colors.inkMuted,
                )
            }
            Spacer(Modifier.height(Space.xl))
            content()
            Spacer(Modifier.height(Space.xl))
        }
        Column(Modifier.padding(bottom = Space.xl), content = footer)
    }
}

@Composable
private fun WelcomePage(
    onNext: () -> Unit,
    googleStatus: GoogleSignInStatus,
    onGoogleSignIn: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        // The signature ambient moment: petals drifting slowly behind the
        // hero. Used here and on the badge reveal only — ambience on every
        // screen stops being ambience.
        PetalDrift(Modifier.fillMaxSize())

        WelcomeContent(onNext, googleStatus, onGoogleSignIn)
    }
}

@Composable
private fun WelcomeContent(
    onNext: () -> Unit,
    googleStatus: GoogleSignInStatus,
    onGoogleSignIn: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = Space.gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))

        // The welcome hero: our own line-art mother with the heart where the
        // baby is — the app's first image is now of her, not a flower emoji.
        Breathing(minScale = 0.985f, maxScale = 1.015f) {
            MotherLineArt(
                modifier = Modifier.size(190.dp),
                line = PregaTheme.colors.ink.copy(alpha = 0.85f),
            )
        }

        Spacer(Modifier.height(Space.xl))
        Text(
            "Prega",
            style = MaterialTheme.typography.displayLarge,
            color = PregaTheme.colors.ink,
        )
        Spacer(Modifier.height(Space.md))
        Text(
            "A calmer way through the next nine months.",
            style = MaterialTheme.typography.bodyLarge,
            color = PregaTheme.colors.inkMuted,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(Space.xxl))

        // Three promises, not three feature bullets.
        Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
            Promise("🤍", "No pressure", "Miss a day and nothing is lost. Ever.")
            Promise("🔒", "Yours alone", "Everything stays on your device.")
            Promise("💬", "Answers at 3am", "For the questions you'd rather not google.")
        }

        Spacer(Modifier.weight(1f))

        PregaButton("Begin", onNext)

        // Google sign-in is a secondary, skippable option — never the
        // primary path. Everything in the app works fully signed-out.
        Spacer(Modifier.height(Space.md))
        GoogleSignInRow(status = googleStatus, onClick = onGoogleSignIn)

        Spacer(Modifier.height(Space.md))
        Text(
            "Prega supports you — it doesn't replace your midwife or doctor.",
            style = MaterialTheme.typography.bodySmall,
            color = PregaTheme.colors.inkFaint,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Space.xl))
    }
}

/**
 * The "G" mark is drawn with plain coloured text rather than bundling
 * Google's logo asset — same recognisable four-colour cue, no brand-asset
 * file to manage, and it reads fine at this size.
 */
@Composable
private fun GoogleSignInRow(status: GoogleSignInStatus, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        when (status) {
            GoogleSignInStatus.InProgress -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = PregaTheme.colors.inkMuted,
                    )
                    Spacer(Modifier.width(Space.sm))
                    Text(
                        "Signing in…",
                        style = MaterialTheme.typography.labelMedium,
                        color = PregaTheme.colors.inkMuted,
                    )
                }
            }

            else -> {
                // Same weight class as the primary button — full width, same
                // height — so signing in reads as a real choice, not fine
                // print. Still optional: "Begin" above works without it.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(percent = 50))
                        .background(PregaTheme.colors.cardSurface)
                        .border(
                            1.5.dp,
                            PregaTheme.colors.hairline,
                            androidx.compose.foundation.shape.RoundedCornerShape(percent = 50),
                        )
                        .clickable(onClick = onClick),
                ) {
                    GoogleGlyph()
                    Spacer(Modifier.width(Space.sm))
                    Text(
                        "Continue with Google",
                        style = MaterialTheme.typography.labelLarge,
                        color = PregaTheme.colors.ink,
                    )
                }
            }
        }

        if (status == GoogleSignInStatus.Failed) {
            Spacer(Modifier.height(Space.xs))
            Text(
                "That didn't go through — you can keep going without it.",
                style = MaterialTheme.typography.bodySmall,
                color = PregaTheme.colors.inkFaint,
                textAlign = TextAlign.Center,
            )
        }
        if (status == GoogleSignInStatus.NotConfigured) {
            Spacer(Modifier.height(Space.xs))
            Text(
                "Google sign-in isn't set up on this build yet.",
                style = MaterialTheme.typography.bodySmall,
                color = PregaTheme.colors.inkFaint,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun GoogleGlyph() {
    val text = androidx.compose.ui.text.buildAnnotatedString {
        withStyle(androidx.compose.ui.text.SpanStyle(color = Color(0xFF4285F4))) { append("G") }
        withStyle(androidx.compose.ui.text.SpanStyle(color = Color(0xFFEA4335))) { append("o") }
        withStyle(androidx.compose.ui.text.SpanStyle(color = Color(0xFFFBBC05))) { append("o") }
        withStyle(androidx.compose.ui.text.SpanStyle(color = Color(0xFF4285F4))) { append("g") }
        withStyle(androidx.compose.ui.text.SpanStyle(color = Color(0xFF34A853))) { append("l") }
        withStyle(androidx.compose.ui.text.SpanStyle(color = Color(0xFFEA4335))) { append("e") }
    }
    Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
}

@Composable
private fun Promise(emoji: String, title: String, body: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(PregaTheme.colors.cardSurface),
            contentAlignment = Alignment.Center,
        ) { Text(emoji, fontSize = 20.sp) }
        Spacer(Modifier.width(Space.lg))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, color = PregaTheme.colors.ink)
            Text(body, style = MaterialTheme.typography.bodySmall, color = PregaTheme.colors.inkMuted)
        }
    }
}

/**
 * A typed date field is where most onboarding abandonment happens on this kind
 * of screen — a wrong digit silently produces a wrong week, and a phone
 * keyboard makes typing "2026-03-11" more fiddly than it looks. This uses
 * Material's picker instead: no format to get wrong, no keyboard, and each
 * mode's calendar only lets her tap dates that are actually possible for it
 * (last period can't be in the future; a due date can't be implausibly far
 * away), so a mistaken tap is structurally harder to make.
 */
@Composable
private fun DatePage(
    mode: DateMode,
    onModeChange: (DateMode) -> Unit,
    value: String,
    onValueChange: (String) -> Unit,
    computed: ComputedDates?,
    onNext: () -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    val selectedDate = remember(value) { value.toLocalDateOrNull() }

    PageScaffold(
        title = "Let's find your week",
        subtitle = "You don't need to know it — just pick whichever date you have.",
        footer = {
            PregaButton("Continue", onNext, enabled = computed != null)
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            PregaChip("Due date", mode == DateMode.DueDate, { onModeChange(DateMode.DueDate) })
            PregaChip("Last period", mode == DateMode.LastPeriod, { onModeChange(DateMode.LastPeriod) })
        }

        Spacer(Modifier.height(Space.xl))

        Text(
            when (mode) {
                DateMode.DueDate -> "Your due date"
                DateMode.LastPeriod -> "First day of your last period"
            },
            style = MaterialTheme.typography.titleSmall,
            color = PregaTheme.colors.inkMuted,
        )
        Spacer(Modifier.height(Space.sm))

        PregaCard(onClick = { showPicker = true }, contentPadding = PaddingValues(Space.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.CalendarMonth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(Space.md))
                Text(
                    selectedDate?.format(DISPLAY_FMT) ?: "Tap to choose a date",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (selectedDate != null) PregaTheme.colors.ink else PregaTheme.colors.inkFaint,
                )
            }
        }

        AnimatedVisibility(visible = computed != null, enter = Motion.popIn, exit = Motion.popOut) {
            computed?.let {
                Column {
                    Spacer(Modifier.height(Space.lg))
                    PregaCard(containerColor = PregaTheme.colors.sageSoft, border = false) {
                        Text(
                            "That puts you at week ${it.week}, day ${it.dayOfWeek}.",
                            style = MaterialTheme.typography.titleMedium,
                            color = PregaTheme.colors.ink,
                        )
                        Spacer(Modifier.height(Space.xs))
                        Text(
                            "Due ${it.dueDate.format(DISPLAY_FMT)} · ${it.daysRemaining} days to go",
                            style = MaterialTheme.typography.bodySmall,
                            color = PregaTheme.colors.inkMuted,
                        )
                    }
                }
            }
        }
    }

    if (showPicker) {
        PregaDatePickerDialog(
            mode = mode,
            initial = selectedDate,
            onDismiss = { showPicker = false },
            onConfirm = {
                onValueChange(it.toString())
                showPicker = false
            },
        )
    }
}

/**
 * Thin wrapper around Material3's DatePicker. [SelectableDates] does the real
 * work: it disables — greys out, un-tappable — any day that mode makes
 * impossible, rather than accepting a bad tap and rejecting it afterwards.
 */
@Composable
private fun PregaDatePickerDialog(
    mode: DateMode,
    initial: LocalDate?,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val today = remember { LocalDate.now() }

    val selectable = remember(mode, today) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val date = java.time.Instant.ofEpochMilli(utcTimeMillis)
                    .atZone(java.time.ZoneOffset.UTC).toLocalDate()
                return when (mode) {
                    // Can't have had a last period in the future, and dating
                    // back further than ~46 weeks isn't a current pregnancy.
                    DateMode.LastPeriod -> !date.isAfter(today) && !date.isBefore(today.minusDays(320))
                    // A due date can reasonably sit a little in the past
                    // (she's likely just given birth, or overdue) through to
                    // about 46 weeks out.
                    DateMode.DueDate -> !date.isBefore(today.minusDays(30)) && !date.isAfter(today.plusDays(320))
                }
            }
        }
    }

    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial?.atStartOfDay(java.time.ZoneOffset.UTC)
            ?.toInstant()?.toEpochMilli(),
        selectableDates = selectable,
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedDateMillis != null,
                onClick = {
                    state.selectedDateMillis?.let { millis ->
                        val date = java.time.Instant.ofEpochMilli(millis)
                            .atZone(java.time.ZoneOffset.UTC).toLocalDate()
                        onConfirm(date)
                    }
                },
            ) { Text("Use this date") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = state, title = null, showModeToggle = true)
    }
}

private fun String.toLocalDateOrNull(): LocalDate? =
    if (isBlank()) null else runCatching { LocalDate.parse(this) }.getOrNull()

@Composable
private fun ConfirmPage(
    week: Int,
    dueDate: LocalDate?,
    describe: (Int) -> Triple<String, String, String>,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    val (sizeName, emoji, description) = remember(week) { describe(week) }

    PageScaffold(
        title = "Week $week",
        subtitle = null,
        footer = {
            PregaButton("This looks right", onNext)
            Spacer(Modifier.height(Space.sm))
            PregaTextButton("Change my date", onBack, fillWidth = true)
        },
    ) {
        GradientCard(brush = PregaTheme.colors.bloomBrush) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Breathing { Text(emoji, fontSize = 52.sp) }
                Spacer(Modifier.width(Space.lg))
                Column {
                    Overline("About the size of", color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f))
                    Spacer(Modifier.height(Space.xs))
                    Text(
                        sizeName,
                        style = MaterialTheme.typography.headlineMedium,
                        color = androidx.compose.ui.graphics.Color.White,
                    )
                }
            }
        }

        Spacer(Modifier.height(Space.lg))

        PregaCard {
            Overline("Happening now")
            Spacer(Modifier.height(Space.sm))
            Text(
                description,
                style = MaterialTheme.typography.bodyLarge,
                color = PregaTheme.colors.ink,
            )
        }

        dueDate?.let {
            Spacer(Modifier.height(Space.lg))
            PregaCard(containerColor = PregaTheme.colors.goldSoft, border = false) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📅", fontSize = 22.sp)
                    Spacer(Modifier.width(Space.md))
                    Column {
                        Text(
                            "Due ${it.format(DISPLAY_FMT)}",
                            style = MaterialTheme.typography.titleMedium,
                            color = PregaTheme.colors.ink,
                        )
                        Text(
                            "Only about 4% of babies arrive on their due date — treat it as a window, not a deadline.",
                            style = MaterialTheme.typography.bodySmall,
                            color = PregaTheme.colors.inkMuted,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutYouPage(
    name: String,
    onNameChange: (String) -> Unit,
    firstPregnancy: Boolean,
    onFirstPregnancyChange: (Boolean) -> Unit,
    onNext: () -> Unit,
) {
    PageScaffold(
        title = "A little about you",
        subtitle = "Both optional. Skip anything you'd rather not share.",
        footer = { PregaButton(if (name.isBlank()) "Skip for now" else "Continue", onNext) },
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { onNameChange(it.take(40)) },
            label = { Text("What should I call you?") },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(Space.xl))

        Text(
            "Is this your first pregnancy?",
            style = MaterialTheme.typography.titleMedium,
            color = PregaTheme.colors.ink,
        )
        Spacer(Modifier.height(Space.xs))
        Text(
            "It changes what's worth explaining and what you already know.",
            style = MaterialTheme.typography.bodySmall,
            color = PregaTheme.colors.inkMuted,
        )
        Spacer(Modifier.height(Space.md))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            PregaChip("Yes, my first", firstPregnancy, { onFirstPregnancyChange(true) })
            PregaChip("I've done this before", !firstPregnancy, { onFirstPregnancyChange(false) })
        }
    }
}

@Composable
private fun PersonalisePage(
    babyName: String,
    onBabyNameChange: (String) -> Unit,
    diet: Set<String>,
    onDietToggle: (String) -> Unit,
    dietNotes: String,
    onDietNotesChange: (String) -> Unit,
    onNext: () -> Unit,
) {
    PageScaffold(
        title = "Making it yours",
        subtitle = "This is what lets meal plans and advice actually fit you.",
        footer = { PregaButton("Continue", onNext) },
    ) {
        OutlinedTextField(
            value = babyName,
            onValueChange = { onBabyNameChange(it.take(30)) },
            label = { Text("A nickname for the baby") },
            placeholder = { Text("Bean, Peanut, Little One…") },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(Space.xl))

        Text(
            "How you eat",
            style = MaterialTheme.typography.titleMedium,
            color = PregaTheme.colors.ink,
        )
        Spacer(Modifier.height(Space.md))
        FlowChips(DIET_TAGS, diet, onDietToggle)

        Spacer(Modifier.height(Space.lg))
        OutlinedTextField(
            value = dietNotes,
            onValueChange = { onDietNotesChange(it.take(120)) },
            label = { Text("Allergies or foods you can't face") },
            placeholder = { Text("Nuts, shellfish, anything with garlic…") },
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun NotificationsPage(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onFinish: () -> Unit,
) {
    PageScaffold(
        title = "Staying in touch",
        subtitle = "A handful of useful nudges — never guilt, never noise.",
        footer = { PregaButton("Start my journey", onFinish) },
    ) {
        PregaCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Gentle reminders",
                        style = MaterialTheme.typography.titleMedium,
                        color = PregaTheme.colors.ink,
                    )
                    Spacer(Modifier.height(Space.xs))
                    Text(
                        "Week milestones, a kick-count nudge, and a quiet check-in. Nothing between 9:30pm and 8am.",
                        style = MaterialTheme.typography.bodySmall,
                        color = PregaTheme.colors.inkMuted,
                    )
                }
                Spacer(Modifier.width(Space.md))
                Switch(checked = enabled, onCheckedChange = onEnabledChange)
            }
        }

        Spacer(Modifier.height(Space.lg))

        PregaCard(containerColor = PregaTheme.colors.recessed, border = false) {
            Text("🔒 Your data", style = MaterialTheme.typography.titleMedium, color = PregaTheme.colors.ink)
            Spacer(Modifier.height(Space.sm))
            Text(
                "Everything you log stays on this device. Nothing is uploaded, sold, or shared. " +
                    "When you ask the coach a question, only that question is sent — never your logs.",
                style = MaterialTheme.typography.bodyMedium,
                color = PregaTheme.colors.inkMuted,
            )
        }
    }
}

// ─── Small pieces ──────────────────────────────────────────────────────────

@Composable
private fun StepDots(current: Int, total: Int, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(total) { i ->
            val active = i <= current
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .size(width = if (i == current) 20.dp else 6.dp, height = 6.dp)
                    .clip(CircleShape)
                    .background(
                        if (active) MaterialTheme.colorScheme.primary
                        else PregaTheme.colors.hairline
                    )
            )
        }
    }
}

@Composable
private fun FlowChips(
    options: List<Pair<String, String>>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        options.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                row.forEach { (label, emoji) ->
                    PregaChip(label, label in selected, { onToggle(label) }, leadingEmoji = emoji)
                }
            }
        }
    }
}

private val DIET_TAGS = listOf(
    "Vegetarian" to "🥬",
    "Vegan" to "🌱",
    "Halal" to "🕌",
    "Kosher" to "✡️",
    "Gluten-free" to "🌾",
    "Dairy-free" to "🥛",
)

// ─── Date maths ────────────────────────────────────────────────────────────

enum class DateMode { DueDate, LastPeriod }

data class ComputedDates(
    val week: Int,
    val dayOfWeek: Int,
    val dueDate: LocalDate,
    val daysRemaining: Int,
)

private val DISPLAY_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

/** A pregnancy is dated as 280 days from the first day of the last period. */
private const val GESTATION_DAYS = 280L

/**
 * Turns whichever date she has into a gestational week.
 *
 * Returns null for anything unparseable or implausible, so the UI can hold the
 * Continue button rather than silently accepting a typo that would misdate her
 * entire pregnancy.
 */
internal fun computeWeek(
    input: String,
    mode: DateMode,
    today: LocalDate = LocalDate.now(),
): ComputedDates? {
    val parsed = runCatching { LocalDate.parse(input.trim()) }.getOrNull() ?: return null

    val lmp = when (mode) {
        DateMode.LastPeriod -> parsed
        DateMode.DueDate -> parsed.minusDays(GESTATION_DAYS)
    }

    val elapsed = ChronoUnit.DAYS.between(lmp, today)
    // Reject impossible inputs: conception in the future, or a pregnancy that
    // would already be well past term.
    if (elapsed < 0 || elapsed > 320) return null

    val due = lmp.plusDays(GESTATION_DAYS)

    return ComputedDates(
        week = (elapsed / 7).toInt().coerceIn(1, 42),
        dayOfWeek = (elapsed % 7).toInt(),
        dueDate = due,
        daysRemaining = ChronoUnit.DAYS.between(today, due).toInt().coerceAtLeast(0),
    )
}
