package com.kkfittracking.ui.log

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.kkfittracking.data.ExerciseRepository
import com.kkfittracking.data.GameRepository
import com.kkfittracking.data.PainRepository
import com.kkfittracking.data.RoutineRepository
import com.kkfittracking.data.SettingsRepository
import com.kkfittracking.data.WorkoutRepository
import com.kkfittracking.guide.GuideState
import com.kkfittracking.guide.GuidedWorkout
import com.kkfittracking.model.Category
import com.kkfittracking.model.DayExercise
import com.kkfittracking.model.Exercise
import com.kkfittracking.model.ExerciseLink
import com.kkfittracking.model.ExercisePlan
import com.kkfittracking.model.ExerciseType
import com.kkfittracking.model.GameStats
import com.kkfittracking.model.GuideTarget
import com.kkfittracking.model.HistorySession
import com.kkfittracking.model.NextStep
import com.kkfittracking.model.PainEntry
import com.kkfittracking.model.PainMoment
import com.kkfittracking.model.Routine
import com.kkfittracking.model.SetEntry
import com.kkfittracking.model.SetValues
import com.kkfittracking.model.Settings
import com.kkfittracking.model.Side
import com.kkfittracking.model.SupersetContext
import com.kkfittracking.model.SupersetMember
import com.kkfittracking.model.Tendon
import com.kkfittracking.model.UnitSystem
import com.kkfittracking.model.XpRules
import com.kkfittracking.model.celebrationsBetween
import com.kkfittracking.model.formatNumber
import com.kkfittracking.model.nextDropKg
import com.kkfittracking.model.nextStep
import com.kkfittracking.model.parseDecimal
import com.kkfittracking.model.pendingDrop
import com.kkfittracking.model.personalRecordSetIds
import com.kkfittracking.model.recordScore
import com.kkfittracking.model.roundToPlates
import com.kkfittracking.model.sideDue
import com.kkfittracking.timer.IntervalPhase
import com.kkfittracking.timer.IntervalTimer
import com.kkfittracking.timer.IntervalTimerState
import com.kkfittracking.timer.RestTimer
import com.kkfittracking.timer.RestTimerState
import com.kkfittracking.ui.ExerciseLogRoute
import com.kkfittracking.ui.appViewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class ExerciseLogUiState(
    val exercise: Exercise? = null,
    val units: UnitSystem = UnitSystem.METRIC,
    /** The sets logged on the screen's date. */
    val sets: List<SetEntry> = emptyList(),
    /** The most recent session before the screen's date. */
    val previousSession: HistorySession? = null,
    val history: List<HistorySession> = emptyList(),
    /** Sets that were a personal record when logged. */
    val recordSetIds: Set<String> = emptySet(),
    val settings: Settings = Settings(),
    /** The exercises of this exercise's superset on the screen's date, in order; empty if none. */
    val superset: List<DayExercise> = emptyList(),
    /** This exercise's entry on the screen's date (with its superset timing), once logged or planned. */
    val dayEntry: DayExercise? = null,
    val isLoading: Boolean = true,
) {
    val plan: ExercisePlan get() = exercise?.plan ?: ExercisePlan()

    /** Today's superset and its plan, when this exercise is in one. */
    val supersetContext: SupersetContext?
        get() = superset.takeIf { it.size >= 2 }?.let { members ->
            val first = dayEntry ?: members.first()
            SupersetContext(
                memberIds = members.map { it.exerciseId },
                transitionSeconds = first.transitionSeconds,
                roundRestSeconds = first.roundRestSeconds,
                rounds = members.firstNotNullOfOrNull { it.supersetRounds },
                dropOnLastRound = members.any { it.supersetDropLast },
                members = members.map { SupersetMember(it.exerciseId, it.memberRounds, it.memberDropSet) },
            )
        }

    /** The plan as today's superset shapes it: its sets are the rounds it does. */
    val activePlan: ExercisePlan get() = supersetContext?.planFor(exercise?.id ?: "", plan) ?: plan

    /** The planned drop set that is due now, if any. */
    val dueDrop: NextStep.DropSet?
        get() = exercise?.let { pendingDrop(activePlan, it.type, sets, settings.dropSetPercent, units) }
}

class ExerciseLogViewModel(
    savedStateHandle: SavedStateHandle,
    private val exerciseRepository: ExerciseRepository,
    private val workoutRepository: WorkoutRepository,
    settingsRepository: SettingsRepository,
    private val restTimer: RestTimer,
    private val intervalTimer: IntervalTimer,
    gameRepository: GameRepository,
    private val routineRepository: RoutineRepository,
    private val guidedWorkout: GuidedWorkout,
    private val painRepository: PainRepository,
) : ViewModel() {
    /** The tendon pain log, for this exercise's tendon check and the suggestions. */
    val painEntries: StateFlow<List<PainEntry>> =
        painRepository.entries.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Fills the fields with a suggested next set, keeping the side already chosen. */
    fun applySuggestion(values: SetValues) {
        val side = input.side
        updateInput(SetInput.from(values, uiState.value.units).copy(side = side ?: values.side))
    }

    /** Rates [tendons]' pain: a morning rating is for today, the others for the screen's day. */
    fun ratePain(tendons: List<Tendon>, moment: PainMoment, score: Int) {
        val day = if (moment == PainMoment.MORNING) LocalDate.now() else date
        viewModelScope.launch { tendons.forEach { painRepository.rate(it, day, moment, score) } }
    }

    /** The play button's guided workout, when one runs. */
    val guide: StateFlow<GuideState> = guidedWorkout.state

    /** All plans, to add this exercise to or remove it from. */
    val plans: StateFlow<List<Routine>> =
        routineRepository.routines.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The library's sections, to name this exercise's section. */
    val categories: StateFlow<List<Category>> =
        exerciseRepository.categories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val route = savedStateHandle.toRoute<ExerciseLogRoute>()
    val date: LocalDate = LocalDate.ofEpochDay(route.epochDay)
    val exerciseId: String = route.exerciseId

    var input by mutableStateOf(SetInput())
        private set

    /** The set being edited, or null when the buttons add a new set. */
    var selectedSetId by mutableStateOf<String?>(null)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    /** A message to celebrate with, such as a new record or an unlocked achievement. */
    var celebration by mutableStateOf<String?>(null)
        private set

    /** When on, new sets are saved as drop sets: lighter, right after the set before. */
    var dropMode by mutableStateOf(false)
        private set

    /** The weight to go back to when drop set mode is turned off by hand. */
    private var weightBeforeDrops: String? = null

    /** The superset exercise to open next, after a set was saved. */
    var switchTo by mutableStateOf<String?>(null)
        private set

    val timerState: StateFlow<RestTimerState> = restTimer.state

    val intervalState: StateFlow<IntervalTimerState> = intervalTimer.state

    /** The interval workout whose result was already filled in, so it is filled in once. */
    private var filledRunId = 0L

    val uiState: StateFlow<ExerciseLogUiState> = combine(
        exerciseRepository.observeExercise(exerciseId),
        workoutRepository.observeHistory(exerciseId),
        settingsRepository.settings,
        workoutRepository.observeDay(date),
    ) { exercise, history, settings, day ->
        val entry = day.firstOrNull { it.exerciseId == exerciseId }
        val superset = entry?.supersetId?.let { id -> day.filter { it.supersetId == id } }.orEmpty()
        ExerciseLogUiState(
            dayEntry = entry,
            superset = if (superset.size >= 2) superset else emptyList(),
            exercise = exercise,
            units = exercise?.unitsOr(settings.unitSystem) ?: settings.unitSystem,
            sets = history.firstOrNull { it.date == date }?.sets.orEmpty(),
            previousSession = history.firstOrNull { it.date < date },
            history = history,
            recordSetIds = exercise?.let { personalRecordSetIds(history, it.type) }.orEmpty(),
            settings = settings,
            isLoading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExerciseLogUiState())

    init {
        // Compare each new snapshot of the game stats with the previous one and celebrate what changed.
        viewModelScope.launch {
            var previous: GameStats? = null
            gameRepository.stats.collect { stats ->
                previous?.let { before -> celebrate(celebrationsBetween(before, stats)) }
                previous = stats
            }
        }
        viewModelScope.launch {
            val state = uiState.first { !it.isLoading }
            if (input == SetInput()) {
                // One-sided exercises start on the side still to do, else on the left.
                val side = if (state.exercise?.perSide == true) state.sets.sideDue() ?: Side.LEFT else null
                input = startingInput(state).copy(side = side)
            }
            // Coming back after the planned sets: the drop sets are next.
            state.dueDrop?.let { enterDrop(it, state.units) }
        }
        // A finished interval workout fills in the rounds and time, ready to save.
        viewModelScope.launch {
            intervalTimer.state.collect { timer ->
                if (timer.phase == IntervalPhase.DONE && timer.exerciseId == exerciseId && timer.runId != filledRunId) {
                    filledRunId = timer.runId
                    input = input.copy(
                        reps = timer.completedRounds.takeIf { it > 0 }?.toString().orEmpty(),
                        minutes = (timer.workoutSeconds / 60).toString(),
                        seconds = (timer.workoutSeconds % 60).toString(),
                    )
                }
            }
        }
    }

    /**
     * Where the fields start, like a gym notebook: today's last set, else the plan's reps and
     * weight, else last session's values.
     */
    private fun startingInput(state: ExerciseLogUiState): SetInput {
        val today = state.sets.lastOrNull { !it.values.isDropSet }
        if (today != null) return SetInput.from(today.values, state.units)
        val last = state.previousSession?.sets?.lastOrNull { !it.values.isDropSet }
        var start = last?.let { SetInput.from(it.values, state.units) } ?: SetInput()
        val plan = state.plan
        val type = state.exercise?.type
        if (plan.reps != null && type?.usesReps == true && type != ExerciseType.INTERVALS) {
            start = start.copy(reps = plan.reps.toString())
        }
        if (plan.weightKg != null && type?.usesWeight == true) {
            start = start.copy(weight = formatNumber(state.units.weightFromKg(plan.weightKg)))
        }
        return start
    }

    fun updateInput(value: SetInput) {
        input = value
        errorMessage = null
    }

    /**
     * Sets this exercise's own weight unit (null: the app's setting). A weight being entered is
     * converted between kg and lb; machine levels are not weights, so the number stays.
     */
    fun setWeightUnit(unit: UnitSystem?) {
        val state = uiState.value
        val exercise = state.exercise ?: return
        val from = state.units
        val to = unit ?: state.settings.unitSystem
        parseDecimal(input.weight)?.takeIf { from != UnitSystem.LEVELS && to != UnitSystem.LEVELS }?.let { value ->
            input = input.copy(weight = formatNumber(roundToPlates(from.weightToKg(value), to).let(to::weightFromKg)))
        }
        viewModelScope.launch { exerciseRepository.saveWeightUnit(exercise.id, unit) }
    }

    /** The time a hold lasted, from the hold timer. */
    fun setHeldSeconds(total: Int) = updateInput(input.copy(minutes = (total / 60).toString(), seconds = (total % 60).toString()))

    /** Which side the next set is (one-sided exercises). */
    fun chooseSide(side: Side) = updateInput(input.copy(side = side))

    fun adjustWeight(direction: Int) = updateInput(input.adjustWeight(direction, uiState.value.units))

    fun adjustReps(direction: Int) = updateInput(input.adjustReps(direction))

    /** Adds a new set, or updates the selected one. */
    fun save() {
        val state = uiState.value
        val exercise = state.exercise ?: return
        when (val result = input.toSetValues(exercise.type, state.units)) {
            is SetInput.Result.Invalid -> errorMessage = result.message
            is SetInput.Result.Valid -> {
                val editingId = selectedSetId
                selectedSetId = null
                if (editingId != null) {
                    // Editing keeps whether the set was a drop set.
                    val wasDrop = state.sets.firstOrNull { it.id == editingId }?.values?.isDropSet == true
                    viewModelScope.launch { workoutRepository.updateSet(editingId, result.values.copy(isDropSet = wasDrop)) }
                    return
                }
                val values = result.values.copy(isDropSet = dropMode && canUseDropSets(state))
                if (intervalTimer.state.value.let { it.phase == IntervalPhase.DONE && it.exerciseId == exerciseId }) {
                    intervalTimer.reset()
                }
                viewModelScope.launch {
                    val best = state.history.flatMap { it.sets }.mapNotNull { recordScore(it.values, exercise.type) }.maxOrNull()
                    val score = recordScore(values, exercise.type)
                    workoutRepository.addSet(date, exerciseId, values)
                    if (best != null && score != null && score > best) {
                        celebrate(listOf("⭐ New personal record! +${XpRules.PER_RECORD} XP"))
                    }
                    val guided = guidedWorkout.state.value.isActiveOn(date)
                    val target = if (guided) guidedWorkout.afterSetLogged(date) else null
                    afterNewSet(state, exercise, values, guided, target)
                }
            }
        }
    }

    /** Tapping a set loads it for editing; tapping it again goes back to adding sets. */
    fun selectSet(set: SetEntry) {
        errorMessage = null
        if (selectedSetId == set.id) {
            selectedSetId = null
        } else {
            selectedSetId = set.id
            input = SetInput.from(set.values, uiState.value.units)
        }
    }

    fun deleteSelectedSet() {
        val id = selectedSetId ?: return
        selectedSetId = null
        viewModelScope.launch { workoutRepository.deleteSet(id) }
    }

    fun clearInput() = updateInput(SetInput())

    /**
     * What happens after a new set (see [nextStep]): straight into a planned drop set with no rest,
     * else the walk to the next superset exercise, else the rest (the superset's after a round).
     */
    private fun afterNewSet(
        state: ExerciseLogUiState,
        exercise: Exercise,
        saved: SetValues,
        guided: Boolean = false,
        target: GuideTarget? = null,
    ) {
        // Drop set weights are worked out in this exercise's own unit (kg, lb or machine levels).
        val settings = state.settings.copy(unitSystem = state.units)
        // In a guided workout the rest ends with where to go next, and the screen moves there.
        val nextLabel = target?.let { "Next: ${it.name} · ${it.step}" }
        val goTo = target?.exerciseId?.takeIf { guided && it != exerciseId }
        if (guided && target == null) celebrate(listOf("🏁 Everything planned for today is done!"))
        val plan = exercise.plan
        val superset = state.supersetContext
        if (saved.isDropSet && !state.activePlan.dropSets) {
            // Drop sets by hand: lighter again for the next one. The rest runs in case this was the last.
            lowerWeightForDrop(state)
            if (settings.autoStartRestTimer) restTimer.start(settings.restTimerSeconds, label = nextLabel.takeIf { goTo != null })
            goTo?.let { switchTo = it }
            return
        }
        val setsNow = state.sets + SetEntry("new", saved)
        val step = nextStep(exerciseId, exercise.type, plan, setsNow, superset, settings)
        when (step) {
            is NextStep.DropSet -> {
                enterDrop(step, state.units)
                val reps = step.reps?.let { " × $it" }.orEmpty()
                celebrate(listOf("↘ Drop ${step.number} of ${step.of}: ${input.weight} ${state.units.weightUnit}$reps, no rest"))
            }
            is NextStep.Transition -> {
                leaveDrops()
                val nextName = state.superset.firstOrNull { it.exerciseId == step.exerciseId }?.exerciseName
                if (settings.autoStartRestTimer && step.seconds > 0) restTimer.start(step.seconds, label = "Go to $nextName")
                if (settings.supersetAutoAdvance) switchTo = step.exerciseId
                goTo?.let { switchTo = it }
            }
            is NextStep.Rest -> {
                leaveDrops()
                if (settings.autoStartRestTimer) restTimer.start(step.seconds, label = nextLabel.takeIf { goTo != null })
                val next = step.nextExerciseId
                if (settings.supersetAutoAdvance && next != null && next != exerciseId) switchTo = next
                goTo?.let { switchTo = it }
            }
            NextStep.Done -> {
                leaveDrops()
                goTo?.let { switchTo = it }
            }
            // Halfway through a one-sided set: the other side now, no rest, same exercise.
            is NextStep.OtherSide -> {
                restTimer.stop()
                input = input.copy(side = step.side)
                celebrate(listOf("Now the ${step.side.label.lowercase()} side"))
            }
        }
        // A full one-sided set is done: the next one starts on the left again.
        if (exercise.perSide && step !is NextStep.OtherSide) input = input.copy(side = Side.LEFT)
    }

    /** Drop set mode with the drop's weight and reps filled in. */
    private fun enterDrop(drop: NextStep.DropSet, units: UnitSystem) {
        if (!dropMode) weightBeforeDrops = input.weight
        dropMode = true
        input = input.copy(
            weight = formatNumber(units.weightFromKg(drop.weightKg)),
            reps = drop.reps?.toString() ?: input.reps,
        )
    }

    /** Back to normal sets, at the weight used before the drops. */
    private fun leaveDrops() {
        if (!dropMode) return
        dropMode = false
        weightBeforeDrops?.let { input = input.copy(weight = it) }
        weightBeforeDrops = null
    }

    fun canUseDropSets(state: ExerciseLogUiState = uiState.value): Boolean =
        state.exercise?.type == ExerciseType.WEIGHT_REPS && (state.settings.dropSetsEnabled || state.activePlan.dropSets)

    /** Turns drop set mode on (lowering the weight right away) or off (back to the weight before). */
    fun toggleDropMode() {
        if (dropMode) {
            leaveDrops()
        } else {
            weightBeforeDrops = input.weight
            dropMode = true
            lowerWeightForDrop(uiState.value)
        }
    }

    private fun lowerWeightForDrop(state: ExerciseLogUiState) {
        val current = parseDecimal(input.weight) ?: return
        val kg = state.plan.nextDropKg(state.units.weightToKg(current), state.settings.dropSetPercent, state.units)
        updateInput(input.copy(weight = formatNumber(state.units.weightFromKg(kg))))
    }

    /** Saves the exercise's set plan; if it makes a drop set due right now, drop set mode starts. */
    fun savePlan(plan: ExercisePlan) {
        viewModelScope.launch {
            exerciseRepository.savePlan(exerciseId, plan)
            val state = uiState.value
            val type = state.exercise?.type ?: return@launch
            pendingDrop(plan, type, state.sets, state.settings.dropSetPercent, state.units)?.let { enterDrop(it, state.units) }
        }
    }

    fun clearPlan() = savePlan(uiState.value.plan.withoutSetPlan())

    fun consumeSwitch() {
        switchTo = null
    }

    fun setInPlan(planId: String, inPlan: Boolean) {
        viewModelScope.launch {
            if (inPlan) {
                routineRepository.addExercise(planId, exerciseId)
            } else {
                routineRepository.removeExerciseFromRoutine(planId, exerciseId)
            }
        }
    }

    fun createPlanWithExercise(name: String) {
        viewModelScope.launch { routineRepository.addExercise(routineRepository.createRoutine(name), exerciseId) }
    }

    fun consumeCelebration() {
        celebration = null
    }

    private fun celebrate(messages: List<String>) {
        if (messages.isEmpty()) return
        celebration = (listOfNotNull(celebration) + messages).joinToString("\n")
    }

    // Guided workout

    fun pauseGuide() {
        restTimer.stop()
        guidedWorkout.pause()
    }

    fun resumeGuide() = guidedWorkout.resume()

    fun skipInGuide() {
        guidedWorkout.skip(exerciseId)
        viewModelScope.launch {
            guidedWorkout.afterSetLogged(date)?.let { switchTo = it.exerciseId }
        }
    }

    fun stopGuide() = guidedWorkout.stop()

    fun startTimer(seconds: Int) = restTimer.start(seconds)

    fun stopTimer() = restTimer.stop()

    fun addTimerSeconds(delta: Int) = restTimer.addSeconds(delta)

    // HIIT

    fun startIntervals() {
        val plan = uiState.value.plan
        restTimer.stop()
        intervalTimer.start(plan.intervalHighSeconds, plan.intervalLowSeconds, plan.intervalRounds, exerciseId)
    }

    fun pauseIntervals() = intervalTimer.pause()

    fun resumeIntervals() = intervalTimer.resume()

    fun finishIntervals() = intervalTimer.finish()

    fun resetIntervals() = intervalTimer.reset()

    /** Changes the high or low seconds (by 5) or the rounds (by 1), and remembers them for next time. */
    fun changeIntervals(highDelta: Int = 0, lowDelta: Int = 0, roundsDelta: Int = 0) {
        val plan = uiState.value.plan
        val changed = plan.copy(
            highSeconds = (plan.intervalHighSeconds + highDelta).coerceIn(5, MAX_INTERVAL_SECONDS),
            lowSeconds = (plan.intervalLowSeconds + lowDelta).coerceIn(0, MAX_INTERVAL_SECONDS),
            rounds = (plan.intervalRounds + roundsDelta).coerceIn(1, MAX_ROUNDS),
        )
        viewModelScope.launch { exerciseRepository.savePlan(exerciseId, changed) }
    }

    // Description and links

    fun saveDescription(text: String) {
        viewModelScope.launch { exerciseRepository.saveNotes(exerciseId, text) }
    }

    fun addLink(link: ExerciseLink) {
        val links = uiState.value.exercise?.links.orEmpty()
        if (links.any { it.url == link.url }) return
        viewModelScope.launch { exerciseRepository.saveLinks(exerciseId, links + link) }
    }

    fun removeLink(link: ExerciseLink) {
        val links = uiState.value.exercise?.links.orEmpty()
        viewModelScope.launch { exerciseRepository.saveLinks(exerciseId, links - link) }
    }

    companion object {
        const val MAX_INTERVAL_SECONDS = 600
        const val MAX_ROUNDS = 99

        val Factory = appViewModelFactory { container ->
            ExerciseLogViewModel(
                savedStateHandle = createSavedStateHandle(),
                exerciseRepository = container.exerciseRepository,
                workoutRepository = container.workoutRepository,
                settingsRepository = container.settingsRepository,
                restTimer = container.restTimer,
                intervalTimer = container.intervalTimer,
                gameRepository = container.gameRepository,
                routineRepository = container.routineRepository,
                guidedWorkout = container.guidedWorkout,
                painRepository = container.painRepository,
            )
        }
    }
}
