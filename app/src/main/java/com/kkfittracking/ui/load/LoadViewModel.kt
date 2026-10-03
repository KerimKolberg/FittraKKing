package com.kkfittracking.ui.load

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kkfittracking.data.ExerciseRepository
import com.kkfittracking.data.PainRepository
import com.kkfittracking.data.WorkoutRepository
import com.kkfittracking.model.ForgottenArea
import com.kkfittracking.model.LoadRatio
import com.kkfittracking.model.Muscle
import com.kkfittracking.model.PainCheck
import com.kkfittracking.model.PainEntry
import com.kkfittracking.model.PainMoment
import com.kkfittracking.model.Tendon
import com.kkfittracking.model.forgottenAreas
import com.kkfittracking.model.loadRatio
import com.kkfittracking.model.painChecks
import com.kkfittracking.model.setsPerMuscle
import com.kkfittracking.model.setsPerTendon
import com.kkfittracking.ui.appViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class LoadUiState(
    /** Sets per muscle in the last 7 days. */
    val muscleSets: Map<Muscle, Double> = emptyMap(),
    /** Sets per tendon in the last 7 days. */
    val tendonSets: Map<Tendon, Int> = emptyMap(),
    val ratio: LoadRatio = LoadRatio(0.0, 0.0),
    val forgotten: List<ForgottenArea> = emptyList(),
    /** Each rated tendon's latest session, the most recent first. */
    val checks: List<PainCheck> = emptyList(),
    val pain: List<PainEntry> = emptyList(),
    val isLoading: Boolean = true,
)

/** The week's training load per muscle and tendon, the load trend, forgotten areas and the tendon pain log. */
class LoadViewModel(
    workoutRepository: WorkoutRepository,
    exerciseRepository: ExerciseRepository,
    private val painRepository: PainRepository,
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {
    val uiState: StateFlow<LoadUiState> = combine(
        workoutRepository.observeAllHistory(),
        exerciseRepository.exercises,
        painRepository.entries,
    ) { histories, exercises, pain ->
        val day = today()
        val weekStart = day.minusDays(6)
        LoadUiState(
            muscleSets = setsPerMuscle(histories, exercises, weekStart, day),
            tendonSets = setsPerTendon(histories, exercises, weekStart, day),
            ratio = loadRatio(histories, exercises, day),
            forgotten = forgottenAreas(histories, exercises, day),
            checks = painChecks(pain),
            pain = pain,
            isLoading = false,
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LoadUiState())

    /** A morning rating is for today; before and after, for today's session too. */
    fun rate(tendons: List<Tendon>, moment: PainMoment, score: Int) {
        viewModelScope.launch { tendons.forEach { painRepository.rate(it, today(), moment, score) } }
    }

    fun delete(entry: PainEntry) {
        viewModelScope.launch { painRepository.delete(entry.id) }
    }

    companion object {
        val Factory = appViewModelFactory { container ->
            LoadViewModel(container.workoutRepository, container.exerciseRepository, container.painRepository)
        }
    }
}
