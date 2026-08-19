package com.example.ai

import com.example.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * OpenRouter-backed AI layer for Prega AI.
 *
 * OpenRouter exposes an OpenAI-compatible chat-completions endpoint, which lets
 * us route every AI feature in the app through a single client and swap the
 * underlying model per-task without touching call sites (see [PregaModel]).
 *
 * ⚠️ SECURITY — READ BEFORE SHIPPING
 * The key is currently read from BuildConfig, i.e. it is embedded in the APK.
 * That is fine for development but **must not ship to production**: an APK can
 * be trivially unzipped and the key extracted, letting anyone spend your credit.
 * Before release, stand up a thin backend that holds the key and forwards
 * requests, then point [BASE_URL] at it. The rest of this file stays unchanged.
 */

// ─── Wire models (OpenAI-compatible) ───────────────────────────────────────

@JsonClass(generateAdapter = true)
data class AiMessage(
    val role: String,   // "system" | "user" | "assistant"
    val content: String,
)

@JsonClass(generateAdapter = true)
data class ChatRequest(
    val model: String,
    val messages: List<AiMessage>,
    val temperature: Double = 0.8,
    @Json(name = "max_tokens") val maxTokens: Int = 1200,
)

@JsonClass(generateAdapter = true)
data class ChatChoice(val message: AiMessage?)

@JsonClass(generateAdapter = true)
data class ChatResponse(val choices: List<ChatChoice>?)

interface OpenRouterApi {
    @POST("api/v1/chat/completions")
    suspend fun chat(@Body request: ChatRequest): ChatResponse
}

// ─── Model routing ─────────────────────────────────────────────────────────

/**
 * Which model handles which job. Cheap-and-fast for short generative copy,
 * stronger models where the answer actually matters to her.
 *
 * Verified against openrouter.ai/models on 6 Aug 2026. Model slugs move fast —
 * re-check before a release and update in one place here.
 */
/**
 * Cost note from real testing: credits were burning fast, so both tiers now
 * point at a free chat model. (The specifically requested
 * nvidia/nemotron-3-embed-1b cannot be used: it is an EMBEDDING model — it
 * converts text to vectors for search and cannot generate a single word of
 * reply. Wiring it in would silently break the coach, insights, captions,
 * everything.) Llama 3.3 70B free is the strongest no-cost chat option on
 * OpenRouter; quality for the coach will be a step below Claude — revert
 * Conversational to "anthropic/claude-sonnet-4.5" the moment revenue
 * justifies it, or when the Supabase proxy adds per-user rate limits.
 * Free-tier models are also rate-limited by OpenRouter, so occasional
 * "try again in a moment" fallbacks are expected under load.
 */
enum class PregaModel(val slug: String) {
    /** Coach conversations, meal plans — quality matters most. */
    Conversational("google/gemma-4-26b-a4b-it:free"),
    /** Notification copy, micro-content, quest text — high volume, short. */
    Quick("google/gemma-4-26b-a4b-it:free"),
}

// ─── Result type ───────────────────────────────────────────────────────────

/**
 * Explicit result so the UI can distinguish "offline" from "not configured"
 * from "the model said something", instead of parsing error strings out of a
 * success value the way the old Gemini client did.
 */
sealed interface AiResult {
    data class Success(val text: String) : AiResult
    /** No API key configured — the app falls back to curated offline content. */
    data object NotConfigured : AiResult
    data class Failure(val reason: String, val retryable: Boolean) : AiResult
}

// ─── Client ────────────────────────────────────────────────────────────────

object OpenRouterClient {

    private const val BASE_URL = "https://openrouter.ai/"

    private val apiKey: String
        get() = runCatching { BuildConfig.OPENROUTER_API_KEY }.getOrDefault("")

    val isConfigured: Boolean
        get() = apiKey.isNotBlank() && !apiKey.startsWith("MY_") && apiKey != "YOUR_KEY_HERE"

    private val authInterceptor = Interceptor { chain ->
        val request = chain.request().newBuilder()
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            // OpenRouter uses these for attribution on their dashboard/leaderboards.
            .addHeader("HTTP-Referer", "https://prega.ai")
            .addHeader("X-Title", "Prega AI")
            .build()
        chain.proceed(request)
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    private val api: OpenRouterApi = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(OpenRouterApi::class.java)

    /**
     * Single entry point for every AI call in the app.
     *
     * @param history prior turns, oldest first. Pass an empty list for one-shot
     *   generations. The coach passes real history so the model can follow up
     *   properly instead of treating every question as the first.
     */
    suspend fun complete(
        systemPrompt: String,
        userPrompt: String,
        history: List<AiMessage> = emptyList(),
        model: PregaModel = PregaModel.Conversational,
        temperature: Double = 0.8,
        maxTokens: Int = 1200,
    ): AiResult = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext AiResult.NotConfigured

        val messages = buildList {
            add(AiMessage("system", systemPrompt))
            addAll(history.takeLast(MAX_HISTORY_TURNS))
            add(AiMessage("user", userPrompt))
        }

        try {
            val response = api.chat(
                ChatRequest(
                    model = model.slug,
                    messages = messages,
                    temperature = temperature,
                    maxTokens = maxTokens,
                )
            )
            val text = response.choices?.firstOrNull()?.message?.content?.trim()
            if (text.isNullOrBlank()) {
                AiResult.Failure("The model returned an empty response.", retryable = true)
            } else if (text.isDegenerate()) {
                // Model got stuck in a repetition loop ("practices iter
                // practices iter..." — seen in the wild on the daily insight
                // card). Treat like an empty response: callers with curated
                // fallbacks use them, the coach shows a retryable error. Never
                // show looped garbage to a pregnant woman asking about her baby.
                AiResult.Failure("The model returned a garbled response.", retryable = true)
            } else {
                AiResult.Success(text)
            }
        } catch (e: HttpException) {
            AiResult.Failure(describeHttpError(e.code()), retryable = e.code() in RETRYABLE_CODES)
        } catch (e: IOException) {
            AiResult.Failure("No internet connection.", retryable = true)
        } catch (e: Exception) {
            AiResult.Failure(e.localizedMessage ?: "Something went wrong.", retryable = true)
        }
    }

    /** Convenience for fire-and-forget generation where fallback copy exists. */
    suspend fun completeOrNull(
        systemPrompt: String,
        userPrompt: String,
        model: PregaModel = PregaModel.Quick,
        temperature: Double = 0.9,
        maxTokens: Int = 300,
    ): String? = (
        complete(
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
            model = model,
            temperature = temperature,
            maxTokens = maxTokens,
        ) as? AiResult.Success
        )?.text

    private fun describeHttpError(code: Int): String = when (code) {
        401, 403 -> "Your AI key was rejected. Check it in settings."
        402 -> "The AI account is out of credit."
        429 -> "Too many requests just now — give it a moment."
        in 500..599 -> "The AI service is having trouble. Try again shortly."
        else -> "Request failed (error $code)."
    }

    private val RETRYABLE_CODES = setOf(408, 429, 500, 502, 503, 504)

    /** Keeps token spend bounded on long coach conversations. */
    private const val MAX_HISTORY_TURNS = 12
}

/**
 * Defensive markdown strip for display.
 *
 * The system prompt forbids markdown, but "forbids" is a request to a
 * language model, not a guarantee — and a single literal ** on screen looks
 * broken. This runs on every AI string at the point of receipt, so the UI
 * never needs to know markdown existed. Conservative on purpose: it unwraps
 * emphasis and headers and normalises bullets, and touches nothing else.
 */
fun String.stripMarkdown(): String {
    var s = this
    // **bold** / __bold__ -> bare
    s = s.replace(Regex("""\*\*(.+?)\*\*"""), "$1")
    s = s.replace(Regex("""__(.+?)__"""), "$1")
    // *italic* / _italic_ (single, non-greedy, not part of a word like snake_case)
    s = s.replace(Regex("""(?<!\w)\*(?!\s)(.+?)(?<!\s)\*(?!\w)"""), "$1")
    s = s.replace(Regex("""(?<!\w)_(?!\s)(.+?)(?<!\s)_(?!\w)"""), "$1")
    // `code`
    s = s.replace(Regex("""`([^`]*)`"""), "$1")
    // # Headers at line start -> plain line
    s = s.replace(Regex("""(?m)^#{1,6}\s*"""), "")
    // * or + bullets at line start -> "- "
    s = s.replace(Regex("""(?m)^\s*[*+]\s+"""), "- ")
    return s.trim()
}

/**
 * Detects model degeneration — the repetition-loop failure mode where a
 * completion collapses into the same few words over and over ("practices
 * iter practices iter practices iter..."). Observed in production on the
 * daily insight card. Two complementary heuristics, tuned to never flag
 * legitimate prose (which naturally repeats words like "your", "baby",
 * "week" — but not the SAME PHRASE back to back many times):
 *
 * 1. Consecutive phrase loops: any 1–4 word phrase immediately repeated
 *    5+ times in a row. Healthy writing essentially never does this;
 *    looped output almost always does.
 * 2. Vocabulary collapse: in a long response (30+ words), fewer than 25%
 *    distinct words. Normal English sits well above 40% even in
 *    repetitive instructional text.
 * 3. Model-internal special tokens (<pad>, <unk>, <|im_end|>, [PAD]...)
 *    leaking into the text 3+ times. Seen in production: an affirmation
 *    rendered as "<pad><pad><pad>..." — these tokens never belong in
 *    prose for a mother.
 * 4. Whitespace-free loops: any 3–24 char chunk repeated 4+ times
 *    back-to-back. Catches the same failure as (1) when the loop has no
 *    spaces, which made it invisible to word-level checks.
 */
private val SPECIAL_TOKEN_REGEX = Regex(
    """<\|[^|>]{1,30}\|>|</?(?:pad|unk|eos|bos|mask)>|\[(?:PAD|UNK|CLS|SEP|MASK)]""",
    RegexOption.IGNORE_CASE,
)

private val CHUNK_LOOP_REGEX = Regex("""(\S{3,24})\1{3,}""")

fun String.isDegenerate(): Boolean {
    // 3. Special-token leakage — three or more is unambiguous garbage
    if (SPECIAL_TOKEN_REGEX.findAll(this).count() >= 3) return true

    // 4. Repeated chunk with no whitespace (e.g. "<pad><pad><pad>...")
    if (CHUNK_LOOP_REGEX.containsMatchIn(this)) return true

    val words = trim().lowercase().split(Regex("""\s+""")).filter { it.isNotBlank() }
    if (words.size < 10) return false

    // 1. Consecutive repeated n-gram (phrase of length 1..4, looped 5+ times)
    for (n in 1..4) {
        var run = 1
        var i = n
        while (i + n <= words.size) {
            val same = (0 until n).all { words[i + it] == words[i - n + it] }
            if (same) {
                run++
                if (run >= 5) return true
                i += n
            } else {
                run = 1
                i += 1
            }
        }
    }

    // 2. Vocabulary collapse on longer texts
    if (words.size >= 30 && words.distinct().size.toDouble() / words.size < 0.25) return true

    return false
}
