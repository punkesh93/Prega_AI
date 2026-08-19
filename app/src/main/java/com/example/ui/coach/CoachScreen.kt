package com.example.ui.coach

import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.*
import com.example.ui.theme.Motion
import com.example.ui.theme.PregaTheme
import com.example.ui.theme.Space
import com.example.viewmodel.ChatMessage

/**
 * Prega AI — Coach.
 *
 * Two things distinguish this from a generic chat screen:
 *
 * 1. The red-flag banner fires locally, the instant she types, before anything
 *    is sent. It doesn't wait on a network round trip and it can't be softened
 *    by a model. See [containsRedFlag].
 *
 * 2. Suggested prompts are week-specific, because a blank chat box is a hard
 *    thing to face when you're anxious and don't know what to ask.
 */
@Composable
fun CoachScreen(
    messages: List<ChatMessage>,
    loading: Boolean,
    week: Int,
    chatLanguage: String,
    dynamicSuggestions: List<String>,
    onLanguageChange: (String) -> Unit,
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val focus = LocalFocusManager.current

    // Fires on what she's typing, before she even sends it.
    val showRedFlag = remember(input) { containsRedFlag(input) }

    LaunchedEffect(messages.size, loading) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex + if (loading) 1 else 0)
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Language: EN / हिंदी / Hinglish — one tap, remembered, applies to
        // the coach's replies and the suggested chips.
        LanguageRow(chatLanguage, onLanguageChange)

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                start = Space.gutter,
                end = Space.gutter,
                top = Space.lg,
                bottom = Space.lg,
            ),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            items(messages, key = { it.id }) { message ->
                MessageBubble(message)
            }

            if (loading) {
                item { ThinkingBubble() }
            }

            // Only offered at the start — suggestions below a real conversation
            // would be noise.
            if (messages.size <= 1 && !loading) {
                item {
                    Spacer(Modifier.height(Space.md))
                    SuggestedQuestions(week, dynamicSuggestions) { onSend(it) }
                }
            }
        }

        AnimatedVisibility(visible = showRedFlag, enter = Motion.popIn, exit = Motion.popOut) {
            Box(Modifier.padding(horizontal = Space.gutter, vertical = Space.sm)) {
                SafetyNotice(text = RED_FLAG_MESSAGE)
            }
        }

        Composer(
            value = input,
            onValueChange = { input = it },
            enabled = !loading,
            chatLanguage = chatLanguage,
            onSend = {
                val text = input.trim()
                if (text.isNotEmpty()) {
                    onSend(text)
                    input = ""
                    focus.clearFocus()
                }
            },
        )
    }
}

@Composable
private fun LanguageRow(selected: String, onSelect: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.gutter, vertical = Space.sm),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        listOf("English" to "English", "Hindi" to "हिंदी", "Hinglish" to "Hinglish")
            .forEach { (value, label) ->
                PregaChip(
                    label = label,
                    selected = selected == value,
                    onClick = { onSelect(value) },
                )
            }
    }
}

// ─── Messages ──────────────────────────────────────────────────────────────

@Composable
private fun MessageBubble(message: ChatMessage) {
    val isUser = message.isUser

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        if (!isUser) {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(PregaTheme.colors.lavenderSoft),
                contentAlignment = Alignment.Center,
            ) { Text("🌸", fontSize = 14.sp) }
            Spacer(Modifier.width(Space.sm))
        }

        Column(horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
            Box(
                Modifier
                    .widthIn(max = 300.dp)
                    .clip(
                        // Asymmetric corners so the bubble points at its speaker.
                        RoundedCornerShape(
                            topStart = 20.dp,
                            topEnd = 20.dp,
                            bottomStart = if (isUser) 20.dp else 6.dp,
                            bottomEnd = if (isUser) 6.dp else 20.dp,
                        )
                    )
                    .background(
                        if (isUser) MaterialTheme.colorScheme.primary
                        else PregaTheme.colors.cardSurface
                    )
                    .padding(horizontal = Space.lg, vertical = Space.md),
            ) {
                Text(
                    message.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isUser) Color.White else PregaTheme.colors.ink,
                )
            }
            // "Read it to me" — coach replies only, tucked under the bubble.
            if (!isUser) {
                ListenChip(message.text)
            }
        }
    }
}

/** Three dots breathing in sequence — calmer than a spinner. */
@Composable
private fun ThinkingBubble() {
    val transition = rememberInfiniteTransition(label = "thinking")

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(PregaTheme.colors.lavenderSoft),
            contentAlignment = Alignment.Center,
        ) { Text("🌸", fontSize = 14.sp) }

        Spacer(Modifier.width(Space.sm))

        Row(
            Modifier
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 6.dp))
                .background(PregaTheme.colors.cardSurface)
                .padding(horizontal = Space.lg, vertical = Space.lg),
            horizontalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            repeat(3) { i ->
                val alpha by transition.animateFloat(
                    initialValue = 0.25f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(700, delayMillis = i * 160, easing = Motion.EaseBreath),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "dot$i",
                )
                Box(
                    Modifier
                        .size(7.dp)
                        .alpha(alpha)
                        .clip(CircleShape)
                        .background(PregaTheme.colors.inkMuted)
                )
            }
        }
    }
}

// ─── Suggestions ───────────────────────────────────────────────────────────

@Composable
private fun SuggestedQuestions(
    week: Int,
    dynamic: List<String>,
    onPick: (String) -> Unit,
) {
    // AI-written, week+language chips when available; static English set
    // otherwise (offline, rate-limited, degenerate output — all land here).
    val suggestions = if (dynamic.isNotEmpty()) dynamic
    else remember(week) { suggestionsFor(week) }

    Column {
        Overline("Not sure where to start")
        Spacer(Modifier.height(Space.md))
        suggestions.forEach { question ->
            PregaCard(
                onClick = { onPick(question) },
                contentPadding = PaddingValues(Space.md),
                modifier = Modifier.padding(bottom = Space.sm),
            ) {
                Text(
                    question,
                    style = MaterialTheme.typography.bodyMedium,
                    color = PregaTheme.colors.ink,
                )
            }
        }
    }
}

/**
 * Week-aware openers. What's on someone's mind at week 8 (nausea, scans) is not
 * what's on it at week 36 (labour, hospital bags), so generic suggestions would
 * be wasted space.
 */
internal fun suggestionsFor(week: Int): List<String> = when {
    week <= 13 -> listOf(
        "What can I do about the nausea?",
        "Which foods should I avoid right now?",
        "I'm exhausted all the time — is that normal?",
    )
    week <= 27 -> listOf(
        "What should I expect at my next scan?",
        "How can I sleep more comfortably?",
        "What exercise is safe for me now?",
    )
    week <= 36 -> listOf(
        "What should go in my hospital bag?",
        "How do I know if it's Braxton Hicks?",
        "What helps with back pain this late?",
    )
    else -> listOf(
        "What are the early signs of labour?",
        "When should I head to the hospital?",
        "What actually happens if I go past my due date?",
    )
}

// ─── Composer ──────────────────────────────────────────────────────────────

@Composable
private fun Composer(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    chatLanguage: String,
    onSend: () -> Unit,
) {
    // Voice-to-text via Android's built-in recognizer — no new permission
    // (the system speech activity owns the microphone), no SDK, and it
    // follows her chat language: Hindi -> hi-IN, English/Hinglish -> en-IN
    // (Indian English recognition handles Hinglish code-switching best).
    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val heard = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            .orEmpty()
        if (heard.isNotBlank()) {
            onValueChange(if (value.isBlank()) heard else "$value $heard")
        }
    }
    val speechAvailable = remember {
        // Devices without the Google speech activity would crash on launch.
        true
    }

    fun launchSpeech() {
        val locale = if (chatLanguage == "Hindi") "hi-IN" else "en-IN"
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Ask Prega…")
        }
        try {
            speechLauncher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            // No recognizer on this device — typing still works; stay silent.
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
        // Mic — speak instead of type.
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(PregaTheme.colors.lavenderSoft)
                .clickable(enabled = enabled && speechAvailable) { launchSpeech() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Mic,
                contentDescription = "Speak your question",
                tint = PregaTheme.colors.ink,
                modifier = Modifier.size(22.dp),
            )
        }

        Spacer(Modifier.width(Space.sm))

        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Ask me anything…") },
            enabled = enabled,
            maxLines = 4,
            shape = RoundedCornerShape(24.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = PregaTheme.colors.hairline,
            ),
        )

        Spacer(Modifier.width(Space.sm))

        val canSend = enabled && value.isNotBlank()
        val sendInteraction = remember { MutableInteractionSource() }
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    if (canSend) MaterialTheme.colorScheme.primary
                    else PregaTheme.colors.hairline
                )
                .clickable(
                    interactionSource = sendInteraction,
                    indication = null,
                    enabled = canSend,
                    onClick = onSend,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text("↑", fontSize = 20.sp, color = if (canSend) Color.White else PregaTheme.colors.inkFaint)
        }
    }
}
