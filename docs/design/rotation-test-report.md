# Test report: rotating program days over training days (#87)

Tester: experienced lifter (tester brief). Debug build from the `feat/87-rotate-program-days-over-training-days`
worktree, on the emulator (1280×2856, same screen as the phone). Fresh install, the starter program
(Hyperlog Full Body), training days Mon 60 min / Thu 75 min / Sat 45 min. The clock was moved by hand
from Mon 26 Oct to Thu 19 Nov 2026.

## Tap counts

| Scenario | Taps | Notes |
|---|---|---|
| 1. Run the starter, set Mon/Thu/Sat with 60/75/45 min | 12 | Run this program 1, pencil 1, three days 3, three minutes menus 2 each, Done 1. One extra tap went astray because the sheet grows as days turn on (Minor 1). |
| 1. Read Today and Program | 0–1 | Today answers it at a glance; Program is 1 tap. |
| 2. Train Monday | 1 + logging + 4 | Start 1; finishing takes ⋮, Finish session, Finish, Done. |
| 2. Friday: Train it today | 1 | Goes straight into Thursday's Day B. |
| 3. A week with no sessions | 0 | Today shows it without being asked. |
| 4. Two empty weeks: the easy week | 0 | Today shows it without being asked. |
| 5. Move Saturday to Sunday | 6 | Pencil, Sa off, Su on, minutes menu, 45, Done. No way to move a day, so the minutes are lost. |
| 5. Clear every training day | 5 | Pencil, three days off, Done. |
| 5. Set them again | 11 | Pencil, three days on, all three minutes again (6), Done. The minutes were forgotten. |
| 6. Add a fourth day (Tue) | 3 (+2 for minutes) | Pencil, Tu, Done. |

## Issues

### Blocker

**B1. Today keeps yesterday's date while the app stays open or in the background, so a planned day has no Start.**
- Scenario: 2 (also seen on Tue 27 Oct and Thu 19 Nov).
- What I did: had the app open on Wednesday 18 Nov, pressed Home, moved the clock to Thursday 19 Nov
  08:00 (and sent the TIME_SET broadcast), waited a minute, then opened the app again from the launcher.
  I also tried switching tabs and tapping Thursday on the calendar.
- What happened: Today still ringed Wednesday 18 as today. Thursday's Day B read `Next · Thu 19 Nov ·
  Week 2` with **no Start session**, even with Thursday picked. After Monday's session it was the same:
  the next morning, Today still showed Monday. Only force-stopping the app moved it to the real date.
  Today's date seems to be read only when the program's data changes.
- Why it matters: this is the normal case. You open the app the evening before to look at tomorrow,
  Android keeps the process, and in the gym the next morning the day you planned can't be started.
  The only ways out are an ad-hoc session (which isn't the program day) or knowing to force-close the
  app. "Start only on a day planned for today" makes a stale date a dead end. Before #87 Start was
  always there, so this may be older than #87, but the rotation is what makes it a blocker. It could
  also mark days missed late, or let a day stay "today" past midnight.

### Major

**M1. Clearing the training days in the middle of an easy week throws the easy week away, and setting them again doesn't bring it back.**
- Scenario: 5.
- What I did: came back after 17 days. Monday 16 Nov was an easy week (Day A at 8 sets, +1 RIR, tagged
  `Easy week`), and I trained it. On Wednesday I moved Sat to Sun (the easy week stayed), then cleared
  every training day, then set Mon/Thu/Sat again.
- What happened: the moment the days were cleared, the hero turned into `Week 2 · Ramp-in` Day B at
  13 sets and normal RIR. Setting the days again kept the place (B on Thursday, C on Saturday), but
  still at full week-2 volume with no `Easy week` tag. Two days into coming back from a layoff, the
  ramp back in was gone.
- Why it matters: the easy week exists to keep you from getting smashed after time off. Someone whose
  schedule is a mess (which is why they cleared their days) is the one who needs it, and nothing tells
  them it's been dropped. The spec doesn't say what should happen here (Spec gaps).

**M2. Program's week page puts every missed day in full before the day you'll actually train.**
- Scenarios: 3 and 4.
- What I did: after two empty weeks and while in the easy week, opened Program. It opens on Week 2.
- What happened: Week 2 listed **12 day cards**: Day A, B and C `Missed · 2/5/7 Nov`, the same three
  `Missed · 9/12/14 Nov`, three easy-week days headed `Week 2 · Thu 19 Nov · Day B — … Easy week`,
  and then the three real week-2 days (23/26/28 Nov). Each missed day shows its whole dimmed
  prescription. The first thing on screen is `Week 2 · Day A — Missed · Mon 2 Nov`, and the `Next`
  day is about eight screens down.
- Why it matters: "which week am I in, and what's next?" is the question Program has to answer. It
  reads as if I've missed week 2 twice and am doing it a third time. The easy week is headed `Week 2`
  even though the spec says it sits outside the program. A missed day needs one line, not its whole
  plan.

**M3. The easy week shows up but isn't explained, and the wording changes from screen to screen.**
- Scenario: 4.
- What I did: came back on Mon 16 Nov after 17 days and read Today, Program and the session.
- What happened: Today says `Easy week` three times (the time-off card's subline, the hero line `Easy
  week · back after 17 days`, and the tag), but nothing says what's easy about it. Bench went from
  `1 × 4–6 · ↘ 2 × 8–10 @2` to `1 × 4–6 · ↘ 1 × 8–10 @3`. In the session, the cue reads `80 · deload
  week, 3 in reserve`, so it's "deload" there. The calendar header says `Week 2` for the easy week and
  for the week after it. The Done card for the easy session has no tag. Nothing says that week 2 comes
  back afterwards.
- Why it matters: why it's easy is clear (17 days off). What changed isn't, and to a lifter a missing
  back-off set looks like a bug. I'd wonder whether the app had eaten my week 2. RP and MacroFactor
  both put a one-line "here's what changes this week" on a deload. Tapping the tag does nothing.

### Minor

1. **The Training days sheet grows upward as you turn days on**, so the weekday toggles slide up under
   your finger. My quick second tap (meant for Th) opened Monday's minutes menu instead. (Scenario 1)
2. **You can't move a training day.** Saturday to Sunday is "Sa off, Su on", and Saturday's 45 min is
   lost. Clearing every day loses every minutes setting, so setting them again costs 11 taps instead
   of 5. (Scenario 5)
3. **A missed day picked on the calendar doesn't offer Train it today.** The card reads `Missed · Thu
   29 Oct · Week 1` and offers nothing. The offer only sits under today's hero, as small text under a
   different day (C) that is easy to read past. (Scenario 2)
4. **Train it today goes straight into the session without showing that day.** The hero above it is
   Saturday's Day C, so you never see Day B's plan before you're in its first exercise. An untouched
   session is discarded, so this is recoverable. (Scenario 2)
5. **The strike-through on a missed letter is thin.** On the phone at arm's length, a struck-out `B`
   looks almost the same as an outlined planned day, and every past date is muted anyway. (Scenario 2)
6. **`No sessions last week · Week 2 again`** when week 2 was never trained: "again" reads oddly, and
   "Still week 2" would be clearer. It also stacks under the time-off card, so two notices sit above
   the hero. (Scenario 3)
7. **The calendar header drops the week number.** The empty week of 2 Nov shows only `November 2026`
   while its missed cards say `Week 2`, and the week of 30 Nov shows `Nov – Dec 2026` without its week
   (the Today button takes the room). (Scenarios 3 and 6)
8. **Program's header `Next` has no date** (`Week 2 · Day B — …`), though every day card below has one,
   and it doesn't say `Easy week` during one. (Scenarios 1 and 4)
9. **`~0 minutes`.** After a short logged session (one set, finished at once), that day's estimate is
   the median of its sessions, so the hero shows `~0 minutes`. It stays that way for weeks, including
   for the easy-week day. A session logged after the fact will do the same, and then the time fit
   against the training day's minutes can't say anything useful. Very short sessions shouldn't count.
   This comes from #64's rule, but #87 now compares it with training-day minutes. (Scenario 3)
10. **The second Day A of a week looks just like the first** (`Week 3 · Mon 23 Nov · Day A` and `Week 3 ·
    Sat 28 Nov · Day A`), and the row's `3 days take turns` is cryptic. `Your 3 days rotate over 4
    training days` says what happens. (Scenario 6)

Not part of #87, seen along the way: tapping blindly in Day B's session swapped Barbell RDL for
**Deadlift**, and the warm-ups it offered were **315 × 8, 455 × 5, 475 × 3 kg** before a 100 kg work
set. Worth a ticket of its own.

Not checked: the time fit (`~45 min · Saturday has 35`). With Saturday set to 35 min, Day C (~45) showed no
amber on Today, but the fit isn't in #87's acceptance criteria, so I've left it to its ticket.

## What works and must not be lost

- **Three days on three training days land on the same weekdays every week:** A Mon, B Thu, C Sat,
  dated through the block. Planning the week at a glance on the calendar strip is exactly what a
  lifter wants.
- **On a rest day, the next day is dated and has no Start** (`Next · Thu 29 Oct · Week 1`), and on the
  day itself Start is right there. With a fresh date (see B1), you're never asked to train on the
  wrong day.
- **Train it today does the right thing:** one tap on Friday trained Thursday's Day B. Friday shows
  `B` done, Thursday is no longer struck through, and Saturday still has C. That's how I'd handle a
  missed day myself.
- **The missed day's card** (`Missed · Thu 29 Oct · Week 1`, dimmed, no Start) is clear.
- **The empty-week and easy-week arithmetic is right:** one empty week trained week 2 again, two
  brought an easy week, and the week after the easy week was week 2, not week 3. Nothing was moved or
  piled up.
- **Changing training days keeps your place:** Saturday to Sunday kept C's turn, clearing them
  switched to the sequence at B, setting them again resumed at B on Thursday, and adding Tuesday
  mid-week didn't mark the Tuesday already past as missed.
- **Three days on four training days** follow the spec on the calendar: `A B C A`, `B C A B`, `C A B C`.
  Program dates the second A of the week and gives it that week's prescriptions.
- **Minutes per training day** in the same sheet, one menu per day, is quick enough.

## Spec gaps

- **An easy week in progress when training days are cleared or changed.** Does it carry over to the
  sequence, and does it come back when they're set again? (M1)
- **Today's date must follow the clock.** Coming back to the app on a new day, or passing midnight with
  Today on screen, must move "today" (and Start, missed days, the time-off line). The spec only
  covers midnight during a running session. (B1)
- **Training a day early.** On a day with no training day, the next day can't be started (owner, Q9).
  Lifters often train Thursday's day on Wednesday because Thursday is busy. Today the only ways are an
  ad-hoc session or editing the training days. The spec should either say that's intended or offer
  `Train it today` for the next day too.
- **Back-to-back days with three days on four.** With Mon/Tue/Thu/Sat, two full-body days land on
  consecutive days every week (Mon A heavy bench → Tue B close-grip bench). The fit checks time and
  equipment but not spacing. A one-line note when two heavy days touch would help.
- **What the easy week tells you:** the sets and RIR it changes, and that the program week comes after
  it. Nothing in the spec says where the user learns this. (M3)
- **How Program shows a week with repeats and misses:** collapsing missed days, and whether easy-week
  days belong under a program week heading at all. (M2)
- **Moving a training day** with its gym and minutes, rather than off-and-on. (Minor 2)
