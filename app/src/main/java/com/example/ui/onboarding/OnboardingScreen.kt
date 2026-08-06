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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
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

private enum class Step { Welcome, DateEntry, Confirm, AboutYou, Personalise, Notifications }

@Composable
fun OnboardingScreen(
    weekDescriber: (Int) -> Triple<String, String, String>, // sizeName, emoji, description
    onComplete: (OnboardingResult) -> Unit,
) {
    var step by remember { mutableStateOf(Step.Welcome) }

    // Collected state
    var dateMode by remember { mutableStateOf(DateMode.DueDate) }
    var dateInput by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
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
                    Step.Welcome -> WelcomePage(onNext = { step = Step.DateEntry })

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
private fun WelcomePage(onNext: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        // The signature ambient moment: petals drifting slowly behind the
        // hero. Used here and on the badge reveal only — ambience on every
        // screen stops being ambience.
        PetalDrift(Modifier.fillMaxSize())

        WelcomeContent(onNext)
    }
}

@Composable
private fun WelcomeContent(onNext: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = Space.gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))

        Breathing { Text("🌸", fontSize = 76.sp) }

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

@Composable
private fun DatePage(
    mode: DateMode,
    onModeChange: (DateMode) -> Unit,
    value: String,
    onValueChange: (String) -> Unit,
    computed: ComputedDates?,
    onNext: () -> Unit,
) {
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

        OutlinedTextField(
            value = value,
            onValueChange = { onValueChange(it.filter { c -> c.isDigit() || c == '-' }.take(10)) },
            label = {
                Text(
                    when (mode) {
                        DateMode.DueDate -> "Your due date"
                        DateMode.LastPeriod -> "First day of your last period"
                    }
                )
            },
            placeholder = { Text("YYYY-MM-DD") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
            ),
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        )

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

        if (value.length >= 8 && computed == null) {
            Spacer(Modifier.height(Space.md))
            Text(
                "That date doesn't look right — check the format is YYYY-MM-DD.",
                style = MaterialTheme.typography.bodySmall,
                color = PregaTheme.colors.alert,
            )
        }
    }
}

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
            PregaTextButton("Change my date", onBack)
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
