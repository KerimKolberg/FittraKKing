package com.kkfittracking

import android.app.Application
import com.kkfittracking.background.BackgroundWork
import com.kkfittracking.background.TodayWidget
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class FitTraKKingApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.appScope.launch { container.exerciseRepository.syncBuiltIns() }
        container.watchBridge.start()
        // The app was called KK-Fittracking: move its backups to the new folder once (later starts find none).
        container.appScope.launch { runCatching { container.dataTransfer.moveOldBackups() } }
        // Automatic backups and reminders follow the settings.
        container.appScope.launch {
            container.settingsRepository.settings.map { it.autoBackup }.distinctUntilChanged()
                .collect { BackgroundWork.scheduleAutoBackup(this@FitTraKKingApplication, it) }
        }
        container.appScope.launch {
            container.settingsRepository.settings.distinctUntilChanged { a, b ->
                a.reminderDays == b.reminderDays && a.reminderMinute == b.reminderMinute
            }.collect { BackgroundWork.scheduleReminder(this@FitTraKKingApplication, it) }
        }
        // The home-screen widget follows today's log while the app runs (and refreshes itself every 30 minutes).
        container.appScope.launch {
            flow {
                while (true) {
                    emit(LocalDate.now())
                    delay(60_000)
                }
            }.distinctUntilChanged()
                .flatMapLatest { container.workoutRepository.observeDay(it) }
                .collect { TodayWidget.update(this@FitTraKKingApplication, it) }
        }
    }
}
