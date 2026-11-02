# Test report: the exercise mannequin (#122)

Tester persona: experienced lifter (8+ years; Strong, Hevy, RP). Run on the emulator
`emulator-5554` (1280×2856, software rendering), debug build `com.hyperlog.app.dev`, in the running
program session (Day A, Barbell Back Squat added ad hoc, so its sheet has no prescription). Timings
come from screen recordings at 10 fps. The emulator renders slowly, so treat load times and
smoothness as hints. Behaviour (what the gestures do, what state survives) should be the same on a
phone.

Screenshots and recordings are in the session scratchpad (`…/scratchpad/test/`).

## Tap counts

| # | Scenario | Taps / gestures | Notes |
|---|----------|-----------------|-------|
| 1 | Between squat sets: open the sheet, read the mannequin, close it | **2 taps** (name, then scrim or Back) | The text is readable as soon as the sheet is up. The stage stays empty for about 2–2.5 s after the sheet appears. A drag-down close needs a long drag (see minor 4). |
| 2 | Front view, side view, reset | **3 gestures**: one drag of ~400 px right (default to front), one of ~250 px left (default to side), one double-tap | About 0.5° per dp, so 45° is roughly a third of the stage width. The reset is instant. |
| 3 | Open the Incline DB Press sheet from the squat | **3 taps** (Next ⌃, Start, name) | Unchanged from before #122. Tapping a name in the session list doesn't open its sheet; you have to make it the current exercise. |
| 5 | Light/dark, animations removed | n/a | The theme switches while the sheet is open, with no reload. |
| 6 | Debug gallery | **3 taps** from Settings (scroll, Mannequin gallery) | 1 clip ("1 of 1"). |
| 7 | Open/close 10×, then home and back with the sheet open | 20 taps | Clean every time. No stale state and no memory growth (native heap +0.8 MB over 10 more cycles). |

## Issues

No blockers. Nothing stops me logging, and the mannequin never got in the way of the set rows or
the pad.

### Major

None observed. One risk I couldn't test is listed under spec gaps: a program-day sheet, where the
mannequin sits on top of a full prescription.

### Minor

1. **The side view hides the body behind the near plate** (scenario 2/4).
   - Did: turned the mannequin a quarter-turn from the default to see it exactly side-on.
   - Happened: the near plate covers the head, shoulders and most of the trunk for the whole rep.
     Only the legs show.
   - Why it matters: side-on is the view a lifter turns to on purpose. It shows trunk lean, bar
     over midfoot and hip depth. The turn feature invites you there and shows a plate. The ¾
     views either side are fine, so it's a narrow band. Still, it's the first place I'd turn.

2. **The stage is an empty grey box for ~2 s on every open** (scenario 1/7).
   - Did: tapped the squat name, recorded.
   - Happened: the sheet is up in ~0.5 s, then the stage sits empty for ~2.2 s before the mannequin
     appears. It's the same on every one of 20 opens; nothing is kept between opens. On the
     emulator. A phone will be faster, but it loads from scratch every time.
   - Why it matters: between sets I glance at the sheet for 2–3 s. For roughly the first half of
     that glance, the top third of the sheet is a blank card. It doesn't block anything, because
     the name, note and video button are already there, but it reads as "something's loading"
     every single time.

3. **Coming back to the app flashes the stage empty** (scenario 7).
   - Did: sheet open, front view, Home, then back into the app.
   - Happened: the old frame shows, then the stage goes empty for ~0.4 s, then the mannequin
     reappears and restarts the rep from the top. The turned view is kept, which is good.
   - Why it matters: it's the screen-lock case. During rest the phone locks, I unlock, and the
     sheet blinks. Small, but it looks like a glitch.

4. **Closing the sheet by dragging down takes a very long drag** (scenario 2; not caused by
   the mannequin).
   - Did: vertical drags of 400 and 600 px (slow and fast) on the mannequin, the text and the drag
     handle.
   - Happened: the sheet follows a little and snaps back. Only a ~1400 px drag from the handle
     closed it. The Incline DB Press sheet, which has no mannequin, behaves the same, so this is the
     sheet and not the stage. It may also be how the emulator reports swipe speed.
   - Why it matters: a tired thumb flicks down to dismiss. If that doesn't work on the phone
     either, I'd use Back or the scrim. Worth a check on the real phone.

5. **Nothing tells you the mannequin turns** (scenario 2).
   - Happened: there's no hint, icon or first-time nudge. I found turning only because the brief
     told me.
   - Why it matters: low stakes, since it's a "which movement" cue. But the side view is where a
     lifter gets something extra, and most people will never find it.

6. **Debug gallery: no way to tell where to swipe to page** (scenario 6, debug only).
   - Happened: with one clip there's nothing to page. A horizontal swipe on the stage turns the
     mannequin, and a swipe below it does nothing. With more clips, paging has to happen outside
     the stage, and nothing shows that.
   - Why it matters: only to whoever reviews clips. Fine for now.

### Movement notes (scenario 4)

It reads as a back squat at first sight, from the default ¾ view, in both themes and when held
still. Things a lifter would notice:

- **Bar position:** high-bar on the traps, behind the neck, grip just outside the shoulders, elbows
  down and back. Believable.
- **Depth:** very deep, hips well below the knees, which matches the note ("as deep as you can").
  Good. Someone who squats to parallel might read it as "ass to grass is the standard".
- **Trunk:** fairly upright, knees well forward over the toes, a high-bar/Olympic style. Consistent
  with the bar position. The side view is blocked (minor 1), so I couldn't judge the lean properly.
- **Feet:** about hip-width, toes slightly out, heels down throughout. A touch narrow for most gym
  squatters, but not wrong.
- **Head:** at the bottom the head pitches down noticeably, chin toward the chest. It's the one
  thing that looked off to me. Most coaching wants a neutral neck. It's distracting more than
  wrong.
- **Knees:** from the front they track out over the toes with no cave. Good.
- **Plates:** big full-size plates. They dominate in light mode (near-black against the pale
  stage), but the figure stays readable.

### Things I checked that behaved as specified

- **Diagonal drags.** Shallow (~25°) and 45° diagonals turn the mannequin and never move the sheet.
  A steep diagonal (~65° from horizontal) and pure vertical drags go to the sheet (it follows, then
  snaps back). A drag never closed the sheet when I meant to turn it, and never turned it when I
  meant to close it.
- **Double-tap reset.** It works with and without animations. (My first tries failed because adb
  fired the two taps 30 ms apart, faster than a double-tap. That's not a bug.)
- **Animations removed.** On the next open it holds still at the bottom of the rep, and it still
  turns and resets. Changing the setting while the sheet is open takes effect only on the next
  open. That's fine.
- **Fresh view on each open.** Reopening the sheet starts from the default view again.

## What works and must not be lost

- **It's instantly recognisable.** The default ¾ view says "back squat" in under a second with no
  reading. For a new lifter, or a program full of unfamiliar names ("2-Grip Lat Pulldown"), this
  is the right cue.
- **It never touches logging.** It lives in the sheet, the sheet still closes in one tap, and the
  pad and set rows behind it are exactly as I left them.
- **The text doesn't wait for the 3D.** Name, equipment, note and video button are readable while
  the stage loads.
- **Gestures don't fight the sheet.** Turning is forgiving of sloppy diagonals, and vertical drags
  still go to the sheet.
- **Held still at the bottom with animations removed.** The bottom position is the most
  informative frame, so this was a good choice.
- **Exercises without a clip are untouched.** The Incline DB Press sheet looks exactly as before:
  no empty stage and no placeholder.
- **It's cheap to open and close.** 30 opens with flat memory, and it stops when you leave the
  app and resumes when you come back.

## Spec gaps

1. **The mannequin against a full prescription.** I could only test an ad-hoc squat, whose sheet is
   short. In a program day the sheet also has the estimate, every set's target, the rest and two
   notes. The Incline DB Press sheet already fills the screen without a stage. Add the stage's
   ~1000 px on top and the sets, rest and notes will be pushed down or below the fold. Between
   sets, the targets and rest are what I open the sheet for. The spec puts the mannequin "at the
   top, above the note", but doesn't say whether the prescription must stay visible without
   scrolling, whether the sheet scrolls, or whether the stage shrinks when the content is long.
   This needs a rule and a check on a program-day exercise with a clip.
2. **Prescription tempo against the mannequin's tempo.** The spec deliberately ignores a tempo on
   the prescription. A lifter on a program that prescribes a 3 s eccentric will see the mannequin
   do the default tempo and may wonder which one is right. Consider saying so in the UI or the
   spec, or at least confirm it's intended for programs that set tempos.
3. **Which views must be readable.** The spec says a drag turns the mannequin but not what should be
   visible from the side (minor 1). If the side view is meant to be useful, say so.
4. **A load-time budget.** The spec says the stage shows while loading. It doesn't say how long
   that is acceptable for, or whether a clip opened earlier in the session should come back
   faster.
5. **Discoverability of turning.** The spec has no hint that the mannequin can be turned. Decide
   whether one is wanted (minor 5).
6. **Whether a turned view should persist.** Today it resets on every open and survives
   home/back. That seems right to me, but the spec doesn't say.

## Taste questions for the owner

- Once I know the movement, the mannequin is a third of the screen I see on every sheet open, with
  no way to collapse it. Is that acceptable for exercises a lifter has done 50 times, or should it
  shrink or hide?
- The head drop at the bottom of the squat: keep or fix?
