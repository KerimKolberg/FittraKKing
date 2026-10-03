package com.kkfittracking

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** Whether the app may show notifications (asked for on Android 13 and later). */
fun canPostNotifications(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

/**
 * Opens the app from a notification or the widget, back to the screen it was on; with [epochDay]
 * and [exerciseId], straight to that exercise. Each caller uses its own [requestCode], so their
 * intents do not replace each other's.
 */
fun openAppIntent(context: Context, requestCode: Int, epochDay: Long? = null, exerciseId: String? = null): PendingIntent {
    val intent = Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    if (epochDay != null && exerciseId != null) {
        intent.putExtra(MainActivity.EXTRA_EPOCH_DAY, epochDay).putExtra(MainActivity.EXTRA_EXERCISE_ID, exerciseId)
    }
    return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
}

/** The request codes of the app's pending intents. */
object RequestCodes {
    const val GUIDE_NOTIFICATION = 0
    const val ALERT_NOTIFICATION = 1
    const val REMINDER = 10
    const val START_GUIDE = 11
    const val WIDGET = 12
}
