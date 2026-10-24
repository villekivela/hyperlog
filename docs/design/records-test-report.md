# Records (#71): tester report

**This reviews the renders and the rule. Nobody ran a session on the phone.** No phone was attached, so I
judged the feature from the eight Robolectric renders (`docs/design/screens/{light,dark}-{session,
day-done,session-done,history}-records.png`), SPEC "Records", GLOSSARY, the design notes
(`logging-screen.md` and `history.md`, Records), and the rule in `progression/Records.kt`,
`session/SessionRecords.kt` and `RecordsTest.kt`. I walked through each scenario in my head as I'd
train it. I have no tap counts: records add no taps, and nothing here asks for one.

Persona: 8+ years of training, Strong/Hevy/RP user, Nippard-style rep ranges at RIR targets, drops,
myo-reps, lengthened partials, three gyms with odd lb cable stacks.

## Issues

### Blocker

None. Records never get in the way of logging, and every scenario can still be logged as it happened.

### Major

**M1. An estimate record can come from the RIR call alone, and a fat-fingered RIR sticks.**
*Scenarios 5 and 6.* The estimate is weight × (1 + (reps + RIR)/K). So 90 × 5 @ 2 is an
"Estimate record" over last week's 90 × 5 @ 1, though I lifted the same weight for the same reps. My
RIR calls are good to about ±1 on a good day. A trophy for "I felt fresher" is a false record, and
after a few of those I stop trusting the trophy. The sticky case is worse. The RIR chips sit side by
side on the pad, so with sweaty hands it's easy to hit 3 instead of 1 and not notice. 100 × 6 @ 3
comes out about 6% above 100 × 6 @ 1, and that set becomes the best to beat. If I don't spot it, no
real top set earns a trophy for weeks, and nothing tells me why. The same goes for a mistyped weight
that's under 1.5× and on the ladder (102.5 for 92.5), which the weight check lets through. Correcting
the set does move the record (they're computed, not stored), which is right. But you only correct a
mistake you've noticed, and the mark is small (M3 and m1).
Why it matters: records only motivate if they're earned. I'd expect a set to need more weight or
more reps than before to count, with RIR at most breaking a tie. I'm not asking for RIR to be
ignored, but a set with the same weight and the same reps shouldn't make a record on RIR alone.

**M2. High-rep exercises can never earn a record for going up in weight.**
*Scenario 5.* The estimate only counts sets of 12 reps or fewer, and a rep record needs a weight
lifted before. So on lateral raises, leg extensions, calf raises or curls in a 12–20 range, going
from 7.5 × 20 to 10 × 14 earns nothing, because 10 kg is new and 14 is over 12. The only record left
on those exercises is a rep record at an old weight. In a Nippard program that's half the exercises,
and moving up the stack is the progress I'm proudest of on them. That's a missed record. Hevy
covers it with a "heaviest weight" record.

**M3. Mid-session, the mark is easy to miss, and it never says which kind it is.**
*Scenario 1.* The trophy is a 16 dp grey badge on the lower corner of the grey index circle, at the
far left. My eyes are on the pad, the RIR chips and the rest pill, which are all on the right or
bottom. Out of breath at arm's length I don't think I'd see it go on, and in dark mode it's grey on
dark grey (`dark-session-records.png`). It isn't distracting, and that part is right: no pop-up, no
confetti, no accent. But "good news you can't see" loses the moment, and it weakens the one cue that
would catch M1's fat-finger. The table also never says "Estimate" or "Rep" except to TalkBack, so
mid-session I can't tell whether set 2's trophy is a rep record or a bigger e1RM. I'm only rating
this major because of how it combines with M1. On its own it would be minor.

### Minor

**m1. Dark-mode badge contrast.** The onSurfaceVariant badge on the surfaceContainerHighest circle
barely stands out in the dark render. It's readable when you look for it and missed when you
don't.

**m2. Records don't say what they beat or by how much.** "100 × 6 [1] · Estimate record" doesn't
give the new estimate, the old best, or the margin (+2.5 kg, +1 rep). Strong shows the e1RM figure.
At Session done that number is the payoff, and it's also the quickest sanity check against a typo.

**m3. The Day done and Session done lists repeat the exercise.** Two bench records give two rows
that both say "Barbell Bench Press" (all four renders). With four records over two exercises the
list doubles in height and reads like a log. One heading per exercise with its record sets under it
would be shorter and easier to scan.

**m4. A record mark could come and go mid-session.** The Epley constant is learned from history
*plus today* (`constant(history + today)`). Logging a back-off set after a top set can shift K, and
for a borderline top set compared against a best at different reps, that can switch set 1's trophy
on or off after the fact. It'll be rare, but a trophy that disappears when I log the next set feels
like a bug. No test covers it.

**m5. Deload sets make cheap rep records possible.** Deload sets "still count as sets to beat", so
a weight that was only ever lifted in a deload (80 kg × 5, easy) counts as "lifted before". Any
later back-off at 80 kg for 6+ reps is then a rep record. It's technically true and feels hollow.

**m6. Bodyweight moves weighted-bodyweight records.** Same dips with the same 20 kg on the belt,
3 kg heavier on the scale, and it's an Estimate record (tested deliberately). Lifting more total
load is true, but a bulking lifter collects trophies without adding to the belt, and a cutting lifter
gets fewer. The record also changes when a bodyweight is first logged, since past sessions fall back
to the first value. I'd want this to be a conscious choice, written down in SPEC.

**m7. The set-text trophy sits close to the next set.** "100 × 6 [1] 🏆   87.5 × 9 [1] 🏆" reads
fine at this width. When the line wraps, a trophy at the end of a line could look like it belongs to
the set that starts the next line. I'd check this with a six-set exercise.

**m8. No render shows both badges.** Scenario 3 (a drop set or myo-reps set that's also a record)
should put the violet technique badge on the upper corner and the trophy on the lower one. The rule
allows it: drops and mini-sets never count, but the main set can. No render shows both badges on one
circle, so I can't judge whether a 40 dp circle with two badges still reads, and neither can the
owner. The same goes for History's per-exercise table, where the trophy is specified but not
rendered.

## Scenarios, as a lifter

1. **+2.5 kg top set.** It's correctly an Estimate record. I probably wouldn't notice it as it
   logs (M3). The Day done strip and Session done make up for that.
2. **Back-off with more reps at a known weight.** "Rep record" reads exactly right (87.5 × 9 over a
   best of 8 at 87.5). It's correct that this doesn't count as a heavier lift.
3. **Drop set or myo-reps set that's also a record.** The rule is right: the main set can make a
   record, and its drops, mini-sets and partials can't. The two-badge visual isn't rendered (m8).
4. **Day done with 3–4 records.** Finish session stays pinned bottom-right in the strip, under the
   list, and past 288 dp the list scrolls inside the strip, so the button stays easy to hit. The
   rows only make the strip taller (m3). I can't tell from a still whether the taller strip covers
   the last set rows I might want to correct.
5. **Deload, first session, other gym's cable station.** No records in a deload week, none in an
   exercise's first session, and none on a new machine's first session (per machine). All of that
   matches what I'd expect, and none of it feels like a missed record. The false records come from
   RIR (M1) and deloads (m5). The missed ones are weight jumps on high-rep exercises (M2).
6. **Mistype 255 for 25.** The weight check catches it (10×), and a weight kept past a check makes
   no record until a set confirms it, so that case is safe. Correcting it moves the record, because
   records aren't stored. Smaller mistypes and RIR fat-fingers get through and stick (M1).
7. **Scanning History.** Yes: the trophies after the RIR chips in the session summary make record
   sets easy to pick out. The kind only shows to TalkBack there. That's acceptable in History.

## What works well and must not be lost

- **Computed, not stored.** Correcting a set moves its record, and a past session in History is
  judged only against the sessions before it, so old trophies don't change as later ones arrive.
- **Ink, not accent.** The trophy never looks like a button or a warning, and it never fights the
  RIR colours. No pop-ups or interruptions mid-set.
- **No records in a deload or a first session, and per machine for cables.** The rule matches how I
  think about records at three gyms.
- **Partials never count.** 8 + 4p doesn't beat 8.
- **Set 3 can beat set 1.** A record within the session is right.
- **Weight-check guard.** A weight kept past a check makes no record until confirmed, so an obvious
  typo never earns one.
- **Session done's Records card**, at the top and in plain words. It's the right place for the
  payoff.
- **One symbol, with words for the kind.** "Estimate record" and "Rep record" never depend on
  colour.

## Spec gaps

- **A "heaviest weight" (or weight-for-reps) record** for sets over 12 reps, or some other way for
  high-rep work to earn a record (M2).
- **How much RIR counts toward a record** (M1). SPEC counts the estimate as progression does, but
  doesn't say whether a set with the same weight and reps and a higher RIR should make a record.
- **What a record shows:** the new estimate and the margin or previous best (m2).
- **Where to look up current bests.** No screen answers "what's my bench record, and when was it?"
  Strong and Hevy both have a per-exercise records list, and lifters check it.
- **Whether K learned today can change a record already shown** (m4), and whether a bodyweight
  change should create records on weighted bodyweight exercises (m6).
- **Whether sets logged in a deload count as "a weight lifted before"** for rep records (m5).
