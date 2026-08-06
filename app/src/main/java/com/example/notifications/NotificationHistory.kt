package com.example.notifications

import android.content.Context
import androidx.core.content.edit

/**
 * Remembers recently-sent notification copy.
 *
 * This is what makes "always fresh" actually work. The AI is perfectly capable
 * of producing the same cheerful sentence eleven days running, and the second
 * time a user recognises a notification she's seen before, the whole thing
 * stops feeling personal. So every send is recorded and the last N are fed back
 * into the prompt as an explicit do-not-repeat list.
 *
 * SharedPreferences rather than Room: this is ephemeral, non-user-facing data
 * that should never appear in an export or survive a reinstall.
 */
class NotificationHistory(context: Context) {

    private val prefs = context.getSharedPreferences("prega_notif_history", Context.MODE_PRIVATE)

    /** Titles+bodies of recent sends, newest first. */
    fun recent(kind: String, limit: Int = MEMORY): List<String> =
        prefs.getString(key(kind), null)
            ?.split(SEPARATOR)
            ?.filter { it.isNotBlank() }
            ?.take(limit)
            .orEmpty()

    fun record(kind: String, title: String, body: String) {
        val entry = "$title — $body".replace(SEPARATOR, " ")
        val updated = (listOf(entry) + recent(kind, MEMORY - 1)).take(MEMORY)
        prefs.edit { putString(key(kind), updated.joinToString(SEPARATOR)) }
    }

    /** Last date (yyyy-MM-dd) a given kind was sent, for once-per-day guards. */
    fun lastSentDate(kind: String): String? = prefs.getString(dateKey(kind), null)

    fun markSent(kind: String, date: String) {
        prefs.edit { putString(dateKey(kind), date) }
    }

    fun clear() = prefs.edit { clear() }

    private fun key(kind: String) = "history_$kind"
    private fun dateKey(kind: String) = "lastdate_$kind"

    private companion object {
        /** Enough to cover a fortnight of a daily notification. */
        const val MEMORY = 14
        const val SEPARATOR = "\u001F"
    }
}
