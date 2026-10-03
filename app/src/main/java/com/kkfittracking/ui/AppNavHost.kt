package com.kkfittracking.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.kkfittracking.ui.analysis.AnalysisScreen
import com.kkfittracking.ui.load.LoadScreen
import com.kkfittracking.ui.body.BodyMetricScreen
import com.kkfittracking.ui.body.BodyScreen
import com.kkfittracking.ui.calendar.CalendarScreen
import com.kkfittracking.ui.exercises.EditExerciseScreen
import com.kkfittracking.ui.exercises.ExercisePickerScreen
import com.kkfittracking.ui.game.AchievementsScreen
import com.kkfittracking.ui.log.ExerciseLogScreen
import com.kkfittracking.ui.routines.RoutineScreen
import com.kkfittracking.ui.routines.RoutinesScreen
import com.kkfittracking.ui.settings.SettingsScreen
import com.kkfittracking.ui.supersets.SupersetsScreen
import com.kkfittracking.ui.workout.WorkoutScreen
import com.kkfittracking.ui.workout.WorkoutViewModel
import kotlinx.serialization.Serializable

@Serializable
object WorkoutRoute

/**
 * Picks an exercise to log on a day, or to add to a routine when [routineId] is set, or several
 * exercises to group as a superset on the day when [superset] is true.
 */
@Serializable
data class ExercisePickerRoute(val epochDay: Long = 0, val routineId: String? = null, val superset: Boolean = false)

@Serializable
data class EditExerciseRoute(val exerciseId: String? = null)

@Serializable
data class ExerciseLogRoute(val epochDay: Long, val exerciseId: String)

@Serializable
data class CalendarRoute(val epochDay: Long)

@Serializable
object SettingsRoute

@Serializable
object RoutinesRoute

@Serializable
data class RoutineRoute(val routineId: String)

/** "Superset edit" for the plan [planId], or else for the day [epochDay]. */
@Serializable
data class SupersetsRoute(val epochDay: Long = 0, val planId: String? = null)

@Serializable
object AchievementsRoute

/** Graphs and records, showing [exerciseId] first when set. */
@Serializable
data class AnalysisRoute(val exerciseId: String? = null)

@Serializable
object LoadRoute

@Serializable
object BodyRoute

@Serializable
data class BodyMetricRoute(val metric: String)

@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController(),
    /** An exercise to open over the day, e.g. from the guided workout's notification. */
    openRequest: ExerciseLogRoute? = null,
    onOpened: () -> Unit = {},
) {
    LaunchedEffect(openRequest) {
        openRequest ?: return@LaunchedEffect
        navController.navigate(openRequest) {
            popUpTo<WorkoutRoute>()
            launchSingleTop = true
        }
        onOpened()
    }
    NavHost(navController = navController, startDestination = WorkoutRoute) {
        composable<WorkoutRoute> {
            WorkoutScreen(
                onAddExercise = { date -> navController.navigate(ExercisePickerRoute(date.toEpochDay())) },
                onOpenExercise = { date, exerciseId ->
                    navController.navigate(ExerciseLogRoute(date.toEpochDay(), exerciseId))
                },
                onOpenCalendar = { date -> navController.navigate(CalendarRoute(date.toEpochDay())) },
                onOpenSettings = { navController.navigate(SettingsRoute) },
                onOpenRoutines = { navController.navigate(RoutinesRoute) },
                onOpenBody = { navController.navigate(BodyRoute) },
                onOpenAchievements = { navController.navigate(AchievementsRoute) },
                onOpenAnalysis = { navController.navigate(AnalysisRoute()) },
                onOpenLoad = { navController.navigate(LoadRoute) },
                onNewSuperset = { date -> navController.navigate(ExercisePickerRoute(date.toEpochDay(), superset = true)) },
                onSupersets = { date -> navController.navigate(SupersetsRoute(epochDay = date.toEpochDay())) },
            )
        }
        composable<ExercisePickerRoute> { entry ->
            val route = entry.toRoute<ExercisePickerRoute>()
            ExercisePickerScreen(
                onBack = { navController.popBackStack() },
                onLogExercise = { exerciseId ->
                    navController.navigate(ExerciseLogRoute(route.epochDay, exerciseId)) {
                        popUpTo<ExercisePickerRoute> { inclusive = true }
                    }
                },
                onCreateExercise = { navController.navigate(EditExerciseRoute()) },
                onEditExercise = { exerciseId -> navController.navigate(EditExerciseRoute(exerciseId)) },
                onSupersetCreated = { firstExerciseId ->
                    navController.navigate(ExerciseLogRoute(route.epochDay, firstExerciseId)) {
                        popUpTo<ExercisePickerRoute> { inclusive = true }
                    }
                },
            )
        }
        composable<EditExerciseRoute> {
            EditExerciseScreen(
                onBack = { navController.popBackStack() },
                onDeleted = {
                    // Leave the deleted exercise's log screen too, when editing started there.
                    val previous = navController.previousBackStackEntry?.destination
                    val cameFromLog = previous?.hasRoute<ExerciseLogRoute>() == true
                    if (cameFromLog) {
                        navController.popBackStack<ExerciseLogRoute>(inclusive = true)
                    } else {
                        navController.popBackStack()
                    }
                },
            )
        }
        composable<ExerciseLogRoute> { entry ->
            val route = entry.toRoute<ExerciseLogRoute>()
            ExerciseLogScreen(
                onBack = { navController.popBackStack() },
                onEditExercise = { exerciseId -> navController.navigate(EditExerciseRoute(exerciseId)) },
                onSwitchExercise = { exerciseId ->
                    // Swap to the other superset exercise, so Back still returns to the day.
                    navController.navigate(ExerciseLogRoute(route.epochDay, exerciseId)) {
                        popUpTo<ExerciseLogRoute> { inclusive = true }
                    }
                },
            )
        }
        composable<CalendarRoute> {
            CalendarScreen(
                onBack = { navController.popBackStack() },
                onDateSelected = { date ->
                    navController.previousBackStackEntry?.savedStateHandle
                        ?.set(WorkoutViewModel.KEY_EPOCH_DAY, date.toEpochDay())
                    navController.popBackStack()
                },
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable<RoutinesRoute> {
            RoutinesScreen(
                onBack = { navController.popBackStack() },
                onOpenRoutine = { routineId -> navController.navigate(RoutineRoute(routineId)) },
            )
        }
        composable<RoutineRoute> {
            RoutineScreen(
                onBack = { navController.popBackStack() },
                onAddExercise = { routineId -> navController.navigate(ExercisePickerRoute(routineId = routineId)) },
                onSupersets = { routineId -> navController.navigate(SupersetsRoute(planId = routineId)) },
            )
        }
        composable<SupersetsRoute> {
            SupersetsScreen(onDone = { navController.popBackStack() })
        }
        composable<LoadRoute> {
            LoadScreen(onBack = { navController.popBackStack() })
        }
        composable<AnalysisRoute> {
            AnalysisScreen(onBack = { navController.popBackStack() })
        }
        composable<AchievementsRoute> {
            AchievementsScreen(onBack = { navController.popBackStack() })
        }
        composable<BodyRoute> {
            BodyScreen(
                onBack = { navController.popBackStack() },
                onOpenMetric = { metric -> navController.navigate(BodyMetricRoute(metric.name)) },
            )
        }
        composable<BodyMetricRoute> {
            BodyMetricScreen(onBack = { navController.popBackStack() })
        }
    }
}
