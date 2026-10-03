package com.kkfittracking.ui.analysis

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.kkfittracking.data.BodyRepository
import com.kkfittracking.data.ExerciseRepository
import com.kkfittracking.data.SettingsRepository
import com.kkfittracking.data.WorkoutRepository
import com.kkfittracking.model.BodyMeasurement
import com.kkfittracking.model.Exercise
import com.kkfittracking.model.ExerciseRecord
import com.kkfittracking.model.HistorySession
import com.kkfittracking.model.UnitSystem
import com.kkfittracking.model.allRecords
import com.kkfittracking.ui.AnalysisRoute
import com.kkfittracking.ui.appViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

data class AnalysisUiState(
    /** Every exercise's history, newest session first. */
    val histories: Map<String, List<HistorySession>> = emptyMap(),
    /** The exercises that have been logged, most recently logged first. */
    val logged: List<Exercise> = emptyList(),
    val records: List<ExerciseRecord> = emptyList(),
    val measurements: List<BodyMeasurement> = emptyList(),
    val units: UnitSystem = UnitSystem.METRIC,
    val isLoading: Boolean = true,
)

class AnalysisViewModel(
    savedStateHandle: SavedStateHandle,
    workoutRepository: WorkoutRepository,
    exerciseRepository: ExerciseRepository,
    bodyRepository: BodyRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {
    /** The exercise to show first, when the screen was opened for one. */
    val startExerciseId: String? = savedStateHandle.toRoute<AnalysisRoute>().exerciseId

    val uiState: StateFlow<AnalysisUiState> = combine(
        workoutRepository.observeAllHistory(),
        exerciseRepository.exercises,
        bodyRepository.measurements,
        settingsRepository.settings,
    ) { histories, exercises, measurements, settings ->
        val logged = exercises.filter { histories[it.id].orEmpty().isNotEmpty() }
            .sortedByDescending { histories.getValue(it.id).first().date }
        AnalysisUiState(
            histories = histories,
            logged = logged,
            records = allRecords(histories, logged, settings.unitSystem),
            measurements = measurements,
            units = settings.unitSystem,
            isLoading = false,
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalysisUiState())

    companion object {
        val Factory = appViewModelFactory { container ->
            AnalysisViewModel(
                createSavedStateHandle(),
                container.workoutRepository,
                container.exerciseRepository,
                container.bodyRepository,
                container.settingsRepository,
            )
        }
    }
}
