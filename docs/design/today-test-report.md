# Today, Settings and training days test report (#58)

- **Tester:** the tester agent, as the experienced lifter in `tester-brief.md` (8+ years, runs
  periodized RIR programs).
- **When:** 2026-10-17, 18:53–19:01.
- **Build:** DEV build `com.hyperlog.app.dev` from branch `phone-pass` (head `481a860`). The phone
  was in dark mode with system text at 115 %.
- **Data:** the owner's real data: 19 sessions, Hyperlog Full Body, Next = Week 7 · Day B, one gym
  (Kalliokoski), no training days. Today (Sat 17 Oct) already has two finished sessions (Day C, then
  week 7 Day A), so the app could not be seen on a morning with nothing logged yet.
- **What I touched:** training days only. From Today's pencil I set Mon Wed Fri, then
  Tue Thu Sat. From Program's row I set Mon Wed Fri, and at the end I cleared them all from the
  pencil. I opened one past session (Thu 15 Oct) read-only. I opened Export's save dialog and the
  Restore file picker and backed out of both. I opened Gyms and equipment, Kalliokoski and the
  exercise library without changing anything, and I didn't flip the update switch. Afterwards the
  database had `weekday_plan` 0 rows and `session` 19 rows. I left the app on Today.

The brief's session scenarios don't apply here. I ran the eight Today, Settings and training-days
scenarios I was given.

## Tap counts

Swipes are counted apart from taps. "Back" is the system Back gesture, counted as a tap.

| # | Scenario | Taps | Swipes | Notes |
|---|---|---|---|---|
| 1a | Open the app: what am I doing next, can I start? (no training days) | 1 (tap 18 on the strip) or 1 (Next link) | 0 or 2 | Today opens on today's two done sessions. Next (Day B, Sun 18) is outlined on the strip, and the Next link is two screens down. Start sits on the hero. |
| 1b | Same, with Mon Wed Fri | 1 (tap 19) or 1 (Next link) | 1 (strip) or 2 (page) | Day B lands on Mon 19 Oct, in next week, so the strip shows nothing ahead this week. |
| 2a | Set Mon Wed Fri from Today's pencil, first time | 5 (pencil, M, W, F, Done) | 0 | The sheet saves each toggle at once. The card behind updates live. |
| 2b | Change to Tue Thu Sat | 8 (pencil, 6 toggles, Back) | 0 | Back keeps the change, just like Done. |
| 2c | From Program's training-days row (back to Mon Wed Fri) | 9 (Program, row, 6 toggles, Done), +1 Back to Today | 0 | The row reads `3 days a week · Mon Wed Fri` right after Done. |
| 3 | Next session, the one after it, then back to today (Mon Wed Fri) | 3 (19, 21, Today) | 1 | Or Rest day → Next link → 21 → Today: 4 taps, no swipe. |
| 4 | Last Thursday, last week, open a done day, come back | 4 (15, Open session, Back, and 15 again to carry on) | 1 | Last week (5–11 Oct) is empty, as the first session was 12 Oct. Back drops the selection to today (issue 1). |
| 5 | A rest day, then to the next session | 2 (Sun 18, Next link) | 0 | `Rest day` + `Next: Day B on Mon 19 Oct ›`. The link pages the strip to next week and selects Monday. |
| 6 | Swipe weeks fast with one thumb | 0 | 3 fwd, 2 back, 6 alternating, 1 diagonal | Every swipe moved exactly one week. Nothing was selected by mistake. It stops 4 weeks back (14 Sep) and at the last planned week (23 Nov). |
| 7 | Settings: gym equipment, library, export, update switch, back to Today | 1 (Settings) + 2 to equipment (Gyms, Kalliokoski) + 2 Back; 1 to library + 1 Back; 1 Export + 1 Back; 1 Restore + 1 Back; 0 to the switch; 1 Back to Today = 13 | 0 | System Back always went one level up, and from Settings to Today. |
| 8 | Read everything at 115 % | — | — | See issues 11–13. The calendar header held in every state I saw. |

## Issues

### Blocker

None. Every question had an answer on the screen, and nothing I did changed sessions or the
program.

### Major

1. **Back from a done session's Open session returns Today to today and loses the day you
   picked.** (Scenario 4)
   - *What I did:* Today → tapped Thu 15 Oct → Open session → system Back.
   - *What happened:* the session opened (`Day C — Bench volume, leg pre…`, 293 min). Back landed
     on Today, but with Sat 17 selected and the strip on this week. Thursday was gone.
   - *Why it matters:* "What did I do last week?" is rarely one day. I check Monday's bench, then
     Thursday's, then compare. Each day costs a re-swipe and a re-tap, between sets. This is the
     same complaint as Program's issue 1 in `program-test-report.md`. Back should return to where I
     was.

2. **On a day that isn't a training day, the plan still puts the next session on today, and every
   later date moves one training day.** (Scenarios 1 and 5. I didn't see it on the phone, because
   today already has sessions. I read it from SPEC, Today → Planned dates, "The next day goes on
   today", and the app's planned-dates code does the same.)
   - *What would happen:* with Mon Wed Fri set, on a Sunday morning with nothing logged, Today
     opens on a Day B hero with Start session dated Sunday. Monday then shows Day C, Wednesday A,
     and so on. When I take Sunday off as planned, it re-plans again on Monday.
   - *Why it matters:* I set training days so the app knows Sunday is rest. Sunday's view then
     looks like I'm behind, and the look-ahead is one session off until Monday comes. "What am I
     doing Wednesday?" gets the wrong answer every rest day. With training days, today should be a
     rest day when it isn't one of them (with Start still one tap away for an extra session).

3. **Swiping the strip changes the week but not the day, so the card under it belongs to another
   week.** (Scenarios 3 and 6)
   - *What I did:* swiped the strip to 2–8 Nov, and back to 14–20 Sep.
   - *What happened:* the header read `November 2026` (or `September 2026`), but the card still
     said `Done · Sat 17 Oct · Kalliokoski` with its sets, under a week with no 17.
   - *Why it matters:* glancing at the phone between sets, I read the card as belonging to the
     week on screen. The date is in the card's small head line, but the month in big type over it
     says otherwise. Either select the same weekday on the new page, or make it obvious the card is
     elsewhere (a "Today" button is shown, but it doesn't tell me which day the card is).

### Minor

4. **After training, the next session is at the bottom of the page.** (Scenario 1) Today opens on
   the done session(s). With two done today, `Next: Day B on …` comes after both cards and all 12
   logged exercises, two full swipes down. With Mon Wed Fri the next day is in next week, so the
   strip doesn't show it either. The first thing I want after a session is "what's next and
   when". Today has that information but puts it last.

5. **A day with more than one session shows only the first one's letter.** (Scenario 4) Fri
   16 Oct had Day A and Day B, and the strip shows `A`. Sat 17 had C and then A, and shows `C`.
   12 Oct had ten sessions and shows `A`. Looking back, the strip claims I didn't train B on
   Friday. Double days are rare, but the strip reads as a log.

6. **Past the program's last day, the calendar says `Rest day`.** (Scenario 3) The last planned day
   is Wed 25 Nov (week 12 Day C). Sun 29 Nov reads `Rest day` with no Next and nothing saying the
   program has ended. Week 12 Day C on 25 Nov is the last session, and nothing marks it so.

7. **Deload + test week isn't marked on Today.** (Scenario 3) Wed 25 Nov reads
   `Planned · Wed 25 Nov · Week 12`, every set 1 × … @ 3. Planned cards drop the block name (by
   design since #58), so nothing says Deload or test. It's the same gap as Program's issue 6.

8. **The strip doesn't say which program week you're on.** (Scenarios 3 and 6) Paging ahead, I see
   letters (B, C, A) but not week 9 or Intensify vs Deload. To know which week 2 Nov is, I have to
   tap a day.

9. **Program's Next line has no date.** (Scenario 2) Program reads `Next  Week 7 · Day B — …`
   while Today knows it's Mon 19 Oct. The training-days row sits right under it, so the date
   would fit.

10. **The weekday toggles repeat letters.** (Scenario 2) `M T W T F S S`: the two T's and two S's
    differ only by position. With a sweaty thumb I checked twice which T was Thursday. Mon Tue Wed
    or two-letter labels would remove the doubt. The Program row already spells `Tue Thu Sat`.

11. **Cut-off text at 115 %.** (Scenario 8)
    - The past session's title: `Day C — Bench volume, leg pre…`.
    - Kalliokoski's Plates row: `8×20 2×15 2×10 2×5 2×2.5 2×…`. The smallest plates are the ones
      I'd check.
    - The exercise library's New exercise button covers the last visible row's muscles
      (`Barbell · Quads, Glutes, Hamstr…`).
    - The calendar header itself held everywhere: `September 2026` and `Oct – Nov 2026` with
      Today and the pencil, and `October 2026 ✎ Set your training days` on today.

12. **Gyms and equipment undersells the gym.** (Scenario 7) Kalliokoski reads `1 equipment item`,
    but the gym holds a plate-loaded shoulder press, dumbbells, plates and pin plates. The gym page
    then opens on a violet card asking for the program's 2 machines and cable station. It's fine,
    but the list row made me think the gym was nearly empty.

13. **Export backup doesn't say when I last exported.** (Scenario 7) The row reads
    `Everything on this phone, in one file`, but the Downloads folder shows backups from 13 Oct.
    After cancelling the dialog it reads the same, which is right, but I can't tell from Settings
    whether I'm four days or four weeks without a backup.

14. **The day card doesn't swipe.** (Scenario 6) A sideways swipe on the card does nothing; only
    the strip pages. Calendar apps let you swipe the day itself to the next day. I don't need
    it, but I tried it twice.

15. **The minutes estimate looks low.** (Scenario 3) Day C planned reads `9 exercises · 13 sets ·
    ~35 minutes`, and week 12 reads `9 sets · ~25 minutes`. With warm-ups and walking between
    machines, 13 sets is closer to 50–60 minutes for me. The owner's own logged times don't help
    (75, 241, 293 min), so I can't say what's right. It reads like rest plus work only, with no
    warm-ups.

## What works and must not be lost

- **The calendar header fits at 115 %.** In every state I saw, the month was whole: today with
  the prompt, off today with Today and the pencil, `September 2026`, `Oct – Nov 2026`.
- **Done, planned and next look like what they are.** Filled pills for done, outlined for planned,
  the ringed today, muted past rest days. I could read the week's shape at a glance.
- **The hero.** Letter, name, `Next · Mon 19 Oct · Week 7`, exercises/sets/minutes and a big Start
  session. On another day it's one line of head, then the name, and the button is a thumb's width
  across the card.
- **Planned days read like Program and like the session.** Same `1 × 8–10 [1] · 1 × 10–12 [0]`,
  same technique glyphs. Looking ahead to Wednesday showed exactly what I'll do.
- **The Next link and the Today button.** Next pages the strip and selects the day. Today brings
  both back in one tap. Rest day → next session is two taps.
- **The strip pages precisely.** Fast, alternating and diagonal swipes each moved exactly one week,
  and never selected a day by accident. It stops at sensible ends.
- **The Training days sheet.** One line says what the days do. Toggles save at once, Done and Back
  both keep them, and the screen behind updates live, so I can see the plan move while I choose.
  Program's row and Today's pencil open the same sheet and agree.
- **Done days as logged.** Sets with partials, myo-reps and drops (`75 × 11 + 3 + 3 + 3 + 3 + 3`)
  with RIR chips, and Open session one tap away.
- **Settings is calm and safe.** Every row says what it holds. System Back from Gyms, a gym and the
  library goes one level up, and from Settings to Today. Export and Restore both stop at the system
  dialog, and backing out changed nothing.

## Spec gaps

- **Today on a non-training day.** Planned dates always put the next day on today. The spec
  doesn't say what training days mean for today itself: whether a day off the plan is a rest day
  (issue 2).
- **Where Back from Open session lands.** The spec says Back comes back to Today, not whether
  Today keeps the selected day and week (issue 1).
- **The selection when the strip pages.** Nothing says whether paging moves the selection or what
  the card shows when its day is off screen (issue 3).
- **Several sessions on one day.** The marker is defined for "a session finished on it", with no
  rule for two or more (issue 5).
- **After the program's last day.** The spec says "A finished program plans nothing", but not how
  the days after the last planned day read while the program is still running (issue 6).
- **Marking the deload and the test on Today** (issue 7, and Program's report).
- **The last backup's date** in Settings (issue 13). The spec only has `Saved …` right after an
  export.

## Resolution

The designer's fixes, in the same PR. Each fix was test-first, and every test went red before the
code changed. Checked on the emulator (AVD `hyperlog`, the phone's size and density, text at
115 %, dark and light) after the owner took the phone back.

| # | Outcome |
|---|---|
| 1 | **Fixed.** Today's selection is held by the host, so Back from Open session returns to the day and week. Choosing Today on the bar starts again on today. `TodayCalendarTest.backFromADoneDaysSessionReturnsToThatDayAndWeek`. |
| 2 | **Owner question.** SPEC's Planned dates rule puts the next day on today, even on a day that isn't a training day. Whether a non-training day should read as a rest day (with Start one tap away) is the owner's call. |
| 3 | **Fixed.** Paging picks the same weekday in the week shown. The pages are whole weeks, so it never falls off the calendar. In SPEC (Today, the calendar). `TodayCalendarTest.pagingTheStripSelectsTheSameWeekdayInTheWeekShown`. |
| 4 | **Fixed.** On today, after training, `Next: Day B on …` comes first, above the done cards. The assertion was added to `TodayCalendarTest.aSessionFinishedTodayShowsItsCardOpensInHistoryAndPointsToTheNextDay`. |
| 5 | **Fixed (designer's call).** The marker shows `CA` for two program days in order, and `A+` for more sessions or for a program day beside an ad-hoc session. TalkBack names every session. `DayMarkerTest` (all four tests). |
| 6 | **Fixed (designer's call).** With training days, the last planned date reads `Last day · Wed 25 Nov · Week 12`. Any date after it reads `Nothing planned` with `Last planned: Day C on Wed 25 Nov ›`. `TodayCalendarTest.pastTheProgramsLastDayNothingIsPlannedAndTheLastDaySaysSo`. |
| 7 | **Kept, owner question.** Marking deload and test weeks on Today is the same open question as Program's issue 6. |
| 8 | **Kept, owner question.** It's the same question as 7: whether the strip should name the program week or block. |
| 9 | **Kept (designer's call).** Program's "No dates" principle (#40, the owner's pick) stands. The training-days row sits right under Next, and Today shows the date one tap away. |
| 10 | **Fixed.** The toggles read `Mo Tu We Th Fr Sa Su`, TalkBack still says the full name, and they fit at 115 % on the emulator. `WeekdayTogglesTest.noTwoDaysShareALabelAndTalkBackKeepsTheirNames`. |
| 11 | **Fixed.** The past session's day name is now a two-line headline under a bar with only Back (`LargeTextTest.aPastSessionsTitleWrapsRatherThanBeingCut`). The Plates row wraps, up to three lines (`LargeTextTest.aGymsPlatesRowShowsEveryPlate`). The library already ended with a 96 dp foot plus the navigation inset. At the end of the list on the emulator, the last row clears New exercise, so there was no change and no test. A row passing under the button mid-scroll is how a floating button works. |
| 12 | **Fixed.** "1 equipment item" counted only machines and cable stations. Every gym also has dumbbells, plates and pin plates. The row now reads `1 machine · dumbbells and plates`, or `Dumbbells and plates` with no machines. `GymScreenTest.theGymsListSaysWhatEachGymHolds`. |
| 13 | **Fixed, no migration.** The export's time is kept in the existing `setting` table (`last_export`), and the Export row keeps showing `Saved Tue 13 Oct, 14:05`. `BackupsTest.theLastExportIsKeptForSettingsToShow` and `SettingsScreenTest.theLastExportStaysOnTheRowAfterwards`. Not yet seen after a real export on a device. |
| 14 | **Kept** (no swipe on the day card), as asked. |
| 15 | **Kept** (the minutes estimate), as asked. |

### Light mode, found by the designer on the emulator (#58)

| Finding | Outcome |
|---|---|
| The gym's setup card looked inverted in light: the picked "kg, with small" had no fill, "lb stacks" was white, and Add had no shape. In light, primary and primaryContainer are the same `#5B3DD0`. No render covered the card, so only the device showed it. | **Fixed.** On an accent card, the picked choice and filled buttons are onPrimaryContainer with violet words, and the other choices sit in a shaded well. The same fix covers Coach's Review button and the session's Finish session. `AccentContrastTest.aChoiceOnTheAccentCardShowsWhichIsPicked` and `AccentContrastTest.aFilledButtonOnTheAccentCardStandsOffIt`. |
| Done markers and the done card's cookie barely showed in light (secondaryContainer on the screen, 1.1:1). | **Fixed.** They are now filled primary with an onPrimary letter. `AccentContrastTest.aDoneDaysFillStandsOffTheScreenAndItsCards`. |
| Connected-group selection is light lilac in some places and deep violet in others (dark only). | **Owner question.** In light both are one violet. Material's own roles differ between toggle buttons and chips, so this is taste. |

## Re-test on the emulator (6fc835b)

- **When:** 2026-10-17, 19:35–19:41.
- **Build:** the worktree's debug APK (`app-debug.apk`, built from 6fc835b; no source newer than
  it) installed on AVD `hyperlog`: 1280 × 2856 at 480 dpi, font scale 1.15. I checked in dark,
  switched to light, and switched back to dark at the end.
- **Data:** the owner's data on the emulator: 19 sessions, no training days, no export recorded.
- **What I touched:** training days (Mo We Fr from the pencil, cleared at the end). I opened three
  past sessions read-only and three gym and library screens. I didn't export or restore. Afterwards
  the database had `weekday_plan` 0 rows and `session` 19 rows, and the app was on Today in dark.

### The issues, again

| # | Verdict | What I saw (dark and light alike unless said) |
|---|---|---|
| 1 | **Verified** | Thu 15 → Open session → system Back lands on Thu 15 with this week's strip. Fri 16 → Open session → the bar's back arrow does the same. Scroll position isn't kept (new issue 18). |
| 3 | **Verified** | From Fri 16, paging forward selects Fri 23. Two pages back select Fri 9 Oct. From Sat 17, six pages forward select Sat 28 Nov. The card always matches the week on screen. |
| 4 | **Verified** | On today, `Next: Day B on Sun 18 Oct ›` (with Mo We Fr: `… on Mon 19 Oct ›`) sits right under the strip, above the done cards. It's the first thing I read. |
| 5 | **Verified** | 12 Oct `A+`, 13 Oct `B+`, 16 Oct `AB`, 17 Oct `CA`, 15 Oct `C`. I can tell two-session days at a glance. In light the done pills are solid violet and stand off the page. |
| 6 | **Verified** (with training days) | Wed 25 Nov reads `Last day · Wed 25 Nov · Week 12`. Sat 28 and Thu 26 Nov read `Nothing planned` with `Last planned: Day C on Wed 25 Nov ›`, and the link selects the 25th. Without training days the same words mislead (new issue 16). |
| 10 | **Verified** | `Mo Tu We Th Fr Sa Su`, all seven fit on one row at 115 % in both themes. |
| 11 | **Verified** | The past session's title wraps to two lines (`Day C — Bench volume, leg` / `press, pulldown`). Kalliokoski's Plates row wraps to `… 2×2.5` / `2×1.25`. At the end of the library, Zercher Squat clears New exercise. |
| 12 | **Verified** | `Kalliokoski · 1 machine · dumbbells and plates`. |
| 13 | **Not verifiable without an export** | The emulator has no export recorded, and the row reads `Everything on this phone, in one file`, as it should. I didn't export, as asked. The owner's earlier exports (13 Oct, before this build) aren't known to the app, so the row stays generic until the next export. |

### Light mode

Today, the training-days sheet, Settings, Gyms and equipment, Kalliokoski and a past session all
read cleanly in light. The gym's setup card shows the picked `kg, with small` as a white pill with
violet words beside a darker `lb stacks`, and Add is a white pill. That's clear. The planned
outlined pills and the Last-day cookie read well on the light page. I found nothing new that reads
wrong in light.

### Cold start

Recorded three cold starts (`am force-stop`, then launch, with `screenrecord`). Each time, after
the splash, Today first draws **today as an untrained day**: the violet hero `Week 7 · Intensify`,
`B — Hinge, close-grip bench, row`, **Start session**, a `B` outlined under Sat 17, and no done
markers anywhere on the strip. Then it swaps to the real day: the markers appear, the Next link
comes in above, and the hero becomes the done Day C card with Open session. The wrong frame lasted
about **0.33 s, 0.32 s and 0.43 s**.

It's misleading but short. The first thing the screen tells me is "you haven't trained today, start
Day B", and the layout jumps under my thumb as it corrects. A thumb already moving toward the big
white Start button can hit it. With one gym that opens the gym sheet rather than starting, so it
isn't destructive, but it's the wrong first impression on every cold open, and a slower phone
would hold it longer.

### New issues

#### Major

None.

#### Minor

16. **Without training days, any later date says `Last planned`, as if the program ends
    tomorrow.** (Scenario 3) With no training days, Fri 23 Oct reads `Nothing planned` and
    `Last planned: Day B on Sun 18 Oct ›`. The program runs to week 12. Only tomorrow is planned
    because I haven't set training days. "Last" reads as the end of the program, and it's the
    default state for anyone who hasn't set days. Something like `Next: Day B on Sun 18 Oct` (and a
    nudge to set training days to see the rest) would say what's true.

17. **Cold start shows an untrained today for about a third of a second.** (Scenario 1) See Cold
    start above: the hero with Start session and no done markers, then the done card. Holding the
    calendar until the sessions are read would remove both the false "start Day B" and the jump.

18. **Back from Open session keeps the day but not the scroll.** (Scenario 4) On Fri 16 I scrolled
    to the second card (Day B), opened it and came back. Fri 16 was still selected, but the page
    was back at the top, on Day A. With two sessions on a day, you scroll again each time.

19. **Back from another tab keeps Today's old selection, while the Today tab starts on today.**
    (Scenario 7) I left Today on Wed 25 Nov, went to Settings → Gyms → Kalliokoski and pressed Back
    three times. Today came back on 25 Nov. Tapping the Today tab instead goes to today. Either is
    defensible, but coming back from Settings to a day five weeks out took me a second to place.
    The Today button is there, so it's minor.

## Resolution of the re-test

| # | Outcome |
|---|---|
| 16 | **Fixed.** With no training days, a date after the next day reads `Not planned yet`, with `Next: Day B on Sun 18 Oct ›` and **✎ Set your training days**, which opens the sheet. `Nothing planned` is kept for dates past the program's real end. The wording is in SPEC (Today, a day with nothing). `TodayCalendarTest.withNoTrainingDaysLaterDatesSayTheProgramGoesOnUndated`. |
| 17 | **Fixed.** Today shows nothing under the navigation bar until the finished sessions are read (`sessions` is null until then). On the emulator a cold start now goes from blank straight to the done day. `TodayLoadingTest.beforeTheSessionsAreReadTodayShowsNoPlanAndNoStart` uses a source that hasn't emitted yet. |
| 18 | **Fixed.** The day's scroll is kept by the host, keyed by the day. Back from Open session on Fri 16's second card returns to that card, and another day opens at its top. `TodayCalendarTest.backFromADoneDaysSessionKeepsWhereTheDayWasScrolled`. |
| 19 | **Kept, recorded in SPEC** (Today, the calendar). Back from another destination returns to the day picked, as Back returns you to where you were. Choosing Today on the navigation bar starts on today. SPEC's navigation rules don't say otherwise. |
