# Program screen test report (#40, PR #41)

- **Tester:** the tester agent, as the experienced lifter in `tester-brief.md` (8+ years, runs
  periodized RIR programs).
- **When:** 2026-10-16, 13:49–13:55.
- **Build:** DEV build `com.hyperlog.app.dev` from branch `program-screen` (head `f9fd0fa`).
- **Data:** the owner's real data. Hyperlog Full Body, Next = Week 6 · Day A (Build). Weeks 1–5
  have logged sessions (12–15 Oct). No Changes in force; 8 reverted or replaced.
- **What I touched:** nothing changed. I opened four Change reviews and rejected each one. I
  cancelled the Shift dialog once. I cancelled both replace dialogs (Import program, Run Hyperlog
  Full Body). I opened one past session (Week 5 Day C, Thu 15 Oct) read-only. Afterwards Program
  still read "Next Week 6 · Day A" and "Changes · none in force". I left the app on Program, at
  the top.

The brief's session scenarios don't apply here. I ran the seven Program scenarios I was given.

## Tap counts

Swipes are counted apart from taps.

| # | Scenario | Taps | Swipes | Notes |
|---|---|---|---|---|
| 1 | What am I doing this block, and how does it differ from the last? | 1 (Program), +1 to unfold "19 more" | 1–3 | The block opens on Build and the week opens on 6. The diff fills the first screen. |
| 2 | My next-but-one session (Week 6 Day B) in full | 1 | 2–3 | It sits below the block card and Day A. |
| 3 | What changes in Intensify and in Deload + test | 2 (Intensify, Deload + test), +1 to unfold | 2–4 | Intensify is clear (5 slots). Deload is 22 slots, folded. |
| 4 | Last week's sessions, open one, come back to Program | 5 (Program, week 5, session, Back, Program) | 2 | Back lands on **History**, not Program. Program then forgets week 5. |
| 5 | Skip day / Repeat week / Deload week / Shift program, reject each | 3 per action (⋮, item, Reject); Shift takes 4 (⋮, item, Review, Reject) | 0 | All four apply only on Accept. Reject left nothing behind. |
| 6 | Open Changes and come back | 2 | 0 | |
| 7 | ⋮ → Import program, then ⋮ → Run Hyperlog Full Body, cancel both | 3 each (⋮, item, Cancel) | 0 | |

## Issues

### Blocker

None. Every question had an answer on the screen, and every action stopped before it changed
anything.

### Major

1. **Back from a past session lands on History, not Program, and Program forgets the week you
   picked.** (Scenario 4)
   - *What I did:* Program → Build → week 5 → tapped Day C (Thu 15 Oct) → system Back.
   - *What happened:* I landed on History's session list. Tapping Program reopened it at the top
     with week 6 picked. Week 5 was gone.
   - *Why it matters:* "What did I do last week?" usually means checking two or three days in a
     row, e.g. Monday's bench and then Thursday's. Each extra day costs Program, a scroll, week 5
     and the day again, between sets on a cable stack someone is waiting for. Back should return
     to where I came from. Hevy's routine → past workout → Back does that.

2. **The "From <previous block>" comparison is a wall when a block changes every slot by the same
   rule.** (Scenarios 1 and 3)
   - *What I did:* opened Build (From Ramp-in) and Deload + test (From Intensify).
   - *What happened:* each shows 3 slots and "19 more". Unfolded, it's 22 before → after pairs over
     about three screens. In Build every one says the same thing: RIR one closer, and a technique
     added on the last set. The week chips sit under the whole list, so you scroll past it to
     change week.
   - *Why it matters:* the summary I want is "Build = everything 1 RIR harder, last sets get
     myo / drop / lengthened partials". Today I have to read 22 rows to find it, and I can't tell
     whether one exercise changed in a different way. Intensify (5 rows: the heavy compounds drop
     from 6–8 to 5–7) reads well. The design works for targeted changes and drowns in uniform
     ones. Possible directions: group identical deltas, or name the common change once ("All
     slots: @2 → @1") and list only the exceptions.

3. **"Run Hyperlog Full Body" sits right under Shift program, while Hyperlog Full Body is already
   running, and its warning undersells what it does.** (Scenario 7)
   - *What I did:* ⋮ → Run Hyperlog Full Body.
   - *What happened:* "Replace Hyperlog Full Body? The new program starts at week 1, day 1. Past
     sessions are kept." Cancel / Replace. Import program shows the same dialog.
   - *Why it matters:* I'm in week 6. Replace throws away five weeks of position, but the copy
     reads like a harmless refresh ("Past sessions are kept"). It doesn't say "you're in week 6;
     this restarts at week 1". The four routine Change actions sit directly above it, one divider
     apart, so a sweaty thumb aiming for Shift program can land on it. Cancel saved me, but this
     is the one irreversible item in the menu. Saying where you are now and what you'd lose would
     make the confirm mean something.

4. **Repeat week and Shift program by 1 week show the same review.** (Scenario 5)
   - *What I did:* opened both and rejected both.
   - *What happened:* "Repeat week 5" and "The plan 1 week later" both show Next Week 6 · Day A →
     Week 5 · Day A and Left 21 → 24 days. The Shift dialog explains itself as "The rest of the
     plan starts later, so the weeks before it are trained again", which I had to read twice.
   - *Why it matters:* when life gets in the way, I need to know which one to pick, and the app
     shows me no difference. Also, "Repeat week" repeats **last** week (5), not the week I'm in
     (6), and nothing says so until the review. Week 5 may be the right default, but the menu
     item gives no hint.

### Minor

5. **Nothing on the day list tells you which week you're looking at once you scroll past the
   chips.** (Scenarios 2 and 3) The day headers read "Day A — Heavy bench, squat, pulldown" with
   no week number. After scrolling, week 6, week 7 and week 12 all look alike, apart from the Next
   tag on the current week.

6. **The Deload + test week isn't marked Deload.** (Scenario 3) Week 12 days drop to 1 set @ 3 RIR
   and bench to 1 × 1 @ 1, but the day headers carry no **Deload** tag. The Deload week review
   does show one. The spec says a deload day is headed Deload. Either this program's week isn't
   flagged as a deload in the data, or the tag only appears for a `deload_week` Change. A lifter
   reads "Deload + test" in the block name, so it's a small thing, but the bench single is the
   whole point of the week and nothing marks it as the test.

7. **Done days truncate exercise names when the top set is a drop chain.** (Scenario 4) "Incline
   Mac…  50 × 10 @ 0, 37.5 × 6, 27.5 × 6" and "Bayesian …  21.5 × 12 @ 0, 19.3 × 6, 12.5 × 6".
   The name is the part I scan for, and the drop tail matters less in a summary.

8. **Technique icons need a key.** (Scenarios 1–3) After slots like "2 × 8–10 @ 0" come small
   glyphs for myo, drop and partials. I can guess from other apps, but Program never says which is
   which, and the slots aren't tappable.

9. **Changes page rows don't open.** (Scenario 6) Struck-through rows read "Reverted" with no date
   (only "Replaced 16 Oct" has one). Tapping one does nothing, so I can't see what a reverted
   Change did. It's fine as an audit list, but dates on Reverted would help.

10. **The navigation bar stays up on the Changes page.** (Scenario 6) The spec hides the bar "on
    screens opened from a destination". Changes is opened from Program. Back works either way.

11. **Program jumps back to the current block after a review.** (Scenario 5) I was on Ramp-in,
    opened Skip day and rejected it, and came back on Build. That's fine on its own, but together
    with issue 1, Program never keeps where I was.

## What works well and must not be lost

- **It opens on the block and week I'm in, with Next tagged.** One tap from Today answers "what's
  this week", and the next-but-one session is just a scroll.
- **Future days read exactly like Today**, including supersets (5a/5b) and the per-set RIR split
  ("1 × 6–8 @ 1 · 1 × 8–10 @ 0"). I trust it because Today shows the same thing.
- **Before → after with the arrow** is the right format. For Intensify it took me five seconds to
  see that the compounds drop a rep bracket and nothing else changes. Day A beside Day A, slot by
  slot, is right.
- **Past days as trained.** Date plus each exercise's top set, with swaps showing the exercise
  actually done (Machine Chest Press on Day C). Ticks on done weeks. Opening the real session from
  there is exactly what I want, once Back returns to Program.
- **The actions are safe.** Every one goes through a review with a clear diff (the Deload week
  diff strikes through the old sets and shows the new ones day by day), and Reject leaves no trace.
  Shift's stepper with Cancel is easy to back out of.
- **Moving the actions into ⋮** cleans up the header. The header card (title, Next, Changes row)
  is calm and readable.
- **Fold to 3 slots with "N more", unfolded in place.** It's necessary given issue 2, and it works.

## Spec gaps

- **Where Back goes from a past session opened from Program**, and whether Program keeps its picked
  block, week and scroll when you return. The spec says Back from a destination goes to Today, but
  it says nothing about returning from a screen opened from Program.
- **Summarising a uniform block change.** "From <previous block>" lists every differing slot with
  no rule for when all of them differ the same way (issue 2).
- **The starter replacing itself.** The spec says Run Hyperlog Full Body asks before it replaces
  the program running. It doesn't cover the case where the running program *is* Hyperlog Full Body
  (a restart), or what the confirm must say about the position lost.
- **Repeat week vs Shift program by 1 week.** The spec defines both but not how a lifter tells
  them apart when they produce the same diff, nor that Repeat week repeats the previous week.
- **The test in a "Deload + test" week.** There's no way to mark the heavy single as the test, and
  it's unclear whether a program's own deload week gets the Deload heading or only a `deload_week`
  Change does.
- **A key for technique glyphs** on read-only screens like Program, where slots don't open.
