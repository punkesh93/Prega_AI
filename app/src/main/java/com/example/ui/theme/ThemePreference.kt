package com.example.ui.theme

import android.content.Context
import androidx.core.content.edit

/**
 * Manual theme override: System / Light / Dark.
 *
 * Stored in SharedPreferences rather than the Room profile on purpose — theme
 * choice is a device-level display setting, not pregnancy data. Keeping it out
 * of the database means no migration, no presence in the data export, and it
 * survives the "delete all my data" wipe (deleting her logs shouldn't also
 * blind her with a white screen at night).
 */
enum class ThemeMode { System, Light, Dark }

object ThemePreference {
    private const val PREFS = "prega_display"
    private const val KEY = "theme_mode"

    fun get(context: Context): ThemeMode {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, ThemeMode.System.name)
        return runCatching { ThemeMode.valueOf(raw!!) }.getOrDefault(ThemeMode.System)
    }

    fun set(context: Context, mode: ThemeMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit { putString(KEY, mode.name) }
    }
}
