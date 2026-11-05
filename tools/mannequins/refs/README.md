# References for the missing machines

One `<machine>/ref.md` per piece of equipment the library's exercises need and `kit.mjs` has no kit
for (#161 builds them). Each names a real model to base the recipe on, with links, the dimensions
found, the motion and the fit rules. Brand names live only here; the recipes and the app stay
generic. Links only: no web photo, video frame or 3D model is committed (#149).

Most makers publish a footprint, a height and a stack, but not seat heights, pivot places or ranges
of motion. Those are measured from the makers' photos and the exercises' technique videos (#168):
each such cell says "estimated from" the photo or the video at m:ss it was measured in, and the
known size it was scaled by (a published dimension, a 450 mm bumper plate, a stack plate, or a
person of about 1.75 m, the file naming the height used). What no picture settles is "chosen
from" a named fit rule and the mannequin's body, at low confidence, for the owner's eye in the
studio. The internet is the only source; no one photographs machines at the gym. Each file's last
section, Open gaps, lists what stays chosen and why.

## Build order

The T-bar row machine, then the cable crossover, then the smith machine, then the rest by exercises served.

| # | Machine | Exercises | Based on |
| --- | --- | --- | --- |
| 1 | [`t_bar_row_machine`](t_bar_row_machine/ref.md) | 3 | Titan Chest-Supported T-Bar Row (adjustable); built (#176), posed (#188) |
| 2 | [`cable_crossover`](cable_crossover/ref.md) | 25 | Rogue CC-1 Cable Crossover; built (#181), 19 not yet posed on it (6 are posed on `cable_column`) |
| 3 | [`smith_machine`](smith_machine/ref.md) | 9 | Hammer Strength Smith Machine (7°); built (#182), not yet posed |
| 4 | [`power_rack`](power_rack/ref.md) | 16 | Rogue Monster Lite Rack (RML-390BT) with the Rogue Crown Pull-up Bar and Monster Lite Matador dip handles as add-ons; built (#189, named the pull-up and dip station in the issue), posed (#196) |
| 5 | [`ez_bar`](ez_bar/ref.md) | 3 | Rogue Curl Bar; built (#209), not yet posed |
| 6 | [`pec_deck`](pec_deck/ref.md) | 3 | Hammer Strength Select Pectoral Fly / Rear Deltoid (HS-FLY); built (#208), posed (#241) |
| 7 | [`lateral_raise_machine`](lateral_raise_machine/ref.md) | 2 | Hammer Strength Select Lateral Raise; built (#212, seated only), not yet posed |
| 8 | [`hip_machine`](hip_machine/ref.md) | 2 | Hammer Strength Select Hip Abduction and Adduction; built (#214), not yet posed |
| 9 | [`safety_bar`](safety_bar/ref.md) | 2 | Rogue SB-1 Safety Squat Bar; built (#219), not yet posed |
| 10 | [`hyperextension_bench`](hyperextension_bench/ref.md) | 1 | Bench Fitness REVOLVE 45 Degree Back Extension; built (#220), not yet posed |
| 11 | [`shrug_machine`](shrug_machine/ref.md) | 1 | Hammer Strength Plate-Loaded Shrug; built (#223), not yet posed |
| 12 | [`shoulder_press`](shoulder_press/ref.md) | 1 | Hammer Strength Select Shoulder Press |
| 13 | [`dip_machine`](dip_machine/ref.md) | 1 | Hoist ROC-IT RS-1101 Seated Dip |
| 14 | [`hip_thrust_machine`](hip_thrust_machine/ref.md) | 1 | Hammer Strength Plate-Loaded Glute Drive; built and posed (#229) |
| 15 | [`glute_ham_raise`](glute_ham_raise/ref.md) | 1 | Rogue GH-1 GHD |
| 16 | [`crunch_machine`](crunch_machine/ref.md) | 1 | Hammer Strength Select Abdominal Crunch |
| 17 | [`trap_bar`](trap_bar/ref.md) | 1 | Rogue TB-3 Open Trap Bar; built (#234), not yet posed |
| 18 | [`captains_chair`](captains_chair/ref.md) | 1 | Life Fitness Dip/Leg Raise (LBR-LD) |

The cable crossover serves the 25 cable exercises that use a free handle at an adjustable height.
The other 16 cable exercises (the pulldowns, the seated rows, the pressdown and the rest named in
its `ref.md`) stay on the existing `cable_tower`. The smith machine's 9 ids include
`ffe_smith_machine_lunge`, the same movement as `smith_machine_lunge` with the front foot raised.

## Not here

- `bench` is built, so it isn't in the list above, but it has a reference of its own, the adjustable
  bench and its decline kit ([ref](bench/ref.md)) (#195).
- `plate` (5 exercises) needs no reference: it is a plate in the hands. Use a 450 mm plate, the IWF
  bumper size, which Rogue's
  [Echo bumpers](https://www.roguefitness.com/eu/nl/rogue-lb-echo-bumper-plates-with-white-text)
  keep at every weight; cast-iron plates run smaller (Rogue's
  [45 lb iron plate](https://www.roguefitness.com/ca/rogue-olympic-plates) is 445 mm, the 25 lb 277 mm).
- `weighted_bodyweight` is the power rack plus a dip belt (its file notes the plate
  hangs on the belt's chain at about mid-thigh, between the legs, as the videos show) and the
  hyperextension bench.
- Nordic and reverse nordic curls need only a pad and an anchor (a note in the hyperextension file).
- `chest_supported_t_bar_row` and `t_bar_row`, with `kelso_shrug`, are on `t_bar_row_machine` (#176):
  the `t_bar_row` video shows a chest-supported plate-loaded machine, not a landmine or an
  unsupported T-bar ([ref](t_bar_row_machine/ref.md)).
- `roman_chair_crunch` is a knee raise on a captain's chair, as its video shows, not an exercise on
  the hyperextension bench ([ref](captains_chair/ref.md)). #161 should give it that kit.
- `kelso_shrug` is done chest-supported on the T-bar row machine, as the owner confirms
  ([video](https://youtu.be/76rz0UNAlYI)), so it is on `t_bar_row_machine`, not `shrug_machine`
  ([ref](t_bar_row_machine/ref.md)). That leaves `shrug_machine` at one exercise.

## Gaps

Every dimension a maker doesn't publish is estimated from a viewed photo or video frame, or
chosen from a fit rule at low confidence. What stays chosen, or rough, per machine (each file's Open
gaps says why):

- `t_bar_row_machine` (built, #176; posed, #188): the foot plate setting for the mannequin is
  solved from the fit rule, near photo 07's but not measured on it; the top of the row comes from a three-quarter photo. The recipe runs the neutral grips fore and aft through the
  yoke's top blocks, as photos 05 and 06 show them, where the ref's spec calls them vertical.
- `cable_crossover`: the pulley heights and hole pitch come from three-quarter photos (about ±50 to
  80 mm); the carriage position per exercise is chosen.
- `smith_machine` (built, #182): the 7 degree lean's direction, the bar and sleeve length and the hooks' turn are
  chosen; hook heights are from three-quarter renders (about ±10%). No video uses an angled smith.
- `power_rack`: the bar height (2.30 m) is chosen and the dip handle height is rough, from one
  video. The chin-up video is unavailable.
- `ez_bar`: the bend angles and grip places are chosen; the zigzag depth is a rough photo estimate.
- `pec_deck` (built, #208; posed, #241): built at 2.0 m to the demo's lifter against the page's 1.8 m; the seat
  range, the start position's step, the lever radius and the rear beam's reach stay chosen or rough; no top or
  straight-front view.
- `lateral_raise_machine`: the seat range is chosen and the pivot spacing is rough. The standing
  exercise's video shows another, standing machine, with no reference model of its own.
- `hip_machine` (built, #214): the seat range, closed pad spacing and the range of motion stay chosen or rough; no
  side-on photo, and the video is another make.
- `safety_bar`: the handle arms' length and angle are chosen; the published width and height fit the
  bar held, rolled about 25° forward, and the photos show the rack's steeper hang. No side-on photo
  exists.
- `hyperextension_bench`: the body line and pad height are chosen (the bench telescopes); all sizes
  come from one spec drawing.
- `shrug_machine`: the seat range and the bottom of the rep are chosen; no photo shows the bottom.
- `shoulder_press`: the pivot, yoke length and rotation come from oblique views; the seat range is
  chosen.
- `dip_machine`: the seat positions, grip spread and carriage pivot stay chosen; no front view of
  the Hoist.
- `hip_thrust_machine`: the hips' top height is chosen from the fit rule; the pivot's place and
  radius are rough, hidden in the only side-on photo. Built and posed (#229) from the photos, a seat, a platform and one
  carriage, its two rear-frame horns read as plate storage; the pads' fore-and-aft place (480 mm behind the hips) is by eye, and the range is 23°, the video make's, because the glutes rest on the seat at the bottom.
- `glute_ham_raise`: the pad width and the mannequin's pad-to-plate distance are chosen; pad sizes
  are good to about 10%.
- `crunch_machine`: the axis's place along the body is by eye and the handle spacing is chosen. The
  recipe is Hammer Strength's reclined crunch; the technique video shows an upright one.
- `trap_bar`: the lockout height is chosen from the mannequin's arm; the frame's plan comes from one
  corner photo, mirrored. The kit is closed at the rear, as the TB-1's page shows, and has no brackets or
  jack: the handles are braced straight to the corners.
- `captains_chair`: the plan is scaled from the manual's drawing, but every height is rough (about
  ±50 mm, one three-quarter render, no side view); the arm pads' tilt is chosen flat. The video's
  chair is a lower make with no visible step.

Across them:

- Few technique videos use the based-on model, so most videos settle the range of motion and the fit
  rules, and the photos the sizes. Each file says which make its videos show.
- Rep, Titan's store, Hoist and some dealer pages refused the fetch (HTTP 429 or 403), and Rogue's
  support guides return 403; each file says which.
