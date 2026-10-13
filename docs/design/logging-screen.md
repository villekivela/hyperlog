# Logging screen: design note

The in-workout screen where sets are logged. The expressive pass (tonal cards, roomier grid,
Next card, rest pill in primary container) is described here; the pad is unchanged. Screens:
`screens/session-*.png` from the owner's phone (dark, monochrome system style) and
`screens/light-session-*.png`, `dark-session-*.png`, `light-adhoc-add-exercise.png`, Robolectric
renders. The column heads sit above the table's rows, so they stay when rows scroll.

## Principles

1. **One exercise, one table.** Its sets are rows on one card, a tone off the screen. The row being
   done is the only filled row (secondary container); the focused value is a well in it.
2. **Every decision for a set is on the pad.** Digits, one-rung/one-rep steps, RIR, full/partial,
   ✓. The thumb never has to reach up the screen to log.
3. **Planned things look planned, done things look done.** Prefilled values are muted, logged
   values are full contrast. An RIR ring is a target and a filled RIR dot is a result. Anything
   logged can be tapped and corrected.
4. **A technique is visible before it starts.** The technique is named under its set, and its
   drops or mini-sets appear as planned sub-rows with their weights.
5. **One clock, shown only when there is something to wait for.** No rest before drops, myo
   mini-sets or a superset partner.
6. **Symbols over words.** Text is for names and numbers. Every symbol is a Material Symbol
   (Rounded).

## Tokens

Spacing is on an 8 dp grid, with 4 dp as the half step.

| Token | Value | Use |
| --- | --- | --- |
| Margin | 24 dp | Left edge of all text: exercise name, set numbers, Next line, sheets |
| Table card | 12 dp from the edge, 4 dp padding, 28 dp radius | The set table; as tall as its rows |
| Row inset | 16 dp | Edge of the active row's highlight (card 12 + 4; content stays at 24 dp) |
| Set row | 64 dp | Main set rows |
| Sub-row | 48 dp | Drops and myo mini-sets |
| Label row | 32 dp | Column heads |
| Tag line | 40 dp, 28 dp pill | Technique, back-off and warm-up tags under a set |
| Columns | 32 · 80 · flex · 72 · 88 · 36 dp | Set, Target, Last, kg, Reps, RIR |
| Cell | 48 dp tall, 12 dp radius, 4 dp inside its column | Tappable value in the active row: 8 dp between kg and reps, 12 dp from the RIR dot |
| Next card | 56 dp, 20 dp radius, 12 dp from the edges, 8 dp above the pad | The Next / Now / Session summary line |
| Key | 56 dp tall, 4 dp gaps, 12 dp radius | Pad keys, 4 equal columns |
| Row highlight | 24 dp radius | Active row (the card's 28 less its 4 dp padding) |
| RIR mark | 20 dp circle, 2 dp ring | Target (ring) and result (filled) |

Type (Material 3 scale, system font, tabular figures for every number):

| Role | Style |
| --- | --- |
| Exercise name | headlineSmall |
| Superset context, technique label, Next label | labelLarge |
| Column heads | labelMedium |
| Values (kg, reps) | titleLarge; focused cell SemiBold |
| Set number | titleMedium; Bold in the active row |
| Target and last time | bodyMedium |
| Keys | 24 sp |

Colour comes from the dynamic theme, light and dark:

| Role | Colour |
| --- | --- |
| Screen | surface (the one screen that keeps it, so the pad and the cards stand off it) |
| Table card | surfaceContainer |
| Active row, active sub-row | secondaryContainer |
| Focused cell | surfaceContainerLowest fill, 2 dp primary outline |
| Next card | surfaceContainerHigh; "Next" in primary |
| Pad | surfaceContainer, drawn through the gesture area |
| Keys | surfaceContainerHighest |
| ✓ / → key | primary (dimmed to surfaceContainerHigh when there is nothing to log) |
| Prefilled values | outline |
| Weight warning | error (cell outline and value, strip text and `warning` symbol) |
| Last time | onSurfaceVariant |
| Technique tag | tertiaryContainer pill, onTertiaryContainer; surfaceContainerHighest once done |
| Back-off and warm-up tags | surfaceContainerHighest pill, onSurfaceVariant |
| Rest pill | primaryContainer, time left laid over it in onPrimaryContainer at 16%; surfaceContainerHighest after rest |
| Breathing pill | tertiaryContainer |
| Day done strip | primaryContainer, rounded 28 dp at the top |
| Sheet groups | surfaceContainerHighest segments on the surfaceContainerLow sheet; the picked row secondaryContainer |

RIR is the one fixed scale and does not follow the theme: 0 `#D9645B`, 1–2 `#E0A84E`, 3+
`#6BAFA9`, with dark numerals on filled dots.

## Layout

```
 status bar
 A1 with Seated Leg Curl  40.5 × 12 ●1        (only in a superset: partner and its last set)
 Leg Extension                  (⏱ 1:42)  ⋮     ← name opens prescription; pill opens rest sheet
╭──────────────────────────────────────────────╮ ← table card, surfaceContainer
│      Target     Last       kg    Reps   RIR  │
│╭────────────────────────────────────────────╮│
││1    10–12 (1) 55 × 12    60  [ 12 ]    ●1  ││ ← active row, secondaryContainer; focused well
│╰────────────────────────────────────────────╯│
│ 2    10–12 (0) 55 × 12    60    12           │ ← upcoming: muted prefill
│     (⟲ Myo-reps, mini-sets of 4)             │ ← technique tag, tertiaryContainer pill
│ ↳    4                    60     4           │ ← planned mini-set
╰──────────────────────────────────────────────╯
                     (space)
╭──────────────────────────────────────────────╮
│ Next  Seated Leg Curl                     ⌃  │ ← Next card, surfaceContainerHigh
╰──────────────────────────────────────────────╯
▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓
  ●0   ●1   (●2)  ●3   ●4+         [F| P ]      ← strip: RIR + full/partial
 [ 1 ] [ 2 ] [ 3 ] [ ⌫ ]                        (weight field: ladder rungs instead)
 [ 4 ] [ 5 ] [ 6 ] [ − ]                        (drop/myo: progress + Finish set)
 [ 7 ] [ 8 ] [ 9 ] [ + ]
 [ ⌨ ] [ 0 ] [ . ] [ ✓ ]
▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓ gesture area ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓
```

`(n)` is an RIR ring (target), `●n` a filled RIR dot (result or selection).

## Components

**Set row, upcoming.** Set number in onSurfaceVariant. Target reps, target RIR ring, last time.
In a program session "last time" is the session progression judges (same slot and rep range, else
the latest), the one the why-line names, matched to the rows by position; ad hoc it's the latest.
kg and reps are the prefilled values in `outline`. The RIR column is empty.

**Set row, active.** Filled secondaryContainer with a 24 dp radius, 4 dp inside the table card so
its content keeps the 24 dp edge. The set number is bold primary. kg and reps are tappable cells,
and the focused one is a well (surfaceContainerLowest) with a primary outline, 4 dp inside its
column, so it never touches the RIR dot. The RIR column shows the selected RIR as a
filled dot. A locked cell (myo mini-set weight) stays muted and can't be tapped.

**Set row, done.** Values at full contrast and a filled RIR dot with the logged RIR. Partials
read `12 + 6`. Tapping it loads it into the pad for a correction.

**Set row, being corrected.** Looks like the active row, but the set number is replaced by an
`edit` symbol and the pad's hide key becomes `close` (cancel). The set to do now goes back to
its upcoming look until the correction is saved or cancelled.

**Sub-row (drop, myo mini-set).** 48 dp, with a `subdirectory_arrow_right` symbol in the set
column. Target: a 0 ring for a drop (to failure), the rep target for a mini-set. Last: last
time's drop (`18 × 7`). kg shows the planned drop weight, computed from the set weight and
rounded down to the equipment's ladder. It updates live while the main set's weight is being
typed. Drop reps are prefilled from last time's drops (else 6), muted like every prefill. A done
sub-row ends in a check (no RIR is logged for drops or mini-sets) and can be tapped to correct.

**Technique label.** A 28 dp pill on a 40 dp line under its set, its symbol on the Target column:
symbol and name in a tertiaryContainer tag (`trending_down` Drop set, `repeat` Myo-reps, mini-sets of 4, `unfold_less`
Lengthened partials to failure). It stays, neutral (surfaceContainerHighest), once the set is done, so the record shows what
was done. A back-off rule and the warm-ups take the neutral tag throughout: tertiary means a
technique.

**Pad.** Drawn like the system number keyboard. The strip above the keys changes with the
field:
- Reps of a set: RIR 0–4+ as filled dots, then the F/P toggle.
- Weight: five rungs of the equipment's ladder around the current value.
- Weight that looks wrong: `warning` symbol, the reason ("10× last set", "3× lighter than the
  plan", "Not on this equipment") and three real rungs near the expected weight.
- Drop or mini-set: progress ("Drop 1 of 2", "Mini-set 2 of 5") and **Finish set**.

**Hidden pad.** The pad folds down to its bottom row's corners: keyboard (show pad) at the left
and ✓ at the right, still over the gesture area. ✓ there logs the set as shown.

Keys: digits, backspace, − and + (one ladder rung for weight, one rep for reps), hide keyboard,
`.` (weight only) and ✓. ✓ is one key, the same size as the others, filled primary. On the weight
field it shows → instead.

**Timer.** A 48 dp pill in the header, primaryContainer, so rest reads from across the gym. It
drains left to right during rest. After rest it counts
up (`+0:12`) in a muted container until the next set is logged. Before a myo mini-set it is a
15 s breathing countdown with an `air` symbol. Tapping it during rest opens the rest sheet:
−15, the time, +15, End rest.

**Superset line.** Above the name: `A1 with Seated Leg Curl`, then the partner's last logged set
and its RIR dot, so the place in the round is clear without a second table. The app uses the
day's slot labels (`2a with Seated Leg Curl`), the label in tertiary like the session list's tag;
the rest is labelLarge in onSurfaceVariant, on a 24 dp line, so the header stays 72 dp.

**Next line.** A card of its own between the table and the pad (surfaceContainerHigh, 20 dp
radius), "Next" in primary: "Next" and the next exercise outside the current one (or superset): the first with
planned sets left in the day's order, so after a Start out of order it names the skipped one; "Last
exercise" alone when nothing follows. Tap opens the session list. While looking at a finished
exercise it reads "Now" and the current exercise, and tap goes back.

**Sheets** (modal bottom sheets, surfaceContainerLow, 24 dp text edge). Their rows sit in
surfaceContainerHighest groups 12 dp in, with the picked or current row in secondaryContainer
(the session list's exercise on screen, the set sheet's technique, the start sheet's gym); the
prescription's plan, the machine row and the set sheet's target steppers are cards too.
- *Session list:* status symbol (done, now, upcoming), superset tag, name, a summary (logged sets
  as `25 × 10 @ 2`, `5.7 × 12 + 6p @ 0`, drops and mini-sets after it; never broken inside a set;
  or set count and rep range) with technique symbols. A tonal **Start** on exercises not yet
  done, **Open** on finished ones.
  In a program session (ticket #11) the tag is a 40 dp column after the status symbol holding every
  slot's label (`1`, `2a`, `2b`, `3`), so names align; superset labels are tertiary, straight ones
  onSurfaceVariant. A superset on screen shows both partners as now, with no Start on either. The
  done symbol means every planned set is logged: an exercise left halfway keeps the empty circle
  and shows its logged sets.
- *Prescription:* name, equipment, each set (reps, RIR ring, technique with its numbers, e.g.
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
- No Target column; Last takes its width. The "Last" head shows only once there is history.
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
- Every set's technique is named under it, and its drops, mini-sets or hold follow as 48 dp
  sub-rows from the start, in the order they'll be done. Planned sub-rows are muted prefills
  (drop weights computed from the set's planned or typed weight, live), done ones end in a check.
- **Drop**: Target is a 0 ring, Last is last time's drop (`18 × 9`), reps prefill from it.
- **Myo-reps**: Target is the mini-set's reps. Before the set one planned mini-set shows; during
  it, the done ones and the active one. The weight is muted and can't be tapped.
- **Iso hold**: one sub-row, Target and value in seconds (`20 s`); the weight is the set's, muted.
  The strip reads `pan_tool` Hold 20 s, a tonal **Start** / **Stop**, and Finish set. While it
  runs, the header pill shows `pan_tool` and the time held, filling toward the target, and turns
  primary when it's reached.
- **Breathing pill**: `air` and the 15 s countdown, drained like rest; it can't be tapped, and it
  goes away at zero.
- **Set sheet** (tap a set number still to do, or ⋮ → Edit set N): "Set 3", then
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
 ⇄ In place of Barbell Bench Press             ← swapped slot, labelLarge, symbol tertiary
 DB Bench Press                          ⋮
       Target      Last      kg  Reps  RIR
 1     4–6 (1)              [  ]   6    ●1
 2     6–8 (1)                     8
 +     Add set                                 ← 48 dp sub-row, labelLarge onSurfaceVariant
```

- **Header ⋮**: Add exercise, Swap exercise, Edit set N, Remove from session, divider, Finish
  session. Swap and Remove only while nothing is logged on the exercise.
- **Swapped line**: the superset line's slot (24 dp above the name): `swap_horiz` in tertiary and
  "In place of <planned>" in onSurfaceVariant. A superset's line wins when both apply.
- **Add set**: the table's last row for a program exercise, `add` in the set column at 20 dp.
- **Swap sheet**: "Swap <name>" headlineSmall, the
  search field, then "Alternatives" (the slot's planned exercise, tagged "Planned" in tertiary
  once swapped, and its alternatives) and "Same movement" (the library by movement pattern), as
  labelLarge heads over 56 dp rows. A search lists the whole library instead.
- **Set sheet**: the title row ends in a text **Remove set** (`delete`)
  when the set can go. "Target" then has three 56 dp rows, label at the margin and a − value +
  stepper at the end, values in one column: Fewest reps, Most reps, RIR (the value as its ring).
  Separate rows, because a dash between two steppers reads as a minus. Steps that can't go
  further are dimmed (outlineVariant). Then the technique choices as before.
- **Session list**: each row ends in a 48 dp ⋮ (Swap exercise, Add set,
  Move up, Move down, Remove from session, each only when it applies). The row's end padding
  drops to 4 dp so the ⋮'s touch target meets the 16 dp edge.
- **Remove dialog**: "Remove <name>?", "From this session only. The program keeps it." (Cancel /
  Remove), the shape of the Leave and Finish dialogs.

**Warm-ups and plates** (built in ticket #14).

Principles: warm-ups are guidance, not records, so they look like the rows around them but
lighter, and ask for nothing; the plates for a weight are drawn, one component wherever plates
show; nothing about a warm-up touches the clock. Screens: `light-session-plates.png`,
`dark-session-plates.png` (renders).

```
╭──────────────────────────────────────────────╮
│      Target     Last       kg    Reps   RIR  │
│     (⟋ Warm-up)                              │ ← neutral tag
│ W    ─┤▌▌├─ ■1 × 10 ■1 × 2.5   45     8   ✓  │ ← compact plates over Target and Last
│ W    ─┤▌├─  ■1 × 20            60     5      │
│╭────────────────────────────────────────────╮│
││1    4–6 (1)              [100]   6    ●1  ││
│╰────────────────────────────────────────────╯│
╰──────────────────────────────────────────────╯
 ▓ pad ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓
   ─┤▐█▐█├──   ■ 2 × 20   ⟋ 20                  ← plate strip, while the weight is typed
   ────────────────────────────────────────────   ← outlineVariant divider
   95   97.5  (100)  102.5  105                   ← the strip and keys, unchanged
```

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
- **In the pad**: a 48 dp strip at the top of the pad for a barbell or plate-loaded exercise, on
  the weight and the reps alike, so the pad keeps its height between them, then a thin outlineVariant divider, then the pad as it was.
  It updates as the weight is typed; with nothing typed it draws the bar alone, and a weight the
  plates can't make draws the bar muted with "Not on these plates". The weight check's strip
  replaces it. A correction shows it too. Regular size: a 40 dp drawing, titleSmall legend.
- **The whole bar**: a tap on the pad's strip ("Show the whole bar") opens a sheet: the total in
  headlineMedium over "On a 20 kg bar" ("On a 30 kg machine, empty"); both sides drawn 96 dp tall,
  mirrored about the grip (on a machine, the machine as a block in the middle), every plate
  heaviest inside; then a 40 dp row per plate (its colour, `25 kg`, `2 each side`) and a last row
  for the bar or the machine's starting weight with its symbol. Description: "147.5 kg: a 20 kg
  bar with 2 × 25, 1 × 10, 1 × 2.5, 1 × 1.25 on each side". Screens: `light-whole-bar.png`,
  `dark-whole-bar.png`, `dark-whole-machine.png` (renders).
- **On a warm-up row**: the compact size over Target and Last (a warm-up has neither): a 24 dp
  drawing and a one-line labelMedium legend without the bar's weight, which the pad already
  gives, a single plate as its weight alone (`■20 ■10 ■2.5`, `■2×20`). The row stays 48 dp.
- **Keeping the next set in view**: when the table's rows change (a set logged, warm-ups appearing,
  a correction), it scrolls as little as it takes to show the active row and the set after it,
  with any tag or plates between them; warm-ups above scroll away first.
- **Warm-up row**: `W` in labelLarge onSurfaceVariant in the set column, the compact plates, kg
  and reps in titleLarge, `outline` until ticked, onSurface after, with a `check` in the RIR
  column. The whole row is one checkbox: a tap ticks it and those above, a tap on the last
  ticked one takes it back. The pad stays on the work set.
- **Warm-up label**: the neutral tag, `stairs` and "Warm-up".
- The warm-ups go once the first work set is logged. There is no plates line under the active set
  any more: the pad's strip is where the plates are read, mid-set.

**Corrections** (built in ticket #15).

**Fix the set just logged.** A Material snackbar over the Next card after each logged set:
"<exercise> 60 × 12 @ 1 logged" with **Fix** (long duration). Fix loads that set into the pad on
its own exercise's table, focused on the weight when it was kept past a weight check, and saving
or cancelling returns to the set to do: a 255-for-25 typo is Fix, 2, 5, →, ✓ (5 taps, was 9). A
corrected weight runs the same weight check as logging, its reason and three weights in the
strip.

Principles: a correction is the same pad on the row it fixes, so there is nothing new to learn;
the set to do steps back while it happens and the clock never notices; a finished exercise or past
session reads like the logging screen with nothing to do, so tapping a number is the only action.

```
 DB Bench Press                    [⏱ 2:28]  ⋮
       Target      Last       kg   Reps  RIR
┌────────────────────────────────────────────┐
│✎     8–10 (2)               25  [  9 ]   ●2 │  ← the logged set, loaded: edit symbol for its number
└────────────────────────────────────────────┘
 2     8–10 (2)               25    10     ●2   ← logged rows stay tappable
 3     8–10 (1)               25    10          ← the set to do, back to its upcoming look
▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓
  ●0   ●1   (●2)  ●3   ●4+         [F| P ]      ← a drop or mini-set: ✎ Drop 1 of set 3
 …
 [ × ] [ 0 ] [   ] [ ✓ ]                        ← × cancels, ✓ saves ("Save correction")
```

- **Loaded row**: the active row's look (surfaceContainerHigh, focused cell outlined) with `edit`
  in primary in the set column; a sub-row the same at 48 dp. It opens on reps; the weight cell
  takes a tap. A mini-set's or hold's weight stays muted (it is the set's).
- **Pad**: always shown while correcting; the hide key becomes `close` (Cancel correction) and ✓
  saves. For a set the strip is RIR and F/P; for a drop, mini-set or hold it names the row
  (`edit` + "Drop 1 of set 3"), since they have no RIR.
- **Done rows** are one tap target each (16 dp radius ripple), "Correct set N", "Correct drop 1 of
  set 3".
- **Finished exercise**: the session list's **Open** shows its table
  with no active row and no Add set; the Next line reads `Now  <current exercise>  ›` and goes
  back. The timer keeps running in the header.
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
| Weight field | (→) moves to reps |
| Reps of a straight set | Logs weight, reps, partials and RIR; starts rest; the next set becomes active |
| Reps of a lengthened-partials set, partials not entered | Switches to P so partials can be typed; second ✓ logs |
| The same with the pad hidden | Logs the set as shown: the target partials if the set has them, else none |
| Reps of a drop or myo set | Logs the main set; rest stops; the first drop or mini-set becomes active |
| Drop sub-row | Logs the drop; after the last planned drop the set closes and rest starts |
| Myo mini-set | Logs the mini-set; closes the set when it misses the target or hits the maximum, else starts the next breathing countdown |
| Iso hold | Stops a running hold timer and logs the whole seconds held (or the seconds typed); the set closes and rest starts |
| Left reps of a split set | (→ in effect) moves to the right side's reps; the next ✓ logs |
| Correcting a logged row | Saves the correction; the set to do now becomes active again. Rest is not touched |
| Weight looks wrong, first time | Does not log: buzzes, focuses the weight and shows why in the strip. The key turns into ✓ |
| Weight looks wrong, second ✓ | Logs the weight as typed (odd plates happen) |
| Nothing typed | Disabled look, does nothing |
| Within 1 s of logging a set | Does nothing, so a double tap logs one set |

Prefilled values are real values: ✓ without typing logs the set as planned. The first key on a
field replaces the prefill. − / + step from the current value.

**Prefills follow what just happened.** The next set takes the last logged set's weight and its
reps (capped at the set's top of range). The first set takes the suggestion and the top of the
range. A correction to an earlier set feeds the prefills of the sets after it. The SPEC rule of
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
