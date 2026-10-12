# Logging screen: design note

The in-workout screen where sets are logged. The prototype in `prototype/` implements this note
(round 4). Screenshots from the phone are in `screens/`.

## Principles

1. **One exercise, one table.** Its sets are rows. The row being done is the only highlighted
   surface. No cards, no nested containers.
2. **Every decision for a set is on the pad.** Digits, one-rung/one-rep steps, RIR, full/partial,
   ✓. The thumb never has to reach up the screen to log.
3. **Planned things look planned, done things look done.** Prefilled values are muted, logged
   values are full contrast. An RIR ring is a target and a filled RIR dot is a result.
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
| Last time | onSurfaceVariant |
| Technique label and symbols | tertiary; onSurfaceVariant once done |
| Timer pill | secondaryContainer, drained with primary at 24% |

RIR is the one fixed scale and does not follow the theme: 0 `#D9645B`, 1–2 `#E0A84E`, 3+
`#6BAFA9`, with dark numerals on filled dots.

## Layout

```
 status bar
 Superset with Seated Leg Curl                (only in a superset)
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
read `12 + 6`.

**Sub-row (drop, myo mini-set).** 48 dp, with a `subdirectory_arrow_right` symbol in the set
column. Target: a 0 ring for a drop (to failure), the rep target for a mini-set. kg shows the
planned drop weight, computed from the set weight and rounded down to the equipment's ladder.
It updates live while the main set's weight is being typed. A done sub-row ends in a check
(no RIR is logged for follow-ups).

**Technique label.** One 32 dp line under its set, aligned with the Target column: symbol and
name in tertiary (`trending_down` Drop set, `repeat` Myo-reps, mini-sets of 4, `unfold_less`
Lengthened partials to failure). It stays, muted, once the set is done, so the record shows what
was done.

**Pad.** Drawn like the system number keyboard. The strip above the keys changes with the
field:
- Reps of a set: RIR 0–4+ as filled dots, then the F/P toggle.
- Weight: five rungs of the equipment's ladder around the current value.
- Drop or mini-set: progress ("Drop 1 of 2", "Mini-set 2") and **Finish set**.

Keys: digits, backspace, − and + (one ladder rung for weight, one rep for reps), hide keyboard,
`.` (weight only) and ✓. ✓ is one key, the same size as the others, filled primary. On the weight
field it shows → instead.

**Timer.** A 40 dp pill in the header. It drains left to right during rest. After rest it counts
up (`+0:12`) in a muted container until the next set is logged. Before a myo mini-set it is a
15 s breathing countdown with an `air` symbol. Tapping it during rest opens the rest sheet:
−15, the time, +15, End rest.

**Next line.** "Next" and the next exercise outside the current one (or superset). It sits above
the pad, or above the gesture area when the pad is hidden. Tap opens the session list.

**Sheets** (modal bottom sheets, surfaceContainerLow, 24 dp margins):
- *Session list:* status symbol (done, now, upcoming), superset tag, name, a summary line (logged
  sets, or set count and rep range) with technique symbols, and a tonal **Start** on exercises not
  yet done. Start makes that exercise current and ends the rest.
- *Prescription:* name, equipment, each set (reps, RIR ring, technique), rest, why the weight was
  suggested, the technique note, and **Technique video**.
- *Rest:* see Timer.

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
| Nothing typed (empty drop reps) | Disabled look, does nothing |

Prefilled values are real values: ✓ without typing logs the set as planned. The first key on a
field replaces the prefill. − / + step from the current value.

**Rest.**
- Logging a set starts the exercise's rest (per exercise, e.g. 2:30 bench, 1:30 laterals).
- No rest before drops or myo mini-sets: logging the main set clears the timer. Myo mini-sets
  get their own 15 s breathing countdown instead.
- No rest from A1 to A2 in a superset: the screen switches straight to the partner. After A2,
  full rest.
- Rest never resets when an earlier set is changed.
- When rest ends the phone vibrates and the timer counts up.
- Starting another exercise from the session list ends the rest.

**Pad.**
- The pad is visible by default, for the active row.
- The hide-keyboard key hides it. The Next line drops to the gesture area.
- Tapping any value in the active row brings the pad back, focused on that value.
- F/P: F types full reps, P types partials into the same cell (`12 + 6`). While typing partials
  the full reps are muted.
