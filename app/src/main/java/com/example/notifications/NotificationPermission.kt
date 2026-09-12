package com.example.notifications

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit

/**
 * Prega AI — the one place that knows the state of POST_NOTIFICATIONS.
 *
 * Android 13+ has three states the app must tell apart, and the runtime
 * permission API only exposes two of them:
 *
 *  - never asked      → we may show the dialog
 *  - asked, denied    → we may show the dialog once more
 *  - denied twice     → the dialog is gone for good; launch() returns false
 *                       immediately and any switch wired to it looks broken.
 *
 * `shouldShowRequestPermissionRationale` is false in BOTH the first and last
 * states, so a tiny "have we asked before" flag is what separates them.
 * Below Android 13 the permission doesn't exist and everything is granted.
 */
object NotificationPermission {

    private const val PREFS = "prega_notifications"
    private const val KEY_ASKED = "asked_post_notifications"

    fun granted(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    fun asked(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ASKED, false)

    fun markAsked(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit { putBoolean(KEY_ASKED, true) }
    }

    /** Asked before, still denied, and the system will no longer show the dialog. */
    fun permanentlyDenied(activity: Activity): Boolean {
        if (granted(activity) || !asked(activity)) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        return !ActivityCompat.shouldShowRequestPermissionRationale(
            activity, Manifest.permission.POST_NOTIFICATIONS
        )
    }

    /** Opens the system notification page for this app (falls back to app info). */
    fun openSystemSettings(context: Context) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", context.packageName, null))
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}
