package com.kkfittracking.guide

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.kkfittracking.FitnessApplication
import com.kkfittracking.R
import com.kkfittracking.RequestCodes
import com.kkfittracking.openAppIntent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps a guided workout going while the phone is locked or another app is open: an ongoing
 * notification with the exercise to do now, how much of the plan is done, the training time, and
 * Pause, Skip and Stop. Tapping it opens that exercise. Stops itself when the workout ends.
 */
class WorkoutGuideService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val guide: GuidedWorkout get() = (application as FitnessApplication).container.guidedWorkout
    private var watching = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // A foreground service must show its notification right away, even when it is about to stop.
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, buildNotification(guide.state.value),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
        when (intent?.action) {
            ACTION_PAUSE -> guide.pause()
            ACTION_RESUME -> guide.resume()
            ACTION_SKIP -> guide.state.value.target?.let { guide.skip(it.exerciseId) }
            ACTION_STOP -> guide.stop()
        }
        if (!guide.isRunning) {
            finish()
            return START_NOT_STICKY
        }
        if (!watching) {
            watching = true
            scope.launch {
                // Only what the notification shows: the day's sets change more often than that.
                guide.state
                    .map { NotificationContent.of(it) }
                    .distinctUntilChanged()
                    .collect { content ->
                        if (content == null) {
                            // The state catches up with a new workout a moment after it starts.
                            if (!guide.isRunning) finish()
                        } else {
                            getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, build(content))
                        }
                    }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun finish() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(state: GuideState): Notification =
        build(NotificationContent.of(state) ?: NotificationContent("Workout", "Getting ready…", null, null, 0, paused = false, canSkip = false))

    private fun build(content: NotificationContent): Notification {
        ensureChannel(this)
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content.text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setProgress(100, content.percent, false)
            .setContentIntent(openIntent(content.epochDay, content.exerciseId))
        if (!content.paused && content.startedAtMillis != null) {
            // The system counts the training time up, so the notification needs no update each second.
            builder.setUsesChronometer(true).setWhen(content.startedAtMillis).setShowWhen(true)
        } else {
            builder.setShowWhen(false)
        }
        if (content.paused) {
            builder.addAction(0, "Resume", actionIntent(ACTION_RESUME))
        } else {
            builder.addAction(0, "Pause", actionIntent(ACTION_PAUSE))
        }
        if (content.canSkip) builder.addAction(0, "Skip", actionIntent(ACTION_SKIP))
        builder.addAction(0, "Stop", actionIntent(ACTION_STOP))
        return builder.build()
    }

    private fun actionIntent(action: String): PendingIntent = PendingIntent.getService(
        this, action.hashCode(), Intent(this, WorkoutGuideService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun openIntent(epochDay: Long?, exerciseId: String?): PendingIntent =
        openAppIntent(this, RequestCodes.GUIDE_NOTIFICATION, epochDay, exerciseId)

    /** The parts of the guide the notification shows. */
    private data class NotificationContent(
        val title: String,
        val text: String,
        val epochDay: Long?,
        val exerciseId: String?,
        val percent: Int,
        val paused: Boolean,
        val canSkip: Boolean,
        /** When the training time started counting, with the pauses taken out. */
        val startedAtMillis: Long? = null,
    ) {
        companion object {
            fun of(state: GuideState): NotificationContent? {
                val session = state.session ?: return null
                val target = state.target
                val done = "${state.completion.percent}% of the plan done"
                val startedAt = session.startedAtMillis + session.pausedMillis
                return when {
                    session.isPaused -> NotificationContent(
                        title = "Paused",
                        text = "Take your time. $done.\nNext: ${target?.let { "${it.name} · ${it.step}" } ?: "nothing left"}",
                        epochDay = session.epochDay, exerciseId = target?.exerciseId, percent = state.completion.percent,
                        paused = true, canSkip = false,
                    )
                    target == null -> NotificationContent(
                        title = "Everything planned is done",
                        text = "$done. Log more, or tap Stop to finish.",
                        epochDay = session.epochDay, exerciseId = null, percent = state.completion.percent,
                        paused = false, canSkip = false, startedAtMillis = startedAt,
                    )
                    else -> NotificationContent(
                        title = "${target.name} · ${target.step}",
                        text = "Exercise ${target.position} of ${target.of} · $done" +
                            state.upcoming.takeIf { it.isNotEmpty() }?.let { "\nThen: " + it.joinToString(" → ") }.orEmpty(),
                        epochDay = session.epochDay, exerciseId = target.exerciseId, percent = state.completion.percent,
                        paused = false, canSkip = !target.isDrop, startedAtMillis = startedAt,
                    )
                }
            }
        }
    }

    companion object {
        private const val CHANNEL_ID = "guided_workout"
        private const val NOTIFICATION_ID = 3
        private const val ACTION_PAUSE = "com.kkfittracking.guide.PAUSE"
        private const val ACTION_RESUME = "com.kkfittracking.guide.RESUME"
        private const val ACTION_SKIP = "com.kkfittracking.guide.SKIP"
        private const val ACTION_STOP = "com.kkfittracking.guide.STOP"

        /** Brings up the notification; call while the app is in the foreground. */
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, WorkoutGuideService::class.java))
        }

        private fun ensureChannel(context: Context) {
            val channel = NotificationChannel(CHANNEL_ID, "Guided workout", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows the exercise to do next while a guided workout runs"
                setShowBadge(false)
            }
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }
}
