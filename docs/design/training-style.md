# Training style

Settings → Coach → **Training style** (SPEC, Coaching → Training style): one page holding the
training targets, How I train and About me. Every field is edited in place and kept as it
changes. The one exception is How I train, which is kept with Save. A sub-screen: back arrow, no
navigation bar.

## Principles

- **One tile per setting.** Each setting is a tile of a `SegmentedList` group on
  `surfaceContainer`. Its title (bodyLarge) and one short line saying what it means or holds
  (bodyMedium, onSurfaceVariant) sit on the 28 dp text edge, with its control 12 dp under them.
  The tiles carry no symbol circles, so every title starts on the same edge.
- **Three groups, named with `GroupHead`:** Training targets, How I train, About me. About me
  starts with one line under its head: every field is optional.
- **Selection is a filled container.** Two to four short options use a `ConnectedChoice` (Goal,
  Volume, Across a block, Frequency). Longer options, one per line (Failure, Where are you now?),
  are rows whose picked one is a `secondaryContainer` pill with its radio on. Chips (Age, Aches
  and injuries) pick in `primaryContainer`, as `selectedChipColors` does elsewhere.
- **The line under a title follows the choice.** Volume shows the range it holds
  (`8–14 work sets per muscle a week`), Across a block and Failure explain the picked option, and
  How I train says whose text it is.
- **Numbers sit in a right-hand column.** Ranges are two 80 dp fields with a centred figure and
  an en dash between them, after a label on the text edge (Sets a week, Compounds, Isolation).
  A range that isn't whole turns both fields red, shows its rule under them, and keeps nothing.
- **Save stays in reach.** While How I train differs from the kept text, a bottom strip
  (`BottomActions`, `surfaceContainerHighest`) holds "How I train isn't saved", Discard and Save,
  and rides on top of the keyboard. That way Save is in reach wherever the cursor is in a long
  text.

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
 ├ Across a block   (Steady)[ Rising ]           ┤
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
 ╭ Hyperlog's default, read in every check-in    ╮    "Your own words, …" once edited
 │ ┌───────────────────────────────────────────┐ │
 │ │ - Form comes before load. Every rep …     │ │    grows with the text, min 4 lines
 │ └───────────────────────────────────────────┘ │
 │                              Back to default  │    once edited; its label on the field's edge
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

 ╭───────────────────────────────────────────────╮    while How I train is unsaved,
 │ How I train isn't saved     Discard   (Save)  │    over the keyboard
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
- **A wrong range** (an end outside 1–40, or 1–30 for reps, or the fewest above the most): both
  fields red, the rule in `error` bodySmall under them, nothing kept. The last whole range stays
  in force.
- **How I train** shows the default with its markdown's wrapped lines joined, so it wraps to the
  screen; your own text shows as you wrote it. A save keeps the text as shown. The strip's Discard puts back the kept text, and Save
  keeps the typed text and closes the keyboard. Save is off while the field is empty.
  **Back to default** shows once the text is your own, and asks first: an `undo` symbol,
  "Back to the default?", "Your own words are replaced by Hyperlog's default text.",
  Cancel / Back to default. Leaving the page (Back) with the strip up drops the unsaved text.
- **Options and chips clear on a second tap** in About me (Age, Where are you now?), since every
  field there is optional. Failure always holds one.
- **Aches and injuries:** a picked area adds a one-line note field labelled with the area.
  Unpicking the area drops the area and its note.
- **Exercises to avoid and to favour:** Add opens the picker sheet, which leaves out hidden
  exercises and ones already on either list. A pick closes it and lists the exercise on its tile
  with a × (TalkBack: "Remove <name>"). Add is named "Add an exercise to avoid / favour" for
  TalkBack and the tests.
- **The keyboard** pushes the page up (imePadding). A focused field scrolls into view above it.
  The unsaved strip rides on the keyboard.

## Screens

From the emulator (1280 × 2856, text at 115 %), 35 %. The phone was in use by another worker:

- `screens/training-style-settings-row-{dark,light}.png`: the row in Settings' Coach group.
- `training-style-defaults-{dark,light}.png`: the top of the page with every default.
- `training-style-custom-volume-{dark,light}.png`: Custom with 8–50, the error, the number keyboard.
- `training-style-unsaved-{dark,light}.png`: How I train being typed, the strip over the keyboard.
- `training-style-edited-{dark,light}.png`: your own text kept, Back to default under it.
- `training-style-default-dialog-light.png` (emulator), `training-style-default-dialog-dark.png`
  (Robolectric render).
- `training-style-about-me-{dark,light}.png`, `training-style-about-me-keyboard-dark.png`: About
  me filled, and an ache note being typed.
- `training-style-picker-{dark,light}.png`, `training-style-picker-search-dark.png`: the picker
  sheet, and searching it.
- `training-style-page-{defaults,about-me}-{dark,light}.png`: the whole page on one tall
  Robolectric render, at defaults and with About me filled.
