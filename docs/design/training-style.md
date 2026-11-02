# Training style

Settings → Coach → **Training style** (SPEC, Coaching → Training style): one page holding the
training targets, How I train and About me. Every field is edited in place and kept as it
changes. A sub-screen: back arrow, no navigation bar.

## Principles

- **One tile per setting.** Each setting is a tile of a `SegmentedList` group on
  `surfaceContainer`. Its title (bodyLarge) and one short line saying what it means or holds
  (bodyMedium, onSurfaceVariant) sit on the 28 dp text edge, with its control 12 dp under them.
  The tiles carry no symbol circles, so every title starts on the same edge.
- **Three groups, named with `GroupHead`:** Training targets, How I train, About me. About me
  starts with one line under its head: every field is optional.
- **Selection is a filled container.** Two to four short options use a `ConnectedChoice` (Goal,
  Volume, Sets through a block, Frequency). Longer options, one per line (Failure, Where are you now?),
  are rows whose picked one is a `secondaryContainer` pill with its radio on. Chips (Age, Aches
  and injuries) pick in `primaryContainer`, as `selectedChipColors` does elsewhere.
- **The line under a title follows the choice.** Volume shows the range it holds
  (`8–14 work sets per muscle a week`), Sets through a block and Failure explain the picked option
  (`Adds sets each week toward 14, then a deload`, with the range's top).
- **Numbers sit in a right-hand column.** Ranges are two 80 dp fields with a centred figure and
  an en dash between them, after a label on the text edge (Sets a week, Compounds, Isolation).
  A range that isn't whole turns both fields red, shows its rule under them, and keeps nothing.
- **How I train is principles, not prose.** You see each principle's short label; Claude reads
  its sentence from `assets/coach/how-i-train.json`. The fixed rules share one tile, "Always",
  each with a check in the column where the switches below sit: on, and not yours to turn off,
  without a greyed switch that would look broken.

## Layout

```
←  Training style
   Training targets                                   GroupHead
 ╭ Goal                                          ╮
 │ Your lift goals sit on top of any of them     │
 │ (Hypertrophy)[ Strength ][ Both ]             │    ConnectedChoice, labels fitted
 ├ Volume                                        ┤
 │ 8–14 work sets per muscle a week              │
 │ [ Low ](Moderate)[ High ][ Custom ]           │
 │ Sets a week               [ 12 ] – [ 18 ]     │    Custom only
 │            Each end 1–40, the fewest not …    │    error, right-aligned under the fields
 ├ Sets through a block                          ┤    the spec's Across a block (owner, #138)
 │ Same sets every week, then a deload           │
 │ (Same each week)[ Build up each week ]        │
 ├ Frequency        [1][2](2–3)[3+]              ┤
 ├ How hard                                      ┤    shown, not chosen
 │ Last set 0–1 RIR, earlier sets 1–2 RIR        │
 ├ Failure                                       ┤
 │ To failure on isolation and machine, …        │    the picked option's meaning
 │▐ Isolation and machines only             ◉ ▌ │    secondaryContainer pill, 4 dp in
 │  Never                                   ○   │
 │  Any lift                                ○   │
 ├ Calibration sets                         (●) ┤    the whole tile toggles
 ├ Rep ranges                                    ┤
 │ Compounds                 [  5 ] – [ 10 ]     │
 │ Isolation                 [  8 ] – [ 15 ]     │
 ╰                                               ╯
   How I train
 ╭ Always                                        ╮    the fixed rules, one tile
 │ Form before load                          ✓   │    40 dp lines; the check centred
 │ No loads in a program                     ✓   │    on the switches' 52 dp column,
 │ Swap around pain                          ✓   │    onSurfaceVariant
 │ Alternatives in every slot                ✓   │
 ├ Stretch-biased exercises                 (●) ┤    switches, on by default;
 ├ Stable lifts you can push hard           (●) ┤    the whole tile toggles
 ├ Blocks with a deload                     (●) ┤
 ├ Anything else                                 ┤
 │ ┌ One line, like "Supersets for arms" ────┐   │
 ╰                                               ╯
   About me
   Every field is optional
 ╭ Age  (Under 30)(30–39)(40–49)(50–59)(60 and over)
 ├ Where are you now?                            ┤
 │ How fast you progress, not years trained      │
 │  Still learning the lifts / Progress takes weeks / Progress takes months
 ├ Coming back from a break                 (●) ┤
 ├ Aches and injuries                            ┤
 │ (Neck)(Shoulders)…(Ankles and feet)           │
 │ ┌ Shoulders ─────────────────────────────┐    │    a one-line note per picked area
 ├ Exercises to avoid                   (+ Add) ┤    FilledTonalButton
 │ Barbell Back Squat                         ×  │    × lined up under Add's end
 ├ Exercises to favour                  (+ Add) ┤
 ├ Anything else                                 ┤
 │ ┌ One line, like "I train fasted" ────────┐   │
 ╰                                               ╯
```

The Settings row: `tune` symbol, **Training style**, `Hypertrophy · 8–14 sets a week` (the goal and
the volume range, one line), chevron. It is the Coach group's first row, above the Claude key.

## Tokens

- Screen `surfaceContainer` (`Tones.screen`), tiles `Tones.card`, 4 dp apart, 32 dp outer and 8 dp
  inner corners, 12 dp from the edge, 16 dp padding.
- Picked option pill: `secondaryContainer`, fully rounded, 48 dp tall, 4 dp inside the tile,
  12 dp start padding (its words on the text edge).
- `ConnectedChoice` with 8 dp horizontal padding and fitted widths, so `Hypertrophy` and `Moderate`
  keep their words at 115 % text.
- Number fields 80 dp wide, bodyLarge (Inter, tabular) centred. The dash is a 24 dp column.
- The picker sheet: the swap picker's look (`Sheet`, `surfaceContainerLow`, rows
  `surfaceContainerHighest`), headlineSmall title on the text edge, then search.

## States and rules

- **Volume → Custom** opens the range fields, filled with the current range. Picking Low, Moderate
  or High closes them. A custom range that matches none of the three opens on Custom.
- **A range is kept as you leave its field** (Next, Done, or a tap elsewhere) with both ends
  right, never halfway through typing an end (tester, #132: typing 18 kept 1–16). Tapped, a
  number field selects its number, so typing replaces it wherever the thumb lands.
- **Failure's line** under its title keeps three lines whichever option says it, so picking one
  never moves the options under a thumb (tester, #132).
- **The picker's search** takes the keyboard as the sheet opens.
- **A wrong range** (an end outside 1–40, or 1–30 for reps, or the fewest above the most): both
  fields red, the rule in `error` bodySmall under them, nothing kept. The last whole range stays
  in force.
- **How I train's fixed rules** are text, never a control: no switch, no ripple, nothing
  toggleable around them. Their checks carry no TalkBack label; "Always" reads first.
- **How I train's switches** keep as they turn, and the Anything else line as it's typed (blank
  keeps nothing). A switch turned off is all that's kept of them, so a principle added later
  starts on.
- **Options and chips clear on a second tap** in About me (Age, Where are you now?), since every
  field there is optional. Failure always holds one.
- **Aches and injuries:** a picked area adds a one-line note field labelled with the area.
  Unpicking the area drops the area and its note.
- **Exercises to avoid and to favour:** Add opens the picker sheet, which leaves out hidden
  exercises and ones already on either list. A pick closes it and lists the exercise on its tile
  with a × (TalkBack: "Remove <name>"). Add is named "Add an exercise to avoid / favour" for
  TalkBack and the tests.
- **The keyboard** pushes the page up (imePadding). A focused field scrolls into view above it.

## Screens

From the emulator (1280 × 2856, text at 115 %), 35 %. The phone was in use by another worker:

- `screens/training-style-settings-row-{dark,light}.png`: the row in Settings' Coach group.
- `training-style-defaults-{dark,light}.png`: the top of the page with every default (Robolectric
  render, after #138's Sets through a block and light wells).
- `training-style-custom-volume-{dark,light}.png`: Custom with a wrong end and its error
  (Robolectric render, as above).
- `training-style-how-i-train-{dark,light}.png`: How I train with Blocks with a deload off and
  an Anything else line (Robolectric render, the section cropped).
- `training-style-about-me-{dark,light}.png`, `training-style-about-me-keyboard-dark.png`: About
  me filled, and an ache note being typed.
- `training-style-picker-{dark,light}.png`, `training-style-picker-search-dark.png`: the picker
  sheet, and searching it.
- `training-style-page-{defaults,about-me}-{dark,light}.png`: the whole page on one tall
  Robolectric render, at defaults and with About me filled.
