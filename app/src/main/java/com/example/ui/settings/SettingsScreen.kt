package com.example.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.UserProfileEntity
import com.example.ui.theme.ThemeMode
import com.example.ui.components.*
import com.example.ui.theme.PregaTheme
import com.example.ui.theme.Space

/**
 * Prega AI — Settings.
 *
 * Replaces the old "FacebookProfileScreen", which imitated another product's
 * chrome (cover photos, a slate-blue Facebook palette, a simulated photo
 * upload) and buried the settings that actually matter underneath it.
 *
 * Deletion is presented plainly rather than hidden behind a survey. The old
 * cancellation flow had a three-step retention funnel; making it hard to leave
 * is how you turn a lapsed user into someone who leaves a one-star review.
 */
@Composable
fun SettingsScreen(
    profile: UserProfileEntity,
    themeMode: ThemeMode = ThemeMode.System,
    onThemeModeChange: (ThemeMode) -> Unit = {},
    onUpdateProfile: (name: String, babyName: String, diet: String) -> Unit,
    onNotificationsChanged: (Boolean) -> Unit,
    onWaterGoalChanged: (Int) -> Unit,
    onManageSubscription: () -> Unit,
    onUpgrade: () -> Unit,
    onExportData: () -> Unit,
    onDeleteEverything: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember(profile.name) { mutableStateOf(profile.name) }
    var babyName by remember(profile.babyNamePlaceholder) { mutableStateOf(profile.babyNamePlaceholder) }
    var diet by remember(profile.dietaryPreferences) { mutableStateOf(profile.dietaryPreferences) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.gutter)
            .padding(bottom = Space.navClearance),
    ) {
        Spacer(Modifier.height(Space.lg))

        // ── Subscription ──
        if (profile.isPremium) {
            PregaCard(containerColor = PregaTheme.colors.goldSoft, border = false) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("👑", fontSize = 24.sp)
                    Spacer(Modifier.width(Space.md))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Premium active",
                            style = MaterialTheme.typography.titleMedium,
                            color = PregaTheme.colors.ink,
                        )
                        Text(
                            "Manage or cancel any time in the Play Store",
                            style = MaterialTheme.typography.bodySmall,
                            color = PregaTheme.colors.inkMuted,
                        )
                    }
                }
                Spacer(Modifier.height(Space.md))
                // Straight to Play. No retention funnel, no exit survey.
                PregaTextButton("Manage subscription", onManageSubscription)
            }
        } else {
            PregaCard(onClick = onUpgrade) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🌷", fontSize = 24.sp)
                    Spacer(Modifier.width(Space.md))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Prega Premium",
                            style = MaterialTheme.typography.titleMedium,
                            color = PregaTheme.colors.ink,
                        )
                        Text(
                            "Unlimited coach questions and tailored meal plans",
                            style = MaterialTheme.typography.bodySmall,
                            color = PregaTheme.colors.inkMuted,
                        )
                    }
                    Text("→", color = PregaTheme.colors.inkFaint, fontSize = 18.sp)
                }
            }
        }

        Spacer(Modifier.height(Space.xl))

        // ── Appearance ──
        SectionHeader(title = "Appearance", overline = "Display")
        Spacer(Modifier.height(Space.md))
        PregaCard {
            Text(
                "Theme",
                style = MaterialTheme.typography.titleMedium,
                color = PregaTheme.colors.ink,
            )
            Spacer(Modifier.height(Space.xs))
            Text(
                "Dark is easier on your eyes at night. System follows your phone.",
                style = MaterialTheme.typography.bodySmall,
                color = PregaTheme.colors.inkMuted,
            )
            Spacer(Modifier.height(Space.md))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                PregaChip("System", themeMode == ThemeMode.System, { onThemeModeChange(ThemeMode.System) })
                PregaChip("Light", themeMode == ThemeMode.Light, { onThemeModeChange(ThemeMode.Light) })
                PregaChip("Dark", themeMode == ThemeMode.Dark, { onThemeModeChange(ThemeMode.Dark) })
            }
        }

        Spacer(Modifier.height(Space.xl))

        // ── About you ──
        SectionHeader(title = "About you", overline = "Profile")
        Spacer(Modifier.height(Space.md))
        PregaCard {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(40) },
                label = { Text("Your name") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Space.md))
            OutlinedTextField(
                value = babyName,
                onValueChange = { babyName = it.take(30) },
                label = { Text("Baby's nickname") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Space.md))
            OutlinedTextField(
                value = diet,
                onValueChange = { diet = it.take(200) },
                label = { Text("Dietary needs and allergies") },
                supportingText = { Text("Used to tailor your meal plans") },
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Space.md))
            PregaButton("Save changes", { onUpdateProfile(name, babyName, diet) })
        }

        Spacer(Modifier.height(Space.xl))

        // ── Daily goals ──
        SectionHeader(title = "Daily goals", overline = "Tracking")
        Spacer(Modifier.height(Space.md))
        PregaCard {
            Text(
                "Water goal",
                style = MaterialTheme.typography.titleMedium,
                color = PregaTheme.colors.ink,
            )
            Spacer(Modifier.height(Space.xs))
            Text(
                "A target you set yourself, and can change whenever it stops fitting.",
                style = MaterialTheme.typography.bodySmall,
                color = PregaTheme.colors.inkMuted,
            )
            Spacer(Modifier.height(Space.md))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                listOf(6, 8, 10, 12).forEach { goal ->
                    PregaChip(
                        label = "$goal",
                        selected = profile.waterGoalGlasses == goal,
                        onClick = { onWaterGoalChanged(goal) },
                    )
                }
            }
        }

        Spacer(Modifier.height(Space.xl))

        // ── Notifications ──
        SectionHeader(title = "Reminders", overline = "Notifications")
        Spacer(Modifier.height(Space.md))
        PregaCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Gentle reminders",
                        style = MaterialTheme.typography.titleMedium,
                        color = PregaTheme.colors.ink,
                    )
                    Text(
                        "At most three a day. Nothing between " +
                            "${profile.quietHoursStart} and ${profile.quietHoursEnd}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = PregaTheme.colors.inkMuted,
                    )
                }
                Switch(
                    checked = profile.notificationsEnabled,
                    onCheckedChange = onNotificationsChanged,
                )
            }
            Spacer(Modifier.height(Space.md))
            Text(
                "You can mute individual types — milestones, appointments, daily " +
                    "check-ins, nudges — in your phone's notification settings for Prega.",
                style = MaterialTheme.typography.bodySmall,
                color = PregaTheme.colors.inkFaint,
            )
        }

        Spacer(Modifier.height(Space.xl))

        // ── Data ──
        SectionHeader(title = "Your data", overline = "Privacy")
        Spacer(Modifier.height(Space.md))
        PregaCard {
            Text(
                "Everything you log — your profile, daily logs, kick sessions, moods and " +
                    "appointments — is stored only on this device. It is never uploaded, " +
                    "sold, or shared. When you ask the coach a question, only that question " +
                    "is sent for an answer; your logs never leave the phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = PregaTheme.colors.inkMuted,
            )
            Spacer(Modifier.height(Space.lg))
            PregaTextButton("Export my data", onExportData)
        }

        Spacer(Modifier.height(Space.lg))

        // ── Privacy policy ──
        // Play requires a stated policy for apps handling health data; more
        // to the point, she deserves one she can actually read. Plain words,
        // shown in-app so it's true offline too.
        var showPolicy by remember { mutableStateOf(false) }
        PregaCard(onClick = { showPolicy = !showPolicy }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Privacy policy",
                        style = MaterialTheme.typography.titleMedium,
                        color = PregaTheme.colors.ink,
                    )
                    Text(
                        "The short version: your data stays on this phone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = PregaTheme.colors.inkMuted,
                    )
                }
                Text(if (showPolicy) "−" else "+", color = PregaTheme.colors.inkFaint, fontSize = 22.sp)
            }
            AnimatedVisibility(visible = showPolicy) {
                Column {
                    Spacer(Modifier.height(Space.md))
                    Text(
                        """
                        What we store: everything you enter — your profile, daily logs, kick sessions, moods, appointments, journal notes and photos — lives only in this app's private storage on this phone. There is no account and no server holding your data.

                        What leaves the phone: only your questions to Prega AI (and basic context like your week) are sent to our AI provider to generate an answer. They are not used to identify you and are not sold. Journal photos are never uploaded.

                        Sharing: nothing is ever shared unless you tap share yourself.

                        Payments: subscriptions are handled entirely by Google Play. We never see your payment details.

                        Deleting: "Delete everything" below removes all of it, immediately and permanently. Uninstalling the app does the same.

                        Not medical care: Prega AI offers general information and never replaces your midwife or doctor.
                        """.trimIndent(),
                        style = MaterialTheme.typography.bodySmall,
                        color = PregaTheme.colors.inkMuted,
                    )
                }
            }
        }

        Spacer(Modifier.height(Space.lg))

        PregaCard(border = true) {
            Text(
                "Delete everything",
                style = MaterialTheme.typography.titleMedium,
                color = PregaTheme.colors.alert,
            )
            Spacer(Modifier.height(Space.sm))
            Text(
                "Permanently removes all of it from this device. This can't be undone, " +
                    "and your kick history can't be recreated — export first if you might " +
                    "want it.",
                style = MaterialTheme.typography.bodySmall,
                color = PregaTheme.colors.inkMuted,
            )
            Spacer(Modifier.height(Space.md))
            PregaTextButton("Delete all my data", { confirmDelete = true })
        }

        Spacer(Modifier.height(Space.xxl))

        Text(
            "Prega supports you. It doesn't replace your midwife, doctor or " +
                "maternity unit — please contact them with anything that worries you.",
            style = MaterialTheme.typography.bodySmall,
            color = PregaTheme.colors.inkFaint,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Space.xl))
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete everything?") },
            text = {
                Text(
                    "All your logs, kick sessions, badges and profile will be permanently " +
                        "removed from this device. This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDeleteEverything() }) {
                    Text("Delete", color = PregaTheme.colors.alert)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Keep my data") }
            },
        )
    }
}
