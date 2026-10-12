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

- **Google Play internal testing** track: you and a few invited users install from the Play Store
  and get updates automatically. Needs a Play Console developer account (one-time fee).
- Signed with a new **upload key** kept outside the repo and backed up (Play App Signing holds the
  app signing key). The legacy app was signed with the old machine's debug key, so the new app
  can't install over it: uninstall the legacy app when switching; its data stays in the backup.
- During development, debug builds install over USB with adb alongside the Play build.
- **Other users** get the same app with no accounts and no server; each has their own data on their
  phone. **No programs are bundled**: Nippard's programs are paid and copyrighted, so each user
  imports program files they own. The app ships with the exercise library only.
- Starting weights from a legacy backup are a one-off "Import starting weights" action in settings,
  not built into the app.

## Domain

| Term | Meaning |
| --- | --- |
| **Program** | A periodized plan: blocks of weeks, each week a set of training days. |
| **Block** | A run of weeks (`weekStart..weekEnd`) that share the same days, e.g. Accumulation 1–7. |
| **Day** | One planned session: an ordered list of groups. |
| **Group** | Exercises done together: `straight` or `superset`. |
| **Slot** | A place in a day for one exercise. It has a primary exercise and alternatives (`options`). |
| **Set prescription** | Type (`warmup`, `work`, `amrap`, `drop`, `myo`), reps, optional target RIR, rest, load rule. |
| **Position** | Where you are: program, week, next day. |
| **Session** | What was done on a date: sets with weight, reps, RIR. It's a record and is never recalculated. |
| **Training max (TM)** | Per-exercise working max for %TM programs (SBS). Changes are events with a reason. |
| **Change** | A proposed edit to the plan (see Coaching). Accepted changes are kept as history. |
| **Gym** | A place you train, with its equipment. Picked at session start. |
| **Equipment item** | One piece of equipment at a gym (a cable station, a leg press), with a weight ladder. Dumbbells and plates are global. |
| **Weight ladder** | The weights an equipment item can actually be set to. A **rung** is one step on it. |
| **Estimate** | The per-exercise (per-machine for machines) e1RM from reps + RIR that drives load suggestions. |
| **Intensity technique** | How a work set is extended: drop set, myo-reps, lengthened partials, iso hold, failure. |

Programs use a JSON shape close to the legacy one (`meta`, `blocks[].days[].groups[].exercises[].options[].sets[]`),
but legacy programs are **not** imported. Programs are written fresh for this app once intensity
techniques work (below).

**Authoring.** Programs are JSON files, written with Claude's help from a Nippard PDF or sheet,
and imported through a strict validator that names the exact path of every error
(`blocks[1].days[0].groups[2]…: unknown exercise "bb_row"`). No in-app program editor in v1.

**Week overrides.** Days are defined once per block; any set field can be overridden for given
weeks of the block, e.g. `"weeks": {"1": {"rir": 3}, "4": {"sets": 2, "technique": null}}`. This
covers ramp-in weeks, techniques that start in week 2, and deload weeks without duplicating days.

Load is prescribed in one of two ways:

- **Rep range + RIR** (Nippard programs): weight is suggested from recent sessions on that exercise.
- **%TM** (SBS): weight = TM × week percentage. The AMRAP result adjusts the TM. Port
  `src/app/utils/sbs-auto-regulation.ts` and `src/app/data/sbs-hypertrophy-table.ts` from legacy.

### Automatic progressive overload

Every exercise gets a suggested weight (and target reps) for its next session without any manual
work. You can override the suggestion on the spot; the override is logged, not hidden.

- **One strength estimate per exercise** (per machine for machines): e1RM from Epley on
  reps + RIR, using sets of 12 reps or fewer, with a per-exercise correction learned from your logs.
  An exercise or machine with no history gets no suggestion until its first logged set.
- **Rep range @ RIR** (e.g. 8–10 @ 2 RIR): progress is judged on reps + RIR. When every work set
  reaches top + target RIR (10 @ 2, 11 @ 1, 12 @ 0), the next session moves up one rung of the
  equipment's ladder; otherwise same weight, more reps.
- **Fixed reps @ RIR**: a range with min = max. Beat it by one or more (5 @ 3, or 6 @ 2) → next rung.
- **Last set to failure**: beat the previous reps at the same weight; at the top of the range →
  next rung.
- **Rung too big**: if the next rung would put you below the bottom of the range (a +2.3 kg cable
  jump on lateral raises), keep the weight and target +1 rep. If neither works, say so instead of
  guessing.
- **Misses**: miss the target by one rep → same weight. Miss by two or more → one rung down (about
  5% where the ladder is fine-grained). Deload weeks and high-RIR sets never lower the estimate.
- **New rep range** (block change): weight from the estimate, rounded down to the ladder, marked
  "estimated, check on the first set".
- **Within a session**, the next set's weight follows the gap between target and logged RIR (about
  5% per RIR point, capped at ±10%).
- **AMRAP / %TM (SBS)**: the AMRAP result adjusts the TM and the week's percentage sets the weight.
  Port legacy `src/app/utils/sbs-auto-regulation.ts` as is (the public SBS rules are paywalled).
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
- **Missing equipment**: on picking the gym, the day is checked against that gym's kit; swaps are
  proposed from the slot's alternatives, then by movement pattern (same logic as the coach) and
  apply to this session only.
- **History and progression are shared for free weights** (barbell, dumbbells, bodyweight) across
  gyms. **Machines and cables keep a history per machine**, because stacks and pulley ratios don't
  compare. On a machine with no history, the first set asks you to pick a weight for the target reps
  and RIR; progression runs from there. Machine exercise trends show one line per machine.
- In a session, the weight field is a picker over that item's ladder; free typing stays possible.
- Machine ladders are often lb stacks shown in kg (1.1 / 3.4 / 5.7 kg = start 2.5 lb, step 5 lb), so
  setup offers "lb stack" as a one-tap preset.

The old app's machine weights were wrong in both directions: suggestions that didn't exist on the
stack, and slow entry of odd values. The ladder picker fixes both.

## Screens

1. **Today** — the next day's prescription. Start the session, log sets (weight, full + partial
   reps, RIR, technique follow-ups), rest timer, technique video link and note for each exercise,
   swap to an alternative, add or change an intensity technique on a set. Works without a program
   too (ad-hoc session).
   - **Edit on the fly:** add, remove, reorder or swap exercises; add or remove sets; change reps,
     RIR or technique. Edits apply to this session only, with an optional "keep for the rest of the
     block" that saves them as a Change.
   - **Warm-ups** generated from the first work set's weight (ramp by percentage, rounded to the
     equipment's available weights).
   - **Rest timer** that keeps running with the screen off: ongoing notification, vibration and
     sound when rest ends.
   - **Plate calculator** for barbell and plate-loaded exercises.
   - **Layout and interaction** follow `docs/design/logging-screen.md`: one exercise at a time as
     set rows (target, last time, kg, reps, RIR), techniques and their planned drops/mini-sets
     visible before the set, and a compact in-app pad drawn like the system number keyboard.
   - **Rest rules**: no rest before drops, between myo mini-sets (a short breathing countdown
     instead) or between superset partners; full rest after the set or superset round.
   - **Rest on the lock screen**: an ongoing notification with time left, the next set's target and
     +15 s / skip; it vibrates when rest ends with the screen off.
   - **Every tap is saved immediately**; a session survives back gestures, app kills and reboots.
     Logged sets stay editable during and after the session; editing one never resets the timer.
   - **Prefills** follow what just happened (a changed weight carries to the next sets; drop reps
     prefill from last time). Weights that don't exist on the equipment's ladder are flagged, not
     silently accepted.
   - Drops and myo mini-sets record no RIR; they are taken to failure.
2. **Program** — blocks and weeks, where you are now, past sessions in context. Actions: skip day,
   repeat week, deload week, shift the remaining plan by N weeks.
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
- **Supersets** log alternately (A1 → A2 → A1…) with a short transition timer between exercises and
  full rest after the last one.
- **Unilateral exercises** log one number (reps per side) by default; a per-set toggle splits left and
  right.
- **Past sessions are editable.** The app never recalculates a past session from new rules, but you
  can correct one; progression then reruns from the corrected data.
- **English only.**
- **Exercise library** is editable on the phone: add, edit (name, video link, note, equipment
  type, muscles, movement pattern) and hide.
- **Technique videos** open in the YouTube app, at the link's timestamp if it has one. They are
  reached from the exercise's prescription sheet (tap the exercise name / info symbol in a session).
- **Two kinds of notes, neither written by hand:** the exercise's own technique cue comes with the
  library (seeded from the legacy notes, cleaned of program-specific advice like "dropset: …");
  program-specific cues come from the program file's slot `note` (the legacy programs had 281). The
  prescription sheet shows both. Library notes stay editable on the phone.
- **History** (v1): session list, per-exercise top set and e1RM trend, weekly sets per muscle.
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

Operations: `swap`, `remove`, `add`, `adjust_sets`, `adjust_load` (percent), `skip_day`,
`deload_week`, `shift_program`. Scope is either a date range (temporary) or `rest_of_program`.

### Rules (offline, built in)

You pick a situation and constraints:

- Situation (v1, all five): time off (missed days or a week); pain or injury in an area (hands/grip,
  shoulder, elbow, knee, lower back); gym not available (home: dumbbells and bodyweight); short on
  time (session capped at N minutes); poor recovery (bad sleep or stress, lower today's volume or
  effort).
- Constraints: no grip, no axial load, no overhead, home (dumbbells/bodyweight), max N minutes.

The rules produce a Change. Swaps come from the slot's own `options` first, then from library
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

The **exercise library** is seeded from the legacy library (189 exercises, 150 with technique
videos), cleaned up: duplicates merged (e.g. `db_incline_press` / `incline_db_press`), equipment types
fixed, and a grip-demand tag (`high` / `low` / `none`) added for the coach's swap rules.

## Build order

1. Project skeleton, theme, launcher icon from `brand/`.
2. Room schema, gyms and equipment ladders, exercise library.
3. Today screen: set logging with full and partial reps, intensity techniques, automatic progression.
4. Min-Max Essentials converted to the new format (from the legacy backup) as the first program.
5. Program screen and its manual actions.
6. Change model, diff view, apply/revert.
7. Rule-based coach.
8. Ask Claude handoff and paste.
9. History.
