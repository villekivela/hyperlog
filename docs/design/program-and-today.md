# Program, Today and planned sets: design note

Ticket #6 brings a program to the app: the Program screen, Today showing the next day, and the
logging screen's Target column, planned rows and prescription sheet. Everything extends
`logging-screen.md`. The expressive pass (below, first) replaces the earlier flat look. **Today's
calendar (#53, last section) replaces Today's title bar, its floating Start and its next-day
layout;** the workout and running-session layouts below still hold on today. Screens:
`light-` and `dark-today-starter.png`, `-today-update-banner.png` and `-today-day.png` (a
superset as 5a and 5b) are renders from `AppRenders` (`RENDERS=<dir> ./gradlew testDebugUnitTest
--tests '*AppRenders*'`, `DARK=1` for dark). Program's own screens are listed under "Program
screen (#40)".

## Expressive pass

- **Navigation bar** (ShortNavigationBar, surfaceContainerHighest): Today, Program, Coach,
  History; the selected item is a primaryContainer pill with the filled symbol. Settings is the
  last icon of each destination's top bar. Hidden while the session screen is up.
- **Today** sits on surfaceContainer. Week and block in primary over the day's name; the slots are
  one segmented group (cards 32 dp outside, 8 dp at the joins, 4 dp apart), a superset's partners
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
   away on Program. Since #53 a calendar of done and planned days sits above it, its dates an
   estimate of the sequence.
2. **The plan reads like the table it becomes.** Today's slot lines, the session list and the
   prescription sheet all write sets the same way: `1 × 4–6 [1] · 2 × 6–8 [1]`.
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
     1 × 4–6 [1] · ↘ 2 × 6–8 [1]               ← bodyMedium, onSurfaceVariant; ↘ = back-off
 2   Hack Squat
     1 × 6–8 [1] · 1 × 8–10 [0]
 5a  Cable Lateral Raise
     2 × 10–12 [0]  ⟲                          ← technique symbols in tertiary
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
│ [✓3] [■4■] [5] [6]                        │   ← week chips first; done weeks ticked
│ From Ramp-in                              │   ← labelLarge, primary
│ Every set: 1 RIR closer to failure ·      │   ← a block-wide override, said once (bodyLarge)
│ techniques back                           │
│ Otherwise written as in Ramp-in           │   ← or the slots written differently:
╰───────────────────────────────────────────╯
   (Intensify: Day A · Barbell Bench Press · 1 × 4–6 [1] · ↘ … → 1 × 3–5 [1] · ↘ …, the arrow
    leading the after when it wraps; past 5 slots the first 3 show, then "19 more ⌄")
 Week 4 · Day A — Heavy bench, squat, …  Done
╭───────────────────────────────────────────╮
│ Tue 13 Oct                              › │   ← opens the session in History
│ Barbell Bench Press             85 × 5 [1]│   ← each exercise's top set
╰───────────────────────────────────────────╯
 Week 4 · Day B — Hinge, close-grip …   Next     ← day head with its week; Next in primary
╭───────────────────────────────────────────╮
│ (⇄) Ends 26 Oct; if this day falls later, │   ← only under a date-ranged Change
│     it's as written.                      │
├───────────────────────────────────────────┤
│ 1   In place of Barbell RDL               │   ← Today's slot rows, one segment per group
│     45° Hyperextension                    │
│     2 × 6–8 [1]                           │
╰───────────────────────────────────────────╯
 Week 3 · Day B — …                 Skipped      ← a past week: the plan at 60 % opacity
 Week 12 · Day A — …               ✿ Deload       ← spa symbol and Deload in tertiary
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
reps and outlined RIR chip, technique or back-off); rest as one line (`timer` Rest 3:00) when all sets share
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
 ↗ Last time 80 kg × 6 [2], top of 4–6 → 82.5 kg     ← bodyMedium, onSurfaceVariant
       Target   Last       kg   Reps  RIR
 1     4–6 (2)  80 × 7    82.5    5    ●2
```

The suggestion screens of this ticket were replaced with the expressive pass's.

**Starting weights (#9).** A free weight with no history yet gets the why-line with `history`,
tertiary like an estimate: "From old app: 20 kg × 10 [2], 3 Sep", with "→ 22.5 kg × 8" appended when
the ladder or today's range changed the set. A machine or cable with no history on the machine
picked gets the same row in onSurfaceVariant, two lines: "From old app, another machine" and the set
(tabular figures, the chip never parted from its set). Its weight cell stays empty: the hint informs the
pick on the ladder, it doesn't prefill it. No screenshots are kept: they would show real data from a
legacy backup.

```
 Chest-Supported Machine Row               ⋮
 ⟲ From old app, another machine
   45 kg × 12 [1], 3 Sep
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
- **The owner's answers (#58, 2026-10-17).** The next program day stays on today even on a day
  that isn't a training day; training days only place the later dates (issue 2, decided). Deload
  days carry a `Deload` tag under the name on the hero and planned cards: the program's spa
  symbol and the word, labelMedium in onTertiaryContainer, in a 24 dp tertiaryContainer pill with
  8 dp sides, 8 dp under the title (the program doesn't mark a test, so none is tagged). The head
  names the program week (`programWeekOf`: the week of the first planned day in the calendar week,
  else of its first program session done): `October 2026 · Week 7`. When it doesn't fit, the
  pencil's words go, then the month abbreviates (`Sep 2026 · Week 12` beside Today and the pencil
  at 115 % on the phone's 426 dp), then the week goes; a cut month is the last resort, since the
  month anchors the strip and the week is also on the cards. Every connected group picks in
  primaryContainer (in dark, the others in a `surfaceContainerLowest` well for 3:1).
- **Code review (f4d74f7).** TalkBack names a day's sessions `Day C`, `workout` or `ad-hoc
  session` from resources (a workout is never ad hoc, GLOSSARY). The floating Start workout waits
  for the sessions too. Today's saved state lives in the host's `SaveableStateHolder`, and each
  day's list keeps its scroll under a key of its day and kind (done, planned, rest), with the
  workout list keyed by its workout, since a restored value ignores `rememberSaveable`'s inputs.
  The header's probe measures with the text button's own padding and least width, and places
  relative for right-to-left. A day with nothing is a `RestMode`: Rest, AfterProgram or Undated.
- Rest after a planned set is that set's rest from the program.

## Program screen (#40)

The owner picked proposal B (blocks) on 2026-10-16, out of three: up next by date, blocks, and
exercise arcs. Program answers "what am I doing this block?" and "how is this block different?".
It reads the plan and never edits it.

Principles:

- **The block is the unit.** Its card, its weeks and its days sit together. The block group picks
  it, opening on the one you're in.
- **Differences are shown, not described.** "From Build" is computed: a block-wide override
  said once for every set, then each slot written differently, before → after. There is no
  written note.
- **Every week resolves.** A day to come reads exactly as the session will prescribe it, with
  overrides, deloads and Changes (`ProgramWeek.days` through `Changes.day`).
- **Past weeks are what happened.** A done day shows its date and top sets and opens the session.
  A skipped day shows its plan, headed Skipped.
- **No dates.** The next day reads as Today shows it. A later day takes a date-ranged Change by
  the window its in-force diff shows (`Changes.dayAhead`), and says when the Change ends.
- **The actions live in ⋮.** The header holds only the title and Next. Your training days are one
  row under it (`3 days a week · Mon Wed Fri`, or `Set your training days`) that opens their sheet
  (#52, ADR 0004); Changes are one row that opens their own page.

States: a program running (opening on the current block and week), a past week (done and skipped
days), a future block (its comparison and its days), the deload block (days headed Deload), a day
under a date-ranged Change (the Ends line), finished (Next reads "Program finished", opening on the
last block), the Changes page (Revert on each in force; reverted and replaced struck through), and
the empty, starter and import-error states as before.

Layout (build pass):

- **Header, training days and Changes are one segmented group**: the title card on top, the
  training days row (`calendar_today`) and the Changes row joined under it (4 dp joins), 16 dp
  above the block group. The Training days sheet: title, one line on what the days do, the seven
  round toggles across the sheet's 24 dp text edges, and, with two or more gyms, a
  `surfaceContainerHighest` group with a row per day that's on (the day, then its gym as a text
  button with a menu: the gyms, then Any gym), then Done. **Next** is primary here as on the day head,
  so the one word means the same everywhere; the day sits in a 56 dp column, and "Program
  finished" takes the same column with the flag in Next's place.
- **The block group sizes buttons by their labels** (`ConnectedChoice(fitLabels = true)`), so
  `Deload + test` keeps its words beside `Build`. Past four blocks, or about 40 characters of
  names, it is a scrolling row of chips.
- **A block-wide rule is one line.** Ramp-in → Build differs only by Ramp-in's week override
  (+1 RIR, no techniques), so Build reads "Every set: 1 RIR closer to failure · techniques back"
  and lists no slot (the tester found 22 identical rows a wall). The week chips come before the
  comparison, so changing week never means scrolling past it.
- **The comparison folds.** A block written afresh (week 12 rewrites every slot) can still list
  22 slots. Up to 5 slots show in full; past that the
  first 3 show, then **19 more ⌄** (labelLarge, primary, 48 dp row) unfolds the rest in place and
  **Fewer ⌃** folds it again. Each pair is before (muted), then the arrow and after (onSurface) as
  one piece, so a long pair breaks before the arrow, never after it.
- **A skipped day's plan sits back**: its slot rows at 60 % opacity, under "Skipped", so a past
  week doesn't read as days still to train. Done days keep full tone (they're real sessions).

Interaction: tapping a block resets the week to the current week if it's in that block, else the
block's first; the comparison opens folded on each block. A done day's segment opens its session
in History, and Back from there returns to Program on the same week. The week picked and the
Changes page are kept by the host (`ProgramView`) through a session opened from Program or a
review; choosing Program on the navigation bar opens it on where you are. The navigation bar is
hidden on the Changes page; Back there returns to Program. The menu names Repeat week for the week
it repeats ("Repeat week 5"), and the replace question says where you are ("You're in week 6 of
Hyperlog Full Body; …"). Test report: `program-test-report.md`.

Screens (Robolectric renders from `ProgramRenders`, `RENDERS=<dir>`, `DARK=1` for dark, each
`light-` and `dark-`): `program-current` (Build, week 4, Day A done, Day B next),
`program-past-week` (week 3: Day B skipped, Day C done), `program-future-block` (Intensify and
its From Build card), `program-deload` (Deload + test, days headed Deload), `program-ends` (a
date-ranged Change: the Ends line), `program-menu` (⋮ open), `program-changes` (a reverted and a
replaced Change struck through), `program-finished`. Checked on the phone (dark, the owner's
data): the current week matches Today slot for slot, done days show the logged top sets and open
their sessions. Light mode was checked through the renders only; the phone's theme was left as set.

## Today's calendar (#53)

Decided on 2026-10-17 from the look C prototype (`prototype/today-settings`, never merged): Today
opens on a calendar of weeks, and the day picked fills the screen below it. The owner wanted the
strip "to do something": show planned and done sessions, and scroll back into history and on into
the future.

```
 October 2026                  Today  ✎     ← Today once you've moved off it; ✎ training days
  M    T    W    T    F    S    S
 12   13   14   15   16  (17)  18          ← 40 dp circles: selected filled, today ringed
 [A]       [B]       [C]  ⟦A⟧               ← done filled, planned outlined, past rest days muted
┌──────────────────────────────────────┐
│ (A)  Week 7 · Intensify              │   ← primaryContainer hero: the cookie, then the name
│      Heavy bench, squat, pulldown    │
│ 6          12        ~35             │   ← Inter stats
│ exercises  sets      minutes         │
│ ┌──────────────────────────────────┐ │
│ │        ▶ Start session           │ │   ← onPrimaryContainer, across the card
│ └──────────────────────────────────┘ │
└──────────────────────────────────────┘
 1   Barbell Bench Press                   ← label centred on the row
     1 × 3–5 [1] · ↘ 2 × 6–8 [1]
              🏋 Ad-hoc session              ← text button after the day
```

- **No title bar.** The calendar head is the top of the screen: the month and year in
  titleLarge (`October 2026`, `Oct – Nov 2026` across a month), **Today** (a text button) once the
  strip or the selection is off today, and the `edit` pencil, which opens the Training days sheet.
  With no training days the pencil reads `✎ Set your training days`. It is the way to the sheet
  without a program, which #52 left open. **The month is never cut short** (#58): when the words
  don't fit beside it, the pencil shows alone, read out as `Set your training days`
  (`CalendarHead` measures the month and the prompt; `CalendarHeadTest` holds it at 115 % text).
- **The strip** is a HorizontalPager a week a page, from the week of the first finished session to
  the week of the last planned day, four weeks each way at least. A day is its narrow weekday
  letter (primary on today), its date in `figureMedium` in a 40 dp circle (primaryContainer when selected,
  a 2 dp primary ring on today otherwise) and a 28 × 20 dp marker: primary with the
  day's letter in labelMedium Bold when done (`check` for an ad-hoc session or a workout), a 1.5 dp
  primary outline with the letter when planned. Past days without a session have their dates at
  70 % onSurfaceVariant. Each day reads as one tab to TalkBack: `Sat 17 Oct, planned Day A`.
- **The hero** (the next day to train, today or later; only a plan while a session runs) is a 32 dp
  primaryContainer card, its text on the 24 dp edge (#58: see "Tester fixes" for markers, Next
  and the program's end): the
  letter in a 64 dp `Cookie9Sided` filled onPrimaryContainer, the head (`Week 7 · Intensify`,
  `Next · Mon 19 Oct · Week 7` on another date, as a planned card names it) at 80 % over the name after its letter
  in titleLarge, the stats (`figureHeadline` over bodySmall labels) and a medium
  **Start session** button in onPrimaryContainer across the card. It replaces the floating Start.
- **Done** cards and **planned** cards are the same layout on the card tone: the letter's cookie
  filled primary with its letter in onPrimary (done) or outlined in primary (planned), the head in primary (`Done ·
  Sat 17 Oct · Kalliokoski`, `Planned · Wed 21 Oct · Week 7`). A done card's stats are the minutes
  taken and its button a tonal **Open session**; a planned card has no button. A done day lists
  its exercises with the sets as logged, filled RIR chips; on today `Next: Day B on Mon 19 Oct ›`
  and Ad-hoc session follow.
- **Rest day**: a card with the date in primary over `Rest day` and `Next: Day A on Sat 17 Oct ›`.
  The link's 48 dp row carries its own space under the words, so the card's bottom padding is
  8 dp with it (20 dp without), and the words sit as far from the bottom as the date from the top.
- **Slot rows** centre the label (`1`, `5a`) on the row, beside the name and the sets, where it
  used to sit on the name's baseline.
- The update and time-off banners sit between the strip and the day while today is selected. A
  workout from Claude and a running session keep their layouts under the strip, with the
  floating Start workout or Resume session.

Screens (Robolectric renders from `TodayRenders`, `RENDERS=<dir>`, `DARK=1` for dark, each
`light-` and `dark-`): `today-calendar-hero` (week 7 Day A today, week 6 done), `-done` (Day A
finished today: the card, the logged sets, Next), `-next` (the hero on Mon 19 Oct, from Next),
`-planned` (Wed 21 Oct, outlined), `-rest`, `-past-done` (Wed 14 Oct), `-last-week` (swiped
back to an ad-hoc session: the tick), `-no-training-days` (only today marked; the header asks),
`-deload` (week 12's Day B today: the Deload tag; the owner's imported copy of the program has no
deload mark, so the emulator's data shows none).
The older Today renders (`today-starter`, `-update-banner`, `-day`, `-time-off`, `-workout*`) are
rendered again with the calendar on top.

### Phone pass (#58)

Checked on the phone (dark, the owner's data, system text at 115 %) in Ink and violet, two rounds.
The renders run at 100 % text on a 412 dp screen; the phone is 426 dp at 115 %, which is where
they parted.

- **The month went to `Octob…`** off today with no training days: `Today` and the
  `✎ Set your training days` words took the row. Now the words drop to the pencil when they don't
  fit, and the month keeps its whole name.
- **The hero's head wrapped to two lines** on another date (`Next · Sun 18 Oct · Week 7 ·` /
  `Intensify`), pushing the name down. It now drops the block, as the planned card does; the block
  shows on today's hero and on Program.
- **The rest card sat low**: 20 dp over the date, about 34 dp under the Next link. Balanced (above).
- **The sheet's line was out of date**: it said only that time off counts from the days. It now
  leads with what #53 made them do: `Today plans your next sessions on these days and counts time
  off from them.`
- Held up as built: the strip (circles, markers, the today ring), the hero's cookie and Start, the
  done and planned cards, the sticky calendar over scrolling cards, Program's training-days row.

### Tester fixes (#58, `today-test-report.md`)

- **The day picked survives Open session.** Today's selection lives in the host (`todayDay` in
  `MainActivity`, beside `programView`), so Back from History lands on the same day and week. The
  navigation bar's Today starts again on today.
- **The card follows the strip.** When a page settles on another week, the same weekday there is
  picked (`snapshotFlow { pager.settledPage }`). The pages are whole weeks, so nothing to clamp.
- **What's next comes first after training.** On today with sessions done, `Next: Day B on …`
  sits between the strip (and any banner) and the first done card; Ad-hoc session stays at the
  foot.
- **Every session of a day shows on its marker.** `CA` for two program days in order, `A+` for
  more or for a program day beside an ad-hoc session, a tick for ad hoc alone; the pill grows
  past 28 dp to fit two letters. TalkBack reads them all (`done Day C, Day A`).
- **The program's end is marked.** With training days the last planned date reads `Last day ·
  Wed 25 Nov · Week 12` in Planned's place (one line, as the hero's head). Any date after the last
  planned one reads `Nothing planned` with `Last planned: Day C on Wed 25 Nov ›`; with no training
  days that covers every date after the next day, which `Rest day` used to claim.
- **The weekday toggles say `Mo Tu We Th Fr Sa Su`**, so Tuesday and Thursday never look alike.
- **Re-test (6fc835b).** With no training days a later date is `Not planned yet` (the next day's
  link and **✎ Set your training days**, both text buttons on the card), not `Nothing planned`,
  which now means past the program's end. Today composes nothing under the bar until the sessions
  are read (`sessions` null), so a cold start goes from blank to the real day with no false hero.
  The picked day's scroll lives in the host beside its date (`rememberSaveable(todayDay)` of a
  `LazyListState`), so Back from a session returns to the same card; another day starts at its
  top. Back from another tab keeps the day picked, like any Back; the bar's Today starts on today.
  The header's width probe is now a plain replica of the buttons, so nothing tappable is composed
  twice.
- **The owner's answers (#58, 2026-10-17).** The next program day stays on today even on a day
  that isn't a training day; training days only place the later dates (issue 2, decided). Deload
  days carry a `Deload` tag under the name on the hero and planned cards: the program's spa
  symbol and the word, labelMedium in onTertiaryContainer, in a 24 dp tertiaryContainer pill with
  8 dp sides, 8 dp under the title (the program doesn't mark a test, so none is tagged). The head
  names the program week (`programWeekOf`: the week of the first planned day in the calendar week,
  else of its first program session done): `October 2026 · Week 7`. When it doesn't fit, the
  pencil's words go, then the month abbreviates (`Sep 2026 · Week 12` beside Today and the pencil
  at 115 % on the phone's 426 dp), then the week goes; a cut month is the last resort, since the
  month anchors the strip and the week is also on the cards. Every connected group picks in
  primaryContainer (in dark, the others in a `surfaceContainerLowest` well for 3:1).
- **Code review (f4d74f7).** TalkBack names a day's sessions `Day C`, `workout` or `ad-hoc
  session` from resources (a workout is never ad hoc, GLOSSARY). The floating Start workout waits
  for the sessions too. Today's saved state lives in the host's `SaveableStateHolder`, and each
  day's list keeps its scroll under a key of its day and kind (done, planned, rest), with the
  workout list keyed by its workout, since a restored value ignores `rememberSaveable`'s inputs.
  The header's probe measures with the text button's own padding and least width, and places
  relative for right-to-left. A day with nothing is a `RestMode`: Rest, AfterProgram or Undated.
- **Done reads in light too.** Done markers and the done card's cookie were secondaryContainer,
  `#E6DEFF` on the `#F0ECF7` screen (1.1:1, gone on the emulator in light). They are now filled
  primary with the letter in onPrimary: the planned day's outline filled in, so done and planned
  read as one pair in both modes (`doneFill`, `AccentContrastTest`).
- **On the hero and other accent cards**, a filled button or the picked choice is
  onPrimaryContainer (white) with violet words, like Start; see SPEC, theme. The selected choice
  colours elsewhere (toggle buttons primary, filter chips primaryContainer) are one violet in light
  and differ only in dark, where Material's own roles differ too (toggle buttons primary, chips
  secondaryContainer, which Hyperlog already moved to primaryContainer): left to the owner.
- Kept: Program's Next line has no date (issue 9). Program's "No dates" principle (#40) stands,
  and Program's training-days row sits right under it with Today one tap away.

Phone screens, each in `phone-dark-` and `phone-light-`: `today-done` (two sessions today,
Mon Wed Fri set: Next on Mon 19 Oct), `-today-no-training-days` (today, the header asking), `-today-next-no-training-days` (the hero on
tomorrow: the header off today with Today and the pencil alone), `-today-hero` (Mon Wed Fri: the
hero on Mon 19 Oct), `-today-planned` (Wed 21 Oct), `-today-rest` (Tue 20 Oct),
`-training-days-sheet` (from the pencil, Mon Wed Fri on), `-program-training-days` (the row with
Mon Wed Fri), `-program-no-training-days`, `-program-training-days-sheet` (from Program's row,
none on). After the tester's fixes these came from the emulator (AVD `hyperlog`, the phone's
size and density, text at 115 %): `phone-dark-today-done`, `-today-no-training-days`,
`-today-hero`, `-today-planned`, `-today-rest`, `-today-not-planned-yet`, `-today-last-day`,
`-today-after-program`, `-today-past-done`, `-past-session`, `-training-days-sheet`,
`-program-training-days`, `-program-training-days-sheet`, and every `phone-light-*` shot.
`phone-{dark,light}-rir-session` and `-rir-sheet` (the session's RIR chips and the prescription
sheet, Day B's Barbell RDL) came from a session started on the emulator's copy of the data, which
was then put back from a saved database.


## Programs (#84)

Program → ⋮ → Programs, a saved program's page, the new program review's Save for later, Coach's
"Saved to your programs", and the saved programs Today offers.

Principles:

- **A list you read, actions you ask for.** Each row is a standard `SegmentedRow`: a symbol, the
  title and one line. A row carries no buttons. A saved program's row opens its page; another's
  opens its sheet. The first cut put Start, Run again and ⋮ on every row, which squeezed past dates
  onto a second line and repeated "Run again" down the list.
- **The symbol says where it stands**: `play_arrow` running, `flag` finished (the flag that
  "Program finished" has on Program), `bookmark` saved (Save for later), `history` past. The
  running row's circle is the accent (`primaryContainer`), because it is the selected program.
  The others are `secondaryContainer`.
- **A saved program reads as its review did.** Its page is the review's list (`ProgramReviewList`)
  under "Saved program", with the date it was saved as the card's quiet line where the review says
  "From <file>": `Saved 24 Oct`, the same words as its row. Start program sits alone at the end of
  the strip, where the review has it; Export and Delete are in ⋮, because neither is why you open
  it.
  Its days have plain labels ("Week 1 · Day A"), with no Next, since nothing runs yet.
- **One way to run, said once.** A sheet has one filled button: **Resume** for a run replaced
  mid-way, else **Run again** for a past one or a finished running one. Run again (under Resume)
  and Export are rows below it. Where the run was left is on the sheet's status line ("Ran 1 Sep –
  13 Oct · Left in week 3"); the dialog says the exact day ("picks up at week 3, day 2").
- **Today offers, it doesn't crowd.** With no program running (or the running one finished), the
  saved programs are a "Your saved programs" group at the bottom of Today, under the starter offer
  or the empty state, which keep their look and sit centred above it. When both don't fit (a small
  screen, a large font, many saved programs) the whole screen scrolls; nothing is squeezed.
- **Nothing hidden when nothing runs.** With no program running, Program has no ⋮. **Programs**
  sits beside Import program under the starter offer (or under Import on the empty screen) while
  the app holds any program.

Tokens: rows as `SegmentedRow` (72 dp, 40 dp circle); sheet groups `Tones.sheetCard`; the sheet's
button `ButtonDefaults.MediumContainerHeight`, full width at the 24 dp edges, with a 24 dp symbol
(`play_arrow` Resume, `replay` Run again) and titleMedium; strip buttons 16 dp side padding; strip
padding 12 dp start, 16 dp end. Saved page: the review's card, its `Saved 24 Oct` line labelLarge
`onSurfaceVariant`, `BottomActions` with Start program at the end. Today: the offer's own space
under it drops from 96 dp to 8 dp (the group head's 24 dp top gives 32 dp at least); the group ends
16 dp above the navigation bar; `Arrangement.SpaceBetween` over [space, offer, space, group] in a
scrolling column at least the screen's height puts the offer a third of the free space above the
group.

```
 ←  Programs                          ───── (sheet, past) ─────────
                                        Strength block, 3 days
 Running                                Hyperlog · 12 weeks · 3 days a week
 ╭────────────────────────────────╮     Ran 1 Sep – 13 Oct · Left in week 3
 │ (▶) Hyperlog Full Body          │     12 weeks, 3 days a week. …
 │     4 blocks · 12 weeks · Week 7│    ╭──────────────────────────╮
 ╰────────────────────────────────╯     │   ▶  Resume               │
 Saved                                  ╰──────────────────────────╯
 ╭────────────────────────────────╮    ╭──────────────────────────╮
 │ (🔖) Scripted full body, 2 days  │    │ (↻) Run again            │
 │     1 block · 4 weeks · Saved 24 Oct  ├──────────────────────────┤
 ╰────────────────────────────────╯    │ (⬇) Export               │
 Past                                   ╰──────────────────────────╯
 ╭────────────────────────────────╮
 │ (⟲) Strength block, 3 days      │   Review strip:
 │     4 blocks · 12 weeks · 1 Sep – 13 Oct   Reject   [Save for later] [Start program]
 ╰────────────────────────────────╯

 ←  Saved program               ⋮      Today, no program running
 ╭────────────────────────────────╮     Week calendar
 │ Scripted full body, 2 days      │    ╭────────────────────────────╮
 │ Claude · 4 weeks · 2 days a week│    │     (starter hero)          │
 │ Saved 24 Oct                    │    │     Run this program        │
 │ Two full-body days a week …     │    ╰────────────────────────────╯
 ╰────────────────────────────────╯      Start session   Import a program
  [ Main ]                                       (free space)
  Main · weeks 1–4   [1] 2 3 4          Your saved programs
  Week 1 · Day A                      ╭────────────────────────────╮
  …                                    │ (🔖) Scripted full body, 2 days│
 ╭────────────────────────────────╮    ├────────────────────────────┤
 │                 [Start program] │    │ (🔖) Upper lower, 4 days     │
 ╰────────────────────────────────╯    ╰────────────────────────────╯
```

States: running mid-program (sheet: "You're in week 7", Export only); running and finished (flag,
"Program finished", sheet with Run again); saved (its page: Start program, ⋮ Export and Delete);
past ("Ran 1 Jun – 31 Aug": Run again, Export); past, replaced mid-way (Resume, then Run again and
Export); Replace, Resume and Delete dialogs; nothing running (Programs beside Import program);
Today with saved programs under the starter offer, and under "Program finished" (its line becomes
"Start a saved program below, or train ad hoc."); the review's strip; Coach's snackbar.

Interaction: a saved row (on Programs or Today) opens its page; Back returns there. Start program
asks "Replace <title>?" while a program runs and starts at once when none does, staying where the
page was opened. Another row opens its sheet; Resume and Run again close it, then ask "Replace
<title>?" while a program runs ("You're in week 7 of Hyperlog Full Body; Strength block, 3 days
picks up at week 3, day 2 instead. Past sessions are kept.") and start at once when none does.
Delete (saved page ⋮) asks "Delete <title>?". Export opens the system save dialog. The sheet is
the one sheet on the page; Back closes it, then the page. The snackbar stays as it was: the app's
other snackbars use the same default.

Screens (phone at font scale 1.15, dark unless named light): `programs-list`,
`programs-list-light`, `programs-saved-page`, `programs-saved-page-light`, `programs-saved-menu`,
`programs-sheet-running`, `programs-finished`, `programs-sheet-finished`, `programs-sheet-resume`,
`programs-resume-dialog`, `programs-replace`, `programs-delete`, `programs-no-program`,
`today-saved-programs`, `today-saved-programs-light`, `today-saved-programs-finished`,
`program-review-save`, `program-review-save-light`, `coach-saved-snackbar`.


## The rotation on Today and Program (#87)

With training days the program's days take turns on them (SPEC, Domain → Training days and the
rotation), so Today and Program gain dates, missed days, a week trained again and the easy week,
and the Training days sheet gains minutes. The words and the rules are SPEC's; this is how they sit.

Principles:

- **The accent is for what you can still do.** A missed day gives up the violet: its marker's
  outline is `outline` with the letter in `onSurfaceVariant`, struck through, and its card's
  cookie and head are `onSurfaceVariant`. The planned days keep their violet outlines, so the strip
  reads as "done, gone, to come" at a glance.
- **Dim the words, not the tiles.** A missed day's plan sits back at 60 % (`MissedAlpha`), on the
  content only, so its card and slot tiles keep their tone and shape on the screen. Dimming the
  whole tile (the first cut) let the light screen show through and the group dissolved.
- **What happened, then what to do.** `Monday’s Day A is missed · Train it today` is one text
  button, its first half in `onSurfaceVariant` and the action in primary; the action never breaks
  (it moves to the next line whole on a 360 dp phone). TalkBack reads the words as written.
- **One line each, then a second line, rather than three wrapped ones.** Program's training-days
  row puts `3 days take turns` on the row's supporting line. (The time-off line in an easy week
  no longer says `Easy week`: the head, the hero and the tag do; owner, 2026-10-27.) The words are the same; only the separator dot goes.
- **Notices stack without looking alike.** `No sessions last week · Week 8 again` is a
  `surfaceContainerHigh` banner with the `replay` symbol; the time-off line keeps `history` on
  `tertiaryContainer`.
- **Menus line up.** In the Training days sheet the minutes menu is as wide as its widest choice
  (`120 min`) and the day name as wide as `Wed`, so every row's gym and minutes start and end on
  the same edges whatever is picked; the picked choice in either menu carries a `check`.

Tokens: missed marker 28 × 20 dp, 1.5 dp `outline`, labelMedium Bold `onSurfaceVariant` with a
line through; `MissedAlpha` 0.6 on content; Train it today a TextButton 8 dp under the hero, text
on the 24 dp edge; notices 72 dp min, 8 dp apart, 8 dp over the hero; `Easy week` tag as Deload's
(24 dp `tertiaryContainer` pill, `spa`, 8 dp under the title); sheet rows 56 dp, 16 dp start, 8 dp
end.

```
 Oct – Nov 2026 · Week 8                ✎      Mon 26 picked:
  M    T    W    T    F    S    S              ╭──────────────────────────────╮
 26  (27)  28   29   30   31    1              │ (A)  Missed · Mon 26 Oct · Wk 8│ ← muted, no accent
 ⟦A̶⟧        [B]       [C]  [A]                  │      Heavy bench, squat, …    │
╭──────────────────────────────────╮           │ 6 exercises  12 sets  ~55 min │ ← dimmed
│ (B)  Next · Wed 28 Oct · Week 8  │           ╰──────────────────────────────╯
│      Hinge, close-grip bench, row│            tiles at full tone, words at 60 %
│ 7          11        ~55         │
╰──────────────────────────────────╯           Training days
 Monday’s Day A is missed · Train it today      Mon   Kalliokoski ▾    60 min ▾
 ╰──── onSurfaceVariant ────╯ ╰─ primary ─╯     Wed   Harbour F… ▾     75 min ▾
                                                Fri   Kalliokoski ▾  No limit ▾
 Easy week, Mon 9 Nov:                          Sat   Harbour F… ▾     45 min ▾
╭──────────────────────────────────╮
│ ⟲  16 days since your last session  [Ease back in] │
╰──────────────────────────────────╯
 November 2026 · Easy week, and the hero: Back after 16 days / … / [spa Easy week]
 One set fewer, 1 more RIR, no techniques
```

States: today with no training day and a missed Monday (the hero dated, no Start, Train it today);
the missed day picked; a week trained again (the notice over a hero with Start); an easy week (the
time-off line, the head's `November 2026 · Easy week`, the hero's `Back after 16 days`, the tag with `One set fewer, 1 more RIR, no
techniques` under it in the head's tone (owner, 2026-10-27); the notice doesn't show, as
the easy week isn't the program week again); a later planned day of the easy week; Program's
training-days row with `3 days take turns`, the missed day's head and the dated heads; the
Training days sheet with two gyms and minutes, its minutes menu, and both at 360 dp.

Interaction: as SPEC. Train it today opens the start sheet for the missed day; a missed day on the
strip selects like any day; the minutes menu lists `No limit` then 30–120 in fives, the picked one
ticked.

Screens (emulator, the phone's 1280 × 2856 at 480 dpi, text at 115 %, dark unless named light;
`-360` at a 360 dp width): `today-train-missed`, `-light`, `-360`; `today-missed-day`, `-light`;
`today-week-again`; `today-easy-week`; `today-easy-week-planned`; `program-rotation-row`,
`-light`; `program-rotation-week` (the missed day), `-light`; `program-rotation-dated`, `-light`;
`training-days-minutes`, `-menu`, `-360`.

## Block review (#91)

The page that says how a finished block went (SPEC, Program → Block review), its entry points
(Review on a finished block's card, Today's `<Block> is done · See the review`, Program review on a
past program's sheet) and the program review with its second column. The words and placements are
the owner's (2026-10-28); this pass sets the rows, the columns and the spacing.

Principles:

- **One left edge.** Every row is the review's own row (`ReviewRow`): no symbol, so no empty
  40 dp column; titles start 16 dp inside the tile, as on Volume, in every section.
- **Numbers on the right, words on the left.** Values end at the tile's 16 dp padding; a lift's
  gain sits under its kg; a record's kind sits where a value would.
- **Good news in the accent, the rest muted.** A gain, `Reached` and `On pace for` are primary;
  a stall, a drop, `short at this pace` and a missing value (`–`) are onSurfaceVariant.
- **Columns you can always read.** On the program review the heads (the block, `Program`) stay
  under the top bar once scrolled past, so the two numbers on a Volume row 1,200 dp down still
  have their names.
- **Calm length.** Volume shows its first 6 muscles past 8, with `15 more muscles ⌄` at the end of
  its group, as a block card's From list folds; one-line rows are 56 dp, not 72.

Tokens (in addition to the above):

| Token | Value | Use |
| --- | --- | --- |
| Review row | 72 dp min with a line under the title, 56 dp with none; 16 dp padding | every row on the page |
| Value column | 88 dp, 12 dp apart, end-aligned, bodyLarge | the program review's block and Program values |
| Column heads | labelLarge onSurfaceVariant, up to two lines, bottom-aligned, 28 dp from the edge | over the values; pinned on `surfaceContainer` with 8 dp above |
| Gain / pace | titleSmall, primary or onSurfaceVariant | `+5`, `±0`, `Reached`, `On pace for 2 Sep` |
| Volume fold | folds past 8 rows to 6; labelLarge primary + `expand_more` 20 dp, 56 dp row | `15 more muscles` / `Fewer` |

```
←                                          ←          Deload +            ← pinned once
Build review                                            test   Program       scrolled past
Weeks 3–6 · 17 Aug – 11 Sep                ╭──────────────────────────╮
Lifts                                      │ Leg Press        –   +14.75│
╭──────────────────────────────────╮       ╰──────────────────────────╯
│ Incline DB Press   71.75 → 80 kg │        Volume
│ Cable left · Harbour       +8.25 │       ╭──────────────────────────╮
╰──────────────────────────────────╯       │ Chest      5 → 5  7.5 → 8│
 Turning up                                ╰──────────────────────────╯
╭──────────────────────────────────╮       ╭──────────────────────────╮
│ 9 of 12 training days            │       │ 15 more muscles ⌄        │
│ Day B missed twice · Day A miss… │       ╰──────────────────────────╯
╰──────────────────────────────────╯        Goal
 Volume                                    ╭──────────────────────────╮
╭──────────────────────────────────╮       │ Barbell Bench Press      │
│ Chest       6.5 → 8.5 sets a week│ 56 dp │ 119.58 of 100 kg by 15 Jan│
╰──────────────────────────────────╯       │ Reached          primary │
 Records                                   │ Program: Reached         │
╭──────────────────────────────────╮       ╰──────────────────────────╯
│ Hack Squat       Estimate record │
│ 41.25 kg × 10 · 19 Oct           │
╰──────────────────────────────────╯
▔▔▔▔▔▔▔▔ [Check in about the next block]   ▔▔▔▔ [New program] [Check in]
```

States: a block with lifts (Build); a block whose lifts all stalled or none trained twice (`No lift
trained twice outside a deload` as the one row of Lifts); missed days running to two lines; the
weeks row when a week repeated or an easy week came; Volume folded and open; Changes as the last
row of Volume, up to three lines; records; bodyweight alone, or with `Program:` under it; a goal
reached, on pace or short; the program review at the top (heads in the page) and scrolled (heads
pinned), its block column `–` for lifts the last block didn't train twice; Today's line for a block
and for the program; the card's Review.

Interaction: as SPEC. The Volume fold toggles in place and survives rotation; the pinned heads are
a copy left out of the semantics tree, so a screen reader reads the page's heads once. Back and the
top bar's arrow close the page.

Screens (emulator, 1280 × 2856 at 480 dpi, text at 115 %, `-light` and `-dark`):
`block-review-ramp-in`, `block-review-ramp-in-volume` (the fold), `block-review-build`,
`block-review-program` (heads in the page), `block-review-program-columns` (heads pinned),
`block-review-card`, `block-review-today` (Ramp-in), `block-review-today-program`. Neither
fixture holds a past program, so the Programs sheet's Program review isn't shown.
