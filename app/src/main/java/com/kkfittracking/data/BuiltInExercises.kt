package com.kkfittracking.data

import com.kkfittracking.model.ExercisePlan
import com.kkfittracking.model.ExerciseType
import com.kkfittracking.model.ExerciseType.DISTANCE_TIME
import com.kkfittracking.model.ExerciseType.INTERVALS
import com.kkfittracking.model.ExerciseType.REPS
import com.kkfittracking.model.ExerciseType.REPS_HEIGHT
import com.kkfittracking.model.ExerciseType.SESSION
import com.kkfittracking.model.ExerciseType.TIME
import com.kkfittracking.model.ExerciseType.TIME_WEIGHT
import com.kkfittracking.model.ExerciseType.WEIGHT_REPS
import com.kkfittracking.model.Muscle
import com.kkfittracking.model.Muscle.ABS
import com.kkfittracking.model.Muscle.ADDUCTORS
import com.kkfittracking.model.Muscle.BICEPS
import com.kkfittracking.model.Muscle.CALVES
import com.kkfittracking.model.Muscle.CHEST
import com.kkfittracking.model.Muscle.FEET
import com.kkfittracking.model.Muscle.FOREARMS
import com.kkfittracking.model.Muscle.FRONT_DELTS
import com.kkfittracking.model.Muscle.FULL_BODY
import com.kkfittracking.model.Muscle.GLUTES
import com.kkfittracking.model.Muscle.HAMSTRINGS
import com.kkfittracking.model.Muscle.HIPS
import com.kkfittracking.model.Muscle.LATS
import com.kkfittracking.model.Muscle.LOWER_BACK
import com.kkfittracking.model.Muscle.MIND
import com.kkfittracking.model.Muscle.NECK
import com.kkfittracking.model.Muscle.OBLIQUES
import com.kkfittracking.model.Muscle.QUADS
import com.kkfittracking.model.Muscle.REAR_DELTS
import com.kkfittracking.model.Muscle.ROTATOR_CUFF
import com.kkfittracking.model.Muscle.SHINS
import com.kkfittracking.model.Muscle.SIDE_DELTS
import com.kkfittracking.model.Muscle.TRICEPS
import com.kkfittracking.model.Muscle.UPPER_BACK
import com.kkfittracking.model.Muscle.WHOLE_LEGS
import com.kkfittracking.model.Regions
import com.kkfittracking.model.TrainingStyle
import com.kkfittracking.model.TrainingStyle.CARDIO
import com.kkfittracking.model.TrainingStyle.DANCE
import com.kkfittracking.model.TrainingStyle.ECCENTRIC
import com.kkfittracking.model.TrainingStyle.HIIT
import com.kkfittracking.model.TrainingStyle.ISOMETRIC
import com.kkfittracking.model.TrainingStyle.MEDITATION
import com.kkfittracking.model.TrainingStyle.MOBILITY
import com.kkfittracking.model.TrainingStyle.PLYOMETRIC
import com.kkfittracking.model.TrainingStyle.SPORT
import com.kkfittracking.model.TrainingStyle.STRENGTH
import com.kkfittracking.model.TrainingStyle.STRETCHING
import com.kkfittracking.model.TrainingStyle.YOGA
import java.util.UUID

/**
 * The body sections and exercises shipped with the app. The library has three levels: section
 * (Legs), muscle (Hamstrings) and training style (Eccentric), e.g. Legs → Hamstrings → Eccentric →
 * Nordic Hamstring Curl.
 *
 * Ids are derived from the names, so the same built-in exercise has the same id on every device.
 * That keeps a future cloud sync from creating duplicates. A key must never change: if a built-in
 * name is ever reworded, pass its old key explicitly so the id stays the same.
 */
object BuiltInExercises {
    data class Region(val key: String, val name: String, val color: Int) {
        val id: String get() = stableId("category", key)
    }

    data class BuiltInExercise(
        val key: String,
        val name: String,
        /** Every muscle it trains, the main one first. */
        val muscles: List<Muscle>,
        /** Every way it trains, the main one first. */
        val styles: List<TrainingStyle>,
        val type: ExerciseType = WEIGHT_REPS,
        val tempo: String = "",
        val perSide: Boolean = false,
        /** The plan a new install starts with, e.g. the interval timings of Tabata. */
        val plan: ExercisePlan = ExercisePlan(),
        /** The section key it was in before a later version moved it, if it moved. */
        val movedFrom: String? = null,
    ) {
        val id: String get() = stableId("exercise", key)
        val muscle: Muscle get() = muscles.first()
        val style: TrainingStyle get() = styles.first()

        /** The body section it is filed in: that of its main muscle. */
        val regionKey: String get() = muscle.regionKey
    }

    /**
     * A category of earlier versions, now split over the sections. Built-in exercises move to their
     * place in the catalog; the user's own exercises move to this fallback.
     */
    data class RetiredCategory(val key: String, val muscle: Muscle, val style: TrainingStyle) {
        val id: String get() = stableId("category", key)
    }

    fun stableId(kind: String, key: String): String =
        UUID.nameUUIDFromBytes("builtin:$kind:$key".toByteArray()).toString()

    /** The key of a built-in exercise, derived from its name. */
    fun keyOf(name: String): String = name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

    val regions: List<Region> = listOf(
        Region(Regions.CHEST, "Chest", 0xFFE53935.toInt()),
        Region(Regions.BACK, "Back", 0xFF43A047.toInt()),
        Region(Regions.SHOULDERS, "Shoulders", 0xFF8E24AA.toInt()),
        Region(Regions.ARMS, "Arms", 0xFF3949AB.toInt()),
        Region(Regions.LEGS, "Legs", 0xFFFB8C00.toInt()),
        Region(Regions.CORE, "Core", 0xFF00897B.toInt()),
        Region(Regions.FULL_BODY, "Full body", 0xFF1E88E5.toInt()),
        Region(Regions.CARDIO, "Cardio", 0xFF6D4C41.toInt()),
        Region(Regions.SPORTS, "Sports", 0xFF546E7A.toInt()),
        Region(Regions.MIND, "Mind & recovery", 0xFF26A69A.toInt()),
    )

    val retiredCategories: List<RetiredCategory> = listOf(
        RetiredCategory("triceps", TRICEPS, STRENGTH),
        RetiredCategory("biceps", BICEPS, STRENGTH),
        RetiredCategory("mobility", FULL_BODY, MOBILITY),
        RetiredCategory("stretching", FULL_BODY, STRETCHING),
        RetiredCategory("isometrics", FULL_BODY, ISOMETRIC),
        RetiredCategory("tendons", FULL_BODY, ECCENTRIC),
        RetiredCategory("plyometrics", FULL_BODY, PLYOMETRIC),
    )

    private class Entry(
        val name: String,
        val type: ExerciseType,
        val tempo: String,
        val perSide: Boolean,
        val plan: ExercisePlan,
        val also: List<Muscle>,
        val alsoStyles: List<TrainingStyle>,
        val movedFrom: String?,
    )

    /** [also] lists the other muscles it trains, besides the main one of its group. */
    private fun e(
        name: String,
        type: ExerciseType = WEIGHT_REPS,
        tempo: String = "",
        perSide: Boolean = false,
        plan: ExercisePlan = ExercisePlan(),
        also: List<Muscle> = emptyList(),
        alsoStyles: List<TrainingStyle> = emptyList(),
        movedFrom: String? = null,
    ) = Entry(name, type, tempo, perSide, plan, also, alsoStyles, movedFrom)

    private fun intervals(high: Int, low: Int, rounds: Int) =
        ExercisePlan(highSeconds = high, lowSeconds = low, rounds = rounds)

    /** Exercises whose main muscle is [muscle], trained the [style] way. */
    private fun MutableList<BuiltInExercise>.group(muscle: Muscle, style: TrainingStyle, vararg entries: Entry) {
        entries.forEach {
            val muscles = (listOf(muscle) + it.also).distinct()
            val styles = (listOf(style) + it.alsoStyles).distinct()
            add(BuiltInExercise(keyOf(it.name), it.name, muscles, styles, it.type, it.tempo, it.perSide, it.plan, it.movedFrom))
        }
    }

    val exercises: List<BuiltInExercise> = buildList {
        // Chest
        group(
            CHEST, STRENGTH,
            e("Flat Barbell Bench Press", also = listOf(TRICEPS, FRONT_DELTS)),
            e("Flat Dumbbell Bench Press", also = listOf(TRICEPS, FRONT_DELTS)),
            e("Incline Barbell Bench Press", also = listOf(FRONT_DELTS, TRICEPS)),
            e("Incline Dumbbell Bench Press", also = listOf(FRONT_DELTS, TRICEPS)),
            e("Decline Barbell Bench Press", also = listOf(TRICEPS)),
            e("Machine Chest Press", also = listOf(TRICEPS, FRONT_DELTS)), e("Flat Dumbbell Fly"), e("Cable Crossover"),
            e("Push Up", REPS, also = listOf(TRICEPS, FRONT_DELTS)),
        )
        group(CHEST, ISOMETRIC, e("Push-Up Hold", TIME, also = listOf(TRICEPS, ABS)))
        group(CHEST, ECCENTRIC, e("Eccentric Push Up", REPS, tempo = "4-0-1-0", also = listOf(TRICEPS, FRONT_DELTS)))
        group(CHEST, PLYOMETRIC, e("Clap Push Up", REPS, also = listOf(TRICEPS, FRONT_DELTS)))
        group(CHEST, STRETCHING, e("Doorway Chest Stretch", TIME, also = listOf(FRONT_DELTS)))

        // Back
        group(
            LATS, STRENGTH,
            e("Lat Pulldown", also = listOf(BICEPS)), e("Pull Up", REPS, also = listOf(BICEPS, UPPER_BACK)),
            e("Chin Up", REPS, also = listOf(BICEPS)), e("One-Arm Dumbbell Row", also = listOf(UPPER_BACK, BICEPS)),
        )
        group(LATS, ISOMETRIC, e("Flexed-Arm Hang", TIME, also = listOf(BICEPS, FOREARMS)))
        group(LATS, ECCENTRIC, e("Eccentric Pull Up", REPS, tempo = "5-0-1-0", also = listOf(BICEPS)))
        group(LATS, STRETCHING, e("Dead Hang", TIME, also = listOf(FOREARMS)))
        group(
            UPPER_BACK, STRENGTH,
            e("Barbell Row", also = listOf(LATS, BICEPS)), e("T-Bar Row", also = listOf(LATS)),
            e("Seated Cable Row", also = listOf(LATS, BICEPS)),
        )
        group(UPPER_BACK, MOBILITY, e("Thoracic Rotation", REPS, perSide = true))
        group(
            LOWER_BACK, STRENGTH,
            e("Deadlift", also = listOf(HAMSTRINGS, GLUTES, UPPER_BACK, FOREARMS)),
            e("Rack Pull", also = listOf(UPPER_BACK, GLUTES, FOREARMS)),
            e("Back Extension", REPS, also = listOf(GLUTES, HAMSTRINGS)),
        )
        group(LOWER_BACK, ISOMETRIC, e("Superman Hold", TIME, also = listOf(GLUTES)))
        group(LOWER_BACK, MOBILITY, e("Cat-Cow", REPS, also = listOf(UPPER_BACK), alsoStyles = listOf(YOGA)))
        group(LOWER_BACK, STRETCHING, e("Child's Pose", TIME, also = listOf(LATS), alsoStyles = listOf(YOGA)))

        // Shoulders
        group(
            FRONT_DELTS, STRENGTH,
            e("Overhead Press", also = listOf(TRICEPS, SIDE_DELTS)),
            e("Seated Dumbbell Press", also = listOf(TRICEPS, SIDE_DELTS)),
            e("Arnold Dumbbell Press", also = listOf(SIDE_DELTS, TRICEPS)),
            e("Push Press", also = listOf(TRICEPS, WHOLE_LEGS)), e("Front Dumbbell Raise"),
        )
        group(SIDE_DELTS, STRENGTH, e("Lateral Dumbbell Raise"), e("Upright Barbell Row", also = listOf(UPPER_BACK)))
        group(SIDE_DELTS, ISOMETRIC, e("Lateral Raise Hold", TIME_WEIGHT))
        group(
            REAR_DELTS, STRENGTH,
            e("Rear Delt Dumbbell Raise", also = listOf(UPPER_BACK)),
            e("Face Pull", also = listOf(UPPER_BACK, ROTATOR_CUFF)),
        )
        group(REAR_DELTS, STRETCHING, e("Cross-Body Shoulder Stretch", TIME, perSide = true, also = listOf(UPPER_BACK)))
        group(ROTATOR_CUFF, STRENGTH, e("Cable External Rotation", perSide = true))
        group(ROTATOR_CUFF, MOBILITY, e("Shoulder CARs", REPS, perSide = true))

        // Arms
        group(
            BICEPS, STRENGTH,
            e("Barbell Curl"), e("Dumbbell Curl"), e("Hammer Curl", also = listOf(FOREARMS)),
            e("Incline Dumbbell Curl"), e("EZ-Bar Preacher Curl"), e("Cable Curl"), e("Concentration Curl"),
        )
        group(BICEPS, ISOMETRIC, e("Isometric Curl Hold", TIME_WEIGHT))
        group(BICEPS, ECCENTRIC, e("Eccentric Barbell Curl", tempo = "4-0-1-0"))
        group(
            TRICEPS, STRENGTH,
            e("Close Grip Barbell Bench Press", also = listOf(CHEST, FRONT_DELTS)), e("Rope Push Down"),
            e("V-Bar Push Down"), e("Parallel Bar Triceps Dip", also = listOf(CHEST, FRONT_DELTS)),
            e("Overhead Dumbbell Triceps Extension"), e("EZ-Bar Skullcrusher"),
        )
        group(
            FOREARMS, ECCENTRIC,
            e("Tyler Twist", REPS, tempo = "3-0-1-0", perSide = true),
            e("Eccentric Wrist Extension", tempo = "3-0-1-0", perSide = true),
        )

        // Legs
        group(
            QUADS, STRENGTH,
            e("Barbell Squat", also = listOf(GLUTES, LOWER_BACK)), e("Front Squat", also = listOf(GLUTES)),
            e("Leg Press", also = listOf(GLUTES)), e("Leg Extension Machine"),
            e("Bulgarian Split Squat", also = listOf(GLUTES)), e("Walking Lunge", also = listOf(GLUTES)),
        )
        group(
            QUADS, ISOMETRIC,
            e("Wall Sit", TIME_WEIGHT, also = listOf(GLUTES)), e("Spanish Squat Hold", TIME_WEIGHT),
            e("Split Squat Hold", TIME_WEIGHT, perSide = true, also = listOf(GLUTES)),
            e("Isometric Leg Extension Hold", TIME_WEIGHT),
        )
        group(
            QUADS, ECCENTRIC,
            e("Decline Board Squat", tempo = "3-0-1-0"),
            e("Heavy Slow Leg Press", tempo = "3-0-3-0", also = listOf(GLUTES)),
            e("Reverse Nordic Curl", REPS, tempo = "3-0-1-0", also = listOf(HIPS)),
            e("Eccentric Leg Extension", tempo = "4-0-1-0"),
        )
        group(QUADS, PLYOMETRIC, e("Jump Squat", REPS, also = listOf(GLUTES, CALVES)))
        group(QUADS, STRETCHING, e("Couch Stretch", TIME, perSide = true, also = listOf(HIPS)))
        group(
            HAMSTRINGS, STRENGTH,
            e("Romanian Deadlift", also = listOf(GLUTES, LOWER_BACK)), e("Seated Leg Curl Machine"),
            e("Lying Leg Curl Machine"),
        )
        group(HAMSTRINGS, ISOMETRIC, e("Hamstring Bridge Hold", TIME, also = listOf(GLUTES)))
        group(HAMSTRINGS, ECCENTRIC, e("Nordic Hamstring Curl", REPS, tempo = "5-0-1-0"))
        group(HAMSTRINGS, STRETCHING, e("Hamstring Stretch", TIME, perSide = true))
        group(GLUTES, STRENGTH, e("Barbell Hip Thrust", also = listOf(HAMSTRINGS)))
        group(GLUTES, ISOMETRIC, e("Glute Bridge Hold", TIME_WEIGHT, also = listOf(HAMSTRINGS)))
        group(
            GLUTES, STRETCHING,
            e("Pigeon Stretch", TIME, perSide = true, also = listOf(HIPS), alsoStyles = listOf(YOGA)),
        )
        group(
            HIPS, MOBILITY,
            e("Hip CARs", REPS, perSide = true), e("90/90 Hip Switches", REPS, also = listOf(GLUTES)),
            e("Deep Squat Hold", TIME, also = listOf(CALVES, ADDUCTORS)),
        )
        group(HIPS, STRETCHING, e("Hip Flexor Stretch", TIME, perSide = true, also = listOf(QUADS)))
        group(ADDUCTORS, STRENGTH, e("Adductor Machine"))
        group(ADDUCTORS, ISOMETRIC, e("Copenhagen Plank", TIME, perSide = true, also = listOf(OBLIQUES)))
        group(CALVES, STRENGTH, e("Standing Calf Raise"), e("Seated Calf Raise"))
        group(CALVES, ISOMETRIC, e("Single-Leg Calf Raise Hold", TIME_WEIGHT, perSide = true))
        group(CALVES, ECCENTRIC, e("Eccentric Heel Drop", tempo = "3-0-1-0", perSide = true))
        group(CALVES, PLYOMETRIC, e("Pogo Hops", REPS))
        group(CALVES, MOBILITY, e("Ankle Dorsiflexion Rocks", REPS, perSide = true))
        group(CALVES, STRETCHING, e("Calf Stretch", TIME, perSide = true))
        group(SHINS, STRENGTH, e("Tibialis Raise"))
        group(
            WHOLE_LEGS, PLYOMETRIC,
            e("Box Jump", REPS_HEIGHT), e("Broad Jump", REPS_HEIGHT), e("Depth Jump", REPS_HEIGHT),
            e("Lateral Bound", REPS_HEIGHT, perSide = true, also = listOf(GLUTES)), e("Tuck Jump", REPS),
            e("Skater Jump", REPS, also = listOf(GLUTES)),
        )

        // Core
        group(
            ABS, STRENGTH,
            e("Crunch", REPS), e("Hanging Leg Raise", REPS, also = listOf(HIPS)), e("Cable Crunch"),
            e("Ab Wheel Rollout", REPS, also = listOf(LATS)), e("Dead Bug", REPS),
        )
        group(ABS, ISOMETRIC, e("Plank", TIME), e("Hollow Body Hold", TIME))
        group(ABS, STRETCHING, e("Cobra Stretch", TIME, also = listOf(LOWER_BACK), alsoStyles = listOf(YOGA)))
        group(
            OBLIQUES, ISOMETRIC,
            e("Side Plank", TIME), e("Pallof Press Hold", TIME, perSide = true, also = listOf(ABS)),
        )

        // Full body
        group(
            FULL_BODY, STRENGTH,
            e("Kettlebell Swing", also = listOf(GLUTES, HAMSTRINGS, LOWER_BACK)),
            e("Farmer's Carry", TIME_WEIGHT, also = listOf(FOREARMS, UPPER_BACK)),
        )
        group(FULL_BODY, ISOMETRIC, e("Isometric Mid-Thigh Pull", TIME_WEIGHT, also = listOf(UPPER_BACK, WHOLE_LEGS)))
        group(FULL_BODY, PLYOMETRIC, e("Medicine Ball Slam", also = listOf(ABS, LATS)))
        group(
            FULL_BODY, MOBILITY,
            e("World's Greatest Stretch", REPS, perSide = true, also = listOf(HIPS, HAMSTRINGS, UPPER_BACK)),
        )

        // Cardio
        group(
            Muscle.CARDIO, CARDIO,
            e("Running", DISTANCE_TIME), e("Walking", DISTANCE_TIME), e("Cycling", DISTANCE_TIME),
            e("Rowing Machine", DISTANCE_TIME), e("Elliptical Trainer", DISTANCE_TIME), e("Swimming", DISTANCE_TIME),
            e("Stair Climber", TIME), e("Jump Rope", TIME),
        )

        // Sports
        group(
            Muscle.SPORT, SPORT,
            e("Tennis", SESSION), e("Table Tennis", SESSION), e("Volleyball", SESSION), e("Padel", SESSION),
            e("Badminton", SESSION), e("Squash", SESSION), e("Football", SESSION), e("Basketball", SESSION),
            e("Climbing", SESSION), e("Martial Arts", SESSION),
        )

        // Cardio intervals (HIIT moved here from Sports in version 0.3)
        group(
            Muscle.CARDIO, HIIT,
            e("HIIT Intervals", INTERVALS, plan = intervals(high = 30, low = 30, rounds = 10), movedFrom = Regions.SPORTS),
            e("Tabata", INTERVALS, plan = intervals(high = 20, low = 10, rounds = 8), movedFrom = Regions.SPORTS),
            e("Sprint Intervals", INTERVALS, plan = intervals(high = 15, low = 45, rounds = 10), movedFrom = Regions.SPORTS),
            e("Bike Intervals", INTERVALS, plan = intervals(high = 40, low = 20, rounds = 8)),
            e("Rowing Intervals", INTERVALS, plan = intervals(high = 60, low = 60, rounds = 6)),
            e("Jump Rope Intervals", INTERVALS, plan = intervals(high = 30, low = 30, rounds = 10)),
        )
        group(
            Muscle.CARDIO, DANCE,
            e("Dance Session", SESSION, alsoStyles = listOf(CARDIO)), e("Zumba", SESSION, alsoStyles = listOf(CARDIO)),
            e("Salsa", SESSION, alsoStyles = listOf(CARDIO)), e("Bachata", SESSION, alsoStyles = listOf(CARDIO)),
            e("Hip-Hop Dance", SESSION, alsoStyles = listOf(CARDIO)),
            e("Ballet", SESSION, alsoStyles = listOf(CARDIO, STRETCHING)),
        )

        // Mind & recovery
        group(
            Muscle.MIND, MEDITATION,
            e("Meditation", SESSION), e("Box Breathing", TIME), e("Body Scan", SESSION),
            e("Walking Meditation", SESSION), e("Yoga Nidra", SESSION, alsoStyles = listOf(YOGA)),
        )

        // Yoga: each pose also counts as stretching, mobility or an isometric hold.
        group(
            FULL_BODY, YOGA,
            e("Sun Salutation", REPS, alsoStyles = listOf(MOBILITY)),
            e("Yoga Flow", SESSION, alsoStyles = listOf(MOBILITY, STRETCHING)),
        )
        group(HAMSTRINGS, YOGA, e("Downward Dog", TIME, also = listOf(CALVES, LATS), alsoStyles = listOf(STRETCHING)))
        group(
            HAMSTRINGS, YOGA,
            e("Triangle Pose", TIME, perSide = true, also = listOf(OBLIQUES, ADDUCTORS), alsoStyles = listOf(STRETCHING)),
        )
        group(
            HAMSTRINGS, YOGA,
            e("Seated Forward Fold", TIME, also = listOf(LOWER_BACK), alsoStyles = listOf(STRETCHING)),
        )
        group(
            HAMSTRINGS, YOGA,
            e("Warrior III", TIME, perSide = true, also = listOf(GLUTES, LOWER_BACK), alsoStyles = listOf(ISOMETRIC)),
        )
        group(ABS, YOGA, e("Upward Dog", TIME, also = listOf(LOWER_BACK, CHEST), alsoStyles = listOf(STRETCHING)))
        group(ABS, YOGA, e("Boat Pose", TIME, also = listOf(HIPS), alsoStyles = listOf(ISOMETRIC)))
        group(ABS, YOGA, e("Crow Pose", TIME, also = listOf(TRICEPS, FOREARMS), alsoStyles = listOf(ISOMETRIC)))
        group(
            QUADS, YOGA,
            e("Warrior I", TIME, perSide = true, also = listOf(HIPS, GLUTES), alsoStyles = listOf(ISOMETRIC)),
        )
        group(
            QUADS, YOGA,
            e("Warrior II", TIME, perSide = true, also = listOf(ADDUCTORS, SIDE_DELTS), alsoStyles = listOf(ISOMETRIC)),
        )
        group(QUADS, YOGA, e("Chair Pose", TIME, also = listOf(GLUTES), alsoStyles = listOf(ISOMETRIC)))
        group(QUADS, YOGA, e("Camel Pose", TIME, also = listOf(HIPS, ABS), alsoStyles = listOf(STRETCHING)))
        group(
            CALVES, YOGA,
            e("Tree Pose", TIME, perSide = true, also = listOf(GLUTES, HIPS), alsoStyles = listOf(ISOMETRIC)),
        )
        group(
            GLUTES, YOGA,
            e("Bridge Pose", TIME, also = listOf(HAMSTRINGS, LOWER_BACK), alsoStyles = listOf(ISOMETRIC)),
        )
        group(
            HIPS, YOGA,
            e("Lizard Pose", TIME, perSide = true, also = listOf(HAMSTRINGS), alsoStyles = listOf(STRETCHING)),
        )
        group(HIPS, YOGA, e("Low Lunge", TIME, perSide = true, also = listOf(QUADS), alsoStyles = listOf(STRETCHING)))
        group(HIPS, YOGA, e("Happy Baby", TIME, also = listOf(ADDUCTORS), alsoStyles = listOf(STRETCHING)))
        group(ADDUCTORS, YOGA, e("Butterfly Pose", TIME, also = listOf(HIPS), alsoStyles = listOf(STRETCHING)))
        group(ADDUCTORS, YOGA, e("Frog Pose", TIME, also = listOf(HIPS), alsoStyles = listOf(STRETCHING)))
        group(
            LOWER_BACK, YOGA,
            e("Supine Spinal Twist", TIME, perSide = true, also = listOf(OBLIQUES, GLUTES), alsoStyles = listOf(STRETCHING)),
        )
        group(
            UPPER_BACK, YOGA,
            e("Thread the Needle", TIME, perSide = true, also = listOf(REAR_DELTS), alsoStyles = listOf(MOBILITY)),
        )
        group(LATS, YOGA, e("Puppy Pose", TIME, also = listOf(UPPER_BACK, CHEST), alsoStyles = listOf(STRETCHING)))

        // More strength
        group(CHEST, STRENGTH, e("Pec Deck"), e("Incline Cable Fly"), e("Dumbbell Pullover", also = listOf(LATS)))
        group(LATS, STRENGTH, e("Straight-Arm Pulldown"))
        group(
            UPPER_BACK, STRENGTH,
            e("Chest-Supported Row", also = listOf(LATS, REAR_DELTS)), e("Barbell Shrug", also = listOf(FOREARMS)),
            e("Inverted Row", REPS, also = listOf(LATS, BICEPS)),
        )
        group(LOWER_BACK, STRENGTH, e("Good Morning", also = listOf(HAMSTRINGS, GLUTES)))
        group(SIDE_DELTS, STRENGTH, e("Cable Lateral Raise", perSide = true))
        group(REAR_DELTS, STRENGTH, e("Reverse Pec Deck", also = listOf(UPPER_BACK)))
        group(FRONT_DELTS, STRENGTH, e("Landmine Press", perSide = true, also = listOf(CHEST, TRICEPS)))
        group(BICEPS, STRENGTH, e("Spider Curl"), e("Reverse Curl", also = listOf(FOREARMS)))
        group(FOREARMS, STRENGTH, e("Wrist Curl"), e("Reverse Wrist Curl"))
        group(
            TRICEPS, STRENGTH,
            e("Cable Overhead Triceps Extension"), e("Diamond Push Up", REPS, also = listOf(CHEST, FRONT_DELTS)),
        )
        group(
            QUADS, STRENGTH,
            e("Hack Squat", also = listOf(GLUTES)), e("Goblet Squat", also = listOf(GLUTES)),
            e("Step Up", perSide = true, also = listOf(GLUTES)), e("Sissy Squat", REPS),
        )
        group(HAMSTRINGS, STRENGTH, e("Single-Leg Romanian Deadlift", perSide = true, also = listOf(GLUTES)))
        group(
            GLUTES, STRENGTH,
            e("Sumo Deadlift", also = listOf(HAMSTRINGS, ADDUCTORS, LOWER_BACK)), e("Hip Abduction Machine"),
            e("Cable Glute Kickback", perSide = true, also = listOf(HAMSTRINGS)),
        )
        group(CALVES, STRENGTH, e("Donkey Calf Raise"))
        group(
            ABS, STRENGTH,
            e("Leg Raise", REPS, also = listOf(HIPS)), e("Bicycle Crunch", REPS, also = listOf(OBLIQUES)),
            e("Mountain Climber", REPS, also = listOf(HIPS)),
        )
        group(
            OBLIQUES, STRENGTH,
            e("Russian Twist", REPS, also = listOf(ABS)), e("Cable Woodchopper", perSide = true, also = listOf(ABS)),
        )
        group(
            FULL_BODY, STRENGTH,
            e("Clean and Press", also = listOf(FRONT_DELTS, WHOLE_LEGS)),
            e("Thruster", also = listOf(QUADS, FRONT_DELTS)),
            e("Turkish Get-Up", perSide = true, also = listOf(ABS, ROTATOR_CUFF)),
        )
        group(FULL_BODY, HIIT, e("Burpee", REPS, alsoStyles = listOf(PLYOMETRIC)))

        // More isometrics
        group(ABS, ISOMETRIC, e("L-Sit", TIME, also = listOf(HIPS, TRICEPS)))
        group(QUADS, ISOMETRIC, e("Horse Stance", TIME, also = listOf(ADDUCTORS, GLUTES)))
        group(FRONT_DELTS, ISOMETRIC, e("Overhead Barbell Hold", TIME_WEIGHT, also = listOf(TRICEPS, ABS)))
        group(FOREARMS, ISOMETRIC, e("Plate Pinch Hold", TIME_WEIGHT), e("Towel Hang", TIME, also = listOf(LATS)))
        group(UPPER_BACK, ISOMETRIC, e("Inverted Row Hold", TIME, also = listOf(LATS, BICEPS)))
        group(LOWER_BACK, ISOMETRIC, e("Bird Dog Hold", TIME, perSide = true, also = listOf(GLUTES, ABS)))
        group(ADDUCTORS, ISOMETRIC, e("Adductor Squeeze", TIME))
        group(GLUTES, ISOMETRIC, e("Reverse Plank", TIME, also = listOf(HAMSTRINGS, REAR_DELTS)))
        group(HAMSTRINGS, ISOMETRIC, e("Isometric Nordic Hold", TIME))

        // More eccentrics and tendon work
        group(
            QUADS, ECCENTRIC,
            e("Eccentric Step-Down", tempo = "4-0-1-0", perSide = true, also = listOf(GLUTES)),
            e("Pistol Squat Negative", REPS, tempo = "5-0-1-0", perSide = true, also = listOf(GLUTES)),
            e("Heavy Slow Squat", tempo = "3-0-3-0", also = listOf(GLUTES)),
        )
        group(HAMSTRINGS, ECCENTRIC, e("Slider Hamstring Curl", REPS, tempo = "4-0-1-0", also = listOf(GLUTES)))
        group(TRICEPS, ECCENTRIC, e("Eccentric Dip", REPS, tempo = "5-0-1-0", also = listOf(CHEST)))
        group(FOREARMS, ECCENTRIC, e("Eccentric Wrist Flexion", tempo = "3-0-1-0", perSide = true))
        group(ROTATOR_CUFF, ECCENTRIC, e("Eccentric External Rotation", tempo = "4-0-1-0", perSide = true))

        // More mobility and stretching
        group(ROTATOR_CUFF, MOBILITY, e("Shoulder Dislocates", REPS, also = listOf(FRONT_DELTS)))
        group(FOREARMS, MOBILITY, e("Wrist Mobility", REPS))
        group(
            HIPS, MOBILITY,
            e("Hip Airplane", REPS, perSide = true, also = listOf(GLUTES)), e("Spiderman Lunge", REPS, perSide = true),
        )
        group(HAMSTRINGS, MOBILITY, e("Jefferson Curl", tempo = "5-0-5-0", also = listOf(LOWER_BACK)))
        group(FULL_BODY, MOBILITY, e("Foam Rolling", TIME))
        group(QUADS, STRETCHING, e("Standing Quad Stretch", TIME, perSide = true))
        group(LATS, STRETCHING, e("Lat Stretch", TIME, perSide = true))
        group(TRICEPS, STRETCHING, e("Triceps Stretch", TIME, perSide = true))
        group(GLUTES, STRETCHING, e("Figure-Four Stretch", TIME, perSide = true))
        group(FOREARMS, STRETCHING, e("Wrist Flexor Stretch", TIME))
        group(CALVES, PLYOMETRIC, e("Single-Leg Hop", REPS_HEIGHT, perSide = true, also = listOf(WHOLE_LEGS)))

        // Sports and athletic training (version 0.5): prehab, tendons and power for court sports,
        // climbing, sprinting and fighting sports.
        group(
            Muscle.SPORT, SPORT,
            e("Bouldering", SESSION), e("Kickboxing", SESSION), e("Boxing", SESSION), e("Beach Volleyball", SESSION),
        )
        group(Muscle.CARDIO, CARDIO, e("Sprinting", DISTANCE_TIME), e("Shuttle Run", TIME), e("Agility Ladder", TIME))
        group(
            Muscle.CARDIO, HIIT,
            e("Hill Sprints", INTERVALS, plan = intervals(high = 10, low = 50, rounds = 8)),
            e("Heavy Bag Rounds", INTERVALS, plan = intervals(high = 180, low = 60, rounds = 5), also = listOf(FULL_BODY)),
            e("Shadow Boxing", INTERVALS, plan = intervals(high = 180, low = 60, rounds = 3), also = listOf(FULL_BODY)),
        )
        // Hips: glute medius, hip flexors and groin.
        group(
            GLUTES, ECCENTRIC,
            e("Slow Single-Leg Pelvic Drop", tempo = "3-1-1-0", perSide = true, also = listOf(HIPS)),
        )
        group(
            GLUTES, ISOMETRIC,
            e("Side-Lying Clamshell Hold", TIME, perSide = true, also = listOf(HIPS)),
            e("Single-Leg Glute Bridge", REPS, perSide = true, also = listOf(HAMSTRINGS)),
        )
        group(
            GLUTES, STRENGTH,
            e("Clamshell", REPS, perSide = true, also = listOf(HIPS)),
            e("Banded Lateral Walk", REPS, also = listOf(HIPS)),
        )
        group(HIPS, STRENGTH, e("Psoas March", REPS, perSide = true, also = listOf(ABS)))
        group(HIPS, ISOMETRIC, e("Seated Knee Lift Hold", TIME, perSide = true))
        group(
            ADDUCTORS, STRENGTH,
            e("Lateral Lunge", perSide = true, also = listOf(GLUTES, QUADS)),
            e("Side-Lying Adductor Raise", REPS, perSide = true),
        )
        group(ADDUCTORS, MOBILITY, e("Cossack Squat", REPS, perSide = true, also = listOf(HIPS)))
        // Legs: knees, ankles and hamstrings for jumping, landing and sprinting.
        group(QUADS, ISOMETRIC, e("Isometric Reverse Nordic Hold", TIME, also = listOf(HIPS)))
        group(CALVES, ECCENTRIC, e("Slow Deficit Calf Raise", tempo = "3-1-3-0"))
        group(CALVES, ISOMETRIC, e("Single-Leg Balance Hold", TIME, perSide = true, also = listOf(SHINS)))
        group(SHINS, STRENGTH, e("Banded Ankle Eversion", REPS, perSide = true))
        group(
            HAMSTRINGS, ECCENTRIC,
            e("Eccentric Romanian Deadlift", tempo = "3-0-1-0", also = listOf(GLUTES, LOWER_BACK)),
            e("Hamstring Walkout", REPS, also = listOf(GLUTES)),
        )
        group(LOWER_BACK, STRENGTH, e("45-Degree Hyperextension", also = listOf(GLUTES, HAMSTRINGS)))
        group(
            WHOLE_LEGS, PLYOMETRIC,
            e("Approach Jump", REPS_HEIGHT, also = listOf(CALVES, GLUTES)),
            e("Single-Leg Landing Stick", REPS, perSide = true, also = listOf(QUADS, GLUTES), alsoStyles = listOf(ECCENTRIC)),
            e("Split Squat Jump", REPS, also = listOf(QUADS, GLUTES)), e("A-Skip", REPS, also = listOf(HIPS, CALVES)),
            e("Lateral Shuffle", TIME, also = listOf(ADDUCTORS, GLUTES)),
        )
        group(
            FULL_BODY, STRENGTH,
            e("Sled Push", TIME_WEIGHT, also = listOf(WHOLE_LEGS)),
            e("Sled Pull", TIME_WEIGHT, also = listOf(WHOLE_LEGS)),
        )
        // Shoulders: rotator cuff and shoulder blades for serving, spiking, climbing and punching.
        group(
            ROTATOR_CUFF, STRENGTH,
            e("Side-Lying External Rotation", perSide = true),
            e("Prone Y-T-W Raise", REPS, also = listOf(UPPER_BACK, REAR_DELTS)),
        )
        group(ROTATOR_CUFF, ISOMETRIC, e("90/90 External Rotation Hold", TIME_WEIGHT, perSide = true))
        group(ROTATOR_CUFF, STRETCHING, e("Sleeper Stretch", TIME, perSide = true))
        group(ROTATOR_CUFF, MOBILITY, e("Wall Slide", REPS, also = listOf(UPPER_BACK)))
        group(
            UPPER_BACK, STRENGTH,
            e("Band Pull-Apart", REPS, also = listOf(REAR_DELTS)), e("Scapular Push Up", REPS, also = listOf(CHEST)),
        )
        group(LATS, STRENGTH, e("Scapular Pull Up", REPS, also = listOf(UPPER_BACK)))
        group(
            LATS, ISOMETRIC,
            e("Lock-Off Hold", TIME, perSide = true, also = listOf(BICEPS)),
            e("Tuck Front Lever Hold", TIME, also = listOf(ABS)),
        )
        // Forearms and fingers: climbing grip, racket and paddle arms.
        group(FOREARMS, ISOMETRIC, e("Hangboard Hang", TIME_WEIGHT), e("No-Hang Lift", TIME_WEIGHT, perSide = true))
        group(FOREARMS, PLYOMETRIC, e("Campus Board", REPS, also = listOf(LATS)))
        group(
            FOREARMS, STRENGTH,
            e("Finger Extension with Band", REPS), e("Wrist Roller"),
            e("Dumbbell Pronation-Supination", perSide = true), e("Rice Bucket", TIME),
        )
        group(FOREARMS, ECCENTRIC, e("Reverse Tyler Twist", REPS, tempo = "3-0-1-0", perSide = true))
        // Core: rotation and bracing for swings, throws and kicks.
        group(
            OBLIQUES, PLYOMETRIC,
            e("Rotational Medicine Ball Throw", REPS, perSide = true, also = listOf(FULL_BODY)),
            e("Overhead Medicine Ball Throw", REPS, also = listOf(ABS, LATS)),
        )
        group(
            OBLIQUES, STRENGTH,
            e("Landmine Rotation", also = listOf(ABS)), e("Cable Lift", perSide = true, also = listOf(ABS)),
        )
        group(ABS, STRENGTH, e("Hollow Rock", REPS))

        // From a real upper and lower body plan (version 0.5).
        group(
            FOREARMS, ISOMETRIC,
            e("Half-Crimp Hang", TIME_WEIGHT, also = listOf(LATS)),
            e("Open-Hand Hang", TIME_WEIGHT, also = listOf(LATS)),
        )
        group(
            ROTATOR_CUFF, ECCENTRIC,
            e("Slow Cable External Rotation", tempo = "3-0-1-0", perSide = true),
            e("Slow High Cable External Rotation", tempo = "3-0-1-0", perSide = true, also = listOf(REAR_DELTS)),
            e("Slow High Cable Internal Rotation", tempo = "3-0-1-0", perSide = true, also = listOf(CHEST)),
        )
        group(ROTATOR_CUFF, ISOMETRIC, e("Band Internal Rotation Hold", TIME, perSide = true))
        group(TRICEPS, STRENGTH, e("Overhead Cable Triceps Extension"))
        // A lateral raise that keeps going overhead: more range of motion under the weight.
        group(SIDE_DELTS, STRENGTH, e("Lu Raise", also = listOf(FRONT_DELTS, UPPER_BACK)))

        // Tendon training (version 0.6): isometrics to calm a sore tendon, then heavy slow and
        // eccentric work to rebuild it, for every tendon in the library.
        group(QUADS, ISOMETRIC, e("Long-Lever Wall Sit", TIME_WEIGHT, also = listOf(GLUTES)))
        group(
            QUADS, ECCENTRIC,
            e("Single-Leg Decline Squat", REPS, tempo = "3-0-1-0", perSide = true),
            e("Poliquin Step-Up", tempo = "2-0-1-0", perSide = true),
            e("Backward Sled Drag", TIME_WEIGHT, also = listOf(CALVES)),
            e("Heavy Slow Split Squat", tempo = "3-0-3-0", perSide = true, also = listOf(GLUTES)),
        )
        group(
            CALVES, ISOMETRIC,
            e("Seated Soleus Hold", TIME_WEIGHT, perSide = true), e("Smith Machine Calf Raise Hold", TIME_WEIGHT),
        )
        group(
            CALVES, ECCENTRIC,
            e("Bent-Knee Eccentric Heel Drop", tempo = "3-0-1-0", perSide = true),
            e("Toe-Elevated Calf Raise", tempo = "3-2-3-0", perSide = true),
            e("Heel Raise with Ball Squeeze", REPS, tempo = "3-1-1-0"),
            e("Heavy Slow Seated Calf Raise", tempo = "3-0-3-0"),
        )
        group(
            SHINS, STRENGTH,
            e("Banded Ankle Inversion", REPS, perSide = true), e("Short Foot Hold", TIME, also = listOf(FEET)),
        )
        group(
            HAMSTRINGS, ISOMETRIC,
            e("Long-Lever Bridge Hold", TIME, perSide = true, also = listOf(GLUTES)),
            e("Isometric Leg Curl Hold", TIME_WEIGHT),
        )
        group(
            HAMSTRINGS, ECCENTRIC,
            e("Razor Curl", REPS, tempo = "4-0-1-0", also = listOf(GLUTES)),
            e("Eccentric Single-Leg Leg Curl", tempo = "4-0-1-0", perSide = true),
        )
        group(
            ADDUCTORS, ISOMETRIC,
            e("Short-Lever Copenhagen Plank", TIME, perSide = true, also = listOf(OBLIQUES)),
            e("Isometric Adductor Ball Squeeze", TIME),
        )
        group(ADDUCTORS, ECCENTRIC, e("Sliding Lateral Lunge", tempo = "3-0-1-0", perSide = true, also = listOf(QUADS)))
        group(
            GLUTES, ISOMETRIC,
            e("Standing Hip Abduction Hold", TIME, perSide = true, also = listOf(HIPS)),
            e("Side-Lying Hip Abduction Hold", TIME, perSide = true, also = listOf(HIPS)),
        )
        group(GLUTES, STRENGTH, e("Hip Hike", REPS, perSide = true, also = listOf(HIPS)))
        group(HIPS, ISOMETRIC, e("Banded Hip Flexion Hold", TIME, perSide = true))
        group(
            ROTATOR_CUFF, ISOMETRIC,
            e("Doorway External Rotation Hold", TIME, perSide = true),
            e("Doorway Internal Rotation Hold", TIME, perSide = true),
            e("Bottoms-Up Kettlebell Carry", TIME_WEIGHT, perSide = true, also = listOf(FOREARMS)),
        )
        group(
            ROTATOR_CUFF, STRENGTH,
            e("Full Can Raise", also = listOf(SIDE_DELTS)), e("Prone Horizontal Abduction", also = listOf(REAR_DELTS)),
            e("Cable Internal Rotation", perSide = true),
        )
        group(BICEPS, ECCENTRIC, e("Eccentric Hammer Curl", tempo = "4-0-1-0", also = listOf(FOREARMS)))
        group(
            FOREARMS, ISOMETRIC,
            e("Isometric Wrist Extension Hold", TIME_WEIGHT, perSide = true),
            e("Isometric Wrist Flexion Hold", TIME_WEIGHT, perSide = true),
            e("Isometric Pronation Hold", TIME_WEIGHT, perSide = true),
        )
        group(TRICEPS, ISOMETRIC, e("Isometric Triceps Pushdown Hold", TIME_WEIGHT))
        group(TRICEPS, ECCENTRIC, e("Eccentric Triceps Pushdown", tempo = "4-0-1-0"))

        // Climbing: fingers, pulling strength, and the hips and shoulders to reach.
        group(FOREARMS, STRENGTH, e("Barbell Finger Curl"), e("Pinch Block Lift", TIME_WEIGHT, perSide = true))
        group(
            FOREARMS, ISOMETRIC,
            e("Hangboard Repeaters", INTERVALS, plan = intervals(high = 7, low = 3, rounds = 6), also = listOf(LATS)),
        )
        group(
            LATS, STRENGTH,
            e("Weighted Pull Up", also = listOf(BICEPS)), e("Archer Pull Up", REPS, also = listOf(BICEPS)),
            e("Typewriter Pull Up", REPS, also = listOf(BICEPS)),
            e("Offset Pull Up", REPS, perSide = true, also = listOf(BICEPS)),
        )
        group(
            ABS, STRENGTH,
            e("Toes to Bar", REPS, also = listOf(HIPS, LATS)), e("Front Lever Raise", REPS, also = listOf(LATS)),
        )
        group(CHEST, STRENGTH, e("Push Up Plus", REPS, also = listOf(FRONT_DELTS)))
        group(
            HIPS, MOBILITY,
            e("High Step Hip Mobility", REPS, perSide = true, also = listOf(GLUTES)),
            e("Hip Turnout Stretch", TIME, also = listOf(ADDUCTORS), alsoStyles = listOf(STRETCHING)),
            e("Drop Knee Rotation", REPS, perSide = true),
        )
        group(
            FOREARMS, STRETCHING,
            e("Forearm Extensor Stretch", TIME, perSide = true), e("Finger Flexor Stretch", TIME),
        )
        group(ROTATOR_CUFF, STRETCHING, e("Overhead Shoulder Flexion Stretch", TIME, also = listOf(LATS)))

        // Tennis: rotation, lateral movement, and a resilient serving shoulder and elbow.
        group(UPPER_BACK, MOBILITY, e("Open Book Rotation", REPS, perSide = true, also = listOf(CHEST)))
        group(
            HIPS, MOBILITY,
            e("Hip Internal Rotation Lift-Off", REPS, perSide = true),
            e("Lateral Lunge Rock", REPS, also = listOf(ADDUCTORS)),
        )
        group(
            WHOLE_LEGS, PLYOMETRIC,
            e("Lateral Hop", REPS, perSide = true, also = listOf(CALVES)),
            e("Split Step Jump", REPS, also = listOf(CALVES)),
        )
        group(
            OBLIQUES, PLYOMETRIC,
            e("Medicine Ball Side Throw", REPS, perSide = true, also = listOf(FULL_BODY)),
            e("Medicine Ball Shot Put", REPS, perSide = true, also = listOf(CHEST)),
        )
        group(OBLIQUES, STRENGTH, e("Pallof Press", REPS, perSide = true, also = listOf(ABS)))
        group(QUADS, STRENGTH, e("Reverse Lunge to Knee Drive", perSide = true, also = listOf(GLUTES, HIPS)))
        group(ROTATOR_CUFF, PLYOMETRIC, e("Reverse Ball Catch", REPS, perSide = true))

        // Swimming: an overhead shoulder with a stable blade, a strong kick and pull, and loose ankles.
        group(
            UPPER_BACK, MOBILITY,
            e("Thoracic Extension on Foam Roller", REPS, also = listOf(LATS)),
            e("Streamline Stretch", TIME, also = listOf(LATS)),
        )
        group(CALVES, STRETCHING, e("Kneeling Ankle Stretch", TIME, also = listOf(SHINS)))
        group(CHEST, STRETCHING, e("Pec Minor Stretch", TIME, perSide = true))
        group(
            LATS, STRENGTH,
            e("Banded Swim Pull", REPS, also = listOf(TRICEPS)),
            e("Prone Swimmer Lift", REPS, also = listOf(UPPER_BACK, LOWER_BACK)),
        )
        group(ABS, STRENGTH, e("Flutter Kicks", TIME, also = listOf(HIPS)))
        group(QUADS, PLYOMETRIC, e("Streamline Squat Jump", REPS, also = listOf(GLUTES, CALVES)))

        // Finger tendons and pulleys in every grip a climber uses, from easy daily loading to max hangs.
        group(
            FOREARMS, ISOMETRIC,
            e("Small Edge Max Hang", TIME_WEIGHT, also = listOf(LATS)), e("Three-Finger Drag Hang", TIME_WEIGHT),
            e("Two-Finger Pocket Hang", TIME_WEIGHT), e("Full Crimp Hang", TIME_WEIGHT),
            e("Sloper Hang", TIME_WEIGHT, also = listOf(LATS)),
            e("One-Arm Hang", TIME_WEIGHT, perSide = true, also = listOf(LATS)), e("Density Hang", TIME_WEIGHT),
            e("Low-Intensity Finger Loading", INTERVALS, plan = intervals(high = 10, low = 20, rounds = 20)),
            e("Wide Pinch Hold", TIME_WEIGHT, perSide = true), e("Isometric Finger Pull", TIME_WEIGHT, perSide = true),
            e("Lumbrical Hold", TIME, perSide = true),
        )
        group(
            FOREARMS, STRENGTH,
            e("Gripper Squeeze", REPS, perSide = true), e("Putty Squeeze", REPS, perSide = true),
            e("Thumb Band Abduction", REPS, perSide = true), e("Thumb Band Extension", REPS, perSide = true),
            e("Radial Deviation Lift", perSide = true), e("Ulnar Deviation Lift", perSide = true),
            e("Sledgehammer Levering", REPS, perSide = true),
        )
        group(FOREARMS, MOBILITY, e("Tendon Gliding", REPS, perSide = true))
        group(
            FOREARMS, STRETCHING,
            e("Thumb Web Stretch", TIME, perSide = true), e("Lumbrical Stretch", TIME, perSide = true),
        )

        // The often forgotten parts: neck, feet and toes, the knee's last degrees, deep core and breath.
        group(NECK, ISOMETRIC, e("Chin Tuck Hold", TIME), e("4-Way Neck Isometric", TIME))
        group(NECK, STRENGTH, e("Lying Neck Flexion", REPS), e("Prone Neck Extension", REPS, also = listOf(UPPER_BACK)))
        group(NECK, MOBILITY, e("Neck CARs", REPS))
        group(NECK, STRETCHING, e("Upper Trap Stretch", TIME, perSide = true, also = listOf(UPPER_BACK)))
        group(
            FEET, STRENGTH,
            e("Toe Yoga", REPS), e("Towel Scrunch", REPS, perSide = true), e("Toe Walk", TIME, also = listOf(CALVES)),
            e("Heel Walk", TIME, also = listOf(SHINS)),
        )
        group(FEET, ISOMETRIC, e("Toe Spread Hold", TIME), e("Big Toe Press Hold", TIME, perSide = true))
        group(FEET, STRETCHING, e("Big Toe Extension Stretch", TIME, perSide = true))
        group(CALVES, MOBILITY, e("Ankle CARs", REPS, perSide = true, also = listOf(FEET)))
        group(
            QUADS, STRENGTH,
            e("Terminal Knee Extension", REPS, perSide = true), e("Peterson Step-Up", REPS, perSide = true),
            e("Lateral Step-Down", REPS, perSide = true, also = listOf(GLUTES)),
        )
        group(ADDUCTORS, MOBILITY, e("Adductor Rockback", REPS, perSide = true, also = listOf(HIPS)))
        group(UPPER_BACK, STRENGTH, e("Serratus Foam Roller Wall Slide", REPS, also = listOf(ROTATOR_CUFF)))
        group(BICEPS, ISOMETRIC, e("Isometric Supination Hold", TIME_WEIGHT, perSide = true, also = listOf(FOREARMS)))
        group(OBLIQUES, STRENGTH, e("Suitcase Carry", TIME_WEIGHT, perSide = true, also = listOf(FOREARMS, LOWER_BACK)))
        group(ABS, STRENGTH, e("McGill Curl-Up", REPS))
        group(ABS, ISOMETRIC, e("Pelvic Floor Hold", TIME))
        group(
            MIND, MEDITATION,
            e("Crocodile Breathing", TIME, also = listOf(ABS)), e("90/90 Breathing", TIME, also = listOf(ABS)),
        )

        // Kickboxing: kicks and knees from strong, mobile hips, punches from the ground up, a guard that lasts.
        group(
            Muscle.SPORT, SPORT,
            e("Pad Work Rounds", INTERVALS, plan = intervals(high = 180, low = 60, rounds = 5)),
            e("Roundhouse Kick Drill", REPS, perSide = true), e("Teep Kick Drill", REPS, perSide = true),
            e("Switch Kick Drill", REPS, perSide = true), e("Knee Strike Drill", REPS, perSide = true),
            e("Slip and Roll Drill", TIME), e("Fighter Footwork Drill", TIME),
        )
        group(FULL_BODY, HIIT, e("Sprawl", REPS), e("Bob and Weave", TIME, also = listOf(QUADS)))
        group(HIPS, ISOMETRIC, e("Kick Chamber Hold", TIME, perSide = true, also = listOf(GLUTES)))
        group(
            HIPS, STRENGTH,
            e("Slow Kick Extension", REPS, tempo = "3-1-3-0", perSide = true, also = listOf(QUADS, GLUTES)),
            e("Cable Knee Drive", perSide = true, also = listOf(ABS)),
            e("Cable Hip Abduction", perSide = true, also = listOf(GLUTES)),
        )
        group(
            HIPS, MOBILITY,
            e("Front Leg Swing", REPS, perSide = true, also = listOf(HAMSTRINGS)),
            e("Lateral Leg Swing", REPS, perSide = true, also = listOf(ADDUCTORS)),
            e("Fire Hydrant Circles", REPS, perSide = true, also = listOf(GLUTES)),
        )
        group(
            HIPS, STRETCHING,
            e("Front Split Stretch", TIME, perSide = true, also = listOf(HAMSTRINGS)),
            e("Middle Split Stretch", TIME, also = listOf(ADDUCTORS)),
        )
        group(
            FRONT_DELTS, STRENGTH,
            e("Landmine Punch", perSide = true, also = listOf(CHEST, TRICEPS, OBLIQUES)),
            e("Band Resisted Punch", REPS, perSide = true, also = listOf(CHEST)),
            e("Cable Rotational Punch", perSide = true, also = listOf(OBLIQUES)),
        )
        group(FRONT_DELTS, ISOMETRIC, e("Guard Hold", TIME_WEIGHT, also = listOf(SIDE_DELTS)))
        group(FRONT_DELTS, HIIT, e("Dumbbell Shadow Boxing", TIME, also = listOf(SIDE_DELTS)))
        group(CHEST, PLYOMETRIC, e("Medicine Ball Chest Pass", REPS, also = listOf(TRICEPS)))
        group(ABS, STRENGTH, e("Sit-Up Punch", REPS, also = listOf(OBLIQUES)))

        // Volleyball: jumping and landing again and again, a fast spiking shoulder, and quick low defence.
        group(
            Muscle.SPORT, SPORT,
            e("Spike Approach Drill", REPS), e("Block Footwork Drill", TIME), e("Passing Drill", TIME),
            e("Serving Practice", REPS),
        )
        group(
            WHOLE_LEGS, PLYOMETRIC,
            e("Block Jump", REPS, also = listOf(CALVES, FRONT_DELTS)),
            e("Shuffle Block Jump", REPS, also = listOf(CALVES, ADDUCTORS)),
            e("Drop Landing", REPS, also = listOf(GLUTES)), e("Hurdle Hops", REPS, also = listOf(CALVES)),
            e("Trap Bar Jump", also = listOf(GLUTES, CALVES)),
        )
        group(WHOLE_LEGS, STRENGTH, e("Star Excursion Balance", REPS, perSide = true, also = listOf(CALVES, GLUTES)))
        group(QUADS, ISOMETRIC, e("Dig Position Hold", TIME, also = listOf(GLUTES, ADDUCTORS)))
        group(FULL_BODY, HIIT, e("Defensive Dive Get-Up", REPS))
        group(
            ROTATOR_CUFF, PLYOMETRIC,
            e("90/90 Ball Drop", REPS, perSide = true), e("Overhead Ball Wall Dribble", TIME, perSide = true),
        )
        group(
            ROTATOR_CUFF, STRENGTH,
            e("Banded Spike Swing", REPS, perSide = true, also = listOf(LATS)),
            e("Prone 90/90 External Rotation", perSide = true, also = listOf(REAR_DELTS)),
        )
        group(OBLIQUES, PLYOMETRIC, e("Medicine Ball Spike Throw", REPS, also = listOf(LATS, ABS)))
        group(ABS, STRENGTH, e("Sit-Up"))
    }

    private val regionKeysById: Map<String, String> = regions.associate { it.id to it.key }
    private val retiredById: Map<String, RetiredCategory> = retiredCategories.associateBy { it.id }
    private val exercisesById: Map<String, BuiltInExercise> = exercises.associateBy { it.id }

    /** The section key of a category id, or null for a category that is not a built-in section. */
    fun regionKeyOf(categoryId: String): String? = regionKeysById[categoryId]

    fun retired(categoryId: String): RetiredCategory? = retiredById[categoryId]

    fun exercise(id: String): BuiltInExercise? = exercisesById[id]
}
