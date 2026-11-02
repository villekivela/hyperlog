# Training style test report (#132)

Tester: experienced lifter persona (`tester-brief.md`), on the emulator (`emulator-5554`, 1280 ×
2856, text at 115 %) with the debug build from `app/build/outputs/apk/debug/app-debug.apk`
(`com.hyperlog.app.dev`), on 2026-11-02 around 15:50–16:10. Screens: Settings → Training style.

The app opened on a running Day A session with nothing logged. Finish was the only way back to
Today, and its dialog said the session would be dropped and nothing would go to History, so I
finished it. Today showed no "Set how you train" card, so an earlier run had already done or
dismissed it.

State left behind: Both, 10–16 sets a week, Rising, frequency 2, failure on any lift, calibration
off, compounds 6–8, isolation 10–15. How I train is back to the default. About me: 30–39,
progress takes months, coming back from a break, Elbows and Knees with notes, EZ-Bar Skull
Crusher avoided, Hack Squat favoured, and an Anything else line. The emulator is shut down, in
light mode.

## First impression

It reads like a coach's intake form written by someone who lifts. "How fast you progress, not
years trained" is the best line on the page: three on-and-off years and three steady ones really
are different lifters, and no other app I've used asks it that way. Failure spells out what
"machines" means (cable, Smith) and where a free-weight compound stops. Calibration says why it
exists. I'd fill this in once, honestly, in about two minutes.

The numbers are where it falls down. The range fields fight you, and the way they keep values can
leave the coach holding a range I never meant.

## Tap counts

Keypad presses count as taps. Free text counts as one "typing". Scrolls are counted apart.

| # | Scenario | Taps | Notes |
| --- | --- | --- | --- |
| 1 | Today → Training style | 2 | Settings tab, Training style row. The row is the Coach group's first, visible without scrolling, and reads "Hypertrophy · 8–14 sets a week". The Coach tab has no way in. |
| 1 | Read the whole page | 0 + 6 scrolls | |
| 2 | Goal and volume: Both, Custom, 10–16 | 12 | Both, Custom, low field, ⌫, 1, 0, high field, ⌫, ⌫, 1, 6, ✓. Needed 2 more because the cursor landed mid-number (issue 2). |
| 2 | Rising, frequency 2 | 2 | |
| 2 | Any lift, calibration off | 2 + 1 scroll | One of these was a mis-tap (issue 3). |
| 2 | Compounds 6–8, isolation 10–15 | 12 + 1 scroll | Compounds low took 7, not 3, because the first try gave "65" (issue 2). The isolation row sat under the keyboard, and ✓ was the only action key, with no Next. |
| 2 | Wrong range 18–16 | 5 | Field, ⌫, ⌫, 1, 8. Red, with the rule. **1–16 kept** (issue 1). |
| 2 | Wrong range 0 | 3 | Rejected. The last whole range held. |
| 2 | Wrong range 50 in "most" | 5 | **1–5 kept** (issue 1). |
| 2 | Getting 10–16 back | 9 | You have to fix the most field first, or the low end goes red again. |
| 3 | Add a line to How I train, Save | 3 + typing, 4 scrolls | Tap at the end, Enter, typing, Save. |
| 3 | Change it, leave without saving | 2 + typing | Tap, typing, ←. Gone, with no question (issue 5). |
| 3 | Back to default | 2 | Back to default, confirm. |
| 4 | Age, Where are you now?, break | 3 | |
| 4 | Two aches with notes | 6 + typing ×2 | Chip, chip, note field, typing, ✓, note field, typing, ✓. Picking a chip doesn't focus its note. |
| 4 | Two to avoid, one to favour | 9 + typing ×3 | Add, search field, typing, pick, for each. The search isn't focused when the sheet opens. |
| 4 | Anything else | 2 + typing | |
| 4 | Remove one avoided exercise | 1 | No undo. |
| 5 | Home, back; force-stop, relaunch | 2 to get back to the page | Everything held, including an edited How I train across a kill (checked separately). |
| 6 | Dark mode | 0 | |

Scenario 2's whole set-up is about 28 taps with no slips, and it took me 32.

## Issues

### Blocker

None. Everything I set was kept and survived a kill.

### Major

**1. A wrong range keeps a number I typed on the way, not the range I had.** Scenario 2. From
10–16, I retyped the fewest as 18: ⌫, ⌫, 1, 8. The fields went red with "Each end 1–40, the
fewest not above the most", which I read as "nothing changed, 10–16 still holds". Settings then
read **"Both · 1–16 sets a week"**, and the page reopened on 1–16 with no red. The "1" on the way
to 18 had made a whole range, and the page kept it. Typing 50 in the most field kept **1–5** the
same way. A lifter fixing a typo goes through these states every time. With Rising, the coach is
now told my block starts near 1 set a week per muscle, and I'd never know. The spec asks for this
("kept as it changes, a range once both its ends are right"; "the last whole range stays in
force"), so it needs a spec change, not only a code fix. The rep ranges work the same way.

**2. The number fields put the cursor wherever the thumb lands.** Scenario 2. Tapping the middle
of "5" put the cursor before the 5. ⌫ did nothing, and typing 6 gave "65" in red. Tapping the
middle of "14" put the cursor between 1 and 4, so ⌫ left "4". Every range edit cost me 2–4 extra
taps, and those extra steps are what feed issue 1. Strong and Hevy select the whole number on
focus, so you just type the new one. Two-digit fields should behave like that too.

**3. Failure's options move under your thumb.** Scenario 2. The line above the options explains
the one picked, and it changes length: three lines for Isolation and machines only, one for Never,
one for Any lift. Picking Never pulled Never, Any lift and everything under them up about 140 px.
My next tap, aimed at Any lift, landed on the Calibration tile, and since the whole tile toggles,
it turned calibration off without my asking. I wanted it off anyway, but I only noticed because I
was looking. Another setting changing on its own is the kind of thing I wouldn't notice until a
program came back wrong.

**4. One tap on an ache chip deletes its note, per spec.** Scenario 4. I unpicked Elbows (one tap
on a chip that sits between Shoulders and Wrists and hands). The note "Left elbow, sore on skull
crushers and close-grip bench" went with it, and re-picking gave an empty field. The tile also
reflowed, so my second tap missed the chip. There's no undo. The aches are what the coach swaps
exercises around, and the note is the useful part. The spec says "Unpicking the area drops the
area and its note", so this needs a spec change too: keep the note until the page closes, or ask
before dropping it.

**5. Leaving the page drops unsaved How I train without asking, per spec.** Scenario 3. Every
other field on the page keeps itself as you go, so after a few minutes on the page you trust it.
Then the longest thing you'll write here goes the moment you tap ←. Home and back kept the text
and the strip, which is good. But ← and system Back drop it with no "Discard your changes?". I'd
rather be asked than lose a paragraph.

### Minor

6. **Notes and Anything else are one-line fields that scroll sideways.** After typing, they stay
   scrolled to the end, so the elbow note read "ı skull crushers and close-grip bench" and the
   knee note "cks on deep squats, fine on leg press". Left and right, the part that matters, were
   hidden until a relaunch. After a relaunch they show the start and cut the end. You never see a
   whole note.
7. **The picker's search isn't focused when it opens.** That's one extra tap per exercise, three
   times in scenario 4. One pick per opening also means two trips to avoid both skull crushers.
8. **Low, Moderate and High don't show their ranges until you pick one.** I only learned Low is
   4–8 by tapping it. That tap also replaced my custom range: Custom then opened on 4–8, and my
   10–16 was gone, per spec ("Custom opens filled with the current range").
9. **Goal doesn't say what it changes.** "Your lift goals sit on top of any of them" stays the
   same for all three choices. Strength didn't touch the rep ranges, which stayed at compounds
   5–10. A strength lifter would expect something like 3–6 and won't know whether they should set
   it themselves.
10. **Never and Calibration contradict each other on screen.** With Never picked, Failure reads
    "Every set stops at 1 RIR or more", and Calibration, on by default right under it, sends a set
    to 0 RIR. The spec says calibration holds under Never, but the page doesn't say so.
11. **The keyboard on the range fields.** The action key is ✓, not Next, so moving from compounds
    to isolation means closing the keyboard or scrolling, and the isolation row sits under it.
    After ✓ the cursor stays blinking in the last field (16|).
12. **× on an exercise removes it with no undo.** It takes 3 taps and some typing to put back.
13. **No route from the Coach tab.** The check-ins live there and this is their input, so I
    looked there first. Settings' row uses the same tune symbol as Coach's "Change my program"
    tile.
14. **Unpicked options look different in dark.** In light, the unpicked segments of Goal, Volume
    and Frequency are flat text. In dark they sit on near-black pills. Both read fine. It's only
    inconsistent.
15. **Autocorrect.** Gboard changed "fasted" to "faster" in Anything else. That's the keyboard,
    not the app, but the field's own example is "I train fasted", so anyone who types the example
    hits it.

### Not issues (belong to #133)

The page has no **Set it up again** and Today had no "Set how you train" card. The spec puts both
with the wizard, which is #133.

## What works and must not be lost

- **Two taps from Today**, and the row's summary ("Both · 10–16 sets a week") tells me what's set
  without opening it.
- **The explanations under each title.** They're written for lifters. Failure says exactly which
  lifts may go to 0 RIR. Calibration says it keeps logged RIR honest and never touches a barbell
  squat, bench or deadlift. "How fast you progress, not years trained" is the best intake question
  I've seen in a training app.
- **The line under Volume and Across a block follows the pick.** It reads "Work sets per muscle a
  week, your own range" on Custom, and "From the range's bottom toward its top, then a deload" on
  Rising.
- **The error state is clear.** Both fields go red, with the rule right under them. 0 and 50 are
  refused.
- **How I train's strip.** "How I train isn't saved" rides on the keyboard with Save in reach at
  the end of a long text. Discard puts the kept text back and closes the keyboard. Back to default
  asks first and then hides itself. The default text is good: I'd keep most of it.
- **Unsaved How I train survives Home**, and an edited, saved text survives a force-stop.
- **The picker leaves out what's already on either list.** EZ-Bar Skull Crusher didn't show up
  when I searched "skull" to favour something. Partial words find exercises ("hack", "upright").
- **Second tap clears** Age and Where are you now?, so "optional" really is optional.
- **Everything persisted** across Home and a force-stop.
- **Dark mode** is clean. The picked pills and chips have strong contrast, and the off switch is
  still readable.

## Spec gaps

- **When a range is kept** (issue 1). "Kept once both ends are right" takes in the states a
  keystroke passes through. It could instead be kept when you leave the field or press ✓, or fall
  back to the range from before you started typing. Whichever it is, the error state should show
  which range is in force.
- **Calibration and the deload.** The spec says the last week of a block, "unless that week is a
  deload". Rising always ends in a deload, and Steady has one. Read literally, calibration either
  never happens or lands in the deload. It probably means the last hard week, but the spec should
  say so. The page drops "unless it's a deload" entirely, so a lifter reads it as a failure set in
  the deload week.
- **Calibration under Never** should be said on the page, not only in the spec (issue 10).
- **What Goal does.** The spec says the lift goals sit on top. It doesn't say whether Strength
  changes rep ranges, rest or the main lifts, or whether Claude picks that up from the goal alone.
- **Ache notes and How I train edits** are dropped by design (issues 4, 5). Both rules could be
  changed to keep the text or to ask first.
- **Session length.** Time per session is the constraint I give any coach first, and it isn't a
  training target. I had to put "75 minutes tops" in Anything else.
- **One volume range for every muscle.** I run side delts and calves higher than chest. The
  program focus covers this per program, but there's no standing per-muscle override.
- **No preview of what Claude is told.** I'd like to see the exact "Training style" block a
  check-in sends, so I know my aches and avoided exercises came across the way I meant.
- **Aches have no severity or date.** "Sore since March, getting better" and "sharp, started last
  week" call for different coaching. That fits in the note today, but the note is one line.
