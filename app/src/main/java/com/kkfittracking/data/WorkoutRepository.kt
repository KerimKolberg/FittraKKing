package com.kkfittracking.data

import androidx.room.withTransaction
import com.kkfittracking.data.db.AppDatabase
import com.kkfittracking.data.db.DayRow
import com.kkfittracking.data.db.WorkoutEntity
import com.kkfittracking.data.db.WorkoutExerciseEntity
import com.kkfittracking.data.db.WorkoutSetEntity
import com.kkfittracking.model.ArrangedExercise
import com.kkfittracking.model.Block
import com.kkfittracking.model.DayExercise
import com.kkfittracking.model.ExercisePlan
import com.kkfittracking.model.HistorySession
import com.kkfittracking.model.MAX_SUPERSET_SIZE
import com.kkfittracking.model.PlannedExercise
import com.kkfittracking.model.SetEntry
import com.kkfittracking.model.SetValues
import com.kkfittracking.model.Side
import com.kkfittracking.model.TrainingStyle
import com.kkfittracking.model.UnitSystem
import com.kkfittracking.model.groupSupersets
import com.kkfittracking.model.historiesOf
import com.kkfittracking.model.unfinishedPart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.util.UUID

class WorkoutRepository(
    private val database: AppDatabase,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val dao = database.workoutDao()

    /** The exercises logged on [date], in the order they were first logged. */
    fun observeDay(date: LocalDate): Flow<List<DayExercise>> =
        dao.observeDay(date).map { groupDayRows(it) }

    /** Every session of an exercise, newest first. */
    fun observeHistory(exerciseId: String): Flow<List<HistorySession>> =
        dao.observeHistory(exerciseId).map { rows ->
            rows.groupBy { it.date }.map { (date, dateRows) ->
                HistorySession(date = date, sets = dateRows.map { it.set.toSetEntry() })
            }
        }

    /** Every exercise's history at once (newest session first), for the graphs and records. */
    fun observeAllHistory(): Flow<Map<String, List<HistorySession>>> =
        dao.observeAllSets().map { rows -> historiesOf(rows.map { Triple(it.date, it.exerciseId, it.set.toSetEntry()) }) }

    /** The dates between [from] and [to] (inclusive) that have at least one logged set. */
    fun observeWorkoutDates(from: LocalDate, to: LocalDate): Flow<Set<LocalDate>> =
        dao.observeWorkoutDates(from, to).map { it.toSet() }

    /** Logs a new set, creating the day's workout and the exercise entry when needed. */
    suspend fun addSet(date: LocalDate, exerciseId: String, values: SetValues): String =
        database.withTransaction {
            val time = now()
            val workoutExercise = getOrCreateWorkoutExercise(getOrCreateWorkout(date, time), exerciseId, time)
            val set = WorkoutSetEntity(
                id = newId(),
                workoutExerciseId = workoutExercise.id,
                sortOrder = (dao.maxSetSortOrder(workoutExercise.id) ?: -1) + 1,
                weightKg = values.weightKg,
                reps = values.reps,
                distanceMeters = values.distanceMeters,
                durationSeconds = values.durationSeconds,
                comment = values.note,
                rpe = values.rpe,
                isDropSet = values.isDropSet,
                side = values.side?.name.orEmpty(),
                createdAt = time,
                updatedAt = time,
            )
            dao.insertSet(set)
            set.id
        }

    suspend fun updateSet(setId: String, values: SetValues) {
        val existing = dao.getSet(setId) ?: return
        dao.updateSet(
            existing.copy(
                weightKg = values.weightKg,
                reps = values.reps,
                distanceMeters = values.distanceMeters,
                durationSeconds = values.durationSeconds,
                rpe = values.rpe,
                comment = values.note,
                isDropSet = values.isDropSet,
                side = values.side?.name.orEmpty(),
                updatedAt = now(),
            ),
        )
    }

    /** Deletes a set. When it was the exercise's last set that day, the exercise leaves the day too. */
    suspend fun deleteSet(setId: String) {
        database.withTransaction {
            val set = dao.getSet(setId) ?: return@withTransaction
            val time = now()
            dao.softDeleteSet(setId, time)
            if (dao.countSets(set.workoutExerciseId) == 0) {
                dao.softDeleteWorkoutExercise(set.workoutExerciseId, time)
            }
        }
    }

    /**
     * Adds exercises to a day without sets, as a plan to fill in (from a routine or an earlier
     * workout). Exercises already on that day are skipped.
     */
    suspend fun addExercisesToDay(date: LocalDate, exerciseIds: List<String>) =
        addPlannedExercises(date, exerciseIds.map { PlannedExercise(it) }, withSupersets = false)

    /**
     * Puts a warm-up at the top of a day: its exercises come before the ones already there, in
     * order. Exercises already on the day stay where they are.
     */
    suspend fun addWarmUp(date: LocalDate, exerciseIds: List<String>) = database.withTransaction {
        val time = now()
        val workout = getOrCreateWorkout(date, time)
        val fresh = exerciseIds.distinct().filter { dao.getWorkoutExercise(workout.id, it) == null }
        val first = (dao.minExerciseSortOrder(workout.id) ?: 0) - fresh.size
        fresh.forEachIndexed { index, exerciseId ->
            dao.insertWorkoutExercise(
                WorkoutExerciseEntity(
                    id = newId(), workoutId = workout.id, exerciseId = exerciseId, sortOrder = first + index,
                    createdAt = time, updatedAt = time,
                ),
            )
        }
    }

    /**
     * Adds a plan's exercises to a day, ready to be filled in. With [withSupersets], the plan's
     * supersets are grouped on the day too (with their timing), each under a new superset id so
     * they can be ungrouped on that day alone. Exercises already on the day are reused.
     */
    suspend fun addPlannedExercises(date: LocalDate, exercises: List<PlannedExercise>, withSupersets: Boolean) {
        if (exercises.isEmpty()) return
        database.withTransaction {
            val time = now()
            val workout = getOrCreateWorkout(date, time)
            val entries = exercises.distinctBy { it.exerciseId }
                .associate { it.exerciseId to getOrCreateWorkoutExercise(workout, it.exerciseId, time) }
            if (!withSupersets) return@withTransaction
            groupSupersets(exercises.distinctBy { it.exerciseId }) { it.supersetId }.forEach { block ->
                if (block !is Block.Superset) return@forEach
                val members = block.items.take(MAX_SUPERSET_SIZE)
                dao.setSuperset(
                    ids = members.mapNotNull { entries[it.exerciseId]?.id },
                    supersetId = newId(),
                    transitionSeconds = members.firstNotNullOfOrNull { it.transitionSeconds },
                    roundRestSeconds = members.firstNotNullOfOrNull { it.roundRestSeconds },
                    now = time,
                )
                val rounds = members.firstNotNullOfOrNull { it.supersetRounds }
                members.forEach { member ->
                    val id = entries[member.exerciseId]?.id ?: return@forEach
                    dao.setSupersetPlan(id, rounds, members.any { it.supersetDropLast }, member.memberRounds, member.memberDropSet, time)
                }
            }
        }
    }

    private suspend fun getOrCreateWorkout(date: LocalDate, time: Long): WorkoutEntity =
        dao.getWorkoutForDate(date) ?: WorkoutEntity(
            id = newId(),
            date = date,
            comment = "",
            createdAt = time,
            updatedAt = time,
        ).also { dao.insertWorkout(it) }

    private suspend fun getOrCreateWorkoutExercise(
        workout: WorkoutEntity,
        exerciseId: String,
        time: Long,
    ): WorkoutExerciseEntity =
        dao.getWorkoutExercise(workout.id, exerciseId) ?: WorkoutExerciseEntity(
            id = newId(),
            workoutId = workout.id,
            exerciseId = exerciseId,
            sortOrder = (dao.maxExerciseSortOrder(workout.id) ?: -1) + 1,
            createdAt = time,
            updatedAt = time,
        ).also { dao.insertWorkoutExercise(it) }

    /**
     * Groups exercises into a superset on [date], adding any that are not on that day yet. Exercises
     * already in another superset leave it. Returns the new superset id.
     */
    suspend fun createSuperset(
        date: LocalDate,
        exerciseIds: List<String>,
        transitionSeconds: Int? = null,
        roundRestSeconds: Int? = null,
    ): String {
        val ids = exerciseIds.distinct()
        require(ids.size in 2..MAX_SUPERSET_SIZE) { "A superset has 2 to $MAX_SUPERSET_SIZE exercises" }
        return database.withTransaction {
            val time = now()
            val workout = getOrCreateWorkout(date, time)
            val entries = ids.map { getOrCreateWorkoutExercise(workout, it, time) }
            val supersetId = newId()
            dao.setSuperset(entries.map { it.id }, supersetId, transitionSeconds, roundRestSeconds, time)
            supersetId
        }
    }

    /** Saves the order and supersets chosen on the "Superset edit" screen. */
    suspend fun arrangeDay(exercises: List<ArrangedExercise>) {
        database.withTransaction {
            val time = now()
            exercises.forEach {
                dao.arrange(
                    id = it.id,
                    sortOrder = it.sortOrder,
                    supersetId = it.supersetId,
                    transitionSeconds = it.transitionSeconds,
                    roundRestSeconds = it.roundRestSeconds,
                    supersetRounds = it.supersetRounds,
                    supersetDropLast = it.supersetDropLast,
                    memberRounds = it.memberRounds,
                    memberDropSet = it.memberDropSet,
                    now = time,
                )
            }
        }
    }

    /** Turns a superset back into separate exercises. */
    suspend fun ungroupSuperset(supersetId: String) {
        dao.clearSuperset(supersetId, now())
    }

    /**
     * Moves what is left of [from] to [to] (see [unfinishedPart]): the unfinished exercises with
     * their supersets are added there; those not started leave [from]. Returns how many moved.
     */
    suspend fun moveUnfinished(from: LocalDate, to: LocalDate): Int {
        if (from == to) return 0
        val move = unfinishedPart(observeDay(from).first())
        if (move.toAdd.isEmpty()) return 0
        addPlannedExercises(to, move.toAdd, withSupersets = true)
        if (move.toRemove.isNotEmpty()) deleteWorkoutExercises(move.toRemove)
        return move.toAdd.size
    }

    /** Removes several exercises and all their sets from a day at once. */
    suspend fun deleteWorkoutExercises(workoutExerciseIds: List<String>) {
        if (workoutExerciseIds.isEmpty()) return
        database.withTransaction {
            val time = now()
            dao.softDeleteSetsOfAll(workoutExerciseIds, time)
            dao.softDeleteWorkoutExercises(workoutExerciseIds, time)
        }
    }

    /** Removes an exercise and all its sets from a day. */
    suspend fun deleteWorkoutExercise(workoutExerciseId: String) {
        database.withTransaction {
            val time = now()
            dao.softDeleteSetsOf(workoutExerciseId, time)
            dao.softDeleteWorkoutExercise(workoutExerciseId, time)
        }
    }
}

private fun groupDayRows(rows: List<DayRow>): List<DayExercise> =
    rows.groupBy { it.workoutExerciseId }.map { (workoutExerciseId, exerciseRows) ->
        val first = exerciseRows.first()
        DayExercise(
            workoutExerciseId = workoutExerciseId,
            exerciseId = first.exerciseId,
            exerciseName = first.exerciseName,
            exerciseType = first.exerciseType,
            categoryColor = first.categoryColor,
            supersetId = first.supersetId,
            transitionSeconds = first.transitionSeconds,
            roundRestSeconds = first.roundRestSeconds,
            supersetRounds = first.supersetRounds,
            supersetDropLast = first.supersetDropLast,
            memberRounds = first.memberRounds,
            memberDropSet = first.memberDropSet,
            plan = ExercisePlan.fromJson(first.exercisePlan),
            weightUnits = UnitSystem.entries.firstOrNull { it.name == first.exerciseWeightUnit },
            perSide = first.exercisePerSide,
            styles = TrainingStyle.resolveAll(first.exerciseStyle, BuiltInExercises.regionKeyOf(first.exerciseCategoryId), first.exerciseType),
            sets = exerciseRows.mapNotNull { row ->
                row.setId?.let { id ->
                    SetEntry(
                        id = id,
                        values = SetValues(
                            weightKg = row.weightKg,
                            reps = row.reps,
                            distanceMeters = row.distanceMeters,
                            durationSeconds = row.durationSeconds,
                            rpe = row.rpe,
                            note = row.comment.orEmpty(),
                            isDropSet = row.isDropSet == true,
                            side = Side.of(row.side),
                        ),
                        loggedAtMillis = row.setCreatedAt,
                    )
                }
            },
        )
    }

private fun WorkoutSetEntity.toSetEntry() = SetEntry(
    id = id,
    values = SetValues(
        weightKg = weightKg,
        reps = reps,
        distanceMeters = distanceMeters,
        durationSeconds = durationSeconds,
        rpe = rpe,
        note = comment,
        isDropSet = isDropSet,
        side = Side.of(side),
    ),
    loggedAtMillis = createdAt,
)
