package com.example.ai

import android.content.Context
import android.media.MediaPlayer
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * "Read it to me" — text-to-speech for coach replies, insights, and
 * affirmations, so a tired mother can listen instead of reading.
 *
 * Uses deepgram/flux-tts (free tier) through the same OpenRouter account as
 * the chat models — no new key, no new backend. Free-tier caveat, accepted
 * knowingly: free OpenRouter variants are rate-limited and occasionally
 * flaky, so every failure here degrades silently back to reading — a
 * missing voice is a shrug, a crash or an error dialog would be a real
 * cost. Same philosophy as AppStats: optional extras never break the app.
 *
 * One utterance at a time by design (tapping a second speaker stops the
 * first): overlapping voices would be chaos, and a calm app interrupts
 * rather than layers.
 */
object PregaSpeech {

    /** What the speaker buttons render against. */
    sealed interface State {
        data object Idle : State
        /** [key] identifies WHICH text is loading/speaking, so only its button animates. */
        data class Loading(val key: Int) : State
        data class Speaking(val key: Int) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    /** Stable key for a piece of text; UI uses it to know "is MY text playing". */
    fun keyOf(text: String): Int = text.hashCode()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private var player: MediaPlayer? = null

    /** Tap-to-toggle: speaking this text already? stop. Otherwise speak it. */
    suspend fun toggle(context: Context, text: String) {
        val key = keyOf(text)
        val current = _state.value
        if ((current is State.Speaking && current.key == key) ||
            (current is State.Loading && current.key == key)
        ) {
            stop()
            return
        }
        speak(context, text)
    }

    private suspend fun speak(context: Context, text: String) {
        if (!OpenRouterClient.isConfigured) return
        stop()
        val key = keyOf(text)
        _state.value = State.Loading(key)

        val file = withContext(Dispatchers.IO) { fetchAudio(context, text) }
        if (file == null) {
            // Silent degradation: she can still read it.
            _state.value = State.Idle
            return
        }

        // Player runs on its own; we only track state for the UI.
        try {
            player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener {
                    it.release()
                    if (player === it) player = null
                    _state.value = State.Idle
                }
                setOnErrorListener { mp, _, _ ->
                    mp.release()
                    if (player === mp) player = null
                    _state.value = State.Idle
                    true
                }
                prepare()
                start()
            }
            _state.value = State.Speaking(key)
        } catch (_: Exception) {
            _state.value = State.Idle
        }
    }

    fun stop() {
        try {
            player?.stop()
            player?.release()
        } catch (_: Exception) {
        }
        player = null
        _state.value = State.Idle
    }

    private fun fetchAudio(context: Context, text: String): File? = try {
        // Keep utterances snappy and inside free-tier limits. Sentences past
        // the cap are dropped whole rather than cut mid-word.
        val trimmed = text.stripMarkdown().let { s ->
            if (s.length <= MAX_CHARS) s
            else s.take(MAX_CHARS).substringBeforeLast('.').plus(".")
        }
        val body = JSONObject()
            .put("model", "deepgram/flux-tts:free")
            .put("input", trimmed)
            .put("voice", "flux-meena-en")
            .put("response_format", "mp3")
            .toString()
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("https://openrouter.ai/api/v1/audio/speech")
            .addHeader("Authorization", "Bearer " + BuildConfig.OPENROUTER_API_KEY)
            .addHeader("HTTP-Referer", "https://prega.ai")
            .addHeader("X-Title", "Prega AI")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val bytes = response.body?.bytes() ?: return null
            if (bytes.isEmpty()) return null
            val dir = File(context.cacheDir, "tts").apply { mkdirs() }
            // One reusable slot — old speech is worthless the moment new
            // speech is requested, and the cache never grows.
            File(dir, "speech.mp3").apply { writeBytes(bytes) }
        }
    } catch (_: Exception) {
        null
    }

    private const val MAX_CHARS = 700
}
