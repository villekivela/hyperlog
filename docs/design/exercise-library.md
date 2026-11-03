# Exercise library: design note

Settings → Exercise library, the exercise editor, and the exercise sheet a session opens from the
exercise name. It extends `logging-screen.md`: same margins, type scale, symbols and theme roles.
Screens: `screens/library.png`, `exercise-editor.png` (phone, dark, Ink and violet, retaken in #58; `phone-dark-library-end.png`, from the emulator at 115 % text, shows the list's 96 dp foot keeping the last row clear of New exercise); `light-` and `dark-library.png`,
`-exercise-editor.png` (renders from `AppRenders`).

## Principles

1. **A segmented list.** One segment per exercise: the name, then one muted line (equipment and
   primary muscles). Rows sit on the 28 dp margin with no containers.
2. **Search comes first.** A pill-shaped search field sits under the title, before the list. Text
   uses the system keyboard; the in-app pad is for numbers only.
3. **Hidden is quiet, not gone.** Hidden exercises are listed last under a "Hidden" label, in
   `outline`. They stay in past sessions.
4. **Every field on one page, as choices.** Equipment, pattern, grip and muscles are picked rather
   than typed, so the coach can rely on them. Save stays disabled until the exercise is complete.
5. **The exercise sheet answers "how do I do this?"** The note, then the video one tap away, with
   its start time on the button.

## Tokens

| Token | Value | Use |
| --- | --- | --- |
| Margin | 24 dp | Left edge of names, labels and fields |
| Search field | 56 dp, full radius, 12 dp side inset (the cards' margin) | surfaceContainerLowest (a well, as the system's search fields), no indicator line; its text lines up with the 24 dp column inside the pill |
| Library row | 72 dp min | Name bodyLarge onSurface; summary bodyMedium onSurfaceVariant |
| Hidden row | both lines in `outline` | Under a labelLarge "Hidden" label, 24 dp above it |
| Editor section | labelLarge onSurfaceVariant label, 8 dp to its control, 24 dp between sections | |
| Picker row (session) | 56 dp, bodyLarge | Name only: names carry the implement |

## Layout

```
 ←  Exercise library
 ( 🔍  Search 173 exercises            )     ← pill, surfaceContainerLowest
 1-Arm Cable Pulldown
 Cable · Lats
 Barbell Bench Press
 Barbell · Chest
   …
 Hidden
 DB Curl                                     ← outline
 Dumbbell · Biceps
                           [ + New exercise ]  ← extended FAB

 ✕  Edit exercise                     Save
 [ Name: Pec Deck                    ]
 Equipment
 (Barbell)(Dumbbell)(✓ Machine)(Cable)…       ← filter chips, the selected one checked
 Two dumbbells                          (●)   ← a switch row, dumbbell exercises only
 Adjustable bench                       (○)   ← the same row, any equipment but a machine
 One in each hand: the estimate counts both…
 Movement pattern
 [ Chest fly                       ▾ ]       ← a field-wide menu of the 24 patterns, current one checked
 Grip demand
 [  None  |  Low ✓  |  High  ]               ← segmented
 Primary muscles
 (Chest ✕) (+ Add)                           ← input chips; Add lists the rest
 Secondary muscles
 (Front Delts ✕) (+ Add)
 [ Technique video: https://youtu.be/… ]
   Opens at 2:31
 [ Note …                            ]
 ⊘ Hide exercise

 Session, exercise sheet (tap the name):
 Cable Lateral Raise                          headlineSmall
 Cable · Side Delts                           bodyMedium onSurfaceVariant
 ▤  Raise your arm "out", not "up". …         sticky_note_2 + bodyLarge
 [ ▶ Technique video · 10:53 ]               filled tonal
```

## Component states

- **Search, empty:** placeholder counts the shown exercises ("Search 173 exercises"). **Typing:**
  the list filters on every key; a close symbol clears it. **No match:** one muted line, "No
  exercise matches "…"". The query survives opening an exercise and coming back.
- **Search order:** names starting with the query, then names holding every word, then exercises
  that only match through equipment or a muscle ("lateral" lists the lateral raises before the
  triceps work for the lateral head). A new query scrolls the list back to the top.
- **Row:** tap opens the editor. Hidden rows look muted but open the same way.
- **Editor, new:** title "New exercise", every field empty, Save disabled. **Complete:** Save
  enabled. **Edit, unchanged:** Save disabled until something changes. **Video field:** "Opens at
  2:31" or "Opens in YouTube" under a valid link; "Not a YouTube link" in error colour otherwise,
  and Save is disabled.
- **Hide:** applies at once and the editor stays open. The button turns into "Show exercise
  again", with one line saying what hiding does.
- **Close with changes:** "Discard changes?" (Keep editing / Discard). Back does the same.
- **Exercise sheet:** the note row and the video button are left out when there is none.

## Interaction rules

- Settings shows "Exercise library" with the count of exercises offered.
- Save returns to the list, which still shows the search that led there.
- The session's Add exercise list (screen and sheet) has the same search field and leaves hidden
  exercises out.
- The Technique video button opens the video at the timestamp in a Custom Tab, inside Hyperlog's
  task, so Back returns to the session (tester report 6, M2). Without a Custom Tabs browser it
  opens the YouTube app, else any browser.

## Mannequin

The clay mannequin at the top of the exercise sheet (SPEC, Technique videos → The mannequin), and
the debug gallery that pages through every clip. Screens: `screens/mannequin-sheet-light.png`,
`-sheet-dark.png`, `-sheet-turned-dark.png`, `-sheet-still-dark.png` (animations removed: held at
the bottom), `mannequin-gallery-dark.png` (emulator, #122).

### Principles

1. **A cue, not a lesson.** The stage is the sheet's first card and the eye lands on it, but it
   keeps the size the owner approved in the prototype; the name and the note follow at once.
2. **A card of the sheet.** The stage is a card on the sheet like the machine row and the
   prescription: `sheetCard` tone, 32 dp corners, 12 dp from the sheet's edges. The text stays on
   the sheet's 24 dp edge below it.
3. **The body is the subject.** It holds the most contrast with the stage; the joint rings are a
   step darker than the body, and the equipment sits apart from it without outshining it. The
   kits read as real machines by tone and roughness by part, not by colour or gloss.
4. **Clay shows its form.** A key light from the side of the camera models the body; a light from
   the camera's direction flattens it into a cut-out. The key moves with the camera, so every turn
   is lit alike.
5. **Grounded.** A soft floor shadow sits under the feet, stronger in dark, where black barely
   shows on charcoal.

### Tokens

| Token | Light | Dark | Use |
| --- | --- | --- | --- |
| Stage | `surfaceContainerHighest` #E4E4E8 | #2E2E33 | `Tones.sheetCard`, `CardShape`, 5:4, 12 dp in from the sheet's edges |
| Body | #BABAC1 | #C4C4CA | the mannequin's shell |
| Joint | #9A9AA1 | #A8A8AF | the rings between its pieces, a step under the body |
| Frame | #2E2E33 | #4A4A50 | frames and uprights, roughness 0.5 |
| Vinyl | #1B1B1E | #3A3A40 | pads, rollers and grips, roughness 0.95 |
| Plate | #8A8A90 | #8A8A90 | a stack's plates and every other weight, roughness 0.8 |
| Metal | #C8C8CC | #C8C8CC | rods, bars, cables and pins, roughness 0.3 |
| Pulley | #5C5C62 | #6E6E75 | pulleys and their housings, roughness 0.55 |
| Floor shadow | 65 % | 100 % | of the baked shadow (black, 45 % at its centre, gone at the rim) |
| Key light | 2.8, casts shadows | same | 55° round to the camera's left, 50° up, turning with the camera |
| Rim light | none | 1.3, no shadows | from behind the kit, 160° round from the camera and 30° up, turning with it: a dark frame's edge |
| Sky | SH 0.36 + 0.26 · up | same | a hemisphere: bright above, dim below, filling the shade |
| Lens | 26° vertical | same | linear tone mapping, exposure 1 |
| Framing (squat) | target (0, 0.87, 0), half 1.02 m, 62° turn, 8° tilt | same | in `poses.mjs`; the plates' top and the feet sit about 8 % in from the stage's edges |
| Stage → name | 16 dp | | |

The stage's 5:4 at the cards' margin has the height of the prototype's 4:3.4 at the text margin, so
the mannequin is the size the owner approved; the stage gains air at its sides.

### Layout

```
 ──────── (handle)
╭──────────────────────────────────────╮   ← 12 dp from the sheet's edge
│                                      │     sheetCard, 32 dp corners, 5:4
│            ◯══╪══◯   (bar)           │
│              ( )                     │     key light from the camera's left
│              /|\                     │
│              / \                     │
│            ░░░░░░░   (floor shadow)  │
╰──────────────────────────────────────╯
                                             16 dp
 Barbell Back Squat                          ← 24 dp text edge, headlineSmall
 Barbell · Quads, Glutes, Hamstrings
 …the rest of the sheet

 Debug gallery (Settings → Debug → Mannequin gallery), on surfaceContainer:
 ←  Mannequin gallery
╭──────────────────────────────────────╮   ← the stage as in the sheet, 12 dp in
│              (clip)                  │
╰──────────────────────────────────────╯
 Barbell Back Squat                          ← 28 dp text edge
 1 of 1
   (swipe below the stage for the next clip)
```

### Component states

- **Loading:** the empty stage, then the mannequin fades in over 300 ms once its first frame is
  drawn.
- **Playing:** one rep in a loop, lowering, hold, lifting, hold.
- **Turned:** a horizontal drag turns it (0.5° a dp); the light turns with the view. A double-tap
  returns to the clip's opening turn.
- **Still** (animations removed): held at the bottom of the rep, drawn only when something changes.
- **Light and dark:** the colours above, swapped with the theme while the sheet is open.

### Interaction rules

- A vertical drag on the stage goes on to the sheet; only a horizontal one turns the mannequin.
- The sheet scrolls only when it is taller than the room. While it fits, a downward drag anywhere
  on it, the stage included, closes it; a scroll would take vertical drags for itself, because a
  scrolling sheet doesn't pass the drag on to the sheet once at its top (the exercise picker
  doesn't either). The stage takes about a third of the screen, so a planned exercise with a
  goal, a long note and many sets can outgrow it, and then it scrolls to its video button.
- In the gallery a swipe on the stage turns the clip; a swipe below it pages.

### The grip choice

An exercise done with a different grip set by set (the 2-Grip Lat Pulldown: set 1 wide and
overhand, set 2 narrow and underhand) has a clip per grip, and a choice under the stage switches
between them. Screens: `screens/mannequin-grip-set-1-light.png`, `-set-1-dark.png`,
`-set-2-light.png`, `-set-2-dark.png`, `-past-last-light.png`, `-past-last-dark.png` (emulator,
115 % text, #127).

Principles:

1. **It belongs to the stage.** It switches what the stage plays, so it sits under it, out to
   the stage's own edges, closer to the stage than to the name below.
2. **Each grip says its set.** `Set 1 · Wide`, `Set 2 · Narrow`: the grip is the set's, so the
   button names both, in the session's own words (`Set 2`, as the rest strip's `· Set 3`). The
   choice the sheet opens on then reads as "this is the set you're on", not as a default.
3. **One control, nothing more.** No caption or help line: the note says what the grips are, the
   mannequin shows the hands, the button says which set.

Tokens:

| Token | Value | Use |
| --- | --- | --- |
| Group | `ConnectedChoice`, 12 dp in from the sheet's edges (the stage's edges) | on the sheet (`surfaceContainerLow`): the others in its well, the picked one `primaryContainer` |
| Label | labelLarge, `Set n · Grip` | one line; `Set n` is the grip's place in the clip's list |
| Stage → group | 8 dp | half the 16 dp to the name, so the group reads with the stage (a 4 dp spacer: the buttons' 48 dp touch target adds 4 dp round their 40 dp) |
| Group → name | 16 dp | the stage's own gap to the name (a 12 dp spacer, for the same reason) |

```
╭──────────────────────────────────────╮   ← the stage, 12 dp in
│              (clip)                  │
╰──────────────────────────────────────╯
                                             8 dp
(████ Set 1 · Wide ████)( Set 2 · Narrow )   ← connected group, the stage's width
                                             16 dp
 2-Grip Lat Pulldown                         ← 28 dp text edge
```

States:

- **Set 1 being done** (and its warm-ups): opens on `Set 1 · Wide`.
- **Set 2 being done:** opens on `Set 2 · Narrow`.
- **Past the last grip** (a third set in an ad-hoc session, or the exercise done): opens on the
  last grip, its label unchanged (`Set 2 · Narrow` while set 3 is done): the label names the
  grip's place in the exercise, not the set on the table.
- **Outside a session:** opens on the first grip.
- **Light and dark:** the group's colours follow the theme with the sheet.

Interaction: a tap picks a grip and the stage loads its clip (it fades in as on opening); the pick
holds while the sheet stays open, and the sheet opens on the set's grip again next time. Turning
the mannequin keeps working on the stage; the group takes only taps.

## Critique log

- **Pass 1** (phone): a video symbol on 138 of 173 rows was noise, not state: removed. The title
  "Exercises" didn't match the Settings row: now "Exercise library". Typing a query kept the old
  scroll offset, hiding the best matches above the fold: the list now jumps to the top. "lateral"
  buried the lateral raises under triceps exercises (the lateral head): name matches rank first.
  Filter chips showed selection only by fill, weak on a monochrome theme: the selected one has a
  check, like the grip segments. The pattern menu was narrower than its field: now field-wide.
- **Pass 2**: the pattern menu didn't show the current pattern: it's checked now. The list, search,
  sheet and video were otherwise right on the phone; the Technique video button opened YouTube at
  10:53 as labelled.

Expressive pass: the screen sits on surfaceContainer, each exercise a segment; the editor's grip
demand is a connected button group and its chips pick in primaryContainer.

- **Mannequin pass** (emulator, #122): the light body (#D2D2D8) was lit from almost the camera's
  own direction (the sun 22° off the view) to a near-white cut-out on a near-white stage: the key
  now comes 55° to the camera's left and turns with it, the sky is a little dimmer so the shade
  models the form, and the body is a step darker (#BABAC1). The dark plates (#C8C8CE) were the
  brightest thing on the stage, brighter than the body: now a muted #55555C under a lighter body
  (#9A9AA2), and the joint rings sit a step under the body in both themes, as they didn't in dark.
  The floor shadow barely showed in either theme: darker at its centre, at full strength in dark.
  The stage sat inside the text margin, narrower than the sheet's other cards: it now runs to the
  cards' margin at 5:4, the same height as before. The feet sat 6 % above the stage's bottom edge,
  crowding their shadow: the camera now looks 5 cm lower. Pass 2 softened the light plates (#3A3A40 → #48484E), which
  outweighed the body, and checked the turned, still and gallery states. A scroll added to the
  sheet for long content now turns on only when the content outgrows the room: always on, it
  stopped a downward drag on the stage from closing the sheet.
- **Kit detail and tones** (owner, #127, from the machines prototype's "B · tones"): every kit is
  rounded tubing on base plates, soft pads, a stack of separate plates on guide rods with a pin,
  pulleys in housings and shaped bars with grips, in five tones by part. In dark the body went
  lighter (#9A9AA2 → #C4C4CA), so it stays the subject against the lighter frame. The light body
  stays #BABAC1: the issue quoted the prototype's #D2D2D8, the near-white this note's first pass
  darkened. Dark vinyl was the dark stage's own tone (#2E2E33); the owner took it a step up to
  #3A3A40 so a pad stands apart from the stage. Every weight stays in the plate tone, and the
  pressdown's stack stays 20 cm back.
- **Kit detail, pass 2** (owner, #127): only the lat pulldown's tower read as the prototype's B, so
  the rest came up to it: the standing tower's low pulley, the chest press's housings, brace and
  capped grips, the leg press's framed base and roller carriage, and the benches' wheels, handle
  and adjustable strut. Rendered shaded in light (`mannequin-kits-before.png`, the #123 kits;
  `mannequin-kits-after.png`, these).
- **Grip choice** (emulator, 115 % text, #127): the first build was a plain `Wide | Narrow` group
  12 dp under the stage, which showed 16 dp above the buttons (their touch target adds 4 dp) and
  20 dp below: it floated halfway between the stage and the name, and said nothing about why the
  sheet opened on one grip. Pass 1 named the set on each button (`Set 1 · Wide`) and closed the
  gap to 8 dp shown; pass 2 measured the gaps on the screenshot (8 dp above, 16 dp to the name),
  checked set 1, set 2 and a set past the last in light and dark, and that a tap loads the other
  clip. The group is told it sits on the sheet (`surfaceContainerLow`), not on a sheet card; the
  well comes out the same, but it now says what it means.
