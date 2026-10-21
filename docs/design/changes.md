# Changes: design note

Ticket #16 makes plan edits Changes: the Program screen's actions, "Keep edits for the block" in
a session, and (later) the rule coach and pasted Changes all end in one review screen, a diff
against the current plan with Accept and Reject. It extends `program-and-today.md` and
`logging-screen.md`; their tokens apply. Screens: `screens/light-coach-08-review.png` and `dark-coach-08-review.png` (renders).

Expressive pass: the review sits on surfaceContainer. Its summary, scope and the Next / Left rows
(before → after) are one card at the top; each day is a group headed in primary, its slots
segments; **Reject** and **Accept** sit on a surfaceContainerHighest strip rounded at the top.

## Principles

1. **Nothing moves until Accept.** Every edit to the plan goes through the same review screen.
2. **A diff, not a description.** The screen shows the plan as it will be trained: before in muted
   type, after in full contrast, an arrow between. Strikethrough only for what goes away.
3. **Short where it repeats.** Position rows use the day's short name (`Week 3 · Day B`), so
   before → after fits one line; day heads keep the full name, ellipsized.
4. **One accent per row.** Primary for the arrow and an added `+`; tertiary for a moved load and
   the Deload tag. Removal is struck-through muted text, not the error colour.
5. **Actions sit with the position.** The four actions are chips under the Next line they move;
   history sits under them, above the weeks.

## Tokens

| Token | Value | Use |
| --- | --- | --- |
| Head label column | 56 dp | `Next`, `Left` on the review screen (as Program's Next) |
| Slot label column | 40 dp | `1`, `5a`, `+` (primary, bold, for an added exercise) |
| Slot row | 72 dp min, 8 dp vertical padding | One slot of the diff |
| Day head | 48 dp, labelLarge, 20 dp top | `Week 4 · Day A — Heavy bench…`, `Deload` at the right in tertiary |
| Arrow | `arrow_forward` 18 dp, primary, 8 dp each side | between before and after |
| Action chips | AssistChip, 8 dp gaps, flow to two rows | Skip day `skip_next`, Repeat week `replay`, Deload week `spa`, Shift program `more_time` |
| Change row | 72 dp min; summary bodyLarge (2 lines), scope · date bodyMedium; `undo` IconButton | Program → Changes |

## Wireframes

```
 ×  Review change
 Hands need a week off grip work.               ← headlineSmall: the summary
 13 Oct – 19 Oct                                ← bodyMedium, onSurfaceVariant: the scope
 Next   Week 3 · Day B  →  Week 2 · Day B       ← only when the position moves
 Left   29 days  →  32 days
 Week 2 · Day B — Hinge, close-grip bench…      ← day head (first of each day kind)
 1   B̶a̶r̶b̶e̶l̶l̶ ̶R̶D̶L̶                                ← removed, struck through
     Removed
 2   Close-Grip Barbell Bench Press
     2̶ ̶×̶ ̶6̶–̶8̶ ̶[̶2̶]̶  →  1 × 6–8 [2]              ← sets before → after
 3   In place of Chest-Supported Machine Row    ← labelMedium, as the session's swapped line
     Chest-Supported DB Row
     1 × 8–10 [2] · 1 × 8–10 [1]
 1   Barbell Bench Press
     1 × 4–6 [1] · ↘ 2 × 6–8 [1]
     ↘ Weights −10%                             ← tertiary
 +   Leg Extension                              ← added
     2 × 12–15 [1]
 ───────────────────────────────────────────
                         Reject   [Accept]
```

```
 Next  Week 3 · Day B — Hinge, close-grip…
 [⏭ Skip day] [⟲ Repeat week]
 [✿ Deload week] [⏱ Shift program]
 Changes                                         ← section head, as block heads
 Hands need a week off grip work…           ↶
 13 Oct – 19 Oct · accepted 13 Oct
 T̶h̶e̶ ̶p̶l̶a̶n̶ ̶1̶ ̶w̶e̶e̶k̶ ̶l̶a̶t̶e̶r̶                           ← reverted
 Reverted
```

## States

- **Position only** (skip, repeat, shift): Next and Left rows, no days (`change-02-shift-review`).
- **Deload**: every slot of the week's days, sets before → after, `Deload` at the head's right
  (`change-04-deload-review`).
- **Plan edits** (coach, kept edits): days and slots (`change-05-coach-review`).
- **Nothing changes** (e.g. a swap for a slot outside the window): "Nothing in the plan changes."
- **Program**: actions under Next while the program runs, none when finished
  (`change-01-program-actions`); Changes list once one is accepted (`change-03`, `change-06`).
- **Shift program** dialog: body text, − `1 week later` + (1–8), Cancel / Review. **Revert** dialog:
  "Revert “<summary>”?", Revert / Cancel. Dialogs don't render in Robolectric captures.

## Interaction rules

- Every source of a Change opens the review over whatever is on screen (Program or session);
  Accept and Reject return there. Back and × reject.
- Accept applies at once: the Program screen, Today and a running session follow (the session
  re-reads its day; an untouched prefill follows a moved load).
- "Keep edits for the block" sits in the session ⋮ above Finish, only while the session has edits
  it can keep; after Accept it goes, since the edits are now the plan.
