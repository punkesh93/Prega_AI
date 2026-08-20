package com.example.ui.appointments

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.data.DoctorQuestionEntity
import com.example.stats.AppStats
import com.example.stats.StatEvent
import com.example.ui.components.Overline
import com.example.ui.components.PregaButton
import com.example.ui.components.PregaCard
import com.example.ui.theme.PregaTheme
import com.example.ui.theme.Space
import com.example.viewmodel.PregnancyViewModel

/**
 * The care-coordination pieces that live on the Appointments surface:
 *
 * QUESTION BANK — durable questions that roll forward until answered.
 * Tap the circle to mark answered (strikethrough, drops below open ones),
 * long-press for edit / archive / delete. Adding is one field, one tap.
 *
 * PREP SHEET — "your visit is coming; here's what you may want to discuss,"
 * compiled locally from data the app already holds. THE PRODUCT RULE made
 * structural: every section is a toggle she controls, the preview shows
 * exactly what will leave the phone, and nothing is shared until she taps
 * share. No AI, no network — deterministic, offline, hers.
 */

// ─── Question bank section (embedded in AppointmentsScreen's list) ────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QuestionBankSection(
    questions: List<DoctorQuestionEntity>,
    onAdd: (String) -> Unit,
    onToggleAnswered: (DoctorQuestionEntity) -> Unit,
    onEdit: (DoctorQuestionEntity, String) -> Unit,
    onArchive: (DoctorQuestionEntity) -> Unit,
    onDelete: (Int) -> Unit,
) {
    var draft by remember { mutableStateOf("") }
    var menuFor by remember { mutableStateOf<DoctorQuestionEntity?>(null) }
    var editingQ by remember { mutableStateOf<DoctorQuestionEntity?>(null) }

    Column {
        Overline("Questions for your doctor")
        Spacer(Modifier.height(Space.sm))

        if (questions.isEmpty()) {
            Text(
                "Anything you keep meaning to ask — save it here and it waits for your next visit.",
                style = MaterialTheme.typography.bodySmall,
                color = PregaTheme.colors.inkFaint,
            )
            Spacer(Modifier.height(Space.sm))
        }

        questions.forEach { q ->
            val answered = q.answeredAt != null
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(PregaTheme.colors.cardSurface)
                    .combinedClickable(
                        onClick = { onToggleAnswered(q) },
                        onLongClick = { menuFor = q },
                    )
                    .padding(Space.md)
                    .alpha(if (answered) 0.55f else 1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = answered, onClick = { onToggleAnswered(q) })
                Spacer(Modifier.width(Space.sm))
                Text(
                    q.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = PregaTheme.colors.ink,
                    textDecoration = if (answered) TextDecoration.LineThrough else null,
                )
            }
            Spacer(Modifier.height(Space.xs))

            DropdownMenu(expanded = menuFor == q, onDismissRequest = { menuFor = null }) {
                DropdownMenuItem(text = { Text("Edit") },
                    onClick = { editingQ = q; menuFor = null })
                DropdownMenuItem(text = { Text("Archive") },
                    onClick = { onArchive(q); menuFor = null })
                DropdownMenuItem(text = { Text("Delete") },
                    onClick = { onDelete(q.id); menuFor = null })
            }
        }

        Spacer(Modifier.height(Space.sm))
        Row(verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.take(300) },
                placeholder = { Text("e.g. Is this much back pain normal?") },
                modifier = Modifier.weight(1f),
                maxLines = 3,
                shape = MaterialTheme.shapes.medium,
            )
            Spacer(Modifier.width(Space.sm))
            TextButton(
                enabled = draft.isNotBlank(),
                onClick = { onAdd(draft); draft = "" },
            ) { Text("Save") }
        }
    }

    editingQ?.let { q ->
        var text by remember(q.id) { mutableStateOf(q.text) }
        AlertDialog(
            onDismissRequest = { editingQ = null },
            title = { Text("Edit question") },
            text = {
                OutlinedTextField(value = text, onValueChange = { text = it.take(300) }, maxLines = 4)
            },
            confirmButton = {
                TextButton(
                    enabled = text.isNotBlank(),
                    onClick = { onEdit(q, text); editingQ = null },
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editingQ = null }) { Text("Cancel") } },
        )
    }
}

// ─── Appointment prep sheet ───────────────────────────────────────────────

@Composable
fun AppointmentPrepSheet(
    appointmentTitle: String,
    appointmentDate: String,
    prep: PregnancyViewModel.AppointmentPrep,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var includeSymptoms by remember { mutableStateOf(prep.symptomLines.isNotEmpty()) }
    var includeMood by remember { mutableStateOf(prep.moodLine != null) }
    var includeMovement by remember { mutableStateOf(prep.movementLine != null) }
    var includeWater by remember { mutableStateOf(false) } // opt-in: least clinical
    var includeQuestions by remember { mutableStateOf(prep.openQuestions.isNotEmpty()) }

    val summary = remember(
        includeSymptoms, includeMood, includeMovement, includeWater, includeQuestions,
    ) {
        buildString {
            appendLine("Visit prep — $appointmentTitle ($appointmentDate)")
            appendLine(prep.weekLine)
            prep.lastVisitLine?.let { appendLine(it) }
            if (includeSymptoms && prep.symptomLines.isNotEmpty()) {
                appendLine(); appendLine("Since then:")
                prep.symptomLines.forEach { appendLine("\u2022 $it") }
            }
            if (includeMood && prep.moodLine != null) appendLine("\u2022 ${prep.moodLine}")
            if (includeMovement && prep.movementLine != null) appendLine("\u2022 ${prep.movementLine}")
            if (includeWater && prep.waterLine != null) appendLine("\u2022 ${prep.waterLine}")
            if (includeQuestions && prep.openQuestions.isNotEmpty()) {
                appendLine(); appendLine("Questions to ask:")
                prep.openQuestions.forEach { appendLine("\u2022 $it") }
            }
            appendLine()
            append("Compiled by her in Prega AI \u2014 for discussion, not diagnosis.")
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Prepare for this visit") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    "Everything below comes from what you've already logged. " +
                        "Choose what to include — nothing is shared until you share it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = PregaTheme.colors.inkMuted,
                )
                Spacer(Modifier.height(Space.md))
                PrepToggle("Symptoms (${prep.symptomLines.size})", includeSymptoms,
                    enabled = prep.symptomLines.isNotEmpty()) { includeSymptoms = it }
                PrepToggle("Mood pattern", includeMood,
                    enabled = prep.moodLine != null) { includeMood = it }
                PrepToggle("Baby movement", includeMovement,
                    enabled = prep.movementLine != null) { includeMovement = it }
                PrepToggle("Water habits", includeWater,
                    enabled = prep.waterLine != null) { includeWater = it }
                PrepToggle("Open questions (${prep.openQuestions.size})", includeQuestions,
                    enabled = prep.openQuestions.isNotEmpty()) { includeQuestions = it }
                Spacer(Modifier.height(Space.md))
                PregaCard(border = false, containerColor = PregaTheme.colors.recessed) {
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = PregaTheme.colors.inkMuted,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                AppStats.log(StatEvent.AppointmentSummaryShared)
                runCatching {
                    context.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"; putExtra(Intent.EXTRA_TEXT, summary)
                            },
                            "Share visit summary",
                        )
                    )
                }
            }) { Text("Share") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun PrepToggle(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(enabled = enabled) { onChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) PregaTheme.colors.ink else PregaTheme.colors.inkFaint,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked && enabled, onCheckedChange = onChange, enabled = enabled)
    }
}
