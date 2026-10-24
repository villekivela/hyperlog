# Hyperlog — spec

A native Android app that runs a periodized strength program, logs what happened, and tells you
what to do when the plan meets real life.

Hyperlog replaces the Angular/Capacitor app now in `hyperlog-legacy`. That app grew from a PWA
into nutrition, VBT, a desktop editor and more. This one does three things.

1. **Run the program.** Show today's prescription from where you are in the program.
2. **Log the session** against that prescription.
3. **Coach.** When you can't train as planned, check in with Claude in a few taps and get back a
   concrete change to the program, a workout or a new program, applied only after you accept it.

Out of scope: nutrition, VBT, Health Connect, progress photos, body measurements, desktop
configuration, iOS, web, any server.

## Stack

- Kotlin, Jetpack Compose, Material 3. Android only. minSdk 26, targetSdk 36, compileSdk 37 (current Compose needs it).
- **Material 3 Expressive** (the current Compose Material 3 expressive components, shapes and
  motion).
- **Hyperlog's own theme**: Ink and violet (#50), the same on every phone, with no dynamic colour.
  Surfaces, text and dividers carry a faint violet tint: in light mode the surface is `#FBF9FE`,
  screens `#F0ECF7`, cards white, containers `#F7F4FB` to `#E4DEEE`, text `#1B1623` and muted text
  `#5E5570`; in dark the surface is near-black ink `#14111B`, screens `#18141F`, cards `#241E2F`,
  containers `#1C1724` to `#2E2640`, text `#ECE6F5` and muted text `#A9A0BA`. One violet accent
  marks only what you act on or what's selected: primary and primaryContainer (`#5B3DD0` light;
  `#C4B1FF` and `#6A4CE0` dark, so the Start button stands off the dark surfaces), and a violet
  tint in secondaryContainer (`#E6DEFF` light, `#3A2C66` dark) for the selected tab and chips that
  are on. **Every connected button group picks in primaryContainer** with onPrimaryContainer
  words, the deep violet of the selected calendar day, the selected tab and Start (decided by
  the owner on #58; in light it is the same violet as primary). In dark that fill is 2.9:1 on a
  card, so the choices not picked sit in a `surfaceContainerLowest` well (3.4:1 to the picked
  one); in light they are the white card. Tertiary follows primary; secondary and tertiaryContainer (advice cards, technique pills)
  stay tinted greys. The RIR, progression, plate and error colours stay fixed, and every one keeps
  at least 30° of hue from the accent, so the accent is never green, amber, red or plate blue. The
  lightest-but-one container stops at `#E4DEEE` so the hot RIR chip keeps 3:1 on it. Light and dark
  follow the system setting; there is no in-app theme picker. **On the accent** (a
  primaryContainer card or strip: the hero, the gym's setup card, Claude's reply, Day done), a
  filled button or a picked choice is onPrimaryContainer with the card's violet for its words, as
  the hero's Start, and the choices not picked sit in a shaded well: in light primary and
  primaryContainer are one violet, so a primary button there would vanish (#58). Everything that
  marks a state keeps 3:1 on what it sits on (`AccentContrastTest`).
- **Space Grotesk** (SemiBold and Bold, bundled as static fonts under the SIL Open Font License
  1.1, `docs/licences/SpaceGrotesk-OFL.txt`) sets the words of display, headline and titleLarge
  text: screen titles, the day's name and exercise names. A long exercise name at the top of a session or a
  past exercise wraps to a second line at full size; only a name longer than two lines is cut off.
- **Inter** (Regular, Medium, SemiBold and Bold, bundled as static fonts under the SIL Open Font
  License 1.1, `docs/licences/Inter-OFL.txt`) sets everything else: body, labels, titleMedium and
  titleSmall (SemiBold), and every number. **Words in titles are Space Grotesk; numbers are always
  Inter** (the owner's rule): a title's digits (`Build · weeks 3–6`, `Set 2`) are set in Inter at
  the title's weight (`interFigures`), and a figure on its own line is all Inter: the set table's
  kg and reps (Inter Medium at titleLarge's size, titleMedium's for a long value), History's latest
  weight, a trend's estimate, the goal figure and the plates' total (Inter SemiBold at
  headlineMedium's size), and the rest timer (Inter SemiBold at displayMedium's size).
  Inter's tabular figures would also widen the hyphen to a digit's width ("Ramp - in"), so the
  bundled fonts are patched to keep the hyphen proportional
  (`scripts/inter-proportional-hyphen.py`); digits stay tabular.
- **Larger, rounder groups**: a segmented group's outer corners are 32 dp, its joins 8 dp and its
  rows 4 dp apart; a lone card is 32 dp. Every group and card draws through `Segment` and
  `CardShape`.
- **Tabular figures everywhere**: every style in the type scale sets `tnum`, so weights, reps,
  RIR, dates, timers and counts keep their columns as they change.
- **Every symbol is a Material Symbol** (Rounded). The prototype bundles the 15 MB variable font;
  the app ships only the icons it uses (vector drawables or a subset font).
- Room for storage. Everything lives on the device.
- kotlinx.serialization for program files, backup import and coach changes.
- The network is for the update check (Distribution) and, with a Claude key, the coach's calls to
  `api.anthropic.com` (Coaching, ADR 0005). Nothing else goes out.
- Application ID `com.hyperlog.app`, debug builds `.dev`. A debug build is labelled **DEV Hyperlog** and has its own launcher icon, the logo on amber with a DEV badge (themed icon too), so it can never be mistaken for the real app. The launcher glyph spans about 46 dp of the 108 dp adaptive canvas at its tallest, inside the keyline and as large as other apps' icons, in the coloured and themed layers alike.

## Distribution

- **GitHub Releases**, no Play Store fee. Pushing a version tag makes GitHub Actions
  (`.github/workflows/release.yml`) build and sign a release APK and publish it as a release on
  that tag in the public `villekivela/hyperlog` repo itself. The first install comes from that
  release page in the phone's browser; after that the app updates itself (below). Fallback: Firebase App Distribution (free,
  email invites).
- **Versions come from the tag.** Tag `vMAJOR.MINOR.PATCH` (minor and patch 0–99) builds with
  `-PhyperlogVersion=vMAJOR.MINOR.PATCH`: versionName `MAJOR.MINOR.PATCH`, versionCode
  `MAJOR·10000 + MINOR·100 + PATCH` (v0.2.0 is 200, v1.0.0 is 10000). Without the property a build
  is 0.1.0. A release's name is its tag; it is neither a draft nor a prerelease and holds exactly
  one asset, `hyperlog-MAJOR.MINOR.PATCH.apk`. Never reuse or lower a tag: Android refuses a lower
  versionCode.
- **Update check.** Each time the app comes to the foreground, but at most once in 15 minutes
  (coming back sooner reuses the last answer), it reads
  `https://api.github.com/repos/villekivela/hyperlog/releases/latest` (a plain GET with
  no token, `User-Agent: Hyperlog`, nothing about the user or their data), off the main thread
  (5 s to connect, 5 s to read, 10 s in all). A release whose tag gives a higher versionCode
  than the installed one shows a banner on Today: "Hyperlog 0.2.0 is available · Tap to update", with a cross that hides that
  version for good. Tapping downloads the APK ("Downloading…") and hands it to Android's
  installer, which asks "Update this app?"; the first time, Android asks to allow Hyperlog to
  install apps (`REQUEST_INSTALL_PACKAGES`). A failed download says "Couldn't download it. Tap to
  try again." Offline, no release, a slow answer or a missing APK: no banner, and the next check
  tries again. Nothing waits on the check.
  - **Settings → Check for updates**, a switch, on by default. Off, the app never asks GitHub;
    turned back on, it forgets the last answer, so the next check reads GitHub.
  - **Settings → Check for updates now** (under the switch, while it is on) reads GitHub at once,
    whatever the last answer, and says what it found: `A newer version is ready: see Today` (the
    banner shows) or `No newer version, or no connection`.
  - **The installed version** is the last line of Settings, under the update rows: "Hyperlog
    0.2.0" (a debug build says "Hyperlog 0.1.0-dev").
  - "Check for updates" and the dismissed version are backed up; the last check is a cache and
    isn't.
  - **Debug builds (`.dev`) don't check.** A launch extra feeds them a fake release whose APK is
    the installed build, to try the banner and installer on a phone:
    `adb shell am start -n com.hyperlog.app.dev/com.hyperlog.app.MainActivity --es fake_release 9.9.9`.
- Signed with a release key (PKCS12, alias `hyperlog`) kept outside the repo, backed up by the
  owner, and stored as the GitHub Actions secrets `HYPERLOG_KEYSTORE_BASE64` and
  `HYPERLOG_KEYSTORE_PASSWORD`. Every release is signed with that key forever: updates and Auto
  Backup restores need the same key. Local release builds without the key come out unsigned.
  The legacy app was signed with the old machine's debug key, so the new app can't install over it:
  uninstall the legacy app when switching; its data stays in the backup.
- **Google's developer verification** (checked 2026-10-13): since 30 Sep 2026 it is enforced only
  in Brazil, Indonesia, Singapore and Thailand; all other regions follow "in 2027 and beyond".
  Installing unregistered apps over adb stays allowed. A free hobbyist account exists (no fee, no
  ID, up to 20 devices). **To do:** register a hobbyist account for Hyperlog before the 2027
  rollout. Sources:
  <https://android-developers.googleblog.com/2026/06/android-developer-verification.html>,
  <https://android-developers.googleblog.com/2026/03/android-developer-verification-rolling-out-to-all-developers.html>.
- During development, debug builds install over USB with adb alongside the release build.
- **Other users** get the same app with no accounts and no server; each has their own data on their
  phone. **No third-party programs are bundled**: Nippard's programs are paid and copyrighted, so
  each user imports program files they own. The app ships with the exercise library and one
  original starter program, Hyperlog Full Body (`docs/programs/full-body-3-day.md`).
  - **Its time budget** is two ~55-min days (A, B) and one ~45-min day (C), from the first
    warm-up set to the last set. Day A's bench back-offs are 2 × 8–10 @ 1, and it ends on a seated
    calf raise. Day B holds the cable shoulder press and lateral raise, cable flye and reverse
    crossover supersets and ends on the Bayesian curl. Day C pairs the machine pulldown with the DB
    incline press and has no pec deck or calf raise. It stays gym-neutral; hand pain and missing
    equipment are the coach's Change and swaps.
  - **Its week-12 bench test** ("the bench goal if the week-11 estimate is within 3% of it, e.g.
    97 kg for a 100 kg goal, else a single @ 1 RIR") is a load rule, which the program format
    can't hold. It is written as a heavy single (1 rep @ 1 RIR, its weight suggested from the
    estimate like any new rep range), and the goal condition is in that slot's note.
- Starting weights from a legacy backup are a one-off "Import starting weights" action in settings,
  not built into the app.

## Domain

| Term | Meaning |
| --- | --- |
| **Program** | A periodized plan: blocks of weeks, each week a set of days. |
| **Block** | A run of weeks (`weekStart..weekEnd`) that share the same days, e.g. Accumulation 1–7. |
| **Day** | One planned session: an ordered list of groups. |
| **Group** | Exercises done together: `straight` or `superset`. |
| **Slot** | A place in a day for one exercise. It has a planned exercise and alternatives. |
| **Set prescription** | A work set's reps (fixed or a range), optional target RIR, rest, intensity technique and back-off rule. |
| **Position** | Where you are: program, week, next day. |
| **Session** | What was done on a date: sets with weight, reps, RIR. It's a record and is never recalculated. |
| **Change** | A proposed edit to the plan (see Coaching). Accepted changes are kept as history. |
| **Workout** | A day Claude builds for one date, outside the program (see Coaching). Training it doesn't move the position. |
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
  with the same rep range (in the same slot when there is one, else in any), against the
  prescription they were done under. So a day that benches 4–6 and another that benches 6–8 each
  progress on their own; with no session in that range it's a new rep range (below). A session
  done ad hoc is judged against today's prescription. Effort is reps + RIR, so 11 @ 1 is as good as
  10 @ 2.
- **"Last time" in a program session is that session**: the Last column, the first set's prefill
  and the why-line all show it, so Day A's bench reads Day A's sets even when Day C benched since.
  With no session in the range (a new rep range), the Last column shows the latest session. In an
  ad-hoc session, and for exercises added to a program day, last time is the latest session.
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
- **Every suggestion says why**, e.g. "Last time 50 kg × 12, 12, 12 [2], top of 8–12 → 52.5 kg"
  (every set reached the top at the target RIR; 12, 12, 11 [2] would keep 50 kg for 12 reps).
  - The why-line sits under the exercise name (and its swap or superset chip) for the active set,
    one short muted line led by a direction symbol (`trending_up`, `trending_flat`,
    `trending_down`) in a fixed colour scale like RIR's, the same on every phone: calm green for
    up, neutral for the same weight, amber for down (a deload or back-off isn't a failure, so never
    red). It is short (`↑ 62.5 · hit the top of 8–10 last time`); the full sentence is in the
    exercise sheet. A set that just follows the one before has no line.
- Coaching changes (e.g. `adjust_load -10%` after time off) apply on top of the suggestion for their
  scope; progression then continues from what you actually lift.

### Intensity techniques

A work set can carry one intensity technique, prescribed in the program and changeable during the
session ("do a drop set on this one"). Each technique has its own prescription, its own way of
logging, and computed loads where it needs them.

| Technique | Prescription | Logged as | App computes |
| --- | --- | --- | --- |
| `dropset` | drops (1–3), reduction % (default 25) | weight × reps for each drop | drop weights from the set weight, rounded to the exercise's increment |
| `myo_reps` | activation reps, mini-set target reps (no maximum) | activation set, then reps per mini-set | mini-sets continue while each hits the target; a miss, or **Finish set**, closes the set; short rest countdown between mini-sets |
| `lengthened_partials` | partials to failure, or a target count | full reps + partial reps | — |
| `iso_hold` | seconds, position (stretch/contraction) | seconds held | hold timer |
| `failure` | last set to failure | full reps (+ optional partials) | — |

- **Drops**: each drop is the weight before it (the set's, then the drop actually done) minus the
  reduction, rounded down to a rung (the lightest rung if none is below). Drop reps prefill from
  the same drop last time, else 6.
- **Myo-reps**: the set's reps are the activation set. Mini-sets are at its weight (locked) and
  prefill with their target; a 15 s breathing countdown comes before each. The set closes when a
  mini-set falls short of the target, or **Finish set** is tapped; there is no prescribed number
  of mini-sets and no maximum, in the program, the coach or the screen. Before the set, one planned
  mini-set row shows the first one's target; the pad reads "Mini-set N". There is no `+` on a myo
  pill. Programs and stored sets that still carry `maxMiniSets` load, and it is ignored.
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
  starts with the format's defaults (2 drops of 25%, mini-sets of 3, partials to failure)
  and iso hold at 20 s in the stretch. The change is for this session only; a logged set keeps
  the technique it was done with.
- The session list and Session done read a set with its extensions after it and its RIR chip
  last, after the whole set: `25 × 9, 18 × 7, 12 × 6 [1]`, `60 × 12 + 4 + 3 [0]`,
  `40 × 10 + 20 s [0]`, `50 × 8 + 3p [0]`. A set never wraps inside. Plain text keeps the RIR after
  the set's own reps (`25 × 9 @ 1, 18 × 7, 12 × 6`), as the Claude prompt's legend reads it.

### Full and partial reps

Every set logs **full reps** and **partial reps** separately, on any set, whether or not partials
were prescribed. Rep entry uses an in-app number pad with a **Full / Partial toggle** (as in
MacroFactor's F/P toggle): type the full reps, flip to Partial, type the partials. Typing `12+5`
also works. History shows `50 kg × 12 + 5p`. Partials never count towards e1RM or progression.

### Gyms and equipment

You may train at several gyms with different equipment, and cable stacks differ between machines, so
available weights belong to a **machine at a gym**, not to an exercise.

- **Gym**: a named place with its equipment. Picked at session start.
- **Equipment item**: one piece of equipment at a gym (a cable station, a leg extension, the
  dumbbell rack, a barbell with plates) with its **weight ladder**: a start and step in kg or lb
  (shown in kg), an explicit list, or for plate-loaded machines a base weight plus plates.
- **Each gym has its own dumbbell rack** (Gyms and equipment → the gym → Dumbbells and plates →
  Dumbbells), set up from its labels like a stack (`1 2 3 … 10 12.5 15 … 45`). A new gym, and a
  session with no gym, have the usual rack: 1 to 10 kg by 1, then 12.5 to 50 kg by 2.5. Only the
  weights on offer depend on the gym: dumbbell history and estimates stay shared across gyms, as
  for every free weight. Updating moved the one global ladder of earlier versions into every gym,
  keeping one edited by hand and replacing the untouched old default (2 kg steps to 20, then 22.5,
  25 and 5 kg steps) with the new one; a restored backup from before gets the same step.
- **Each gym has its own plates**, counted as pairs per size (Gyms and equipment → the gym →
  Dumbbells and plates → Plates, `8×20 2×15 2×10 2×5 2×2.5 2×1.25`), set a pair at a
  time with − and + per size (25, 20, 15, 10, 5, 2.5, 1.25 and 0.5 kg, plus any other size the
  gym has). The gym's barbell, its EZ, trap and safety bars (each its own bar weight: 20, 10, 25
  and 30 kg until set, shared by every gym unless a gym file names its own) and its plate-loaded
  machines all load them. A session with no gym loads the default.
  - **Default: 8 pairs of 20, then 2 pairs each of 15, 10, 5, 2.5 and 1.25 kg** (455 kg on an
    empty sled, 475 kg on a 20 kg bar). Decided because a commercial gym keeps most of its iron in
    20s, enough to load a leg press or hack squat with several plates a side, so the 20s must
    never be what caps a machine; two pairs of each smaller size make every 2.5 kg step from the
    bar and a heavy top-up (15 + 10) without counting on plates that are in use elsewhere. The
    one set of 0.1.3 (three pairs of 20) capped every plate-loaded machine at 187.5 kg.
  - Updating gave every gym the shared set edited by hand, or the new default in place of the old
    one; plate-loaded machines holding a copy of the shared set load their gym's plates from then
    on. A restored backup from before gets the same step. A gym file without plates gets the
    default.
- **A gym's screen groups its equipment by kind**, each section under its header and sorted by
  name: **Stack machines** (the cable station among them), **Plate-loaded machines**, **Bars**,
  **Racks and benches**, then **Dumbbells and plates** (the dumbbell rack and the plates for the
  pin). A kind the gym has none of has no section. Each kind has its own symbol: a stack, a cable
  for the cable station, a plate, a barbell, a bench; the dumbbells a dumbbell and the pin plates
  a pin.
- **Setup both ways**: a gym screen to list equipment up front (its **Add equipment** an extended
  FAB, in reach however long the list), and adding on the go. The first
  time an exercise is done at a gym without a known machine, the app asks "which machine?" (pick or
  add) and its ladder, then remembers it.
- **Gym is picked at session start**, defaulting to the gym you usually train at on that weekday
  (your training days' gym, else learned from history).
  - Start session opens a sheet with the gyms as a single choice and **Start**. The preselected gym
    says why: "Usual on Mondays" when the weekday plan or that weekday's sessions picked it,
    "Trained here last" when the last session did; the first gym, picked with nothing to go on,
    says nothing. One tap on another row changes it. With no gyms set up the session starts at
    once, without a gym.
  - The usual gym on a weekday: the gym your training days name for that day (Training days,
    below), or the only gym when that day is a training day and you have one gym; else the gym
    with the most sessions on that weekday, the latest breaking a tie; else the gym trained at
    last; else the first gym.
  - The session list shows the session's gym under its title. Changing gyms mid-session isn't
    offered.
- **Missing equipment**: on picking the gym, the day is checked against that gym's kit; swaps are
  proposed from the slot's alternatives, then by movement pattern, and apply to this session only.
  - A gym's kit is learned like its machines: **Not at <gym>** under "Which machine?" marks the
    exercise as one the gym has no equipment for, and swaps it at once (left out of the session when
    nothing fits). The gym screen lists them under **Not here**, each with × to undo. Everything else
    is assumed to be there.
  - The start sheet lists the swaps for the gym picked under `Not at Harbour · for this session`, as
    ticked checkbox rows reading `In place of Leg Extension` over `Reverse Nordic`; one with nothing
    that fits reads `Lying Leg Curl: nothing here fits`. Start applies the ticked ones to this
    session only; no Change is made. The candidates are the slot's alternatives, then library
    exercises with the same movement pattern, none of them missing at the gym.
- **"Which machine?"**: in a session with a gym, a machine, cable or Smith machine exercise the
  gym has no machine for yet shows "Which machine?" in place of its sets: the gym's equipment with
  the weights each gives, and **Add machine** (the ladder editor; a machine exercise's name is
  filled in, a cable station's left for you). The answer is remembered per gym and exercise, so the
  next time it goes straight to the sets. The exercise sheet shows the machine and gym, with
  **Edit** (the machine's setup, as on the gym's screen: its name, ladder, add-ons or starting
  weight; saving applies to the running session at once) and **Change** until a set is logged on it.
- **Importing a gym.** Settings → Gyms and equipment → **Import gym** reads a gym file
  (`docs/gym-format.md`) through the system file picker and adds it as a new gym; it never
  replaces or changes data already there. The file is validated strictly like a program file,
  every problem listed with its path, and nothing is added until it is clean. A file holds the
  gym's name, its usual days (`usualDays`: those days become training days at this gym, taken
  from any gym that had them), its pin plates, its dumbbell rack, its bars with their
  weights, and its equipment: stacks, plate-loaded machines, and racks and benches, each stack or
  plate-loaded item with a ladder of any kind (or the default guess) and the library exercises it
  serves, which fills in "Which machine?" for them. An item marked `unconfirmed` (the default
  without a ladder) is a guess: its first use opens its setup from the labels instead of asking
  which machine. The gym's barbell, EZ, trap and safety bars set what sessions there load the
  gym's plates on; its racks, benches and other bars are listed on the gym screen under **Bars**
  and **Racks and benches**.
- **Set up the gym from the program.** With a program active, a gym's screen (so also right after
  adding a gym) opens on a `primaryContainer` card, **Add the equipment your program uses**, while
  the program has machine or cable exercises the gym has no machine for: `6 machines and a cable
  station for Full Body 3-Day`, then **Add**. In one go it makes a machine for each machine and
  Smith machine exercise in the program's slots (not their alternatives), named for it, and one
  shared cable station for every cable exercise, and remembers each, so "Which machine?" doesn't
  ask for them. A machine already there by name is used instead; exercises marked **Not at** the
  gym are left out; running it again adds only what's new.
  - The machines use the gym's **default stack**, chosen once on the card the first time (**Stacks
    here**: **kg, with small plates** (default: pin steps of 5 kg from 5 to 100 with the gym's pin
    plates) or **lb stacks** (2.5 lb, then 5 lb steps, 40 plates)) and kept for later runs.
    Plate-loaded machines load the gym's plates from a zero start.
  - Everything it makes is a guess: each machine is unconfirmed until its first use asks for its
    stack's labels (Learning a stack).
- **One shared cable station per gym.** Every cable exercise at a gym defaults to the gym's cable
  station, so it is set up once, not per exercise: "Which machine?" doesn't ask, and the exercise
  remembers the station. A machine remembered for the exercise (a lat pulldown on its own station)
  still wins. The station is the one made by **Add the equipment your program uses**, or the first
  machine added for a cable exercise at a gym without one; the gym screen marks it `Every cable
  exercise`. Machine exercises still ask.
  - History stays per exercise and machine (ADR 0002): sharing a station never merges two
    exercises' histories, since each history is the exercise's sets on that machine.
- **History and progression are shared for free weights** (barbell, dumbbells, bodyweight, and the
  EZ, trap and safety bars, plates and weighted bodyweight) across gyms. **Machines and cables keep a history per machine**, because stacks and pulley ratios don't
  compare. On a machine with no history, the first set asks you to pick a weight for the target reps
  and RIR; progression runs from there. Machine exercise trends show one line per machine.
- In a session, the weight field is a picker over that item's ladder; free typing stays possible.
- Machine ladders are often lb stacks shown in kg (1.1 / 3.4 / 5.7 kg = start 2.5 lb, step 5 lb), so
  setup offers "lb stack" as a one-tap preset (2.5 lb, then 5 lb steps, 40 weights). Lb rungs
  show and log to 0.1 kg.
- A stack is a start, a step and a number of weights. Plates are a base weight (bar or sled) plus
  plate pairs, one of each pair per side; any combination of pairs is a rung. A plate-loaded
  machine with no pairs of its own (the default) loads its gym's plates; one given its own pairs
  loads those.
- **Setting up a stack from its labels** is the primary way to add or edit a stack machine (and a
  dumbbell rack): type two or three weights as printed on the stack, the lightest first and next to
  each other, and the app fills in the rest. **Edit exactly** keeps the start-and-step, list,
  plates and exact pin-stack fields as a fallback.
  - One field takes the weights, separated by spaces or commas, with a decimal comma allowed
    (`12,5`). `…` (or `..`) fills in the step before it up to the next weight, so `5 10 … 100`
    sets the top, and a step that changes partway is written out: `1 2 3 … 10 12.5 15 … 45`. With
    no top, a stack has 20 plates. Plates typed in kg or lb (**Printed in kg | Printed in lb**).
  - Kg steps that fit cleanly win (0.5, 1, 1.25, 2, 2.5, 3, 4, 5, 6, 7.5, 8, 10, 12.5, 15, 20 or
    25 kg). Otherwise labels in kg are read as an **lb stack**: the one lb step (2.5 to 25 lb) and
    2.5 lb start that explain every label to its printed precision, so `4.5 9.1 13.6` is 10 lb and
    `36 43 49` (labels in whole kg, cut from 36.3, 43.1, 49.9) is 15 lb. Three plates with a
    smaller first step are a lighter first plate (`2.5 5 10`: 2.5, then 5 kg steps).
  - Ambiguous input asks rather than guesses: one weight, or plates that fit more than one stack,
    read `Type one more weight to tell the steps apart`; plates that skip some (`36 49`) read `Type
    plates next to each other, from the lightest`. Save waits for a reading.
  - **Printed labels are what the pad shows and logs.** A stack read from kg labels keeps the
    labels' precision and rounding (or cutting), so the weight logged is the one read on the
    machine. Decided over keeping the true weights: per-machine history (ADR 0002) never compares
    one machine's weights with another's, so a label's error under a kilogram is the same on every
    set and never reaches progression, while a logged weight that differs from the plate would.
  - Under the fields a card checks the result, live: `You can pick these weights`, the ladder in
    one line with its count (`36–121 kg by 15 lb · 20 weights`), how it was read (`Reads as 15 lb
    steps`) and `Check: 36 · 43 · 49 …, looks right?`, which opens to every weight on a tap.
- **Learning a stack.** A machine whose ladder is a guess (one the app set up for you, from the
  program or an imported gym, never one you set up) is **unconfirmed**. The first time an exercise
  is done on it, the session opens its setup from the labels (`Set up Leg extension`: "Its weights
  are a guess…") in place of the sets; closing it keeps the guess. On a guessed ladder −/+ still
  step over it, but typed weights are kept as typed, with no weight check.
  - Once three different weights are logged on the machine (any exercise) and some are off the
    guess, the same reading as setup infers the stack (kg steps, an lb stack, or the default with
    its add-ons) and offers it once, as a `tertiaryContainer` card over the sets: `Looks like 10 lb
    steps: use that?`, `The weights typed on Leg extension fit 4.5–90.7 kg by 10 lb.`, **Keep as
    typed** and **Use**. Weights that fit more than one stack wait for more.
  - **Use** makes it the machine's ladder, confirmed. **Keep as typed** leaves the guess and is
    never offered again. The ladder never changes without an answer.
- **Stacks with add-ons** (a fourth ladder kind, **pin stack**, for selectorised machines). The pin
  picks a plate: a start, a step and a top in kg or lb (shown in kg), with an optional lighter first
  plate (2.5, then steps of 5 from 5). On top of the pin go two kinds of add-on, both at once if
  there are both:
  - **Built-in add-ons**, the machine's own: a small weight on top of the stack, or a lever or knob
    that adds a half step. Their weights are set per machine, one entry per plate, so two identical
    plates are `2×2.5` and give 0, 2.5 or 5.
  - **Plates on the pin**: the gym's small loose plates (default 1.25 and 2.5 kg), set once per gym
    under the gym's **Dumbbells and plates → Plates for the pin** and usable on every pin stack there that
    takes them (on by default).
  - The rungs are every pin weight plus any count from none to all of each built-in add-on plus any
    combination of the pin plates, sorted, with near-duplicates (under 0.15 kg apart) kept once, by
    the handiest loading: the fewest extras, then built-in add-ons before loose plates.
  - Default: pin steps of 5 kg from 5 to 100, no built-in add-ons, the gym's pin plates.
  - In a session, the pad shows how to load the typed weight in the plate strip's place and
    language: a drawing of the stack with the pin, the add-on block and the hung plates in their
    competition colours, and a legend (`Pin 40`, `2 add-ons`, `1 × 2.5`). Its description reads
    `Load pin 40 + 2 add-ons + 2.5 on the pin for 47.5 kg`. A weight off the stack reads `Not on
    this stack`.
- Setup lives in Settings → Gyms and equipment: gyms (each with its equipment, dumbbell rack and pin
  plates; the list says what each holds: its equipment items as machines and cable stations, then
  the rack, plates and pin plates every gym has, `1 machine, 1 cable station · dumbbells and
  plates`, or `Dumbbells and plates` with none; its Plates row wraps to show every plate), then the barbell and the three specialty bars (`10 kg bar, the gym’s plates`; a tap asks for the bar's
  weight). Every ladder shows in one line as its runs of steps and its count (`1–10 kg by 1 ·
  12.5–50 by 2.5 · 26 weights`, an lb stack by its kg labels and lb step: `1.1–89.6 kg by 5 lb`).
  Setup uses the system keyboard, since it happens once and away from a set. Editing a gym's
  dumbbells or plates applies to a running session at once.
- **Renaming and deleting equipment.** Every item on a gym's screen, machines, bars, racks and
  benches alike, has a ⋮ with **Rename** (a name dialog) and **Delete**. A machine's setup renames
  it through its name field and deletes it from its top bar; saving it keeps what it is (the cable
  station stays the cable station).
- **Editing a gym's bars and plate-loaded machines.** A bar on a gym's screen (the EZ, trap or
  safety bar a gym file brought in, say) opens its setup: its name and **Bar weight (kg)** (above
  0, up to 100), and the check under it shows what it loads with the gym's plates (`10 kg +
  plates, to 465 kg`); the bar has no plates of its own. A plate-loaded machine's setup has
  **Starting weight (kg)** (the empty machine or sled) the same way, over its own pairs, empty for
  the gym's. Racks and benches have no weights, so they only rename and delete.
- **Everything a gym file sets can be edited in the app**: the usual days (as training days), the
  pin plates (up to
  six, each up to 25 kg), the plates, the dumbbell rack, each bar's weight, and each machine's
  ladder with its add-ons and pin plates. The file's limits are the setup's, so an import never
  holds a value setup can't save.
- **Deleting** a gym or an equipment item asks first (`Delete Leg press?`, `Sessions done on it
  keep it in their history.`). One with history (sessions at the gym, sets
  on the machine) is archived instead of deleted: it leaves setup, the start sheet and "Which
  machine?", and exercises forget it as their machine, but its past sessions keep it, so a
  machine's history never joins another machine's or the no-gym pool (ADR 0002). History still
  heads its line `Cable left · Harbour`.

The old app's machine weights were wrong in both directions: suggestions that didn't exist on the
stack, and slow entry of odd values. The ladder picker fixes both.

## Screens

1. **Today** — the next day's prescription. Start the session, log sets (weight, full + partial
   reps, RIR, technique follow-ups), rest timer, technique video link and note for each exercise,
   swap to an alternative, add or change an intensity technique on a set. Works without a program
   too (ad-hoc session).
   - **Today opens on a calendar of weeks** (decided 2026-10-17, #53), and the day picked on it
     fills the screen below. Today has no title bar: the calendar is the top of the screen.
     - **The calendar** is a row of seven days, Monday to Sunday, under the month and year
       (`October 2026`, or `Oct – Nov 2026` for a week across two months) and the program week
       the calendar week stands for (`October 2026 · Week 7`, its figure in Inter): the week of
       the first planned day in it, else of the first program session done in it. A week with
       neither (outside the program, or with no program) shows the month alone. When the head
       doesn't fit, the pencil's words go first, then the month abbreviates (`Sep 2026 · Week
       12`), then the week goes; the month is cut only past all three. It swipes sideways a
       week at a time, back to the week of the first finished session and on to the week of the
       last planned day, and at least four weeks each way. **Paging to another week picks the same
       weekday in it**, so the card below is always of a day on screen (the pages are whole weeks,
       so that day is always on the calendar). The day picked is kept while a session opened from
       it is looked at: Back returns to that day and week, scrolled where it was. Back from another
       destination also returns to the day picked, as Back returns you to where you were; choosing
       Today on the navigation bar starts again on today. A **Today** button in the header shows
       once you've moved off today and brings the strip and the selection back. A pencil in the
       header opens the **Training days** sheet, with or without a program; with no training days
       the header says `Set your training days` beside it. When the words don't fit (larger system
       text, a long month, the Today button), the pencil shows alone and is still read out as
       `Set your training days`.
     - **Each day** has its weekday letter, its date in a 40 dp circle (filled primaryContainer
       when selected, ringed in primary when it's today and not selected) and a marker under it:
       a filled primary pill with the day's letter in onPrimary when a session was finished on it (a
       tick for an ad-hoc session or a workout; two program days' letters in order, `CA`, for two
       sessions; the first letter and a plus, `A+`, for more, or for a program day beside an
       ad-hoc session; TalkBack names every one: `done Day C, workout, ad-hoc session`, a
       workout never called ad hoc), an outlined primary pill with the letter when a
       program day is planned for it, else nothing. Past days without a session have muted dates.
       A day's letter is the one its name starts with (`Day A — …`), else its place in the week.
     - **Planned dates.** The program runs as a sequence, so its dates are an estimate. The next
       day goes on today, even when today isn't a training day (decided by the owner on #58:
       Start is always for today), or on the next training day when a session was already
       finished today (any session). Training days only place the later, estimated dates. Each following day goes on the next training day after that, to the
       program's last day. **With no training days, only the next day is marked** (tomorrow when
       a session was finished today). Each planned day is read for its date, with the Changes
       covering it. A finished program plans nothing.
     - **The next day to train**, on whatever date it falls, is the hero card in primaryContainer:
       the day's letter in the 9-sided cookie, `Week 7 · Intensify` (`Next · Mon 19 Oct · Week 7`
       when it isn't today, like a later planned day's, so the line fits) over the name, its numbers (exercises, sets, about how many
       minutes, `~N minutes`, see **Minutes** below) and **Start session**
       across the card. Then each slot's exercise with its sets (`1 × 4–6 [1] · 2 × 6–8 [1]`,
       technique symbols after; a Change's swap reads `In place of …` over the name), the slot
       label (`1`, `5a`) centred on its row, and **Ad-hoc session** as a text button after them.
       Start session starts the program's next day at the picked gym. While a session runs, the
       next day on a later date reads as a later planned day, without Start.
     - **Minutes** (the hero card and planned days), to the nearest 5, set
       on #64; the numbers live in `EstimateSeconds` (`ui/today/DayEstimate.kt`) to tune after real
       sessions.
       1. **Past sessions first.** With finished sessions of the same program day (the same day
          in the active program), the estimate is the median duration (`finishedAt − startedAt`)
          of the latest 3 (of 1 or 2, the median of those). Other days' sessions and running
          sessions don't count.
       2. **Otherwise the whole session is counted:** each work set about 45 s; warm-ups about
          60 s each, and the card has no weight to ramp from, so 3 for a main lift (bench, squat
          pattern, RDL, row) and 1 for any other exercise; a changeover of about 90 s per
          exercise, once per superset; techniques on their set: about 30 s per drop, 45 s for
          myo-reps, 15 s for lengthened partials; and rest as the session gives it (none between
          superset partners, none before drops, none after the day's last set).
     - **A day with finished sessions** has a card for each on the card tone: `Done · Sat 17 Oct ·
       Kalliokoski`, the letter, the name, the numbers (exercises, sets, minutes taken) and **Open
       session**, which opens it in History (Back comes back to Today). Then its exercises with
       the sets as logged, RIR chips filled. On today, `Next: Day A on Mon 19 Oct` comes first,
       over the cards (what's next is the first question after training), selecting that day;
       Ad-hoc session follows the day.
     - **A day of a deload week** (as the program marks it, GLOSSARY Deload), the hero or a planned
       card, carries a small `Deload` tag under its name: the spa symbol and the word in a 24 dp
       tertiaryContainer pill, like the technique pills. The program has no mark for a test, so
       the week-12 bench test isn't tagged.
     - **A later planned day** reads `Planned · Wed 21 Oct · Week 7` with the letter outlined, its
       numbers and its planned exercises. It has no Start. With training days, the last date
       planned is the program's last day, and reads `Last day · Wed 25 Nov · Week 12`.
     - **A day with nothing** is `Rest day`, with `Next: Day B on Wed 21 Oct` selecting the next
       planned day. **Past the program's last planned day** it reads `Nothing planned`, with
       `Last planned: Day C on Wed 25 Nov` selecting that day. **With no training days** only the
       next day has a date, so a later date reads `Not planned yet`, with `Next: Day B on Sun 18
       Oct` selecting the next day and **Set your training days** opening their sheet: the program
       goes on, it just isn't dated.
     - **Until the finished sessions are read**, Today shows nothing under the navigation bar: no
       calendar, no plan, no Start, and no floating Start workout. Drawing today untrained first would offer the wrong Start
       for a moment on every cold start, then jump.
     - The update and time-off banners show only while today is selected. A workout from Claude
       and a running session keep their own layout on today, with their Start or Resume floating.
     - With no program, today keeps the starter offer (or the empty state) under the calendar, and
       a finished program says so and offers an ad-hoc session.
   - A **workout** added from Coach comes first, for the day it was added, as today's main
     Start (Start workout floats); the program day follows as a second card with a tonal Start
     (Coaching → Workout).
     While a program session runs, Today shows that session's day, the one **Resume session**
     opens, even after a finish and Resume moved the program's position on; while a workout's
     session runs, the workout, headed "Workout".
   - **A program day's session** has a row for every planned set from the start, its target shown
     as muted placeholders in the empty cells (`6–8` in Reps, an outlined RIR chip in RIR) until typed. The first set prefills with the suggestion (Automatic
     progressive overload): its weight, at the target RIR; with no history, an empty weight. **Reps
     are never prefilled**: the Reps cell starts empty and shows the target range (`6–8`) as a muted
     placeholder, and the row's ✓ stays disabled until reps are typed (the pad's ✓ on the weight
     moves on to the reps; on the reps it logs only once they are typed). Later sets
     follow the set before (its RIR), and their weight moves with the RIR gap. Rest after a set is the program's rest for it. After an
     exercise's last planned set the first exercise with sets left comes up. When every planned
     set is logged, the pad gives way to **Day done** (with the session's records, if any) and
     Finish session.
   - The **prescription sheet** (tap the exercise name) shows every set's target, technique or
     back-off rule, the rest, the slot note and the exercise's technique note.
   - **Edit on the fly:** add, remove, reorder or swap exercises; add or remove sets; change reps,
     RIR or technique. Edits apply to this session only, with an optional "keep for the rest of the
     block" that saves them as a Change (the Changes ticket). The next session of the day has the
     program as written.
     - **Keep edits for the block** (session ⋮, shown while the session has edits it can keep)
       makes a `rest_of_block` Change, "Keep Day A edits for the rest of Build", and opens its
       diff. It keeps swaps, removals, set counts, an RIR change every set of an exercise shares,
       and added exercises with sets logged (their logged sets become the prescription: count,
       rep range, lowest RIR). Reps targets, techniques and the order stay in the session.
     - **Swap** (⋮ → Swap exercise, or the session list row's ⋮) offers the slot's planned exercise
       and alternatives, then the library's exercises with the same movement pattern; a search
       covers the whole library. The swapped exercise keeps the slot (and its superset partner and
       label) and does the sets the program gives it (an alternative's own, else the slot's). Its
       history, suggestion and machine are its own. Once a set is logged on an exercise it can't be
       swapped or removed. A swapped slot has an outlined chip under the name, `⇄ for <planned
       exercise>`; a superset's chip reads `A1 + <partner>`. The chips can be tapped: the swap
       chip opens Swap (while the exercise can still be swapped), the superset chip brings the
       partner on screen (as the session list does, so a set left open closes and its rest runs).
     - **Sets:** **Add set** under a program exercise's sets adds one like its last planned set,
       without its technique (on a finished exercise it brings the exercise back into the order).
       A set's circle in the index column opens its sheet (the session ⋮ has no set options): **Remove set**, and its target (fewest reps, most reps,
       RIR, by steps), as long as the set isn't logged. One set always stays, and so does one that
       others back off from, or one with a set logged after it. Removing the active exercise's last
       set to do moves the session on. An edit keeps what was typed for the set to do; an untouched
       prefill follows the new target.
     - **Exercises:** the session list row's ⋮ swaps, adds a set, moves the exercise up or down (a
       superset moves as a pair) and removes it (asking first; only with nothing logged). Added
       exercises are open-ended like ad-hoc ones (no slot, no target), in a program session too.
   - **Warm-ups** generated from the first work set's weight (ramp by percentage, rounded to the
     equipment's available weights).
     - The ramp follows the weight planned or typed for the first work set, but not keystroke by
       keystroke: while the weight is being typed the warm-ups stay as they were, and they ramp
       again once the pad moves on to reps or → is pressed (typing `80` never passes through an
       8 kg ramp). Lifts with
       several joints get 45% × 8, 60% × 5, 75% × 3, 85% × 2 when the first work set is 6 reps or
       fewer (its planned top reps, or last time's reps for an ad-hoc exercise; typing reps never
       changes the warm-ups), else 45% × 8, 65% × 5, 85% × 3;
       single-joint exercises (by movement pattern: curls,
       raises, extensions, flyes, calf raises…) get one, 60% × 8. Each is the nearest rung; on a
       bar or plate-loaded machine, the nearest 5 kg step, so the small plates are only for work
       sets. A warm-up that doesn't climb, or reaches the work weight, is left out (a 20 kg top set
       on the bar has none). Bodyweight and weighted bodyweight exercises have none.
     - They show as full `W` rows above the first work set, in ad-hoc sessions too. They are never
       the rows scrolled away first: the table scrolls as one list, with a fade at an edge that has
       more, and the active row always in view.
     - A warm-up row works like a set row: a tap puts it on the pad, its kg and reps can be typed
       (the plate strip shows its plates; the rows draw none), and → goes kg, reps, then ticks it
       and moves to the next warm-up or set 1, with no RIR and no rest. A warm-up typed by hand
       stays as typed for this session, and only that one: the others go on following the ramp
       (a new weight for set 1 still ramps them), and so does one typed back to its ramp value. A
       typed warm-up past the end of a shorter ramp is left out. The next session ramps again.
     - When the session moves to an exercise, its first unticked warm-up is the active row; with
       none, set 1. This holds however it gets there: the last set of the one before, the second
       exercise of a superset pair, or opening it from the session list; the first exercise of a session
       and an exercise added to it count too. Reopening an app already on an exercise changes nothing.
     - Its check ticks it off. Warm-ups are never logged: they start no rest, add no volume and
       never reach progression or history. They go once the first work set is logged. Ticking is
       optional. Ticks and typed warm-ups are saved with the session.
   - **The check column.** Every row, warm-ups and work sets, ends in a check circle (MacroFactor
     and Hevy have one). On a work set it logs the set with the values shown, prefilled or typed,
     and starts rest: a set done as planned is one tap. → on the pad still logs too. A done row has
     a filled `primary` check and full-strength values, with no row tint. A tap on the check of the
     last checked row takes it back (a work set goes back to editable and unlogged, its exercise
     comes back on screen and its rest stops; not once one of its drops, mini-sets or its hold is
     logged, so takebacks stay in order, nor as a double tap within a second of logging it);
     earlier rows are fixed by tapping the row. The check of a drop, mini-set or hold being done logs it as shown.
   - **The index column** shows `W` for a warm-up and the set number for a work set, on a circle.
     A set with a technique (drop set, myo-reps, lengthened partials, to failure, rest-pause) has
     a tinted circle with the technique's Material Symbol as a small badge on its corner, so it
     reads without colour. A back-off set has no mark: its lowered weight shows it. A set's drops
     or mini-sets join it in one tall pill down the index column, with `+` at a drop
     set's foot to add another drop, before the set or while it is under way. Before a myo-reps set,
     one muted planned mini-set row shows in its pill; myo-reps have no `+` and no maximum. A tap on a circle explains
     the technique and opens the set's sheet.
   - **The active row has no highlight.** The row being done (a set, a drop, a mini-set, a hold,
     a warm-up on the pad or a row being corrected) sits flat like the others; its kg and reps
     are outlined cells, the focused one outlined in primary. A set's index circle is filled with
     primary while it is being done, and stays so through its drops or mini-sets, so a
     technique's tall pill is never cut by a row shape.
   - **Rest timer** that keeps running with the screen off: ongoing notification, and vibration
     when rest ends (no sound unless it's turned on in the system's settings).
   - **Plate calculator** for barbell and plate-loaded exercises.
     - Barbell exercises use the gym's plates; machines whose ladder is plates use the gym's too,
       or their own pairs if they have them. While
       the weight is typed the pad draws one side of the bar with its plates (fixed competition
       colours, sized by weight) and a legend (`1 × 20`, `1 × 15`, the bar's 20), live, for a
       warm-up on the pad too. Plates are the fewest that make the weight, heaviest
       first, from the pairs there are. A weight the plates can't make draws the bar alone with
       "Not on these plates" (the weight check says so on ✓). Design:
       `docs/design/logging-screen.md`, Plates.
     - **Which exercises get it**: plate-loaded bars (barbell, EZ, trap and safety bars) and
       plate-loaded machines (a leg press, a plate-loaded hack squat, a Smith machine, a sled):
       equipment whose ladder is a base plus plate pairs. Never dumbbells, stack or cable
       machines, or bodyweight exercises.
     - A plate-loaded machine's base is its **starting weight**, the empty machine or sled, set
       per machine in its equipment (0 kg by default, editable). It counts toward the total, as a
       bar's weight does, and the legend shows it with its own symbol (a sled) where a bar's
       weight has a slash.
     - A machine added for a plate-loaded exercise starts on plates: the gym's plates (its pairs
       field left empty) from a 0 kg start. Its own pairs, if typed, are one number a pair or a
       count (`3×20`).
     - "Which machine?" is skipped when the gym has a machine named for the exercise: it is taken
       and remembered. Otherwise it offers the machines of the exercise's kind first (plate-loaded
       or not), those sharing a word with its name at the top, then the others.
     - A tap on the pad's plates opens the **whole bar**: the total, both sides with every plate,
       and each plate with how many go on each side, then the bar or the machine's starting
       weight.
     - EZ, trap and safety bar exercises use the gym's plates on their own bar, so they get the
       same line, warm-ups on 5 kg steps and a weight check against the real weights.
   - **Layout and interaction** follow `docs/design/logging-screen.md`: one exercise at a time as
     set rows (index, last time, kg, reps, RIR, check) flat on the screen with no card behind them,
     techniques and their planned drops/mini-sets visible before the set, and a compact in-app pad
     drawn like the system number keyboard. The top is the exercise name with the rest pill and ⋮,
     the swap or superset chip, and the why-line. **Next** is the pad's top row, on the pad's
     surface, and stays at the bottom as the same row when the pad is hidden.
   - **Rest rules**: no rest before drops, between myo mini-sets (a short breathing countdown
     instead) or between superset partners; full rest after the set or superset round.
   - **Rest on the lock screen**: an ongoing notification with time left, the next set's target,
     **+15** and **End rest**; it vibrates when rest ends with the screen off.
     - It reads `DB Bench Press · Set 3` over `25 kg × 8–10 @ 1` (the prefilled weight, the target
       reps and RIR; `10–12 reps @ 2` with no weight yet), with `Rest` and the countdown in its
       header, and the actions **+15** and **End rest**, as on the rest sheet. On Android 16 it is a
       Live Update, so the countdown also sits in the status bar as a chip.
     - When rest runs out the phone buzzes twice with alarm-class vibration (through vibrate and
       silent mode), and the notification reads `Rest over` and counts up, with End rest, until the
       next set is logged. Out of the app it also pops up, vibrating only: no sound by default.
       The sound can be turned on in the system's settings for the "Rest over" channel. With the app on
       screen it only buzzes, once.
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
   - **Prefills** follow what just happened (each set takes the previous set's weight and RIR, never
     its reps; drop reps prefill from last time's drops).
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
     - A correction changes only the values (weight, reps, partials, RIR, left/right); the set keeps
       its technique and its drops, mini-sets or hold. Mini-sets and a hold follow a corrected set
       weight, since they are done at it; their own weight can't be typed. Drops keep theirs.
     - It never touches rest or the rest notification, which keep showing the set to do. A
       changed weight gets the weight check a logged one does (against the set before it, else
       last time's first): the first ✓ warns, a second keeps it, and it then counts as kept. A
       corrected weight that passes the check no longer counts as kept.
     - **No "logged" snackbar**: logging a set shows nothing over the table. The check of the set
       logged last takes it back; any other fix is a tap on its row.
     - The set to do is prefilled again from the correction if nothing was typed into it yet.
     - Rows can't be corrected while a drop set, myo-reps or hold is still open; finish it first.
     - A correction half typed isn't kept if the app is killed; the row keeps its logged values.
   - **Out of order** (owner, 2026-10-24): tapping a later, unlogged set of a program exercise
     makes it the active row, in this exercise and in later exercises of the session.
     - The pad moves to it, prefilled as its row shows it: the weight planned for it (from the set
       before it, the one being typed for the set to do, or its back-off) and its target RIR, else
       the RIR of the set logged before it. The set to do steps back and keeps what was typed for it.
     - Logging it logs it in its own place, out of order, and starts its rest (its own rest time,
       in a superset too). The active row then goes back to the first unlogged set, so the skipped
       set is still waiting. A drop, mini-set or hold still follows its own set, with no rest
       before it, and rest starts once the set closes; the set to do is then prefilled afresh,
       since the pad's entry was the drops'.
     - Back, the hide key (`close`, "Back to the set") or a tap on the set to do returns the pad to
       the first unlogged set without logging. So does a correction or a warm-up put on the pad.
     - The check of a set logged out of order takes it back while it is the set logged last: it
       goes back on the pad in its place and the set to do keeps its entry. A skipped set logged
       after it is then the one logged last.
     - A correction of a set logged out of order changes it in its own place. A record is judged
       in the order sets were logged, so a set logged out of order keeps its mark when a skipped
       set logged after it beats it.
     - Progression reads a set against its own target. The suggestion and prefill of the set to do
       come from the sets before it; a set logged out of order doesn't move them. A set left
       skipped when the session finishes stays unlogged, and later sessions read the others
       against their own targets.
     - A planned set can't be taken out while a set after it is logged.
     - Warm-ups are the exception: they are ticked in order, so typing a later warm-up and ✓ (or its
       check) ticks it with the ones before it, as before (owner, 2026-10-24). Tapping a later work
       set while a warm-up is on the pad works like any other.
     - A picked set half typed isn't kept if the app is killed; the pad comes back on the set to do.
     - **Open** on a finished exercise in the session list shows its table in place of the current
       one, to look at or correct; the line at the bottom reads "Now <current exercise>" and goes
       back, as does Back. The session stays on its set and rest keeps running.
   - **RIR is optional.** Tapping the selected RIR again clears it, and the set logs without one
     (the RIR cell stays empty). A set without an RIR is kept like any other but feeds no estimate
     and moves no suggestion, since progression is judged on reps + RIR. It reads without `@ RIR`
     wherever sets are summarised.
   - Drops and myo mini-sets record no RIR; they are taken to failure.
   - **Ad-hoc session** (no program): Today offers **Start session**, or **Resume session** while
     one runs. At most one session runs at a time, and the app opens on it. You add exercises from
     the exercise library (searchable; hidden exercises aren't offered) through ⋮ or the session
     list, which also has **Finish session**.
     - There is no prescription, so the Target column is left out and the sets are open-ended:
       there is always one active row after the logged ones. Last time's sets of the exercise show
       as muted upcoming rows, so you can see what is likely to come.
     - The first set prefills from last time's first set. With no history the pad starts on an
       empty weight. Later sets take the previous set's weight and RIR, never its reps. A set logs only
       with a weight and full reps; **RIR is optional**.
     - Rest after each set is 2:00.
     - **One ✓, one set**: a ✓ within 1 s of logging a set logs nothing, so a stutter or double
       tap can't log a phantom set. No two sets are ever logged that close on purpose.
     - −/+ and the weight strip use the shared dumbbell ladder for dumbbell exercises, the gym's
       plates on a 20 kg bar for barbell exercises, the gym's plates on the bar's own weight for
       EZ, trap and safety bar exercises, the machine's ladder for machines and cables, and 2.5 kg
       steps for everything else (and for machines in a session with no gym).
     - Last time, for machines and cables, is the last session on the same machine; free weights
       take the last session at any gym. (A program day's exercise shows the session progression
       judges instead: Automatic progressive overload.)
     - Finishing shows **Session done**. Exercises with nothing logged are left out of it, and a
       session with nothing logged is discarded. Each exercise opens its table to correct, as a
       past session's does; back returns to the summary.
     - **Finishing is deliberate and recoverable.** Finish session (⋮, below a divider, or the
       session list) asks "Finish the session?" (Keep going / Finish). Session done offers
       **Resume session**, which reopens the session exactly as it was.
2. **Program** — the block you're in and how it differs from the one before, its weeks, and each
   day of a week in full, past days as trained. A destination of the navigation bar. It reads the
   plan; it never edits it.
   - **The header card** holds the program's title and its **Next** day (or "Program finished").
     Under it, the training days' row (Training days), then, while there are accepted
     Changes, one row **Changes · 1 in force** ("Changes · none
     in force" once all are reverted or replaced) opens the Changes page with Revert (Coaching),
     a reverted one reading "Reverted 16 Oct". The navigation bar is hidden on that page.
   - **The actions are Changes**, in ⋮ above Import program and Run Hyperlog Full Body (a divider
     between) while the program runs: **Skip day** (`skip_day`), **Repeat week 5** (`repeat_week`,
     named for the week it repeats: the last one trained),
     **Deload week** (`deload_week` for the week you're in) and **Shift program** (`shift_program`,
     1–8 weeks later, picked by steps first). Each opens its diff, and applies only on Accept.
   - **Blocks**: a connected button group picks the block (a scrolling row of chips past four
     blocks), opening on the block you're in. The block's card names it (`Build · weeks 3–6`), then
     chips pick the week, opening on the week you're in (done weeks ticked), then **From <previous
     block>**. A block-wide week override that differs is said once, for every set ("Every set:
     1 RIR closer to failure · techniques back", "A deload week"). Then each slot written
     differently, comparing the previous block's last week with this block's first without that
     override, Day A beside Day A (a swap as "In place of …", sets before → after, an added or
     removed slot), or "Otherwise written as in Ramp-in" when none is. That comparison is the
     whole description of a block; the program format has no note for it. The first block has
     none. Past five slots it shows the first three and folds the rest behind "N more", unfolded
     in place.
   - **Days**: the picked week's days, each headed by its week and name (`Week 7 · Day A — …`). A day to come is in full, slot by
     slot as Today lists it, exactly as the session will prescribe it: the program's day with week
     and day overrides, deloads and the Changes covering it, through the same plan Today and the
     session read. The next day is headed **Next**; a deload day **Deload** with the `spa` symbol. There are no dates.
     The next day is read for today, as Today shows it. A later day takes a date-ranged Change by
     the window the Change's card on Coach shows (its in-force diff): one week of program days per
     week of dates, counted from the later of the range's start and today, never more program days
     than dates, starting at the next day. Other Changes cover it as they cover today. A day a
     date-ranged Change covers reads "Ends 26 Oct; if this day falls later, it's as written."
   - **Past days show the sessions.** A done day (one before the position with a session of this
     program for it, the latest if a repeated week trained it twice) shows the date and each
     exercise's top set (the set itself, without its drops, mini-sets or hold), and opens that
     session in History; Back from it returns to Program. A day before the position with no
     session was skipped: it shows its plan, dimmed, headed **Skipped**.
   - **Program keeps where you were** (the week picked, the Changes page) while a session opened
     from it or a Change's review is up; choosing Program on the navigation bar opens it on where
     you are.
   - **Import program** picks a JSON file. A file with problems shows them all, path then message,
     and imports nothing. Importing over a program asks first ("Replace <title>?"), saying where
     you are: "You're in week 6 of Hyperlog Full Body; the new program starts at week 1, day 1
     instead. Past sessions are kept."
   - **Starter program**: while no program runs (the first run), Today and Program offer Hyperlog
     Full Body with **Run this program**; Today keeps Start session (ad hoc) and Import a program
     beside it. Program's ⋮ keeps "Run Hyperlog Full Body" later, asking before it replaces the
     program running. The starter is an ordinary program file in the app's assets
     (`programs/full-body-3-day.json`) and imports through the same validator.
   - **Position**: the program runs as a sequence. Finishing a session of the next day moves the
     position to the day after it (the next day of the block, then the next week's first day); a
     session with nothing logged is discarded and moves nothing; finishing a resumed session again
     doesn't move it twice. After the last day the program is finished.
   - Design: `docs/design/program-and-today.md`; Changes and their diff: `docs/design/changes.md`.
3. **Coach** — see Coaching below. A destination of the navigation bar (`sports`), there at any
   time: a check-in with Claude, ending in words, a Change, a workout or a new program. With no
   program running no Change can come back.
4. **History** — session list, per-exercise top set and e1RM trend, weekly sets per muscle.
   A destination of the navigation bar (`history`). Two tabs: **Sessions** (the default) and **Progress**.
   Design: `docs/design/history.md`.
   - **Sessions** lists finished sessions newest first: the day trained (or "Ad-hoc session", or a
     workout's name) and
     the date, then the gym and the exercises. A row opens the session (Past sessions are
     editable, below). An open session's top bar holds only Back; its name is the headline under
     it, two lines at most, so a long day name (`Day C — Bench volume, leg press, pulldown`)
     isn't cut at large text sizes.
   - **Progress** shows the bodyweight (latest value, its date, a small line of the last 90 days;
     a tap opens the log, + logs today's), sets per muscle for this week (chevrons step back up to
     8 weeks), and every exercise trained, the one done last first, with a sparkline of its
     estimate and the latest value (a machine exercise says "2 machines" instead).
   - **An exercise's trend** shows the latest estimate, a chart of the estimate after each session
     (a line) with each session's top set weight (dots), and the sessions as a table (date, top
     set, estimate), newest first; a row opens that session. A tap or drag on the chart reads the
     nearest session above it. Machines and cables get one chart per machine, headed
     `Cable left · Harbour`, the machine used last first, each on its own scale (ADR 0002).
     - The top set is the session's heaviest work set that counts (a kept weight nothing confirmed
       doesn't), with the most reps at that weight.
     - The estimate after a session is the estimate as suggestions read it then: the last 12
       sessions up to and including it (Automatic progressive overload).
     - A plain bodyweight exercise has no estimate: its trend is the top set's reps.
     - A weighted bodyweight exercise's estimate counts the bodyweight (below), and reads
       "Estimate with bodyweight"; its top sets read as logged and stay off the chart.
   - **Weekly sets per muscle**: weeks run Monday to Sunday; finished sessions count. Every
     logged work set counts 1 for each primary muscle of its exercise and 0.5 for each secondary
     muscle (Volume, above: drops, myo mini-sets and partials still add no sets). Halves read
     `7.5`, whole sets `7`.

**Navigation**: a Material 3 navigation bar at the bottom holds the five destinations, Today,
Program, Coach, History and Settings, in that order (the owner's call, 2026-10-17: Settings is a
tab, not a gear on every top bar). Back on Program, Coach, History or Settings goes to Today. The
bar is hidden while the session screen is up (a running session left with Back shows it again) and
on screens opened from a destination (a trend, a past session, a Change under review, Gyms and
equipment, the exercise library).

**Sheets** are Material 3 modal bottom sheets, all through one shared sheet: a drag handle, a scrim,
Back and a drag down from anywhere on the sheet (scrolling content first scrolls to its top) close
it with the system's animation. A sheet opens fully expanded at its final height and keeps that
height while it moves. It stops below the status bar; a list longer than that scrolls inside it.

Settings is the navigation bar's fifth tab, a destination with no back arrow and no gear on any top
bar; its sub-screens (Gyms and equipment, the exercise library) keep their back arrows to Settings.
It holds gyms and their equipment, exercise library, the Claude key and this month's cost
(Coaching → With a Claude key), export backup, restore from a backup, starting weights from the
old app, and "Check for updates" (Distribution). Debug builds add Scripted coach.

- **Effort is RIR everywhere**, both targets and logging.
  RIR has a small fixed colour scale (3+ calm, 1–2 warm, 0 hot) that is not part of the
  theme. **RIR has one shape everywhere** (the owner's call, 2026-10-16): the RIR chip below, in set
  text and on its own alike. The logging screen's RIR column, its RIR pad and the exercise sheet's
  set table draw the same chips as Today, Program and History, never rings or dots.
- **RIR is a coloured chip** wherever the app shows set text, in place of `@ N`: the number in a
  small rounded chip of the RIR scale, sitting on the text's baseline and wrapping with it
  (`1 × 4–6 [1] · 2 × 6–8 [1]`, written `[1]` in this spec). A **target** (planned sets, a block's
  rule, a suggestion's "target") is an outlined chip; a **logged** set (History, past and finished
  sessions, Last time in a why-line, a logged row on the logging screen) is a filled chip, a badge.
  An outline is the scale's colour, darkened in light mode (`#D5544A`, `#AA741E`, `#4A8983`) so it
  keeps 3:1 against every light surface in daylight, with an onSurface number; dark mode keeps the
  base colours (`#D9645B`, `#E0A84E`, `#6BAFA9`). A fill is a solid badge, the same in light and dark
  (the owner's call, 2026-10-16): a white number on the outline's light-mode shade darkened, hue
  kept, just far enough for 4.5:1 (hot `#D14237`, warm `#9D6B1C`, calm `#447E79`). It keeps 3:1 on every light surface; in
  dark mode the white number carries it, since the fill's edge drops to about 2.8:1 on the lightest
  container. The chip is 1.25 em tall and centred on the line, its number 0.8 em semibold with
  0.45 em either side; corners are 4 dp, so a chip reads as a tag, never as the violet accent or a
  button. A set's parts and its chip are joined by no-break spaces, so a line wraps only between
  sets, never inside one or before its chip (`45 × 10 + 3 + 3 + 3 [2]`). TalkBack reads
  each one once, as "1 RIR"; the number is always there, so colour is never the only sign. The chip
  is for set text the app writes: Claude's own words (a Change's or workout's summary) show as
  Claude wrote them.
  - **On its own**, outside set text, the chip takes the same shape and colours at a fixed size: in
    the set table's RIR column (a target outlined, the RIR chosen or logged a badge), on the exercise
    sheet's set table and the set sheet's RIR stepper (targets outlined) and on the RIR pad. A pad
    key is an outlined chip, and the
    chosen one is the badge it will become, so a set looks the same when you tap its RIR as it does
    in History later. `4` reads `4+` there, as the pad offers it. Each pad key keeps a 48 dp tap
    target, and logging a set takes no more taps than before.
  - **In a column**, a list of sets right-aligns its set text so the chips line up, for scanning
    "where did I hit 0": Program's done days and an exercise's progress page.
  - **Text that leaves the app keeps `@ N`:** the Claude prompts (`@ N RIR` in the legend), backups,
    the rest notification (system UI, `25 kg × 8–10 @ 1`) and anything copied as plain text. The
    domain keeps writing `@ N` as the one source; only the UI draws it as a chip.
- **Scheduling**: the program runs as a sequence (next day is whatever comes next, whenever you
  train). Optional training days (Training days, below) set the default gym, let Today notice
  time off and place the program's coming days on Today's calendar. No reminders or notifications.
- **Training days** (the weekday plan) are yours, kept once for the app, not per gym (ADR 0004):
  the weekdays you train on, each with an optional gym (Mon = Kalliokoski, Wed = hotel).
  - **Program shows them** as a row under its header card: `3 days a week · Mon Wed Fri` with a
    `calendar_today` symbol, or `Set your training days` with none. The row opens the **Training
    days** sheet. **Today's calendar** has a pencil in its header that opens the same sheet, also
    with no program (Screens → Today).
  - **The sheet** says what the days do (`Today plans your next sessions on these days and counts
    time off from them`; with two or more gyms, `… and counts time off from them. A session
    started on one picks its gym.`),
    then has the seven weekdays as round toggles labelled `Mo Tu We Th Fr Sa Su` (two letters,
    so no two read the same; TalkBack says the full name), Monday first, and, with two or more
    gyms, a row for each day that's on with its gym in a menu (the gyms, then **Any gym** for none
    named). The row shows the short day name (`Mon` … `Sun`) on one line, never wrapped, and the gym
    name on one line with an ellipsis when it doesn't fit, the drop-down arrow always showing
    (TalkBack reads the full day and gym name; the menu lists full gym names). With several gyms, a
    day turned on names no gym until one is picked. With one gym there's no menu: a day turned on is
    at that gym, so it keeps it when a second gym is added.
    Each change applies at once; **Done** closes it.
  - With no training days, everything behaves as with no weekday plan: the usual gym comes from
    history, and time off counts the days since the last session.
  - Deleting a gym keeps its days as training days naming no gym.
  - **From before (database v23):** every gym's weekdays became training days, each with that
    gym; when two gyms shared a day, the first in the gym list (by name) kept it. A gym already
    deleted (archived) gave none, as its days no longer counted. An older backup restores the same
    way.
- **Supersets** log alternately (A1 → A2 → A1…) with no timer between partners and full rest after
  the round. A superset started out of order keeps its partner until the round is done.
  - Closing a partner's set (logged, its last drop or mini-set, or **Finish set**) brings up the other
    partner's set of the same round at once, with no rest; closing the round starts the rest of the
    set that closed it. With uneven set counts, the extra sets run as straight sets.
  - "Until the round is done" means until all of the superset's planned sets are logged. The pair
    stays on screen as one, then the session goes back to the first group with sets left.
  - The superset line above the name reads `2a with Seated Leg Curl`, using the day's slot labels
    (as Today numbers them), then the partner's last set this session with its RIR chip.
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
    takes the left reps, ✓, then the right, under an `L / R` head, and logs as `30 × 10/9 [1]`.
    The next set stays split. Progression reads the weaker side.
- **Past sessions are editable.** The app never recalculates a past session from new rules, but you
  can correct one; progression then reruns from the corrected data.
  - A past session keeps the plan it was done under: its targets are the Changes in force when it
    finished, so a Change accepted or reverted later doesn't touch them.
  - History's Sessions tab lists finished sessions (Screens → History). A session opens on its
    summary (date, start time, duration, gym), and an exercise on its table, where logged rows are
    corrected exactly as during a session. Back returns to History.
  - Corrections change values only: a past session's exercises and sets can't be added, removed or
    swapped. Last time in a past session's table is the session before it.
  - Suggestions read the corrected sets from then on, including a session running at the time:
    its suggestion and last time are worked out again, with the set to do's prefill if untouched.
- **English only.**
- **Exercise library** is editable on the phone (Settings → Exercise library): browse and search,
  add, edit (name, equipment type, two dumbbells, movement pattern, grip demand, primary and
  secondary muscles, video link, note) and hide. Design: `docs/design/exercise-library.md`.
  - **Names carry the implement** ("DB Bench Press", "Smith Machine Squat"), so a name alone tells
    exercises apart wherever it shows. A name is modifiers, then the implement, then the movement:
    "1-Arm Lean-In DB Lateral Raise", "Incline Smith Machine Press", "Seated Machine Dip". The
    implement words are Barbell, DB, Machine, Cable, Smith Machine, EZ-Bar, Trap Bar, Safety Bar,
    Plate and Weighted (bodyweight has none).
    - **Short names** (the owner's call, 2026-10-21): a machine station named for itself ("Pec Deck",
      "Leg Press", "Lat Pulldown", "Triceps Pressdown") and a named lift ("Deadlift", "Front Squat",
      "Pendlay Row", "JM Press") leave the implement out. `LibrarySeedTest` lists them, so a new short
      name is a decision, not a slip.
    - **One side at a time** starts with `1-Arm` or `1-Leg` (lunges, split squats and step-ups say
      it already), since that is how the app knows to split a set into left and right.
  - **Grip demand**: high when the hands hold the load (free-weight presses, curls, rows, pulls and
    hinges with a bar or dumbbells), low for handles that guide it (Smith, machines, cables), none
    when the hands do nothing. Free-weight presses are high because the hands hold and balance the
    whole load, which is what sore hands notice.
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
  - **No video:** when the exercise has no video link, the sheet shows **Search technique videos**
    in its place, styled the same (a tonal button with the Material Symbols `search` icon). It opens
    `https://www.youtube.com/results?search_query=<exercise name> technique` (URL-encoded) in the
    same Custom Tab as the videos. The app calls no API and stores nothing; adding a link in the
    library editor brings back the video button.
- **Two kinds of notes, neither written by hand:** the exercise's own technique cue comes with the
  library (seeded from the legacy notes, cleaned of program-specific advice like "dropset: …");
  program-specific cues come from the program file's slot `note` (the legacy programs had 281). The
  prescription sheet shows both. Library notes stay editable on the phone.
- **History** (v1): session list, per-exercise top set and e1RM trend, weekly sets per muscle.
- **Lift goals**: a target for one exercise's estimate (e.g. barbell bench 100 kg, optional date).
  The exercise's prescription sheet and History show the current estimate against the goal, with
  its trend. A goal never changes the program or the suggestions.
  - One goal an exercise: a target in kg (the estimate's unit, so a weighted bodyweight goal
    counts the body too) and an optional date. It is set, changed or removed from the flag on
    the exercise's trend in History (History → Progress → the exercise), in a field that opens in
    place, with the date from the system date picker.
  - Only exercises with one estimate in kg take a goal: free weights and weighted bodyweight.
    Machines and cables have an estimate per machine (ADR 0002) and plain bodyweight exercises
    track reps, so they have no flag.
  - The estimate is the one after the latest finished session, as History shows it. It reads `95 kg
    of 100 kg`, then `5 kg to go · by Thu 31 Dec` (worked out from the figure as shown), `Goal
    reached` (without the date) once the estimate is at or past the goal, or `No estimate yet`. The
    trend chart draws the goal as a 1 dp line labelled `Goal 100`, kept on the chart's scale; the
    prescription sheet shows the same lines and a small estimate trend with the goal line under the
    prescription. Reaching a goal changes nothing else.
  - Nothing in progression reads goals: the suggestions are the same with or without one (tested
    over the progression engine's inputs).
- **Records**: a logged work set that beats the exercise's earlier sets (the machine's, for
  machines and cables, ADR 0002) is a record, of one of two kinds:
  - an **estimate record**: its e1RM, counted as the estimate counts it (sets of 12 reps or fewer,
    with an RIR, both dumbbells, bodyweight included), is above the best of every earlier set.
    The RIR counts, so the same weight and reps further from failure is a record (90 × 5 @ 2 after
    90 × 5 @ 1). A set of more than 12 reps makes no estimate record, though it can make a rep
    record (owner, 2026-10-24);
  - a **rep record**: more full reps than any earlier set at the same weight, which was lifted
    before. A set that is both reads as an estimate record.
  - Warm-ups, deload sets, drops, mini-sets and partial reps never make a record, and an
    exercise's first session has nothing to beat.
  - **What it beats:** every earlier set of the exercise, in every session before this one (not
    only the twelve progression reads) and earlier in this session, so set 3 can beat set 1. Deload
    sets still count as sets to beat (owner, 2026-10-24). A weight kept past a weight check makes no record until a set
    near it confirms it. A rep record's weight is the weight as logged: one dumbbell's, or the load
    on the belt; a split set counts its weaker side, as progression does. A machine or cable logged
    in a session with no gym has no machine to keep it apart, so those sessions share one history,
    as last time does (owner, 2026-10-24). The estimate is counted
    with the Epley constant learned from the exercise's earlier sessions, so a later set never
    takes an earlier set's mark away.
  - **As the set logs**, its row takes a small mark, `Estimate record` or `Rep record`, kept on
    the set wherever it shows (the session, Session done, History). The mark is a trophy, the
    Material Symbol `trophy`, read by TalkBack as its name, in ink (never the accent, which marks
    what you act on): a badge on the lower corner of the set's index circle in the set table, a
    disc of full ink (onSurface) ringed in the row's surface with the trophy cut out of it, so it
    reads in dark mode too; and after the set's RIR chip in set text, in the text's colour (owner,
    2026-10-24). **Day done** and Session done list the session's records, a row each: the trophy, the exercise, and under it the set and the
    record (`92.5 × 5 [1] · Estimate record`), read by TalkBack as one line
    (`Barbell bench press · 92.5 × 5 [1] · Estimate record`). On Session done the Records card
    comes before the exercise summaries (owner, 2026-10-24).
  - Records are worked out from the logged sets, never stored: correcting a set moves them.
- **Bodyweight**: a dated log with a small trend line on History; the latest value is used for
  weighted bodyweight exercises.
  - One value a day: logging again the same day replaces it. It is typed on the system keyboard
    in a field that opens in place (`Bodyweight today`, 20–400 kg), since it happens away from a
    set. The log (History → Progress → Bodyweight) lists every day; a day picked on the chart or
    in the list can be deleted.
  - A weighted bodyweight exercise logs the load on the belt or held. Its estimate is Epley on
    bodyweight plus that load: today's suggestion uses the latest value, and each past session the
    value logged on or before its date (else the first value logged). Suggestions stay in the
    load on the belt, on its ladder. With no bodyweight logged the load alone is used.
  - A plain bodyweight exercise logs 0 kg: its pad starts on the reps with the weight at 0. A
    weighted bodyweight set takes 0 kg on the belt (bodyweight alone). Loads are never negative:
    an assisted exercise (a machine's counterweight) is logged as a machine exercise, with the
    counterweight as its weight.
- **Dumbbells are logged per dumbbell** (the number printed on it). Volume and e1RM count both
  dumbbells for two-dumbbell exercises.
  - The library marks a dumbbell exercise done with one in each hand as **two dumbbells** (the
    shipped ones: presses, rows, curls, raises, flyes, lunges, RDLs and shrugs with two; a goblet
    squat, a 1-arm row, an overhead triceps extension, a French press, a preacher curl and a
    kickback hold one). The editor shows it as a switch for dumbbell exercises.
  - The estimate (History, goals) counts both: 30 kg dumbbells × 8 @ 1 is 60 kg × 8 @ 1. Weights,
    suggestions and why-lines stay per dumbbell, as logged, and come out the same since the
    estimate scales with the load. Volume is counted in sets, so it doesn't change.
- **Backups**: manual JSON export and restore, plus Android Auto Backup (`docs/backups.md`).
  - A backup holds everything the app stores: programs and their Changes, workouts, every session (the
    running one with its pad draft and rest timer), gyms, equipment, ladders, remembered machines
    and missing equipment, the library with its edits, starting weights, goals, bodyweight and
    settings ("Check for updates", the dismissed update), and the Claude cost by month. Every table
    goes in row for row except the last update check, a cache of GitHub's answer, and the Claude
    key, which never leaves the phone (sealed with Keystore in `no_backup`, not the database) (a test fails on a table that is neither in
    the backup nor excluded with a reason).
  - **Settings → Export backup** saves one JSON file through the system's save dialog
    (`hyperlog-backup-2026-10-13.json`), then reads `Saved Tue 13 Oct, 14:05`, and keeps saying
    so afterwards. The time is kept in the `setting` table and recorded once the file is saved, so
    a backup never holds its own export: it holds the export before it, or none. Restoring a
    backup therefore shows the export made before that backup (or the row's plain line). The file names its
    format, its own version and the database version (`"format": "hyperlog-backup", "version": 1,
    "database": 17`).
  - **Settings → Restore from a backup** picks a file, then asks "Replace everything on this
    phone?" with the backup's date. Restoring replaces all the data on the phone, a running session
    included, all or nothing, and the app starts again on Today or the restored session. There is
    no merging: a restore is for a new phone or going back.
  - A file that can't be restored says why under the row (not a Hyperlog backup, made by a newer
    Hyperlog, too old, damaged) and changes nothing. Backups from older database versions keep
    restoring: a schema change brings an upgrade step for them.
  - **Auto Backup** takes the database to the Google account about daily and restores it when
    Hyperlog is installed on a phone signed in to the account. A muted line under the settings
    rows says so. The manual file is the dependable route between phones.

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

Operations: `swap`, `remove`, `add`, `adjust_sets`, `adjust_load` (percent), `adjust_rir`,
`remove_technique`, `skip_day`, `deload_week`, `shift_program`, `repeat_week`. Scope is a date range (temporary), `rest_of_program`
or `rest_of_block`.

**Ending a Change.** A Change may carry `replaces`: a list of ids of Changes in force (the prompt
lists each with its id). Accepting it ends those first, by the same rules as Revert, then applies
its own operations. With `replaces`, `operations` may be empty, for a reply that only ends them
("Hands feel fine now"); otherwise it ends them and applies its own ("Worse: drop all grip work").

```json
{ "summary": "Hands feel fine: back to the plan.", "scope": "rest_of_program", "replaces": [12], "operations": [] }
```

**What each one does.** The program runs as a sequence, so a Change covers sessions by the date
they are trained on. Plan edits apply, in order, to every day trained on a date in scope;
position operations run once, when the Change is accepted.

| Operation | JSON | Effect |
| --- | --- | --- |
| `swap` | `slot`, `exercise` | The slot does the exercise instead (an alternative's own sets, else the slot's). The session reads "In place of …". |
| `remove` | `slot` | The slot is left out. |
| `add` | `day`, `exercise`, `sets` (program-file set lines: `count`, `reps`, `rir`, `rest`), `after`? | The exercise is added to that day after slot `after` (else at the end), labelled `+`. |
| `adjust_sets` | `slot`, `sets` (1–10) | The slot does that many sets: its first ones, or its last set repeated without its technique. |
| `adjust_load` | `slot`?, `percent` (−50…50) | Suggested weights move by the percent, to the nearest rung, on top of progression; the why-line ends "· 10% lighter for a Change". No slot: every slot. |
| `adjust_rir` | `slot`?, `by` (−5…5) | Target RIR moves by `by` (+1 = one more in reserve), 0–10. No slot: every slot. |
| `remove_technique` | `slot`?, `technique`? (`dropset`, `myo_reps`, `lengthened_partials`, `iso_hold`, `failure`) | The slot's sets lose that intensity technique (any technique when left out). No slot: every slot. |
| `skip_day` | — | The next day is passed over. |
| `deload_week` | `week`? | That program week (the one you're in when left out) is a deload: half its sets per slot (rounded up), RIR + 2, no techniques, and it never lowers the estimate. |
| `shift_program` | `weeks` (≠ 0) | The rest of the plan starts that many weeks later: the position goes back that many weeks (to week 1 at most), so they are trained again. Negative moves it earlier, skipping weeks. |
| `repeat_week` | — | The week of the last day trained runs again from its first day. |
| (`replaces`, on the Change) | ids | The Changes in force with those ids end on accept, as Revert ends them, before the operations apply. Not an operation, so it carries no `reason`. |

Every operation may carry a `reason`. Slots are named by id; a block reusing an id means the same
place, so a `rest_of_program` edit follows the slot into later blocks, and `rest_of_block` stops at
the end of the block the Change was accepted in. A date range covers its first and last day;
`rest_of_…` starts the day it is accepted.

- **Validation** (pasted or built) names every problem by path, like program import:
  `operations[0].slot: unknown slot "rows"`. Slots, days and exercises must be known, values in
  range, the summary not blank, and `to` not before `from`. Every `replaces` id must be a Change in
  force (`replaces[0]: no Change in force has id 9`), and `operations` may be empty only with
  `replaces`.
- **The diff** shows the summary and the scope, then where the next day moves and how many days
  are left (before → after), then the first day of each kind in the block it edits (each day
  of each block once), slot by slot: swapped (`In place of …`), removed (struck through), added
  (`+`), sets before → after, and a moved load. A date range looks one week of days ahead per week
  of dates, and never more program days than dates (a one-day range shows one day); `rest_of_block` to the end of the block; `rest_of_program` to the end. Nothing changes
  until **Accept**; **Reject**, × and Back drop it. A Change with `replaces` lists what it ends
  above the diff ("Ends: Hands need a week off grip work"), and its diff compares the plan as it
  is with the plan once those have ended and it applies; one Accept does both.
- **History.** Accepted Changes are kept with the day they were accepted, the position they moved
  from and to, and the block. The Program screen lists them on its Changes page (the **Changes ·
  1 in force** row), newest first, each with **Revert** (asking first). Reverting stops its plan edits and takes its move of the position back; days
  trained since still count (the position moves back by the same number of days). Reverted ones
  stay in the list, struck through. A Change ended by another's `replaces` stays struck through
  too, reading "Replaced 18 Oct". One that only ended others has no Revert: it has nothing of its
  own, and isn't in force. A row's summary shows two lines; tapping a row in force opens it as
  tapping its card on Coach does (read-only, "Change in force", with Revert), and tapping any other
  shows its summary in full and its status in a dialog, with Close. A running session follows an
  accept or revert at once.
- **Reorders stay in the session.** No operation moves a slot; the order is the program's.

### Check-in: a wizard with Claude (ADR 0005)

Every situation goes through Claude, which reads the program, the week you're in and your sessions
(ADR 0003). A **check-in** is a wizard: one question a screen, a few big answers to tap, and
"Type my own" under them. With a **Claude key** (Settings) the app asks Claude itself; without one
it hands off to the Claude app (**Ask Claude**, below). Nothing changes until you accept.

**The first step** (`ui/coach/CheckInScreen.kt`; design: `docs/design/coach.md`) asks "What's going
on?" with tiles: **Something hurts**, **Short on time**, **Different gym or none**, **Tired or
slept badly**, **Change my program**, **Build me a workout**, **New program** and **Something
else**. **Change my program** is for edits meant to last (swap an exercise, add or drop one, more
or fewer sets): Claude asks which exercise and why, and proposes a `rest_of_program` Change, which
follows the slot into later blocks and stays revertible on the Changes page (owner, 2026-10-24). A
tile is your first answer: tapping it starts the check-in, and Claude's first question is its
first call. Above the tiles, while Changes are in force, one line, `2 changes in force`, opens them
(below): the line reads the count over the newest one's summary. With no program running, the tiles that change the plan still go to Claude, which can
only answer in words or with a workout or a program; Build me a workout and New program stay. A
pasted Change then reads "No program is running, so there’s nothing for a Change to change." (one
problem, with the copy button, for Claude). Once a program is finished a Change can still repeat a
week or shift the program.

**Claude's steps.** After the first tile Claude asks what the situation needs, one question a
screen, through its `ask` tool: the question, 2–4 answers, whether you may type your own, and
whether the step offers **Attach a spreadsheet, PDF or photo** (New program, below).
"Which hand, and does pressing hurt or only gripping?" for pain; the gym, with your gyms and
**Elsewhere** as answers, only when the equipment matters (with more than 3 gyms, the 3 likeliest,
today's planned gym and those in recent sessions, and Elsewhere, with Type my own for the rest:
owner, 2026-10-24; `ask`'s description and the context say so); effort, time and focus only for a
workout, with the three least-trained muscle groups this week carrying their counts (`Back · 4
sets`). Effort is Easy (3 RIR or more, no techniques), Normal (1–2 RIR) or Hard (0–1 RIR,
techniques allowed); time is the whole session with warm-ups and rest, 45 min to 1 h 30; focus is
any of the seven muscle groups (`MuscleGroup`), or none for a balanced full-body day. **Marks
count groups** (owner, 2026-10-16): weekly sets per group are counted as History counts muscles
(`History.weeklyGroupSets`), and among the groups trained in the last 4 weeks the three with the
fewest sets this week carry the count, fewest first, ties in group order. It asks about four questions at most. While a call runs
the screen says what Claude is doing, from its reply's stream (#75): "Reading your spreadsheet…",
"Reading your PDF…" or "Looking at your photo…" while it reads a file just attached, then
"Writing the program…", "Writing the Change…", "Writing the workout…" or "Writing the answer…" once
it writes one, else "Thinking…". **Back** goes one step back, to change that answer, and Claude asks
again from there. There is no step counter, since Claude doesn't know in advance how many it needs.

- **How the steps read** (#73): each answer is a full-width row to tap; **Type my own** opens a
  field with Send. An answer that names a muscle group carries that group's mark (`Back · 4
  sets`): Claude is told the marked groups and names them as History does, and the app adds the
  count. An answer naming one of your gyms goes back to Claude with that gym's equipment, and is
  where a workout from the check-in starts; **Elsewhere** goes back with "I'm not at any of my
  gyms. Ask what equipment is there if it matters, and plan only with that.", and a workout from
  it keeps no gym.
- **Back** from Thinking stops the call and shows the step just answered; from a failed call
  too. Back from the first of Claude's steps is the tiles, and from the tiles it leaves Coach.
  **Reject** (or Back) on a review is Back with **Type my own** open, even where Claude didn't offer
  it, so you can say what was wrong (your own words, if you typed that answer; owner, 2026-10-24).
  × on a review drops the check-in, as on every step.
- **The navigation bar hides** while a check-in is under way, as it does in a session (owner,
  2026-10-24); system Back and × lead out. It shows on the tiles again.
- **The review doesn't show your answers** (owner, 2026-10-24): Back does, and the check-in shows
  under the Change once accepted.
- **One reply at a time.** Claude may make one call a reply; if it makes several, a proposal wins
  over a question, and the others go back as "Not shown: Hyperlog uses one question or reply at a
  time." An `ask` with fewer than 2 or more than 4 answers, or a blank question, is a reply with
  problems like any other.
- **New program** goes to Claude too, and ends in a program through `propose_program` (New
  program, below).

**The end** is one of:
- **An answer in words**, when the plan can stand ("Ramp-in already eases effort. Use straps and
  train as planned."), with **Done**, and **Tell me more** to go on. Tell me more adds a step:
  Claude's next words go in a card under the earlier ones, which stay (quieter), never in their
  place (owner, 2026-10-24).
- **A review**: a Change (**Accept**), a workout (**Add to Today**) or a program (**Start
  program**), each through its tool. The review is the one each already has.
- **×**, at any step, which drops the check-in.

A check-in is kept, step by step, while you're on other tabs or away from the app, until it ends.
It is a file in `no_backup` (`check_in.json`), saved before each call, so one left while thinking
asks again on coming back; a backup has none. Done, ×, Accept, Add to Today and Start program end it.

**Calls go on while you're away** (owner, 2026-10-24, after a program's reply died when the app
went to the background: Android cuts a backgrounded app's network). Every call runs in a foreground
service (`dataSync`, `coach/CheckInService.kt`) with an ongoing, silent notification, "Claude is
building your program…" for New program and "Claude is thinking…" otherwise, that opens the
check-in; it goes when the reply comes. The runner saves the reply as always, so nothing is lost.
A reply that lands while the app is away posts one notification that opens the check-in at that
step: "Your program is ready", "Claude asked a question", "Claude answered", "Claude's reply is
ready" (a Change or workout) or "Claude couldn't finish" (a failure or problems); the next call
takes it down; opening it shows Coach even over a running session, which goes on as Leave leaves it. Notifications are asked for at the first check-in only, if not granted (Android 13+);
without them the call still runs in the service and the check-in works the same.

**Replies are checked before you see them.** A Change, workout or program from a tool goes
through the same validators as a paste or an import. One with problems goes back to Claude as the
tool's error, paths and all, and Claude tries again, at most twice; then the problems show as
Paste reply shows them, with **Try again** (a new call) and ×. Try again sends those problems
once more and counts the two tries afresh.

**Changes in force.** The `2 changes in force` line opens a page with one card for each accepted
Change that covers today or a later day, newest first: its summary and how long it runs (`Until
Tue 27 Oct`, `Until today`, `Thu 15 Oct – Thu 22 Oct` for one that starts later, `Rest of Build`
while the block it was accepted in runs, `Rest of the program`), with **Revert** (asking first,
as on Program) and **How's it going?**. That one starts a check-in about the Change, sending the
check-in that made it (its questions and your answers) with it. Tapping a card opens what that
Change does from today on, read-only and headed "Change in force": the days it still covers, slot
by slot, with every other Change applied before and after, and the move of the position it made.
Its one button is **Revert** (asking first); × and Back close it. A finished program has none.
Under its summary it shows **The check-in** that made it: the opening, then each of Claude's
questions over your answer (Claude's words and Tell me more stay out). Program → Changes shows the same in a row's dialog. A Change pasted, or proposed from a
session, has none. It is kept with the Change (`plan_change.check_in`, JSON) and goes into backups.

**A check-in's review opens on Coach** (tester, #75): one read back as the app starts waits there
rather than covering Today, and opens when Coach does.

**After an accept.** Accept goes to Today. With a workout first on Today, Today scrolls the program
day the Change edited into view, its heading at the top. Start program goes to Today on the new
program's first day. Add to Today while a session runs lands on Today, which still shows the session, with "Workout
added. It’s on Today once this session ends."

**Time off on Today.** Days off are counted from your training days (the weekday plan): whole
days from the first planned day after the last finished session that went untrained (today isn't
missed yet), so the plan's own rest days are not time off. Training Tuesdays only, two weeks since
the last session is 7 days off. With no weekday plan it counts the days since the last finished
session. After more than 7 days off, Today shows `11 days since your last session` (the words
always count from the last session) with **Ease back in**, until a Change or a workout is
accepted that day. Ease back in starts a check-in whose first answer is "I've had 11 days off.
Ease me back in." The context holds the count either way.

### What Claude is told

The same context goes to the API and into Ask Claude's prompt (`coach/AskClaude.kt`), built once:

- The check-in so far: the tile, Claude's questions and your answers, quoted; the instruction to
  ask only what the situation needs, then answer in words when the plan can stand or reply with
  one Change, workout or program, using only the listed ids; today's date and the days off (the
  last finished session's date and how long ago, and with a weekday plan the days off as Today
  counts them); the gym answered, with its equipment by kind and each item's weights, its
  dumbbells and plates, and the exercises missing there (or, with Elsewhere, that the equipment is
  in the words); the program's title, the next day (week, day id, block) and days left; the
  Changes in force, newest first, each with its id, summary, scope, the day it was accepted, its
  operations as JSON and its check-in; every block with its days and slots (slot id, exercise id
  and name, sets as planned in the block's first week or the current one, alternatives, note); the
  finished sessions of the last 28 days (date, day, gym, each exercise's sets as `80 × 5 [1]`); the
  weekly sets per muscle for this week and the 4 before it; every visible library exercise, and any
  hidden one the program uses (id, name, equipment, movement pattern, grip, primary muscles); and
  the formats.
- **The formats are generated** from the code that reads them: operation names and fields from the
  serializer, scopes from the scope serializer, ranges from the validators, the program format from
  the program parser, an example Change built for the running program (a swap to the next day's
  first alternative, +1 RIR, for a week) and an example workout built from the library, both of
  which the validators accept. With a Change in force, two more examples use its id: one that only
  ends it, and one that replaces it with the example's operations. A new operation or field can't
  ship without its description. Through the API the formats are the tools' input schemas (`ask`,
  `propose_change`, `propose_workout`, `propose_program`); in Ask Claude's prompt they are text and
  examples. Only `ask` is `strict` (owner's phone, 2026-10-24: the API refused the strict grammar of
  all three as "too large"); a proposal's input is checked by the validators, and the self-fix loop
  sends its problems back. `CoachToolsTest` pins which tools are strict. `propose_program` can never
  be strict: a program's week overrides are maps keyed by week, which a strict schema can't hold.
- **The schemas are lean** (#73; `coach/CoachTools.kt`). A strict request may carry 24 optional and 16
  union-typed parameters in all, and no ranges; the Change's and the workout's schemas keep within
  those too, so either could go strict again, and they leave optional only what the
  serializer does and Claude can't fill in for it (`slot` on the load, RIR and technique
  operations, `technique`, `week`, `after`, `replaces`, a slot's `note`, a set line's
  `technique`); every operation's `reason` and every set line's `count`, `rir` and `rest` are
  required, in a program's set lines too. `reps` is a number or a range string. `ask`'s `attach`
  is required, false but for a new program. The validators check the ranges and ids. A test
  holds the limits and checks that the replies the validators take fit the schemas.
- **The check-in's context** (`coach/CheckInContext.kt`) opens with how a check-in goes (the
  `ask` tool, at most four questions, the gym only when the equipment matters with your gyms and
  Elsewhere as answers, the workout's effort, time and focus with the marked groups, Change my
  program's `rest_of_program`, and that a reply's problems come back to fix), then the situation
  Ask Claude's prompt carries, built by the same code (`AskClaude.situation`). A gym's equipment
  comes with its answer, not in the context. **How's it going?** opens with the Change, its id and
  the day it was accepted, then "The check-in that made it:" and that check-in.
- **Caching.** Through the API the stable part (the formats, the library, the program) comes first
  and is cached, so the questions after the first read it from the cache.

### With a Claude key

- **Settings → Claude key**: in a Coach group under the setup rows, a row that opens a field in
  its card for an Anthropic API key (masked, with Cancel and Save), checked once with a free call
  (the models list) and saved encrypted with Android Keystore, in a file in `no_backup`. While
  the check runs the field reads "Checking the key…"; a refused key reads "Claude doesn't accept
  this key." and no connection "Couldn't reach Claude. Try again.", and neither is saved; a key
  Keystore can't seal reads "Couldn't save the key on this phone. Try again." A key
  that has hit its spend limit is still a good key and is saved. Then the row reads `Key ending
  …a1b2` with **Remove**, which forgets it at once. The key is never shown again, never in a
  backup, a log or Auto Backup; a restore keeps the phone's key, or none.
- **The model** is Claude Opus 5.5 (`claude-opus-5-5`), with adaptive thinking at medium effort
  and streaming, the tools above (`ask` strict) with `tool_choice` auto, and the server-side refusal
  fallback (`fallbacks: "default"`), through the official Anthropic Java SDK. The context is the
  system prompt, cached, and the conversation is cached up to its last turn; Claude's replies go
  back unchanged, thinking included. One client
  (`coach/ClaudeClient.kt`) makes every call; no other code opens a connection but the update check.
- **Cost.** Each reply's usage (input, cache writes and reads, output) is added up by month at the
  model's prices, a refused reply's too, and Settings reads `This month: ≈ $2.40` under the key.
  Opus 5.5 is $4 in, $5 a cache write (five-minute), $0.20 a cache read and $20 out per million
  tokens; a reply that fell back is priced hop by hop at each model's own prices. The month's sum
  is kept in `setting` as `coach_cost_<yyyy-MM>` in millionths of a dollar, and goes into backups. The hard limit is the
  monthly spend limit set in the Anthropic Console; the app has no cap of its own.
- **Timeouts** (`ClaudeClient.TIMEOUT`): a connection within 30 s, packets at most 5 minutes apart
  (a reply's stream sends pings while Claude thinks), and the whole reply within 45 minutes, room
  for thinking and a 64k-token program. The SDK's own default caps a streamed reply at 10 minutes.
- **Errors.** No connection, a timeout or an API error shows "Couldn't reach Claude." with **Try
  again** on that step; nothing is lost. A reply whose stream breaks off once it began (the
  connection dropped, or stalled past the timeout) reads "Claude's reply was cut off." ("The
  connection dropped while Claude was answering. Nothing you answered is lost: Try again asks once
  more.") with **Try again**. A key the API refuses says "Claude doesn't accept this key.
  Check it in Settings." (the whole sentence stays in the title: owner, 2026-10-24) and offers Ask
  Claude for this check-in. A spend limit reached says so, and
  offers Ask Claude. A refusal (after the fallback) reads "Claude declined to answer this one." with
  Try again and Ask Claude. Any other 4xx (not the key, a spend limit, a timeout or a rate limit) is
  the request itself, never "Couldn't reach Claude": it reads "Claude couldn't take this request."
  with Try again and Ask Claude, and the API's message goes to Logcat (`Rejected, HTTP 400 …`).
  **Ask Claude** there shares Ask Claude's prompt with the check-in so far
  as the words (the gym answered, or Elsewhere, with it), and **Paste reply** becomes the step's
  filled button; a Change pasted back and accepted keeps the check-in, which then ends. Every failure is warned in Logcat (tag `ClaudeClient`) with the HTTP status, the
  API's error type and its message, then each cause under it by class and message (`Reply failed:
  Dropped, AnthropicIoException: Stream failed ← SocketTimeoutException: timeout`), and a key being checked with its length and whether it holds
  only key characters (`ClaudeKeySetup`); never the key or the headers that carry it.
- **Testable without a key.** The client is behind one interface (`CoachModel`). Tests use a fake
  that plays scripted turns: questions, each kind of reply, a reply that fails validation and its
  fix, a refusal and each error. **Scripted coach** (Settings, debug builds only) plays canned
  check-ins for every tile with no key and no network, so the wizard can be tried on the phone and
  by the tester. It is a switch in Settings' Coach group, kept in `setting` as `scripted_coach`
  (a release build has no switch and never reads it), and its code is in the debug source set
  (`app/src/debug/.../coach/ScriptedCoach.kt`; the release build's `BuildCoach` has none). It
  builds its Changes, workouts and programs from the program and library, so they pass the
  validators: Something hurts and Short on time end in a Change, Change my program asks which and
  why and proposes a wrong slot first, to show the fix, then a `rest_of_program` swap; Different gym
  and Build me a workout end in a workout; New program offers Attach a spreadsheet, PDF or photo, asks the days
  a week, and proposes the program format's example with one swap (Seal Row) and one new exercise
  (Barbell Drag Curl); Tired and Something else end in words. One live test calls the API and runs only when `ANTHROPIC_API_KEY` is set.
- **One host.** A test fails if code outside the client and the update check opens a connection,
  or if the client calls any host but `api.anthropic.com`.

### Without a key: Ask Claude

A phone without a key (and without the scripted coach) opens the check-in on the same tiles and
then hands off (`ui/coach/AskClaudeScreen.kt`). The old words-first Coach screen, with its gym row,
starters and Build me a workout's options, is gone: Claude asks for the gym and the workout's shape in
the chat, as the wizard would.

After a tile, one screen takes your words: a text field (3 lines at least) with the tile's sentence in
it to edit. The sentences are `Something hurts: `, `I'm short on time today: I have 45 minutes.`,
`No gym today, only dumbbells at home.` (Different gym or none), `I slept badly last night.` (Tired
or slept badly), `I want to change my program: `, `Build me a workout for today: `, `I want a new
program: ` and, for Something else, nothing. The bar names the tile; the tab bar is hidden, as in
the wizard, and Back returns to the tiles. **Ask Claude** opens the Android share sheet with the
prompt (What Claude is told) for the Claude app. Ask Claude is enabled with a tile even when the
words are cleared, because the prompt quotes the tile's own sentence (`Something hurts.`) before the
words, so Claude knows what the check-in is about; when the words already begin with that sentence
(`Something hurts: left elbow.`), it isn't quoted twice. You talk it through there as long as you like;
Claude may answer in words alone, and the check-in doesn't treat the ask as unfinished. Otherwise
Claude ends with one fenced JSON block holding a Change, a workout or a program; copy it and come
back.

- **Other ways in.** Ease back in on Today and **How's it going?** on a Change in force open the same
  screen with their words in it (`I've had 11 days off. Ease me back in.`; the Change, its id and the
  check-in that made it), titled "Easing back in" and "How's it going?".
- **No gym row.** The prompt carries no gym's equipment: say where you train in your words, and
  Claude asks in the chat. A workout pasted back starts at the usual gym, as the start sheet picks it.
- **An accepted Change keeps the check-in:** the tile's sentence, with your words as its one answer
  ("Tell me more."), shown with the Change as a wizard's is.
- **Paste reply** is then the filled button. It reads the clipboard: the first fenced block holding
  JSON, else the text from the first `{` to the last `}`, so Claude's whole reply can be copied. An
  object with `workout` is a workout, one with `program` a program (read as program import reads
  it, each problem's path starting `program.`, with its swaps and new exercises), anything else a
  Change. A valid Change, workout or program opens its review. Otherwise the problems show as program import shows them (path over
  message, "Not a reply Hyperlog can use", "N problems. Nothing changed. Copy them into your Claude
  chat, then paste the new reply.") with a copy button to hand them back to Claude, and nothing is
  applied. A problem with the copy, not the reply, shows one message and no copy button: a reply
  with no JSON says "No Change, workout or program found in what you copied. Copy Claude's whole
  reply, with its JSON block, then paste again.", and one whose JSON opens more braces than it
  closes says "Claude's reply is cut off before its JSON ends. Copy the whole reply, then paste
  again."
- **Reply found.** Coming back to Hyperlog after asking (it went to the background, and its window
  has focus again, which Android requires for reading the clipboard), the app reads the clipboard
  once. If it holds a Change, workout or program that validates, a card reads "Claude's reply is ready" with a filled
  **Review**. Anything else is ignored quietly. Nothing opens by itself. Android shows its own toast
  when an app reads the clipboard.

### New program

**New program** builds a whole program in a check-in, from your answers or from a file (#75).

- **Attach a spreadsheet, PDF or photo** is a row under the answers of a step whose `ask` sets
  `attach`; Claude offers it when you may have the program as a file: its spreadsheet (a Nippard
  one), a program PDF, a printed sheet, a whiteboard, a screenshot. The system file picker opens for
  an .xlsx, a .csv, a PDF or an image, and the file is your answer: it goes to Claude in the
  answer's tool result ("I attached Essentials.xlsx.") and the check-in keeps the file's name as the
  answer. Claude reads the blocks, days, exercises, sets, rep ranges, RIR and techniques, and asks
  only what the file doesn't settle ("The sheet has 5 days. You train 3: which to merge?"). Without
  a file it builds from your answers.
- **Spreadsheets** (owner, 2026-10-24: Nippard's programs come as Excel files). The API takes no
  .xlsx, so the phone reads it (`coach/Spreadsheet.kt`, the zip's XML, no library) into one CSV
  text a sheet, in the workbook's order under `## Sheet: <name>`, sent as a plain-text document
  titled with the file's name. Cells read as Excel shows them: shared and rich text, numbers without
  a float's noise (`1.0000000000000002` reads 1), percentages (`75%`), and dates in their own format,
  so a range Excel saved as a date reads as typed (`2-3` and `8-9` in Nippard's sheets, format
  `m-d`). A merged cell's value fills down its rows (a day's name labels each exercise) but not
  across (a title is said once). Empty rows and columns, and empty sheets, are left out. A .csv
  goes as its text. An old .xls, or a workbook with nothing in it, doesn't read.
- **Sizes.** A PDF goes as it is (base64, as a document), up to 23 MB: the API takes a 32 MB
  request, and base64 makes a file a third larger. A larger one is turned down before any call,
  reading "This PDF is too big for Claude. Attach the program’s spreadsheet or photos of its pages
  instead." as the Attach row's second line, its paperclip turned into a warning, and the check-in
  goes on. A photo goes as a JPEG turned upright, its long edge at most 2576 px (what Opus 5.5
  reads). A file that isn't one of these, or doesn't read, says "Couldn’t read that file. Attach a
  spreadsheet (.xlsx or .csv), a PDF or a photo." the same way. While it is read, "Reading the
  file…", the paperclip turns into a loading indicator and the row takes no taps.
- **Not stored.** The file lives in memory only, while the app runs: the check-in's file in
  `no_backup` keeps its name and type, never its bytes. A check-in read back after the app was
  closed shows where it was (a review it reached still opens), but a call that would carry the file
  again (an answer, Back and a new answer, Try again) goes back to the step that took it instead,
  with no call, to attach it again: its note reads "Attach Nippard PPL.pdf again: Hyperlog doesn’t
  keep files." Dropping the turns after it keeps what Claude was sent before unchanged.
- **The reply** is `propose_program`'s input, or pasted: `{ "program": <a program file
  (docs/program-format.md)>, "swaps": [...], "newExercises": [...] }`. The program validates as
  import does, its problems' paths under `program.`, and goes through the self-fix loop.
- **Exercises the library doesn't have.** Claude uses the closest library exercise and lists each
  swap as `{ "from": its name in the source, "to": a library id }`; the review lists them under
  "Swapped for your library" ("Seal Row → 1-Arm DB Kroc Row"). Only when nothing comes close does
  the reply add an exercise to `newExercises`: `id`, `name`, `equipment`, `pattern`, `grip`,
  `muscles` (one primary at least) and `secondary`, by the library editor's rules and its keys. A
  new exercise must be new to the library by id and by name (`newExercises[1].name: the library
  already has "Seated Cable Row" (seated_cable_row): use it`), added once, and used by the program.
  The review lists them under "New in your library" (name, then equipment and primary muscles), and
  only Start program saves them, under their own ids, with the program.
- **The review** (`ui/coach/ProgramReview.kt`), titled "New program": the title, then the author,
  weeks and days a week on one line ("Jeff Nippard · 12 weeks · 5 days a week"), the file it was
  built from (`From Nippard PPL.pdf`), and the description in a card; the swaps; the new exercises; then the program as Program shows it (the
  blocks, the block's card with its weeks, and the picked week's days), from week 1.
  **Start program** asks first when a program runs, as import does ("Replace <title>? You're in
  week 6 of Hyperlog Full Body; the new program starts at week 1, day 1 instead. Past sessions are
  kept."), then runs it from week 1, day 1, and goes to Today; the program before stays as the
  record of its sessions. If the library gained one of its new exercises since the review, nothing
  starts: Program shows the problem as a rejected import (`newExercises[0].id: the library already
  has "barbell_drag_curl"`). **Reject** and Back step back in the check-in (Type my own open), × drops
  it, as on every review.
- **Without a key**, Ask Claude's prompt carries the program format (`coach/ProgramFormat.kt`),
  built from the parser's fields and with an example the paste reads; you attach the file in the
  Claude app, and a pasted program opens the same review.

Later, if syncing is ever wanted for its own sake: a custom MCP connector would let the Claude app
read the plan and write a reply directly (ADR 0001).

### Workout

A day Claude builds for one date, outside the program, for a hotel gym, half an hour, or no
program at all.

```json
{
  "workout": {
    "name": "Hotel dumbbells, 45 min",
    "summary": "Upper body with dumbbells only. Back first, since it's behind this week.",
    "groups": [
      { "slots": [ {
        "id": "row",
        "exercise": "chest_supported_db_row",
        "sets": [ { "count": 3, "reps": "8-12", "rir": 1, "rest": 120 } ]
      } ] }
    ]
  }
}
```

- `groups` are the program file's groups, slots and set lines (`docs/program-format.md`), without
  week overrides (`weeks` on a set line is an unknown field). Slot ids are unique within the day. `name` and `summary` must not be blank. The
  check-in validates it as import does, paths and all (`workout.groups[0].slots[0].exercise:
  unknown exercise "bb_row"`).
- **The review** shows the name, the summary, where it's for (`At Itis`, or `Elsewhere`; nothing
  with no gyms set up) and the exercises as Today lists a day, each with its rest after its sets
  (`3 × 8–12 [1] · 2:00 rest`, `1:00–2:00` when its sets rest differently). **Add to Today**
  keeps it; Reject, × and Back drop it.
- **On Today, it's today's main Start** (tester's report, 2026-10-16). It comes first as a card:
  "Workout" over its name, summary and gym line on the secondaryContainer head (decided, owner
  2026-10-16), with ⋮, then its exercises with their rest. The floating button reads **Start
  workout** and starts it, through the start sheet. The program day follows as the second card,
  its head holding week and block over the day's name with a tonal **Start**, so its heading and
  its Start show together and the floating button never covers them; then its exercises and the
  Ad-hoc session row. With no program the card is followed by the Ad-hoc row. While another
  session runs, Today shows that session's day and not the workout. It holds for the day it was
  added and is gone the next day if it wasn't started. Adding another replaces it, asking first
  ("Replace today's workout?"). ⋮ → Remove drops it at once, with a snackbar, "Workout removed"
  with **Undo**, for the long duration (a lifter looks up before tapping); Undo puts it back as
  it was (its day and the gym answered in the check-in).
- **Elsewhere means no gym.** A workout asked for with Elsewhere keeps no gym. The start sheet for
  a workout names it under the title and offers **Elsewhere** ("No gym: the usual dumbbells and
  plates") after the gyms, picked for such a workout; the gym answered in the check-in otherwise (a gym
  since deleted reads as Elsewhere). Elsewhere starts the session without a gym, by the rules of a
  session with no gym: the usual dumbbell rack and plates, machines asked about without a gym, no
  missing-equipment swaps, and no gym on the session.
- **The session** logs like a program day: targets, suggestions, warm-ups and techniques. It
  doesn't move the position. Its sets count toward estimates and weekly sets like any session's.
  History lists it under its name. While it runs, Today shows it as the day, headed "Workout",
  with Resume session. Once it has started the card is gone; a session discarded with nothing
  logged puts it back for that day. Finishing a session with nothing logged says so: "Nothing is
  logged yet, so the session is dropped and the workout goes back on Today." (for any other
  session, "…dropped, and nothing goes to History."), never a Session done it won't show. Its edits stay in the session: there's no "Keep edits for the
  block", since a workout has no program to keep them in.
- Workouts are kept with the sessions (the `workout` table, the session's `workout_id`) and go
  into backups.

## Fresh start, with starting weights from the old app

No history is imported from the legacy app: no sessions, programs, TM history or bodyweight.
History and trends start empty. The legacy backup (the old app's full-backup JSON export) stays as
an archive.

The one thing taken from it is **starting weights**. For each exercise, the most recent work sets
in the backup become a one-off starting point, used only until the exercise has its own history:

- **Free weights** (barbell, dumbbells, bodyweight): the first suggestion comes from the old data,
  labelled "from old app: 60 kg × 8 [2], 12 Sep". The first real session replaces it.
- **Machines and cables**: the old data doesn't say which gym or machine, so the old weight is shown
  as a hint only while you pick a weight on the new machine's ladder.
- Old dumbbell weights logged as both dumbbells together are halved. These five were totals:
  seated DB shoulder press, DB bench press, DB incline press (`db_incline_press`, `incline_db_press`),
  chest-supported DB rows (`chest_supported_db_row`, `incline_chest_supported_db_row`), DB Romanian
  deadlift. All other dumbbell weights are per dumbbell.

How the import works:

- **Settings → Starting weights from the old app** picks a backup file (format 7.0, `type:
  full_backup`, `version: "7.x"`; another version is refused as not a backup, since its data sits
  elsewhere). Importing again replaces the earlier import; a file that isn't a backup says so and
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
  set, the label shows both: "From old app: 60 kg × 12 [2], 12 Sep → 65 kg × 8". An ad-hoc
  exercise is prefilled with the old set itself, on the ladder.
- The label sits in the suggestion line, tertiary like an estimate (check it on the first set).
  A machine's hint ("From old app, another machine" and the set) is in the exercise sheet only,
  not on the logging screen, and the weight stays empty. Both go once the exercise (for machines, on that machine) has a logged set.

The **exercise library** is seeded from the legacy library (189 exercises, 150 with technique
videos), cleaned up: duplicates merged (e.g. `db_incline_press` / `incline_db_press`), equipment types
fixed, and a grip-demand tag (`high` / `low` / `none`) added, which the coach's prompt shares. The result
ships as `app/src/main/assets/exercises.json`: 176 exercises, 140 with videos (merged duplicates
shared links), with a `merged` table of legacy id → kept id for anything that still carries an old
id (the starting-weights import). Three cable exercises were added after the legacy library (v0.1.1):
`cable_chest_press`, `low_to_high_cable_press` and `cable_rope_hammer_curl`. Every launch syncs the
seed, so an update brings new exercises to an install already in use.

Jeff Nippard's tier lists (chest, back, shoulders, biceps, triceps, quads, glutes; 2024–2025), his
"7 Amazing Exercises No One Does" and "The Only 25 Exercises You Ever Need" are covered for every
exercise ranked S, A or B: 37 were added, each with a video link to its spot in his ranking video,
bringing the library to 213 exercises, 177 with videos. Left out: variations of one exercise the
library already holds (rope or bar pressdown, face-away or facing Bayesian curl, barbell or EZ-bar
skull crusher, flat-bench curl, diamond push-up), techniques that aren't exercises (21s, cheat and
strict curls) and banded push-ups, since a band isn't equipment the app can load. Twelve names were
changed to the naming rule, ids kept: `db_incline_press` is "Incline DB Press", `weighted_crunch`
"Plate Crunch", `machine_hip_abduction` "Machine Hip Abduction".

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
10. Records.
11. Claude key, the API client and the month's cost.
12. Check-in with a Claude key, and the scripted coach.
13. Check-in without a key (Ask Claude in the wizard).
14. New program in a check-in.
