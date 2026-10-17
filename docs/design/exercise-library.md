# Exercise library: design note

Settings → Exercise library, the exercise editor, and the exercise sheet a session opens from the
exercise name. It extends `logging-screen.md`: same margins, type scale, symbols and theme roles.
Screens: `screens/library.png`, `exercise-editor.png` (phone, dark, Ink and violet, retaken in #58); `light-` and `dark-library.png`,
`-exercise-editor.png` (renders from `AppRenders`).

## Principles

1. **A segmented list.** One segment per exercise: the name, then one muted line (equipment and
   primary muscles). Rows sit on the 24 dp margin with no containers.
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
| Search field | 56 dp, full radius, 16 dp side inset | surfaceContainerLowest (a well, as the system's search fields), no indicator line; its text lines up with the 24 dp column inside the pill |
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
