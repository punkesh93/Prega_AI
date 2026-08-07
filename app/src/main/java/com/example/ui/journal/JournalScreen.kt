package com.example.ui.journal

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.data.JournalEntity
import com.example.ui.components.*
import com.example.ui.theme.Motion
import com.example.ui.theme.PregaTheme
import com.example.ui.theme.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Prega AI — the memory journal.
 *
 * A daily check-in becomes a keepsake: mood, a note, an optional photo, and
 * a caption — hers when she writes one, AI-written to match her note's vibe
 * when she doesn't. Entries stack into an animated timeline grouped the way
 * she'll remember the pregnancy: by trimester.
 *
 * Photos are picked with the system Photo Picker (no storage permission —
 * she chooses exactly which image the app may see) and are COPIED into
 * private app storage, because picker grants are temporary. They exist
 * nowhere but this phone until she taps share.
 *
 * Sharing stamps the text with "Captured with Prega AI" — her memory
 * carries the app to the group chat, which is the only growth channel that
 * costs nothing and annoys no one.
 */
@Composable
fun JournalScreen(
    entries: List<JournalEntity>,
    checkedInToday: Boolean,
    onSave: (note: String, photoFile: String, mood: Int) -> Unit,
    onDelete: (Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var composing by remember { mutableStateOf(!checkedInToday) }

    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .imePadding(),
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
                "Journal",
                style = MaterialTheme.typography.headlineMedium,
                color = PregaTheme.colors.ink,
                modifier = Modifier.weight(1f),
            )
            if (!composing) {
                PregaTextButton("New memory", { composing = true })
            }
        }

        if (composing) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                Composer(
                    onSave = { note, photo, mood ->
                        onSave(note, photo, mood)
                        composing = false
                    },
                    onCancel = { composing = false },
                )
                Spacer(Modifier.height(Space.xl))
            }
        } else if (entries.isEmpty()) {
            EmptyJournal(onStart = { composing = true })
        } else {
            Timeline(entries, onDelete, onShare = { shareEntry(context, it) })
        }
    }
}

// ─── Composer ──────────────────────────────────────────────────────────────

@Composable
private fun Composer(
    onSave: (note: String, photoFile: String, mood: Int) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var note by remember { mutableStateOf("") }
    var mood by remember { mutableStateOf(0) }
    var photoFile by remember { mutableStateOf("") }

    val pickPhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                photoFile = withContext(Dispatchers.IO) { copyIntoJournal(context, uri) }
            }
        }
    }

    PregaCard(
        modifier = Modifier.padding(horizontal = Space.gutter),
        contentPadding = PaddingValues(Space.lg),
    ) {
        Overline("Today's check-in")
        Spacer(Modifier.height(Space.md))

        // Mood: the same circled-faces language as the home screen.
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            listOf("😔", "😕", "😊", "🥰", "✨").forEachIndexed { i, e ->
                val selected = mood == i + 1
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) PregaTheme.colors.sageSoft
                            else PregaTheme.colors.recessed
                        )
                        .clickable { mood = i + 1 },
                    contentAlignment = Alignment.Center,
                ) { Text(e, fontSize = 20.sp) }
            }
        }

        Spacer(Modifier.height(Space.md))
        OutlinedTextField(
            value = note,
            onValueChange = { note = it.take(500) },
            placeholder = { Text("How was today, really?") },
            minLines = 2,
            maxLines = 5,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(Space.md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(PregaTheme.colors.lavenderSoft)
                    .clickable {
                        pickPhoto.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.AddAPhoto,
                    contentDescription = "Add a photo",
                    tint = PregaTheme.colors.lavender,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(Space.md))
            Text(
                if (photoFile.isBlank()) "Add a photo of today (optional)"
                else "Photo attached",
                style = MaterialTheme.typography.bodySmall,
                color = PregaTheme.colors.inkMuted,
            )
        }

        Spacer(Modifier.height(Space.lg))
        Row {
            PregaTextButton("Not today", onCancel)
            Spacer(Modifier.weight(1f))
            PregaButton(
                text = "Keep this day",
                onClick = { onSave(note.trim(), photoFile, mood) },
                fillWidth = false,
            )
        }
        Spacer(Modifier.height(Space.xs))
        Text(
            "If you leave the caption to us, Prega AI writes one to match your day.",
            style = MaterialTheme.typography.bodySmall,
            color = PregaTheme.colors.inkFaint,
        )
    }
}

// ─── Timeline ──────────────────────────────────────────────────────────────

@Composable
private fun Timeline(
    entries: List<JournalEntity>,
    onDelete: (Long) -> Unit,
    onShare: (JournalEntity) -> Unit,
) {
    val byTrimester = remember(entries) { entries.groupBy { it.trimester } }

    LazyColumn(
        contentPadding = PaddingValues(
            start = Space.gutter, end = Space.gutter,
            top = Space.md, bottom = Space.navClearance,
        ),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        byTrimester.forEach { (trimester, group) ->
            item(key = "t$trimester") {
                Overline("Trimester $trimester")
                Spacer(Modifier.height(Space.xs))
            }
            items(group, key = { it.id }) { entry ->
                Box(Modifier.animateItem()) {
                    EntryCard(entry, onDelete, onShare)
                }
            }
        }
    }
}

@Composable
private fun EntryCard(
    entry: JournalEntity,
    onDelete: (Long) -> Unit,
    onShare: (JournalEntity) -> Unit,
) {
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }

    // Decode off the main thread; small screens, small files, but never jank.
    val bitmap by produceState<android.graphics.Bitmap?>(null, entry.photoFile) {
        value = if (entry.photoFile.isBlank()) null
        else withContext(Dispatchers.IO) {
            runCatching {
                android.graphics.BitmapFactory.decodeFile(
                    File(File(context.filesDir, "journal"), entry.photoFile).absolutePath
                )
            }.getOrNull()
        }
    }

    PregaCard(contentPadding = PaddingValues(0.dp)) {
        Column {
            bitmap?.let {
                val photoAlpha by animateFloatAsState(
                    targetValue = 1f,
                    animationSpec = tween(450),
                    label = "photoFade",
                )
                androidx.compose.foundation.Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                        .graphicsLayer { alpha = photoAlpha },
                )
            }
            Column(Modifier.padding(Space.lg)) {
                Text(
                    "\u201C${entry.caption}\u201D",
                    style = MaterialTheme.typography.titleLarge,
                    color = PregaTheme.colors.ink,
                )
                if (entry.note.isNotBlank() && entry.note != entry.caption) {
                    Spacer(Modifier.height(Space.xs))
                    Text(
                        entry.note,
                        style = MaterialTheme.typography.bodyMedium,
                        color = PregaTheme.colors.inkMuted,
                    )
                }
                Spacer(Modifier.height(Space.sm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Week ${entry.week} · ${entry.date}",
                        style = MaterialTheme.typography.bodySmall,
                        color = PregaTheme.colors.inkFaint,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onShare(entry) }) {
                        Icon(
                            Icons.Outlined.Share,
                            contentDescription = "Share this memory",
                            tint = PregaTheme.colors.inkMuted,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                    PregaTextButton("Remove", { confirmDelete = true })
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Remove this memory?") },
            text = { Text("It will be deleted from this phone. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete(entry.id) }) {
                    Text("Remove", color = PregaTheme.colors.alert)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Keep it") }
            },
        )
    }
}

@Composable
private fun EmptyJournal(onStart: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(Space.gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        LeafSprig(
            color = PregaTheme.colors.sage,
            modifier = Modifier.size(120.dp),
        )
        Spacer(Modifier.height(Space.lg))
        Text(
            "Nine months goes faster than it feels",
            style = MaterialTheme.typography.headlineSmall,
            color = PregaTheme.colors.ink,
        )
        Spacer(Modifier.height(Space.sm))
        Text(
            "A photo and a line a day becomes the story of all of it.",
            style = MaterialTheme.typography.bodyMedium,
            color = PregaTheme.colors.inkMuted,
        )
        Spacer(Modifier.height(Space.xl))
        PregaButton("Keep today", onStart, fillWidth = false)
    }
}

// ─── Plumbing ──────────────────────────────────────────────────────────────

/** Copies a picked image into private storage; returns the stored filename. */
private fun copyIntoJournal(context: Context, uri: Uri): String {
    val dir = File(context.filesDir, "journal").apply { mkdirs() }
    val name = "mem_${System.currentTimeMillis()}.jpg"
    runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            File(dir, name).outputStream().use { output -> input.copyTo(output) }
        }
    }.onFailure { return "" }
    return name
}

/**
 * Shares a memory: the photo when there is one, plus stamped text. The
 * stamp is one quiet line — her memory is the content, the app is the
 * signature.
 */
private fun shareEntry(context: Context, entry: JournalEntity) {
    val text = buildString {
        append("\u201C${entry.caption}\u201D\n")
        append("Week ${entry.week} of our journey\n\n")
        append("Captured with Prega AI 🌸")
    }

    val intent = Intent(Intent.ACTION_SEND).apply {
        putExtra(Intent.EXTRA_TEXT, text)
        if (entry.photoFile.isNotBlank()) {
            val file = File(File(context.filesDir, "journal"), entry.photoFile)
            if (file.exists()) {
                val uri = FileProvider.getUriForFile(
                    context, "${context.packageName}.share", file,
                )
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                type = "image/jpeg"
            } else type = "text/plain"
        } else type = "text/plain"
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, "Share this memory"))
    }
}
