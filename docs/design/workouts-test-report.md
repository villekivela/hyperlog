# Workouts test report: Build me a workout (#33, PR #36)

Tester: experienced lifter persona (`tester-brief.md`), on the owner's phone over wireless adb,
with the DEV build of `coach-workouts`, on 2026-10-16 from 09:11 to 09:26. Data was the owner's
real data: Week 6 · Build, Day A next, gym Kalliokoski, no Changes in force, no workout on Today.

Cleanup: I logged one set in a workout session (1-Arm DB Row, 30 × 12 @ 3), unticked it, and
finished the session with nothing logged, so it was discarded. I accepted one test Change ("TEST
one more RIR today", `adjust_rir` +1 for 16 Oct only) and reverted it. It stays in Program's
Changes list, struck through, as reverted Changes do. I added two test workouts ("Hotel
dumbbells, 45 min", then "Second hotel day" in its place) and removed the last one. Today is back
to Week 6, Day A, with the same prescriptions as before, and History's newest session is still
Day C on Thu 15 Oct. Nothing went to the Claude app: I opened the share sheet four times and backed
out each time. The clipboard still holds my "Second hotel day" test JSON.

Two test artifacts, not app bugs: `adb shell input text` cuts long strings, so my first paste was
truncated (the app said so correctly, see below). And launching the app from adb with `monkey`
stacked a second activity on top of the first, which looked like Coach losing its state. I backed
out of it and used Recents from then on, the way a lifter would come back from the Claude app.

## Tap counts

| # | Scenario | Taps | Notes |
| --- | --- | --- | --- |
| 1 | Hotel gym, 45 min, dumbbells, to the share sheet | 7 (+ typing) | Coach, Elsewhere, Build me a workout, Easy, 45 min, the words field, Ask Claude. An 8th picks Claude in the sheet. |
| 2 | Back is behind: check, pick a focus, ask | 2 from scenario 1's state; 11 in practice | Back, Ask Claude. I first went to History → Progress to see whether back really was behind (3 taps), and coming back to Coach had reset the gym and the options, so I re-picked Elsewhere, Build me a workout, Easy, 45 min (4 more). |
| 3 | Reply back, reviewed, on Today | 2 | Review, Add to Today. The "Claude's reply is ready" card was waiting when I came back through Recents. Paste reply, Add to Today is also 2. |
| 4 | Today with both days | 0 to see, 1 swipe to find Day A's head | The workout card fills the first screen. |
| 5 | Start, log a set, check targets, undo, leave | Start 2 (the card's Start after a swipe, Start in the sheet). One set with my own weight: 8 (kg field, ⌫, 3, 0, →, 1, 2, ✓). Undo: 1 (tap the tick) plus 2 to get back to the exercise. Finish: 3 (^, Finish session, Finish). | With the suggested weight a set is 1–3 taps. |
| 6 | Remove, Undo, remove again | 2 + 1 + 2 | ⋮, Remove; Undo; ⋮, Remove. My first Undo missed: the snackbar had gone before I tapped. |
| 7 | Bad workout paste | 1 to see the problems, 1 to copy them | Paste reply, then the copy icon. |
| 8 | A Change with a workout on Today, and a workout with a Change in force | 2 and 3 | Paste reply, Accept. Paste reply, Add to Today, Replace. Revert took 3 (Coach, Revert, Revert). |

## Issues

### Blockers

None. Every scenario reached the share sheet, a review, Today or a session.

### Major

**1. On Today the program day's Start session sits on the workout card.** (Scenario 4) After Add
to Today, the first screen is the workout card, and the only filled button on it is the floating
Start session, right over the card's bottom edge, partly covering the card's own tonal Start.
Day A's heading is off screen at that point. I'm at a hotel with a workout Claude just built for
me, and the big button under it starts Day A instead: barbell bench and hack squat at a gym with
dumbbells to 30 kg. Starting the program day by mistake moves the position, and on a tired
thumb that's the button I'd hit. The tonal Start was meant to keep one primary action on the
screen. Here that primary action belongs to a day I can't see. Once you scroll, Day A's heading
with Start session under it reads fine. The problem is the first screen.

**2. At Elsewhere the session thinks I'm at Kalliokoski, so the weights are wrong.** (Scenarios
1, 5) I picked Elsewhere and wrote "dumbbells 2-30 kg and a flat bench only". Start's sheet
offered only Kalliokoski ("Trained here last"), with no hotel or no-gym choice, and no word of
which day it starts. The session then suggested 50 kg for 1-Arm DB Row ("50 × 11 · from the old
app") with warm-ups up to 42.5, and the kg quick row offered Kalliokoski's rungs (25, 27.5, 30,
32.5, 35). The session sheet is headed "Session · Kalliokoski". Claude got my equipment in words,
but the app never did, so every exercise starts with a weight I have to type over: 8 taps a set
instead of 1–3. The session will also be filed under a gym I wasn't at. The spec says Start uses
the usual gym for Elsewhere, so this is as designed, but the design falls apart at exactly the
moment Elsewhere exists for.

**3. The focus marks don't show that back is behind.** (Scenario 2) This week, per History:
Chest 18 sets, Lats 4, Rhomboids 2.5, Teres Major 2, Rear Delts 3. That's an obvious back
deficit. The three marks were Forearm Extensors · 0.5 sets, Brachialis · 1 set and Hip Flexors ·
1 set: muscles that only get secondary credit and that nobody trains on purpose. They're the
fewest sets among muscles trained lately, as the spec says, but a lifter reads them as "train
your forearm extensors", which is noise. I had to leave Coach for History to find out back was
behind, and then pick Back by hand. The Back chip works and the prompt sends it with its muscles
(`Back (Lats, Teres Major, Traps, Rhomboids, Lower Back, Neck)`). The marks just point at the
wrong things.

**4. Leaving Coach for another tab drops the gym and the workout's options, but keeps the
words.** (Scenario 2) I went to History to check my weekly sets and came back. Elsewhere was back
to Kalliokoski, Build me a workout was closed, and Easy, 45 min were gone. The words and the
asked state were still there, so the screen looked like my request survived. If I hadn't
noticed, asking again would have sent Kalliokoski's full equipment list for a 1 h Normal day. In
the code, `picked` and `options` are `rememberSaveable` inside the screen while the words live in
the view model, which would explain the split. Checking History before asking is the natural
thing to do when you're about to ask for a focus, so this will happen.

### Minor

**5. The Undo snackbar is gone in about four seconds.** (Scenario 6) My first Undo landed after
it had gone, and the workout was lost. I had to paste it again. In a gym, with a dumbbell in one
hand, four seconds is short. The spec set the standard short duration on purpose. A long
duration, or keeping the removed workout one tap away in the ⋮ menu until the day ends, would
cover a lifter who looks up before tapping.

**6. Finish doesn't say what happens to an empty workout session.** (Scenario 5) "Exercises with
nothing logged are left out. You can resume it from Session done." In fact the session was thrown
away and the workout came back on Today, which is the right outcome, but the dialog promises a
Session done screen that never appeared. I finished not knowing whether I'd just logged an empty
workout to History.

**7. The review leaves out rest and the gym.** (Scenario 3) The review shows sets, reps and RIR.
My JSON asked for 90 s on rows and presses and 60 s on the superset, and the session honoured it
(the timer started at 1:30). But I couldn't see that before Add to Today, nor which gym the
session would run at. For a 45-minute hotel slot, rest decides whether it fits.

**8. Changing the options after asking leaves Paste reply as the next step.** (Scenario 2) After
the first ask I changed the focus to Back. That's a new request, but Paste reply stayed filled
and Ask Claude was the text button. Low cost, since I knew what I wanted, but the "filled button
is the next step" rule says the wrong thing here.

**9. Elsewhere with no words still asks.** (Scenario 1, from the code) With the options open, Ask
Claude is enabled without words. With Elsewhere picked, the prompt then says "The equipment there
is in my words above; plan only with that." with nothing above. Claude would have to guess or ask.
I didn't send this one, so I can't say what Claude does with it.

**10. After accepting a Change, the Change is below the fold.** (Scenario 8) Accept goes to Today,
where the workout card is first and Day A, now at +1 RIR, is under it. Nothing clashes: the
workout kept its own targets (still @ 3) and Day A took the Change. But the screen I land on
after accepting a Change shows something the Change didn't touch.

## What works and must not be lost

- Build me a workout opens Effort, Time and Focus without the keyboard, and they're a whole
  request: seven taps from Coach to the share sheet, words only for the equipment.
- The reply-ready card. I came back through Recents and it was waiting with Review. Two taps from
  there to the workout on Today.
- The review: name, summary, numbered exercises, a superset as 3a/3b, Reject and Add to Today at
  the bottom.
- "Replace today's workout?" names the one coming off ("Hotel dumbbells, 45 min" comes off
  Today). No guessing which is which.
- Bad pastes. A missing name and an unknown id gave "2 problems. Nothing changed. Copy them into
  your Claude chat, then paste the new reply." with `workout.name: is required` and
  `workout.groups[0].slots[0].exercise: unknown exercise "bb_row"`, and a copy button. A truncated
  copy gave one clear message, "Claude's reply is cut off before its JSON ends. Copy the whole
  reply, then paste again.", with no copy button.
- While the workout runs, Today shows only the workout, headed "Workout", with Resume session.
  The program day is out of the way.
- The workout's targets carry into the session: the RIR target is preselected on the set, the
  rest timer uses the workout's rest, and warm-ups recompute once I change the weight.
- Unticking a logged set takes it back in one tap.
- A session finished with nothing logged disappears, the workout returns to Today, and the
  position stays at Week 6, Day A.
- A Change and a workout don't step on each other: the Change edits Day A, the workout keeps
  Claude's prescription.

## Spec gaps

- **Equipment at Elsewhere.** What gym a workout's session runs at when Coach had Elsewhere, and
  how the app learns the equipment there (at least a dumbbell range) so suggestions and quick
  rungs stay within it. Whether such a session is filed under a gym at all.
- **Start's sheet for a workout.** It doesn't name the workout, and it has no "not at a set-up
  gym" choice.
- **Focus marks.** Which muscles count: primary sets only, a minimum lately, or a deficit against
  the muscle's own recent weeks. And whether a group chip should carry its own count (`Back · 8.5
  sets`), since groups are what a lifter thinks in.
- **Coach state across tabs.** The spec keeps the words, gym, options and asked state through the
  trip to the Claude app. It doesn't say what happens when you switch tabs, and the app keeps half
  of it.
- **Changes and workouts.** "Plan edits apply to every day trained on a date in scope" doesn't
  say whether a workout trained in scope is one of those days. The code says no (a deload week
  leaves a workout at full volume). Write it down either way.
- **What Claude knows about today.** The prompt doesn't mention a workout already waiting on
  Today, so asking for a Change, or for another workout, happens without it.
- **Easy and the weight.** Easy asks Claude for 3+ RIR, and the session suggests weights from
  history at that RIR. Whether Easy should also lighten the suggested load isn't said.
- **What finishing an empty workout session says.**
