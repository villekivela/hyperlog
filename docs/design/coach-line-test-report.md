# The coach line: designer and tester pass (#89)

Rendered on Robolectric at the phone's size (412 × 915 dp, xxhdpi, 3 px to the dp), light and dark,
by `CoachLineRenders` (`cd app && RENDERS=<dir> ../gradlew testDebugUnitTest --tests
'*CoachLineRenders*'`, again with `DARK=1` and `--rerun-tasks` for dark), then halved with `magick
-resize 50%`. No phone was used in this pass. The words are the owner's to sign off, so they were
left as they are. The owner has since taken the recommended words (2026-10-28).

Screens, each `light-` and `dark-`:

- `coach-line-stall.png`: `Barbell Bench Press hasn't moved in 3 sessions`, alone over the hero.
- `coach-line-daymissed.png`: `Day A missed 3 of its last 4 turns`.
- `coach-line-grinding.png`: `Leg Press hit 0 RIR in 3 sessions running`.
- `coach-line-sameswap.png`: `Incline Machine Press swapped 3 times running` (rendered again
  after the owner chose a line that never names the gym; the designer's pass below saw the longer
  `… swapped at Kalliokoski 3 times`).
- `coach-line-stall-time-off.png`: under the time-off banner, 4 sessions.

## Designer

- **The line blended into the screen.** On `surfaceContainerHigh` the banner measured 1.06:1
  against the light screen and 1.08:1 against the dark one, the "banners blend into the
  background" the owner objected to. It is on `surfaceContainerHighest` now: 1.13:1 light and
  1.27:1 dark against the screen. It still reads quieter than time off, whose
  `tertiaryContainer` is a step further (1.11:1 light, 1.18:1 dark beyond the coach line) and
  whose button is filled. Still a neutral surface, so the SPEC's words hold. Fixed.
- **Contrast.** Ink on the container is 13.5:1 light and 11.7:1 dark; the Check in label
  (`primary`) is 5.3:1 light and 7.6:1 dark. All above 4.5:1. Nothing to fix.
- **Wrapping left a word alone.** `Day A missed 3 of its last 4 / turns` and `swapped at
  Kalliokoski 3 / times` split the number from its noun. The line now breaks balanced
  (`LineBreak.Heading` on the banner's title style, set in `CoachLineBanner` only): `Day A missed 3
  / of its last 4 turns`, `Leg Press hit 0 RIR in / 3 sessions running`. The longest line is
  still three lines, `Incline Machine / Press swapped at / Kalliokoski 3 times`, which now splits
  the exercise name instead; left, as the number staying with its noun matters more. Fixed.
- **Height and spacing.** 72 dp for one or two lines, 84 dp for three (a 4 dp half step). 16 dp
  under the calendar, 8 dp between the time-off banner and the line and between the line and the
  hero. On the grid; nothing to fix.
- **The text edge.** The symbol starts at 28 dp and the words at 68 dp, 4 dp in from the 24 dp
  edge and from the exercise names (64 dp) below. That is the shared `Banner`'s padding
  (`CardPadding + 4.dp`), the same on the update, time-off and again banners; left, as the pass
  keeps the `Banner` shape. Worth a look across all banners at once.
- **The symbol** is 24 dp, as on every banner, in the same ink as the words. Left.
- **Weight against the hero.** The line is `titleSmall` against the hero's headline and figures,
  and a text button against Start session's filled one: it reads as a note over the day, not a
  rival to it. Left.

## Tester

Read as a lifter of 8+ years standing on the gym floor before a session, from the renders and the
SPEC (no phone). The line is the right size: one quiet strip, Start session still well above the
fold even under the time-off banner, and Check in reads as a text link, not a rival to the hero.
The problems are in when it fires and how it comes back, not in how it looks.

### Major

- **The stall fires on normal double progression.** The estimate is the latest session's best
  e1RM, so the session where you move up a rung usually reads lower: 100 × 6 @ 2 (≈127) then
  102.5 × 4 @ 2 (≈123), then × 5 (≈126). Two of three sessions in a healthy cycle "haven't moved",
  and on coarse ladders (cable stacks, 2 kg dumbbell jumps, machines with 5 kg plates) the cycle
  runs longer, so `hasn't moved in 3 sessions` lands on someone who is progressing exactly as the
  app told them to. The same rule misses a real stall with noise in it (127, 125, 127, 125 never
  fires, as each up session counts as moving). Impact: the most common line is the least
  trustworthy, and after two false ones I stop reading any of them. From experience, a stall is
  "no better than my best of the sessions before": compare the best of the last 3 sessions with
  the best of the 3 before, or call it a stall when the progression rule said "same weight" or
  "down" 3 sessions running, which is what the app already judges.
- **The stall counts Ramp-in.** Deload and easy weeks are left out but Ramp-in (+1 RIR, often new
  rep ranges, deliberately submaximal) isn't. The stall render shows the line in `Week 1 ·
  Ramp-in`. Against the estimate carried from the end of the last block, the first weeks of every
  block will read flat. Impact: a false alarm at the start of each block, when the plan is working.
  Leave Ramp-in sessions (and the first session in a new rep range) out as deloads are.
- **× doesn't really dismiss.** It hides the line "until the pattern grows", and every pattern
  grows with each session: the stall reaches 4, then 5; the swap at Harbour is a 4th swap the next
  time I train there. So for a pattern I've decided is fine (that gym has no incline machine, full
  stop) the line comes back every session there. Impact: nagging, which is what makes people turn
  coaching off. Suggest growth means something new: the stall doubles (3, then 6), the day is
  missed again after it was trained, and a swap hidden at a gym stays hidden at that gym.
- **The same swap wants an action, not a conversation.** `Incline Machine Press swapped at
  Kalliokoski 3 times` is almost always an equipment fact. With three gyms, this will be the line I
  see most, and a check-in with Claude is a heavy way to say "use the Smith incline here". The line
  also doesn't say what I swapped *to*, which is the part I'd act on. Impact: friction and
  repetition for a non-problem. At least name the replacement (`Incline Machine Press → Smith
  Incline at Kalliokoski, 3 times`); better, let the check-in's answer be a standing per-gym swap,
  if the coaching ADRs allow it.

### Minor

- **Grinding is more urgent than a stall, and ordered after it.** Grinding at 0 RIR when 2 was the
  target is fatigue or a load that's too heavy, often the *cause* of the stall, and the one with a
  joint-pain angle (the program's hand-pain example). With one line at a time I'd see the stall and
  not why. Put grinding before the stall, or let the stall's check-in mention it.
- **Grinding's line hides its point.** `Leg Press hit 0 RIR in 3 sessions running` reads like
  praise to most lifters; the reason it matters is the target. `Leg Press: 0 RIR 3 sessions running
  (target 2)` says it. Purpose-built failure sets don't trip it (target 0 is under 2), which is
  right. Check that drop-set drops and myo-rep minis logged at 0 RIR don't count against the main
  set's 2 RIR target, or every session with a drop set will fire it.
- **Grinding counts any one work set.** One set at 0 in a session (usually the last one, under
  fatigue, or a misjudged RIR on a new weight) is common and not a problem by itself. Requiring the
  top set, or two work sets, in each session would cut false ones.
- **Ramp-in makes grinding fire on the wrong number.** Ramp-in's +1 RIR turns 1 RIR targets into 2,
  so a lifter who habitually grinds the week-3 target trips the line in ramp-in. Probably fine,
  worth knowing.
- **"Turns" is app language.** `Day A missed 3 of its last 4 turns`: no lifter says turns. `Day A
  missed 3 of the last 4 times` or `... 3 of the last 4 weeks` (if the rotation makes it weekly)
  reads naturally. The pattern itself is sound and the one I'd want most.
- **"Hasn't moved" is ambiguous.** It reads as "the weight on the bar hasn't changed", which is
  also true during a perfectly fine rep-adding run. `Bench hasn't improved in 3 sessions` or
  `... no stronger in 3 sessions` names the estimate.
- **Check in doesn't say where it goes.** Nothing tells me it opens a conversation with Claude
  rather than marking something done; the first tap is a surprise. If the app already teaches
  "check-in" elsewhere this is fine; if this is the first place a lifter meets it, it isn't.
- **No undo on ×.** × and Check in sit close on a three-line banner, and a sweaty thumb hitting ×
  hides the line until the pattern grows, with no way back. A short undo snackbar fixes it.

### What was done with the report

- **Stall on double progression: left.** The line compares each of the last 3 sessions with the
  estimate *before* them, not with the session before each one. 127, 123, 126 is two sessions
  under 127, so no line yet; it fires only if a third session also stays at or under 127, which
  is three sessions without beating the estimate. The noisy 127, 125, 127, 125 does fire (the last
  three are all at or under the 127 before them). `CoachLinesTest` holds both cases.
- **Ramp-in: asked the coordinator** whether weeks the plan eases (`rirAdd` above 0) are left out
  as deloads are.
- **× brings the line back when it grows, the swap line points at a check-in, the order puts the
  stall before grinding, and "turns":** the owner's decisions (SPEC, Coaching → The coach line),
  kept.
- **Drops and myo-rep minis:** grinding reads the logged set's own RIR only; drops and minis are
  extensions of the set and never count.
- **Undo on ×, naming the target, one set versus two:** left for the owner's next pass.
