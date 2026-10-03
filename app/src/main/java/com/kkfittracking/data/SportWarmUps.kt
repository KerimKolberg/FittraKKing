package com.kkfittracking.data

/**
 * Short warm-ups before a sport or a gym session (5–8 minutes): pulse up, the joints the sport
 * uses most through their range, then a few quick, sport-like moves. Added to the top of the day
 * and started in the guided workout with one tap.
 */
object SportWarmUps {
    data class WarmUp(val sport: String, val exercises: List<String>)

    val all: List<WarmUp> = listOf(
        WarmUp("Gym", listOf("Jump Rope", "Cat-Cow", "World's Greatest Stretch", "Hip CARs", "Shoulder CARs", "Band Pull-Apart")),
        WarmUp(
            "Kickboxing",
            listOf("Jump Rope", "Neck CARs", "Hip CARs", "Front Leg Swing", "Lateral Leg Swing", "Fire Hydrant Circles", "Shadow Boxing"),
        ),
        WarmUp(
            "Volleyball",
            listOf("Ankle CARs", "Lateral Shuffle", "World's Greatest Stretch", "Band Pull-Apart", "Banded Spike Swing", "Pogo Hops", "Block Jump"),
        ),
        WarmUp(
            "Climbing",
            listOf("Wrist Mobility", "Tendon Gliding", "Shoulder CARs", "Scapular Pull Up", "Hip CARs", "High Step Hip Mobility", "Dead Hang"),
        ),
        WarmUp(
            "Tennis",
            listOf("Ankle CARs", "Lateral Shuffle", "Open Book Rotation", "Band Pull-Apart", "Doorway External Rotation Hold", "Split Step Jump"),
        ),
        WarmUp(
            "Swimming",
            listOf("Shoulder CARs", "Band Pull-Apart", "Streamline Stretch", "Thoracic Rotation", "Banded Swim Pull", "Ankle CARs"),
        ),
        WarmUp(
            "Running & sprinting",
            listOf("Ankle Dorsiflexion Rocks", "Front Leg Swing", "Lateral Leg Swing", "Hip CARs", "A-Skip", "Pogo Hops"),
        ),
    )

    fun exerciseIds(warmUp: WarmUp): List<String> = warmUp.exercises.map(StarterPlans::exerciseId)
}
