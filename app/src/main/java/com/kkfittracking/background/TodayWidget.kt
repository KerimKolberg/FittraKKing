package com.kkfittracking.background

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.kkfittracking.FitnessApplication
import com.kkfittracking.MainActivity
import com.kkfittracking.R
import com.kkfittracking.model.DayExercise
import com.kkfittracking.model.dayCompletion
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

/** The home-screen widget: today's plan, how much is done, what is left, and ▶ Start. */
class TodayWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        val container = (context.applicationContext as FitnessApplication).container
        container.appScope.launch {
            try {
                val day = container.workoutRepository.observeDay(LocalDate.now()).first()
                val views = views(context, day)
                ids.forEach { manager.updateAppWidget(it, views) }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        /** Redraws every placed widget with [day] (today's exercises). */
        fun update(context: Context, day: List<DayExercise>) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TodayWidget::class.java))
            if (ids.isEmpty()) return
            val views = views(context, day)
            ids.forEach { manager.updateAppWidget(it, views) }
        }

        private fun views(context: Context, day: List<DayExercise>): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_today)
            val completion = dayCompletion(day)
            val left = completion.exercises.filter { !it.isDone }
            views.setTextViewText(R.id.widget_progress, if (day.isEmpty()) "" else "${completion.percent}% done")
            views.setTextViewText(
                R.id.widget_list,
                when {
                    day.isEmpty() -> "Nothing planned yet. Tap to add a plan or log freely."
                    left.isEmpty() -> "Everything planned is done. 🏁"
                    else -> left.joinToString("\n") { "• ${it.name} · ${it.label}" }
                },
            )
            views.setViewVisibility(R.id.widget_start, if (left.isEmpty()) View.GONE else View.VISIBLE)
            views.setOnClickPendingIntent(R.id.widget_start, MainActivity.startGuideIntent(context))
            val open = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            views.setOnClickPendingIntent(
                R.id.widget_root,
                PendingIntent.getActivity(context, 12, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
            return views
        }
    }
}
