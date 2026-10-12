# Program, Today and planned sets: design note

Ticket #6 brings a program to the app: the Program screen, Today showing the next day, and the
logging screen's Target column, planned rows and prescription sheet. Everything extends
`logging-screen.md`; its tokens, type and colours apply unchanged. Screenshots in `screens/`
(`program-*.png`, `today-*.png`, `planned-*.png`) are from the owner's phone (monochrome theme),
except `program-empty.png` and `today-no-program.png`, rendered under Robolectric.

## Principles

1. **One next thing.** Today names the next day and offers Start; the rest of the program is a tap
   away on Program. The program runs as a sequence, so there is no calendar.
2. **The plan reads like the table it becomes.** Today's slot lines, the session list and the
   prescription sheet all write sets the same way: `1 × 4–6 @ 1 · 2 × 6–8 @ 1`.
3. **Structure is the hierarchy.** Blocks are headed labels, weeks are rows, days are tokens. No
   cards; one surface level.
4. **Where you are is the one accent.** The next day's token is the only filled primary shape on
   the Program screen.
5. **Errors are a to-do list.** A rejected file shows every problem as path, then message, so it can
   be fixed in one pass; nothing changes until a file is valid.

## Tokens (in addition to logging-screen.md)

| Token | Value | Use |
| --- | --- | --- |
| Slot label column | 40 dp | `1`, `5a` on Today and in the sheet; names start at 64 dp |
| Slot line | 72 dp min | Today's slot rows (name + set summary) |
| Week row | 48 dp | Program screen; week number column 40 dp |
| Day token | 32 dp circle, 8 dp gap | A, B, C… for the days of a week |
| Block head | 48 dp, labelLarge | `Build · weeks 3–6`; primary for the current block |
| Target column | 88 dp | Logging table, between Set and Last |

Day tokens: done is surfaceContainerHighest with onSurfaceVariant letter; next is primary with
onPrimary letter; upcoming is a 1 dp outlineVariant ring with onSurfaceVariant letter.

## Wireframes

```
 Today                               ▦  ⚙      ← view_timeline opens Program
 Week 3 · Build                                ← labelLarge, onSurfaceVariant
 Day A — Heavy bench, squat, pulldown          ← headlineSmall
 1   Barbell Bench Press                       ← bodyLarge
     1 × 4–6 @ 1 · ↘ 2 × 6–8 @ 1               ← bodyMedium, onSurfaceVariant; ↘ = back-off
 2   Hack Squat
     1 × 6–8 @ 1 · 1 × 8–10 @ 0
 5a  Cable Lateral Raise
     2 × 10–12 @ 0  ⟲                          ← technique symbols in tertiary
 ───────────────────────────────────────────
                    Ad-hoc session  [Start]    ← bottom bar over the gesture area
```

```
 ←  Program                              ⋮      ← ⋮: Import program
 Hyperlog Full Body                             ← headlineSmall
 Next  Week 3 · Day B — Hinge, close-grip      ← labelLarge + bodyLarge, wraps to 2 lines
       bench, row
 Ramp-in · weeks 1–2                            ← block head
 1    (A) (B) (C)                               ← done tokens
 2    (A) (B) (C)
 Build · weeks 3–6                              ← primary: the current block
 3    (A) [B] (C)                               ← next token filled
 4    (A) (B) (C)
```

```
 Not imported                                   ← headlineSmall
 2 problems. Fix them and import again; nothing changed.
 blocks[0].days[0].groups[1].slots[0].exercise  ← bodySmall, monospace, onSurfaceVariant
 unknown exercise "bb_row"                      ← bodyMedium
                      Close   [Choose file]
```

Logging table with a plan (logging-screen.md's layout, Target column added):

```
       Target   Last       kg   Reps  RIR
 1     4–6 (1)  90 × 5      92.5   6      ●1   ← done / active as before
       ↘ Back-off, top set − 10%               ← label row above a run of back-offs
 2     6–8 (1)  80 × 8      82.5   8           ← upcoming: planned weight, muted
 3     6–8 (1)  80 × 8      82.5   8
```

## Components and states

**Today, program day.** Week and block, the day's name, then one line per slot: label (on the
name's baseline), exercise name, and the set summary with technique symbols. A slot's alternatives aren't listed (swapping is
#12). The bottom bar has **Start session** (filled) and **Ad-hoc session** (text). While a session
runs, the bottom bar is **Resume session** alone.

**Today, program finished.** The empty-state layout with `flag`: "Program finished", "Import the
next one from Program, or train ad hoc."

**Today, no program.** The empty-state layout as before, with **Import a program** (text) under
Start session.

**Program screen.** Title and author, the Next line (the only place the next day is named; it
wraps to two lines), then each block's head and week rows. With no program: `view_timeline`, "No
program yet", "A program is a JSON file. Claude can write one from a program you own.", **Import
program**. Importing over a program asks first: "Replace <title>?", "The new program starts at
week 1, day 1. Past sessions are kept." (Replace / Cancel, the change on the left as in the Leave
and Finish dialogs).

**Import errors.** Replace the screen's content until closed (back closes them too). Each problem:
path in monospace bodySmall, breaking only after its dots, message in bodyMedium, 8 dp between
problems.

**Planned rows.** Every planned set has a row from the start, so the session's shape is visible.
Upcoming rows show the target, last time's set at that index, the planned weight (a back-off's is
worked out live from the top set's weight, rounded down to a rung) and the top of the range, all
muted. A technique's label sits under its set (`trending_down` Drop set, 2 drops of 25% · `repeat`
Myo-reps, mini-sets of 4 · `unfold_less` Lengthened partials to failure · `pan_tool` Iso hold
30 s, stretch · `local_fire_department` To failure). Sub-rows for drops and mini-sets come with
#10.

**Header.** A long exercise name ellipsizes 8 dp clear of the rest pill.

**Day done.** When every planned set is logged the pad gives way to a bar with "Day done" and a
filled **Finish session** (the usual Finish dialog follows).

**Prescription sheet.** Name, equipment and muscles; then each set as a 40 dp row (number, target
reps and RIR ring, technique or back-off); rest as one line (`timer` Rest 3:00) when all sets share
it, else per row at the right; the slot note with `view_timeline` (the program's cue); the
technique note with `sticky_note_2`; **Technique video**.

## Interaction rules

- Start session on a program day asks for the gym as before, then starts that day.
- After an exercise's last planned set the first exercise with sets left comes up (order rules in
  `logging-screen.md`); rest still runs.
- Finishing a session of a program day moves the position to the next day. A session with nothing
  logged is discarded and the day stays next. Finishing a resumed session again doesn't move it
  twice.
- Rest after a planned set is that set's rest from the program.
