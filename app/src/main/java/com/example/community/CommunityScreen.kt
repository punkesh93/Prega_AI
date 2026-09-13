package com.example.community

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.luminance
import androidx.core.net.toUri
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.stats.AppStats
import com.example.stats.StatEvent
import com.example.ui.components.*
import com.example.ui.theme.BloomRoseDeep
import com.example.ui.theme.BloomRoseSoft
import com.example.ui.theme.BloomRoseWhisper
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
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
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
                Breathing {
                    Text("🌸", fontSize = 28.sp)
                }
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

/**
 * The one growth loop the community has: every member is an inviter, and
 * the share sheet reaches WhatsApp/SMS where Indian mothers actually are.
 * Shared by the header chip and the new-club empty state so the message
 * can never drift between them.
 */
private fun inviteToClub(context: android.content.Context, dueMonth: String) {
    AppStats.log(StatEvent.CommunityInviteSent)
    runCatching {
        val text = "Join my ${prettyMonth(dueMonth)} birth club on " +
            "Prega AI — mothers due the same month, chatting and helping " +
            "each other through it. Free download: " +
            "https://punkesh93.github.io/Prega_AI/"
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                },
                "Invite to your birth club",
            )
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ClubHome(profile: CommunityProfile, currentWeek: Int) {
    var section by rememberSaveable { mutableStateOf("Chat") }
    val context = LocalContext.current
    // Who is here. Discoverability is cohort-only by design, so a brand-new
    // club is EMPTY — and an empty room with no explanation reads as
    // broken. The member row makes the size honest ("Just you so far") and
    // the chat's empty state turns that into the invite.
    val membersFlow = remember(profile.dueMonth) { CommunityRepository.members(profile.dueMonth) }
    val members by membersFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    Column(Modifier.fillMaxSize()) {
        MembersRow(
            handles = members,
            me = profile.handle,
            modifier = Modifier.padding(horizontal = Space.gutter),
        )
        Spacer(Modifier.height(Space.md))
        Row(
            Modifier.padding(horizontal = Space.gutter),
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf("Chat", "Posts", "Circles").forEach { s ->
                PregaChip(label = s, selected = section == s, onClick = { section = s })
            }
            Spacer(Modifier.weight(1f))
            InviteChip { inviteToClub(context, profile.dueMonth) }
        }
        Spacer(Modifier.height(Space.sm))
        when (section) {
            "Chat" -> ChatSection(
                profile = profile,
                memberCount = members.size,
                onInvite = { inviteToClub(context, profile.dueMonth) },
            )
            "Posts" -> PostsSection(profile)
            else -> CirclesSection(profile)
        }
    }
}

/** Overlapping initials plus an honest count. No presence — we don't have it. */
@Composable
private fun MembersRow(handles: List<String>, me: String, modifier: Modifier = Modifier) {
    val shown = remember(handles, me) {
        // Her own initial first, then the others in join order.
        (listOf(me) + handles.filter { it != me }).take(5)
    }
    val tints = listOf(
        PregaTheme.colors.sageSoft to PregaTheme.colors.sage,
        PregaTheme.colors.terracottaSoft to PregaTheme.colors.terracotta,
        PregaTheme.colors.lavenderSoft to PregaTheme.colors.lavender,
        PregaTheme.colors.goldSoft to PregaTheme.colors.gold,
    )
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        // Overlap by hand: offset() moves pixels, not layout, so the box is
        // sized to the overlapped width or the gap after it would be wrong.
        val step = 20
        Box(Modifier.width((28 + step * (shown.size - 1)).dp).height(28.dp)) {
            shown.forEachIndexed { i, h ->
                val (bg, fg) = tints[i % tints.size]
                Box(
                    Modifier
                        .offset(x = (step * i).dp)
                        .size(28.dp)
                        .clip(RoundedCornerShape(50, 50, 50, 15))
                        .background(bg),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        h.trim().take(1).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = fg,
                    )
                }
            }
        }
        Spacer(Modifier.width(Space.md))
        val count = handles.size.coerceAtLeast(1)
        Text(
            when (count) {
                1 -> "Just you so far"
                2 -> "You and one other mother"
                else -> "$count mothers"
            },
            style = MaterialTheme.typography.bodySmall,
            color = PregaTheme.colors.inkMuted,
        )
    }
}

/** The invite gets its own tint — it is the only chip that isn't a filter. */
@Composable
private fun InviteChip(onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(PregaTheme.colors.terracottaSoft)
            .clickable(onClick = onClick)
            .padding(horizontal = Space.md, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "+ Invite",
            style = MaterialTheme.typography.labelMedium,
            color = PregaTheme.colors.terracotta,
        )
    }
}

// ─── Chat ──────────────────────────────────────────────────────────────────

/** A row in the chat list: a message, or the day it belongs to. */
private sealed interface ChatRow {
    data class Msg(val m: ClubMessage) : ChatRow
    data class Day(val label: String, val key: String) : ChatRow
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatSection(
    profile: CommunityProfile,
    memberCount: Int,
    onInvite: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    // The flows MUST be remembered. collectAsState keys its collection on
    // the flow INSTANCE, and messages()/myBlockedIds() build a fresh
    // callbackFlow on every call — so calling them inline meant every
    // recomposition (each keystroke in the input box, most of all) tore
    // down the Firestore listener, restarted collection at
    // initial = emptyList(), and repainted the club empty before the
    // snapshot came back. That is the reported flicker, and it also
    // churned a listener per keystroke against the free Firestore quota.
    val messagesFlow = remember(profile.dueMonth) {
        CommunityRepository.messages(profile.dueMonth)
    }
    val blockedFlow = remember(profile.uid) { CommunityRepository.myBlockedIds() }
    val messages by messagesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val blocked by blockedFlow.collectAsStateWithLifecycle(initialValue = emptySet())
    val visible = remember(messages, blocked) { messages.filter { it.authorId !in blocked } }
    // Newest-first list + reverseLayout: a day label must sit AFTER the
    // oldest message of its day in list order so it renders ABOVE it.
    val rows = remember(visible) {
        val out = ArrayList<ChatRow>(visible.size + 8)
        visible.forEachIndexed { i, m ->
            out += ChatRow.Msg(m)
            val day = dayKey(m.createdAt)
            val next = visible.getOrNull(i + 1)
            if (next == null || dayKey(next.createdAt) != day) {
                out += ChatRow.Day(label = dayLabel(m.createdAt), key = "day-$day")
            }
        }
        out
    }
    var input by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        if (visible.isEmpty()) {
            if (memberCount < 3) {
                NewClubEmpty(
                    month = prettyMonth(profile.dueMonth),
                    onInvite = onInvite,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Box(Modifier.weight(1f)) { EmptyState("It's quiet in here — say the first hello 🌸") }
            }
        } else {
            LazyColumn(
                Modifier.weight(1f),
                reverseLayout = true,
                contentPadding = PaddingValues(horizontal = Space.gutter, vertical = Space.md),
                verticalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                items(
                    rows,
                    key = { r -> when (r) { is ChatRow.Msg -> r.m.id; is ChatRow.Day -> r.key } },
                ) { r ->
                    when (r) {
                        is ChatRow.Day -> DayDivider(r.label)
                        is ChatRow.Msg -> CommunityBubble(
                            handle = r.m.handle,
                            time = prettyClock(r.m.createdAt),
                            body = r.m.body,
                            flagged = r.m.flagged,
                            mine = r.m.authorId == profile.uid,
                            onReport = {
                                scope.launch {
                                    CommunityRepository.report(profile.dueMonth, "message", r.m.id, "reported from app")
                                }
                            },
                            onBlock = { scope.launch { CommunityRepository.block(r.m.authorId) } },
                        )
                    }
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
                placeholder = {
                    Text(
                        if (visible.isEmpty()) "Say hello to ${prettyMonth(profile.dueMonth).substringBefore(' ')}…"
                        else "Message ${prettyMonth(profile.dueMonth).substringBefore(' ')}…"
                    )
                },
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

/**
 * The state every new member actually lands in. Cohort-only discovery
 * means the first mother due in a month opens an empty room; this makes
 * the emptiness the point and the invite the answer.
 */
@Composable
private fun NewClubEmpty(month: String, onInvite: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Space.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("\uD83C\uDF38", fontSize = 40.sp)
        Spacer(Modifier.height(Space.lg))
        Text(
            "Every club starts with one mother",
            style = MaterialTheme.typography.titleLarge,
            color = PregaTheme.colors.ink,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(Space.sm))
        Text(
            "Mothers due in $month will land here as they join. The fastest way to " +
                "fill it is the ones you already know — a sister, a friend, someone " +
                "from your clinic.",
            style = MaterialTheme.typography.bodyMedium,
            color = PregaTheme.colors.inkMuted,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(Space.xl))
        PregaButton(
            text = "Invite someone due in ${month.substringBefore(' ')}",
            onClick = onInvite,
        )
        Spacer(Modifier.height(Space.md))
        Text(
            "Or write the first message below",
            style = MaterialTheme.typography.bodySmall,
            color = PregaTheme.colors.inkFaint,
        )
    }
}

@Composable
private fun DayDivider(label: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = Space.xs), contentAlignment = Alignment.Center) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = PregaTheme.colors.inkFaint,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(PregaTheme.colors.recessed)
                .padding(horizontal = Space.md, vertical = 3.dp),
        )
    }
}

// ─── Posts ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PostsSection(profile: CommunityProfile) {
    val scope = rememberCoroutineScope()
    val postsFlow = remember(profile.dueMonth) { CommunityRepository.posts(profile.dueMonth) }
    val blockedFlow = remember(profile.uid) { CommunityRepository.myBlockedIds() }
    val posts by postsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val blocked by blockedFlow.collectAsStateWithLifecycle(initialValue = emptySet())
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
    val circlesFlow = remember(profile.dueMonth) { CommunityRepository.circles(profile.dueMonth) }
    val circles by circlesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
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
                            AppStats.log(StatEvent.CircleJoinTapped)
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
    time: String? = null,
) {
    var menu by remember { mutableStateOf(false) }
    // Hers on the right in a rose whisper, everyone else on the left on
    // card — the shape any messaging app has taught her thumb. Rose isn't
    // in the Material scheme (olive is primary, sage secondary), so it is
    // taken from the brand palette and dimmed for dark mode by hand.
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val mineBg = if (dark) BloomRoseDeep.copy(alpha = 0.22f) else BloomRoseWhisper
    val mineBorder = if (dark) BloomRoseDeep.copy(alpha = 0.45f) else BloomRoseSoft
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
    ) {
        Text(
            if (time != null) "${if (mine) "You" else handle} \u00B7 $time" else if (mine) "You" else handle,
            style = MaterialTheme.typography.labelSmall,
            color = PregaTheme.colors.inkFaint,
            modifier = Modifier.padding(horizontal = Space.sm, vertical = 2.dp),
        )
        Column(
            Modifier
                .fillMaxWidth(0.84f)
                .clip(
                    if (mine) RoundedCornerShape(18.dp, 18.dp, 5.dp, 18.dp)
                    else RoundedCornerShape(18.dp, 18.dp, 18.dp, 5.dp)
                )
                .background(if (mine) mineBg else PregaTheme.colors.cardSurface)
                .then(
                    if (mine) Modifier.border(
                        1.dp, mineBorder,
                        RoundedCornerShape(18.dp, 18.dp, 5.dp, 18.dp),
                    ) else Modifier.border(
                        1.dp, PregaTheme.colors.hairline,
                        RoundedCornerShape(18.dp, 18.dp, 18.dp, 5.dp),
                    )
                )
                .combinedClickable(onClick = { }, onLongClick = { menu = true })
                .padding(horizontal = Space.md, vertical = 10.dp),
        ) {
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

private fun prettyClock(millis: Long): String =
    if (millis == 0L) "" else SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(millis))

private fun dayKey(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(millis))

private fun dayLabel(millis: Long): String {
    val today = dayKey(System.currentTimeMillis())
    val yesterday = dayKey(System.currentTimeMillis() - 86_400_000L)
    return when (dayKey(millis)) {
        today -> "Today"
        yesterday -> "Yesterday"
        else -> SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(millis))
    }
}
