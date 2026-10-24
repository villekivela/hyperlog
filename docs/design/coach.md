# Coach: design note

The Coach screen is words first: what's going on, in your words, to the Claude app, and Claude's
reply pasted back as a Change or a workout (`SPEC.md`, Coaching; ADR 0003). The rule screen of
ticket #17 (five situations and Limits chips) is gone. The missing-equipment swaps on the start
sheet stay, and are at the end of this note. The tokens of `program-and-today.md` and
`logging-screen.md` apply.

The owner's review on 2026-10-15 found the old screen hard to read: it never said what it does,
the Limits repeated the situations, Time off showed at 0 days, and Pain → Hands buried a list of
six exercise chips. The redesign answers those, with the tokens of the designer
pass below. The screens before it were dropped in #42; `screens/coach.png` is today's Coach on the
phone (dark, Ink and violet, #58).

## Principles

1. **Say what it does.** One line under the title: tell Claude what's going on, and get back a
   Change to your program or a workout. Nothing changes until you accept it.
2. **What the coach already did comes first.** Changes in force are cards at the top, each with
   Revert and Tell Claude how it's going.
3. **One way in.** Words, with starter chips to start from. No form, no limits.
4. **Options only where they mean something.** Effort, time and focus appear only for Build me a
   workout, and they are a whole request: no words needed.
5. **The filled button is the next step.** Ask Claude until you've asked, then Paste reply.
6. **Swaps read as the session will.** "In place of …" over the new name, on Today, in the start
   sheet and in the session, as in the review.
7. **The gym learns what it lacks** the way it learns machines: on the go ("Not at Harbour"), undone
   on the gym screen.

## Tokens

The tokens of `program-and-today.md` and `logging-screen.md` hold; these are Coach's own.

| Token | Value | Use |
| --- | --- | --- |
| Intro | bodyLarge, onSurfaceVariant, 24 dp edge, 8 dp under the bar, 16 dp over the first group | The one line under the title |
| Change in force | segment of a `SegmentedList` (Tones.card), 32 dp outer corners | One per Change, newest first |
| Change row | `SegmentedRow`: `tune` in the 40 dp secondaryContainer circle, summary bodyLarge (2 lines), how long it runs bodyMedium onSurfaceVariant | The Change |
| Change card tap | the whole segment, "See what it changes" | Opens the Change in force, read-only |
| Fold row | 56 dp segment, bodyLarge onSurfaceVariant on the 72 dp text column, `expand_more` at the end | `1 more in force` past two cards |
| Change actions | 48 dp row, end-aligned, 12 dp from the segment's end, 8 dp between: **Revert** text button, **Tell Claude how it's going** tonal button | Under the Change row, in the same segment |
| Composer | one `SectionCard` (Tones.card, 32 dp radius, 12 dp padding) | Gym, starters and the words, one thing to fill in |
| Composer head | labelLarge, onSurfaceVariant, on the 24 dp edge, 16 dp before the gym chips on the same row | "Gym"; the row is left out with no gyms |
| Gym chip | FilterChip, `selectedChipColors()`, `check` leading when picked, 8 dp apart | Single choice; **Elsewhere** last |
| Starter chip | SuggestionChip, `add` leading, 8 dp apart, no head: the `add` says what it does. One row that scrolls sideways, running to the card's edges, so the chip cut off at the edge shows there's more | Puts a sentence into the words |
| Words | OutlinedTextField, label "In your words", 3 lines at least, 16 dp over it | |
| Bottom bar | Tones.bar strip, 28 dp top corners, 16 dp padding, through the gesture area | Paste reply, Ask Claude |
| Next step | filled Button at the end of the bar; the other is a text button whose label sits on the 24 dp edge | Ask Claude, then Paste reply, then Review |
| Reply ready | primaryContainer card, 16 dp radius, inside the bar over its buttons, 12 dp under the strip's top: `check_circle` 24 dp, titleMedium "Claude's reply is ready", filled **Review** | The one accent of the screen |
| Row head | labelLarge onSurfaceVariant in a 56 dp column on the 24 dp edge | Gym, Effort, Time, Focus, so their choices start on one line |
| Build me a workout | FilterChip, first in the starter row: `add` leading, `check` and primaryContainer when open | Opens the options; types nothing |
| Effort, Time | `ConnectedChoice` (connected button group) on Tones.screen inside the card, 8 dp apart; Time with 8 dp content padding so `45 min` fits | Normal and 1 h picked |
| Focus | FilterChips, 8 dp apart, wrapping: the seven groups, Chest, Back, Shoulders, Arms, Legs, Glutes, Core; the three behind this week carry their sets (`Back · 4 sets`) | Nothing picked |
| Workout review | as a Change's: a SectionCard with the name (headlineSmall) and summary (bodyMedium onSurfaceVariant), then the groups as segments with Today's slot rows | "Workout for today", Reject and **Add to Today** |
| Workout card (Today) | a segmented group 16 dp above the day's head: a secondaryContainer head segment ("Workout" labelLarge primary, the name headlineSmall, the summary bodyMedium, ⋮ at the end), one segment per group with Today's slot rows, and a 64 dp segment with **Start** at the end | Start tonal beside a program day, filled without one. The accent head is decided |

## Wireframes

```
 Coach                                              ⚙
 Tell Claude what's going on. It answers in words, or with a
 change you review first.

 ┌──────────────────────────────────────────────────┐ ← a Change in force (segment)
 │ (tune)  Hands need a week off grip work           │
 │         Until Tue 27 Oct                          │
 │                 Revert  [Tell Claude how it's going]│
 └──────────────────────────────────────────────────┘

 ┌──────────────────────────────────────────────────┐ ← the composer card
 │ Gym  [✓ Harbour] [Itis]                           │
 │ [+ Something hurts] [+ Short on time today] [+ N ├→ scrolls sideways
 │ ┌ In your words ────────────────────────────────┐ │
 │ │ Left elbow aches on curls…                    │ │
 │ └───────────────────────────────────────────────┘ │
 └──────────────────────────────────────────────────┘
 ╭──────────────────────────────────────────────────╮
 │ ┌ ✓ Claude's reply is ready          [Review] ┐  │ ← after asking, when the clipboard holds one
 │ Paste reply                        Ask Claude    │   (then neither is filled)
 ╰──────────────────────────────────────────────────╯
```

Before asking the bar reads `Paste reply   [Ask Claude]`; once asked, `Ask Claude   [Paste
reply]`. The filled button always sits at the end, so the next step is always in the same place.

```
 Gym     [✓ Harbour] [Itis] [Elsewhere]
 [✓ Build me a workout] [+ Something hurts] [+ Sho├→
 Effort  ( Easy |▐Normal▌| Hard )                     ← connected button groups
 Time    ( 45 min |▐1 h▌| 1 h 15 | 1 h 30 )
 Focus   [Chest] [Back · 4 sets] [Shoulders]
         [Arms] [Legs · 0 sets] [Glutes] [Core · 0 sets] ← none picked: balanced full body
 ┌ In your words ─────────────────────────────────┐
```

```
 Today
 ┌────────────────────────────────────────────────┐ ← secondaryContainer head
 │ Workout                                      ⋮ │
 │ Hotel dumbbells, 45 min                        │
 │ Upper body with dumbbells only. Back first…    │
 │ ⌖ At Itis                                      │ ← or Elsewhere
 ├────────────────────────────────────────────────┤
 │ 1   Chest-Supported DB Row                     │
 │     3 × 8–12 [1]  ·  2:00 rest                 │
 └────────────────────────────────────────────────┘
 ┌────────────────────────────────────────────────┐ ← the program day, second
 │ Week 2 · Ramp-in                    (▶ Start)  │ ← tonal, beside its heading
 │ Day B — Hinge, close-grip bench, row           │
 ├────────────────────────────────────────────────┤
 │ 1   Barbell RDL …                              │
 └────────────────────────────────────────────────┘
 [Ad-hoc session                              ›]
                                  [▶ Start workout] ← floating: starts the workout
```

With no program the card is followed by the **Ad-hoc session** row, in place of the empty state;
Start workout floats as on any day.

```
 Start session
 ( ) Itis
 (•) Harbour
     Usual on Tuesdays
 Not at Harbour · for this session
 [✓] In place of Leg Extension
     Reverse Nordic
     Lying Leg Curl: nothing here fits       ← no replacement: muted, no checkbox
                                    [Start]
```

## States

Issue #32 (this pass), with the screenshot of each in `screens/` as `light-<name>.png` and
`dark-<name>.png`. They are Robolectric renders from `CoachRenders` (skipped unless asked for:
`RENDERS=<dir> ./gradlew testDebugUnitTest --tests '*CoachRenders*'`, `DARK=1` for dark), since
the phone was locked for most of the pass.

- Coach: empty, nothing in force, Ask Claude disabled (`coach-01-empty`); a Change in force
  (`coach-02-in-force`); words typed (`coach-03-words`); asked, Paste reply filled
  (`coach-04-asked`); Claude's reply ready (`coach-05-reply-ready`); a bad paste with its problems
  (`coach-06-bad-paste`); no program running, no Change cards and no Paste reply
  (`coach-07-no-program`).
- Review of a Change, headed "Change to your program" (`coach-08-review`); one that replaces a
  Change in force, with "Ends: …" in a tertiaryContainer card above it
  (`coach-14-review-replaces`).
- A Change in force opened from its card, read-only, headed "Change in force", with Revert
  (`coach-10-change-in-force`).
- Three Changes in force: two cards and `1 more in force` (`coach-11-folded`).
- Elsewhere picked (`coach-12-elsewhere`).
- A reply copied short: one message, no count and no copy button (`coach-13-cut-off`).
- Program → Changes: a replaced Change struck through, "Replaced 15 Oct"
  (`program-change-replaced`).
- Today: the time-off line with Ease back in (`today-time-off`), which opens Coach with the words
  filled in (`coach-09-ease-back-in`).

Issue #33 (workouts), Robolectric renders from `WorkoutRenders` (same switches as
`CoachRenders`), since the phone stayed with the owner:

- Build me a workout open with its defaults and the focus's three marks
  and the seven groups (`coach-15-workout-options`); Hard, 1 h 15, Hamstrings, Back and Glutes
  picked (`coach-16-workout-focus`); with no program, no marks yet and Paste reply back
  (`coach-17-workout-no-program`, and `coach-07-no-program` with words).
- A workout's review, "Workout for today" with Add to Today (`coach-18-workout-review`).
- Today: the card above the program day (`today-workout`); with no program, above an Ad-hoc
  session row (`today-workout-no-program`); Start's sheet at the gym picked on Coach, Itis, while
  Harbour is the usual one (`today-workout-start`); the workout's session running, headed
  "Workout" (`today-workout-running`); removed, with "Workout removed" and Undo
  (`today-workout-removed`).
- The workout's session, logging with last time, the suggestion and warm-ups
  (`session-workout`), and History listing it under its name (`history-workout`).

Unchanged by the redesign:

- Start sheet: missing swaps for the gym picked (`start-01-missing`); another gym recomputes them.
- Which machine?: **Not at Harbour** under Add machine (`machine-01-not-here`).
- Gym screen: **Not here** list with × to undo (`gym-01-not-here`).

## Interaction rules

- A starter replaces the words only when they're empty; otherwise it's added on a new line.
- Tell Claude how it's going starts the words with the Change and focuses the field.
- The words, the gym, the workout's options and the asked state live outside the screen: they
  survive a tab switch (to History to check the weekly sets) as well as the trip to the Claude
  app (saved state).
- Once asked, Paste reply stays the next step until the request ends: an accept, a revert, or
  the words cleared.
- Tapping a Change card opens it read-only; Revert there asks first, then goes back to Coach.
- On return after asking, the clipboard is read once. A valid reply shows the ready bar; Review
  opens it. Nothing opens by itself.
- Editing the words clears a bad paste's problems; so does Back.
- Accept or Add to Today goes to Today; Reject goes back to Coach as it was.
- Build me a workout opens its options and types nothing, with the keyboard down; tapping it
  again closes them and they leave the request. Ask Claude is enabled while they're open.
- Add to Today asks first when a workout already waits today ("Replace today's workout?").
- Start workout opens the start sheet with the workout's name under the title, at the gym picked
  on Coach, or at **Elsewhere** (no gym) for a workout asked for there; Elsewhere is offered for
  any workout. The program day's tonal Start opens it for the day. ⋮ → Remove takes the workout
  off at once, with a long "Workout removed" snackbar whose Undo puts it back as it was. While
  another session runs, Today shows that session and not the workout.
- Finish on a session with nothing logged says it's dropped (and, for a workout, back on Today).
- Changing the gym or the options after asking ends the asked state: Ask Claude is filled again.
- With Elsewhere picked, Ask Claude waits for words; the field's supporting text reads "Say what
  equipment is there, so Claude plans with it" (`coach-19-elsewhere-needs-words`).
- Accepting a Change from Coach lands on Today scrolled to the program day when a workout is
  above it, so the screen shows what the Change edited.
- Missing swaps start ticked; unticking one keeps the planned exercise for that session. Start
  applies the ticked ones to this session only; the plan is untouched.
- Not at Harbour marks the exercise missing at the gym and swaps it straight away (or leaves it out of
  the session with nothing that fits).

## Check-in (#73)

With a Claude key, or the scripted coach in a debug build, Coach is the check-in (`SPEC.md`,
Coaching → Check-in; ADR 0005): "What's going on?" over eight tiles, then Claude's questions one a
screen, ending in words, a review, or ×. `ui/coach/CheckInScreen.kt`. Without either, the
words-first screen above stays until #74.

### Principles

1. **One question a screen, and the question is the screen.** It is set large (headlineMedium,
   Space Grotesk) at the top; its answers sit right under it, big enough to hit mid-set.
2. **Tiles are tiles.** The eight situations are a 2 × 4 grid of square-ish cards, a symbol over a
   name, so all eight show at once with a Change in force above them. A list of eight rows
   pushed the last one below the fold.
3. **Your words are marked.** Wherever a check-in is read back (the transcript under a Change), what
   you said carries the `subdirectory_arrow_right` symbol; Claude's questions don't. No chat
   bubbles: it's a record, not a conversation.
4. **Waiting is the one moment of motion.** Thinking… is Material 3 Expressive's morphing loading
   indicator, alone in the middle of the screen. Nothing else animates.
5. **A failure says what to do next.** Each failure is a calm centred state (its symbol in the
   cookie shape, a title, one line) with its way forward as the bottom strip's filled button.
6. **The filled button is the next step**, at the end of the bottom strip, as on the rest of Coach.

### Tokens

| Token | Value | Use |
| --- | --- | --- |
| Tile grid | 2 columns, 4 dp apart both ways, 12 dp from the screen edge; the group's four outer corners 32 dp, every other corner 8 dp (`segmentShape` applied to the grid) | The eight tiles, in SPEC's order, row by row |
| Tile | Tones.card, 124 dp tall at least (so every row is the same height whether its names take one line or two), 16 dp padding; the 40 dp secondaryContainer circle top-left, the name titleMedium at the bottom, 8 dp under it, two lines | One situation; the whole tile taps |
| In-force line | a lone `Segment` with `SegmentedRow`: `tune`, `1 change in force` over the newest summary, chevron | Opens Changes in force |
| Question head | "What's going on?" headlineSmall on the 24 dp edge, the intro bodyMedium onSurfaceVariant, 16 dp to the tiles | Tiles step |
| Question | headlineMedium, 24 dp edge, 8 dp under the bar, 24 dp over the answers | Claude's question |
| Answer row | a segment of a group, 72 dp at least, titleMedium on the 24 dp edge, no symbol or chevron; a muscle group's mark joins it (`Back · 4 sets`), the mark in onSurfaceVariant at regular weight | One answer; tapping it sends it |
| Type my own | a lone card 16 dp under the answers, 64 dp: `keyboard` 24 dp onSurfaceVariant at the 24 dp edge, "Type my own" bodyLarge onSurfaceVariant | Closed |
| Own answer | the same card holds a borderless text field (placeholder "Your answer") and a 48 dp filled icon button, `arrow_upward`, "Send", at the end, 8 dp from the card's edge | Open; Send disabled while blank |
| Thinking | `LoadingIndicator` 96 dp in primary, "Thinking…" bodyLarge onSurfaceVariant 16 dp under it, a little above the middle | While a call runs, and until a review opens |
| Claude's words | a `SectionCard`, bodyLarge on the 24 dp edge, 16 dp top and bottom padding | The words ending |
| Problems | a `SectionCard`: `warning` 24 dp and titleMedium in error, the line under it bodyMedium onSurfaceVariant, then each problem (path over message) | The reply Claude couldn't fix |
| Failure | `ShapedSymbol` 72 dp (`cable` no connection, `key` key refused, `toll` spend limit, `block` declined), titleLarge, bodyMedium onSurfaceVariant, centred on the 24 dp edges, 48 dp under the bar | Each failure |
| Transcript | in its card: "The check-in" labelLarge primary; Claude's questions bodyMedium onSurfaceVariant; your words bodyLarge after `subdirectory_arrow_right` 18 dp primary, 4 dp under the question; 12 dp between pairs | Under a Change in force, in Program → Changes' dialog |
| Bottom strip | `BottomActions` (Tones.bar, 28 dp top corners) | Tell me more / **Done**; Cancel / **Try again**; Ask Claude / **Try again**; **Ask Claude**; Ask Claude / **Paste reply** |

### Wireframes

```
 Coach
 ┌──────────────────────────────────────────────┐
 │ (tune) 1 change in force                   › │ ← only while one is in force
 │        Today only: leave out the last ex…    │
 └──────────────────────────────────────────────┘
 What's going on?
 Claude asks what it needs. Nothing changes until you accept.
 ╭─────────────────────╮ ┌─────────────────────╮
 │ (✋)                │ │ (⏱)                 │
 │                     │ │                     │
 │ Something hurts     │ │ Short on time       │
 └─────────────────────┘ └─────────────────────┘
 ┌─────────────────────┐ ┌─────────────────────┐
 │ (⌖)                 │ │ (bed)               │
 │ Different gym       │ │ Tired or slept      │
 │ or none             │ │ badly               │
 └─────────────────────┘ └─────────────────────┘
   … Change my program, Build me a workout,
 ╰ New program ────────┘ └ Something else ─────╯ ← outer corners 32 dp, the rest 8 dp
```

```
 ←  Something hurts                            ×
 Does it hurt when you
 press, or only when you
 grip?                                           ← headlineMedium
 ╭──────────────────────────────────────────────╮
 │ Only gripping                                │ ← 72 dp, titleMedium
 ├──────────────────────────────────────────────┤
 │ Pressing too                                 │
 ╰──────────────────────────────────────────────╯
 ╭──────────────────────────────────────────────╮
 │ ⌨  Type my own                               │ ← opens in place:
 ╰──────────────────────────────────────────────╯   │ Your answer…                  (↑) │
```

```
 ←  Tired or slept badly                       ×      ←  Tired or slept badly        ×
                                                      ╭────────────────────────────────╮
                                                      │ Train as planned, but stop     │
                 ⬢  (morphing)                        │ each set a rep earlier than    │
                 Thinking…                            │ written. …                     │
                                                      ╰────────────────────────────────╯
                                                      ╭────────────────────────────────╮
                                                      │ Tell me more            [Done] │
```

```
 ←  Tired or slept badly                       ×
                  (cookie: key)
       Claude doesn't accept this key.
              Check it in Settings.
   Or ask in the Claude app: this check-in goes
       with it, and you paste the reply back.
 ╭──────────────────────────────────────────────╮
 │                                [Ask Claude]  │ ← then: Ask Claude   [Paste reply]
```

### States

Screenshots in `screens/`, `dark-checkin-…` and `light-checkin-…`; the failures and problems are
Robolectric renders from `CheckInRenders` (the scripted coach plays no failures), the rest from the
emulator with the scripted coach.

- Tiles, nothing in force (`checkin-01-tiles`); a Change in force above them
  (`checkin-02-tiles-in-force`).
- A question with its answers (`checkin-03-question`); Type my own open (`checkin-04-own`);
  focus answers with their marks (`checkin-05-marks`).
- Thinking (`checkin-06-thinking`).
- Claude's words with Tell me more and Done (`checkin-07-words`).
- The problems of a reply Claude couldn't fix, Try again (`checkin-08-problems`).
- No connection (`checkin-09-no-connection`); key refused (`checkin-10-key-refused`); spend limit
  (`checkin-11-spend-limit`); declined (`checkin-12-declined`); after Ask Claude, a bad paste's
  problems under it (`checkin-13-bad-paste`).
- Add to Today while a session runs: Today's snackbar (`checkin-17-added-mid-session`, dark).
- Changes in force (`checkin-14-in-force`); a Change in force with the check-in that made it
  (`checkin-15-transcript`).
- The emulator shots are at font scale 1.15, the owner's; the tiles at 1.3 (`checkin-16-large-text`)
  still fit with a Change in force, the last row just under the fold.

### Interaction rules

- A tile, an answer and Send each start a call at once: Thinking shows in their place. No
  confirm step.
- Type my own opens its card in place with the keyboard up; Send (or the keyboard's send key)
  sends the trimmed words, and is disabled while they're blank. Its words are kept outside the
  screen (`CheckInViewModel.draft`), so a half-typed answer survives a tab switch; they're cleared
  when an answer goes or the check-in ends.
- Back (or Reject) to a question you answered in your own words opens Type my own with them, the
  cursor at the end, so a wrong guess from Claude is one edit away.
- On a review from a check-in, × drops the check-in, as on every step (its label is "Close the
  check-in"); Reject and Back step back to the question before.
- Add to Today while a session runs lands on Today with a long snackbar, "Workout added. It's on
  Today once this session ends.", since Today shows the running session's day
  (`checkin-17-added-mid-session`).
- The bar's title is the tile (or "How's it going?", "Easing back in"); Back is one step back;
  × drops the check-in at any step.
- The navigation bar stays through the check-in, so another tab can be checked and the check-in
  is still there on return; it hides on the Changes in force page, a sub-screen.
- After Ask Claude on a failure, Paste reply becomes the filled button and Ask Claude moves to the
  text button; a bad paste shows its problems under the failure, scrolled into view.
- Reverting the last Change in force closes the page: the tiles and the bar come back.

### Pass notes (#73)

- First build: eight full-width rows put Something else below the fold with a Change in force;
  the answer rows carried chevrons (they send, they don't navigate); Type my own was a loose text
  button and its field an outlined box under the group; Thinking was a small spinner; the words
  and problems sat on the bare screen; failures were left-aligned text; the bar stayed on the
  Changes in force page; How's it going? was titled "Check-in".
- First pass: the grid; answers without chevrons; Type my own as its own card that opens in
  place with a filled Send; the expressive loading indicator; words and problems in cards;
  centred failures with their symbol; the bar hidden on Changes in force; the bar titled
  How's it going? or Easing back in; the intro cut to one sentence a job.
- Second pass: rows of tiles were uneven (one-line names shorter than two-line), so tiles got a
  height that holds two lines; the marks went quieter than the answer; the own answer's text
  moved onto the 24 dp edge; the strip's text button onto the 24 dp edge; the bad paste's
  problems onto the card's edge; the bar came back when the last Change is reverted (it stayed
  hidden).
- Tester's report (`checkin-test-report.md`), fixed: × on a review now drops the check-in (it
  stepped back, like Reject); typed answers survive a tab switch and come back on Back or Reject;
  the scripted coach offers Type my own on the focus and "which exercise" questions (effort and
  time stay fixed); Add to Today mid-session says where the workout went. Left for the owner:
  what Reject should lead to, Tell me more replacing the first answer, the review not showing
  your answers, and more gyms than an answer list holds.

## Pass notes

- First pass on the phone: with two Changes in force, the starters wrapped to three rows and
  pushed the words field below the fold. The gym chips moved onto their head's row and the
  starters became one sideways-scrolling row, so the field shows with two cards.
- Second pass: the intro went from three lines to two.
- #33, first pass (renders): `45 min` was clipped to `45` in the four-way Time group, so its
  buttons got 8 dp content padding; with All muscles open the marked muscles fell into body
  order, out of sight, so they stay first; the card's Start was a filled button competing with
  the program day's Start session, so it is tonal there and filled only with no program day.
- #33, second pass: the intro grew to three lines with "or a workout" and pushed the words down,
  so both intros were cut back to two ("It answers in words, a change or a workout you review.";
  with no program, "It answers in words or builds you a workout for today."). The focus folds to
  its marks and picks: all 29 muscles at once filled the screen.
- Tester's report on workouts (`workouts-test-report.md`), fixed: a workout added for today is
  today's main Start (Start workout floats; the program day is the second card with its heading
  and a tonal Start together); Elsewhere starts without a gym, and the sheet offers it and names the
  workout; the focus marks count groups, on the group chips; the gym and options survive a tab
  switch; the Undo snackbar is long; Finish on an empty session no longer promises Session done;
  the review and the card show each exercise's rest and the gym. Renders only: the phone was
  locked for this pass (`today-workout`, `today-workout-program-day`, `today-workout-start`,
  `today-workout-start-elsewhere`, `today-workout-no-program`, `today-workout-removed`,
  `coach-15`, `coach-16`, `coach-17`, `coach-18`, `coach-12-elsewhere`). Then 8 to 10: a new
  gym or options after asking is a new request; Elsewhere needs words; an accepted Change lands on
  the program day. Left for later: the report's spec gaps.
- Owner's answers (2026-10-16): the card's secondaryContainer head stays, as decided. Focus
  picks seven muscle groups in place of All muscles (29 chips), with the per-muscle marks first.
  Remove gets Undo in a snackbar instead of a confirmation.
- Tester's report (`coach-test-report.md`), fixed: a card opens its Change; the prompt carries
  each Change's id and operations, and a reply can end or replace one (`replaces`); Elsewhere;
  the asked state ends; a reply copied short says so once; a third Change folds. The renders for
  these are from Robolectric: the phone went back to the owner before they could be checked on
  it.
