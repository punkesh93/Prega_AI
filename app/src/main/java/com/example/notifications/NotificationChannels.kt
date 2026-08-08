package com.example.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.getSystemService

/**
 * Prega AI — notification channels.
 *
 * Deliberately split into four rather than one. Android lets users mute
 * individual channels, so separating them means someone who finds the daily
 * check-in too much can silence just that, instead of turning off every
 * notification and losing the week milestones and appointment reminders too.
 *
 * That choice costs nothing and is the difference between "muted" and
 * "uninstalled".
 */
object NotificationChannels {

    /** Week changes, trimester milestones. Low volume, high value. */
    const val MILESTONES = "prega_milestones_v2"

    /** Appointment reminders. The most functionally important channel. */
    const val APPOINTMENTS = "prega_appointments_v2"

    /** Daily check-in, quests, encouragement. The chattiest channel. */
    const val DAILY = "prega_daily_v2"

    /** Hydration, kick counting, rest. Opt-out friendly. */
    const val NUDGES = "prega_nudges_v2"

    fun registerAll(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService<NotificationManager>() ?: return

        listOf(
            Channel(
                MILESTONES,
                "Milestones",
                "New pregnancy weeks and trimester changes",
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
            Channel(
                APPOINTMENTS,
                "Appointments",
                "Reminders for scans, checks and midwife visits",
                NotificationManager.IMPORTANCE_HIGH,
            ),
            // Both of these were IMPORTANCE_LOW — deliberately silent — and
            // real-device feedback was that "notifications have no sound",
            // i.e. silence read as broken, not considerate. DEFAULT gives the
            // system sound; quiet hours still gate WHEN anything fires, and
            // she can silence any channel in system settings. IDs are bumped
            // to _v2 because Android never updates an existing channel's
            // importance — without new IDs, existing installs would stay
            // silent forever.
            Channel(
                DAILY,
                "Daily check-in",
                "Your daily quests and a gentle morning open",
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
            Channel(
                NUDGES,
                "Gentle nudges",
                "Water, rest and kick-counting reminders",
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        ).forEach { manager.createNotificationChannel(it.build()) }
    }

    private data class Channel(
        val id: String,
        val title: String,
        val description: String,
        val importance: Int,
    ) {
        @RequiresApi(Build.VERSION_CODES.O)
        fun build() = NotificationChannel(id, title, importance).also {
            it.description = description
            it.enableVibration(importance >= NotificationManager.IMPORTANCE_DEFAULT)
            it.setShowBadge(true)
        }
    }
}
