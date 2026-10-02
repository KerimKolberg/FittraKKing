# KK-Fittracking — Roadmap

A customized Android gym workout tracker inspired by FitNotes, published on Google Play.

## Key decisions

| Decision | Choice | Notes |
|---|---|---|
| Platform | Android only | Distributed via Google Play |
| Language / UI | Kotlin + Jetpack Compose (Material 3) | Native, modern Android toolkit |
| Local database | Room (SQLite) | All data stored on the device |
| Architecture | MVVM + repository layer | UI → ViewModel → Repository → Room |
| Dependency injection | Manual (`AppContainer`) | Simpler than Hilt for one app module; can switch later if the app grows |
| Connectivity | Offline-only for v1 | Designed so cloud sync can be added later (see below) |
| Units | kg / lb, user-selectable | Store weights in kg internally, convert for display |

### Keeping the door open for cloud sync

v1 is fully offline, but the data layer follows these rules so sync/accounts can be added later without a painful migration:

1. **UUID primary keys** (not auto-increment integers), so records created on different devices never collide.
2. **`createdAt` / `updatedAt` timestamps** on every synced table, for change tracking and conflict resolution.
3. **Soft deletes** via a nullable `deletedAt` column, so deletions can be propagated to other devices.
4. **Repository layer**: screens never access Room directly. A remote data source can later be added behind the repositories.
5. **Room migrations** are written for every schema change (no destructive migrations once released).

## Feature list

### Must-have (MVP)
- [x] Exercise library: built-in exercises + custom exercises, grouped by category (chest, back, legs, shoulders, arms, core, cardio)
- [x] Exercise types: weight × reps, reps only, distance/time (cardio), time only
- [x] Workout logging: add exercises to a day, log sets (weight × reps), edit/delete sets
- [x] Previous-session history shown while logging an exercise
- [x] Calendar view of past workouts
- [x] Per-exercise history screen
- [x] Rest timer with notification/vibration when finished
- [x] Settings: kg/lb, default rest time, theme

### Should-have
- [x] Personal records (PRs) and estimated 1RM, with a marker when a PR is beaten
- [x] Progress graphs per exercise (max weight, estimated 1RM, volume, reps)
- [x] Routines/templates and copying a past workout to today
- [x] Body tracker: bodyweight, body fat %, measurements, with graphs
- [ ] Backup/restore to a local file / Google Drive (via the Android file picker)
- [ ] CSV export
- [ ] Set and workout comments
- [ ] Plate calculator

### Nice-to-have / custom (Phase 5)
- [ ] Features found missing after real use, to be added here
- [x] Supersets / circuits (Phase 2c)
- [ ] RPE / RIR per set
- [ ] Progression suggestions
- [ ] Home-screen widget
- [x] Wear OS companion (Galaxy Watch, Pixel Watch): the guided workout on the wrist (next exercise, log weight and reps, rest countdown with a buzz, pause, skip and stop, start today's plan), synced with the phone over Bluetooth through the Wear OS Data Layer; stays on screen dimmed (always-on)
- [x] On the watch: live heart rate (saved with each set), a Workout tile, every exercise type (distance, height, effort, rounds), and recording cardio and sessions with the watch's sensors (time, GPS distance, steps, heart rate, calories)
- [x] Sports and athletic training: bouldering, kickboxing, boxing, sprinting, hill sprints, heavy bag and shadow boxing rounds, and about 55 prehab and power exercises for court sports, climbing, sprinting and fighting (pelvic drops, clamshell holds, ankle eversion, balance holds, Y-T-W, sleeper stretch, hangboard, no-hang lifts, finger extensions, rotational throws…); peroneal and hip flexor tendons; starter plans for court sports, climbing, sprint & jump and kickboxing
- [x] Daily steps and distance from Health Connect on every day (read only)
- [x] Set plans edited from the plan screen; the user's own order of library sections and training styles
- [x] Move what's left of a day to the next day or a picked day: from the summary after stopping a guided workout, or from the day's menu
- [x] Graphs & records screen: exercise graphs by date, body tracker graphs, time ranges, and all personal records
- [x] Clearer names: "New superset" and "Superset edit"
- [x] Watch: type values with the keyboard, 0.5 kg / 1 lb taps and big steps on a long press; an ongoing workout icon on the watch face for one tap back into the app
- [x] Hold timer on phone and watch: counts down, buzzes until Done, counts on past zero (−0:10) and fills in the time held
- [x] "Then: …" shows the next exercises during a guided workout (phone, notification and watch)
- [x] Each exercise can use its own weight unit: kg, lb or machine levels (database version 7)
- [x] More tendon training and sport plans: 70 new exercises. Tendon work: isometric holds (long-lever wall sit, soleus, leg curl, adductor squeeze, hip abduction, doorway rotations, wrist and triceps holds), heavy slow and eccentric work (split squats, bent-knee heel drops, toe-elevated calf raises, razor curls, eccentric hammer curls and pushdowns), and a new posterior tibial tendon (ball-squeeze heel raises, banded inversion, short-foot holds). Climbing (repeaters, weighted, archer and offset pull ups, finger curls, pinch blocks, front lever raises, high-step and drop-knee mobility), tennis (split-step and lateral hops, med ball side throws and shot puts, Pallof press, open books, hip internal rotation), swimming (swim pulls, prone swimmers, flutter kicks, streamline and pec minor stretches, foam-roller thoracic extension). Starter plans: Tendon isometrics, Tendon heavy slow, Climbing/Tennis strength and mobility, Swim dryland and Swim mobility
- [x] Finger tendons in every grip and the forgotten parts: 46 more exercises. Max hangs on small edges, three-finger drag, two-finger pockets, full crimp, slopers, one-arm hangs, density hangs, low-intensity finger loading, wide pinches, finger pulls, lumbricals, grippers, putty, thumb band work, radial/ulnar deviation, sledgehammer levering, tendon gliding; neck (chin tucks, 4-way isometrics, neck curls and extensions, CARs, upper trap stretch) and feet & toes (toe yoga, towel scrunches, toe and heel walks, toe spreads, big toe holds and stretches) as new muscles; terminal knee extensions, Peterson and lateral step-downs, adductor rockbacks, serratus wall slides, supination holds, suitcase carries, McGill curl-ups, pelvic floor holds, crocodile and 90/90 breathing. New tendons: thumb (De Quervain's) and IT band. Starter plans: Finger tendons, Forgotten parts
- [x] Kickboxing and volleyball: 42 more exercises. Kickboxing: pad work rounds, roundhouse, teep, switch kick and knee drills, slips and rolls, footwork, sprawls, bob and weave, kick chamber holds, slow kick extensions, cable knee drives and hip abduction, leg swings, fire hydrant circles, front and middle split stretches, landmine, band and cable punches, guard holds, dumbbell shadow boxing, med ball chest passes, sit-up punches. Volleyball: spike approach, block footwork, passing and serving drills, block and shuffle block jumps, drop landings, hurdle hops, trap bar jumps, star excursion balance, dig position holds, dive get-ups, 90/90 ball drops, overhead wall dribbles, banded spike swings, prone 90/90 external rotations, med ball spike throws. Starter plans: Kickboxing skills, strength and mobility; Volleyball skills, jump & power, prehab and mobility
- [x] Left and right sets for one-sided exercises (phone and watch): both sides make one set in plans, supersets and the guide (database version 8)
- [x] Finished workouts written to Health Connect (at the end of a guided workout, or from a day's menu): gym blocks as strength training, runs, rides and sports sessions as their own workouts; sending a day again replaces it
- [x] Calorie estimate per day and at the end of a workout (MET × bodyweight × time; the watch's measured calories when it recorded the activity); optionally sent to Health Connect
- [ ] Maybe later: iPhone version (Kotlin Multiplatform + Compose Multiplatform; HealthKit; a separate Apple Watch app)
- [ ] Health Connect: read daily steps (and optionally heart rate) that Samsung Health, Google Fit, Fitbit and watches already record; write finished workouts back so they show up in those apps
- [ ] Cloud sync + accounts (see "Keeping the door open for cloud sync")

## Data model

Category, Exercise, Workout, WorkoutExercise and Set exist since Phase 1; Routine, RoutineExercise and BodyMeasurement since Phase 2 (database version 2); exercise tempo/perSide and set RPE since Phase 2b (version 3); drop sets and supersets since Phase 2c (version 4); muscle, training style, set plans, links and plan supersets since Phase 2d (version 5) (`app/src/main/java/.../data/db/Entities.kt`). All tables use `id: UUID`, `createdAt`, `updatedAt`, `deletedAt?`.

- **Category** (a body section): name, color, sortOrder
- **Exercise**: name, categoryId, muscle, style, type (WEIGHT_REPS / REPS / DISTANCE_TIME / TIME / TIME_WEIGHT / REPS_HEIGHT / SESSION / INTERVALS), notes (how to do it), links, tempo, perSide, plan (sets, reps, weight, rest, drop sets, interval timings as JSON), isCustom
- **Workout**: date, comment
- **WorkoutExercise**: workoutId, exerciseId, sortOrder, supersetId?, transitionSeconds?, roundRestSeconds?
- **Set**: workoutExerciseId, sortOrder, weightKg?, reps?, distanceMeters?, durationSeconds?, rpe?, comment, isDropSet (isWarmup to come later)
- **Routine** (a plan): name, notes
- **RoutineExercise**: routineId, exerciseId, sortOrder, supersetId?, transitionSeconds?, roundRestSeconds?
- **BodyMeasurement**: date, metric, value (kg, % or cm)

## Phases

### Phase 0: Planning
- [x] Choose platform and stack
- [x] Define feature list and priorities
- [x] Draft data model
- [ ] Sketch main screens (Today/log, Exercise picker, Exercise log, Calendar, History, Settings)

### Phase 1: MVP
- [x] Android project setup (Gradle, Compose, Room, CI build)
- [x] Database + repositories + seed data for built-in exercises
- [x] Exercise library screens
- [x] Workout logging screens
- [x] Calendar + history
- [x] Rest timer (runs while the app process is alive; a foreground service can make it bulletproof later)
- [x] Settings
- [ ] Try it on a real phone and collect fixes
- **Goal:** installable APK, usable for personal training

### Phase 2: Insights
- [x] PRs and estimated 1RM (Epley), with a star on record-setting sets and rep maxes
- [x] Progress graphs (per exercise, plus body measurements)
- [x] Routines, adding a routine to a day, and copying a day's exercises to today
- [x] Body tracker (bodyweight, body fat, 9 circumference measurements)
- [ ] Try it on a real phone and collect fixes
- **Goal:** feature parity with FitNotes' core

### Phase 2b: Exercise variety
Broaden the library beyond gym lifts. Each group needs the right way to log it:

| Group | Examples | How a set is logged |
|---|---|---|
| Mobility | hip CARs, thoracic rotations, ankle mobility | reps, or time; per side |
| Stretching | hamstring, hip flexor, couch stretch | time held; per side |
| Isometrics | wall sit, Copenhagen plank, split-squat hold, mid-thigh pull | time held + optional weight |
| Slow eccentrics (tendons) | Nordic curls, eccentric heel drops, decline squats, Tyler twist | weight × reps + tempo (e.g. 5 s down) |
| Plyometrics | box jumps, broad jumps, pogo hops, depth jumps | reps + optional height/distance |
| Sports | tennis, table tennis, volleyball, padel, badminton, football, basketball | session time + intensity (RPE) + notes |

- [x] New exercise types: "time + weight" for loaded holds, "reps + height/distance" for jumps, "session" for sports
- [x] Optional tempo per exercise (e.g. 5-0-1-0) and "each side" flag
- [x] Session intensity (RPE 1–10) and notes, for sports and conditioning
- [x] New categories: Mobility, Stretching, Isometrics, Tendons & eccentrics, Plyometrics, Sports
- [x] Built-in exercises for each category (50 new)
- [x] Plans (formerly "routines"): one exercise can be in many plans, "Add to plans" on every exercise, plan filter in the exercise picker, starter plans (Push, Pull, Legs, Upper body, Tendon health, Mobility flow)

### Gamification
- [x] XP for sets, workouts, personal records, new exercises and weekly goals; levels
- [x] Weekly workout goal and week streaks
- [x] 17 achievements, including variety ones for mobility, isometrics and eccentrics, plyometrics, sports, HIIT, yoga or meditation, and dance
- [x] Celebrations after saving a set (record, achievement, level up, weekly goal)
- [ ] Ideas for later: challenges (e.g. "30 days of mobility"), yearly summary, share cards

### Phase 2c: Training techniques
- [x] Drop sets: a "Drop set" option when logging, each drop lighter by a set percentage (Settings, default 20%, rounded to 0.5 kg / 1 lb); shown as ↘ under the set they continue
- [x] Supersets and circuits of 2 to 6 exercises on a workout day: pick them in order, log round by round (the app opens the next exercise after each set), rest timer only after the last exercise of a round, ungroup any time
- [x] "Superset edit" for a day: drag exercises by their ≡ handle between a superset's header and its end line, set the time to walk to the next exercise and the rest after each round
- [x] While logging: a "Go to <next exercise>" countdown between superset exercises
- [x] Supersets saved inside plans: when adding the plan to a day, choose "With supersets" or "One by one"

### Phase 2d: Smarter exercises
- [x] Set plan per exercise: sets, reps, weight and rest between sets; "Set 2 of 3" while logging. A superset's timing replaces the exercise's own rest, with a note saying so
- [x] Drop sets planned in the set plan: with several sets they follow the last set, with one set the whole exercise is a drop set. Choose the number of drops, reps per drop (more or fewer than the sets), and a percentage or a fixed weight, with a preview such as "100 → 80 → 64 kg" from the weight entered
- [x] Library in three levels: section → muscle → training style (e.g. Legs → Hamstrings → Eccentric → Nordic curl). Training styles (Mobility, Stretching, Isometrics…) are main sections too, with muscle groups under them. An exercise can train several muscles (main one first) and is listed under each. The old Triceps, Biceps, Mobility, Stretching, Isometrics, Tendons and Plyometrics categories are folded into Arms, Legs, Core, Full body and so on; the picker filters by muscle and style, and search finds them too
- [x] HIIT under Sports: an "Intervals" exercise type and an interval timer with high and low intensity phases, rounds, a get-ready countdown, and beeps and vibration at each change (HIIT Intervals 30/30, Tabata 20/10, Sprint Intervals 15/45)
- [x] About tab on every exercise: a description of how to do it first, then YouTube and other video or page links, and a "Find on YouTube" search
- [x] Superset rounds: a number of rounds for the superset, a drop set on the last round for all, and per exercise the rounds it joins (e.g. only the last) and its own drop set choice
- [x] The "Drop set" button opens the drop set settings (percent or weight, drops, reps)
- [x] HIIT moved to Cardio; yoga poses (also counted as stretching, mobility or isometrics), meditation and breathing, dance; about 100 more exercises
- [x] Several training styles per exercise; body section icons drawn in the app
- [x] Select several exercises on a day and remove them at once
- [x] Progress graphs mark the all-time high and low, with a summary under the graph
- [ ] Later: play videos inside the app (only through YouTube's official player and terms), and your own recorded videos
- [x] Guided workout (play button): starts a day's plan and opens each exercise in turn, round by round in supersets and straight into planned drop sets. Saving a set moves on by itself; the rest ends with "Next: …". Pause for longer breaks, Skip an exercise, Stop when out of energy. An ongoing notification (a foreground service, so the workout and timers keep going with the screen off) shows the exercise, the plan done and the training time, with Pause, Skip and Stop
- [x] Tendons: isometric, eccentric and heavy slow exercises name the tendons they load (patellar, quadriceps, Achilles, plantar fascia, tibialis, hamstring, adductor, gluteal, rotator cuff, biceps, tennis and golfer's elbow, triceps, finger flexors), with a drawing of the joint, what the tendon connects and what it is known for. Filter the Isometrics and Eccentrics sections by tendon, or search "patellar"
- [x] Day analysis: the percent of the day's plan done, and which exercises were done, partly done and not done (set plans, superset rounds and drop sets count; exercises without a set plan count as 3 sets). A summary with the training time when a guided workout ends

### Phase 3: Data safety
- [x] Backup to a JSON file anywhere the file picker reaches (Drive, Downloads…), with "last backup" shown in Settings
- [x] Restore with a summary and confirmation; all-or-nothing, so a damaged file changes nothing
- [x] CSV export of workouts and body measurements, in the user's units
- [x] Database migration tests: real databases of every earlier version upgraded to the current one with no data lost
- **Goal:** safe for other users' data

### Phase 4: Google Play release
- [ ] App name, icon, screenshots, store listing
- [ ] Move UI text into `strings.xml` (needed for translations)
- [ ] Privacy policy (simple, since data stays on device)
- [ ] Signed release build (AAB), Play App Signing
- [ ] Internal testing → closed testing → production
- **Goal:** published app

### Phase 5: Customization
- [ ] Collect missing features from real-world use
- [ ] Prioritize and implement them as v1.x updates
