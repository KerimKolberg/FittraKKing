package com.kkfittracking.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.kkfittracking.model.Settings
import com.kkfittracking.model.ThemeMode
import com.kkfittracking.model.UnitSystem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepository(private val dataStore: DataStore<Preferences>) {
    val settings: Flow<Settings> = dataStore.data.map { prefs ->
        val defaults = Settings()
        Settings(
            unitSystem = prefs[UNIT_SYSTEM].toEnumOr(defaults.unitSystem),
            restTimerSeconds = prefs[REST_TIMER_SECONDS] ?: defaults.restTimerSeconds,
            autoStartRestTimer = prefs[AUTO_START_REST_TIMER] ?: defaults.autoStartRestTimer,
            themeMode = prefs[THEME_MODE].toEnumOr(defaults.themeMode),
            weeklyGoal = prefs[WEEKLY_GOAL] ?: defaults.weeklyGoal,
            dropSetsEnabled = prefs[DROP_SETS_ENABLED] ?: defaults.dropSetsEnabled,
            dropSetPercent = prefs[DROP_SET_PERCENT] ?: defaults.dropSetPercent,
            supersetAutoAdvance = prefs[SUPERSET_AUTO_ADVANCE] ?: defaults.supersetAutoAdvance,
            supersetTransitionSeconds = prefs[SUPERSET_TRANSITION] ?: defaults.supersetTransitionSeconds,
            sectionOrder = prefs[SECTION_ORDER].toList(),
            styleOrder = prefs[STYLE_ORDER].toList(),
            sendWorkoutsToHealth = prefs[SEND_WORKOUTS] ?: defaults.sendWorkoutsToHealth,
            sendCaloriesToHealth = prefs[SEND_CALORIES] ?: defaults.sendCaloriesToHealth,
        )
    }

    suspend fun setUnitSystem(value: UnitSystem) = dataStore.edit { it[UNIT_SYSTEM] = value.name }

    suspend fun setRestTimerSeconds(value: Int) =
        dataStore.edit { it[REST_TIMER_SECONDS] = value.coerceIn(MIN_REST_SECONDS, MAX_REST_SECONDS) }

    suspend fun setAutoStartRestTimer(value: Boolean) = dataStore.edit { it[AUTO_START_REST_TIMER] = value }

    suspend fun setThemeMode(value: ThemeMode) = dataStore.edit { it[THEME_MODE] = value.name }

    /** When the last backup file was saved, or null if never. */
    val lastBackupAt: Flow<Long?> = dataStore.data.map { it[LAST_BACKUP_AT] }

    suspend fun setLastBackupAt(value: Long) = dataStore.edit { it[LAST_BACKUP_AT] = value }

    /** Replaces all settings at once, for restoring a backup. */
    suspend fun restore(settings: Settings) = dataStore.edit {
        it[UNIT_SYSTEM] = settings.unitSystem.name
        it[REST_TIMER_SECONDS] = settings.restTimerSeconds.coerceIn(MIN_REST_SECONDS, MAX_REST_SECONDS)
        it[AUTO_START_REST_TIMER] = settings.autoStartRestTimer
        it[THEME_MODE] = settings.themeMode.name
        it[WEEKLY_GOAL] = settings.weeklyGoal.coerceIn(1, 7)
        it[DROP_SETS_ENABLED] = settings.dropSetsEnabled
        it[DROP_SET_PERCENT] = settings.dropSetPercent.coerceIn(MIN_DROP_PERCENT, MAX_DROP_PERCENT)
        it[SUPERSET_AUTO_ADVANCE] = settings.supersetAutoAdvance
        it[SUPERSET_TRANSITION] = settings.supersetTransitionSeconds.coerceIn(0, MAX_TRANSITION_SECONDS)
        it[SECTION_ORDER] = settings.sectionOrder.joinToString(",")
        it[STYLE_ORDER] = settings.styleOrder.joinToString(",")
        it[SEND_WORKOUTS] = settings.sendWorkoutsToHealth
        it[SEND_CALORIES] = settings.sendCaloriesToHealth
    }

    suspend fun setSendWorkoutsToHealth(value: Boolean) = dataStore.edit { it[SEND_WORKOUTS] = value }

    suspend fun setSendCaloriesToHealth(value: Boolean) = dataStore.edit { it[SEND_CALORIES] = value }

    /** The order of the library's sections (category ids); empty goes back to the default. */
    suspend fun setSectionOrder(order: List<String>) = dataStore.edit { it[SECTION_ORDER] = order.joinToString(",") }

    /** The order of the training styles (their names); empty goes back to the default. */
    suspend fun setStyleOrder(order: List<String>) = dataStore.edit { it[STYLE_ORDER] = order.joinToString(",") }

    suspend fun setSupersetTransitionSeconds(value: Int) =
        dataStore.edit { it[SUPERSET_TRANSITION] = value.coerceIn(0, MAX_TRANSITION_SECONDS) }

    suspend fun setDropSetsEnabled(value: Boolean) = dataStore.edit { it[DROP_SETS_ENABLED] = value }

    suspend fun setDropSetPercent(value: Int) =
        dataStore.edit { it[DROP_SET_PERCENT] = value.coerceIn(MIN_DROP_PERCENT, MAX_DROP_PERCENT) }

    suspend fun setSupersetAutoAdvance(value: Boolean) = dataStore.edit { it[SUPERSET_AUTO_ADVANCE] = value }

    suspend fun setWeeklyGoal(value: Int) = dataStore.edit { it[WEEKLY_GOAL] = value.coerceIn(1, 7) }

    companion object {
        const val MIN_REST_SECONDS = 15
        const val MAX_REST_SECONDS = 15 * 60
        const val MIN_DROP_PERCENT = 5
        const val MAX_DROP_PERCENT = 50
        const val MAX_TRANSITION_SECONDS = 120

        private val UNIT_SYSTEM = stringPreferencesKey("unit_system")
        private val REST_TIMER_SECONDS = intPreferencesKey("rest_timer_seconds")
        private val AUTO_START_REST_TIMER = booleanPreferencesKey("auto_start_rest_timer")
        private val THEME_MODE = stringPreferencesKey("theme_mode")
        private val WEEKLY_GOAL = intPreferencesKey("weekly_goal")
        private val LAST_BACKUP_AT = longPreferencesKey("last_backup_at")
        private val DROP_SETS_ENABLED = booleanPreferencesKey("drop_sets_enabled")
        private val DROP_SET_PERCENT = intPreferencesKey("drop_set_percent")
        private val SUPERSET_AUTO_ADVANCE = booleanPreferencesKey("superset_auto_advance")
        private val SUPERSET_TRANSITION = intPreferencesKey("superset_transition_seconds")
        private val SECTION_ORDER = stringPreferencesKey("section_order")
        private val STYLE_ORDER = stringPreferencesKey("style_order")
        private val SEND_WORKOUTS = booleanPreferencesKey("send_workouts_to_health")
        private val SEND_CALORIES = booleanPreferencesKey("send_calories_to_health")
    }
}

private inline fun <reified T : Enum<T>> String?.toEnumOr(default: T): T =
    this?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: default

private fun String?.toList(): List<String> = this?.split(",")?.filter { it.isNotBlank() }.orEmpty()
