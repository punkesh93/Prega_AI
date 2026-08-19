package com.example.community

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.core.net.toUri
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.*
import com.example.ui.theme.PregaTheme
import com.example.ui.theme.Space
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Birth Club — the community surface. Full-screen overlay, same pattern as
 * the Journal. Three moments in one screen:
 *
 *   1. Not joined yet -> a plain-words invitation: what it is, what's shared
 *      (a handle + due month + what she chooses to post), what never is
 *      (everything else — the local-first promise stays intact), and the
 *      community's three rules. Joining is one handle + one tap.
 *   2. Joined -> chips: Chat | Posts | Circles.
 *   3. Anything offline/unconfigured -> calm "waking up" copy, never an
 *      error dialog. Community is weather, not plumbing.
 *
 * Safety is structural: long-press any message or post for Report / Block
 * (Play UGC policy), red-flagged medical claims render with the midwife
 * overlay, and blocked authors are filtered before rendering.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CommunityScreen(
    dueDate: String,
    currentWeek: Int,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var profile by remember { mutableStateOf<CommunityProfile?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        CommunityRepository.ensureSignedIn()
        profile = CommunityRepository.myProfile()
        loading = false
    }

    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.gutter, vertical = Space.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Overline("Birth club")
                Text(
                    profile?.let { "Due ${prettyMonth(it.dueMonth)}" } ?: "Mothers due when you are",
                    style = MaterialTheme.typography.titleLarge,
                    color = PregaTheme.colors.ink,
                )
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, contentDescription = "Close", tint = PregaTheme.colors.ink)
            }
        }

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Breathing()
            }
            profile == null -> JoinCard(dueDate) { handle ->
                scope.launch {
                    loading = true
                    profile = CommunityRepository.joinBirthClub(handle, dueDate)
                    loading = false
                }
            }
            else -> ClubHome(profile!!, currentWeek)
        }
    }
}

// ─── Join ──────────────────────────────────────────────────────────────────

@Composable
private fun JoinCard(dueDate: String, onJoin: (String) -> Unit) {
    var handle by remember { mutableStateOf("") }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.gutter),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        PregaCard(containerColor = PregaTheme.colors.sageSoft, border = false) {
            Text(
                "A private room of mothers due ${prettyMonth(dueDate.take(7))} — going through week by week together.",
                style = MaterialTheme.typography.bodyLarge,
                color = PregaTheme.colors.ink,
            )
            Spacer(Modifier.height(Space.md))
            Text(
                "What's shared: a nickname you choose, your due month, and only what you post. " +
                    "Everything else — your journal, moods, weights, photos — stays on your phone, same as always.",
                style = MaterialTheme.typography.bodySmall,
                color = PregaTheme.colors.inkMuted,
            )
            Spacer(Modifier.height(Space.md))
            Text(
                "Three rules: be kind, no selling, and no medical advice — " +
                    "share experiences, let midwives do medicine.",
                style = MaterialTheme.typography.bodySmall,
                color = PregaTheme.colors.inkMuted,
            )
        }
        OutlinedTextField(
            value = handle,
            onValueChange = { handle = it.take(24) },
            label = { Text("Pick a nickname") },
            placeholder = { Text("e.g. MoonMama") },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        PregaButton(
            text = "Join my birth club",
            enabled = handle.trim().length >= 3,
            onClick = { onJoin(handle) },
        )
        Text(
            "You can leave any time from this screen — leaving deletes your community profile.",
            style = MaterialTheme.typography.bodySmall,
            color = PregaTheme.colors.inkFaint,
        )
    }
}

// ─── Club home ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ClubHome(profile: CommunityProfile, currentWeek: Int) {
    var section by rememberSaveable { mutableStateOf("Chat") }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.padding(horizontal = Space.gutter),
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            listOf("Chat", "Posts", "Circles").forEach { s ->
                PregaChip(label = s, selected = section == s, onClick = { section = s })
            }
        }
        Spacer(Modifier.height(Space.sm))
        when (section) {
            "Chat" -> ChatSection(profile)
            "Posts" -> PostsSection(profile)
            else -> CirclesSection(profile)
        }
    }
}

// ─── Chat ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatSection(profile: CommunityProfile) {
    val scope = rememberCoroutineScope()
    val messages by CommunityRepository.messages(profile.dueMonth)
        .collectAsState(initial = emptyList())
    val blocked by CommunityRepository.myBlockedIds().collectAsState(initial = emptySet())
    val visible = remember(messages, blocked) { messages.filter { it.authorId !in blocked } }
    var input by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        if (visible.isEmpty()) {
            EmptyState("It's quiet in here — say the first hello 🌸")
        } else {
            LazyColumn(
                Modifier.weight(1f),
                reverseLayout = true,
                contentPadding = PaddingValues(horizontal = Space.gutter, vertical = Space.md),
                verticalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                items(visible, key = { it.id }) { m ->
                    CommunityBubble(
                        handle = m.handle,
                        body = m.body,
                        flagged = m.flagged,
                        mine = m.authorId == profile.uid,
                        onReport = {
                            scope.launch {
                                CommunityRepository.report(profile.dueMonth, "message", m.id, "reported from app")
                            }
                        },
                        onBlock = { scope.launch { CommunityRepository.block(m.authorId) } },
                    )
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .background(PregaTheme.colors.cardSurface)
                .navigationBarsPadding()
                .imePadding()
                .padding(Space.md),
            verticalAlignment = Alignment.Bottom,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message your club…") },
                maxLines = 4,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = PregaTheme.colors.hairline,
                ),
            )
            Spacer(Modifier.width(Space.sm))
            val canSend = input.isNotBlank()
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (canSend) MaterialTheme.colorScheme.primary
                        else PregaTheme.colors.hairline
                    )
                    .clickable(enabled = canSend) {
                        val text = input.trim(); input = ""
                        scope.launch { CommunityRepository.sendMessage(profile.dueMonth, profile, text) }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.Send,
                    contentDescription = "Send",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

// ─── Posts ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PostsSection(profile: CommunityProfile) {
    val scope = rememberCoroutineScope()
    val posts by CommunityRepository.posts(profile.dueMonth)
        .collectAsState(initial = emptyList())
    val blocked by CommunityRepository.myBlockedIds().collectAsState(initial = emptySet())
    val visible = remember(posts, blocked) { posts.filter { it.authorId !in blocked } }

    if (visible.isEmpty()) {
        EmptyState(
            "Moments your club chose to share appear here.\n" +
                "Share one from your journal — the \u201Cshare with my birth club\u201D switch."
        )
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = Space.gutter, vertical = Space.md),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        items(visible, key = { it.id }) { p ->
            PregaCard(border = false, containerColor = PregaTheme.colors.cardSurface) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        p.handle,
                        style = MaterialTheme.typography.labelMedium,
                        color = PregaTheme.colors.ink,
                        modifier = Modifier.weight(1f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "Week ${p.week}",
                        style = MaterialTheme.typography.labelSmall,
                        color = PregaTheme.colors.inkFaint,
                    )
                }
                Spacer(Modifier.height(Space.sm))
                Text(p.body, style = MaterialTheme.typography.bodyLarge, color = PregaTheme.colors.ink)
                Spacer(Modifier.height(Space.sm))
                Row {
                    Text(
                        "Report",
                        style = MaterialTheme.typography.labelSmall,
                        color = PregaTheme.colors.inkFaint,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable {
                                scope.launch {
                                    CommunityRepository.report(profile.dueMonth, "post", p.id, "reported from app")
                                }
                            }
                            .padding(horizontal = Space.sm, vertical = 2.dp),
                    )
                    if (p.authorId != profile.uid) {
                        Text(
                            "Block",
                            style = MaterialTheme.typography.labelSmall,
                            color = PregaTheme.colors.inkFaint,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable { scope.launch { CommunityRepository.block(p.authorId) } }
                                .padding(horizontal = Space.sm, vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

// ─── Circles ───────────────────────────────────────────────────────────────

@Composable
private fun CirclesSection(profile: CommunityProfile) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val circles by CommunityRepository.circles(profile.dueMonth)
        .collectAsState(initial = emptyList())
    var hosting by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = Space.gutter, vertical = Space.md),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            if (circles.isEmpty()) {
                item {
                    EmptyState(
                        "Circles are small video calls for your club —\n" +
                            "a cup of chai with mothers who get it. Host the first one."
                    )
                }
            }
            items(circles, key = { it.id }) { c ->
                PregaCard(border = false, containerColor = PregaTheme.colors.lavenderSoft) {
                    Text(c.title, style = MaterialTheme.typography.titleMedium, color = PregaTheme.colors.ink)
                    Spacer(Modifier.height(Space.xs))
                    Text(
                        "Hosted by ${c.handle} · ${prettyTime(c.startsAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = PregaTheme.colors.inkMuted,
                    )
                    Spacer(Modifier.height(Space.md))
                    PregaButton(
                        text = "Join the call",
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, "https://meet.jit.si/${c.jitsiRoom}".toUri())
                                )
                            }
                        },
                    )
                }
            }
        }
        Box(Modifier.padding(Space.gutter)) {
            PregaButton(text = "Host a circle", onClick = { hosting = true })
        }
    }

    if (hosting) {
        HostCircleDialog(
            onDismiss = { hosting = false },
            onHost = { title, startsAt ->
                hosting = false
                scope.launch { CommunityRepository.hostCircle(profile.dueMonth, profile, title, startsAt) }
            },
        )
    }
}

@Composable
private fun HostCircleDialog(onDismiss: () -> Unit, onHost: (String, Long) -> Unit) {
    var title by remember { mutableStateOf("") }
    var slot by remember { mutableStateOf(1) }
    val slots = remember {
        fun at(dayOffset: Int, hour: Int): Pair<String, Long> {
            val c = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, dayOffset)
                set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val label = (if (dayOffset == 0) "Today" else "Tomorrow") + " " +
                SimpleDateFormat("h a", Locale.getDefault()).format(c.time)
            return label to c.timeInMillis
        }
        val now = Calendar.getInstance()
        buildList {
            if (now.get(Calendar.HOUR_OF_DAY) < 19) add(at(0, 20))
            add(at(1, 11)); add(at(1, 17)); add(at(1, 20))
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Host a circle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(80) },
                    label = { Text("What's it about?") },
                    placeholder = { Text("Third-trimester sleep survival") },
                    singleLine = true,
                )
                Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    slots.forEachIndexed { i, (label, _) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { slot = i }
                                .padding(Space.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = slot == i, onClick = { slot = i })
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                Text(
                    "The call opens as a private video room link — no app needed to join.",
                    style = MaterialTheme.typography.bodySmall,
                    color = PregaTheme.colors.inkFaint,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.trim().length >= 3,
                onClick = { onHost(title, slots[slot].second) },
            ) { Text("Schedule") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ─── Shared bits ───────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CommunityBubble(
    handle: String,
    body: String,
    flagged: Boolean,
    mine: Boolean,
    onReport: () -> Unit,
    onBlock: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (mine) PregaTheme.colors.sageSoft else PregaTheme.colors.cardSurface)
            .combinedClickable(onClick = { }, onLongClick = { menu = true })
            .padding(Space.md),
    ) {
        Text(
            if (mine) "You" else handle,
            style = MaterialTheme.typography.labelSmall,
            color = PregaTheme.colors.inkFaint,
        )
        Spacer(Modifier.height(2.dp))
        Text(body, style = MaterialTheme.typography.bodyLarge, color = PregaTheme.colors.ink)
        if (flagged) {
            Spacer(Modifier.height(Space.sm))
            SafetyNotice(
                text = "This mentions symptoms that deserve a professional — " +
                    "if this is you, please talk to your midwife or doctor."
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Report") }, onClick = { menu = false; onReport() })
            if (!mine) {
                DropdownMenuItem(text = { Text("Block this member") }, onClick = { menu = false; onBlock() })
            }
        }
    }
}

@Composable
private fun EmptyState(text: String) {
    Box(Modifier.fillMaxWidth().padding(Space.xl), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = PregaTheme.colors.inkMuted,
        )
    }
}

private fun prettyMonth(yyyyMm: String): String = runCatching {
    val (y, m) = yyyyMm.split("-")
    listOf(
        "January", "February", "March", "April", "May", "June", "July",
        "August", "September", "October", "November", "December",
    )[m.toInt() - 1] + " " + y
}.getOrDefault(yyyyMm)

private fun prettyTime(millis: Long): String =
    SimpleDateFormat("EEE d MMM, h:mm a", Locale.getDefault()).format(Date(millis))
