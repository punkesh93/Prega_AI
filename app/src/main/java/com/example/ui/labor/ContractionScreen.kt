package com.example.ui.labor

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.core.net.toUri
import com.example.data.ContractionEntity
import com.example.stats.AppStats
import com.example.stats.StatEvent
import com.example.ui.components.Overline
import com.example.ui.components.PregaButton
import com.example.ui.components.PregaCard
import com.example.ui.theme.PregaTheme
import com.example.ui.theme.Space
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Contraction timing — the calm surface (Kicks tab) and LABOR MODE (a
 * full-screen, high-contrast interface for the real night).
 *
 * The 2 AM contract, and how it's honored: every number on these screens is
 * derived from Room timestamps (an in-progress contraction is a row with
 * durationSeconds == -1). The one-second tick below only refreshes a "now"
 * value used for display math — nothing accumulates in memory, so locking
 * the phone, taking a call, or the OS killing the process changes nothing;
 * reopening resumes mid-contraction with the correct elapsed time.
 *
 * Clinical wording rule (hard product rule): the app never says "you are in
 * labor" and never interprets the pattern. It shows neutral numbers, her own
 * provider's saved instructions verbatim, and one fixed line: patterns are
 * worth discussing with her care team.
 */

// ─── Provider guidance (user-entered, shown verbatim) ─────────────────────

private const val PREFS = "prega_labor"

data class ProviderInfo(
    val name: String = "",
    val phone: String = "",
    val hospital: String = "",
    val instructions: String = "",
)

fun loadProvider(context: Context): ProviderInfo =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).let {
        ProviderInfo(
            it.getString("name", "").orEmpty(),
            it.getString("phone", "").orEmpty(),
            it.getString("hospital", "").orEmpty(),
            it.getString("instructions", "").orEmpty(),
        )
    }

fun saveProvider(context: Context, p: ProviderInfo) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
        putString("name", p.name); putString("phone", p.phone)
        putString("hospital", p.hospital); putString("instructions", p.instructions)
    }
}

private fun shareSummary(context: Context, stats: SessionStats, sessionStart: Long) {
    AppStats.log(StatEvent.ContractionSummaryShared)
    val fmt = SimpleDateFormat("h:mm a", Locale.getDefault())
    val lines = buildSummaryText(
        stats = stats,
        sessionStartLabel = fmt.format(Date(sessionStart)),
        timeLabel = { fmt.format(Date(it)) },
    )
    runCatching {
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"; putExtra(Intent.EXTRA_TEXT, lines)
                },
                "Share contraction summary",
            )
        )
    }
}

// ═══ Calm surface: Contractions section on the Kicks tab ═══════════════════

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContractionScreen(
    contractions: List<ContractionEntity>,
    sessionStartedAt: Long?,
    activeContraction: ContractionEntity?,
    onStartContraction: () -> Unit,
    onStopContraction: () -> Unit,
    onEndSession: () -> Unit,
    onDelete: (Int) -> Unit,
    onAdjust: (Int, Int) -> Unit,
    onEnterLaborMode: () -> Unit,
) {
    val context = LocalContextCompat()
    val now = rememberNowMs(active = activeContraction != null)
    val stats = remember(contractions, now / 1000) { computeStats(contractions, now) }
    var editing by remember { mutableStateOf<ContractionEntity?>(null) }

    // Resume rule: if this screen WAKES UP to find a contraction that has
    // been running a long time (process was killed / phone was away), the
    // app never silently guesses — she decides. Short-lived actives (quick
    // app switches) resume without ceremony.
    var resumePromptFor by remember {
        mutableStateOf(
            activeContraction?.takeIf { elapsedSec(it, System.currentTimeMillis()) > 120 }?.id
        )
    }
    if (resumePromptFor != null && activeContraction?.id == resumePromptFor) {
        AlertDialog(
            onDismissRequest = { resumePromptFor = null },
            title = { Text("A contraction was still running") },
            text = {
                Text(
                    "The timer kept its place while the app was away — it has been " +
                        "${fmtSpan(elapsedSec(activeContraction, now))} since it started. " +
                        "Continue timing it, or end it now?"
                )
            },
            confirmButton = {
                TextButton(onClick = { resumePromptFor = null }) { Text("Continue") }
            },
            dismissButton = {
                TextButton(onClick = { onStopContraction(); resumePromptFor = null }) {
                    Text("End now")
                }
            },
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.gutter),
    ) {
        // Timer card — one big honest button, nothing else moving.
        PregaCard(border = false, containerColor = PregaTheme.colors.cardSurface) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (activeContraction != null) {
                    Overline("Contraction")
                    Text(
                        fmtClock(elapsedSec(activeContraction, now)),
                        fontSize = 72.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = PregaTheme.colors.ink,
                    )
                    Spacer(Modifier.height(Space.md))
                    PregaButton(text = "It's easing — stop", onClick = onStopContraction)
                } else {
                    Overline(if (sessionStartedAt == null) "Contraction timer" else "Between contractions")
                    if (sessionStartedAt != null && stats.lastEndedAgoSec != null) {
                        Text(
                            fmtClock(stats.lastEndedAgoSec),
                            fontSize = 56.sp,
                            fontFamily = FontFamily.Monospace,
                            color = PregaTheme.colors.inkMuted,
                        )
                        Text(
                            "since the last one ended",
                            style = MaterialTheme.typography.bodySmall,
                            color = PregaTheme.colors.inkFaint,
                        )
                    } else {
                        Spacer(Modifier.height(Space.sm))
                        Text(
                            "Tap when a contraction begins. Timing works even if the phone locks or the app closes.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = PregaTheme.colors.inkMuted,
                            textAlign = TextAlign.Center,
                        )
                    }
                    Spacer(Modifier.height(Space.md))
                    PregaButton(text = "Contraction starting", onClick = onStartContraction)
                }
            }
        }

        Spacer(Modifier.height(Space.md))

        if (sessionStartedAt != null && stats.completed.isNotEmpty()) {
            PregaCard(border = false, containerColor = PregaTheme.colors.sageSoft) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Stat("This session", "${stats.completed.size}")
                    Stat("Avg length", fmtSpan(stats.avgDurationSec))
                    Stat("Avg apart", if (stats.avgIntervalSec > 0) fmtSpan(stats.avgIntervalSec) else "—")
                }
            }
            Spacer(Modifier.height(Space.md))
            Overline("Recent")
            Spacer(Modifier.height(Space.sm))
            stats.completed.takeLast(10).reversed().forEach { t ->
                val c = t.entry
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(PregaTheme.colors.cardSurface)
                        .combinedClickable(onClick = { }, onLongClick = { editing = c })
                        .padding(Space.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(c.startTime)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = PregaTheme.colors.ink,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        fmtSpan(c.durationSeconds) +
                            (if (t.intervalSec > 0) "  ·  ${fmtSpan(t.intervalSec)} apart" else ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = PregaTheme.colors.inkMuted,
                    )
                }
                Spacer(Modifier.height(Space.xs))
            }
        }

        Spacer(Modifier.height(Space.md))
        PregaButton(text = "Enter Labor Mode", onClick = onEnterLaborMode)

        if (sessionStartedAt != null) {
            Spacer(Modifier.height(Space.sm))
            TextButton(onClick = onEndSession, modifier = Modifier.fillMaxWidth()) {
                Text("End this session", color = PregaTheme.colors.inkMuted)
            }
        }

        Spacer(Modifier.height(Space.md))
        Text(
            "Every pregnancy is different — your care team's instructions always come first. " +
                "If a pattern feels new or strong, it's worth a call.",
            style = MaterialTheme.typography.bodySmall,
            color = PregaTheme.colors.inkFaint,
        )
        Spacer(Modifier.height(Space.navClearance))
    }

    editing?.let { c ->
        EditContractionDialog(
            contraction = c,
            onDismiss = { editing = null },
            onDelete = { onDelete(c.id); editing = null },
            onSave = { newDur -> onAdjust(c.id, newDur); editing = null },
        )
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = PregaTheme.colors.ink)
        Text(label, style = MaterialTheme.typography.labelSmall, color = PregaTheme.colors.inkMuted)
    }
}

@Composable
private fun EditContractionDialog(
    contraction: ContractionEntity,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onSave: (Int) -> Unit,
) {
    var duration by remember { mutableIntStateOf(contraction.durationSeconds) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adjust this contraction") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(fmtSpan(duration), style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(Space.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                    OutlinedButton(onClick = { duration = (duration - 5).coerceAtLeast(1) }) { Text("−5s") }
                    OutlinedButton(onClick = { duration = (duration + 5).coerceAtMost(600) }) { Text("+5s") }
                }
                Spacer(Modifier.height(Space.md))
                TextButton(onClick = onDelete) {
                    Text("Delete this entry", color = PregaTheme.colors.alert)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(duration) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ═══ LABOR MODE — the different interface ══════════════════════════════════

private val NightInk = Color(0xFF241D14)

@Composable
fun LaborModeScreen(
    contractions: List<ContractionEntity>,
    sessionStartedAt: Long?,
    activeContraction: ContractionEntity?,
    onStartContraction: () -> Unit,
    onStopContraction: () -> Unit,
    onEndSession: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContextCompat()
    var provider by remember { mutableStateOf(loadProvider(context)) }
    var editProvider by remember { mutableStateOf(false) }
    val now = rememberNowMs(active = true)
    val stats = remember(contractions, now / 1000) { computeStats(contractions, now) }

    LaunchedEffect(Unit) { AppStats.log(StatEvent.LaborModeEntered) }

    Column(
        Modifier
            .fillMaxSize()
            .background(NightInk)
            .padding(horizontal = Space.gutter)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(Space.xl))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "LABOR MODE",
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFFD9A441),
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClose) { Text("Exit", color = Color(0xFF9B8F7C)) }
        }

        Spacer(Modifier.height(Space.lg))

        // The one number that matters, readable across a dark room.
        if (activeContraction != null) {
            Text(
                fmtClock(elapsedSec(activeContraction, now)),
                fontSize = 120.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFAF8F1),
            )
            Text("contraction", color = Color(0xFF9B8F7C))
        } else {
            Text(
                stats.lastEndedAgoSec?.let { fmtClock(it) } ?: "—",
                fontSize = 120.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF9B8F7C),
            )
            Text("since the last one", color = Color(0xFF9B8F7C))
        }

        Spacer(Modifier.height(Space.lg))

        // One giant, unmissable action.
        Box(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(if (activeContraction != null) Color(0xFFD97A84) else Color(0xFF66713A))
                .clickable { if (activeContraction != null) onStopContraction() else onStartContraction() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (activeContraction != null) "IT'S EASING" else "CONTRACTION STARTING",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }

        Spacer(Modifier.height(Space.lg))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            NightStat("Count", "${stats.completed.size}")
            NightStat("Avg length", if (stats.avgDurationSec > 0) fmtSpan(stats.avgDurationSec) else "—")
            NightStat("Avg apart", if (stats.avgIntervalSec > 0) fmtSpan(stats.avgIntervalSec) else "—")
        }

        if (provider.instructions.isNotBlank()) {
            Spacer(Modifier.height(Space.lg))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF352B1F))
                    .padding(Space.md),
            ) {
                Text("Your provider's instructions", color = Color(0xFFD9A441),
                     style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                Text(provider.instructions, color = Color(0xFFFAF8F1),
                     style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(Modifier.height(Space.lg))

        NightAction("Call ${provider.name.ifBlank { "provider" }}") {
            if (provider.phone.isNotBlank()) {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_DIAL, "tel:${provider.phone}".toUri()))
                }
            } else editProvider = true
        }
        NightAction("Share with partner") {
            sessionStartedAt?.let { shareSummary(context, stats, it) }
        }
        NightAction("Hospital directions") {
            if (provider.hospital.isNotBlank()) {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW,
                            "geo:0,0?q=${android.net.Uri.encode(provider.hospital)}".toUri())
                    )
                }
            } else editProvider = true
        }
        NightAction("Provider & hospital details") { editProvider = true }

        Spacer(Modifier.height(Space.md))
        TextButton(onClick = onEndSession, modifier = Modifier.fillMaxWidth()) {
            Text("End session", color = Color(0xFF9B8F7C))
        }
        Spacer(Modifier.height(Space.sm))
        Text(
            "Every labour is different — your care team's guidance always comes first.",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF9B8F7C),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Space.xl))
    }

    if (editProvider) {
        ProviderDialog(
            initial = provider,
            onDismiss = { editProvider = false },
            onSave = { p -> saveProvider(context, p); provider = p; editProvider = false },
        )
    }
}

@Composable
private fun NightStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = Color(0xFFFAF8F1))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color(0xFF9B8F7C))
    }
}

@Composable
private fun NightAction(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF352B1F))
            .clickable(onClick = onClick)
            .padding(vertical = Space.md, horizontal = Space.lg),
    ) {
        Text(label, color = Color(0xFFFAF8F1), style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun ProviderDialog(
    initial: ProviderInfo,
    onDismiss: () -> Unit,
    onSave: (ProviderInfo) -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var phone by remember { mutableStateOf(initial.phone) }
    var hospital by remember { mutableStateOf(initial.hospital) }
    var instructions by remember { mutableStateOf(initial.instructions) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Provider & hospital") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                OutlinedTextField(value = name, onValueChange = { name = it.take(60) },
                    label = { Text("Provider name") }, singleLine = true)
                OutlinedTextField(value = phone, onValueChange = { phone = it.take(20) },
                    label = { Text("Phone") }, singleLine = true)
                OutlinedTextField(value = hospital, onValueChange = { hospital = it.take(120) },
                    label = { Text("Hospital / birth place address") })
                OutlinedTextField(value = instructions, onValueChange = { instructions = it.take(300) },
                    label = { Text("Their instructions (e.g. when to call)") })
                Text(
                    "Shown exactly as written — the app never adds its own medical advice.",
                    style = MaterialTheme.typography.bodySmall,
                    color = PregaTheme.colors.inkFaint,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(ProviderInfo(name.trim(), phone.trim(), hospital.trim(), instructions.trim())) }) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun LocalContextCompat() = androidx.compose.ui.platform.LocalContext.current
