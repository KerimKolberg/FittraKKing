# FitTraKKing

_Formerly KK-Fittracking. Updating keeps all your data, and backups in Downloads/KK-Fittracking move to Downloads/FitTraKKing by themselves._

A gym workout tracker for Android, inspired by FitNotes. Works fully offline: all data stays on your phone.

See [ROADMAP.md](ROADMAP.md) for the plan and progress.

## What it does today

- **Workout log**: one screen per day, with previous/next day arrows and a calendar
- **Exercise library**: about 300 built-in exercises, including yoga poses, meditation and breathing, dance, and sport-specific prehab for court sports, climbing, sprinting and fighting sports. Browse by body section (Legs → Hamstrings → Eccentrics → Nordic curl) or by training style (Stretching, Mobility, Isometrics… → muscle group). Exercises that train several muscles, like the deadlift, are listed under each of them. Search, filters and your own custom exercises
- **Logging sets**: weight × reps, reps only, distance and time, or time only. Tap a set to edit or delete it
- **Set plans**: sets, reps, weight and rest per exercise, with "Set 2 of 3" while you train
- **Last time** hint and a full **history** per exercise
- **Rest timer** that starts when you save a set, with vibration and a notification
- **Settings**: kg/lb (km/mi), default rest time, light/dark theme
- **Personal records**: a star on every set that beat your previous best, estimated 1RM, rep maxes
- **Progress graphs** per exercise (estimated 1RM, heaviest weight, volume, reps, time, distance)
- **Plans**: group exercises (Push, Upper body, Tendon health…); one exercise can be in many plans. Add a plan to a workout day, filter the exercise list by plan, or start from the starter plans, then change them like any plan: tap an exercise to set its sets, reps and weight (including KK Upper body and KK Lower body: blocks of a muscular exercise and its tendon work, as 3-round supersets with sets, reps and weights). Copy an earlier day's exercises to today
- **Beyond the gym**: mobility, stretching, isometric holds, slow eccentrics for tendons (with tempo), plyometrics (with jump height) and sports sessions (with intensity and notes)
- **HIIT** (under Cardio): an interval timer for high and low intensity with rounds, a get-ready countdown, beeps and vibration
- **Drop sets**: planned per exercise, on the last set or as the whole exercise, with the number of drops, reps per drop and a percentage or fixed weight; or tap "Drop set" any time
- **Supersets**: group 2 to 12 exercises and log them round by round. "Superset edit" arranges a day or a plan by dragging, with the time to walk to the next exercise and the rest after each round; plans can bring their supersets along. Set the rounds of a superset, a drop set on the last round for all, and per exercise how many rounds it joins or its own drop set choice
- **Tendons**: isometric and eccentric exercises show the tendons they train (patellar, Achilles, rotator cuff, elbow tendons…) with a drawing and what each one connects; filter by tendon in the library. Tendon isometrics and Tendon heavy slow starter plans; skills, strength and mobility plans for climbing, tennis, swimming, kickboxing and volleyball; Finger tendons (every grip: drag, pockets, slopers, pinches, density hangs, low-intensity loading) and Forgotten parts (neck, feet and toes, thumbs, knee, breath, pelvic floor)
- **Watch app** (Wear OS): follow the guided workout on the wrist and log sets there, synced with the phone
- **Workouts to Health Connect**: finished workouts show up in Samsung Health and Google Fit (gym work as strength training, runs, rides and sports as their own workouts), with an estimated calorie count if you like
- **Calories**: an estimate for each day from MET values and your bodyweight (±20–30 %), or what the watch measured when it recorded the activity
- **Daily steps**: the day's steps and distance from Health Connect (what Samsung Health, Google Fit and your watch record), shown on each day
- **Kilos, pounds or machine levels**: each exercise can have its own weight unit (tap "Unit" under the weight), for machines marked in lb or with just pin numbers
- **Left and right**: exercises done one side at a time have Left and Right buttons; a left and a right set count as one set, so supersets and the guide wait for both sides
- **Hold timer**: time an isometric hold on the phone or the watch; it buzzes until you tap Done and keeps counting (−0:10), and the time held goes into the set
- **Your library order**: Settings → Exercise library puts the body sections and training styles in the order you like
- **Guided workout**: press Start on a day with a plan and the app takes you from exercise to exercise (round by round in supersets), moving on after each set you save. Pause for longer breaks, skip, or stop early and move what's left to the next day or any day you pick; a notification shows what's next with the phone locked
- **Day analysis**: how much of the day's plan is done, and what was done and what wasn't
- **How to do it**: a description and video links (YouTube or any page) on every exercise
- **Progress**: graphs per exercise with the all-time high and low marked, and records
- **Graphs & records** (menu): any exercise's progress by date (heaviest weight, estimated 1RM, volume, reps, time, distance…) and your body tracker values (bodyweight, body fat, measurements), over 1 month, 3 months, 1 year or everything, with all-time high and low; plus every exercise's personal record, newest first, one tap from its graph
- **Gamification**: XP and levels, a weekly goal with streaks, 17 achievements, and celebrations when you hit a record
- **Body tracker**: bodyweight, body fat and body measurements with graphs
- **Your data**: one tap saves a backup plus workouts and body measurements as CSV to **Downloads/FitTraKKing**, dated in their names (e.g. `FitTraKKing_backup_2026-10-03_14-30.json`); an automatic daily or weekly backup goes to the same folder (the newest 8 are kept). Copy the folder to Google Drive or a computer now and then. "Save as…" still lets you pick any place, and Restore opens in that folder
- **Tendon pain log**: rate a tendon's pain 0–10 before and after training and the next morning; a green, yellow or red light tells you to add load, hold, or step back (the pain-monitoring model physios use). A morning card asks about yesterday's tendons
- **Suggestions**: the next weight, reps or hold time from your last session (all sets made: add the smallest jump; not yet: one more rep), held back or stepped down when a tendon hurts
- **Warm-up sets and plates**: lighter ramp-up sets before heavy lifts with the plates per side, and a plate calculator for any weight (bar weight in Settings)
- **Training load & tendons** (menu): sets per muscle this week as a coloured map, tendons loaded, this week's load against your usual week (flags sudden jumps), and areas you haven't trained in two weeks
- **Sport warm-ups**: 5–8 minute warm-ups for gym, kickboxing, volleyball, climbing, tennis, swimming and running, put at the top of the day and started in the guided workout with one tap
- **Reminders**: a notification on the days and time you choose, with what's planned and a Start button
- **Home-screen widget**: today's plan, how much is done, what's left, and ▶ Start

## Try it on your phone

Every push builds a debug APK on GitHub:

1. Open the repository's **Actions** tab and pick the latest green **Android build** run.
2. Download the **FitTraKKing-phone-debug-apk** artifact and unzip it. Wait until the download is complete: a
   cut-off file shows no app icon and Android says there is a problem with the app file.
3. Open `FitTraKKing-phone-debug.apk` on the phone (allow "Install unknown apps" when asked).
   `FitTraKKing-watch-debug.apk` (in the watch artifact) is for the watch only.

Builds are signed with the shared debug key in `signing/`, so a newer build installs over an older one.
(Builds from before the watch app used a different key each time: back up in Settings, uninstall once,
install the new build and restore.)

## Try it on your watch (Wear OS 3+: Galaxy Watch 4 and later, Pixel Watch)

The watch app shows the guided workout, with the next exercises coming up: the exercise and set, the values to adjust with + and − (weight,
reps, time, distance, jump height, effort; tap a value to type it, hold + or − for big steps), a hold timer
that buzzes until Done and counts on as −0:10, Log set, the rest countdown (it buzzes when the rest is over),
Pause, Skip and Stop, and your live heart rate (saved with each set). Runs, walks, rides and sessions can be
recorded with the watch's sensors: time, GPS distance, steps, heart rate and calories. A **Workout** tile
shows the exercise and progress at a glance (long-press the watch face or swipe to the tiles to add it).
On first start the watch asks for heart rate, physical activity and location; each one is optional. It talks to the phone
over Bluetooth (or Wi-Fi) through the Wear OS Data Layer; the phone keeps all the data.

1. Download the **FitTraKKing-watch-debug-apk** artifact from the same run as the phone APK, and unzip it.
2. On the watch: Settings → About watch → Software → tap *Software version* 5 times to turn on Developer
   options; then in Developer options turn on *ADB debugging* and *Wireless debugging* (same Wi-Fi as the computer).
3. Pair and install from a computer with `adb pair <ip:port>` (the pairing code is on the watch),
   `adb connect <ip:port>`, then `adb install FitTraKKing-watch-debug.apk`. Without a computer, a phone app such as
   Bugjaeger or Wear Installer can install the APK over the same Wi-Fi.
4. Open FitTraKKing on the phone once, then on the watch. Start a workout from either one.

## Build it yourself

Open the project in Android Studio, or from a terminal (JDK 17+ and the Android SDK are required):

```sh
./gradlew assembleDebug        # APK in app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # unit and database tests
```

## Project layout

```
app/src/main/java/com/kkfittracking/
  model/      plain data types, units, and formatting
  data/       repositories, built-in exercises, settings
  data/db/    Room entities, DAOs, and the database
  timer/      rest and interval timers, beeps, vibration and notifications
  guide/      the guided workout, its notification, and the link to the watch
  ui/         Compose screens, one package per screen, plus navigation
wear/         the Wear OS app (Compose for Wear OS)
wearprotocol/ what the phone and the watch send each other (plain Kotlin)
```

Stack: Kotlin, Jetpack Compose (Material 3), Room, DataStore, Navigation Compose.
The database is designed so cloud sync can be added later without migrating data (see the roadmap).

## Icon

The launcher icon is generated from `branding/logo.jpg`. To change it, replace that file and run
`python3 branding/make_icons.py` (needs Pillow). It writes the adaptive icon layers for every screen
density, a monochrome layer for Android 13+ themed icons, and the 512 px icon Google Play asks for.

