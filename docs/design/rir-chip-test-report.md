# RIR chip test report (#43)

Tester: experienced lifter persona (`docs/design/tester-brief.md`). Run on the phone with the dev
build (`com.hyperlog.app.dev`) from `rir-chip` HEAD, installed 16 Oct 19:15. Dark mode first (the
phone's setting), then light, then dark restored. Screenshots are in the session scratchpad
(`tester/d-*.png` dark, `tester/l-*.png` light).

## What I did

1. **Today**, dark and light: read Day A and Day B.
2. **Session**, dark: Barbell Bench Press, three work sets logged as 80 × 5 at 0, 70 × 7 at 1 and
   70 × 8 at 3. Read the why-line and the exercise sheet, then finished. In light: Barbell RDL,
   85 × 8 at 0, then read the why-line for set 2 (`80 · set 1 was [0], target [1]`), the sheet,
   and Session done.
3. **History**, both modes: the session list, today's session, a full past session (Thu 15 Oct,
   with myo-reps, drop sets and partials), and the Barbell RDL and Barbell Bench Press progress
   pages.
4. **Program**, both modes: the current week (two done days and the next day), past week 5,
   Ramp-in, and the Deload + test rule diff.
5. **Coach**: skipped. No Change is in force, and a workout review needs a reply from Claude.
6. **TalkBack**: skipped. I couldn't be sure of turning it off cleanly over adb without touching
   other accessibility settings. Instead I read the accessibility tree with `uiautomator dump`
   (see minor issue 4).

Tap counts: logging didn't change with this ticket. Three bench sets took 9 taps including one typo
fix, and Finish took 3.

## Issues

### Blocker

None. Everything I logged shows up, with the right RIR, everywhere I looked.

### Major

None.

### Minor

1. **The exercise sheet still draws targets as round rings.** Scenario 2, both modes. Tapping the
   name shows `Last time 77.5 kg × 6 [1]` as a filled square chip. Right under it, the set table
   shows each set's target RIR as a circled number, not an outlined chip. So one sheet uses three
   shapes for RIR: a filled square, a round ring, and on the screen behind it the round dots. On
   Today and Program a target is an outlined square, so this is the one place a target looks
   different. It's still readable, but "outlined = target" is the rule I learned on every other
   screen, and this sheet is where I check the prescription mid-session.
2. **In light mode a logged 1–2 is a different amber in the log than in History.** Scenarios 2 and
   3. On the logging screen the RIR dots and the pad are a bright amber (I logged a 0 in light,
   so I saw a 1 only as a prefilled dot and on the pad). In History,
   Session done and Program the same set's chip is a dark mustard brown. Red and teal shift too,
   but amber shifts the most. SPEC says the darker shade is deliberate for contrast. Still, the
   first time I saw it, "brown 1" didn't read as "the amber 1 I just tapped". Dark mode has no
   such gap.
3. **A chip in the middle of a myo-rep or partials string breaks up the set.** Scenario 3, past
   session. `45 × 10 [2] + 3 + 3 + 3` puts the chip after the activation set, so the solid block
   reads as the end of the set and `+ 3 + 3 + 3` looks like something else. On a narrow row it
   also wraps inside the set (`… [2] + 3 +` / `3 + 3`). Partials are written the other way round:
   `50 × 8 + 3p [0]` has the chip after the partials. `@ 2` in text had the same placement, but
   the chip makes it more visible. A lifter scanning for "what did I do on set 3" has to work out
   which piece the chip belongs to.
4. **Each chip may be read twice by TalkBack (unverified).** In the accessibility tree a row's
   text is `82.5 × 8 1 RIR`, which is right, but the chip also leaves a child text node `1`. If
   TalkBack walks into it, a row would read "82.5 × 8 1 RIR … 1". I didn't hear this, because I
   didn't turn TalkBack on. It needs one check with TalkBack on.
5. **The chips in a progress list don't line up in a column.** Scenario 3. On an exercise's
   progress page the chip follows the set text, so `80 × 5 [0]` and `82.5 × 8 [1]` put their chips
   at different x positions. Program's done days align the set text and chip on the right, and
   that list is quicker to scan for "where did I hit 0". This is polish.

## What works and must not be lost

- **Target vs logged reads at a glance.** Outlined = planned, filled = done. On a sweaty phone at
  arm's length I could read Today's `1 × 4–6 [1] · 2 × 6–8 [1]` faster than `@ 1`, and the colour
  tells me "this one goes to failure" (red 0) before I read the number.
- **The why-line mixing both kinds** (`80 · set 1 was [0], target [1]`) is the best thing in this
  change. It shows in one look that I overshot, and why the weight dropped.
- **Session done, a past session and Program's done days** now show effort per set without
  reading. A row of red chips means a hard day. The 0 / 1 / 3 spread from my bench sets was obvious
  in both modes.
- **The Deload rule diff** (`1 × 3–5 [1] · ↘ 2 × 6–8 [1] → 1 × 1 [1]`) reads cleanly, with the arrow
  and chips keeping old and new apart.
- **No confusion with controls.** Chips are smaller than the week selector squares and the navy
  selected chips, have no ripple, and use only the RIR colours. I never tried to tap one. The navy
  accent is never used for a chip.
- **The number is always there**, so colour alone never carries the meaning. A black number on the
  filled chip stayed readable on red, mustard and teal in light mode.
- **A chip never wrapped away from its set.**

## Spec gaps

- **The prescription sheet's set table** isn't covered by the chip rule. SPEC says a target is an
  outlined chip "wherever the app shows set text", but this table shows a target RIR as a ring.
  The spec should say whether the sheet keeps the logging screen's ring or uses the chip.
- **Chip placement in multi-part sets** (myo-reps, partials, drop sets) isn't specified. Should
  the chip follow the whole set or its first segment, and may a myo-rep string wrap inside itself?
- **SPEC still writes `12, 12, 11 @ 2`** in the why-line example (Coaching, "Every suggestion says
  why"), next to `[2]` a line earlier. It's only notation, but it's the one `@` left in a UI example.
- **Light-mode shade parity with the logging screen's dot.** SPEC says the fill is "like its dot"
  and in "the ring's shades". On the phone, the logging screen's logged dot is the bright shade and
  the chip is the dark one. One of the two should match the other, or the spec should say they
  differ.
