# Hyperlog Full Body — 3 days, 12 weeks

An original program written for Hyperlog. It's the owner's first program in the new app and the
starter program the app ships with.

## Who it's for

An experienced lifter returning from about two weeks off with sore hands, training three days a
week across three commercial gyms. The goals are hypertrophy, getting back into rhythm, and a
**100 kg barbell bench press**.

## Bench goal

The current estimate is about 85 kg (barbell bench 65 × 10, 10, 7 on 2026-09-19). 100 kg is
+18%, too much for one 12-week cycle after time off. This cycle aims for an estimated 92–95 kg.
A second, shorter strength block then peaks for a 100 kg test, around March–April 2027.

The bench is trained three times a week: heavy on Day A, volume on Day C, and close-grip for
triceps and lockout on Day B. Week 12 ends with a top-single test that sets the estimate.

## Shape

- **Three full-body days**, A, B and C, on non-consecutive days (for example Mon / Wed / Fri). Each
  takes about 60 minutes.
- **Each muscle is trained 2–3 times a week**, at roughly 9–12 hard sets for the major groups.
- **Mostly machines, cables and dumbbells.** Every slot has alternatives, so any of the three gyms
  works.
- **Easy on the grip.** There are no heavy barbell pulls from the floor. Rows are chest-supported,
  and pulldowns can use straps. Grip-heavy sets are marked so the coach can swap them.
- **Long muscle lengths are preferred** (stretch-biased exercises and lengthened partials), in line
  with `docs/research/progression-and-partials.md`.
- **Effort is in RIR.** Weights come from automatic progression; the program never prescribes a
  load.

## Blocks

| Weeks | Block | What changes |
| --- | --- | --- |
| 1 | Re-entry | Override: every exercise has one set fewer (minimum 2), target RIR is one higher, no techniques. |
| 1–4 | Base | Rep ranges as written. No intensity techniques. |
| 5–8 | Build | Intensity techniques on the last set of the marked exercises. Isolation exercises go to 0–1 RIR. |
| 9–11 | Peak | Main lifts drop to 5–7 reps @ 1 RIR. Techniques stay. |
| 12 | Deload + test | Override on Days B and C: one set fewer, target RIR 3, no techniques. Day A: work up to a top single on the barbell bench @ 1 RIR, then deload the rest of the day. Deload sets never lower the estimate; the top single updates it. |

Week 1 is an override on the Base block, not a block of its own.

## Days

Sets × reps @ RIR are the Base values. "Build" and "Peak" note what changes in those blocks.
Techniques apply to the last set only.

### Day A — Squat, heavy bench, pulldown

| # | Exercise | Alternatives | Sets × reps @ RIR | Build / Peak |
| --- | --- | --- | --- | --- |
| 1 | Hack squat | Leg press, Smith machine squat | 3 × 6–8 @ 2 | Peak: 3 × 5–7 @ 1 |
| 2 | Barbell bench press (heavy) | Smith machine bench press | 4 × 4–6 @ 2 | Build: 4 × 4–6 @ 1 · Peak: 4 × 3–5 @ 1 |
| 3 | 2-grip lat pulldown | Machine pulldown, 1-arm cable pulldown | 3 × 10–12 @ 1 | — |
| 4 | Seated leg curl | Lying leg curl | 3 × 10–12 @ 1 | Build: lengthened partials |
| 5a | Cable lateral raise | DB lateral raise, machine lateral raise | 3 × 12–15 @ 1 | Build: myo-reps |
| 5b | Overhead cable triceps extension | Overhead DB triceps extension | 2 × 10–12 @ 1 | Build: drop set (2 drops) |

5a and 5b are a superset.

### Day B — Hinge, close-grip bench, row

| # | Exercise | Alternatives | Sets × reps @ RIR | Build / Peak |
| --- | --- | --- | --- | --- |
| 1 | Barbell RDL (straps) | DB Romanian deadlift, 45° hyperextension | 3 × 8–10 @ 2 | Peak: 3 × 6–8 @ 1 |
| 2 | Close-grip bench press | DB bench press, machine chest press | 3 × 6–8 @ 2 | Peak: 3 × 5–7 @ 1 |
| 3 | Chest-supported machine row | Chest-supported DB row, seated cable row | 3 × 10–12 @ 1 | — |
| 4 | Leg extension | Reverse nordic | 3 × 10–12 @ 1 | Build: myo-reps |
| 5 | Machine shoulder press | Seated DB shoulder press, cable shoulder press | 2 × 8–10 @ 1 | — |
| 6a | Bayesian cable curl | Incline DB curl | 3 × 10–12 @ 1 | Build: drop set (2 drops) |
| 6b | Reverse cable crossover | Lying reverse DB flye, 1-arm reverse pec deck | 2 × 12–15 @ 1 | — |

6a and 6b are a superset.

### Day C — Leg press, bench volume, pulldown

| # | Exercise | Alternatives | Sets × reps @ RIR | Build / Peak |
| --- | --- | --- | --- | --- |
| 1 | Leg press | Hack squat, Smith machine squat | 3 × 10–12 @ 1 | Peak: 3 × 8–10 @ 1 |
| 2 | Barbell bench press (volume) | DB bench press, machine chest press | 3 × 6–8 @ 2 | Build: 3 × 6–8 @ 1 · Peak: 3 × 5–7 @ 1 |
| 2b | DB incline press | Incline machine press, Smith machine incline press | 2 × 8–10 @ 1 | Build: drop set (2 drops) |
| 3 | Machine pulldown | 2-grip lat pulldown, neutral-grip pull-up | 3 × 10–12 @ 1 | — |
| 4 | Lying leg curl | Seated leg curl | 2 × 10–12 @ 0 | Build: lengthened partials |
| 5 | Pec deck | Cable flye, DB flye | 2 × 12–15 @ 0 | Build: lengthened partials |
| 6a | Incline DB curl | Bayesian cable curl | 2 × 10–12 @ 1 | — |
| 6b | Triceps pressdown | Cable triceps kickback | 2 × 10–12 @ 1 | — |
| 7 | Seated calf raise | Standing calf raise, leg press calf press | 3 × 12–15 @ 1 | — |

6a and 6b are a superset.

## Rest

- Main lifts (each day's first two exercises, plus Day B's row): 2:30.
- Other exercises: 1:30.
- The rules in `SPEC.md` apply on top of this: no rest before drops, a breathing countdown between
  myo mini-sets, and no rest between superset partners.

## Notes for the program file

- Exercise IDs must match the exercise library, for example `hack_squat`, `db_incline_press`,
  `barbell_bench_press`, `close_grip_bench_press`, `2_grip_lat_pulldown`, `seated_leg_curl`, `cable_lateral_raise`,
  `overhead_cable_triceps_extension`, `barbell_rdl`, `db_incline_press`,
  `chest_supported_machine_row`, `leg_extension`, `machine_shoulder_press`, `bayesian_cable_curl`,
  `reverse_cable_crossover`, `leg_press`, `db_bench_press`, `machine_pulldown`, `lying_leg_curl`,
  `pec_deck`, `incline_db_curl`, `triceps_pressdown`, `seated_calf_raise`. Use the cleaned
  library's ID wherever duplicates were merged.
- The bench goal itself is not part of the program file; it's a lift goal set in the app.
- Slot notes carry the program-specific cues: "same grip, setup and pause every rep" on the bench, "use straps" on the RDL, "chest on the pad, pull with
  the elbows" on the row, and "control the stretch, 1 s pause at the bottom" on the presses and
  curls.
