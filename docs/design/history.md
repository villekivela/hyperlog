# History: design note

Ticket #19 brings History: the session list (taking over #15's Past sessions), each exercise's top
sets and estimate over time, weekly sets per muscle, and the bodyweight log. It extends
`logging-screen.md` (tokens, type, the 24 dp text edge) and `program-and-today.md`. Renders in
`screens/history-*.png` are from Robolectric (Material baseline colours, light and dark); the
phone check is pending.

## Principles

1. **Two questions, two tabs.** Sessions answers "what did I do"; Progress answers "is it
   working". History opens on Sessions, the list the Today icon used to show.
2. **The number leads, the chart confirms.** Every chart sits under its figure (`99 kg`), and the
   rows under it are its table: every value a chart shows can be read without touching it.
3. **One accent.** The estimate line, the bars and the bodyweight line are `primary`; top sets,
   grid and axes are ink (`onSurfaceVariant`, `outlineVariant`). No categorical palette.
4. **Machines don't compare (ADR 0002).** A machine exercise shows one small chart per machine,
   each on its own scale, headed by the machine and gym. No shared axis, no colour per machine.
5. **Flat.** Sections are labelled bands on the one surface; no cards.
6. **Touch reads a point.** A tap or drag on a chart snaps to the nearest session and the readout
   above the chart shows it; with no touch the readout shows the latest session.

## Tokens (in addition to logging-screen.md)

| Token | Value | Use |
| --- | --- | --- |
| Section head | 48 dp, titleSmall, onSurfaceVariant | `Bodyweight`, `Sets this week`, `Exercises` |
| Figure | headlineMedium, proportional figures | `99 kg`, `82.4 kg` |
| Trend chart | 160 dp plot + 24 dp date axis | exercise trend, one per machine |
| Bodyweight chart | 64 dp on Progress (no grid, ticks or crosshair), 160 dp on its screen | small trend line |
| Sparkline | 72 × 24 dp | exercise rows on Progress |
| Line | 2 dp, round cap and join, `primary` | estimate, bodyweight |
| Dot | 8 dp, 2 dp surface ring | top sets (context ink), the selected point (10 dp, `primary`) |
| Context ink | `outline` in dark; `outline` 20% toward `surface` in light | top-set dots and their legend key |
| Grid | 1 dp hairline, `outlineVariant`, solid; 3 ticks at the right edge | charts |
| Bar | 8 dp tall, 4 dp rounded data end, square at the baseline, `primary` | sets per muscle |
| Muscle column | 128 dp | labels before the bars; value after the tip in tabular figures |

The palette is Material 3 dynamic colour, so it can't be validated once. The chart needs one hue
(`primary`) and a neutral context ink for the second mark, which also differs in shape (dot
against line), so identity never rests on colour alone. The dataviz validator, run on the
baseline and logo palettes: primary against context ink is normal-vision ΔE 18.7 (baseline light),
18.8 (baseline dark), 43.6 (logo light), 34.3 (logo dark), and both marks clear 3:1 on the
surface. Material's light `outline` itself failed (ΔE 11.2), hence the lift toward the surface.

## Wireframes

```
 ←  History
   Sessions        Progress                ← PrimaryTabRow, 48 dp
 ──────────────────────────────
 Day A                        Mon 12 Oct   ← bodyLarge · bodyMedium tnum
 Kamppi · Barbell Bench Press · Hack …    ← the gym, then the exercises; 2 lines max
```

```
 Bodyweight                          [+]   ← section head; + = Log bodyweight
 82.4 kg                      12 Oct       ← figure; date of the latest value
 ╭╴╴╴╴╴╴╴╴╴╴╴╴╴╴╴╴╴╴╴╴╴╴╴╴╴╴╴╴╴╴╴╮          ← 64 dp line, last 90 days; tap opens the log
 Sets this week          ‹ 12–18 Oct ›     ← chevrons step weeks (8 back)
 Chest        ████████████  4              ← muscle · bar · value
 Quads        ██████  2
 Exercises
 Barbell Bench Press      ╱╲╱‾  99 kg  ›   ← sparkline (estimate), latest estimate
 Cable Lateral Raise      2 machines   ›
```

```
 ←  Barbell Bench Press
 Estimate
 99 kg                                     ← figure: latest estimate
 Thu 1 Oct · top 82.5 × 5 @ 1 · 99 kg      ← readout: the selected session
 ┌──────────────────────────────── 100     ← hairline grid, ticks right
 │        ●━━━━━━━━━━●━━━━━━━━━━●            ← estimate line, top sets as dots
 │ ●                                 80
 └────────────────────────────────
 28 Sep                          5 Oct     ← first and last date
 ━ Estimate   ● Top set                     ← legend (two series)
 Mon 5 Oct      60 × 5 @ 4         99      ← the table: date · top set · estimate
 Thu 1 Oct      82.5 × 5 @ 1       99
```

For a machine exercise the block from the figure down repeats per machine, headed
`Cable left · Kamppi` (titleSmall), the machine used last first. A plain bodyweight exercise has no
estimate: its figure, line and table show the top set's reps instead (`12 reps`).

## States

- **Empty History**: Sessions says `Finished sessions show here, to look at or correct.`; Progress
  shows the Bodyweight section (`No bodyweight logged`, + to log) and `Exercises you train show
  here.`
- **One session**: the chart is a single dot with its readout; no line.
- **Weighted bodyweight**: the figure reads `Estimate with bodyweight`: the bodyweight of the day
  plus the belt. Top sets read as logged (the belt) in the readout and table, and are left off the
  chart, whose scale is the total. With no bodyweight logged the estimate is the belt alone, and a
  tertiary line says `Log bodyweight for a true estimate`.
- **Bodyweight log**: screen with the figure, the day read (the latest, or the one tapped on the
  chart or in the list, which shows in primary) with a delete icon, a 160 dp chart, then every day
  logged, newest first.
- **Log bodyweight** (+ on Progress and on the log): opens in place under the head, not a dialog:
  a decimal field `Bodyweight today` with `kg` after it (system keyboard, as setup), the filled
  **Save** (enabled for 20–400 kg) and × to close. Logging again the same day replaces the value.

## Interaction

- A session row opens the session's summary, and its exercises open their tables to correct, as
  Past sessions did. Back returns to the tab it came from.
- Progress → an exercise opens its trend; a table row opens that session's summary.
- Charts take tap and horizontal drag; the nearest session (by date) is selected. The selection
  stays until the screen closes.
