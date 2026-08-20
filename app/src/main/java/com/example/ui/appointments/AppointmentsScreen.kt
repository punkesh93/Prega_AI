package com.example.ui.appointments

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.AppointmentEntity
import com.example.ui.components.*
import com.example.ui.theme.PregaTheme
import com.example.ui.theme.Space
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Prega AI — appointments.
 *
 * A real page at last (the explore cell used to dead-end into the You tab).
 * Upcoming visits first, then past ones quietly below. Adding takes a
 * title, a real date picker, optional time/place — and the field that
 * matters most in practice: the questions she wants to remember to ask,
 * because the surest way to leave an appointment frustrated is to remember
 * the question in the car park.
 *
 * Reminders ride the existing notification engine (appointment channel);
 * this screen is where they come from.
 */
@Composable
fun AppointmentsScreen(
    appointments: List<AppointmentEntity>,
    todayDate: String,
    questions: List<com.example.data.DoctorQuestionEntity> = emptyList(),
    onSave: (AppointmentEntity) -> Unit,
    onDelete: (Int) -> Unit,
    onAddQuestion: (String) -> Unit = {},
    onToggleAnswered: (com.example.data.DoctorQuestionEntity) -> Unit = {},
    onEditQuestion: (com.example.data.DoctorQuestionEntity, String) -> Unit = { _, _ -> },
    onArchiveQuestion: (com.example.data.DoctorQuestionEntity) -> Unit = {},
    onDeleteQuestion: (Int) -> Unit = {},
    onBuildPrep: suspend (sinceDate: String?) -> com.example.viewmodel.PregnancyViewModel.AppointmentPrep? = { null },
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf<AppointmentEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    var prepFor by remember { mutableStateOf<AppointmentEntity?>(null) }
    var prepData by remember {
        mutableStateOf<com.example.viewmodel.PregnancyViewModel.AppointmentPrep?>(null)
    }
    val prepScope = rememberCoroutineScope()

    prepFor?.let { appt ->
        prepData?.let { data ->
            AppointmentPrepSheet(
                appointmentTitle = appt.title,
                appointmentDate = appt.date,
                prep = data,
                onDismiss = { prepFor = null; prepData = null },
            )
        }
    }

    val (upcoming, past) = remember(appointments, todayDate) {
        appointments.partition { it.date >= todayDate && !it.completed }
    }

    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.gutter, vertical = Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back",
                    tint = PregaTheme.colors.ink,
                )
            }
            Text(
                "Appointments",
                style = MaterialTheme.typography.headlineMedium,
                color = PregaTheme.colors.ink,
                modifier = Modifier.weight(1f),
            )
            PregaTextButton("Add", { adding = true })
        }

        if (appointments.isEmpty()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(Space.gutter),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                LeafSprig(color = PregaTheme.colors.sage, modifier = Modifier.size(110.dp))
                Spacer(Modifier.height(Space.lg))
                Text(
                    "No visits logged yet",
                    style = MaterialTheme.typography.headlineSmall,
                    color = PregaTheme.colors.ink,
                )
                Spacer(Modifier.height(Space.sm))
                Text(
                    "Add your next check-up and Prega will remind you — and hold the questions you want to ask.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PregaTheme.colors.inkMuted,
                    modifier = Modifier.padding(horizontal = Space.lg),
                )
                Spacer(Modifier.height(Space.xl))
                PregaButton("Add an appointment", { adding = true }, fillWidth = false)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = Space.gutter, end = Space.gutter,
                    top = Space.sm, bottom = Space.navClearance,
                ),
                verticalArrangement = Arrangement.spacedBy(Space.md),
            ) {
                upcoming.minByOrNull { it.date }?.let { next ->
                    item {
                        PregaCard(
                            containerColor = PregaTheme.colors.goldSoft,
                            border = false,
                            onClick = {
                                prepFor = next
                                prepScope.launch {
                                    // Cover the stretch since the last completed
                                    // visit; fall back to everything recent.
                                    val since = appointments
                                        .filter { it.date < todayDate }
                                        .maxByOrNull { it.date }?.date
                                    prepData = onBuildPrep(since)
                                }
                            },
                        ) {
                            Column {
                                Overline("Coming up \u00B7 ${next.date}")
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Prepare for ${next.title}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = PregaTheme.colors.ink,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "One tap compiles what's happened since your last visit — you choose what to bring.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = PregaTheme.colors.inkMuted,
                                )
                            }
                        }
                    }
                }
                item {
                    QuestionBankSection(
                        questions = questions,
                        onAdd = onAddQuestion,
                        onToggleAnswered = onToggleAnswered,
                        onEdit = onEditQuestion,
                        onArchive = onArchiveQuestion,
                        onDelete = onDeleteQuestion,
                    )
                }
                if (upcoming.isNotEmpty()) {
                    item { Overline("Coming up") }
                    items(upcoming, key = { "u${it.id}" }) { appt ->
                        AppointmentCard(
                            appt,
                            onEdit = { editing = appt },
                            onDone = { onSave(appt.copy(completed = true)) },
                        )
                    }
                }
                if (past.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(Space.sm))
                        Overline("Past")
                    }
                    items(past, key = { "p${it.id}" }) { appt ->
                        AppointmentCard(
                            appt,
                            onEdit = { editing = appt },
                            onDone = null,
                            onDelete = { onDelete(appt.id) },
                        )
                    }
                }
            }
        }
    }

    if (adding || editing != null) {
        AppointmentDialog(
            initial = editing,
            onDismiss = { adding = false; editing = null },
            onSave = {
                onSave(it)
                adding = false; editing = null
            },
        )
    }
}

@Composable
private fun AppointmentCard(
    appt: AppointmentEntity,
    onEdit: () -> Unit,
    onDone: (() -> Unit)?,
    onDelete: (() -> Unit)? = null,
) {
    PregaCard(onClick = onEdit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    appt.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = PregaTheme.colors.ink,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    listOf(
                        friendlyDate(appt.date),
                        appt.time.takeIf { it.isNotBlank() },
                        appt.location.takeIf { it.isNotBlank() },
                    ).filterNotNull().joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = PregaTheme.colors.inkMuted,
                )
                if (appt.questionsToAsk.isNotBlank()) {
                    Spacer(Modifier.height(Space.xs))
                    Text(
                        "To ask: ${appt.questionsToAsk}",
                        style = MaterialTheme.typography.bodySmall,
                        color = PregaTheme.colors.sage,
                    )
                }
            }
            if (onDone != null) {
                PregaTextButton("Done", onDone)
            } else if (onDelete != null) {
                PregaTextButton("Remove", onDelete)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppointmentDialog(
    initial: AppointmentEntity?,
    onDismiss: () -> Unit,
    onSave: (AppointmentEntity) -> Unit,
) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var date by remember { mutableStateOf(initial?.date ?: "") }
    var time by remember { mutableStateOf(initial?.time ?: "") }
    var location by remember { mutableStateOf(initial?.location ?: "") }
    var questions by remember { mutableStateOf(initial?.questionsToAsk ?: "") }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New appointment" else "Edit appointment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("What is it? (e.g. 24-week scan)") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = if (date.isBlank()) "" else friendlyDate(date),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Date") },
                    trailingIcon = {
                        PregaTextButton("Pick", { showDatePicker = true })
                    },
                )
                OutlinedTextField(
                    value = time,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Time (optional)") },
                    trailingIcon = {
                        PregaTextButton("Pick", { showTimePicker = true })
                    },
                )
                OutlinedTextField(
                    value = location, onValueChange = { location = it },
                    label = { Text("Where (optional)") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = questions, onValueChange = { questions = it },
                    label = { Text("Questions to ask (optional)") },
                    minLines = 2,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() && date.isNotBlank(),
                onClick = {
                    onSave(
                        (initial ?: AppointmentEntity(title = "", date = ""))
                            .copy(
                                title = title.trim(),
                                date = date,
                                time = time.trim(),
                                location = location.trim(),
                                questionsToAsk = questions.trim(),
                            )
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (showTimePicker) {
        val tState = rememberTimePickerState(is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Pick a time") },
            text = { TimePicker(state = tState) },
            confirmButton = {
                TextButton(onClick = {
                    time = "%02d:%02d".format(tState.hour, tState.minute)
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            },
        )
    }

    if (showDatePicker) {
        val state = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        date = Instant.ofEpochMilli(it)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                            .toString()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
        ) { DatePicker(state = state) }
    }
}

private fun friendlyDate(iso: String): String = runCatching {
    val d = LocalDate.parse(iso)
    "${d.dayOfMonth} ${d.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)} ${d.year}"
}.getOrDefault(iso)
