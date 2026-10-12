# MacroFactor Workouts: in-workout set logging UI

Researched 2026-10-12. Sources are the official help center (help.macrofactorapp.com, Workouts collection), MacroFactor blog posts, the iOS App Store listing, and third-party reviews. I looked at the help-center screenshots directly (iOS, light mode, app ~v1.1–1.2).

Labels:
- **[doc]** means the help article text says it, with URL.
- **[shot]** means I saw it in an official help-center screenshot on the cited page.
- **[inferred]** means my reading. The docs do not say it.

Reddit (r/MacroFactor) could not be fetched: both the search tool and direct requests are blocked. User sentiment below therefore comes from App Store reviews and blog reviews only.

Key pages:
- How to Log a Workout: https://help.macrofactorapp.com/en/articles/310-how-to-log-a-workout
- Logging Full or Partial Reps: https://help.macrofactorapp.com/en/articles/316-logging-full-or-partial-reps
- Changing RIR During an Active Workout: https://help.macrofactorapp.com/en/articles/314-changing-rir-during-an-active-workout
- Logging Different Types of Sets: https://help.macrofactorapp.com/en/articles/317-logging-different-types-of-sets
- Using the Plate Calculator: https://help.macrofactorapp.com/en/articles/313-using-the-plate-calculator
- Changing Rest Time During a Workout: https://help.macrofactorapp.com/en/articles/320-changing-rest-time-during-a-workout
- Accessing Exercise Options During a Workout: https://help.macrofactorapp.com/en/articles/333-accessing-exercise-options-during-a-workout
- Supersetting Exercises: https://help.macrofactorapp.com/en/articles/321-supersetting-exercises
- Swap Exercises: https://help.macrofactorapp.com/en/articles/332-swap-exercises
- Understanding and Using Smart Progressions: https://help.macrofactorapp.com/en/articles/305-understanding-and-using-smart-progressions
- What Does the Smart Progression Wand Mean?: https://help.macrofactorapp.com/en/articles/391-what-does-the-smart-progression-wand-mean
- Configuring Warm-Ups: https://help.macrofactorapp.com/en/articles/304-configuring-warm-ups

---

## 1. Screen layout: one exercise at a time, as a table of set rows

The screen shows one exercise at a time. That exercise's sets are a vertical table, one row per set. Other exercises appear only as thumbnails in a strip near the top. From top to bottom the screen has five parts.

1. **Timer bar** [doc 310, shot]
   - Hamburger (≡) at top-left opens a bottom sheet with Pause, Minimize, Complete, Workout settings, Gym settings and Discard.
   - The workout duration timer (`0:03:46`) sits to its right.
   - The rest timer (`⏱ 0:00`) is at top-right, followed by a grey progress-bar placeholder.
2. **Exercise thumbnail strip** [doc 321, shot]
   - One square photo tile per exercise, plus a `+` tile at the end to add an exercise.
   - Each tile has a thin bar under it. The current exercise's bar is black.
   - Completed exercises get a dark overlay and a white check.
   - You tap a tile to jump to that exercise and drag tiles to reorder. This strip is the whole "what's coming up" view.
3. **Exercise header** [doc 310, shot]
   - Large bold exercise name, wrapping to 2 lines if needed.
   - Grey subtitle `Set 1 of 3` that tracks the current set.
   - At right, a pill-shaped ring gauge (`165 lb / +28 lb (17%)`) for bodyweight contribution. It is green and tappable (doc 315).
4. **Action bar** [doc 333, shot]
   - A horizontally scrolling row of grey pill chips with icons.
   - Order: Smart Progression wand, Info, Warm Up, Targets, Swap, Note, Superset, Equipment, L/R (only on relevant exercises), More.
   - An active toggle such as L/R turns into a black-filled pill.
5. **Set table** [doc 310, shot]
   - Columns are `Set | Auto ⇄ | Lb | Reps | ☐`.
   - **Set**: a grey circular badge with the set number or type letter. Tapping it opens the set-type sheet.
   - **Auto**: the target in two lines, e.g. `245 lb x 8–16` over a grey `1 RIR`. The doc calls this column "Previous Performance". The `⇄` in the header suggests it toggles between targets and previous performance [inferred].
   - **Lb** and **Reps**: rounded light-grey input boxes.
   - **☐**: a square checkbox per row, with a select-all checkbox in the header.
   - A grey circular `+` below the table adds a set.

Current-set highlighting:
- Rows have no strong highlight [shot]. The current set is shown by the `Set N of M` subtitle and, during entry, by a thick black outline on the focused input box.
- The doc says "The first exercise in your workout is highlighted automatically."

Auto-advance [doc 310]:
- When every set of an exercise is checked, the app moves on to the next exercise on its own.
- This can be switched off in Feature Settings > Workouts.
- A setting can hide completed warm-up sets behind the Warm Up chip [doc 304, April 2026 release notes https://macrofactor.com/mm-april-2026/].

## 2. Set entry: custom in-app keypad, RIR chips, F/P toggle

Tapping Lb or Reps does **not** open the system keyboard. A custom numeric keypad slides up from the bottom and fills about the lower 40% of the screen [shot 310, 313]. The table stays visible above it, and the focused cell gets a thick black outline with its value selected.

**Weight keypad** [shot 313]:
- A 3×4 grid of digits, `.`, `0` and backspace.
- A right-hand column holding:
  - a keyboard-dismiss key
  - a plate/barbell icon key, which opens the plate calculator options (bar weight, base weight)
  - a split `−` / `+` stepper
  - a large black `→` key that moves to the next field (Reps)
- **Plate calculator strip** (above the keypad): a tiny side-view barbell drawing with color-coded plates, a legend (`■ 1 x 25`, `■ 1 x 2.5`), and a sentence underneath: "27.5 lb on both sides + 45 lb bar weight = 100 lb". It updates live as you type. You type the total weight and the app works out the plates; it never asks which plates you loaded [doc 313].
- Settings live behind the plate icon (bar or base weight) and a settings gear (plate increments, per-equipment lb/kg) [doc 313].
- Pin-loaded machines use stack ranges configured in the gym profile. There are three kinds of micro-adjustment: dial, selectorized pin and external plates. A stack range can be filled in from a photo with "Scan with AI" [doc 403]. No separate stack picker appeared in the screenshots [inferred: the stack ranges limit recommendations and the −/+ steps rather than opening a picker].

**Reps keypad** [shot 310 image 5; doc 314, 316]:
- The same digit grid.
- **An RIR chip row sits directly above the keypad.** The circles are color-coded: `0` and `1` red, `2` and `3` amber/yellow, `4` and `5` green, `6+` blue, and a grey `?`. The selected one gets a black ring. "Tap the Reps field … select the RIR value" [doc 314]. RIR runs from 0 to 6+ [doc 385].
- The right-hand column holds:
  - keyboard-dismiss
  - a key labelled `RIR` (purpose not documented)
  - a segmented **`F | P`** toggle, with F shown black when selected
  - a large dark **✓** key
- The doc puts the F/P toggle "below the RIR selector near the checkmark". Tapping `P` turns on partial-rep logging and you then "enter the number of full reps and partial reps". Tapping `F` switches back. ✓ "save[s] the set and move[s] on to the next set or exercise" [doc 316].
- The ✓ key on the keypad and the row checkbox both complete the set. To undo, uncheck the box [doc 310].

**RIR display in the table**:
- Each Reps box shows a small filled colored circle with the RIR number, overlapping its bottom-right corner, using the same red/amber/green/blue scale [shot 314, 317].
- RIR can be changed after the set is checked [doc 314].

**Prefill / targets**:
- Targets are always visible as text in the Auto column (`55 lb x 7–9 / 2 RIR`) [shot].
- The input boxes are **pre-filled with real values**, not placeholder text. The "Initial log fill" setting decides whether the values come from "Smart Progression values" or "Previous values" [doc 305 + settings screenshot].
- In the screenshots the reps are filled and the weight is sometimes empty. That appears to be the state before Smart Progression has data [inferred].
- "Apply in session" lets Smart Progression **rewrite later sets' fields mid-workout** based on what you just logged [doc 305]. A reviewer singled out this behavior: "Upcoming sets would adjust automatically based on my input" (https://feastgood.com/marofactor-workouts-review/).
- **To log a set as planned**, you just tick the row checkbox [doc 310; inferred: no field editing is needed when the prefill matches].
- The April 2026 release added a setting to make set-to-set adjustments reps-first instead of weight-first.

**Smart Progression wand** (first chip) [doc 391]:
- The wand icon carries a small colored dot: blue means information, yellow means an adjustment worth reviewing, red means the equipment isn't available in the gym profile.
- Tapping it opens an explanation sheet. Some warnings have a "Fix" action that offers alternative weight/rep combinations [doc 305].

## 3. Set types W / D / M / F

- You pick the type by tapping the set-number badge, which opens a bottom sheet titled "Set Type" [doc 317, shot]. The options are Standard, Warm-Up, Drop, Myo and Failure, each with a one-line description and a radio button on the right.
- The **set badge letter replaces the number** with W, D, M or F. Working-set numbering continues, so a D first set is followed by 2, 3, 4 [shot].
- **Warm-up (W)**:
  - The Auto column reads "No Target" in grey [shot].
  - Smart warm-ups are added automatically from a percentage-based scheme by default [doc 304].
  - Swipe a warm-up row left to delete it [doc 304].
- **Drop (D)**:
  - It is one set holding several weight/rep entries.
  - The badge stretches into a **tall vertical grey pill covering all the sub-rows**, with a small `+` at the bottom of the pill to add another drop [shot 317].
  - The sub-row targets show the drop weight, e.g. `25 lb / 0 RIR`.
- **Myo (M)**:
  - Same tall-pill grouping.
  - The continuation rows have their **weight field greyed out and locked** to the activation weight (`55` in light grey), so you enter reps only [shot 317]. The doc says "Each entry represents another continuation, and all entries are grouped together."
- **Failure (F)**: an ordinary row whose RIR is forced to 0, shown as a red `0` badge [doc 317, shot].
- **L/R** (unilateral): every set becomes two stacked rows marked with small outlined `L` and `R` square tags where the checkbox was [doc 378, shot].

## 4. Rest timer

- It is in the top-right of the timer bar and **starts automatically when a set is completed** [doc 310, 320].
- Tapping it opens a bottom sheet titled "Rest Timer" [shot 310]. The sheet contains:
  - −10 s and +10 s round buttons on either side of a large `⏱ 0:00`
  - a "Restart With New Timer" row of horizontally scrolling pill presets: 0:15, 0:30, 0:45, 1:00, 1:15 and so on, in 15 s steps up to 3:00
  - a settings (sliders) icon
- Picking a preset closes the sheet [doc 320].
- Defaults depend on movement type: lower-body compounds get 3:00 and upper-body isolations 1:30. You can override them per exercise [doc 303 https://help.macrofactorapp.com/en/articles/303-change-default-rest-timers].
- **Notifications and lock screen**:
  - Notifications get through silent mode and music [Feb 2026 https://macrofactor.com/mm-feb-2026/].
  - An iOS Live Activity shows the active set and rest timer on the lock screen [May 2026 https://macrofactor.com/mm-may-2026/].
  - A reviewer says it "vibrates" but they never heard the sound (feastgood).
- **Complaint**: editing a logged set's weight or reps restarts the rest timer (App Store review, https://apps.apple.com/app/id6737156524).

## 5. Reordering, swapping, supersets

- **Reorder** by dragging thumbnails in the top strip [doc 321].
- **Swap**:
  - Open it from the Swap chip in the action bar [doc 332].
  - A list of "Smart Substitutions" chosen by the app appears first. You pick one and tap Replace or Swap Exercise. A quick-swap button also sits next to each suggestion.
  - "Find Other Replacements" opens a full search.
  - Progression for the new exercise starts from the sets you log after the swap [doc 383].
- Exercises can be skipped without being removed from the program [April 2026 release notes].
- **Superset**:
  - Tap the Superset chip and choose to pair with the previous or next exercise. The two must be adjacent, so reorder first if needed. "Detach from superset" undoes it [doc 321].
  - Paired exercises "alternate until both are completed" [doc 333]. They show grouped, with "round indicators" beneath [doc 321].
  - A reviewer found superset rest timing unintuitive (feastgood).

## 6. Visual style

From the screenshots:
- **Flat and white.** There are no cards around the table. The background is white, the input boxes are light grey with rounded corners, and the chips are grey pills.
- **Black is the accent** for primary actions (✓, →, the selected F, the active L/R chip, the Done button). Color is kept for meaning: RIR dots (red, amber, green, blue), the green bodyweight ring, the colored wand dot, and the plate colors in the calculator strip.
- **Typography**: a geometric sans (looks like DM Sans) [inferred]. The exercise name is large and bold. The secondary text (`Set 1 of 3`, `2 RIR`) is small and grey. Numbers in the boxes are medium weight and centered.
- **Moderate density.** Each row is about 48–56 dp tall. One exercise with three sets plus `+` takes about 40% of the screen, and the rest is empty whitespace. Even with L/R doubling, a four-set exercise fits on one screen.
- **Bottom sheets** handle every secondary flow (set type, rest timer, workout menu, swap), with an `×` at top-left and the title centered.
- Light, dark and system themes are available [doc 357].
- Overall it is calm and restrained, but the screen does show a lot: the action bar alone has about 10 chips. A competitor's review describes it as exposing "an enormous amount of useful information" (https://alphaprogression.com/en/blog/best-macrofactor-workouts-alternatives, biased source).

## 7. What users praise and complain about

Praise:
- "easy, intuitive and FAST". Logging weight, reps and RIR "only took seconds after each set", and the rest timer starts on its own (feastgood).
- Upcoming sets update live from what you log (feastgood).
- "lightning fast", "never lag", the rest timer is "incredibly responsive" (https://brobible.com/gear/article/macrofactor-workouts-lives-up-to-they-hype/; affiliate article).
- "sleek ui", "Easy to add/remove sets or exercises during a session" (App Store review).

Complaints:
- Editing a logged set resets the rest timer (App Store).
- "Way too many clicks to just see what I did on an exercise last session." "Generally over engineered, unintuitive." (App Store)
- Lost sessions when a workout got stuck and couldn't be completed (App Store).
- Recommends weights the user doesn't own, and odd numbers like 88.5 lb that come from fractional plates in the gym profile [doc 390; App Store].
- Smart Progression jumps felt "nonsensical", e.g. +21% on squat, and depend on honest RIR ("garbage in / garbage out") (feastgood).
- The jargon assumes the user already knows lifting terms (App Store). Set-type and RIR options can overwhelm casual users (Alpha Progression, competitor).

---

## Patterns worth borrowing for a calm, one-handed Android logger

1. **Custom bottom keypad instead of the IME.** Keep the digits, a −/+ stepper, and one big primary key (→ for weight, ✓ for reps) in the bottom-right, where the thumb rests. Every per-set decision is on the keypad, so the user never reaches up the screen.
2. **Put RIR on the reps keypad as a single row of colored chips.** One tap, no extra screen. The same color then shows as a small dot on the reps cell, so effort is readable at a glance.
3. **Put the F/P partial toggle on the keypad** as a two-way segment next to ✓. It costs nothing when off, and only adds a second number when on.
4. **Show targets as text in their own column and fill the fields with real values.** "As planned" is then a single tick, and the target stays visible after the value is edited.
5. **Show plate math live above the keypad**: a sentence plus a tiny barbell drawing, with no separate screen.
6. **Group drop and myo sets under one tall badge** with an inline `+`, and lock the weight on myo continuations.
7. **Make the rest-timer adjust sheet one tap from the timer**: ±10 s plus a row of preset chips. Start the timer automatically on ✓.
8. **Avoid** MacroFactor's mistakes:
   - the 10-chip action bar crowding the top of the screen
   - resetting the rest timer when a past set is edited
   - looking up last session's numbers taking several taps (show them in the row instead)
   - the top thumbnail strip, which is out of thumb reach on a large phone (consider a bottom "next up" peek instead)
