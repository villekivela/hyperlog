# Check-in test report: the wizard with Claude (#73)

Tester: experienced lifter persona (`tester-brief.md`), on the emulator (`emulator-5554`) with the
current debug build (`com.hyperlog.app.dev`), Settings → Scripted coach on, on 2026-10-24 around
17:15–17:26. Data: the starter program in Week 7 · Intensify, Day B today, one gym (Kalliokoski).
A Day B session was already running when I started (156 minutes on the clock, RDL, row, curl and
crossover partly logged). I left it running.

The scripted coach plays canned replies, so I judged the flow and the screens, not the wording.
It also answers in 1–6 s, so Thinking showed long enough to see on every step.

State left behind: two Changes I accepted are still in force ("Today only: leave out the last
exercise to fit 30 min." and "For a week: an easier-on-the-joints alternative and one more rep in
reserve."). One workout ("Upper body, 45 min", Elsewhere) was added to today. Scripted coach is on
and no check-in is open.

## First impression

The tiles read at a glance: eight situations, all on one screen even with the in-force line above
them. I'd tap Something hurts for a sore hand without thinking about it. One big question with a
few big answers is right for the gym. It's faster than typing to Claude, and it never felt like a
chat window.

## Tap counts

Waiting on Thinking is not counted. Typing counts as one.

| # | Scenario | Taps | Notes |
| --- | --- | --- | --- |
| 1 | Something hurts → Change → Accept | 4 | Tile, Hands or wrists, Only gripping, Accept. Lands on Today with the swap showing ("In place of Barbell RDL"). |
| 1 | In-force line → page → Change → back → How's it going? | 4 | Line, card, ×, How's it going?. The check-in shows under the summary, my answers marked. |
| 1 | How's it going? to its end | 2 | First answer, Done. |
| 2 | Short on time → Change for today → Accept | 3 | Tile, 30 min, Accept. |
| 3 | Change my program → review → Reject | 4 | Tile, Close-Grip Barbell Bench Press, I don't like it, Reject. Reject leaves me on "Why change it?". The wrong first reply never showed. Thinking just ran longer. |
| 4 | Build me a workout → Add to Today | 5 | Tile, Hard, 1 h, Back · 0 sets, Add to Today. None of the three questions offered Type my own. |
| 5 | Different gym → Elsewhere → own words → review | 4 + typing | Tile, Elsewhere, Type my own, typing, Send on the keyboard. A named gym takes 2 to a review (tile, Kalliokoski). |
| 6 | Tired → Tell me more → Back → Done | 5 | Tile, 5 to 7 hours, Tell me more, Back, Done. |
| 7 | Back/× at every kind of step | 1 each | All one tap. Details below. |
| 8 | Scripted coach off and on again | 2 each way | Settings tab, switch. |

Scenario 7 in detail:
- Back from Thinking stops the call and shows the question just answered. It stays there and no
  late reply jumps in. Good.
- Back from Claude's words shows the question before them. Back from the first question shows the
  tiles. Back from the tiles leaves Coach for Today.
- Back (or Reject, or system Back) on a review shows the question before it.
- × on a question or on Thinking drops the check-in, with no confirm.
- Switching to History during a question or during Thinking, then coming back: the check-in is
  there, and a reply that arrived while I was away is waiting.
- Home on a question, then `am start`: the same question.
- Force-stop on a question, and force-stop 0.1 s after tapping an answer: after relaunch, the
  question is still there, and the interrupted call was asked again and its reply shown. Relaunch
  opens the running session first, not Coach. That's the session's rule, not the check-in's.
- Scripted coach off with a check-in open: Coach shows the old Ask Claude screen. Back on: the open
  check-in comes back on the same question.

## Issues

### Blocker

None. Every scenario could be finished, and nothing was lost to a kill or a tab switch.

### Major

1. **× on a review doesn't drop the check-in. It acts as Back.** (Scenario 5, 7.) On the
   workout review I tapped the top-left × expecting to drop the check-in, like the × on every
   question. I landed back on "Where are you training today?" instead. The review puts its × where
   the questions put ←, and the questions put their × on the right. Two × icons, two meanings, on
   consecutive screens. In the gym I'd tap × to get out, find myself still in the check-in, and tap
   × again. That works, but only by luck. SPEC says "× at any step drops the check-in".

2. **Add to Today with a session running shows nothing.** (Scenario 4.) After Add to Today I
   landed on Today, which showed the running session's Day B. The workout was nowhere and there
   was no snackbar. The spec does say Today shows the running session's day, but from the lifter's
   side the button looked like it failed. Mid-session is exactly when "build me something else" is
   asked (machine taken, gym too busy). Without even a line saying "Added. Finish this session to
   start it", I'd ask again and get a second workout.

3. **When Claude leaves out Type my own, there's no way out of its answers.** (Scenario 4.) All
   three workout questions (effort, time, focus) came without Type my own. The focus offered Chest,
   Back, Shoulders and Balanced full body. I couldn't ask for arms or legs, or for two groups at
   once (SPEC says focus is "any of the seven"). The time question stops at 45 min. A 4-answer cap
   plus no free answer means Claude decides what I'm allowed to want. I'd keep Type my own on
   every question. It costs one row.

4. **Typed answers vanish.** (Scenarios 5, 7.) I typed "Hotel gym: DBs to 25kg and a cable", got a
   workout, and rejected it. Back on "What's there?" the field was closed and empty. A half-typed
   answer also disappears if I switch to History and back. Typing on a phone between sets with
   chalky thumbs is the slowest thing in this flow. Having to retype it after a Reject is the one
   step I'd swear at.

5. **Reject only steps back. It can't say what was wrong.** (Scenario 3.) I picked Close-Grip
   Bench, and the Change swapped the RDL. That's the canned reply, but a real Claude will miss
   sometimes too. Reject put me on "Why change it?" with no sign of what I'd answered. Tapping the
   same answer again got the same proposal. To say "wrong exercise" I'd need Back twice and then
   Type my own on the exercise question. Reject should lead to "What's wrong with it?" (or at
   least Type my own), not to a re-ask.

6. **The exercise question can't reach most of the program.** (Scenario 3.) "Which exercise do you
   want to change?" listed 3 exercises from today's day. The program has about 20. Anything else
   has to be typed by name, spelled the way the library spells it. That's a picker's job (Today's
   or Program's list), not a 4-answer question.

### Minor

7. **Claude's words are gone after Done.** (Scenario 6.) "Stop each set a rep earlier than
   written. If the first work set feels heavy, keep the weight and drop the last set" is advice
   I need on set 3, not on the Coach tab. After Done it exists nowhere: not on Today, not in the
   session. I'd screenshot it, which is a sign it should stay somewhere.
8. **Tell me more replaces the first answer.** (Scenario 6.) Only one answer shows at a time, so
   reading both means going Back and forth.
9. **How's it going? doesn't say which Change it's about, and Back leaves its page.** (Scenario 1.)
   The question is generic ("What's on your mind?") with no line naming the Change, so with two
   in force I can't tell which one I'm reporting on. Back from its first question goes to the
   tiles, not back to Changes in force where I started.
10. **The review doesn't show what I answered.** (Scenarios 1–3.) The bar reads "Change to your
    program", not the tile, and my answers only appear later, under a Change in force. Before
    accepting, I'd like the one-line reason ("Hands or wrists · only gripping") above the summary.
    That's how I check that Claude understood me.
11. **The answered row isn't marked on Back.** (Scenarios 3, 7.) After Back or Reject, nothing
    shows which answer I picked last time.
12. **Revert on "Change in force" looks disabled.** (Scenario 1.) It's a faint outlined button on
    the strip. I tapped it to find out, and it works (asks first). On a page with one button, it
    looks like no button at all.
13. **The Today-only removal leaves no trace on Today.** (Scenario 2.) The crossover disappeared,
    and its partner still reads "6a" with no 6b. The swap gets "In place of Barbell RDL". A
    removal gets nothing. I'd look for the missing exercise.
14. **Every tile waits on Thinking first, even Something else.** Something else then asks
    "What's on your mind?" with two guesses. For that tile I'd rather type right away. Also,
    SPEC says of Change my program "The tap costs no call", but the tap showed Thinking before
    "Which exercise".
15. **Same icon twice.** The in-force line and the Change my program tile both use `tune`.
16. **Old and new wording disagree.** With the scripted coach off, the cards say "Tell Claude how
    it's going". The check-in says "How's it going?".
17. **Easy / Normal / Hard come without their meaning.** SPEC defines them (3+ RIR, 1–2, 0–1 with
    techniques), but the rows don't say it. Ask three lifters what "Normal" means and you'll get
    three answers.

## What works well and must not be lost

- **One question a screen with big rows.** Every answer is a thumb-sized target with nothing else
  to hit. Three taps from the tab to a Change for a sore hand is faster than any app I've used.
- **The tiles fit on one screen** with a Change in force above them, and every row of tiles is the
  same height.
- **The check-in survives everything**: tab switches, Home, force-stop mid-question and mid-call,
  and switching the scripted coach off and on. A reply that comes in while I'm on another tab is
  waiting when I return.
- **Back from Thinking really cancels.** No late reply jumps in after I've backed out.
- **A bad first reply stays hidden** behind Thinking. I only ever saw a valid review.
- **The check-in under a Change in force.** "Something hurts → Hands or wrists → Only gripping"
  under the summary tells me in two seconds why my RDL is now a DB RDL. The arrow marking my words
  makes it read as a record, not a chat.
- **The review is the one I already know**: strikethrough old sets, new ones with RIR chips, the
  "In place of" line, and Today showing the swap right after Accept.
- **A running session follows an Accept**: unlogged sets took the new RIR, and logged ones stayed.
- **Named gyms and Elsewhere on the workout review** ("At Kalliokoski" / "Elsewhere").
- **The keyboard's send key sends** an own answer, so I didn't have to reach for the arrow.

## Spec gaps

- **More gyms than answers.** An ask has 2–4 answers. With three gyms plus Elsewhere that's
  already 4. A fourth gym (I train at three, plus a hotel now and then) can't fit. SPEC should say
  what happens: a gym picker, the most recent three, or Type my own as the overflow.
- **A workout with no gym question.** The Build me a workout review read "Elsewhere" though I was
  never asked and I have one gym. SPEC says the gym is asked only when equipment matters, but a
  workout always needs equipment. When the gym isn't asked, it should default to the last
  session's gym, not to none.
- **Type my own on every question?** SPEC leaves it to Claude ("whether you may type your own").
  I'd make it always on. See major 3.
- **Exercise choice.** The answer cap of 4 doesn't fit "which exercise". SPEC needs a way to pick
  any slot of the program (major 6).
- **Where words live after Done.** A words ending has no home. SPEC could say whether today's
  advice shows on Today or in the session (minor 7).
- **What Reject sends back.** SPEC makes Reject equal to Back. It could instead tell Claude the
  proposal was rejected and ask what was wrong (major 5).
- **Feedback after Add to Today while a session runs** (major 2): SPEC says what Today shows, but
  not how the lifter learns the workout was kept.
- **Drafts.** SPEC keeps the check-in step by step but says nothing about a half-typed answer, or
  the typed answer of a step you go back to (major 4).
