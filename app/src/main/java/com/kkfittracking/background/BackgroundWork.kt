package com.kkfittracking.background

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kkfittracking.KKFittrackingApplication
import com.kkfittracking.MainActivity
import com.kkfittracking.R
import com.kkfittracking.RequestCodes
import com.kkfittracking.canPostNotifications
import com.kkfittracking.model.AutoBackup
import com.kkfittracking.model.Settings
import com.kkfittracking.model.dayCompletion
import com.kkfittracking.model.nextReminder
import com.kkfittracking.openAppIntent
import kotlinx.coroutines.flow.first
import java.io.IOException
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** Work that runs while the app is closed: automatic backups and training reminders. */
object BackgroundWork {
    private const val AUTO_BACKUP = "auto_backup"
    private const val REMINDER = "training_reminder"

    /** Saves a backup to Downloads/KK-Fittracking every day or week, or stops doing so. */
    fun scheduleAutoBackup(context: Context, frequency: AutoBackup) {
        val work = WorkManager.getInstance(context)
        val days = frequency.days
        if (days == null) {
            work.cancelUniqueWork(AUTO_BACKUP)
            return
        }
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(days, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        work.enqueueUniquePeriodicWork(AUTO_BACKUP, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** Sets the next training reminder from the settings, or cancels it when no day is chosen. */
    fun scheduleReminder(context: Context, settings: Settings, now: LocalDateTime = LocalDateTime.now()) {
        val work = WorkManager.getInstance(context)
        val next = nextReminder(now, settings.reminderDays, settings.reminderMinute)
        if (next == null) {
            work.cancelUniqueWork(REMINDER)
            return
        }
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
            .build()
        work.enqueueUniqueWork(REMINDER, ExistingWorkPolicy.REPLACE, request)
    }
}

class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as KKFittrackingApplication).container
        return try {
            container.dataTransfer.autoBackup()
            Result.success()
        } catch (e: IOException) {
            // Storage full or busy: try again later.
            Result.retry()
        } catch (e: SecurityException) {
            Result.failure()
        }
    }
}

/** "Time to train": what today holds, with a button that starts the guided workout. */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as KKFittrackingApplication).container
        val settings = container.settingsRepository.settings.first()
        val day = container.workoutRepository.observeDay(LocalDate.now()).first()
        // Not started or only partly done, as the day screen and the widget count it.
        val left = dayCompletion(day).exercises.filter { !it.isDone }
        val text = when {
            day.isEmpty() -> "Nothing planned yet: add a plan or log freely."
            left.isEmpty() -> "Everything planned today is done. Nice."
            else -> "${left.size} to do: " + left.take(4).joinToString(", ") { it.name } +
                if (left.size > 4) "…" else ""
        }
        notify(applicationContext, text, canStart = left.isNotEmpty())
        BackgroundWork.scheduleReminder(applicationContext, settings)
        return Result.success()
    }

    private fun notify(context: Context, text: String, canStart: Boolean) {
        if (!canPostNotifications(context)) return
        val channel = NotificationChannel(CHANNEL_ID, "Training reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Reminds you to train on the days you chose"
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle("Time to train 💪")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openAppIntent(context, RequestCodes.REMINDER))
            .setAutoCancel(true)
        if (canStart) builder.addAction(0, "▶ Start", MainActivity.startGuideIntent(context))
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
    }

    private companion object {
        const val CHANNEL_ID = "training_reminder"
        const val NOTIFICATION_ID = 4
    }
}
