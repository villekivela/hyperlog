# Logging screen: design note

The in-workout screen where sets are logged. The prototype in `prototype/` implements this note
(round 4 with the fixes from `test-report.md`). Screenshots from the phone are in `screens/`.

## Principles

1. **One exercise, one table.** Its sets are rows. The row being done is the only highlighted
   surface. No cards, no nested containers.
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
| Row inset | 16 dp | Edge of the active row's highlight (content stays at 24 dp) |
| Set row | 64 dp | Main set rows |
| Sub-row | 48 dp | Drops and myo mini-sets |
| Label row | 32 dp | Column heads, technique label |
| Columns | 32 · 88 · flex · 64 · 80 · 24 dp | Set, Target, Last, kg, Reps, RIR |
| Cell | 48 dp tall, 12 dp radius | Tappable value in the active row |
| Key | 56 dp tall, 4 dp gaps, 12 dp radius | Pad keys, 4 equal columns |
| Row highlight | 16 dp radius | Active row |
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
| Screen | surface |
| Active row | surfaceContainerHigh |
| Focused cell | surfaceContainerHighest fill, 2 dp primary outline |
| Pad | surfaceContainer, drawn through the gesture area |
| Keys | surfaceContainerHighest |
| ✓ / → key | primary (dimmed to surfaceContainerHigh when there is nothing to log) |
| Prefilled values | outline |
| Weight warning | error (cell outline and value, strip text and `warning` symbol) |
| Last time | onSurfaceVariant |
| Technique label and symbols | tertiary; onSurfaceVariant once done |
| Timer pill | secondaryContainer, drained with primary at 24% |

RIR is the one fixed scale and does not follow the theme: 0 `#D9645B`, 1–2 `#E0A84E`, 3+
`#6BAFA9`, with dark numerals on filled dots.

## Layout

```
 status bar
 A1 with Seated Leg Curl  40.5 × 12 ●1        (only in a superset: partner and its last set)
 Leg Extension                    [⏱ 1:42]  ⋮   ← name opens prescription; timer opens rest sheet
       Target      Last       kg   Reps  RIR
┌────────────────────────────────────────────┐
│1     10–12 (1)  55 × 12     60  [ 12 ]   ●1 │  ← active row, focused cell outlined
└────────────────────────────────────────────┘
 2     10–12 (0)  55 × 12     60    12         ← upcoming: muted prefill
       ⟲ Myo-reps, mini-sets of 4              ← technique label (tertiary)
 ↳     4                      60     4         ← planned mini-set
                     (scrolls)
 Next  Seated Leg Curl                     ⌃   ← opens session list
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
kg and reps are the prefilled values in `outline`. The RIR column is empty.

**Set row, active.** Highlighted surfaceContainerHigh with a 16 dp radius, inset 16 dp so its
content keeps the 24 dp edge. The set number is bold primary. kg and reps are tappable cells,
and the focused one has a fill and a primary outline. The RIR column shows the selected RIR as a
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

**Technique label.** One 32 dp line under its set, aligned with the Target column: symbol and
name in tertiary (`trending_down` Drop set, `repeat` Myo-reps, mini-sets of 4, `unfold_less`
Lengthened partials to failure). It stays, muted, once the set is done, so the record shows what
was done.

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

**Timer.** A 40 dp pill in the header. It drains left to right during rest. After rest it counts
up (`+0:12`) in a muted container until the next set is logged. Before a myo mini-set it is a
15 s breathing countdown with an `air` symbol. Tapping it during rest opens the rest sheet:
−15, the time, +15, End rest.

**Superset line.** Above the name: `A1 with Seated Leg Curl`, then the partner's last logged set
and its RIR dot, so the place in the round is clear without a second table. The app uses the
day's slot labels (`2a with Seated Leg Curl`), the label in tertiary like the session list's tag;
the rest is labelLarge in onSurfaceVariant, on a 24 dp line, so the header stays 72 dp.

**Next line.** "Next" and the next exercise outside the current one (or superset): the first with
planned sets left in the day's order, so after a Start out of order it names the skipped one; "Last
exercise" alone when nothing follows. Tap opens the session list. While looking at a finished
exercise it reads "Now" and the current exercise, and tap goes back.

**Sheets** (modal bottom sheets, surfaceContainerLow, 24 dp margins):
- *Session list:* status symbol (done, now, upcoming), superset tag, name, a summary (logged sets
  as `25 × 10 @ 2`, `5.7 × 12 + 6p @ 0`, drops and mini-sets after it; never broken inside a set;
  or set count and rep range) with technique symbols. A tonal **Start** on exercises not yet
  done, **Open** on finished ones.
  In a program session (ticket #11, screens `superset-04-session-list.png`,
  `order-03-session-list.png`) the tag is a 40 dp column after the status symbol holding every
  slot's label (`1`, `2a`, `2b`, `3`), so names align; superset labels are tertiary, straight ones
  onSurfaceVariant. A superset on screen shows both partners as now, with no Start on either. The
  done symbol means every planned set is logged: an exercise left halfway keeps the empty circle
  and shows its logged sets.
- *Prescription:* name, equipment, each set (reps, RIR ring, technique with its numbers, e.g.
  "2 drops of 25%"), rest, why the weight was suggested, the technique note, and
  **Technique video**.
- *Rest:* see Timer.

**Session done.** Every exercise with its summary. Tapping one opens its table to look at or
correct; back returns to the summary.

**Leave dialog.** Back on the session screen asks "Leave the session?", with "It keeps running.
Everything logged is saved." (Stay / Leave). Every tap is saved, so Leave goes to Today, which
offers **Resume session**. The app opens on a running session.

**Ad-hoc session** (screens `adhoc-*.png`, built in ticket #2). The same table, without a plan:
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
  rows don't open yet: corrections are ticket #15.
- Values cap at 5 characters for weight, 3 for full reps and 2 for partials. A reps value longer
  than `12 + 6` steps down to titleMedium, so `100 + 20` stays inside the 80 dp cell.

**Intensity techniques** (screens `technique-*.png`, built in ticket #10; rendered with
Robolectric, so they have no status bar).
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
- **Set sheet** (tap a set number still to do, or ⋮ → Technique for set N): "Set 3", then
  "Intensity technique" with radio rows None and the five techniques (name, its numbers, its
  symbol in tertiary at the end). Picking one applies it and closes. For a unilateral exercise's
  active set, a "Left and right apart" switch follows.
- **Split set**: the reps cell reads `10 / 9`, the side not being typed muted, and the Reps head
  reads `L / R`.

## Interaction rules

**✓ does:**

| State | ✓ |
| --- | --- |
| Weight field | (→) moves to reps |
| Reps of a straight set | Logs weight, reps, partials and RIR; starts rest; the next set becomes active |
| Reps of a lengthened-partials set, partials not entered | Switches to P so partials can be typed; second ✓ logs |
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
drop or mini-set, Finish set) moves on to the partner's set of the same round with no rest (screens
`superset-01..03`, `order-01..02`).

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
- Not in the prototype: the buzz with the screen off. The real app needs an ongoing notification
  with the countdown and an alarm-class vibration.

**Pad.**
- The pad is visible by default, for the active row.
- The hide-keyboard key hides it, leaving the keyboard and ✓ keys.
- Tapping any value in the active row brings the pad back, focused on that value.
- F/P: F types full reps, P types partials into the same cell (`12 + 6`). While typing partials
  the full reps are muted.
