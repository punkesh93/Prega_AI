package com.example.stats

import android.content.Context
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * Prega AI — anonymous aggregate stats, Firebase Analytics-backed.
 *
 * History: v1 posted rows to Airtable with a write token shipped in the APK.
 * It worked, but carried a free-tier ~1,000-row ceiling and an extractable
 * token. Firebase Analytics removes both: no token in the app (the
 * google-services.json config is public-by-design), no row limits, and
 * dashboards for free. The public API here (init + log) is unchanged, so
 * every call site survived the migration untouched.
 *
 * PRIVACY CONTRACT (mirrored in the website privacy policy — keep in sync):
 *   - Only the [StatEvent] names below are ever sent, with no parameters.
 *     No journal text, no notes, no mood value, no weight, no symptoms, no
 *     name, no photos — nothing she typed or logged, ever.
 *   - Identity is Firebase's per-install app-instance ID: random, created
 *     on-device, not tied to her Google account, resettable by clearing app
 *     data or uninstalling. Pure aggregate counting, not a profile.
 *   - Crash reports (Crashlytics) follow the same spirit: stack traces and
 *     device model, never her content.
 *
 * Note: app opens are NOT logged manually — "app_open" is a reserved event
 * Firebase collects automatically (alongside first_open and session_start),
 * so the old AppOpen enum entry is gone rather than double-counted.
 */
enum class StatEvent(val wireName: String) {
    CoachQuestionAsked("coach_question_asked"),
    JournalSaved("journal_saved"),
    KickSessionCompleted("kick_session_completed"),
    PremiumPurchased("premium_purchased"),
    GardenShared("garden_shared"),
}

object AppStats {

    private var analytics: FirebaseAnalytics? = null

    /** Call once, e.g. from MainActivity.onCreate. Safe to call more than once. */
    fun init(context: Context) {
        if (analytics == null) {
            analytics = runCatching {
                FirebaseAnalytics.getInstance(context.applicationContext)
            }.getOrNull()
        }
    }

    /** Fire-and-forget. Safe to call from anywhere, any thread. Never throws. */
    fun log(event: StatEvent) {
        runCatching { analytics?.logEvent(event.wireName, null) }
    }
}
