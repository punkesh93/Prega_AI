package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ai.PregaSpeech
import com.example.ui.theme.PregaTheme
import com.example.ui.theme.Space
import kotlinx.coroutines.launch

/**
 * "Listen" — a quiet read-it-to-me toggle for any AI-written text (coach
 * replies, the daily insight, the affirmation). Deliberately understated:
 * faint tint, no background, it disappears into the layout until wanted.
 *
 * Tap to speak, tap again to stop; starting one utterance stops any other
 * (see [PregaSpeech]). Only the chip whose own text is loading or speaking
 * changes label, keyed by the text's hash, so a screen of chips never
 * animates in unison. If the voice service is unreachable the chip simply
 * returns to "Listen" — reading always works, so no error surfaces.
 */
@Composable
fun ListenChip(
    text: String,
    tint: Color = PregaTheme.colors.inkFaint,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val speech by PregaSpeech.state.collectAsStateWithLifecycle()
    val key = PregaSpeech.keyOf(text)
    val isLoading = (speech as? PregaSpeech.State.Loading)?.key == key
    val isSpeaking = (speech as? PregaSpeech.State.Speaking)?.key == key

    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .clickable { scope.launch { PregaSpeech.toggle(context, text) } }
            .padding(horizontal = Space.sm, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (isSpeaking) Icons.Rounded.StopCircle else Icons.Rounded.VolumeUp,
            contentDescription = if (isSpeaking) "Stop reading" else "Read aloud",
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = when {
                isLoading -> "Getting voice…"
                isSpeaking -> "Stop"
                else -> "Listen"
            },
            style = MaterialTheme.typography.labelSmall,
            color = tint,
        )
    }
}
