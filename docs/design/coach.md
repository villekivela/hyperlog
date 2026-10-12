# Coach: design note

Ticket #17 adds the offline rule coach: the Coach screen, the time-off line on Today, and the
missing-equipment swaps when a gym is picked. Every proposal ends in the review screen of
`changes.md`, except missing equipment, which is for one session and never a Change. The tokens of
`program-and-today.md` and `logging-screen.md` apply. Screens in `screens/coach-*.png`,
`start-01-missing.png`, `machine-01-not-here.png` and `gym-01-not-here.png` are Robolectric renders
(dark theme), not phone shots.

## Principles

1. **One question, then the diff.** The screen asks what's in the way; the answer is always the
   Change review, or one line saying the plan can stand.
2. **Defaults from the situation.** Picking a situation sets its limits (Pain → Hands → No grip);
   every limit stays a chip you can turn off or on.
3. **Details open in place.** The pain areas and the minutes appear under their row only when it's
   picked, so the list stays five lines.
4. **Swaps read as the session will.** "In place of …" over the new name, on Today, in the start
   sheet and in the session, as in the review.
5. **The gym learns what it lacks** the way it learns machines: on the go ("Not at Kamppi"), undone
   on the gym screen.

## Tokens

| Token | Value | Use |
| --- | --- | --- |
| Situation row | 56 dp min, radio 48 dp at 12 dp, text at 64 dp | as the start sheet's gym rows |
| Detail indent | 64 dp | area chips, pain note, minutes stepper (its − at 52 dp so the icon centres under the text) |
| Area / limit chips | FilterChip, 8 dp gaps, FlowRow | a limit that's on shows `check` |
| Minutes | titleMedium tabular, 64 dp wide, 5-min steps, 20–120 | `−  45 min  +` |
| Limits head | labelLarge onSurfaceVariant, 24 dp top | as Changes' section heads |
| Bottom bar | divider, then the message (bodyMedium, muted) left of **Review change** | the message holds "Resume as planned." |
| Time-off line | `history` 18 dp tertiary, bodyMedium muted, TextButton **Ease back in** | under Today's day name |
| Missing swaps | labelLarge head `Not at Kamppi · for this session`; checkbox rows 56 dp, text at 64 dp | in the start sheet under the gyms |
| Coach entry | `sports` icon, first in Today's top bar, only while a day is next | |

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
 (•) Kamppi
     Usual on Tuesdays
 Not at Kamppi · for this session
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
- Which machine?: **Not at Kamppi** under Add machine (`machine-01-not-here`).
- Gym screen: **Not here** list with × to undo (`gym-01-not-here`).

## Interaction rules

- Picking a situation (or a pain area) resets the limits to its defaults; chips edit them after.
  Any edit clears the message.
- Review change runs the rules at once. A Change opens the review; Accept returns to Today, Reject
  to the coach as it was.
- Coach preselects Time off when more than a week has passed.
- Missing swaps start ticked; unticking one keeps the planned exercise for that session. Start
  applies the ticked ones to this session only; the plan is untouched.
- Not at Kamppi marks the exercise missing at the gym and swaps it straight away (or leaves it out of
  the session with nothing that fits).
