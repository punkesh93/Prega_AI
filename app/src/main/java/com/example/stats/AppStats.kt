package com.example.stats

import android.content.Context
import androidx.core.content.edit
import com.example.BuildConfig
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Prega AI — anonymous aggregate stats, Airtable-backed.
 *
 * PRIVACY CONTRACT (do not widen without re-reading the privacy policy in
 * docs/index.html — it promises this explicitly):
 *   - Only [StatEvent] names below are ever sent. No journal text, no notes,
 *     no mood value, no weight, no symptoms, no name, no photos — nothing
 *     she typed or logged, ever.
 *   - [installId] is a random UUID generated once on-device. It is not her
 *     Google account, not an ad ID, not tied to anything else. It exists
 *     only so "50 people did X once" can be told apart from "1 person did
 *     X 50 times" — pure aggregate counting, not a profile.
 *   - This is fire-and-forget: failures are swallowed, never surfaced to
 *     her, never retried, never block the feature that triggered them.
 *
 * ⚠️ SECURITY — same caveat as OpenRouterService: AIRTABLE_TOKEN is read
 * from BuildConfig, i.e. embedded in the APK. It is scoped (by convention,
 * see .env.example) to data.records:write on ONE base only, so a leaked
 * token can at worst pollute the stats table — never anyone's app data,
 * which never leaves the device in the first place. Still, prefer a proxy
 * before a real production release, same as the OpenRouter key.
 */
enum class StatEvent(val wireName: String) {
    AppOpen("app_open"),
    CoachQuestionAsked("coach_question_asked"),
    JournalSaved("journal_saved"),
    KickSessionCompleted("kick_session_completed"),
    PremiumPurchased("premium_purchased"),
    GardenShared("garden_shared"),
}

@JsonClass(generateAdapter = true)
internal data class StatFields(
    val event: String,
    val platform: String,
    val app_version: String,
    val install_id: String,
)

@JsonClass(generateAdapter = true)
internal data class StatRecord(val fields: StatFields)

@JsonClass(generateAdapter = true)
internal data class StatRecordBatch(val records: List<StatRecord>)

object AppStats {
    private const val PREFS = "prega_stats"
    private const val KEY_INSTALL_ID = "install_id"

    // Own scope: call sites (Application.onCreate, share buttons, etc.) are
    // not always inside a ViewModel with its own scope, and this must never
    // block or get cancelled by whatever screen happened to trigger it.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .writeTimeout(4, TimeUnit.SECONDS)
            .build()
    }
    private val moshi by lazy { Moshi.Builder().build() }
    private val jsonMediaType = "application/json".toMediaType()

    // Cached once from MainActivity.onCreate via init(). PregnancyViewModel
    // is a plain ViewModel (no Context), and threading Context through every
    // call site that might fire a stat (askCoach, saveJournalEntry, kick
    // sessions...) would touch far more files than this deserves. log()
    // before init() is a harmless, silent no-op.
    private var appContext: Context? = null

    /** Call once, e.g. from MainActivity.onCreate. Safe to call more than once. */
    fun init(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    private fun installId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_INSTALL_ID, null)?.let { return it }
        val fresh = UUID.randomUUID().toString()
        prefs.edit { putString(KEY_INSTALL_ID, fresh) }
        return fresh
    }

    private fun isConfigured(): Boolean =
        BuildConfig.AIRTABLE_TOKEN.isNotBlank() &&
            !BuildConfig.AIRTABLE_TOKEN.startsWith("MY_") &&
            BuildConfig.AIRTABLE_BASE_ID.isNotBlank() &&
            BuildConfig.AIRTABLE_TABLE.isNotBlank()

    /** Fire-and-forget. Safe to call from anywhere, any thread. Never throws. */
    fun log(event: StatEvent) {
        val context = appContext ?: return
        if (!isConfigured()) return
        scope.launch {
            try {
                val body = StatRecordBatch(
                    records = listOf(
                        StatRecord(
                            StatFields(
                                event = event.wireName,
                                platform = "android",
                                app_version = BuildConfig.VERSION_NAME,
                                install_id = installId(context),
                            )
                        )
                    )
                )
                val json = moshi.adapter(StatRecordBatch::class.java).toJson(body)
                val request = Request.Builder()
                    .url("https://api.airtable.com/v0/${BuildConfig.AIRTABLE_BASE_ID}/${BuildConfig.AIRTABLE_TABLE}")
                    .addHeader("Authorization", "Bearer ${BuildConfig.AIRTABLE_TOKEN}")
                    .post(json.toRequestBody(jsonMediaType))
                    .build()
                client.newCall(request).execute().close()
            } catch (_: Exception) {
                // Stats are never allowed to affect the app. Swallow silently.
            }
        }
    }
}
