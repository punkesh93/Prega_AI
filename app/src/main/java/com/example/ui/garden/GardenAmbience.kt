package com.example.ui.garden

import android.content.Context
import android.media.MediaPlayer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.MusicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.ui.theme.PregaTheme
import com.example.ui.theme.Space

/**
 * Garden ambience — soft wind and occasional birdsong while she's IN the
 * garden, silent everywhere else.
 *
 * The audio is synthesized, not recorded: wind is low-passed brown noise
 * with two slow swell cycles (seamless 19s loop via crossfade), birds are
 * sparse tone-glide chirps in the natural 2–3kHz songbird range with real
 * quiet between phrases (16s loop). Synthesis keeps the files tiny (~70KB
 * total), royalty-free, and exactly as calm as intended — nothing startles.
 * Waveform + spectrogram were visually verified before shipping.
 *
 * Behavior rules:
 *  - Plays only while the garden is on screen; leaving the tab stops it
 *    (players live in a DisposableEffect keyed to the composition).
 *  - Volumes stay low (wind 0.35, birds 0.5 of an already-quiet master) —
 *    ambience, not soundtrack.
 *  - The toggle chip persists her choice in prefs; default is ON the first
 *    visit because the feature is invisible otherwise, but one tap mutes
 *    it forever.
 *  - Every player error is swallowed: sound is a garnish, never a crash.
 */
private const val PREFS = "prega_garden"
private const val KEY_SOUND = "sound_on"

@Composable
fun GardenAmbience() {
    val context = LocalContext.current
    var soundOn by remember {
        mutableStateOf(
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_SOUND, true)
        )
    }

    if (soundOn) {
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val players = mutableListOf<MediaPlayer>()
            fun make(res: Int, vol: Float) {
                try {
                    MediaPlayer.create(context, res)?.let {
                        it.isLooping = true
                        it.setVolume(vol, vol)
                        it.start()
                        players += it
                    }
                } catch (_: Exception) {
                    // Ambience must never take the garden down with it.
                }
            }
            make(R.raw.garden_wind, 0.35f)
            make(R.raw.garden_birds, 0.5f)
            // People strolling somewhere on the path — depth, not presence.
            make(R.raw.garden_steps, 0.28f)

            // Minimizing the app must silence the garden: composition alone
            // doesn't do it (a backgrounded activity keeps its composition),
            // so pause/resume follows the real lifecycle.
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE ->
                        players.forEach { runCatching { it.pause() } }
                    Lifecycle.Event.ON_RESUME ->
                        players.forEach { runCatching { it.start() } }
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)

            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                players.forEach { p ->
                    try {
                        p.stop(); p.release()
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .clickable {
                soundOn = !soundOn
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit { putBoolean(KEY_SOUND, soundOn) }
            }
            .padding(horizontal = Space.sm, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (soundOn) Icons.Rounded.MusicNote else Icons.Rounded.MusicOff,
            contentDescription = if (soundOn) "Turn garden sounds off" else "Turn garden sounds on",
            tint = PregaTheme.colors.inkFaint,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            if (soundOn) "Birds & breeze on" else "Birds & breeze off",
            style = MaterialTheme.typography.labelSmall,
            color = PregaTheme.colors.inkFaint,
        )
    }
}
