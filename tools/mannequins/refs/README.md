# References for the missing machines

One `<machine>/ref.md` per piece of equipment the library's exercises need and `kit.mjs` has no kit
for (#161 builds them). Each names a real model to base the recipe on, with links, the dimensions
found, the motion and the fit rules. Brand names live only here; the recipes and the app stay
generic. Links only: no web photo or 3D model is committed (#149). The owner's own photos get added
later, downscaled.

Dimensions marked "estimated" or "not published" are gaps: most makers publish a footprint, a
height and a stack, but not seat heights, pivot places or ranges of motion. Each file's last section
lists the photos to take at Hukka (the owner's gym) to close them.

## Build order

The T-bar row machine, then the cable crossover, then the smith machine, then the rest by exercises served.

| # | Machine | Exercises | Based on |
| --- | --- | --- | --- |
| 1 | [`t_bar_row_machine`](t_bar_row_machine/ref.md) | 2 | Titan Chest-Supported T-Bar Row (adjustable) |
| 2 | [`cable_crossover`](cable_crossover/ref.md) | 25 | Rogue CC-1 Cable Crossover |
| 3 | [`smith_machine`](smith_machine/ref.md) | 9 | Hammer Strength Smith Machine (7°) |
| 4 | [`pullup_dip_station`](pullup_dip_station/ref.md) | 16 | Rogue Crown Pull-up Bar and Monster Matador dip handles |
| 5 | [`ez_bar`](ez_bar/ref.md) | 3 | Rogue Curl Bar |
| 6 | [`pec_deck`](pec_deck/ref.md) | 3 | Hammer Strength Select Pectoral Fly / Rear Deltoid |
| 7 | [`lateral_raise_machine`](lateral_raise_machine/ref.md) | 2 | Hammer Strength Select Lateral Raise |
| 8 | [`hip_machine`](hip_machine/ref.md) | 2 | Hammer Strength Select Hip Abduction and Adduction |
| 9 | [`safety_bar`](safety_bar/ref.md) | 2 | Rogue SB-1 Safety Squat Bar |
| 10 | [`hyperextension_bench`](hyperextension_bench/ref.md) | 2 | Bench Fitness REVOLVE 45 Degree Back Extension |
| 11 | [`shrug_machine`](shrug_machine/ref.md) | 1 | Hammer Strength Plate-Loaded Shrug |
| 12 | [`shoulder_press`](shoulder_press/ref.md) | 1 | Hammer Strength Select Shoulder Press |
| 13 | [`dip_machine`](dip_machine/ref.md) | 1 | Hoist ROC-IT RS-1101 Seated Dip |
| 14 | [`hip_thrust_machine`](hip_thrust_machine/ref.md) | 1 | Hammer Strength Plate-Loaded Glute Drive |
| 15 | [`glute_ham_raise`](glute_ham_raise/ref.md) | 1 | Rogue GH-1 GHD |
| 16 | [`crunch_machine`](crunch_machine/ref.md) | 1 | Hammer Strength Select Abdominal Crunch |
| 17 | [`trap_bar`](trap_bar/ref.md) | 1 | Rogue TB-3 Open Trap Bar |

The cable crossover serves the 25 cable exercises that use a free handle at an adjustable height.
The other 16 cable exercises (the pulldowns, the seated rows, the pressdown and the rest named in
its `ref.md`) stay on the existing `cable_tower`. The smith machine's 9 ids include
`ffe_smith_machine_lunge`, the same movement as `smith_machine_lunge` with the front foot raised.

## Not here

- `plate` (5 exercises) needs no reference: it is a plate in the hands. Use a 450 mm plate, the IWF
  bumper size, which Rogue's
  [Echo bumpers](https://www.roguefitness.com/eu/nl/rogue-lb-echo-bumper-plates-with-white-text)
  keep at every weight; cast-iron plates run smaller (Rogue's
  [45 lb iron plate](https://www.roguefitness.com/ca/rogue-olympic-plates) is 445 mm, the 25 lb 277 mm).
- `weighted_bodyweight` is the pull-up and dip station plus a belt (its file notes the belt is drawn
  as a plate at the hips) and the hyperextension bench.
- Nordic and reverse nordic curls need only a pad and an anchor (a note in the hyperextension file).
- `kit.mjs` lists `kelso_shrug` under `shrug_machine`, but the owner confirms it is done
  chest-supported on the T-bar row machine ([video](https://youtu.be/76rz0UNAlYI)). #161 should move
  it to `t_bar_row_machine` ([ref](t_bar_row_machine/ref.md)), not `row_machine`. That leaves `shrug_machine` at one exercise.
- `t_bar_row` stays on `row_machine`: its video is titled only "T Bar Row" and could not be watched, so what it shows is unchecked.

## Gaps

- No maker publishes seat and pad heights, pivot places or ranges of motion for these machines, so
  every machine's motion is partly inferred from its type and marked so.
- Only Rogue's trap bar, GHD and pull-up bar have good dimensions. The Hammer Strength machines have
  a footprint and a stack, and no spec sheet or manual with numbers.
- Few side-on photos, and no carriage or pivot close-ups, were reachable. Rep, Titan and Hoist pages
  often refused the fetch (HTTP 429), so none of their numbers are used.
- Several links were seen in a search result but not read (a Rogue support guide, a Life Fitness
  manual); each file says which.
