package com.kkfittracking.data

import com.kkfittracking.model.ExercisePlan

/** Ready-made plans the user can add with one tap, built from the built-in exercises. */
object StarterPlans {
    /**
     * [supersets] lists groups of the plan's exercises that are done back to back, for [rounds]
     * rounds when set. [setPlans] are sets, reps and weights for exercises that have no set plan yet.
     */
    data class StarterPlan(
        val name: String,
        val exercises: List<String>,
        val supersets: List<List<String>> = emptyList(),
        val rounds: Int? = null,
        val setPlans: Map<String, ExercisePlan> = emptyMap(),
    )

    private fun sets(reps: Int? = null, kg: Double? = null) = ExercisePlan(sets = 3, reps = reps, weightKg = kg)

    /** Tendon holds: five sets of about 45 seconds, the usual dose to calm a sore tendon. */
    private fun holds(vararg names: String) = names.associateWith { ExercisePlan(sets = 5) }

    /** A plan in blocks: each block is a superset of [rounds] rounds (a block of one is a normal exercise). */
    private fun blocks(name: String, rounds: Int, vararg blocks: List<Pair<String, ExercisePlan>>) = StarterPlan(
        name = name,
        exercises = blocks.flatMap { block -> block.map { it.first } },
        supersets = blocks.filter { it.size >= 2 }.map { block -> block.map { it.first } },
        rounds = rounds,
        setPlans = blocks.flatMap { it.toList() }.toMap(),
    )

    val plans: List<StarterPlan> = listOf(
        // Kerim's own plans: a muscular exercise and its tendon work, block by block.
        blocks(
            "KK Upper body", 3,
            listOf("Pull Up" to sets(reps = 8), "Half-Crimp Hang" to sets(kg = 80.0)),
            listOf(
                "Incline Dumbbell Bench Press" to sets(reps = 10, kg = 25.0),
                "Slow Cable External Rotation" to sets(reps = 8, kg = 20.0),
                "Slow High Cable External Rotation" to sets(reps = 8, kg = 20.0),
            ),
            listOf(
                "Arnold Dumbbell Press" to sets(reps = 10, kg = 12.0),
                "Band Internal Rotation Hold" to sets(),
                "Slow High Cable Internal Rotation" to sets(reps = 8, kg = 20.0),
            ),
            listOf(
                "Reverse Curl" to sets(reps = 15, kg = 50.0),
                "Overhead Cable Triceps Extension" to sets(reps = 15, kg = 50.0),
                "Eccentric Wrist Flexion" to sets(reps = 8, kg = 10.0),
                "Eccentric Wrist Extension" to sets(reps = 8, kg = 10.0),
            ),
            listOf(
                "Lu Raise" to sets(reps = 15, kg = 8.0),
                "One-Arm Dumbbell Row" to sets(reps = 10, kg = 36.0),
                "Plate Pinch Hold" to sets(kg = 15.0),
            ),
        ),
        blocks(
            "KK Lower body", 3,
            listOf(
                "Walking Lunge" to sets(reps = 20, kg = 22.0),
                "Wall Sit" to sets(kg = 25.0),
                "Isometric Reverse Nordic Hold" to sets(),
            ),
            listOf(
                "Eccentric Romanian Deadlift" to sets(reps = 8, kg = 17.5),
                "45-Degree Hyperextension" to sets(kg = 10.0),
                "Nordic Hamstring Curl" to sets(),
            ),
            listOf(
                "Farmer's Carry" to sets(kg = 36.0),
                "Side-Lying Clamshell Hold" to sets(),
                "Slow Single-Leg Pelvic Drop" to sets(reps = 8, kg = 36.0),
            ),
            listOf("Cable Woodchopper" to sets(reps = 12, kg = 40.0), "Slow Deficit Calf Raise" to sets(reps = 8, kg = 16.0)),
            listOf("Sit-Up" to sets(kg = 15.0), "Hanging Leg Raise" to sets(reps = 15)),
        ),
        StarterPlan(
            "Push",
            listOf(
                "Flat Barbell Bench Press",
                "Incline Barbell Bench Press",
                "Overhead Press",
                "Lateral Dumbbell Raise",
                "Rope Push Down",
            ),
        ),
        StarterPlan("Pull", listOf("Pull Up", "Barbell Row", "Lat Pulldown", "Face Pull", "Barbell Curl")),
        StarterPlan(
            "Legs",
            listOf(
                "Barbell Squat",
                "Romanian Deadlift",
                "Leg Press",
                "Seated Leg Curl Machine",
                "Standing Calf Raise",
            ),
        ),
        StarterPlan(
            "Upper body",
            listOf(
                "Incline Dumbbell Bench Press",
                "Pull Up",
                "Seated Dumbbell Press",
                "One-Arm Dumbbell Row",
                "Hammer Curl",
                "Rope Push Down",
            ),
        ),
        StarterPlan(
            "Arms supersets",
            listOf(
                "Barbell Curl",
                "Rope Push Down",
                "Hammer Curl",
                "Overhead Dumbbell Triceps Extension",
                "Concentration Curl",
            ),
            supersets = listOf(
                listOf("Barbell Curl", "Rope Push Down"),
                listOf("Hammer Curl", "Overhead Dumbbell Triceps Extension"),
            ),
        ),
        StarterPlan(
            "Court sports prehab",
            listOf(
                "Single-Leg Balance Hold",
                "Banded Ankle Eversion",
                "Lateral Lunge",
                "Copenhagen Plank",
                "Side-Lying External Rotation",
                "90/90 External Rotation Hold",
                "Tyler Twist",
                "Rotational Medicine Ball Throw",
                "Slow Deficit Calf Raise",
            ),
        ),
        StarterPlan(
            "Climbing prehab",
            listOf(
                "Hangboard Hang",
                "No-Hang Lift",
                "Finger Extension with Band",
                "Reverse Tyler Twist",
                "Scapular Pull Up",
                "Lock-Off Hold",
                "Prone Y-T-W Raise",
                "Wall Slide",
            ),
        ),
        StarterPlan(
            "Sprint & jump",
            listOf(
                "A-Skip",
                "Psoas March",
                "Nordic Hamstring Curl",
                "Isometric Reverse Nordic Hold",
                "Slow Deficit Calf Raise",
                "Single-Leg Landing Stick",
                "Approach Jump",
                "Hill Sprints",
            ),
        ),
        StarterPlan(
            "Kickboxing conditioning",
            listOf(
                "Shadow Boxing",
                "Heavy Bag Rounds",
                "Rotational Medicine Ball Throw",
                "Cossack Squat",
                "Psoas March",
                "Slow Single-Leg Pelvic Drop",
                "Hollow Rock",
            ),
            supersets = listOf(listOf("Cossack Squat", "Psoas March")),
        ),
        StarterPlan(
            "Tendon health",
            listOf(
                "Nordic Hamstring Curl",
                "Eccentric Heel Drop",
                "Spanish Squat Hold",
                "Decline Board Squat",
                "Tyler Twist",
                "Copenhagen Plank",
            ),
        ),
        StarterPlan(
            "Mobility flow",
            listOf(
                "Cat-Cow",
                "World's Greatest Stretch",
                "90/90 Hip Switches",
                "Thoracic Rotation",
                "Ankle Dorsiflexion Rocks",
                "Deep Squat Hold",
            ),
        ),
        // Tendon training: holds first when a tendon is sore, then heavy slow and eccentric work.
        StarterPlan(
            "Tendon isometrics",
            listOf(
                "Spanish Squat Hold",
                "Long-Lever Wall Sit",
                "Seated Soleus Hold",
                "Long-Lever Bridge Hold",
                "Isometric Adductor Ball Squeeze",
                "Side-Lying Hip Abduction Hold",
                "Doorway External Rotation Hold",
                "Isometric Wrist Extension Hold",
                "Isometric Triceps Pushdown Hold",
            ),
            setPlans = holds(
                "Spanish Squat Hold", "Long-Lever Wall Sit", "Seated Soleus Hold", "Long-Lever Bridge Hold",
                "Isometric Adductor Ball Squeeze", "Side-Lying Hip Abduction Hold", "Doorway External Rotation Hold",
                "Isometric Wrist Extension Hold", "Isometric Triceps Pushdown Hold",
            ),
        ),
        StarterPlan(
            "Tendon heavy slow",
            listOf(
                "Heavy Slow Split Squat",
                "Heavy Slow Seated Calf Raise",
                "Bent-Knee Eccentric Heel Drop",
                "Heel Raise with Ball Squeeze",
                "Razor Curl",
                "Sliding Lateral Lunge",
                "Full Can Raise",
                "Eccentric Hammer Curl",
                "Eccentric Triceps Pushdown",
            ),
            supersets = listOf(listOf("Heavy Slow Seated Calf Raise", "Full Can Raise"), listOf("Eccentric Hammer Curl", "Eccentric Triceps Pushdown")),
        ),
        // Climbing, tennis and swimming: strength and mobility for each.
        StarterPlan(
            "Climbing strength",
            listOf(
                "Hangboard Repeaters",
                "Weighted Pull Up",
                "Offset Pull Up",
                "Front Lever Raise",
                "Pinch Block Lift",
                "Barbell Finger Curl",
                "Push Up Plus",
                "Toes to Bar",
                "Reverse Tyler Twist",
            ),
            supersets = listOf(listOf("Front Lever Raise", "Push Up Plus"), listOf("Barbell Finger Curl", "Reverse Tyler Twist")),
        ),
        StarterPlan(
            "Climbing mobility",
            listOf(
                "High Step Hip Mobility",
                "Hip Turnout Stretch",
                "Drop Knee Rotation",
                "Cossack Squat",
                "Thoracic Extension on Foam Roller",
                "Overhead Shoulder Flexion Stretch",
                "Forearm Extensor Stretch",
                "Finger Flexor Stretch",
            ),
        ),
        StarterPlan(
            "Tennis strength",
            listOf(
                "Split Step Jump",
                "Lateral Hop",
                "Medicine Ball Side Throw",
                "Medicine Ball Shot Put",
                "Reverse Lunge to Knee Drive",
                "Single-Leg Romanian Deadlift",
                "Pallof Press",
                "Full Can Raise",
                "Reverse Ball Catch",
                "Tyler Twist",
            ),
            supersets = listOf(listOf("Reverse Lunge to Knee Drive", "Pallof Press"), listOf("Full Can Raise", "Tyler Twist")),
        ),
        StarterPlan(
            "Tennis mobility",
            listOf(
                "Open Book Rotation",
                "Hip Internal Rotation Lift-Off",
                "Lateral Lunge Rock",
                "World's Greatest Stretch",
                "Sleeper Stretch",
                "Forearm Extensor Stretch",
                "Ankle Dorsiflexion Rocks",
            ),
        ),
        StarterPlan(
            "Swim dryland",
            listOf(
                "Pull Up",
                "Banded Swim Pull",
                "Prone Swimmer Lift",
                "Push Up Plus",
                "Prone Horizontal Abduction",
                "Doorway External Rotation Hold",
                "Flutter Kicks",
                "Streamline Squat Jump",
            ),
            supersets = listOf(listOf("Banded Swim Pull", "Flutter Kicks"), listOf("Prone Horizontal Abduction", "Doorway External Rotation Hold")),
        ),
        StarterPlan(
            "Swim mobility",
            listOf(
                "Streamline Stretch",
                "Thoracic Extension on Foam Roller",
                "Pec Minor Stretch",
                "Sleeper Stretch",
                "Wall Slide",
                "Open Book Rotation",
                "Kneeling Ankle Stretch",
            ),
        ),
        // Finger tendons in every grip, and the parts that are always forgotten.
        StarterPlan(
            "Finger tendons",
            listOf(
                "Tendon Gliding",
                "Low-Intensity Finger Loading",
                "Density Hang",
                "Three-Finger Drag Hang",
                "Two-Finger Pocket Hang",
                "Sloper Hang",
                "Wide Pinch Hold",
                "Lumbrical Hold",
                "Thumb Band Extension",
                "Finger Extension with Band",
            ),
            supersets = listOf(listOf("Wide Pinch Hold", "Thumb Band Extension"), listOf("Lumbrical Hold", "Finger Extension with Band")),
        ),
        StarterPlan(
            "Forgotten parts",
            listOf(
                "Crocodile Breathing",
                "Chin Tuck Hold",
                "4-Way Neck Isometric",
                "Toe Yoga",
                "Towel Scrunch",
                "Ankle CARs",
                "Terminal Knee Extension",
                "Adductor Rockback",
                "Serratus Foam Roller Wall Slide",
                "Isometric Supination Hold",
                "Thumb Band Abduction",
                "Pelvic Floor Hold",
            ),
            supersets = listOf(listOf("Chin Tuck Hold", "Toe Yoga"), listOf("Terminal Knee Extension", "Serratus Foam Roller Wall Slide")),
        ),
        // Kickboxing and volleyball: skills, strength and power, prehab and mobility.
        StarterPlan(
            "Kickboxing skills",
            listOf(
                "Fighter Footwork Drill",
                "Roundhouse Kick Drill",
                "Teep Kick Drill",
                "Switch Kick Drill",
                "Knee Strike Drill",
                "Slip and Roll Drill",
                "Bob and Weave",
                "Pad Work Rounds",
            ),
        ),
        StarterPlan(
            "Kickboxing strength",
            listOf(
                "Landmine Punch",
                "Cable Knee Drive",
                "Slow Kick Extension",
                "Guard Hold",
                "Cable Rotational Punch",
                "Cable Hip Abduction",
                "Kick Chamber Hold",
                "Medicine Ball Chest Pass",
                "Sprawl",
                "Sit-Up Punch",
                "4-Way Neck Isometric",
            ),
            supersets = listOf(listOf("Landmine Punch", "Cable Knee Drive"), listOf("Slow Kick Extension", "Guard Hold"), listOf("Cable Rotational Punch", "Cable Hip Abduction")),
        ),
        StarterPlan(
            "Kickboxing mobility",
            listOf(
                "Front Leg Swing",
                "Lateral Leg Swing",
                "Fire Hydrant Circles",
                "Hip CARs",
                "Cossack Squat",
                "Thoracic Rotation",
                "Neck CARs",
                "Front Split Stretch",
                "Middle Split Stretch",
            ),
        ),
        StarterPlan(
            "Volleyball skills",
            listOf(
                "Block Footwork Drill",
                "Spike Approach Drill",
                "Passing Drill",
                "Serving Practice",
                "Defensive Dive Get-Up",
            ),
        ),
        StarterPlan(
            "Volleyball jump & power",
            listOf(
                "Block Jump",
                "Shuffle Block Jump",
                "Hurdle Hops",
                "Drop Landing",
                "Trap Bar Jump",
                "Medicine Ball Spike Throw",
                "Bulgarian Split Squat",
                "Dig Position Hold",
            ),
            supersets = listOf(listOf("Bulgarian Split Squat", "Dig Position Hold")),
        ),
        StarterPlan(
            "Volleyball prehab",
            listOf(
                "Banded Spike Swing",
                "90/90 Ball Drop",
                "Overhead Ball Wall Dribble",
                "Prone 90/90 External Rotation",
                "Star Excursion Balance",
                "Banded Ankle Eversion",
                "Spanish Squat Hold",
                "Sleeper Stretch",
            ),
            supersets = listOf(listOf("Banded Spike Swing", "Star Excursion Balance"), listOf("Prone 90/90 External Rotation", "Banded Ankle Eversion")),
        ),
        StarterPlan(
            "Volleyball mobility",
            listOf(
                "Thoracic Extension on Foam Roller",
                "Open Book Rotation",
                "Lat Stretch",
                "Cross-Body Shoulder Stretch",
                "Hip Internal Rotation Lift-Off",
                "Couch Stretch",
                "Kneeling Ankle Stretch",
            ),
        ),
    )

    fun exerciseId(name: String): String = BuiltInExercises.stableId("exercise", BuiltInExercises.keyOf(name))
}
