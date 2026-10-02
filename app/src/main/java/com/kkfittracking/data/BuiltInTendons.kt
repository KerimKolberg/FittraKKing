package com.kkfittracking.data

import com.kkfittracking.model.Tendon

/** The tendons the built-in exercises load most, by exercise name. */
object BuiltInTendons {
    private val lists: Map<Tendon, List<String>> = mapOf(
        Tendon.PATELLAR to listOf(
            "Isometric Reverse Nordic Hold", "Approach Jump", "Single-Leg Landing Stick", "Split Squat Jump",
            "Spanish Squat Hold", "Wall Sit", "Split Squat Hold", "Isometric Leg Extension Hold", "Horse Stance",
            "Decline Board Squat", "Heavy Slow Leg Press", "Heavy Slow Squat", "Eccentric Leg Extension",
            "Eccentric Step-Down", "Pistol Squat Negative", "Leg Extension Machine", "Barbell Squat", "Front Squat",
            "Bulgarian Split Squat", "Chair Pose", "Box Jump", "Depth Jump", "Jump Squat", "Long-Lever Wall Sit",
            "Single-Leg Decline Squat", "Poliquin Step-Up", "Backward Sled Drag", "Heavy Slow Split Squat",
            "Sissy Squat", "Lateral Hop", "Split Step Jump", "Streamline Squat Jump",
        ),
        Tendon.QUADRICEPS to listOf(
            "Isometric Reverse Nordic Hold", "Reverse Nordic Curl", "Spanish Squat Hold", "Decline Board Squat",
            "Heavy Slow Squat", "Isometric Leg Extension Hold", "Eccentric Leg Extension", "Single-Leg Decline Squat",
            "Sissy Squat", "Backward Sled Drag", "Poliquin Step-Up",
        ),
        Tendon.ACHILLES to listOf(
            "Slow Deficit Calf Raise", "Sprinting", "Hill Sprints", "A-Skip", "Approach Jump",
            "Single-Leg Landing Stick", "Eccentric Heel Drop", "Single-Leg Calf Raise Hold", "Standing Calf Raise",
            "Seated Calf Raise", "Donkey Calf Raise", "Pogo Hops", "Single-Leg Hop", "Jump Rope", "Calf Stretch",
            "Ankle Dorsiflexion Rocks", "Seated Soleus Hold", "Smith Machine Calf Raise Hold",
            "Bent-Knee Eccentric Heel Drop", "Toe-Elevated Calf Raise", "Heel Raise with Ball Squeeze",
            "Heavy Slow Seated Calf Raise", "Lateral Hop", "Split Step Jump", "Kneeling Ankle Stretch",
        ),
        Tendon.PLANTAR_FASCIA to listOf(
            "Slow Deficit Calf Raise", "Single-Leg Calf Raise Hold", "Standing Calf Raise", "Eccentric Heel Drop",
            "Toe-Elevated Calf Raise", "Short Foot Hold", "Heel Raise with Ball Squeeze",
        ),
        Tendon.PERONEAL to listOf(
            "Single-Leg Balance Hold", "Banded Ankle Eversion", "Lateral Shuffle", "Lateral Bound", "Skater Jump",
            "Agility Ladder", "Single-Leg Landing Stick", "Lateral Hop", "Lateral Lunge Rock",
        ),
        Tendon.TIBIALIS to listOf("Tibialis Raise"),
        Tendon.POSTERIOR_TIBIAL to listOf(
            "Heel Raise with Ball Squeeze", "Banded Ankle Inversion", "Short Foot Hold", "Single-Leg Balance Hold",
            "Bent-Knee Eccentric Heel Drop", "Lateral Hop",
        ),
        Tendon.HAMSTRING to listOf(
            "Eccentric Romanian Deadlift", "Single-Leg Glute Bridge", "Hamstring Walkout", "45-Degree Hyperextension",
            "Sprinting", "Hill Sprints", "Nordic Hamstring Curl", "Isometric Nordic Hold", "Hamstring Bridge Hold",
            "Slider Hamstring Curl", "Romanian Deadlift", "Single-Leg Romanian Deadlift", "Good Morning",
            "Long-Lever Bridge Hold", "Isometric Leg Curl Hold", "Razor Curl", "Eccentric Single-Leg Leg Curl",
        ),
        Tendon.HIP_FLEXOR to listOf(
            "Psoas March", "Seated Knee Lift Hold", "A-Skip", "Sprinting", "Hip Flexor Stretch", "Couch Stretch",
            "Banded Hip Flexion Hold", "Toes to Bar", "Reverse Lunge to Knee Drive", "Flutter Kicks",
            "High Step Hip Mobility",
        ),
        Tendon.ADDUCTOR to listOf(
            "Cossack Squat", "Lateral Lunge", "Side-Lying Adductor Raise", "Lateral Shuffle", "Copenhagen Plank",
            "Adductor Squeeze", "Adductor Machine", "Short-Lever Copenhagen Plank", "Isometric Adductor Ball Squeeze",
            "Sliding Lateral Lunge", "Hip Turnout Stretch", "Lateral Lunge Rock",
        ),
        Tendon.GLUTEAL to listOf(
            "Slow Single-Leg Pelvic Drop", "Side-Lying Clamshell Hold", "Clamshell", "Banded Lateral Walk",
            "Hip Airplane", "Single-Leg Glute Bridge", "Glute Bridge Hold", "Side Plank", "Standing Hip Abduction Hold",
            "Side-Lying Hip Abduction Hold", "Hip Hike",
        ),
        Tendon.ROTATOR_CUFF to listOf(
            "Slow Cable External Rotation", "Slow High Cable External Rotation", "Slow High Cable Internal Rotation",
            "Band Internal Rotation Hold", "Side-Lying External Rotation", "90/90 External Rotation Hold",
            "Prone Y-T-W Raise", "Band Pull-Apart", "Sleeper Stretch", "Wall Slide", "Cable External Rotation",
            "Eccentric External Rotation", "Shoulder CARs", "Face Pull", "Doorway External Rotation Hold",
            "Doorway Internal Rotation Hold", "Bottoms-Up Kettlebell Carry", "Full Can Raise",
            "Prone Horizontal Abduction", "Cable Internal Rotation", "Reverse Ball Catch", "Push Up Plus",
            "Prone Swimmer Lift", "Banded Swim Pull",
        ),
        Tendon.BICEPS to listOf(
            "Lock-Off Hold", "Eccentric Barbell Curl", "Isometric Curl Hold", "Incline Dumbbell Curl",
            "Eccentric Hammer Curl", "Offset Pull Up", "Typewriter Pull Up",
        ),
        Tendon.LATERAL_ELBOW to listOf(
            "Finger Extension with Band", "Wrist Roller", "Dumbbell Pronation-Supination", "Tyler Twist",
            "Eccentric Wrist Extension", "Reverse Wrist Curl", "Reverse Curl", "Isometric Wrist Extension Hold",
            "Forearm Extensor Stretch", "Isometric Pronation Hold",
        ),
        Tendon.MEDIAL_ELBOW to listOf(
            "Reverse Tyler Twist", "Wrist Roller", "Dumbbell Pronation-Supination", "Eccentric Wrist Flexion",
            "Wrist Curl", "Wrist Flexor Stretch", "Isometric Wrist Flexion Hold", "Isometric Pronation Hold",
            "Barbell Finger Curl", "Finger Flexor Stretch",
        ),
        Tendon.TRICEPS to listOf(
            "Overhead Cable Triceps Extension", "Eccentric Dip", "Parallel Bar Triceps Dip", "EZ-Bar Skullcrusher",
            "Isometric Triceps Pushdown Hold", "Eccentric Triceps Pushdown",
        ),
        Tendon.FINGER_FLEXORS to listOf(
            "Half-Crimp Hang", "Open-Hand Hang", "Hangboard Hang", "No-Hang Lift", "Campus Board", "Rice Bucket",
            "Bouldering", "Lock-Off Hold", "Dead Hang", "Towel Hang", "Plate Pinch Hold", "Flexed-Arm Hang", "Climbing",
            "Hangboard Repeaters", "Barbell Finger Curl", "Pinch Block Lift", "Finger Flexor Stretch",
            "Weighted Pull Up",
        ),
    )

    /** Every exercise name mentioned above, to check that each one is in the catalog. */
    val exerciseNames: Set<String> get() = lists.values.flatten().toSet()

    private val byId: Map<String, List<Tendon>> = lists
        .flatMap { (tendon, names) -> names.map { BuiltInExercises.stableId("exercise", BuiltInExercises.keyOf(it)) to tendon } }
        .groupBy({ it.first }, { it.second })

    /** The tendons a built-in exercise loads; empty for others. */
    fun of(exerciseId: String): List<Tendon> = byId[exerciseId].orEmpty()
}
