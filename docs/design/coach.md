# Coach: design note

The Coach screen is words first: what's going on, in your words, to the Claude app, and Claude's
reply pasted back as a Change or a one-off day (`SPEC.md`, Coaching; ADR 0003). The rule screen of
ticket #17 (five situations and Limits chips) is gone. The missing-equipment swaps on the start
sheet stay, and are at the end of this note. The tokens of `program-and-today.md` and
`logging-screen.md` apply.

The owner's review on 2026-10-15 found the old screen hard to read: it never said what it does,
the Limits repeated the situations, Time off showed at 0 days, and Pain → Hands buried a list of
six exercise chips. The redesign answers those. Tokens for the new screen are set in the designer
pass. Old screens: `screens/coach.png`, `coach-pain.png`.

## Principles

1. **Say what it does.** One line under the title: tell Claude what's going on, and get back a
   Change to your program or a one-off day. Nothing changes until you accept it.
2. **What the coach already did comes first.** Changes in force are cards at the top, each with
   Revert and Tell Claude how it's going.
3. **One way in.** Words, with starter chips to start from. No form, no limits.
4. **Options only where they mean something.** Effort, time and focus appear only for Plan a
   one-off day.
5. **The filled button is the next step.** Ask Claude until you've asked, then Paste reply.
6. **Swaps read as the session will.** "In place of …" over the new name, on Today, in the start
   sheet and in the session, as in the review.
7. **The gym learns what it lacks** the way it learns machines: on the go ("Not at Harbour"), undone
   on the gym screen.

## Wireframes

```
 Coach                                              ⚙
 Tell Claude what's going on. You get back a change to your program
 or a one-off day, and nothing changes until you accept it.

 ┌ Hands need a week off grip work ──────────────┐   ← a Change in force
 │ Until Tue 27 Oct                              │
 │ [Revert]          [Tell Claude how it's going] │
 └───────────────────────────────────────────────┘

 Gym   [✓ Harbour] [Itis]

 [My hands hurt] [Short on time today] [No gym, dumbbells at home]
 [Slept badly] [Plan a one-off day]

 ┌ In your words ────────────────────────────────┐
 │ Left elbow aches on curls…                    │
 └───────────────────────────────────────────────┘
 ───────────────────────────────────────────────────
 Claude's reply is ready                  [Review]   ← after asking, when the clipboard holds one
 Paste reply                           [Ask Claude]
```

```
 [✓ Plan a one-off day]
 Effort  [Easy] [✓ Normal] [Hard]
 Time    [45 min] [✓ 1 h] [1 h 15] [1 h 30]
 Focus   [Back · 4 sets] [Hamstrings · 2 sets] [Calves · 0 sets]
         [Chest] [Quads] [Shoulders] …                ← none picked: balanced full body
```

```
 Today
 ┌ One-off day ──────────────────────────────────┐
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

- Coach: empty, nothing in force (Ask Claude disabled); a Change in force; words typed; Plan a
  one-off day open; asked (Paste reply filled); Claude's reply ready; a bad paste with its
  problems; no program running (no Change cards).
- Reviews: a Change ("Change to your program", Accept); a one-off day ("One-off day for today",
  Add to Today).
- Today: a one-off day card above the program day, and above Start session with no program; the
  time-off line with Ease back in.
- Start sheet: missing swaps for the gym picked (`start-01-missing`); another gym recomputes them.
- Which machine?: **Not at Harbour** under Add machine (`machine-01-not-here`).
- Gym screen: **Not here** list with × to undo (`gym-01-not-here`).

## Interaction rules

- A starter replaces the words only when they're empty; otherwise it's added on a new line.
- Tell Claude how it's going starts the words with the Change and focuses the field.
- The words, the gym, the one-off day's options and the asked state survive the trip to the Claude
  app (saved state).
- On return after asking, the clipboard is read once. A valid reply shows the ready bar; Review
  opens it. Nothing opens by itself.
- Editing the words clears a bad paste's problems; so does Back.
- Accept or Add to Today goes to Today; Reject goes back to Coach as it was.
- Missing swaps start ticked; unticking one keeps the planned exercise for that session. Start
  applies the ticked ones to this session only; the plan is untouched.
- Not at Harbour marks the exercise missing at the gym and swaps it straight away (or leaves it out of
  the session with nothing that fits).
