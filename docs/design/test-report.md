# Test report: logging screen, round 4

Tested on the phone (`./run.sh`, latest build) as an experienced lifter, 2026-10-12. Tap counts
include every tap from the first set of the exercise to the last, and exclude closing sheets
unless noted.

Testing ended with the lock-screen test: the phone has a secure lock and stayed on the keyguard
after retries, so the state after unlocking was not checked.

## Tap counts

| # | Scenario | Taps | Notes |
| --- | --- | --- | --- |
| 1 | Whole session as planned | **20** | Bench 3 + drops 4 (2 typed reps + 2 ✓), laterals 4 (lengthened-partials set takes 2 ✓), superset 9 (LE1, LC1, LE2, 5 myo mini-sets, LC2). With prefilled drop reps it would be 16. |
| 2 | Bench: set 2 is 9 @ 1, set 3 drop set with one drop | **9** | Set 1 ✓; set 2 `9`, RIR 1, ✓; set 3 `8`, ✓; drop `7`, ✓; Finish set. |
| 3 | Laterals: go up from 5.7, set 3 is 12 + 6 partials | **12** | Set 1: kg, 7.9 rung, →, `10`, ✓ (6). Set 2: `10`, ✓ (3). Set 3: ✓ (switches to P), `6`, ✓ (3). A weight change alone is 4 taps. |
| 4 | Equipment taken: do another exercise, come back | **+2** | Next line, Start. Coming back is automatic, apart from the bug in M1. |
| 5 | Superset with myo 15 + 4 + 4 + 3 | **10** | LE1 ✓, LC1 ✓, LE2 `15` ✓, ✓, ✓, `3` ✓, LC2 ✓. +2 to recover from M1 when the superset was started out of order. |
| 6 | Mistype 255 for 25, then fix it | **6 to make, can't fix** | kg, `2 5 5`, →, ✓. The logged set can't be edited. Set 2 inherits 255, which takes 5 taps to correct. |
| 7 | Rest: extend, skip, lock | Extend +30 s: 4. Skip: 2 | Pill, +15, +15, dismiss. Pill, End rest. Lock: see B2. |
| 8 | Prescription, note, video | 1 (sheet), 2 (video) | Video opens YouTube. The session survives the trip, but picture-in-picture covers ✓. |

## Issues

### Blocker

**B1. A logged set can't be corrected (scenario 6).** I logged bench set 1 at 255 kg. Tapping the
done row's kg, tapping the set number, long-pressing and swiping the row all did nothing. The
only way out is "Reset session". In a gym, a typo is noticed one or two sets later, often
mid-rest. A wrong set that can't be fixed corrupts the e1RM and the next session's suggestion.
Every app I've used (Strong, Hevy) lets you tap a done set and retype it.

**B2. The rest-end buzz doesn't fire with the screen off (scenario 7).** I shortened rest to
about 15 s and pressed power. When rest ended, the vibrator log shows the prototype's vibration
as `ignored_background`. The app also posts no notification during rest. The screen stays on
while the app is open (keep-screen-on), but the phone goes in my pocket and gets locked, or
times out while I'm at the rack. I never find out that rest is over. The countdown itself is
right after coming back from the background (it uses wall-clock time). Also: with the screen on,
pulling rest past zero with −15 drops into the count-up without any buzz.

### Major

**M1. A myo set on A1 can skip the superset partner (scenarios 4 and 5).** I left laterals after
2 of 3 sets, started the leg superset from the session list, and logged LE1 and LC1. I then did LE
set 2 with myo-reps 15 + 4 + 4 + 3. When the mini-set of 3 closed the set, the app jumped to
**Cable Lateral Raise**, skipped Seated Leg Curl set 2, and started a 2:00 rest. In normal session
order the same myo set correctly goes to A2. In a gym I'd be sitting at the leg curl with the
app on a different exercise and a rest timer that shouldn't exist. It takes 2 taps to recover,
if I notice.

**M2. One back gesture loses the whole session.** A back swipe (easy to hit with a sweaty
thumb at the screen edge) closes the app, and reopening starts the session from scratch. The
"Session done" screen also has no way back: back exits and everything is gone. I know the
prototype keeps nothing, but it shows how fragile this is. The real app has to restore the
session after back, a process kill or a reboot.

**M3. The finished exercise disappears the moment its last set is logged.** After the last drop
of bench, the screen jumps to laterals straight away. I get no chance to check what I logged,
and with B1 I couldn't fix it anyway. That's fine when everything is right, but I want a beat to
see the finished table, or a way to reach it from the session list (done exercises there have
no action).

**M4. Prefills don't react to what just happened (scenarios 2, 3).**
- After 9 @ 1 on bench set 2, set 3 is still prefilled 10 reps at 25 kg.
- After 10 reps at 7.9 kg on laterals, set 2 is still prefilled 12.

So "✓ logs as planned" logs a set that's unlikely to happen, and I have to retype reps on most
sets after a weight change. SPEC says the next set's weight follows the RIR gap; reps should
follow too, at least as far as "same as last set".

**M5. No sanity check on weight (scenario 6).** 255 kg on dumbbells was accepted, though it's ten
times last time and off the dumbbell ladder. It then spread to sets 2 and 3, and the drops
became 50 and 35. A "that's 10× last time" nudge, or snapping to the ladder, would catch a fat
finger before it's logged.

**M6. Picture-in-picture video covers ✓ (scenario 8).** Technique video opens YouTube. Coming
back leaves the PiP window over ✓, + and 9, so logging the next set means dragging the video
away first. The note and the "why this weight" line in the prescription sheet are good.

### Minor

- **Hidden pad leaves no way to log** without reaching up to tap a cell in the row (see open
  question 5).
- **Drop reps start empty:** 2 extra taps per drop with fried hands (see open question 2).
- **Myo mini-sets keep going while ✓ is mashed:** prefilled at target, so ✓ ✓ ✓ logs mini-sets
  up to the maximum (5 here). Nothing shows how many are left; "Mini-set 3" could read
  "Mini-set 3 of 5".
- **Lengthened partials:** if I hit ✓ twice without typing partials, the set logs with none,
  silently. That's acceptable, but the record then loses the technique.
- **Summaries drop RIR and partials:** the session list and "Session done" show `25 × 10` only. I
  want RIR, and `+ 6p` when partials were done, to check the session at a glance.
- **"Today Last exercise"** in the Next line reads oddly. "Last exercise" alone, or nothing,
  would do.
- **Prescription sheet says "Drop set"** without the number of drops or the % drop.
- **Starting another exercise from the list ends the rest.** If I swap because the machine is
  taken, I still want the clock counting up from my last set.
- **The A2 screen shows nothing of A1** (see open question 4).

## What works and must not be lost

- **One ✓ logs a set as planned**, with the RIR preselected to the target. A straight session is
  close to one tap per set; nothing else I've used is this fast.
- **The ladder strip on the weight field.** Going from 5.7 to 7.9 on an lb stack is one tap, the
  values are what the stack has, and the new weight carries to later sets once logged. This
  fixes the worst part of the old app.
- **Lengthened partials:** ✓ on the last set flips to P with the full reps kept, so `12 + 6` is
  ✓, 6, ✓. It's fast and it can't be missed.
- **Drop weights planned and visible before the set**, with no rest between drops and
  "Finish set" for stopping early.
- **Myo flow:** no rest, a 15 s breathing countdown, mini-sets prefilled at target, and the set
  closes itself on a miss.
- **A1 → A2 with no timer** and the partner named above the exercise name.
- **Session list with Start**, and the app comes back to the skipped exercise by itself.
- **The prescription sheet's "why" line** ("not every set at the top of the range, so same
  weight; aim for 10s") and the technique note one tap away from the name.
- **The timer keeps correct time** in the background, the pill drains, and it counts up after rest.
- **Planned values are muted, done values are full contrast, and RIR colours read at a glance.**

## Spec gaps

- **Edit, delete and undo a logged set** mid-session and after. SPEC covers editing the plan on
  the fly, not correcting what was logged.
- **Session persistence:** survive back, process death and reboot, and resume where I was.
- **The rest alarm with the screen locked and in Do Not Disturb:** an ongoing notification with
  the countdown on the lock screen, and a vibration that isn't suppressed in the background
  (alarm usage, not touch usage). SPEC asks for "vibration and sound" but not that they work in
  the pocket, on silent.
- **A weight sanity check** against history and the ladder before logging.
- **Reps prefill rule within a session:** after a miss or a weight change, what are the next
  set's reps?
- **Unilateral exercises** (left/right reps or "per side"), and **bodyweight / assisted** loads.
- **A per-set or per-exercise note** ("bench was wobbly", "seat 4"). Machine settings such as seat
  position are what I look up most at a new gym.
- **The superset partner busy mid-superset:** do A1 alone and A2 later, or swap A2 only.

## Open questions from the designer

1. **Superset A1→A2: timer or none?** No timer, straight to the partner. The walk to the
   other machine is the transition. If anything, a small count-up since A1 so I know whether I
   dawdled. A countdown would just be something to dismiss.
2. **Drop-set reps: empty or prefilled?** Prefill them (last time's drop reps, else a target),
   muted like every other prefill, so ✓ logs as planned. Drops are the moment my hands are
   most useless. Muted styling already makes clear they're planned, not done.
3. **RIR on drops and myo mini-sets?** Assume failure for drops and record nothing. For
   myo, the target-or-miss is the effort signal. Don't ask; nobody judges RIR honestly on the
   third drop.
4. **Superset: current rows only, or both?** Current exercise only, partner named above. But
   add the partner's last logged set in that line ("Superset with Seated Leg Curl · 40.5 × 12
   @ 1") so I know where I am in the round without a second table.
5. **Pad hidden: fine, or keep a visible ✓?** Keep a visible ✓ at the bottom when the pad is
   hidden. I hide the pad to see the table, and without a ✓ logging means reaching to the top
   half of the screen one-handed.
