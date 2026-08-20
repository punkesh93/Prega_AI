package com.example.update

import android.content.Context
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * One-tap OTA updates for the GitHub-Releases distribution channel.
 *
 * How it works: every build carries its own git hash (BuildConfig.GIT_HASH,
 * injected by Gradle from the clone it was built from). Releases published
 * by Colab cell 7 are tagged build-<hash>. Once a day the app asks GitHub's
 * public releases API for the latest tag; a different hash means a newer
 * build exists, and Home shows a gentle update card whose single tap opens
 * the stable APK URL — the browser downloads it and Android's installer
 * takes over (it installs over the top; data survives).
 *
 * Honest limits, by design:
 *  - This is one-tap DOWNLOAD + system-confirm install, not a silent
 *    background update. True silent OTA needs Play Store (later) or a
 *    device-owner installer (never — that's enterprise malware territory).
 *  - Everything fails silent: no releases yet (404), offline, rate-limited,
 *    GIT_HASH == "dev" — the card simply doesn't appear. An update nag must
 *    never be the thing that breaks.
 *  - Dismissing the card remembers THAT release tag; the card returns only
 *    for the next newer build. Calm app, not a nag machine.
 */
object UpdateChecker {

    const val APK_URL =
        "https://github.com/punkesh93/Prega_AI/releases/latest/download/prega-ai.apk"
    private const val API_URL =
        "https://api.github.com/repos/punkesh93/Prega_AI/releases/latest"
    private const val PREFS = "prega_update"
    private const val KEY_LAST_CHECK_DAY = "last_check_day"
    private const val KEY_LAST_SEEN_TAG = "last_seen_tag"
    private const val KEY_DISMISSED_TAG = "dismissed_tag"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /** Latest release tag if it's a genuinely newer, un-dismissed build; else null. */
    suspend fun updateAvailable(context: Context, todayDate: String): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                if (BuildConfig.GIT_HASH == "dev") return@runCatching null
                val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

                // At most one network check per day; between checks, reuse
                // the cached answer so the card stays consistent all day.
                val tag = if (prefs.getString(KEY_LAST_CHECK_DAY, "") == todayDate) {
                    prefs.getString(KEY_LAST_SEEN_TAG, "").orEmpty()
                } else {
                    val response = client.newCall(
                        Request.Builder().url(API_URL)
                            .header("Accept", "application/vnd.github+json")
                            .build()
                    ).execute()
                    val fresh = response.use {
                        if (!it.isSuccessful) return@runCatching null
                        JSONObject(it.body?.string().orEmpty())
                            .optString("tag_name", "")
                    }
                    prefs.edit()
                        .putString(KEY_LAST_CHECK_DAY, todayDate)
                        .putString(KEY_LAST_SEEN_TAG, fresh)
                        .apply()
                    fresh
                }

                val releaseHash = tag.removePrefix("build-")
                when {
                    tag.isBlank() -> null
                    releaseHash == BuildConfig.GIT_HASH -> null
                    tag == prefs.getString(KEY_DISMISSED_TAG, "") -> null
                    else -> tag
                }
            }.getOrNull()
        }

    fun dismiss(context: Context, tag: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_DISMISSED_TAG, tag).apply()
    }
}
