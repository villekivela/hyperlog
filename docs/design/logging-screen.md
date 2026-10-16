# Logging screen: design note

The in-session screen where sets are logged. This note folds in the owner's decisions of
2026-10-15, taken after a real session ("the top part is really messy", "no indication what so
ever that it can be scrolled", warm-ups hidden); SPEC.md has the same rules. Screens:
`screens/light-*.png` and `screens/dark-*.png` are Robolectric renders of each state
(`session-active-drop` and `session-active-mini-set` show a set under way,
`session-mini-sets-planned` myo-reps about to start, `session-done-sets` finished sets with
drops, partials and myo-reps, each ending in its RIR chip; `exercise-sheet` the exercise sheet's
prescription with its outlined target chips). The renders
come from `SessionRenders` (skipped unless asked for): `RENDERS=<dir> ./gradlew
testDebugUnitTest --tests '*SessionRenders*'`, with `DARK=1` for dark; the whole bar and machine
from `WholeBarRenders` and Session done from `AppRenders`, the same way.
`phone-dark-rir-session.png`, `phone-dark-rir-sheet.png` and `phone-dark-rir-progress.png` are the
RIR chip checked on the phone (#46, dark mode).

## Principles

1. **One exercise, one flat table.** Its rows sit straight on the screen's surface, with no card
   behind them and no row filled. The row being done is marked by its outlined kg and reps cells
   (the focused one outlined in primary) and its index circle filled with primary.
2. **A calm top.** The name, the one clock and ⋮ on one row; a chip under it only when the slot
   is a swap or a superset; one short why-line. Everything longer lives in the exercise sheet.
3. **Every row ends in a check.** A tap on the check logs a set as shown; a set done as planned is
   one tap. The pad still logs too, and keeps every decision for a set in thumb reach.
4. **Planned things look planned, done things look done.** Prefills and targets are muted; done
   rows are full strength with a filled primary check. RIR is the one chip the whole app uses: an
   outlined chip is a target, a solid badge a result.
5. **The index column says what a set is.** `W` or the set number on a circle; a technique tints
   it and adds its symbol, and its drops or mini-sets hang off it in one tall pill.
6. **One list, visibly scrollable.** Warm-ups and sets scroll together, fading at an edge with
   more, and the active row is always in view. Symbols over words; every symbol a Material Symbol.

## Tokens

Spacing is on an 8 dp grid, with 4 dp as the half step.

| Token | Value | Use |
| --- | --- | --- |
| Margin | 24 dp | Left edge of all text: exercise name, chips, why-line, the index circles, sheets |
| Tap inset | 16 dp | Edge of a tappable row's ripple (content stays at 24 dp) |
| Set and warm-up row | 64 dp | Main rows |
| Sub-row | 48 dp | Drops, myo mini-sets, holds, Add set |
| Label row | 32 dp | Column heads |
| Columns | 40 · flex · 72 · 80 · 36 · 40 dp | Index, Last, kg, Reps, RIR, ✓ |
| Index circle | 32 dp; badge 16 dp on its top corner | `W` or the set number; a technique's symbol |
| Check | 28 dp circle (24 dp on sub-rows), 2 dp ring | Still to do (ring), done (filled) |
| Cell | 48 dp tall (40 on sub-rows), 12 dp radius, 4 dp inside its column, 1 dp outline (2 dp focused), 8 dp inner padding | Tappable value in the active row; a value or placeholder too wide for the padding (`10–12`) steps its size down to fit |
| Chip | 32 dp, 8 dp radius, 1 dp outline | Swap or superset, under the name |
| Next row | 48 dp, 16 dp radius | The pad's top row |
| Fade | 32 dp | Top or bottom edge of the list, when it has more that way |
| Key | 56 dp tall, 4 dp gaps, 12 dp radius | Pad keys, 4 equal columns |
| Row ripple | 24 dp radius | A tappable row's ripple (a done row, a warm-up) |
| RIR chip | 1.25 em tall, (0.9 + 0.5 per digit) em wide, 1.5 dp outline, 4 dp corners | `@ N` in set text: target outlined, logged a badge |
| RIR chip on its own | the same, its number 12 sp (14 sp on the pad), so 21 × 19 dp for one digit; `4+` for 4 and up | RIR column, exercise sheet, set sheet stepper and RIR pad |
| RIR pad key | 48 dp tap target, 40 dp pill | The chip on the strip; the chosen key's pill secondaryContainer |

Type (Material 3 scale with tabular figures in every style; Space Grotesk for headlineSmall and
titleLarge, the system font for the rest):

| Role | Style |
| --- | --- |
| Exercise name | headlineSmall, wrapping to two lines at full size before it ellipsizes |
| Chips, Next label | labelLarge |
| Why-line | bodyMedium, one line |
| Column heads | labelMedium |
| Values (kg, reps, placeholders) | titleLarge; focused cell SemiBold |
| Index (`W`, set number) | titleSmall; Bold in the active row |
| Last time | bodyMedium |
| Keys | 24 sp |

Colour comes from Hyperlog's Navy on grey theme, light and dark:

| Role | Colour |
| --- | --- |
| Screen and table | surface (the one screen that keeps it, so the pad stands off it); rows flat on it |
| Active row | no fill: its cells outlined in outline, the index circle primary |
| Focused cell | surfaceContainerLowest fill, 2 dp primary outline |
| Index circle | surfaceContainerHighest, onSurfaceVariant; primary with a bold onPrimary number in the active row, and on a set through its drops or mini-sets |
| Technique set | tertiaryContainer circle and tall pill, onTertiaryContainer; the badge tertiary with its symbol in onTertiary |
| Check | outlineVariant ring to do, primary ring on the row a tap logs, filled primary with an onPrimary `check` when done |
| Prefilled values and placeholders | outline |
| Done values | onSurface |
| Last time | onSurfaceVariant |
| Chip | outlineVariant outline, onSurfaceVariant text and symbol |
| Why-line | text onSurfaceVariant; its symbol in the fixed trend scale |
| Pad and Next row | surfaceContainer, drawn through the gesture area; "Next" in primary |
| Keys | surfaceContainerHighest |
| ✓ / → key | primary (dimmed to surfaceContainerHigh when there is nothing to log) |
| Weight warning | error (cell outline and value, strip text and `warning` symbol) |
| Rest pill | primaryContainer, time left laid over it in onPrimaryContainer at 16%; surfaceContainerHighest after rest |
| Breathing pill | tertiaryContainer |
| Day done strip | primaryContainer, rounded 28 dp at the top |
| Sheet groups | surfaceContainerHighest segments on the surfaceContainerLow sheet; the picked row secondaryContainer |

Two fixed scales sit outside the theme:

- **RIR:** one shape everywhere, the RIR chip, here as on Today, Program and History (#46, the
  owner's call). A target is an outline with an onSurface number: `#D9645B`, `#E0A84E`, `#6BAFA9`
  in dark mode, and in light mode `#D5544A`, `#AA741E` and `#4A8983`, the same hues darkened to at
  least 3:1 on every light surface so they read in daylight (`RirContrastTest`). A logged or chosen
  RIR is a solid badge, the same in both modes, a white number (semibold) on `#D14237`, `#9D6B1C`
  or `#447E79`, the outlines' light shades darkened just far enough for 4.5:1 (#43). So a set looks
  the same the moment you tap its RIR as it does in History. On the pad every key is an outlined
  chip and the chosen one turns into its badge.
- **Trend** (the why-line's symbol): up `#5FAE7A` (calm green), same weight `#9AA1A9`
  (neutral), down `#E0A84E` (amber; a deload or back-off is no failure, so never red).

## Layout

```
 status bar
 Barbell Bench Press              (⏱ 1:42)  ⋮   ← name opens the exercise sheet; pill the rest sheet
 ╭────────────────────────╮
 │ ⇄ for DB Bench Press   │                      ← outlined chip, only for a swap (or `1a + Leg Curl`)
 ╰────────────────────────╯
 ↗ 100 · hit the top of 4–6 last time            ← why-line: trend symbol in its fixed colour
          Last        kg    Reps   RIR
 (W)                  45      8           (✓)    ← warm-up ticked: full strength, filled check
 (W)                  60      5           (✓)
 (W)                  75      3            ○     ← still to do: muted values, an outlineVariant ring
                     ┌───┐  ┏━━━┓
 (①)     97.5 × 6   │100│  ┃ 6 ┃   ▐1▌     ◯     ← active: no fill; outlined cells, the focused one in
                     └───┘  ┗━━━┛                    primary; the circle filled primary; ◯ logs it as shown
 (2)     87.5 × 8     90    6–8    [1]      ○    ← upcoming: planned weight, target as placeholders
 (3)ᵈ                 25   8–10    [0]      ○    ← technique set: tinted circle, badge ᵈ on its corner,
 ┃                  17.5      6    [0]      ○       its drops in one tall pill down the column,
 +                  12.5      6    [0]      ○       `+` at its foot adds a drop
░░░░░░░░░░░░░░░░░░░░░░░ fade ░░░░░░░░░░░░░░░░░░   ← the list scrolls as one; fades where it has more
▓ Next  Seated Leg Curl                       ⌃ ▓  ← the pad's top row, on the pad's surface
▓   ─┤▐█▐█├──   ■ 2 × 20   ⟋ 20                 ▓  ← plates, while a barbell weight is on the pad
▓  [0]  [1]  ▐2▌  [3]  [4+]         [F| P ]     ▓  ← strip: RIR + full/partial (warm-up: its name)
▓ [ 1 ] [ 2 ] [ 3 ] [ ⌫ ]                       ▓
▓ [ 4 ] [ 5 ] [ 6 ] [ − ]                       ▓
▓ [ 7 ] [ 8 ] [ 9 ] [ + ]                       ▓
▓ [ ⌨ ] [ 0 ] [ . ] [ ✓ ]                       ▓
▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓ gesture area ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓
```

`[n]` is an outlined RIR chip (target, or a pad key), `▐n▌` a filled RIR chip (result or selection), `(W)` and `(1)` index
circles, `○` a check to do, `(✓)` a done check.

## Components

**Header.** The exercise name (headlineSmall, ellipsized; a tap opens the exercise sheet), the one
clock (rest, breathing or hold pill) and ⋮ on one row, at least 64 dp. Under it, only when they
apply, outlined chips: `swap_horiz` and `for <planned exercise>` for a swapped slot, `1a + <partner>`
for a superset (both when both apply). They say what the slot is, and a tap acts on it: the swap
chip opens Swap (while the exercise can still be swapped), the superset chip brings the partner on
screen (as the session list does: a set left open closes). Then the
**why-line**, one line: the trend symbol (`trending_up`, `trending_flat`, `trending_down`) in the
fixed trend scale and the short reason in onSurfaceVariant (`100 · hit the top of 4–6 last time`,
`57.5 · 2 short of 8–10 last time`, `70 · estimated, check on the first set`). A set that just
follows the one before has no line. The full sentence is in the exercise sheet, and so is a
machine's set from the old app ("From old app, another machine").

**Exercise sheet** gains, under the summary and the machine row: the full why, led by the same
trend symbol, and the old app's set for a machine with no history on it (`history` symbol).

**Column heads.** Above the list, so they stay when rows scroll: blank over the index, `Last`
(only once there is history), `kg`, `Reps` (`L / R` for a split set), `RIR`, blank over ✓.

**Index column.** A 32 dp circle: `W` for a warm-up, the set number for a work set
(surfaceContainerHighest, onSurfaceVariant). In the active row it is filled with primary, with a
bold onPrimary number, technique or not; a set keeps it primary while its drops, mini-sets or hold
are done. A set with a technique (drop set, myo-reps, lengthened partials, iso hold, to
failure) has a tertiaryContainer circle and the technique's Material Symbol as a 16 dp tertiary
badge on its top corner (`trending_down`, `repeat`, `unfold_less`, `pan_tool`,
`local_fire_department`), described by the technique's name, so it reads without colour. A set with
drops, mini-sets or a hold grows its circle into one tall pill down the column through those rows;
`+` at the pill's foot adds a drop to a drop set, before the set or
while it is under way; a myo pill has no `+`. A back-off set has no mark: its
lowered weight shows it. A tap on a circle of a set still to do opens its set sheet (technique,
target, Remove set, left and right).

**Set row, upcoming.** Planned weight in `outline`; the target as placeholders in the empty cells:
the rep range (`6–8`) in Reps and an outlined RIR chip for the target RIR. Last is last time's set, muted.
In a program session "last time" is the session progression judges (same slot and rep range,
else the latest), the one the why-line names, matched by position; ad hoc it's the latest. Ad hoc,
last time's remaining sets show as upcoming rows prefilled like the next set. The check is an
outlineVariant ring and takes no tap.

**Set row, active.** No fill: the row sits flat like the others, so a technique's tall pill is
never cut by a row shape. kg and reps are cells outlined in `outline`; the focused one is a well
(surfaceContainerLowest) with a 2 dp primary outline. The index circle is primary. An empty reps cell shows the range as a muted placeholder. RIR shows the selected RIR as
a badge, else the target's outlined chip. The check is a primary ring: a tap logs the set with the
values shown, prefilled or typed, wherever the pad is (as ✓ on the hidden pad does) and starts
rest. Until a weight and reps are typed the check is a grey ring and takes no tap (RIR is optional).

**Set row, done.** Values in onSurface, an RIR badge with the logged RIR, partials as
`12 + 6`, and a filled primary check. No row tint. A tap on the row loads it into the pad for a
correction. The check of the set logged last in the session takes it back: the set is deleted, its
values go back on the pad as the set to do, its exercise comes back on screen and rest stops.
Earlier rows are fixed by tapping the row. Not while a drop, mini-set or hold of it is logged, and
not within a second of logging it (a stutter).

**Set row, being corrected.** The active look with `edit` in the index column; the pad's hide key
becomes `close` (cancel). The set to do steps back to its upcoming look until the correction is
saved or cancelled.

**Warm-up row.** A full 64 dp row: `W` circle, no Last and no plate drawing, kg and reps muted
until ticked, onSurface after. Its check ticks it with the ones above it; on the last one ticked it
takes the tick back. A tap on the row puts it on the pad (below).

**Warm-up on the pad.** The row becomes the active row (a primary `W` circle, kg and reps cells), and the set to do
steps back (a tap on it brings the pad back to it). The pad opens on the weight: its strip shows
the ladder's rungs and its plate strip the warm-up's plates; → goes to the reps, where the strip
reads `stairs` and "Warm-up 2 of 4" (no RIR, no F/P); ✓ ("Tick warm-up") ticks it and puts the
next warm-up on the pad, or after the last one goes back to set 1. The hide key is `close` ("Back
to the set"). No rest, no log. A warm-up changed by hand is kept as typed for this session, and
only that one: the others go on following the ramp. The next session ramps again.

**Sub-row (drop, myo mini-set, hold).** 48 dp, inside its set's tall pill. Last is last time's drop
(`18 × 7`); kg the planned drop weight (the set's weight less the percent, rounded down to the
ladder, live while the set's weight is typed) or, for a mini-set or hold, the set's weight muted
and locked; reps prefilled from last time's drop (else 6), the mini-set's target or the hold's
seconds. A drop is to failure: an outlined 0 chip in RIR. The check is a 24 dp ring; on the one being done
a tap logs it as shown; done ones are filled, and a tap on the row corrects them. The one being
done is marked only by its outlined cells (a mini-set's or hold's weight is no cell); the set's
circle at the top of the pill stays primary for the whole group.

**The list.** Warm-ups, sets and sub-rows are one LazyColumn that scrolls as one. When warm-ups
appear it starts from the top; after that it scrolls as little as it takes to keep the active row
(the set to do, the drop or mini-set being done, the warm-up or row on the pad) whole. A 32 dp fade
at the top or bottom edge shows there is more that way.

**Pad.** Drawn like**Pad.** Drawn like the system number keyboard. The strip above the keys changes with the
field:
- Reps of a set: RIR 0–4+ as outlined chips, the chosen one its badge, then the F/P toggle.
- Weight: five rungs of the equipment's ladder around the current value.
- Weight that looks wrong: `warning` symbol, the reason ("10× last set", "3× lighter than the
  plan", "Not on this equipment") and three real rungs near the expected weight.
- Drop or mini-set: progress ("Drop 1 of 2", "Mini-set 2") and **Finish set**.

**Next row.** The pad's top row, on the pad's surface: "Next" in primary, the next exercise
outside the one on screen (or superset), `expand_less`; "Last exercise" alone when nothing follows.
Tap opens the session list. The next is the first exercise with planned sets left in the day's
order, so after a Start out of order it names the skipped one. While looking at a finished exercise
it reads "Now" and the current exercise, `chevron_right`, and goes back. With the pad hidden it is
the top row of the folded pad; with no pad (a finished exercise, an exercise done before the day
is) it stays at the bottom as the same row on a surfaceContainer strip.

**Hidden pad.** The pad folds down to the Next row and its bottom row's corners: keyboard (show
pad) at the left and ✓ at the right, still over the gesture area. ✓ there logs the set as shown.

Keys: digits, backspace, − and + (one ladder rung for weight, one rep for reps), hide keyboard,
`.` (weight only) and ✓. ✓ is one key, the same size as the others, filled primary. On the weight
field it shows → instead.

**Timer.** A 48 dp pill in the header, primaryContainer, so rest reads from across the gym. It
drains left to right during rest. After rest it counts
up (`+0:12`) in a muted container until the next set is logged. Before a myo mini-set it is a
15 s breathing countdown with an `air` symbol. Tapping it during rest opens the rest sheet:
−15, the time, +15, End rest.

**Sheets** (modal bottom sheets, surfaceContainerLow, 24 dp text edge). Their rows sit in
surfaceContainerHighest groups 12 dp in, with the picked or current row in secondaryContainer
(the session list's exercise on screen, the set sheet's technique, the start sheet's gym); the
prescription's plan, the machine row and the set sheet's target steppers are cards too.
- *Session list:* status symbol (done, now, upcoming), superset tag, name, a summary (logged sets
  as `25 × 10 [2]`, `5.7 × 12 + 6p [0]`, `45 × 10 + 3 + 3 [2]`: drops and mini-sets before the
  chip, which ends the set; never broken inside a set;
  or set count and rep range) with technique symbols. A tonal **Start** on exercises not yet
  done, **Open** on finished ones.
  In a program session (ticket #11) the tag is a 40 dp column after the status symbol holding every
  slot's label (`1`, `2a`, `2b`, `3`), so names align; superset labels are tertiary, straight ones
  onSurfaceVariant. A superset on screen shows both partners as now, with no Start on either. The
  done symbol means every planned set is logged: an exercise left halfway keeps the empty circle
  and shows its logged sets.
- *Prescription:* name, equipment, each set (reps, outlined RIR chip, technique with its numbers, e.g.
  "2 drops of 25%"), rest, why the weight was suggested, the technique note, and
  **Technique video**.
- *Rest:* see Timer.

**Session done.** On the screen's surfaceContainer: the shaped check (a primary-container
cookie), "Session done" in headlineMedium, every exercise as a segment with its summary, and
Resume session / **Done** on a bottom strip. Tapping one opens its table to look at or correct;
back returns to the summary.

**Day done.** When every planned set is logged, the pad gives way to a primaryContainer strip
rounded at the top: `check_circle`, "Day done", **Finish session**.

**Leave dialog.** Back on the session screen asks "Leave the session?", with "It keeps running.
Everything logged is saved." (Stay / Leave). Every tap is saved, so Leave goes to Today, which
offers **Resume session**. The app opens on a running session. Every dialog in the app orders its
buttons this way: the way out first, the action last, at the right.

**Ad-hoc session** (built in ticket #2). The same table, without a plan:
- No targets, so no placeholders or target chips. The "Last" head shows only once there is history.
- The sets are open-ended: logged rows, the active row, then last time's remaining sets as
  muted upcoming rows (prefilled the way the next set will be).
- With no history the first set starts on an empty weight. Empty cells show nothing rather than a
  dash, because a dash next to the − key reads as minus. The weight strip stays empty until a
  weight is typed, so the pad keeps its height.
- The empty session opens on the **Add exercise** list (headlineSmall title, 56 dp rows at the
  24 dp margin). The same list opens as a sheet from ⋮ or the session list.
- The exercise name opens the exercise sheet: equipment and muscles, the library note and the
  technique video (see `exercise-library.md`). The Add exercise list has a search field.
- ⋮ holds Add exercise and, below a divider, Finish session. Finish asks first: "Finish the
  session?", "Exercises with nothing logged are left out. You can resume it from Session done."
  (Keep going / Finish), the same dialog shape as Leave. The session list ends with Add exercise and Finish
  session. Its other exercises have **Start**, because ad-hoc exercises are never "done".
- **Session done** shows the start time and duration under the title, the summaries, and a
  filled **Done** at the bottom right that returns to Today (back does the same), with a text
  **Resume session** beside it that reopens the session as it was, for a finish by mistake. Its
  rows open the exercise's table to correct (ticket #15, below).
- Values cap at 5 characters for weight, 3 for full reps and 2 for partials. A reps value longer
  than `12 + 6` steps down to titleMedium, so `100 + 20` stays inside the 80 dp cell.

**Intensity techniques** (built in ticket #10).
- Every set's technique is on its index circle (tint and badge), and its drops, mini-sets or
  hold follow as 48 dp sub-rows in its tall pill from the start, in the order they'll be done.
  Planned sub-rows are muted prefills (drop weights computed from the set's planned or typed weight,
  live), done ones end in a filled check.
- **Drop**: RIR is an outlined 0 chip, Last is last time's drop (`18 × 9`), reps prefill from it.
- **Myo-reps**: reps prefill with the mini-set's target. Myo-reps have no maximum and no `+`. Before the set one muted planned
  row shows the target; during it, the done ones and the active one. The weight is muted and can't be tapped.
- **Iso hold**: one sub-row, its value in seconds (`20 s`); the weight is the set's, muted.
  The strip reads `pan_tool` Hold 20 s, a tonal **Start** / **Stop**, and Finish set. While it
  runs, the header pill shows `pan_tool` and the time held, filling toward the target, and turns
  primary when it's reached.
- **Breathing pill**: `air` and the 15 s countdown, drained like rest; it can't be tapped, and it
  goes away at zero.
- **Set sheet** (tap the index circle of a set still to do): "Set 3", then
  "Intensity technique" with radio rows None and the five techniques (name, its numbers, its
  symbol in tertiary at the end). Picking one applies it and closes. For a unilateral exercise's
  active set, a "Left and right apart" switch follows.
- **Split set**: the reps cell reads `10 / 9`, the side not being typed muted, and the Reps head
  reads `L / R`.

**Edit on the fly** (built in ticket #12).

Principles: edits sit where the thing edited is (a set's number, the exercise's ⋮, the list row's
⋮), never in a mode; every edit applies at once and is for this session only; what was changed
stays readable (a swap names what it replaced).

```
 DB Bench Press                          ⋮
 ╭───────────────────────────╮
 │ ⇄ for Barbell Bench Press │                    ← swapped slot: outlined chip under the name
 ╰───────────────────────────╯
          Last      kg  Reps  RIR
 (1)               [  ]  4–6   (1)    ◯
 (2)                     6–8   (1)    ○
  +   Add set                                     ← 48 dp sub-row, labelLarge onSurfaceVariant
```

- **Header ⋮**: Add exercise, Swap exercise, Remove from session, Keep edits for the block,
  divider, Finish session. Swap and Remove only while nothing is logged on the exercise. A set's
  options are on its index circle, not in ⋮.
- **Swap chip**: see Header.
- **Add set**: the table's last row for a program exercise, `add` in the index column.
- **Swap sheet**: "Swap <name>" headlineSmall, the
  search field, then "Alternatives" (the slot's planned exercise, tagged "Planned" in tertiary
  once swapped, and its alternatives) and "Same movement" (the library by movement pattern), as
  labelLarge heads over 56 dp rows. A search lists the whole library instead.
- **Set sheet**: the title row ends in a text **Remove set** (`delete`)
  when the set can go. "Target" then has three 56 dp rows, label at the margin and a − value +
  stepper at the end, values in one column: Fewest reps, Most reps, RIR (the value as its outlined chip).
  Separate rows, because a dash between two steppers reads as a minus. Steps that can't go
  further are dimmed (outlineVariant). Then the technique choices as before.
- **Session list**: each row ends in a 48 dp ⋮ (Swap exercise, Add set,
  Move up, Move down, Remove from session, each only when it applies). The row's end padding
  drops to 4 dp so the ⋮'s touch target meets the 16 dp edge.
- **Remove dialog**: "Remove <name>?", "From this session only. The program keeps it." (Cancel /
  Remove), the shape of the Leave and Finish dialogs.

**Warm-ups and plates** (built in ticket #14, reworked 2026-10-15).

Principles: warm-ups are guidance, not records: full rows that look like the sets around them but
lighter, typed and ticked like sets, and never logged; the plates for a weight are drawn by one
component, on the pad only; nothing about a warm-up touches the clock.

- **The ramp** follows the weight planned or typed for set 1 and the reps planned for it (last
  time's for an ad-hoc exercise); typing reps never changes it. It does not follow the weight
  keystroke by keystroke:
  while the weight is being typed the warm-ups stay as they were, and they ramp again once the pad
  moves on to the reps (→, or a tap on the reps cell). Typing `80` never passes through an 8 kg
  ramp. With nothing to ramp from yet (an empty first weight), there are none until →.
- **Rows, ticking and typing**: see Warm-up row and Warm-up on the pad above. They go once set 1
  is logged.
- **Plates** (the plate calculator, one component): a drawing of one side of the bar, sleeve to
  the right (the end of the grip, the collar, then the plates heaviest first, then the sleeve),
  the plates as rounded blocks taller and thicker by weight; and a legend, a colour square and
  `1 × 25` per plate, heaviest first, then a slash and the bar's own weight. White plates get a
  1 dp outline. Its content description reads the whole load: "Plates per side: 1 × 20, 1 × 15,
  on a 20 kg bar" ("The bar alone, 20 kg"; "61 kg isn't on these plates").
- **Scope**: plate-loaded bars (barbell, EZ, trap, safety bar) and plate-loaded machines (leg
  press, plate-loaded hack squat, Smith machine, sled), the equipment whose ladder is a base plus
  plate pairs. Never dumbbells, stack or cable machines, or bodyweight. On a machine the base is
  its starting weight (the empty machine or sled, 0 kg unless set), marked in the legend with a
  small sled where a bar has the slash; the description reads "on a 30 kg machine" and "The
  machine alone, 30 kg".
- **Plate colours** are fixed, the kg competition scale, like the RIR scale: 25 and 2.5 red
  `#D9453B`, 20 and 2 blue `#2F6FDB`, 15 and 1.5 yellow `#E5B12E`, 10 and 1 green `#3B9C55`, 5
  and 0.5 white `#F2F2F2` (outlined), 1.25 and any other plate silver `#A7ADB4`. Mid tones, so
  each reads on light and dark containers.
- **In the pad**: a 48 dp strip under the Next row for a barbell or plate-loaded exercise, on the
  weight and the reps alike (a warm-up on the pad too), so the pad keeps its height between them,
  under a thin outlineVariant divider that separates it from the Next row, then a second divider,
  then the strip and keys. It updates as the weight is typed;
  with nothing typed it draws the bar alone, and a weight the plates can't make draws the bar muted
  with "Not on these plates". The weight check's strip replaces it. A correction shows it too.
  Regular size: a 40 dp drawing, titleSmall legend.
- **The whole bar**: a tap on the pad's strip ("Show the whole bar") opens a sheet: the total in
  headlineMedium over "On a 20 kg bar" ("On a 30 kg machine, empty"); both sides drawn 96 dp tall,
  mirrored about the grip (on a machine, the machine as a block in the middle), every plate
  heaviest inside; then a 40 dp row per plate (its colour, `25 kg`, `2 each side`) and a last row
  for the bar or the machine's starting weight with its symbol. Description: "147.5 kg: a 20 kg
  bar with 2 × 25, 1 × 10, 1 × 2.5, 1 × 1.25 on each side". Screens: `light-whole-bar.png`,
  `dark-whole-bar.png`, `dark-whole-machine.png` (renders).

**Corrections** (built in ticket #15).

**No snackbar.** Logging a set shows nothing over the table; the check of the set logged last
takes it back, and a tap on any logged row corrects it. A corrected weight runs the same weight
check as logging, its reason and three weights in the strip.

Principles: a correction is the same pad on the row it fixes, so there is nothing new to learn;
the set to do steps back while it happens and the clock never notices; a finished exercise or past
session reads like the logging screen with nothing to do, so tapping a number is the only action.

```
 DB Bench Press                    [⏱ 2:28]  ⋮
          Last       kg   Reps  RIR
┌────────────────────────────────────────────┐
│✎                   25  [  9 ]   ●2    (✓)  │  ← the logged set, loaded: edit symbol for its index
└────────────────────────────────────────────┘
 (2)                 25    10     ●2    (✓)    ← logged rows stay tappable
 (3)                 25  8–10    (1)     ○     ← the set to do, back to its upcoming look
▓ Next  Cable Fly                          ⌃ ▓
▓  ●0   ●1   (●2)  ●3   ●4+         [F| P ]  ▓  ← a drop or mini-set: ✎ Drop 1 of set 3
 …
▓ [ × ] [ 0 ] [   ] [ ✓ ]                    ▓  ← × cancels, ✓ saves ("Save correction")
```

- **Loaded row**: the active row's look (outlined cells, the focused one in primary) with `edit`
  in primary in the set column; a sub-row the same at 48 dp. It opens on reps; the weight cell
  takes a tap. A mini-set's or hold's weight stays muted (it is the set's).
- **Pad**: always shown while correcting; the hide key becomes `close` (Cancel correction) and ✓
  saves. For a set the strip is RIR and F/P; for a drop, mini-set or hold it names the row
  (`edit` + "Drop 1 of set 3"), since they have no RIR.
- **Done rows** are one tap target each (16 dp radius ripple), "Correct set N", "Correct drop 1 of
  set 3".
- **Finished exercise**: the session list's **Open** shows its table
  with no active row and no Add set; the Next row, alone at the bottom, reads
  `Now  <current exercise>  ›` and goes back. The timer keeps running in the header.
- **Session done**: every row ends in `chevron_right` and opens the exercise's table
 , whose bottom line is `Session summary  ›`; back returns too.
- **Past sessions**: the navigation bar's History → a segmented list (title
  bodyLarge, date bodyMedium at the end, exercises on a second muted line, 72 dp rows) → the
  session's summary under a top app bar (date · start · duration · gym) → the exercise table as
  in Session done.

## Interaction rules

**✓ does:**

| State | ✓ |
| --- | --- |
| Weight field | (→) moves to reps; the warm-ups ramp from the new weight |
| Reps of a straight set | Logs weight, reps, partials and RIR; starts rest; the next set becomes active |
| Reps of a lengthened-partials set, partials not entered | Switches to P so partials can be typed; second ✓ logs |
| The same with the pad hidden | Logs the set as shown: the target partials if the set has them, else none |
| Reps of a drop or myo set | Logs the main set; rest stops; the first drop or mini-set becomes active |
| Drop sub-row | Logs the drop; after the last planned drop the set closes and rest starts |
| Myo mini-set | Logs the mini-set; closes the set when it misses the target, else starts the next breathing countdown |
| Iso hold | Stops a running hold timer and logs the whole seconds held (or the seconds typed); the set closes and rest starts |
| Left reps of a split set | (→ in effect) moves to the right side's reps; the next ✓ logs |
| Correcting a logged row | Saves the correction; the set to do now becomes active again. Rest is not touched |
| Weight looks wrong, first time | Does not log: buzzes, focuses the weight and shows why in the strip. The key turns into ✓ |
| Weight looks wrong, second ✓ | Logs the weight as typed (odd plates happen) |
| Nothing typed | Disabled look, does nothing |
| Within 1 s of logging a set | Does nothing, so a double tap logs one set |
| A warm-up's weight | (→) moves to its reps |
| A warm-up's reps | Ticks it (kept as typed if changed); the next warm-up comes on the pad, after the last set 1. No rest |

**The check column does:**

| Row | Check |
| --- | --- |
| The set to do | Logs it as shown and starts rest (✓ with the pad hidden); a warm-up on the pad steps back first |
| The drop, mini-set or hold being done | Logs it as shown |
| The set logged last in the session | Takes it back: deleted, its values on the pad, rest stopped |
| A warm-up | Ticks it and those above; on the last ticked, takes the tick back |
| Any other row | Nothing (its row tap corrects a logged one) |

Prefilled weight and RIR are real values; reps never are. The first key on a field replaces the
prefill. − / + step from the current value.

**Prefills follow what just happened.** The next set takes the last logged set's weight and its
RIR, never its reps. The first set takes the suggestion's weight. A correction to an earlier set feeds the prefills of the sets after it. The SPEC rule of
adjusting weight by the RIR gap is not in the prototype.

**Weight check.** A typed weight is flagged when it is 1.5× or more, or half or less, of the
reference (the last set, else the plan or planned drop), or not on the equipment's ladder.

**Corrections.** Tap any logged row or sub-row: it loads into the pad. ✓ saves, × or back
cancels. Finished exercises are reached through the session list (Open) or Session done.
Corrections never restart or reset the rest timer.

**Order.** Sets follow the session order, supersets alternating A1 → A2. An exercise started from
the session list stays current, with its superset partner, until all of their sets are logged;
then the session goes back to the first unfinished set. Any close of a partner's set (✓, the last
drop or mini-set, Finish set) moves on to the partner's set of the same round with no rest.

**Rest.**
- Logging a set starts the exercise's rest (per exercise, e.g. 2:30 bench, 1:30 laterals).
- No rest before drops or myo mini-sets: logging the main set clears the timer. Myo mini-sets
  get their own 15 s breathing countdown instead.
- No rest from A1 to A2 in a superset: the screen switches straight to the partner. After A2,
  full rest.
- Rest never resets when an earlier set is changed.
- When rest ends the phone vibrates and the timer counts up.
- Starting another exercise from the session list keeps the clock running, so it still counts
  from the last set.
- Pulling rest past zero with −15 buzzes once.
- With the screen off (ticket #13): an ongoing notification in the
  standard template, so it can be a Live Update. Title is the next set (`DB Bench Press · Set 3`),
  text its target (`25 kg × 8–10 @ 1`), header `Rest` and the system chronometer; actions **+15**
  and **End rest**, the rest sheet's words. When rest runs out: an alarm-class double buzz, header
  `Rest over` counting up, End rest only. Breathing: `air` icon, `Breathe`, `Mini-set 2`, no
  actions. The in-app buzz is the same alarm, so there is one buzz whether the app is open or not.

**Pad.**
- The pad is visible by default, for the active row.
- The hide-keyboard key hides it, leaving the keyboard and ✓ keys.
- Tapping any value in the active row brings the pad back, focused on that value.
- F/P: F types full reps, P types partials into the same cell (`12 + 6`). While typing partials
  the full reps are muted.

## Owner decisions

From the owner's phone test of the DEV build, 2026-10-15 (ticket #25).

- **Clearing RIR.** Tapping the selected RIR again deselects it; no new control. The set logs
  without an RIR, and RIR is optional everywhere a set is logged or corrected.
- **No "logged" snackbar.** It is gone, with its Fix. The undo is the check of the set logged last
  (unchanged); correcting is a tap on the row.
- **Divider on the pad** between the Next row and the plate strip (or the stack's load), in light
  and dark.
- **Warm-ups never follow typed reps.** Typing "12" passed through "1" and added warm-ups. The ramp
  comes from the planned reps, or last time's reps for an ad-hoc exercise.
- **No reps prefill.** The reps field is empty and shows the planned range (`6–8`) as a muted
  placeholder; ✓ is disabled until reps are typed. So the open question about showing the range
  under a prefilled value no longer applies.

From the owner's phone test of the DEV build, 2026-10-15 (ticket #26). The active row's pill cut
across a technique's tall pill (the old `screens/dark-session-techniques.png`).

- **No row highlight.** No row has a background pill. The active row is marked by its kg and reps
  in outlined cells, the focused one outlined in primary, and its index circle filled with
  primary. Linked pills (drops, mini-sets, holds) stay whole and untouched.
- **Active sub-row.** A drop, mini-set or hold being done is marked only by its outlined cells;
  the set's circle at the top of the pill stays primary for the whole group.
- **A typed warm-up freezes only itself.** The other warm-ups still follow the ramp.
- **`+` adds mid-set.** `+` at the foot of a pill adds another drop to a drop set, or another
  mini-set to myo-reps, also after the set has started. Before a myo-reps set its pill shows a
  muted planned row for every mini-set it allows, like planned drops, so each `+` adds a row and
  the count shows.
- **Undo stays in order.** Once a drop is logged the set can't be taken back (the rule as it
  was); a drop is corrected by tapping its row.
- **Tappable chips.** The swap chip opens Swap; the superset chip goes to the partner exercise.
