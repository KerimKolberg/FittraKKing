package com.kkfittracking.ui.settings

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kkfittracking.data.ExerciseRepository
import com.kkfittracking.data.SettingsRepository
import com.kkfittracking.data.backup.BackupException
import com.kkfittracking.data.backup.BackupFile
import com.kkfittracking.data.backup.DataTransfer
import com.kkfittracking.data.health.HealthConnect
import com.kkfittracking.model.AutoBackup
import com.kkfittracking.model.Category
import com.kkfittracking.model.Settings
import com.kkfittracking.model.ThemeMode
import com.kkfittracking.model.TrainingStyle
import com.kkfittracking.model.UnitSystem
import com.kkfittracking.model.inUserOrder
import com.kkfittracking.model.moveInOrder
import com.kkfittracking.ui.appViewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.DayOfWeek

class SettingsViewModel(
    private val repository: SettingsRepository,
    private val dataTransfer: DataTransfer,
    exerciseRepository: ExerciseRepository,
    private val healthConnect: HealthConnect,
) : ViewModel() {
    /** Whether Health Connect is there, and whether workouts may be written to it. */
    var healthStatus by mutableStateOf<String?>(null)
        private set

    /** What to ask Health Connect for: reading steps, writing workouts and their calories. */
    val healthPermissions: Set<String> get() = healthConnect.allPermissions

    fun refreshHealthStatus() {
        viewModelScope.launch {
            healthStatus = when (healthConnect.status()) {
                HealthConnect.Status.UNAVAILABLE -> "Health Connect is not available on this phone"
                HealthConnect.Status.NEEDS_UPDATE -> "Install or update Health Connect first"
                HealthConnect.Status.AVAILABLE ->
                    if (runCatching { healthConnect.canWriteWorkouts() }.getOrDefault(false)) "Connected" else "Not connected yet"
            }
        }
    }

    fun setSendWorkoutsToHealth(value: Boolean) {
        viewModelScope.launch { repository.setSendWorkoutsToHealth(value) }
    }

    fun setSendCaloriesToHealth(value: Boolean) {
        viewModelScope.launch { repository.setSendCaloriesToHealth(value) }
    }

    /** The library's sections, in the user's order, for arranging them. */
    val sections: StateFlow<List<Category>> = combine(exerciseRepository.categories, repository.settings) { categories, settings ->
        categories.inUserOrder(settings.sectionOrder) { it.id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Moves a section one place up (-1) or down (+1) in the library. */
    fun moveSection(id: String, direction: Int) {
        val order = moveInOrder(sections.value.map { it.id }, id, direction)
        viewModelScope.launch { repository.setSectionOrder(order) }
    }

    /** Moves a training style one place up (-1) or down (+1). */
    fun moveStyle(style: TrainingStyle, direction: Int) {
        val shown = TrainingStyle.sectionOrder.inUserOrder(settings.value?.styleOrder.orEmpty()) { it.name }.map { it.name }
        viewModelScope.launch { repository.setStyleOrder(moveInOrder(shown, style.name, direction)) }
    }

    /** Back to the app's own order of sections and styles. */
    fun resetLibraryOrder() {
        viewModelScope.launch {
            repository.setSectionOrder(emptyList())
            repository.setStyleOrder(emptyList())
        }
    }

    val lastBackupAt: StateFlow<Long?> =
        repository.lastBackupAt.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** A short result message, such as "Backup saved". */
    var message by mutableStateOf<String?>(null)
        private set

    /** A backup that was read and is waiting for the user to confirm the restore. */
    var pendingRestore by mutableStateOf<BackupFile?>(null)
        private set

    var isBusy by mutableStateOf(false)
        private set

    fun backUp(uri: Uri) = perform("Backup saved") { dataTransfer.writeBackup(uri) }

    /** Android 10 and later: files go straight to Downloads/KK-Fittracking. */
    val canSaveToDownloads: Boolean get() = dataTransfer.canSaveToDownloads

    fun saveAllToDownloads() {
        viewModelScope.launch {
            isBusy = true
            message = try {
                "Saved to ${dataTransfer.saveAllToDownloads()}"
            } catch (e: IOException) {
                "Could not save: ${e.message}"
            } catch (e: SecurityException) {
                "The app is not allowed to save to Downloads."
            } finally {
                isBusy = false
            }
        }
    }

    fun setAutoBackup(value: AutoBackup) {
        viewModelScope.launch { repository.setAutoBackup(value) }
    }

    fun setProgressionHints(value: Boolean) {
        viewModelScope.launch { repository.setProgressionHints(value) }
    }

    fun setWarmUpSets(value: Boolean) {
        viewModelScope.launch { repository.setWarmUpSets(value) }
    }

    /** One step (2.5 kg or 5 lb) heavier (+1) or lighter (-1) bar. */
    fun changeBar(direction: Int) {
        val current = settings.value ?: return
        val units = current.unitSystem
        val value = units.weightFromKg(current.barKg) + direction * units.weightStep
        viewModelScope.launch { repository.setBarKg(units.weightToKg(value.coerceAtLeast(0.0))) }
    }

    fun toggleReminderDay(day: DayOfWeek) {
        val days = settings.value?.reminderDays ?: return
        viewModelScope.launch { repository.setReminderDays(if (day in days) days - day else days + day) }
    }

    fun setReminderMinute(value: Int) {
        viewModelScope.launch { repository.setReminderMinute(value) }
    }

    fun exportWorkouts(uri: Uri) = perform("Workouts exported") { dataTransfer.exportWorkouts(uri) }

    fun exportBodyMeasurements(uri: Uri) = perform("Body measurements exported") { dataTransfer.exportBodyMeasurements(uri) }

    /** Reads the file and asks for confirmation before replacing anything. */
    fun openBackup(uri: Uri) = perform(null) { pendingRestore = dataTransfer.readBackup(uri) }

    fun confirmRestore() {
        val file = pendingRestore ?: return
        pendingRestore = null
        perform("Backup restored") { dataTransfer.restore(file) }
    }

    fun cancelRestore() {
        pendingRestore = null
    }

    fun consumeMessage() {
        message = null
    }

    private fun perform(successMessage: String?, action: suspend () -> Unit) {
        viewModelScope.launch {
            isBusy = true
            message = try {
                action()
                successMessage
            } catch (e: BackupException) {
                e.message
            } catch (e: IOException) {
                "Could not use that file: ${e.message}"
            } catch (e: SecurityException) {
                "The app is not allowed to use that file."
            } finally {
                isBusy = false
            }
        }
    }

    /** Null until the stored settings are loaded, so the screen never flashes the defaults. */
    val settings: StateFlow<Settings?> =
        repository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setUnitSystem(value: UnitSystem) {
        viewModelScope.launch { repository.setUnitSystem(value) }
    }

    fun changeRestTimer(deltaSeconds: Int) {
        val current = settings.value ?: return
        viewModelScope.launch { repository.setRestTimerSeconds(current.restTimerSeconds + deltaSeconds) }
    }

    fun setAutoStartRestTimer(value: Boolean) {
        viewModelScope.launch { repository.setAutoStartRestTimer(value) }
    }

    fun setDropSetsEnabled(value: Boolean) {
        viewModelScope.launch { repository.setDropSetsEnabled(value) }
    }

    fun changeDropSetPercent(delta: Int) {
        val current = settings.value ?: return
        viewModelScope.launch { repository.setDropSetPercent(current.dropSetPercent + delta) }
    }

    fun changeSupersetTransition(delta: Int) {
        val current = settings.value ?: return
        viewModelScope.launch { repository.setSupersetTransitionSeconds(current.supersetTransitionSeconds + delta) }
    }

    fun setSupersetAutoAdvance(value: Boolean) {
        viewModelScope.launch { repository.setSupersetAutoAdvance(value) }
    }

    fun setThemeMode(value: ThemeMode) {
        viewModelScope.launch { repository.setThemeMode(value) }
    }

    companion object {
        val Factory = appViewModelFactory { container ->
            SettingsViewModel(container.settingsRepository, container.dataTransfer, container.exerciseRepository, container.healthConnect)
        }
    }
}
