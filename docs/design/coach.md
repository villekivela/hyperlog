# Coach: design note

Ticket #17 adds the offline rule coach: the Coach screen, the time-off line on Today, and the
missing-equipment swaps when a gym is picked. Every proposal ends in the review screen of
`changes.md`, except missing equipment, which is for one session and never a Change. The tokens of
`program-and-today.md` and `logging-screen.md` apply. Screens: `screens/coach.png`,
`coach-pain.png` (phone).

## Principles

1. **One question, then the diff.** The screen asks what's in the way; the answer is always the
   Change review, or one line saying the plan can stand.
2. **Defaults from the situation.** Picking a situation sets its limits (Pain → Hands → No grip);
   every limit stays a chip you can turn off or on.
3. **Details open in place.** The pain areas and the minutes appear under their row only when it's
   picked, so the list stays five lines.
4. **Swaps read as the session will.** "In place of …" over the new name, on Today, in the start
   sheet and in the session, as in the review.
5. **The gym learns what it lacks** the way it learns machines: on the go ("Not at Harbour"), undone
   on the gym screen.

## Tokens

| Token | Value | Use |
| --- | --- | --- |
| Situation row | 56 dp min, radio 48 dp at 12 dp, text at 64 dp | as the start sheet's gym rows |
| Detail indent | 64 dp | area chips, pain note, minutes stepper (its − at 52 dp so the icon centres under the text) |
| Area / limit chips | FilterChip, 8 dp gaps, FlowRow | a limit that's on shows `check` |
| Minutes | titleMedium tabular, 64 dp wide, 5-min steps, 20–120 | `−  45 min  +` |
| Limits head | labelLarge onSurfaceVariant, 24 dp top | as Changes' section heads |
| Bottom bar | a surfaceContainerHighest strip rounded at the top, over the navigation bar: the message (bodyMedium, muted) left of **Review change** | the message holds "Resume as planned." |
| Time-off line | `history` 18 dp tertiary, bodyMedium muted, TextButton **Ease back in** | under Today's day name |
| Missing swaps | labelLarge head `Not at Harbour · for this session`; checkbox rows 56 dp, text at 64 dp | in the start sheet under the gyms |
| Coach entry | the navigation bar's Coach (`sports`), always (no program: Ask Claude only; finished: rules say so) | |

## Wireframes

```
 ←  Coach
 What’s in the way?                         ← headlineSmall
 ( ) Time off
     3 days since your last session
 (•) Pain
     [Hands] [Shoulder] [Elbow]
     [Knee] [Lower back]
     Keep pain at 3/10 or less, no worse the next morning. Revert the Change when it’s gone.
     Worse the next morning after             ← labelLarge, only while a hand Change is in force
     [Cable Lateral Raise] [Triceps Pressdown] …   ← filter chips, one at most
 ( ) No gym
     Dumbbells and bodyweight at home
 ( ) Short on time                          ← picked: −  45 min  +
 ( ) Poor recovery
     Bad sleep or stress
 Limits
 [✓ No grip] [No axial load] [No overhead]
 [Home] [Max 45 min]
 ───────────────────────────────────────────
 (Resume as planned.)        [Review change]
```

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

- Coach: nothing picked (Review disabled, `coach-01`); Pain with areas (`coach-02`); Short on time
  with the stepper (`coach-03`); Time off of a week or less, "3 days since your last session.
  Resume as planned." (`coach-04`); the hand review (`coach-05`) and Today after it (`coach-06`).
- Today: more than a week since the last session and no Change accepted today: the time-off line
  (`coach-07`), whose Ease back in opens the re-entry review (`coach-08`). After two weeks the same
  button opens Repeat week.
- Start sheet: missing swaps for the gym picked (`start-01-missing`); another gym recomputes them.
- Which machine?: **Not at Harbour** under Add machine (`machine-01-not-here`).
- Gym screen: **Not here** list with × to undo (`gym-01-not-here`).

## Interaction rules

- Picking a situation (or a pain area) resets the limits to its defaults; chips edit them after.
  Any edit clears the message.
- Review change runs the rules at once. A Change opens the review; Accept returns to Today, Reject
  to the coach as it was.
- Coach preselects Time off when more than a week has passed.
- Missing swaps start ticked; unticking one keeps the planned exercise for that session. Start
  applies the ticked ones to this session only; the plan is untouched.
- Not at Harbour marks the exercise missing at the gym and swaps it straight away (or leaves it out of
  the session with nothing that fits).

## Ask Claude

Ticket #18 adds **Something else** to the Coach screen: free text for the Claude app, and the
Change pasted back (ADR 0001). Earlier renders (dark, and
`ask-04-problems-light`).

### Principles

1. **One more situation, not another screen.** Something else is the sixth row; its details open
   in place like Pain's areas, and Limits give way to one line on what is shared.
2. **The filled button is the next step.** Ask Claude until you've asked, then Paste change. The
   other stays a text button in the same bar.
3. **Problems read like import's.** Path in small monospace over the message, the same component,
   with one way back to Claude: copy them.
4. **Nothing changes without the review.** A good paste opens the same diff as every Change.

### Tokens

| Token | Value | Use |
| --- | --- | --- |
| Text field | OutlinedTextField, 3 lines min, label "In your words", 8 dp top, at the 64 dp indent | the words |
| Shared line | bodyMedium onSurfaceVariant, 8 dp top | what Ask Claude shares |
| Problems head | titleMedium in `error`, 24 dp top; `content_copy` IconButton at the end | "Not a Change Hyperlog can use" |
| Problems | the count line (bodyMedium muted), then `Problem` rows (8 dp vertical) | as program import |
| Bottom bar | `TextButton` (label at the 24 dp margin) left, `Button` right | Paste change has `content_paste` 18 dp |

### Wireframe

```
 (•) Something else
     Talk it through with Claude
     ┌ In your words ───────────────────┐
     │ Left elbow aches on curls…       │
     └──────────────────────────────────┘
     Shares this, your program, the last 4 weeks of sessions and the Change format with the
     Claude app. Copy its reply, then Paste change.
     Not a Change Hyperlog can use        ⧉     ← only after a bad paste, scrolled into view
     3 problems. Nothing changed; ask Claude to fix them, then paste again.
     operations[0].slot
     unknown slot "rows"
 ───────────────────────────────────────────
 [⎘ Paste change]                [Ask Claude]   ← before asking
 Ask Claude                  [⎘ Paste change]   ← after
```

### States

Something else picked, empty (`ask-01`, Ask Claude disabled); words typed (`ask-02`); asked
(`ask-03`, Paste change filled); a bad paste (`ask-04-problems`); a good paste's review (`ask-05`).

### Interaction rules

- The words and the asked state survive the trip to the Claude app (saved state).
- Editing the words or picking another situation clears the problems; Back clears them too.
- Paste reads the whole clipboard; Claude's full reply works, so does the JSON alone.

## Expressive pass

The coach is a destination of the navigation bar, on surfaceContainer. The situations are one
card; the picked one is lifted into secondaryContainer under its radio. Its details (areas, the
pain rule, the exercises worse after) open inside the card. Filter chips pick in
primaryContainer, since secondary container is too close to a card in a grey palette. Limits are a
group headed in primary.
