# Program, Today and planned sets: design note

Ticket #6 brings a program to the app: the Program screen, Today showing the next day, and the
logging screen's Target column, planned rows and prescription sheet. Everything extends
`logging-screen.md`. The expressive pass (below, first) replaces the earlier flat look. Screens:
`today-session-running.png`, `today-superset-group.png` from the phone (Program's own screens are
listed under "Program screen (#40)"); `light-today-starter.png`, `light-today-update-banner.png`, `light-today-day.png`
rendered in light.

## Expressive pass

- **Navigation bar** (ShortNavigationBar, surfaceContainerHighest): Today, Program, Coach,
  History; the selected item is a primaryContainer pill with the filled symbol. Settings is the
  last icon of each destination's top bar. Hidden while the session screen is up.
- **Today** sits on surfaceContainer. Week and block in primary over the day's name; the slots are
  one segmented group (cards 24 dp outside, 4 dp at the joins, 2 dp apart), a superset's partners
  sharing one segment. **Start session** (or **Resume session**) is the expressive medium extended FAB
  (80 dp) in primaryContainer; **Ad-hoc session** is a card row after the day. The update banner is a
  primaryContainer card (tap to update, × to dismiss) and the time-off line a tertiaryContainer
  card with a filled **Ease back in**.
- **Starter offer** is one hero card: a primary-container cookie (`MaterialShapes.Cookie9Sided`)
  with the program symbol, "Starter program" in primary, the title, the description and a
  medium **Run this program**, with Start session and Import a program under the card. Empty
  states (no program, finished) take the same shaped symbol.
- **Program**: rebuilt by #40 around the block you're in; see "Program screen (#40)" below.

## Principles

1. **One next thing.** Today names the next day and offers Start; the rest of the program is a tap
   away on Program. The program runs as a sequence, so there is no calendar.
2. **The plan reads like the table it becomes.** Today's slot lines, the session list and the
   prescription sheet all write sets the same way: `1 × 4–6 @ 1 · 2 × 6–8 @ 1`.
3. **Structure is the hierarchy.** On Program the block is the unit: its card, its weeks as chips,
   then the week's days as segmented groups on the screen.
4. **Where you are is where it opens.** Program opens on the block and week you're in, the next day
   headed **Next** in primary.
5. **Errors are a to-do list.** A rejected file shows every problem as path, then message, so it can
   be fixed in one pass; nothing changes until a file is valid.

## Tokens (in addition to logging-screen.md)

| Token | Value | Use |
| --- | --- | --- |
| Slot label column | 40 dp | `1`, `5a` on Today and in the sheet; names start at 64 dp |
| Slot line | 72 dp min | Today's slot rows (name + set summary) |
| Block card title | titleLarge | `Build · weeks 3–6`, at the top of the block's card |
| Day head | labelLarge, 24 dp above, 8 dp below | the day's name; Next / Deload / Done / Skipped at the end |
| Target column | 88 dp | Logging table, between Set and Last |


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
 Program                                ⚙  ⋮    ← ⋮: Skip day, Repeat week, Deload week, Shift
╭───────────────────────────────────────────╮     program, ─, Import program, Run Hyperlog…
│ Hyperlog Full Body                        │   ← headlineSmall; author under it
│ Next  Week 4 · Day B — Hinge, close-grip  │   ← Next in primary, the day in a 56 dp column
│       bench, row                          │
├───────────────────────────────────────────┤   ← one segmented group, 4 dp join
│ (⇄)  Changes · 1 in force               › │   ← opens the Changes page
╰───────────────────────────────────────────╯
 ( Ramp-in | ■Build■ | Intensify | Deload + test )  ← connected group, widths by label
╭───────────────────────────────────────────╮
│ Build · weeks 3–6                         │   ← titleLarge
│ From Ramp-in                              │   ← labelLarge, primary
│ Day A                                     │   ← labelMedium, when several days changed
│ Barbell Bench Press                       │
│ 1 × 4–6 @ 2 · ↘ 2 × 6–8 @ 2               │   ← before, muted
│ → 1 × 4–6 @ 1 · ↘ 2 × 6–8 @ 1             │   ← the arrow leads the after when it wraps
│ …two more slots                           │
│ 19 more ⌄                                 │   ← past 5 slots, the first 3 show; tap to unfold
│ [✓3] [■4■] [5] [6]                        │   ← week chips; done weeks ticked
╰───────────────────────────────────────────╯
 Day A — Heavy bench, squat, pulldown   Done
╭───────────────────────────────────────────╮
│ Tue 13 Oct                              › │   ← opens the session in History
│ Barbell Bench Press             85 × 5 @ 1│   ← each exercise's top set
╰───────────────────────────────────────────╯
 Day B — Hinge, close-grip bench, row   Next      ← day head; Next in primary
╭───────────────────────────────────────────╮
│ (⇄) Ends 26 Oct; if this day falls later, │   ← only under a date-ranged Change
│     it's as written.                      │
├───────────────────────────────────────────┤
│ 1   In place of Barbell RDL               │   ← Today's slot rows, one segment per group
│     45° Hyperextension                    │
│     2 × 6–8 @ 1                           │
╰───────────────────────────────────────────╯
 Day B — …                           Skipped      ← a past week: the plan at 60 % opacity
```

```
 Not imported                                   ← headlineSmall
 2 problems. Fix them and import again; nothing changed.
 blocks[0].days[0].groups[1].slots[0].exercise  ← bodySmall, monospace, onSurfaceVariant
 unknown exercise "bb_row"                      ← bodyMedium
                      Close   [Choose file]
```

An update (SPEC, Distribution) sits under the title, above whatever Today shows (now a
primaryContainer banner card, see Expressive pass), flat like the
time-off line:

```
 Today                               ▦  ⚙
 [system_update]  Hyperlog 0.2.0 is available   ✕   ← symbol in primary at the 24 dp margin;
                  Tap to update                       bodyLarge over bodyMedium (onSurfaceVariant)
 Week 3 · Build
```

- Tap anywhere on the row: "Downloading…", then Android's installer opens ("Update this app?").
  The row stays, so a cancelled install can be tapped again.
- A failed download reads "Couldn't download it. Tap to try again." in `error`.
- ✕ (content description "Dismiss") hides that version for good; a later release shows again.
- One hero card (Expressive pass): the shaped symbol is the only accent.

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

**Today, no program (the first run).** The starter offer: `view_timeline` (48 dp, primary),
"Starter program" (labelLarge, onSurfaceVariant), the program's title (titleLarge) and its
`description` from the file (bodyMedium, onSurfaceVariant), then **Run this program** (filled) and
a row of two text buttons, **Start session** (ad hoc) and **Import a program**. The program is the
one filled shape, so a new user's first tap trains it; an ad-hoc session stays one tap away. Run
imports it and Today turns into week 1, Day A. While a session runs, or if the starter file can't
be read, the old empty state shows instead (`fitness_center`, "No program yet", Start or Resume
session, Import a program).

```
              ▦                                ← view_timeline 48 dp
        Starter program                        ← labelLarge, onSurfaceVariant
      Hyperlog Full Body                       ← titleLarge
  12 weeks, 3 days a week. Few sets taken      ← bodyMedium, onSurfaceVariant
  hard, a heavy bench, machines and cables
  first.
                                               ← 32 dp
       [Run this program]                      ← filled
  Start session   Import a program             ← text buttons, 8 dp apart
```

**Program screen.** See "Program screen (#40)" below. With no program: the same starter
offer, with **Import program** as its one text button (the old "No program yet" state is the
fallback if the starter can't be read). ⋮ holds the four program actions (Changes: each opens its
diff), a divider, then **Import program** and **Run Hyperlog Full Body** (`view_timeline`), both
behind the replace question. Importing over a program asks first: "Replace <title>?", "The new program starts at
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

**Why-line (#8).** Under the exercise name, above the column heads: why the active set's weight
is what progression suggests. One bodyMedium line in onSurfaceVariant, two at most, ellipsized;
start at the 24 dp margin, 8 dp below it. It leads with an 18 dp symbol in a 20 dp box aligned to
the first line: `trending_up` (primary) for a rung up or a heavier next set, `trending_flat` for
same weight, `trending_down` for a step down. An estimated weight (`calculate`) and "progress isn't
possible" (`block`) are tertiary, symbol and text, so the one weight to check stands out. A set
that only follows the set before, or a back-off, has no line; the back-off label already says why.
The pad prefills the suggested weight and reps, so accepting it is one ✓.

```
 Barbell Bench Press                       ⋮
 ↗ Last time 80 kg × 6 @ 2, top of 4–6 → 82.5 kg     ← bodyMedium, onSurfaceVariant
       Target   Last       kg   Reps  RIR
 1     4–6 (2)  80 × 7    82.5    5    ●2
```

The suggestion screens of this ticket were replaced with the expressive pass's.

**Starting weights (#9).** A free weight with no history yet gets the why-line with `history`,
tertiary like an estimate: "From old app: 20 kg × 10 @ 2 RIR, 3 Sep", with "→ 22.5 kg × 8" appended when
the ladder or today's range changed the set. A machine or cable with no history on the machine
picked gets the same row in onSurfaceVariant, two lines: "From old app, another machine" and the set
(tabular figures, never broken inside "@ 2 RIR"). Its weight cell stays empty: the hint informs the
pick on the ladder, it doesn't prefill it. No screenshots are kept: they would show real data from a
legacy backup.

```
 Chest-Supported Machine Row               ⋮
 ⟲ From old app, another machine
   45 kg × 12 @ 1 RIR, 3 Sep
       Target          kg   Reps  RIR
 1     8–10 (1)      [   ]   10    ●1
```

Settings has a row under the exercise library: `history` "Starting weights from the old app",
supporting "From an old Hyperlog backup, no history", then "42 exercises · tap to import again", or
"That file isn't a Hyperlog backup" in error colour.

## Interaction rules

- Start session on a program day asks for the gym as before, then starts that day.
- After an exercise's last planned set the first exercise with sets left comes up (order rules in
  `logging-screen.md`); rest still runs.
- Finishing a session of a program day moves the position to the next day. A session with nothing
  logged is discarded and the day stays next. Finishing a resumed session again doesn't move it
  twice.
- Rest after a planned set is that set's rest from the program.

## Program screen (#40)

The owner picked proposal B (blocks) on 2026-10-16, out of three: up next by date, blocks, and
exercise arcs. Program answers "what am I doing this block?" and "how is this block different?".
It reads the plan and never edits it.

Principles:

- **The block is the unit.** Its card, its weeks and its days sit together. The block group picks
  it, opening on the one you're in.
- **Differences are shown, not described.** "From Build" is computed: each slot that differs
  between the previous block's last week and this block's first, before → after. There is no
  written note.
- **Every week resolves.** A day to come reads exactly as the session will prescribe it, with
  overrides, deloads and Changes (`ProgramWeek.days` through `Changes.day`).
- **Past weeks are what happened.** A done day shows its date and top sets and opens the session.
  A skipped day shows its plan, headed Skipped.
- **No dates.** The next day reads as Today shows it. A later day takes a date-ranged Change by
  the window its in-force diff shows (`Changes.dayAhead`), and says when the Change ends.
- **The actions live in ⋮.** The header holds only the title and Next. Changes are one row that
  opens their own page.

States: a program running (opening on the current block and week), a past week (done and skipped
days), a future block (its comparison and its days), the deload block (days headed Deload), a day
under a date-ranged Change (the Ends line), finished (Next reads "Program finished", opening on the
last block), the Changes page (Revert on each in force; reverted and replaced struck through), and
the empty, starter and import-error states as before.

Layout (build pass):

- **Header and Changes are one segmented group**: the title card on top, the Changes row joined
  under it (4 dp join), 16 dp above the block group. **Next** is primary here as on the day head,
  so the one word means the same everywhere; the day sits in a 56 dp column, and "Program
  finished" takes the same column with the flag in Next's place.
- **The block group sizes buttons by their labels** (`ConnectedChoice(fitLabels = true)`), so
  `Deload + test` keeps its words beside `Build`. Past four blocks, or about 40 characters of
  names, it is a scrolling row of chips.
- **The comparison folds.** A block that moves RIR differs on every slot (Build has 22), which
  pushed the week chips and days off the first screen. Up to 5 slots show in full; past that the
  first 3 show, then **19 more ⌄** (labelLarge, primary, 48 dp row) unfolds the rest in place and
  **Fewer ⌃** folds it again. Each pair is before (muted), then the arrow and after (onSurface) as
  one piece, so a long pair breaks before the arrow, never after it.
- **A skipped day's plan sits back**: its slot rows at 60 % opacity, under "Skipped", so a past
  week doesn't read as days still to train. Done days keep full tone (they're real sessions).

Interaction: tapping a block resets the week to the current week if it's in that block, else the
block's first; the comparison opens folded on each block. A done day's segment opens its session
in History, and back from there returns to History. Back on the Changes page returns to Program.

Screens (Robolectric renders from `ProgramRenders`, `RENDERS=<dir>`, `DARK=1` for dark, each
`light-` and `dark-`): `program-current` (Build, week 4, Day A done, Day B next),
`program-past-week` (week 3: Day B skipped, Day C done), `program-future-block` (Intensify and
its From Build card), `program-deload` (Deload + test, days headed Deload), `program-ends` (a
date-ranged Change: the Ends line), `program-menu` (⋮ open), `program-changes` (a reverted and a
replaced Change struck through), `program-finished`. The phone stayed locked through this pass, so
the build is installed there but the phone check of the current week is still to do.

