# Hyperlog — spec

A native Android app that runs a periodized strength program, logs what happened, and tells you
what to do when the plan meets real life.

Hyperlog replaces the Angular/Capacitor app now in `hyperlog-legacy`. That app grew from a PWA
into nutrition, VBT, a desktop editor and more. This one does three things.

1. **Run the program.** Show today's prescription from where you are in the program.
2. **Log the session** against that prescription.
3. **Coach.** When you can't train as planned, propose a concrete change and apply it only
   after you accept it.

Out of scope: nutrition, VBT, Health Connect, progress photos, body measurements, desktop
configuration, iOS, web, any server.

## Stack

- Kotlin, Jetpack Compose, Material 3. Android only. minSdk 26, targetSdk 36, compileSdk 37 (current Compose needs it).
- **Material 3 Expressive** (the current Compose Material 3 expressive components, shapes and
  motion).
- **Follows the Android theme**: Material 3 dynamic colour (Material You) from the wallpaper, and
  light/dark from the system setting. No in-app theme picker. Phones without dynamic colour
  (Android < 12) fall back to a palette built from the Hyperlog logo.
- **Every symbol is a Material Symbol** (Rounded). The prototype bundles the 15 MB variable font;
  the app ships only the icons it uses (vector drawables or a subset font).
- Room for storage. Everything lives on the device.
- kotlinx.serialization for program files, backup import and coach changes.
- No network code in v1.
- Application ID `com.hyperlog.app`, debug builds `.dev`.

## Distribution

- **GitHub Releases + Obtainium**, no Play Store fee. Pushing a version tag makes GitHub Actions
  build and sign a release APK and attach it to a release in a public `villekivela/hyperlog-releases`
  repo (the source repo stays private). Phones install and update through Obtainium watching that
  release page. Fallback: Firebase App Distribution (free, email invites).
- Signed with a release key kept outside the repo, backed up, and stored as a GitHub Actions secret.
  The legacy app was signed with the old machine's debug key, so the new app can't install over it:
  uninstall the legacy app when switching; its data stays in the backup.
- Check Google's developer-verification rules for apps installed outside the Play Store before the
  first release; register if required.
- During development, debug builds install over USB with adb alongside the release build.
- **Other users** get the same app with no accounts and no server; each has their own data on their
  phone. **No third-party programs are bundled**: Nippard's programs are paid and copyrighted, so
  each user imports program files they own. The app ships with the exercise library and one
  original starter program, Hyperlog Full Body (`docs/programs/full-body-3-day.md`).
  - **Its week-12 bench test** ("100 kg if the week-11 estimate is 97 kg or more, else a single
    @ 1 RIR") is a load rule, which the program format can't hold. It is written as a heavy single
    (1 rep @ 1 RIR, its weight suggested from the estimate like any new rep range), and the 100 kg
    condition is in that slot's note.
- Starting weights from a legacy backup are a one-off "Import starting weights" action in settings,
  not built into the app.

## Domain

| Term | Meaning |
| --- | --- |
| **Program** | A periodized plan: blocks of weeks, each week a set of training days. |
| **Block** | A run of weeks (`weekStart..weekEnd`) that share the same days, e.g. Accumulation 1–7. |
| **Day** | One planned session: an ordered list of groups. |
| **Group** | Exercises done together: `straight` or `superset`. |
| **Slot** | A place in a day for one exercise. It has a planned exercise and alternatives. |
| **Set prescription** | A work set's reps (fixed or a range), optional target RIR, rest, intensity technique and back-off rule. |
| **Position** | Where you are: program, week, next day. |
| **Session** | What was done on a date: sets with weight, reps, RIR. It's a record and is never recalculated. |
| **Change** | A proposed edit to the plan (see Coaching). Accepted changes are kept as history. |
| **Gym** | A place you train, with its equipment. Picked at session start. |
| **Equipment item** | One piece of equipment at a gym (a cable station, a leg press), with a weight ladder. Dumbbells and plates are global. |
| **Weight ladder** | The weights an equipment item can actually be set to. A **rung** is one step on it. |
| **Estimate** | The per-exercise (per-machine for machines) e1RM from reps + RIR that drives load suggestions. |
| **Intensity technique** | How a work set is extended: drop set, myo-reps, lengthened partials, iso hold, failure. |

Programs use a JSON shape close to the legacy one, `blocks[].days[].groups[].slots[]`, each slot
with an `exercise`, its `alternatives` and its `sets[]`. **`docs/program-format.md` is the format's
reference.** Legacy programs are **not** imported; programs are written fresh for this app.

- **Sets belong to the slot.** A slot's set prescriptions apply to every alternative by default; an
  alternative may override them with its own sets.
- **Only work sets are written.** Warm-ups are generated (Screens → Today), and drops and myo
  mini-sets are part of their technique, so set lines have no type. A line can stand for several
  identical sets (`count`).
- **One program runs at a time.** Importing another replaces it and starts it at week 1, day 1;
  the old one stays as the record of its sessions. Past sessions are kept.

**Authoring.** Programs are JSON files, written with Claude's help from a Nippard PDF or sheet,
and imported through a strict validator that names the exact path of every error
(`blocks[1].days[0].groups[2]…: unknown exercise "bb_row"`). No in-app program editor in v1.

**Top set and back-off.** A set can be a back-off from an earlier set of the same exercise: its
weight is that set's weight minus a percentage (default 10%), rounded down to a rung (the lightest
rung if none is below). Progression judges the top set. Until the top set is logged, the back-off
rows show the weight worked out from the top set's planned or typed weight.

**Week overrides.** Days are defined once per block; a set line's fields can be overridden for
given weeks of the block, e.g. `"weeks": {"1": {"rir": 3}, "4": {"count": 2, "technique": null}}`.
Weeks are program weeks (`"7-11"` for a range) inside the block. A day or a block can override all
of its sets for given weeks too: a target RIR (`rir`) or a change to it (`rirAdd`), no techniques
(`technique: null`), and `fewerSets` (sets removed from the end of every slot, one always stays).
The line's own override applies first, then the block's, then the day's. This covers ramp-in
weeks, techniques that start in week 3, and deload weeks without duplicating days. A day or block
override marks deload weeks with `deload: true`; progression reads the mark (below).

Load is prescribed as **rep range + RIR**: weight is suggested from recent sessions on that
exercise. Percentage-of-training-max programs (SBS-style AMRAP) are out of v1.

### Automatic progressive overload

Every exercise gets a suggested weight (and target reps) for its next session without any manual
work. You can override the suggestion on the spot; the override is logged, not hidden.

- **One strength estimate per exercise** (per machine for machines): e1RM from Epley on
  reps + RIR, using sets of 12 reps or fewer, with a per-exercise correction learned from your logs.
  An exercise or machine with no history gets no suggestion until its first logged set.
  - The estimate is the best set of the latest session; deload sets and sets at 4+ RIR only count
    when they raise it. Progression reads the last 12 sessions.
  - The correction is Epley's constant (30) fitted per exercise from sets at different weights and
    reps in one session (a top set and its back-offs): the median of every such pair, kept within
    20–40, once there are two pairs.
- **What is judged**: the sets at the top weight (back-offs follow the top set) of the last session
  with the same rep range, against the prescription they were done under. So a day that benches
  4–6 and another that benches 6–8 each progress on their own; with no session in that range it's
  a new rep range (below). A session done ad hoc is judged against today's prescription. Effort is
  reps + RIR, so 11 @ 1 is as good as 10 @ 2. "Last time" in the why-line means that session.
- **Rep range @ RIR** (e.g. 8–10 @ 2 RIR): progress is judged on reps + RIR. When every work set
  reaches top + target RIR (10 @ 2, 11 @ 1, 12 @ 0), the next session moves up one rung of the
  equipment's ladder; otherwise same weight, more reps.
- **Fixed reps @ RIR**: a range with min = max. Beat it by one or more (5 @ 3, or 6 @ 2) → next rung.
- **Last set to failure**: beat the previous reps at the same weight; at the top of the range →
  next rung.
- **Moving up** targets the reps the estimate predicts at the new rung, kept inside the range.
  Otherwise the weight stays and the target is one rep more than last time's fewest.
- **Rung too big**: if the next rung would put you below the bottom of the range (a +2.3 kg cable
  jump on lateral raises), keep the weight and target +1 rep. If neither works, say so instead of
  guessing.
  - "Below the range" is what last time's sets predict at the next rung, rounded to whole reps.
  - +1 rep goes up to 2 reps past the top of the range. Past that, or at the heaviest rung, the
    suggestion keeps the weight and says progress isn't possible on this equipment.
- **Misses**: miss the target by one rep → same weight. Miss by two or more → one rung down (about
  5% where the ladder is fine-grained). Deload weeks and high-RIR sets never lower the estimate.
  - A miss is counted in reps + RIR against the bottom of the range at the target RIR, so 8 @ 1
    against 8–10 @ 2 is one short.
  - "About 5%" is the rung nearest 95% of the weight, and always at least one rung down.
  - After a deload session the weight never steps down.
- **Deload weeks** are the weeks a program marks `deload: true`. Their suggestion is last time's
  weight at the deload's easier target.
- **New rep range** (block change): weight from the estimate, rounded down to the ladder, marked
  "estimated, check on the first set".
  - The weight is for the bottom of the new range at its target RIR. The week-12 heavy single of
    Hyperlog Full Body is one of these.
- **Within a session**, the next set's weight follows the gap between target and logged RIR (about
  5% per RIR point, capped at ±10%).
  - Reps outside the range count as points too (5 reps against 6–8 is one point short).
  - The weight is the nearest rung, so on a coarse stack a small gap keeps the weight. A set with a
    different range from the set before gets its weight from the estimate, today's sets included.
- **Available weights** come from the equipment actually used: the next suggestion is the next
  weight on that equipment's weight ladder (see Gyms and equipment), never a computed number the
  machine can't be set to.
- **Inputs:** the main set only (weight, full reps, RIR). Partials, drops and myo mini-sets are
  recorded but don't move the suggestion.
- **Volume**: every work set counts as one set, with or without partials; a set done entirely as
  lengthened partials also counts as one. Drops, myo mini-sets and post-failure partials extend
  the set and don't add sets.

Research behind these rules: `docs/research/progression-and-partials.md`.
- **Every suggestion says why**, e.g. "last time 50 kg × 12, 12, 11 @ 2 RIR, top of 8–12 → 52.5 kg".
  - The why-line sits under the exercise name for the active set, one muted line led by a
    direction symbol (up, flat, down). An estimated weight, or progress that isn't possible, reads
    in the tertiary colour. A set that just follows the one before has no line.
- Coaching changes (e.g. `adjust_load -10%` after time off) apply on top of the suggestion for their
  scope; progression then continues from what you actually lift.

### Intensity techniques

A work set can carry one intensity technique, prescribed in the program and changeable during the
session ("do a drop set on this one"). Each technique has its own prescription, its own way of
logging, and computed loads where it needs them.

| Technique | Prescription | Logged as | App computes |
| --- | --- | --- | --- |
| `dropset` | drops (1–3), reduction % (default 25) | weight × reps for each drop | drop weights from the set weight, rounded to the exercise's increment |
| `myo_reps` | activation reps, mini-set target reps, max mini-sets | activation set, then reps per mini-set | stop when a mini-set misses target; short rest countdown between mini-sets |
| `lengthened_partials` | partials to failure, or a target count | full reps + partial reps | — |
| `iso_hold` | seconds, position (stretch/contraction) | seconds held | hold timer |
| `failure` | last set to failure | full reps (+ optional partials) | — |

- **Drops**: each drop is the weight before it (the set's, then the drop actually done) minus the
  reduction, rounded down to a rung (the lightest rung if none is below). Drop reps prefill from
  the same drop last time, else 6.
- **Myo-reps**: the set's reps are the activation set. Mini-sets are at its weight (locked) and
  prefill with their target; a 15 s breathing countdown comes before each. The set closes when a
  mini-set falls short of the target or the maximum is reached. Before the set, one planned
  mini-set row shows what is coming.
- **Iso hold**: the hold follows the set's full reps as its own sub-row. **Start** on the pad's
  strip runs a hold timer in the header pill, filling toward the target and buzzing when it is
  reached; ✓ (or Stop) records the whole seconds held. Seconds can be typed instead.
- **Lengthened partials**: the first ✓ moves to partials entry, prefilled with the target count if
  the program gives one; the second ✓ logs.
- **Failure**: giving the active set "To failure" in the session sets its RIR to 0.
- A drop set, myo-reps or iso hold stays open after its main set, with no rest, until its
  technique runs out or **Finish set** on the pad's strip closes it; then the set's rest starts.
  Switching to another exercise closes an open set.
- **Changing a technique** during the session: tap a set's number (or ⋮ → Edit set N)
  on any set not logged yet. The sheet offers None and the five techniques; a newly picked one
  starts with the format's defaults (2 drops of 25%, mini-sets of 3 up to 5, partials to failure)
  and iso hold at 20 s in the stretch. The change is for this session only; a logged set keeps
  the technique it was done with.
- The session list and Session done read a set with its extensions after it:
  `25 × 9 @ 1, 18 × 7, 12 × 6`, `60 × 12 @ 0 + 4 + 3`, `40 × 10 @ 0 + 20 s`.

### Full and partial reps

Every set logs **full reps** and **partial reps** separately, on any set, whether or not partials
were prescribed. Rep entry uses an in-app number pad with a **Full / Partial toggle** (as in
MacroFactor's F/P toggle): type the full reps, flip to Partial, type the partials. Typing `12+5`
also works. History shows `50 kg × 12 + 5p`. Partials never count towards e1RM or progression.

### Gyms and equipment

You train at three gyms with different equipment, and cable stacks differ between machines, so
available weights belong to a **machine at a gym**, not to an exercise.

- **Gym**: a named place with its equipment. Picked at session start.
- **Equipment item**: one piece of equipment at a gym (a cable station, a leg extension, the
  dumbbell rack, a barbell with plates) with its **weight ladder**: a start and step in kg or lb
  (shown in kg), an explicit list, or for plate-loaded machines a base weight plus plates.
- **Dumbbells are universal**: one global dumbbell ladder (default 2 kg steps to 20 kg, then
  22.5, 25, then 5 kg steps; editable), not set per gym. Barbell plates likewise use one global set.
- **Setup both ways**: a gym screen to list equipment up front, and adding on the go. The first
  time an exercise is done at a gym without a known machine, the app asks "which machine?" (pick or
  add) and its ladder, then remembers it.
- **Gym is picked at session start**, defaulting to the gym you usually train at on that weekday
  (learned from history, editable per gym).
  - Start session opens a sheet with the gyms as a single choice and **Start**. The preselected gym
    says why ("Usual on Mondays"); one tap on another row changes it. With no gyms set up the
    session starts at once, without a gym.
  - The usual gym on a weekday: a gym whose weekday plan has that day (Gyms and equipment → the
    gym → Usual days); else the gym with the most sessions on that weekday, the latest breaking a
    tie; else the gym trained at last; else the first gym.
  - The session list shows the session's gym under its title. Changing gyms mid-session isn't
    offered.
- **Missing equipment**: on picking the gym, the day is checked against that gym's kit; swaps are
  proposed from the slot's alternatives, then by movement pattern (same logic as the coach) and
  apply to this session only.
- **"Which machine?"**: in a session with a gym, a machine, cable or Smith machine exercise the
  gym has no machine for yet shows "Which machine?" in place of its sets: the gym's equipment with
  the weights each gives, and **Add machine** (the ladder editor; a machine exercise's name is
  filled in, a cable station's left for you). The answer is remembered per gym and exercise, so the
  next time it goes straight to the sets. The exercise sheet shows the machine and gym, with
  **Change** until a set is logged on it.
- **History and progression are shared for free weights** (barbell, dumbbells, bodyweight, and the
  EZ, trap and safety bars, plates and weighted bodyweight) across gyms. **Machines and cables keep a history per machine**, because stacks and pulley ratios don't
  compare. On a machine with no history, the first set asks you to pick a weight for the target reps
  and RIR; progression runs from there. Machine exercise trends show one line per machine.
- In a session, the weight field is a picker over that item's ladder; free typing stays possible.
- Machine ladders are often lb stacks shown in kg (1.1 / 3.4 / 5.7 kg = start 2.5 lb, step 5 lb), so
  setup offers "lb stack" as a one-tap preset (2.5 lb, then 5 lb steps, 40 weights). Lb rungs
  show and log to 0.1 kg.
- A stack is a start, a step and a number of weights. Plates are a base weight (bar or sled) plus
  plate pairs, one of each pair per side; any combination of pairs is a rung. The shared barbell
  plates default to a 20 kg bar with pairs of 20, 20, 20, 15, 10, 5, 2.5 and 1.25 kg.
- Setup lives in Settings → Gyms and equipment: gyms and their equipment, then the shared dumbbells
  and plates. Every ladder shows as the kg weights it gives (`1.1 · 3.4 · 5.7 · 7.9 … 89.6 kg`).
  Setup uses the system keyboard, since it happens once and away from a set. Editing the shared
  dumbbells or plates applies to a running session at once.

The old app's machine weights were wrong in both directions: suggestions that didn't exist on the
stack, and slow entry of odd values. The ladder picker fixes both.

## Screens

1. **Today** — the next day's prescription. Start the session, log sets (weight, full + partial
   reps, RIR, technique follow-ups), rest timer, technique video link and note for each exercise,
   swap to an alternative, add or change an intensity technique on a set. Works without a program
   too (ad-hoc session).
   - **With a program**, Today shows the next day: week and block, the day's name, and each slot's
     exercise with its sets (`1 × 4–6 @ 1 · 2 × 6–8 @ 1`, technique symbols after). **Start
     session** starts that day at the picked gym; **Ad-hoc session** stays available. A finished
     program says so and offers an ad-hoc session.
   - **A program day's session** has a row for every planned set from the start, with a Target
     column (reps and an RIR ring). The first set prefills with the suggestion (Automatic
     progressive overload): its weight and target reps, at the target RIR; with no history, an empty
     weight at the top of the range. Later sets follow the set before (reps capped at the top of
     the range, or the top of a new range), and its weight moves with the RIR gap. Rest after a set is the program's rest for it. After an
     exercise's last planned set the first exercise with sets left comes up. When every planned
     set is logged, the pad gives way to **Day done** and Finish session.
   - The **prescription sheet** (tap the exercise name) shows every set's target, technique or
     back-off rule, the rest, the slot note and the exercise's technique note.
   - **Edit on the fly:** add, remove, reorder or swap exercises; add or remove sets; change reps,
     RIR or technique. Edits apply to this session only, with an optional "keep for the rest of the
     block" that saves them as a Change (the Changes ticket). The next session of the day has the
     program as written.
     - **Swap** (⋮ → Swap exercise, or the session list row's ⋮) offers the slot's planned exercise
       and alternatives, then the library's exercises with the same movement pattern; a search
       covers the whole library. The swapped exercise keeps the slot (and its superset partner and
       label) and does the sets the program gives it (an alternative's own, else the slot's). Its
       history, suggestion and machine are its own. Once a set is logged on an exercise it can't be
       swapped or removed. A swapped slot reads "In place of <planned exercise>" above the name.
     - **Sets:** **Add set** under a program exercise's sets adds one like its last planned set,
       without its technique (on a finished exercise it brings the exercise back into the order).
       A set's number opens its sheet: **Remove set**, and its target (fewest reps, most reps,
       RIR, by steps), as long as the set isn't logged. One set always stays, and so does one that
       others back off from. Removing the active exercise's last set to do moves the session on.
     - **Exercises:** the session list row's ⋮ swaps, adds a set, moves the exercise up or down (a
       superset moves as a pair) and removes it (asking first; only with nothing logged). Added
       exercises are open-ended like ad-hoc ones (no slot, no target), in a program session too.
   - **Warm-ups** generated from the first work set's weight (ramp by percentage, rounded to the
     equipment's available weights).
     - The ramp follows the weight planned or typed for the first work set, live. Lifts with
       several joints get 45% × 8, 60% × 5, 75% × 3, 85% × 2 when the first work set is 6 reps or
       fewer, else 45% × 8, 65% × 5, 85% × 3; single-joint exercises (by movement pattern: curls,
       raises, extensions, flyes, calf raises…) get one, 60% × 8. Each is the nearest rung; on a
       bar or plate-loaded machine, the nearest 5 kg step, so the small plates are only for work
       sets. A warm-up that doesn't climb, or reaches the work weight, is left out (a 20 kg top set
       on the bar has none). Bodyweight and weighted bodyweight exercises have none.
     - They show as `W` rows above the first work set, in ad-hoc sessions too, muted until a tap
       ticks one off (with the ones above it; a tap on the last one ticked takes it back). Ticks
       are saved. Warm-ups are never logged: they start no rest, leave the pad and the rest timer
       alone, add no volume and never reach progression or history. They go once the first work
       set is logged. Ticking is optional.
   - **Rest timer** that keeps running with the screen off: ongoing notification, vibration and
     sound when rest ends.
   - **Plate calculator** for barbell and plate-loaded exercises.
     - Barbell exercises use the shared plates; machines whose ladder is plates use theirs. The
       active work set shows `20 · 15 per side` under its row (`Empty bar` with no plates), for the
       weight in the pad, live; each warm-up row shows its own plates. Plates are the fewest that
       make the weight, heaviest first, from the pairs there are. A weight the plates can't make
       shows no plates (the weight check says so on ✓).
     - EZ, trap and safety bars have no plate calculator yet, since their ladders are the 2.5 kg
       stand-in.
   - **Layout and interaction** follow `docs/design/logging-screen.md`: one exercise at a time as
     set rows (target, last time, kg, reps, RIR), techniques and their planned drops/mini-sets
     visible before the set, and a compact in-app pad drawn like the system number keyboard.
   - **Rest rules**: no rest before drops, between myo mini-sets (a short breathing countdown
     instead) or between superset partners; full rest after the set or superset round.
   - **Rest on the lock screen**: an ongoing notification with time left, the next set's target and
     +15 s / skip; it vibrates when rest ends with the screen off.
     - It reads `DB Bench Press · Set 3` over `25 kg × 8–10 @ 1` (the prefilled weight, the target
       reps and RIR; `10–12 reps @ 2` with no weight yet), with `Rest` and the countdown in its
       header, and the actions **+15** and **End rest**, as on the rest sheet. On Android 16 it is a
       Live Update, so the countdown also sits in the status bar as a chip.
     - When rest runs out the phone buzzes twice with alarm-class vibration (through vibrate and
       silent mode), and the notification reads `Rest over` and counts up, with End rest, until the
       next set is logged. Out of the app it also plays the notification sound and pops up; the
       sound can be turned off in the system's settings for the "Rest over" channel. With the app on
       screen it only buzzes.
     - A myo-reps breathing pause shows as `Breathe` counting down to `Mini-set 2` (`25 kg × 4`),
       with no actions, and ends with one short buzz. Drops, superset partners and iso holds show
       nothing, and neither does rest after the day's last planned set (Day done).
     - −15 that pulls rest past zero counts as rest running out and buzzes once.
     - Rest survives the screen going off, the app being killed and a reboot: the countdown and
       its exact alarm are put back when the phone starts. Notifications are asked for when a
       session first shows (Android 13+); without them rest still buzzes.
     - Leaving an open set for an exercise outside its superset (Start in the session list) closes
       it and its rest runs; only the partner's set of the same round comes without rest.
   - **Every tap is saved immediately**; a session survives back gestures, app kills and reboots.
     Logged sets stay editable during and after the session; editing one never resets the timer.
   - **Prefills** follow what just happened (each set takes the previous set's weight and reps; drop
     reps prefill from last time's drops).
   - **Weight check**: a weight 1.5× or more (or half or less) of the reference, or not on the
     equipment's ladder, is shown with the reason ("10× last set") and three real weights to pick;
     the first ✓ doesn't log it, a second ✓ keeps it.
     - The reference is the previous set of the exercise in this session, else last time's first
       set ("10× last time"). Ratios read `1.6×`, `2.4×`, then whole numbers (`10×`); low ones
       `1/2 of last set`. Off the ladder reads "Not on this equipment".
     - It runs on the ✓ that would log the set. The reason and the three weights replace the pad's
       strip (the pad opens if hidden), and the weight cell and ✓ turn the error colour. For a
       ratio the weights are the rungs around the reference; off the ladder, the rungs around the
       typed weight.
     - Picking one of the three puts it in the weight and the next ✓ logs. Any edit clears the
       check. The second ✓ keeps the weight only if it comes 1 s or more after the warning, the
       same stutter rule as logging.
     - Only real ladders are checked for "not on the ladder": a machine or cable without a known
       machine (a session with no gym) is checked for ratios only.
     - **A kept weight is held back**: a set kept past the check is usually a typo (79 for 7.9), so
       it isn't the next set's prefill or reference, and progression ignores it. The last set that
       counts takes its place (else last time's first). Logging another set near it (within the
       check's 1.5× band) shows the weight was real, and from then on it counts.
   - **Corrections**: tapping any logged set, drop or mini-set loads it into the pad; ✓ saves, the
     hide key cancels. Finished exercises reopen from the session list and from "Session done".
     Back cancels a correction, then asks before leaving the session.
   - Drops and myo mini-sets record no RIR; they are taken to failure.
   - **Ad-hoc session** (no program): Today offers **Start session**, or **Resume session** while
     one runs. At most one session runs at a time, and the app opens on it. You add exercises from
     the exercise library (searchable; hidden exercises aren't offered) through ⋮ or the session
     list, which also has **Finish session**.
     - There is no prescription, so the Target column is left out and the sets are open-ended:
       there is always one active row after the logged ones. Last time's sets of the exercise show
       as muted upcoming rows, so you can see what is likely to come.
     - The first set prefills from last time's first set. With no history the pad starts on an
       empty weight. Later sets take the previous set's weight, reps and RIR. A set logs only
       with a weight, full reps and an RIR.
     - Rest after each set is 2:00.
     - **One ✓, one set**: a ✓ within 1 s of logging a set logs nothing, so a stutter or double
       tap can't log a phantom set. No two sets are ever logged that close on purpose.
     - −/+ and the weight strip use the shared dumbbell ladder for dumbbell exercises, the shared
       plates on a 20 kg bar for barbell exercises, the machine's ladder for machines and cables,
       and 2.5 kg steps for everything else (and for machines in a session with no gym).
     - Last time, for machines and cables, is the last session on the same machine; free weights
       take the last session at any gym.
     - Finishing shows **Session done**. Exercises with nothing logged are left out of it, and a
       session with nothing logged is discarded.
     - **Finishing is deliberate and recoverable.** Finish session (⋮, below a divider, or the
       session list) asks "Finish the session?" (Keep going / Finish). Session done offers
       **Resume session**, which reopens the session exactly as it was.
2. **Program** — blocks and weeks, where you are now, past sessions in context. Actions: skip day,
   repeat week, deload week, shift the remaining plan by N weeks. Reached from Today's top bar.
   - Each block is a heading (`Build · weeks 3–6`), each week a row of day tokens (A, B, C…): done,
     next (the one filled shape) or to come. The current week names its next day.
   - **Import program** picks a JSON file. A file with problems shows them all, path then message,
     and imports nothing. Importing over a program asks first ("Replace <title>?").
   - **Starter program**: while no program runs (the first run), Today and Program offer Hyperlog
     Full Body with **Run this program**; Today keeps Start session (ad hoc) and Import a program
     beside it. Program's ⋮ keeps "Run Hyperlog Full Body" later, asking before it replaces the
     program running. The starter is an ordinary program file in the app's assets
     (`programs/full-body-3-day.json`) and imports through the same validator.
   - **Position**: the program runs as a sequence. Finishing a session of the next day moves the
     position to the day after it (the next day of the block, then the next week's first day); a
     session with nothing logged is discarded and moves nothing; finishing a resumed session again
     doesn't move it twice. After the last day the program is finished.
   - Design: `docs/design/program-and-today.md`.
3. **Coach** — see below.
4. **History** — session list, per-exercise top set and e1RM trend, weekly sets per muscle.

Settings sit behind an icon: gyms and their equipment, exercise library, import backup, export
backup.

- **Effort is RIR everywhere**, both targets and logging.
  RIR has a small fixed colour scale (3+ calm, 1–2 warm, 0 hot) that does not follow the dynamic
  theme; contrast is tuned for light and dark.
- **Scheduling**: the program runs as a sequence (next day is whatever comes next, whenever you
  train). An optional weekday plan (e.g. Mon = gym A, Wed = gym B) sets the default gym and lets the
  coach detect time off. No reminders or notifications.
- **Supersets** log alternately (A1 → A2 → A1…) with no timer between partners and full rest after
  the round. A superset started out of order keeps its partner until the round is done.
  - Closing a partner's set (logged, its last drop or mini-set, or **Finish set**) brings up the other
    partner's set of the same round at once, with no rest; closing the round starts the rest of the
    set that closed it. With uneven set counts, the extra sets run as straight sets.
  - "Until the round is done" means until all of the superset's planned sets are logged. The pair
    stays on screen as one, then the session goes back to the first group with sets left.
  - The superset line above the name reads `2a with Seated Leg Curl`, using the day's slot labels
    (as Today numbers them), then the partner's last set this session with its RIR dot.
- **Session order**: the Next line names what comes up after the exercise (or superset) on screen:
  the first exercise with planned sets left, in the day's order, so a skipped one comes first. It
  opens the session list, whose **Start** does another exercise now; when it is done the session
  returns to the skipped exercise. Rest keeps running across a Start.
  - The session list numbers a program's exercises with the day's labels (`1`, `2a`, `2b`) in their
    own column, superset labels in tertiary. A superset on screen is marked current as a pair, with
    no Start on the partner. An exercise shows as done only once every planned set is logged; one
    started and left keeps the empty circle, with its logged sets as the summary.
- **Unilateral exercises** log one number (reps per side) by default; a per-set toggle splits left and
  right.
  - An exercise is unilateral when its name says so (`1-Arm`, `1-Leg`, `Single-Arm`,
    `Single-Leg`, lunges, split squats, step-ups); the names carry the implement and the side.
  - The toggle is "Left and right apart" in the active set's sheet (tap its number). A split set
    takes the left reps, ✓, then the right, under an `L / R` head, and logs as `30 × 10/9 @ 1`.
    The next set stays split. Progression reads the weaker side.
- **Past sessions are editable.** The app never recalculates a past session from new rules, but you
  can correct one; progression then reruns from the corrected data.
- **English only.**
- **Exercise library** is editable on the phone (Settings → Exercise library): browse and search,
  add, edit (name, equipment type, movement pattern, grip demand, primary and secondary muscles,
  video link, note) and hide. Design: `docs/design/exercise-library.md`.
  - **Names carry the implement** ("DB Bench Press", "Smith Machine Squat"), so a name alone tells
    exercises apart wherever it shows.
  - An exercise needs a name, equipment, a movement pattern, a grip demand and at least one primary
    muscle before it can be saved, because the coach's swaps rely on them. The video link must be a
    YouTube link.
  - **Hiding** keeps the exercise in past sessions and in the library (listed last, muted), but it
    isn't offered when adding an exercise. It can be shown again. Exercises are never deleted.
  - Search matches every word against the name, equipment and primary muscles; exercises named
    for the query come first.
  - The shipped library updates with the app: exercises never edited on the phone take the new
    values; edited ones and hidden flags are kept. Exercises added on the phone get an id made from
    the name (`landmine_press`).
- **Technique videos** open in a Custom Tab of the browser, at the link's timestamp if it has
  one, so Back returns straight to the session. The YouTube app is the fallback when no browser
  shows Custom Tabs: Back from it goes home, and it keeps playing in a floating window over the
  pad. They are reached from the exercise's prescription sheet (tap
  the exercise name in a session). In an ad-hoc session the sheet shows the equipment and primary
  muscles, the library note and **Technique video · 10:53** (the start time when the link has one).
- **Two kinds of notes, neither written by hand:** the exercise's own technique cue comes with the
  library (seeded from the legacy notes, cleaned of program-specific advice like "dropset: …");
  program-specific cues come from the program file's slot `note` (the legacy programs had 281). The
  prescription sheet shows both. Library notes stay editable on the phone.
- **History** (v1): session list, per-exercise top set and e1RM trend, weekly sets per muscle.
- **Lift goals**: a target for one exercise's estimate (e.g. barbell bench 100 kg, optional date).
  The exercise's prescription sheet and History show the current estimate against the goal, with
  its trend. A goal never changes the program or the suggestions.
- **Bodyweight**: a dated log with a small trend line on History; the latest value is used for
  weighted bodyweight exercises.
- **Dumbbells are logged per dumbbell** (the number printed on it). Volume and e1RM count both
  dumbbells for two-dumbbell exercises.
- **Backups**: manual JSON export/import, plus Android Auto Backup enabled.

## Coaching

The coach never edits the plan directly. Every suggestion is a **Change** that is shown as a
diff against the current plan; you accept or reject it.

```json
{
  "summary": "Hands need a week off grip work. Train legs and machines, then resume week 10.",
  "scope": { "from": "2026-10-13", "to": "2026-10-19" },
  "operations": [
    { "op": "swap", "slot": "sbs_acc_cable_row", "exercise": "chest_supported_db_row", "reason": "no grip" },
    { "op": "remove", "slot": "sbs_hinge_primary", "reason": "grip" },
    { "op": "adjust_sets", "slot": "sbs_squat_primary", "sets": 2, "reason": "returning after 11 days off" },
    { "op": "shift_program", "weeks": 1 }
  ]
}
```

Operations: `swap`, `remove`, `add`, `adjust_sets`, `adjust_load` (percent), `adjust_rir`, `skip_day`,
`deload_week`, `shift_program`. Scope is either a date range (temporary) or `rest_of_program`.

### Rules (offline, built in)

You pick a situation and constraints:

- Situation (v1, all five): time off (missed days or a week); pain or injury in an area (hands/grip,
  shoulder, elbow, knee, lower back); gym not available (home: dumbbells and bodyweight); short on
  time (session capped at N minutes); poor recovery (bad sleep or stress, lower today's volume or
  effort).
- Constraints: no grip, no axial load, no overhead, home (dumbbells/bodyweight), max N minutes.

The rules produce a Change. Swaps come from the slot's own alternatives first, then from library
exercises with the same `movementPattern` that meet the constraints (equipment, plus a small
per-exercise tag table such as `grip: high|low|none`). Time off maps to fixed steps: up to 7 days,
resume as planned; 8–14 days, a reduced-volume re-entry session; more than 14 days, repeat the
last week.

### Ask Claude (uses your subscription, no API)

The app makes no model calls. Anthropic's terms keep subscription credentials inside Anthropic's
own apps, so Hyperlog hands off to the Claude app instead:

1. You describe the situation in plain words.
2. **Ask Claude** opens the Android share sheet with a prompt for the Claude app. The prompt holds
   your words, the current program and position, the last ~4 weeks of sessions, the relevant
   exercise library entries, and the Change JSON format above with instructions to reply with
   one fenced JSON block.
3. You talk it through in Claude as long as you like.
4. Copy the final JSON and press **Paste change** in Hyperlog. Hyperlog validates it (known slots
   and exercises, valid ops) and shows the diff. Accept or reject.

Later, if the paste step gets annoying: a custom MCP connector would let the Claude app read the
plan and write a pending Change directly. That needs a public HTTPS server holding the data, so it
waits until syncing is wanted for its own sake.

## Fresh start, with starting weights from the old app

No history is imported from the legacy app: no sessions, programs, TM history or bodyweight.
History and trends start empty. The legacy backup (`~/Downloads/hyperlog-full-backup-2026-10-12.json`)
stays as an archive.

The one thing taken from it is **starting weights**. For each exercise, the most recent work sets
in the backup become a one-off starting point, used only until the exercise has its own history:

- **Free weights** (barbell, dumbbells, bodyweight): the first suggestion comes from the old data,
  labelled "from old app: 60 kg × 8 @ 2 RIR, 12 Sep". The first real session replaces it.
- **Machines and cables**: the old data doesn't say which gym or machine, so the old weight is shown
  as a hint only while you pick a weight on the new machine's ladder.
- Old dumbbell weights logged as both dumbbells together are halved. These five were totals:
  seated DB shoulder press, DB bench press, DB incline press (`db_incline_press`, `incline_db_press`),
  chest-supported DB rows (`chest_supported_db_row`, `incline_chest_supported_db_row`), DB Romanian
  deadlift. All other dumbbell weights are per dumbbell.

How the import works:

- **Settings → Starting weights from the old app** picks a backup file (format 7.0, `type:
  full_backup`). Importing again replaces the earlier import; a file that isn't a backup says so and
  leaves the earlier import in place. Nothing else is read: sessions, programs and settings stay
  out.
- The starting set is the **top work set** (work or AMRAP, heaviest weight, most reps at it) of the
  latest old session of the exercise. Sets over 12 reps are passed over when the session had any
  others, since their RIR is unreliable for the estimate. Old ids go through the library's
  `merged` table first. RIR is 10 − RPE; a set with no RPE shows no RIR and counts as 0 in reserve.
- Machine weights the old app logged as added to a base (`machineWeightMode: added`) include the
  old app's base for that exercise.
- In a program session the old set is **worked into today's prescription** through its e1RM: the
  old reps kept inside the target range, the weight rounded down to a rung. When that changes the
  set, the label shows both: "From old app: 60 kg × 12 @ 2 RIR, 12 Sep → 65 kg × 8". An ad-hoc
  exercise is prefilled with the old set itself, on the ladder.
- The label sits in the suggestion line, tertiary like an estimate (check it on the first set).
  A machine's hint reads "From old app, another machine" with the set on its own line, and the
  weight stays empty. Both go once the exercise (for machines, on that machine) has a logged set.

The **exercise library** is seeded from the legacy library (189 exercises, 150 with technique
videos), cleaned up: duplicates merged (e.g. `db_incline_press` / `incline_db_press`), equipment types
fixed, and a grip-demand tag (`high` / `low` / `none`) added for the coach's swap rules. The result
ships as `app/src/main/assets/exercises.json`: 173 exercises, 138 with videos (merged duplicates
shared links), with a `merged` table of legacy id → kept id for anything that still carries an old
id (the starting-weights import).

- **Equipment**: barbell, dumbbell, machine, cable, smith_machine, ez_bar, trap_bar, safety_bar,
  plate (a held plate), bodyweight, weighted_bodyweight (bodyweight plus a belt or held load).
- **Movement patterns** replace the legacy seven, which lumped curls in with pulldowns and calf raises
  in with squats: horizontal_push, vertical_push, horizontal_pull, vertical_pull, squat, lunge, hinge,
  hip_thrust, knee_extension, knee_flexion, calf_raise, hip_abduction, hip_adduction, chest_fly,
  lateral_raise, rear_delt_fly, shrug, elbow_flexion, elbow_extension, wrist, core_flexion, rotation,
  neck, hang.
- **Grip demand**: `high` when the hands hold or hang from a heavy load or are the target (deadlifts,
  rows, pulldowns and pull-ups, free-weight curls, shrugs, hangs, wrist work); `low` when the hands
  hold a bar or handle without pulling a heavy load (presses, cable and machine curls, triceps work,
  raises, barbell squats); `none` when the hands do nothing (leg machines, calf raises, pad-driven
  machines).
- **Muscles** keep the legacy names, with synonyms merged (Trapezius / Upper Traps → Traps, a generic
  Triceps → both heads, "Shoulders" → the delt heads) and wrong lists fixed.
- **Notes** keep the technique cues and drop program advice ("Dropset: …", "Last set only…", "Focus on
  strength here. Each week add weight…", AMRAP and substitution instructions). Cues copied onto the
  wrong exercise were dropped or reworded for the right implement.

## Build order

1. Project skeleton, theme, launcher icon from `brand/`.
2. Room schema, gyms and equipment ladders, exercise library.
3. Today screen: set logging with full and partial reps, intensity techniques, automatic progression.
4. Hyperlog Full Body (`docs/programs/full-body-3-day.md`) as the first program and the bundled
   starter.
5. Program screen and its manual actions.
6. Change model, diff view, apply/revert.
7. Rule-based coach.
8. Ask Claude handoff and paste.
9. History.
