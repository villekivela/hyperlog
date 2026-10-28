# Block review and program review (#91): tester report

I ran this pass on the emulator (`emulator-5584`, current debug build, light mode with one dark
check) using two fixtures:
- `ramp-in-done`: the starter program, Ramp-in finished, today Wed 28 Oct, Build's Monday missed.
- `program-done`: all 12 weeks finished, today Wed 28 Oct.

I read the pages the way I'd read them at the end of a block, coffee in hand, deciding what to
change. The gym logging flow wasn't part of this pass.

Persona: 8+ years of training, Strong/Hevy/MacroFactor/RP user, periodized Nippard-style programs
at RIR targets.

## Tap counts

| # | Scenario | Taps | Notes |
| --- | --- | --- | --- |
| 1 | Ramp-in ended: how did it go, change anything for Build? | 1 tap + 4 swipes (+1 to open "15 more muscles") | Banner → review. Next sits at the bottom and answers "what does Build change". |
| 2 | Program → Build → Review | 3 (Program, Build chip, Review) | Back returns to Program with Build still selected. |
| 3 | Program finished: program review, best/worst lifts, consistency, bench goal | 1 tap + 6 swipes | Best and worst lifts by kg can be read off; consistency is one row (33 of 36); the goal is last. |
| 4 | Check in about the next block (scripted coach) | 1 (Check in) + 1 answer + 1 Done. Turning on the scripted coach first costs 3 more (Settings, swipe, toggle) | Starts on a generic question that has nothing to do with the block (M3). |
| 5 | New program from the program review | 1 | Scripted coach: the same generic script. Ask Claude: the right opening sentence. |
| 6 | Back out of each | 1 | Review → Today or Program, wherever it was opened from. Check-in → **Coach**, not the review (M5). The Today line goes once the review is opened, from the banner or from Program. |

## Issues

### Blocker

None. Every review opened, read correctly enough to make a decision, and backed out cleanly.

### Major

**M1. The program review compares the whole program against a one-week deload.**
*Scenario 3.* The first column is "Deload + test", the header wraps onto two lines, and every Lifts
row reads `–` there because deload sessions don't count. "Training days 3 of 3" and "Records set
0" aren't much better. The comparison I want at the end of a program is the last *training* block
(Intensify) against the whole program, plus the test. As built, half the table is dashes, and the
block's estimates that the spec puts under each lift's name don't appear (the deload has none). So
the program review shows **no absolute numbers for any lift**, only gains (`Bench +5`). I can't
see where my bench, squat or row finished without going to History.
Why it matters: I open this page once per program, and the most prominent column on it carries no
information.

**M2. The week-12 bench test doesn't appear anywhere, and the goal line ignores it.**
*Scenario 3.* The program's purpose is the week-12 top single. In the fixture it's 105 × 1 @ 1 (the
week-11 estimate was under 97% of 125, so it was the RIR-1 single, as the program file says). The
review never shows that set: Records in the block read 0, Lifts are dashes, and Goal reads
`121.5 of 125 kg by 15 Jan · Program: On pace for 3 Dec`. A 105 single at 1 RIR puts my real max
around 108–110. Being told I'm 3.5 kg off a 125 bench and "on pace" is wrong. "On pace for 3 Dec"
also makes no sense for a program that ended on 23 Oct. Nothing is left to keep the pace going.
Why it matters: this is the moment I decide whether the goal was realistic and what the next
program's target should be. The review buries the one hard data point and shows a flattering
extrapolation instead.

**M3. Check in and New program play the generic "Something else" script under the scripted coach.**
*Scenarios 4, 5.* From the Ramp-in review, Check in about the next block opened `What's on your
mind?` with `My sessions run long / I'm losing motivation`. It ended in advice about timing rests.
New program from the program review opened the same script, never asked about days a week or a
file, and never proposed a program. My opening answer ("Ramp-in is done…") isn't shown on the
scripted check-in at all, so nothing on screen says the check-in is about the block. (The scripted
coach picks its script by tile, and review check-ins open with no tile, so they fall through to
Something else.) With Ask Claude the opening is right
(`Hyperlog Full Body is done. I want a new program for what comes next.`).
Why it matters: I couldn't check that either button leads anywhere useful without a key. The
project rule says every coach path must be testable without a key, and these two aren't yet.

**M4. Check in on the program review says "the next block" when there is none.**
*Scenario 4, program-done.* In Ask Claude mode, Check in pre-fills
`Deload + test is done. Here's how it went: look at it and the next block.` The program is finished
and no next block exists. It should frame the program ("Hyperlog Full Body is done…"), the way New
program does.
Why it matters: Claude gets a wrong premise, and I have to retype the opening before I send it.

**M5. Leaving a check-in started from a review lands on Coach, not the review.**
*Scenario 6.* Back, ✕ or Done in the check-in all go to the Coach tab. The review is gone, and
getting back to it takes Program → chip → Review, or Program → Deload + test → Review for the
program review. Once it has been opened, Today no longer offers it.
Why it matters: I check in with the review open next to my thinking. When Claude asks "how did
the bench feel?", I want to glance at the numbers and come back.

**M6. The Ramp-in review tells me the bench goal is 10 kg short.**
*Scenario 1.* Ramp-in is easy on purpose (+1 RIR, no failure), so the bench estimate is flat:
`115 of 125 kg by 15 Jan · 10 kg short at this pace`, in bold. Above it, Today's coach line says
`Barbell Bench Press hasn't moved in 4 sessions · Check in`. The Build review in the program-done
fixture says `On pace for 1 Nov` for the same goal. A straight line through a ramp-in block reads
as a verdict but tells me nothing. The Ramp-in Lifts are also mostly `±0`, although my bench top
set went from 87.5 × 4 before the program to 90 × 4. The estimate is driven by the 85 × 6 day and
doesn't move, so the review misses a real top-set PR.
Why it matters: after the easiest two weeks of the program, the review's loudest line says I'm
failing. For an eased block I'd want "no pace from an easy block" or the goal standing without a
pace.

**M7. "Best and worst lifts" are ranked by kilos, so heavy lifts always win.**
*Scenario 3.* The program column sorts Leg Press +13.5 first and lateral raise +0.5 near the bottom.
In percentage terms the leg press is +7% and the lateral raise about +7.9% (8.9 → 9.6), so they
did about the same. The bench at +5 (+4.5%) sits mid-table. RP and MacroFactor both show change
as a percentage, or at least next to the kg.
Why it matters: I read the top and bottom of this list to decide what to swap next program. Sorted
by kg, it tells me which lifts are heavy.

### Minor

- **m1. The Volume arrow means something different from the Lifts arrow.** *Scenarios 1–3.* In
  Lifts, `195 → 198` is start → end. In Volume, `3 → 5 sets a week` is done → written. My first
  read of `Traps 3 → 5` was that I'd added two sets. A label (`3 of 5`, or a "done · written"
  header) would fix it.
- **m2. Volume lists 21 muscles, and Traps comes first in every block.** Teres major, brachialis,
  forearm extensors, gastrocnemius and soleus listed separately is a physiology chart, not
  training. Traps leads because it's the furthest under its written sets, but nobody plans traps
  in this program. In Ramp-in nearly every muscle sits about one set under its written count,
  which only reflects the missed Day B. `0.5 → 1 sets a week` should read "set".
- **m3. Build's Records is about 14 rows of "Estimate record".** The bench appears three times. I'd
  want the best record per lift, or a count with the list folded. On the program review it's the
  other extreme: just `Records set 0 / 35`, with no way to see them.
- **m4. Next doesn't name the next block.** It reads `From Build`, then the diff. I have to know
  that Intensify follows. `Next: Intensify · weeks 7–11` would help.
- **m5. A past block's review still offers "Check in about the next block".** Opening Build's
  review after the whole program has finished shows Next and the check-in button for a block that
  ended six weeks ago.
- **m6. The finished program sits under "Running" on Programs** (`Running · Program finished`).
  Its sheet has Run again and Export but no Program review. Once the Today line is gone, the only
  way back is Program → Deload + test → Review, which I wouldn't guess.
- **m7. The program review's Turning up drops the missed-day breakdown.** It's `33 of 36` with no
  `Day B missed twice` line. The block review has that line, and it's the part that tells me which
  day my week can't fit.
- **m8. On Program, the finished block's card only shows Review once its chip is picked.** Program
  opens on the current block, so the review stays hidden until I tap Ramp-in.

## Data that reads wrong (fixtures or estimate)

- **The bench estimate is about 10% high for this lifter.** 90 × 4 @ 1 and 85 × 6 @ 1 read as 115
  kg, and the week-11 97.5 × 3 @ 1 reads about 120. Plain Epley gives about 105 and 110. The cause
  is the per-exercise correction: the fixture's back-offs (70 × 8 @ 1 behind 90 × 4 @ 1) are far
  too light for that RIR, so the constant hits its floor of 20 and every estimate inflates. Real
  lifters sandbag back-off RIR exactly like this. The review then runs a 125 kg goal against an
  inflated number, and the 105 single shows how far off it is (M2).
- No gyms or machines exist in either fixture, so the `Cable left · Harbour` machine line under a
  lift was never exercised.
- Week 12 Day C logged 90 × 6 @ 1 on a deload day written at @ 3. The goal estimate (121.5) comes
  from that set. As a lifter, I'd call a deload set taken to 1 RIR a logging slip, not a data
  point.

## What works and must not be lost

- **One tap from Today** to the review, and the banner goes away once the review is opened from
  anywhere (banner or Program). It doesn't nag.
- **Next on the block review is the best part.** The diff (`1 × 4–6 → 1 × 3–5`, `Every set: 1 RIR
  closer to failure · techniques back`) answers "what changes in the next block" without opening
  the program. I'd keep it exactly as is.
- **Turning up's `9 of 12 training days · Day B missed twice · Day A missed once`.** That's the
  consistency read I want, in one line.
- **Lifts as start → end with the gain in the accent**, stalls muted at the bottom. It's quick to
  scan, and the rounding to half kilos is right.
- **No Claude call to render the page.** It opens instantly and works offline.
- **The Ask Claude openings** carry the review as context, and New program's sentence is right.
- **Dark mode** reads cleanly. The pinned bottom button never covers the last card.

## Spec gaps

- **The program review's block column after a deload block.** The spec says "the last block's
  review" with the program beside it. When the last block is a deload or test week, it should use
  the last block that trained (or show the test beside the program). See M1.
- **The test set.** Nothing in the spec puts the week-12 single (a slot marked as a test, or the
  goal lift's heaviest set in the last week) on the review or into the goal line. See M2.
- **Pace after the program ends, and pace from an eased block.** The spec gives the straight-line
  rule but not what happens once no weeks remain, or when the block was eased by design. See M2
  and M6.
- **The check-in opening on the program review** (M4). The spec gives New program's sentence but
  not Check in's when there's no next block.
- **Where a check-in from a review returns** (M5).
- **Ranking by percentage, or showing %, in Lifts** (M7).
- **The scripted coach's script for review check-ins** (M3). The spec requires coach paths to be
  testable without a key, but names no script for these.
