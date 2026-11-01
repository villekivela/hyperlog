# Bench angle test report (#115)

Tester: experienced lifter persona (`docs/design/tester-brief.md`). Run on the phone with the
installed dev build ("DEV Hyperlog", `com.hyperlog.app.dev`), dark mode, on 1 Nov 2026. The phone
lay flat on the desk the whole time, so the live reading was always 1°. The measuring states
(on target from the phone, over, waiting for the sensor) come from the renders in
`docs/design/screens/bench-angle-*.png`, and each finding below says whether it comes from the
phone, the renders or the code. Screenshots are in the session scratchpad under `tester/`.

## What I logged (DEV data)

- **Day A** (Full Body, Kalliokoski, started 21:01): Incline DB Press set 1, 25 × 10 at 2 RIR. Bench
  angle set to 30°, then 45°. DB Lateral Raise swapped for Seated DB Shoulder Press, nothing logged
  on it. Finished (69 min), so the unlogged exercises were left out. Afterwards I corrected the
  angle from History, 45° → 40°.
- **Exercise library**: Lying Reverse DB Flye now has Adjustable bench on.
- **Ad-hoc session**: added Incline DB Press (angle 40°, then swapped for Seated DB Shoulder Press),
  Lying Reverse DB Flye, DB Bench Press and Incline Machine Press. Nothing logged, so Finish dropped
  it and nothing went to History.

## Tap counts

| Scenario | Taps | Notes |
|---|---|---|
| 1. Set the bench, then log a set | 4 + 3 | Open row, `3 0`, ✓. No earlier angle existed, so there was nothing to match and I typed 30. With the phone on the backrest it would be 2 (open, ✓). The set: `1 0`, ✓. |
| 2. Bench slipped, change to 45 | 4 | Open, `4 5`, ✓. |
| 3. Type 95, fix it | 8 (+1 dead) | Open, `9 5`, ✓ (dimmed, does nothing), ⌫ ⌫, `4 5`, ✓. |
| 4. Swap to Seated DB Shoulder Press | 4 + typing | Before any set: ⋮, Swap exercise, search, pick. After a set is logged, Swap is gone (as specced). From the session list it's 7. |
| 5. Correct the angle from History | 7 | History, session, exercise, Bench, `4 0`, ✓. From Session done it's 5. Finish itself took 3. |
| 6. Mark an exercise adjustable | 5 + typing | From Settings: Exercise library, search, result, switch, Save. |
| 7. Read the sheet at arm's length | 0 | Judged on the phone and the renders. |

## Issues

### Blocker

None. Every angle I set, changed or corrected stuck and showed where I expected it.

### Major

1. **Reopening the sheet hides the angle you already set, and ✓ overwrites it with the phone's
   reading.** Scenario 2, phone. I saved 30°, logged a set, then tapped Bench again. The sheet said
   `1° · From the phone`. The 30° I saved two minutes earlier appears nowhere on the sheet, only
   behind it in the table. The dashed outline and the dot line compare against last session, not
   against what I set today. In the gym this goes wrong two ways. I open it to check, the phone is
   in my hand or on the floor, I tap ✓ out of habit and 30° becomes 12°. Or the bench slipped and I
   want to know by how much: "you set 30, it reads 27". Right now I'd have to remember 30 myself.
   The past-session sheet does start from the saved angle, so the two sheets behave differently.
   (The code confirms it: while measuring, the saved value is ignored.)

2. **✓ saves whatever the phone reads at the moment of the tap.** Scenario 1 and 7, from the code;
   I couldn't tilt the phone to see it. The reading is smoothed but follows the phone within a
   fraction of a second. I set the bench, then reach for the phone. Half the time I lift it before
   tapping ✓, because that's how you tap a phone. The number then shows whatever angle my hand is at
   and saves it silently. That's the one number this feature exists to keep. Something that holds
   a steady reading, or saves the value from just before the phone moved, would make the phone path
   trustworthy. Until I trust it, I'd type the number.

### Minor

1. **Off target has no colour, only green or not.** Scenario 7, phone and renders. On target, the
   number, backrest and dot all go green, and I can see that from a metre away. Off target, the
   number is plain white (black in light mode). Whether I'm over or under is only in the 14 sp line
   (`11° over 30°`), which I can't read at arm's length out of breath, and in the dot and the
   dashed outline. The dashed outline helps the most, because I can see which way the backrest has
   to go. The 45 sp number is big enough to read from where I stand.

2. **At 95° the backrest disappears from the drawing.** Scenario 3, phone and the too-steep render.
   Only the seat is left. It looks like a rendering bug rather than "that's not an angle". The red
   number and `Up to 90°` make the point fine without the backrest vanishing.

3. **The first-time Bench cell is just an empty box.** Scenario 4 and 6, phone. On Seated DB
   Shoulder Press and Lying Reverse DB Flye (no angle yet), the row is an empty box right next to an
   empty kg box. Nothing says it measures anything. I knew because I read the spec. Someone at a new
   exercise would skip past it.

4. **The angle sits under the `kg` header.** Every scenario, phone. `30°` in the kg column reads as
   a weight for a split second. The degree sign saves it, but the "Last" column for that row is
   empty while last time's angle shows in the kg column.

5. **The angle shows nowhere outside the exercise's table.** Scenario 5, phone. Session done, the
   History session summary and the session list show sets but no angle. When I'm at a different gym
   and want to know what I ran last week, I have to drill into the exercise.

6. **No way to clear an angle.** Scenario 3, phone. If I set one on the wrong exercise, ⌫ to empty
   goes back to the live reading and ✓ saves that. The spec says setting it is optional, but once
   set it can't be unset.

7. **Back or a swipe throws away a typed angle without saying so.** Scenario 3, phone. I typed 48
   and pressed Back: 45 stayed. That's the right outcome most of the time, but a stray swipe on a
   sweaty screen loses what I typed.

8. **The seed misses some bench lifts, and the library doesn't show the flag.** Scenario 6, phone
   and `exercises.json`. Lying Reverse DB Flye (chest down on an incline), DB Spider Curl, DB
   Preacher Curl done over the backrest, 1-Arm Side-Lying DB Lateral Raise on an incline, Seated
   Cable Flye and a seated Overhead DB Triceps Extension are all usually done on an adjustable bench
   and aren't flagged. The library list doesn't mark flagged exercises, so finding the gaps means
   opening each one.

## What works and must not be lost

- **One tap on the whole row** opens the sheet straight onto the keypad. Typing and saving is 4
  taps, the same as fixing a weight.
- **Last time's angle shows muted in the table**, so I know my target before opening anything. It
  skips sessions where I didn't set one (code), so one lazy session doesn't lose it.
- **The dashed outline of last time's backrest** behind the live one. Of everything on the sheet,
  that's what I'd use while cranking the pin, because it shows which way to move.
- **Green on target** across number, backrest and dot. Readable at a glance.
- **Typing is forgiving.** Digits replace the reading, a third digit is ignored, ⌫ to empty goes
  back to the phone, and over 90 stays on screen in red with ✓ dimmed.
- **The tilt doesn't care how the phone lies** (code): face up or down, either way along the
  backrest. And the session keeps the screen on, so it won't lock mid-adjustment.
- **The right exercises get it.** DB Bench Press has no row, Incline Machine Press has no row, and a
  machine has no switch in the editor. Switching an exercise to Machine and back keeps the switch on.
- **A swapped-in exercise starts with its own angle**, not the old one's. A shoulder press and an
  incline press shouldn't share a number.
- **History corrections start from the saved angle**, typing only, the first digit replaces it.

## Spec gaps

- **What the sheet compares against after the angle is set this session.** Last session, or the
  angle I set an hour ago? For "the bench slipped" it should be today's.
- **A cue I can feel or hear on target.** When I'm crouched at the pin, I'm looking at the pin, not
  the phone. A short buzz on entering the ±3° window would let me stop without looking.
- **Holding a steady reading** so picking up the phone doesn't change what ✓ saves (Major 2).
- **Clearing an angle** once set.
- **"The latest session's angle"** should say "the latest session that set one", which is what the
  code does.
- **Where else the angle shows**: Session done, the History summary, maybe the session list.
