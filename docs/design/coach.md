# Coach: design note

Coach is a check-in with Claude (`SPEC.md`, Coaching; ADRs 0003 and 0005). It opens on "What's
going on?" over eight tiles. With a Claude key, a tile starts the wizard (the second half of this
note). Without one, a tile opens **Ask Claude**: one screen for your words, then the Claude app, and
Claude's reply pasted back as a Change, a workout or a program. The old words-first screen (gym
row, starter chips, Build me a workout's options) is gone with #74. The tokens of
`program-and-today.md` and `logging-screen.md` apply.

## Ask Claude, without a key (#74)

`ui/coach/AskClaudeScreen.kt`. A step of the wizard in all but name: the bar names the tile (or
"How's it going?", "Easing back in") with Back, the tab bar is hidden, and the words are the one
thing to fill in.

### Principles

1. **One thing to fill in.** The tile's sentence is already in the field, with the caret at its end;
   the field is focused on opening, so the keyboard is up and the sentence is ready to finish.
2. **Say what it does, once, in two lines.** Under the bar: Ask Claude sends this with your plan and
   sessions; copy its reply, then paste it here. (The first pass said more and ran to four lines.)
3. **The filled button is the next step.** Ask Claude until you've asked, then Paste reply, then
   Review when the reply is found.
4. **A tile is a request.** Ask Claude is enabled with the words cleared, because the prompt quotes
   the tile, whose own sentence (`Something hurts.`) says what the check-in is about.

### Tokens

| Token | Value | Use |
| --- | --- | --- |
| Intro | bodyLarge, onSurfaceVariant, 24 dp edge, 8 dp under the bar, 16 dp over the card | The one line under the title |
| Words | `OutlinedTextField` in a `SectionCard` (Tones.card, 32 dp radius, 16 dp padding), label "In your words", 3 lines at least | The tile's sentence, to edit |
| Bottom bar | Tones.bar strip, 28 dp top corners, 16 dp padding, through the gesture area | Paste reply, Ask Claude |
| Next step | filled Button at the end of the bar; the other is a text button whose label sits on the 24 dp edge | Ask Claude, then Paste reply, then Review |
| Reply ready | primaryContainer card, 16 dp radius, inside the bar over its buttons, 12 dp under the strip's top: `check_circle` 24 dp, titleMedium "Claude's reply is ready", filled **Review** | The one accent of the screen |
| Paste problems | titleMedium error "Not a reply Hyperlog can use", count line, each problem as program import shows it, copy button for Claude | Under the card, scrolled into view |

### Wireframe

```
 ←  Something hurts
 Ask Claude sends this with your plan and
 sessions. Copy its reply, then paste it here.

 ┌──────────────────────────────────────────────────┐
 │ In your words                                     │
 │ Something hurts: ▌                                │
 │                                                  │
 └──────────────────────────────────────────────────┘

 ╭──────────────────────────────────────────────────╮
 │ Paste reply                        [Ask Claude]  │
 ╰──────────────────────────────────────────────────╯
```

Once asked the bar reads `Ask Claude   [Paste reply]`. Coming back from the Claude app with a reply
that validates, a "Claude's reply is ready" card with **Review** sits above it and Paste reply is
the plain button.

### States

Words with the sentence (`coach-03-words`); asked (`coach-04-asked`); reply ready (`coach-05-reply-ready`); reply ready; a bad paste (`coach-06-bad-paste`); a reply copied
short (`coach-13-cut-off`); Ease back in (`coach-09-ease-back-in`).

### Interaction rules

- Cleared words end the asked state: Ask Claude is filled again. Editing the words closes the paste
  problems.
- Back closes the screen to the tiles and drops the words; switching tabs keeps them (the tab bar is
  hidden while it is open, so the way out is Back).
- A reply found on return shows the card and hides the keyboard; nothing opens by itself.
- A Change accepted from here lands on Today scrolled to the program day, and keeps the tile and your
  words as its check-in.

### The start sheet's missing-equipment swaps

- Accepting a Change from Coach lands on Today scrolled to the program day when a workout is
  above it, so the screen shows what the Change edited.
- Missing swaps start ticked; unticking one keeps the planned exercise for that session. Start
  applies the ticked ones to this session only; the plan is untouched.
- Not at Harbour marks the exercise missing at the gym and swaps it straight away (or leaves it out of
  the session with nothing that fits).

## Check-in (#73)

With a Claude key, or the scripted coach in a debug build, Coach is the check-in (`SPEC.md`,
Coaching → Check-in; ADR 0005): "What's going on?" over eight tiles, then Claude's questions one a
screen, ending in words, a review, or ×. `ui/coach/CheckInScreen.kt`. Without either, a tile opens
Ask Claude above.

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
| Bottom strip | `BottomActions` (Tones.bar, 28 dp top corners) | Tell me more / **Done**; **Try again**; Ask Claude / **Try again**; **Ask Claude**; Ask Claude / **Paste reply** |

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
- The navigation bar hides while a check-in is under way, as in a session (owner, 2026-10-24),
  and on the Changes in force page, a sub-screen; it shows on the tiles.
- Reject on a review steps back with Type my own open, even on a question that didn't offer it,
  so you can say what was wrong.
- Tell me more adds a card: Claude's earlier words stay above in onSurfaceVariant, the new words
  below in onSurface, scrolled into view.
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
  time stay fixed); Add to Today mid-session says where the workout went.
- Owner's answers (2026-10-24): the bar hides during a check-in; Reject steps back with Type my own
  open; the review doesn't show your answers (Back does); Tell me more adds a step under the
  words; with more than 3 gyms Claude offers the 3 likeliest and Elsewhere, Type my own for the
  rest; the key-refused title keeps "Check it in Settings".

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
