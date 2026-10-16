# Coach: design note

The Coach screen is words first: what's going on, in your words, to the Claude app, and Claude's
reply pasted back as a Change or a workout (`SPEC.md`, Coaching; ADR 0003). The rule screen of
ticket #17 (five situations and Limits chips) is gone. The missing-equipment swaps on the start
sheet stay, and are at the end of this note. The tokens of `program-and-today.md` and
`logging-screen.md` apply.

The owner's review on 2026-10-15 found the old screen hard to read: it never said what it does,
the Limits repeated the situations, Time off showed at 0 days, and Pain → Hands buried a list of
six exercise chips. The redesign answers those, with the tokens of the designer
pass below. Old screens: `screens/coach.png`, `coach-pain.png`.

## Principles

1. **Say what it does.** One line under the title: tell Claude what's going on, and get back a
   Change to your program or a workout. Nothing changes until you accept it.
2. **What the coach already did comes first.** Changes in force are cards at the top, each with
   Revert and Tell Claude how it's going.
3. **One way in.** Words, with starter chips to start from. No form, no limits.
4. **Options only where they mean something.** Effort, time and focus appear only for Plan a
   workout.
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
| Change in force | segment of a `SegmentedList` (Tones.card), 24 dp outer corners | One per Change, newest first |
| Change row | `SegmentedRow`: `tune` in the 40 dp secondaryContainer circle, summary bodyLarge (2 lines), how long it runs bodyMedium onSurfaceVariant | The Change |
| Change card tap | the whole segment, "See what it changes" | Opens the Change in force, read-only |
| Fold row | 56 dp segment, bodyLarge onSurfaceVariant on the 72 dp text column, `expand_more` at the end | `1 more in force` past two cards |
| Change actions | 48 dp row, end-aligned, 12 dp from the segment's end, 8 dp between: **Revert** text button, **Tell Claude how it's going** tonal button | Under the Change row, in the same segment |
| Composer | one `SectionCard` (Tones.card, 24 dp radius, 12 dp padding) | Gym, starters and the words, one thing to fill in |
| Composer head | labelLarge, onSurfaceVariant, on the 24 dp edge, 16 dp before the gym chips on the same row | "Gym"; the row is left out with no gyms |
| Gym chip | FilterChip, `selectedChipColors()`, `check` leading when picked, 8 dp apart | Single choice; **Elsewhere** last |
| Starter chip | SuggestionChip, `add` leading, 8 dp apart, no head: the `add` says what it does. One row that scrolls sideways, running to the card's edges, so the chip cut off at the edge shows there's more | Puts a sentence into the words |
| Words | OutlinedTextField, label "In your words", 3 lines at least, 16 dp over it | |
| Bottom bar | Tones.bar strip, 28 dp top corners, 16 dp padding, through the gesture area | Paste reply, Ask Claude |
| Next step | filled Button at the end of the bar; the other is a text button whose label sits on the 24 dp edge | Ask Claude, then Paste reply, then Review |
| Reply ready | primaryContainer card, 16 dp radius, inside the bar over its buttons, 12 dp under the strip's top: `check_circle` 24 dp, titleMedium "Claude's reply is ready", filled **Review** | The one accent of the screen |

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
 [✓ Build me a workout]
 Effort  [Easy] [✓ Normal] [Hard]
 Time    [45 min] [✓ 1 h] [1 h 15] [1 h 30]
 Focus   [Back · 4 sets] [Hamstrings · 2 sets] [Calves · 0 sets]
         [Chest] [Quads] [Shoulders] …                ← none picked: balanced full body
```

```
 Today
 ┌ Workout ──────────────────────────────────────┐
 │ Hotel dumbbells, 45 min                       │
 │ Chest-Supported DB Row   3 × 8–12 @ 1         │
 │ …                                    [Start]  │
 └───────────────────────────────────────────────┘
 Week 2 · Ramp-in
 Day B — …
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

Issue #32 (this pass), with the screenshot of each in `screens/` as `light-<name>.png` and
`dark-<name>.png`. They are Robolectric renders from `CoachRenders` (skipped unless asked for:
`RENDERS=<dir> ./gradlew testDebugUnitTest --tests '*CoachRenders*'`, `DARK=1` for dark), since
the phone was locked for most of the pass. From the phone (dark, monochrome), with the owner's
own two Changes in force: `phone-coach-02-in-force` and `phone-coach-03-how-its-going` (the words
started with the Change, the field above the keyboard with Ask Claude).

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

Issue #33 (workouts): Plan a workout open; a workout's review ("Workout for today", Add to
Today); Today's workout card above the program day, and above Start session with no program.
Until then Paste reply is left out with no program, since only a workout could come back.

Unchanged by the redesign:

- Start sheet: missing swaps for the gym picked (`start-01-missing`); another gym recomputes them.
- Which machine?: **Not at Harbour** under Add machine (`machine-01-not-here`).
- Gym screen: **Not here** list with × to undo (`gym-01-not-here`).

## Interaction rules

- A starter replaces the words only when they're empty; otherwise it's added on a new line.
- Tell Claude how it's going starts the words with the Change and focuses the field.
- The words, the gym, the workout's options and the asked state survive the trip to the Claude
  app (saved state).
- Once asked, Paste reply stays the next step until the request ends: an accept, a revert, or
  the words cleared.
- Tapping a Change card opens it read-only; Revert there asks first, then goes back to Coach.
- On return after asking, the clipboard is read once. A valid reply shows the ready bar; Review
  opens it. Nothing opens by itself.
- Editing the words clears a bad paste's problems; so does Back.
- Accept or Add to Today goes to Today; Reject goes back to Coach as it was.
- Missing swaps start ticked; unticking one keeps the planned exercise for that session. Start
  applies the ticked ones to this session only; the plan is untouched.
- Not at Harbour marks the exercise missing at the gym and swaps it straight away (or leaves it out of
  the session with nothing that fits).

## Pass notes

- First pass on the phone: with two Changes in force, the starters wrapped to three rows and
  pushed the words field below the fold. The gym chips moved onto their head's row and the
  starters became one sideways-scrolling row, so the field shows with two cards.
- Second pass: the intro went from three lines to two.
- Tester's report (`coach-test-report.md`), fixed: a card opens its Change; the prompt carries
  each Change's id and operations, and a reply can end or replace one (`replaces`); Elsewhere;
  the asked state ends; a reply copied short says so once; a third Change folds. The renders for
  these are from Robolectric: the phone went back to the owner before they could be checked on
  it.
