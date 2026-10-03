package com.kkfittracking.timer

import com.kkfittracking.RequestCodes
import com.kkfittracking.openAppIntent
import com.kkfittracking.canPostNotifications
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.kkfittracking.R

/** Vibrations, beeps and notifications shared by the timers. */
class Alerts(private val context: Context) {
    private var tones: ToneGenerator? = null

    /** Vibrates with a pattern of off/on durations in milliseconds, starting with a pause. */
    fun vibrate(vararg pattern: Long) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            context.getSystemService(Vibrator::class.java)
        }
        vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }

    /**
     * Plays a [ToneGenerator] tone on the media stream, so it is heard through headphones and
     * over music, at the media volume.
     */
    fun beep(tone: Int, durationMillis: Int) {
        val generator = tones ?: try {
            ToneGenerator(AudioManager.STREAM_MUSIC, ToneGenerator.MAX_VOLUME).also { tones = it }
        } catch (e: RuntimeException) {
            // The audio system can refuse a new tone generator; the vibration still comes.
            return
        }
        generator.startTone(tone, durationMillis)
    }

    /** Shows a notification that opens the app, if the user allowed notifications. */
    fun notify(channel: Channel, notificationId: Int, title: String, text: String) {
        if (!canPostNotifications(context)) return
        val notificationChannel = NotificationChannel(channel.id, channel.title, NotificationManager.IMPORTANCE_HIGH).apply {
            description = channel.description
            // The alerts vibrate on their own, so the notification itself does not.
            enableVibration(false)
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(notificationChannel)
        val notification = NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openAppIntent(context, RequestCodes.ALERT_NOTIFICATION))
            .setAutoCancel(true)
            .setTimeoutAfter(TIMEOUT_MILLIS)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    enum class Channel(val id: String, val title: String, val description: String) {
        REST("rest_timer", "Rest timer", "Tells you when your rest between sets is over"),
        INTERVALS("interval_timer", "Interval timer", "Tells you when your HIIT workout is done"),
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5 * 60 * 1000L
    }
}

/** Tells the user their rest is over: a vibration, plus a notification when they allowed notifications. */
class RestTimerAlarm(private val alerts: Alerts) {
    /** Alerts the user; [label] (e.g. "Go to Lat Pulldown") replaces the default text. */
    fun fire(label: String?) {
        alerts.vibrate(0, 400, 200, 400)
        alerts.notify(
            channel = Alerts.Channel.REST,
            notificationId = REST_NOTIFICATION_ID,
            title = if (label != null) "Time's up" else "Rest is over",
            text = label ?: "Time for your next set",
        )
    }

    private companion object {
        const val REST_NOTIFICATION_ID = 1
    }
}

/**
 * Beeps and vibrates through a HIIT workout, so the phone can stay in a pocket: a long buzz when
 * high intensity starts, a double buzz for low intensity, and 3-2-1 beeps before each change.
 */
class IntervalAlarm(private val alerts: Alerts) {
    fun onEvent(event: IntervalEvent) {
        when (event) {
            is IntervalEvent.PhaseStarted -> when (event.phase) {
                IntervalPhase.GET_READY -> alerts.beep(ToneGenerator.TONE_PROP_BEEP, 150)
                IntervalPhase.HIGH -> {
                    alerts.vibrate(0, 600)
                    alerts.beep(ToneGenerator.TONE_CDMA_HIGH_L, 500)
                }
                IntervalPhase.LOW -> {
                    alerts.vibrate(0, 200, 150, 200)
                    alerts.beep(ToneGenerator.TONE_CDMA_LOW_L, 500)
                }
                IntervalPhase.IDLE, IntervalPhase.DONE -> Unit
            }
            is IntervalEvent.Countdown -> alerts.beep(ToneGenerator.TONE_PROP_BEEP, 150)
            is IntervalEvent.Finished -> {
                alerts.vibrate(0, 400, 200, 400, 200, 400)
                alerts.beep(ToneGenerator.TONE_PROP_ACK, 800)
                alerts.notify(
                    channel = Alerts.Channel.INTERVALS,
                    notificationId = INTERVAL_NOTIFICATION_ID,
                    title = "Intervals done",
                    text = "${event.rounds} rounds. Open the app to log them.",
                )
            }
        }
    }

    private companion object {
        const val INTERVAL_NOTIFICATION_ID = 2
    }
}
