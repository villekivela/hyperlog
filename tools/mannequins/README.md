# Mannequins

Makes the exercise mannequin's clips (SPEC, Technique videos → The mannequin). Each exercise has a
pose entry in `poses.mjs`. The tool poses the Quaternius mannequin through the rep's lowering in
generic equipment, checks every frame, and writes one `.glb` per exercise and the `mannequins.json`
index into `app/src/main/assets/mannequins/`. The phone only plays them.

```sh
cd tools/mannequins
npm ci                                  # three.js, pinned in package-lock.json
npm run bake                            # node bake.mjs [out-dir]: check every pose, then bake them all
npm run seats                           # solve every seat afresh into seats.json, then bake
npm test                                # entries, kits, checks, bake, trace, review and the studio, a few files at a time
npm run trace -- <pose> <footage> <from>-<to> [--angles …]   # angles from footage (Tracing a pose)
npm run studio                          # the studio, at http://127.0.0.1:5190/studio/ (The studio)
npm run review -- status <batch>        # whether every exercise in it is OK (Reviewing a batch)
npm run review -- phone <batch>         # the owner has swiped the batch in the debug gallery
npm run numbers -- <clip>               # every frame's numbers from a baked clip (Reviewing a movement)
npm run movement -- <id>                # a clip against its movement spec (Reviewing a movement)
```

Before a push, run the test files you touched and `committed.test.mjs`
(`node --test committed.test.mjs <file>.test.mjs`), not `npm test`. CI runs the whole suite on every
PR that touches `tools/mannequins/`, as the `suite` job's four shards (`npm test -- --test-shard=1/4`);
each takes 2–9 minutes. The whole suite on one machine takes 6–8 minutes quiet and 20–23 with several
workers running.

A test that needs a scratch folder takes it from `scratch()` in `tmp.mjs`, which removes it when the
test process ends, also after a failure; `tmp.test.mjs` fails on any test file that calls `mkdtemp`
itself. A run leaves no `mannequins-*` folder in the temp directory.

Commit the baked files with the entries that made them. Baking is deterministic: the same entries
give byte-identical files. CI re-bakes every pose when `tools/mannequins/` or the clips change and
fails on any difference (`committed.test.mjs`), so bake and commit together.

`seats.json` holds each seat the poses use, because solving one takes over a second and a pose needs
two. A bake adds a seat it had to solve, a new radius or `grip.swing`, so commit the file with the
clips. After a change to the grip solve or the mannequin, run `npm run seats`: `seats.test.mjs` fails
on a file that no longer holds what the solve finds. The bake's order goes into the file, since a
solve lands up to 37 µm off depending on where the rig stands, and the clips are baked from it.

## Making a batch

Poses are made in batches (SPEC: the current program's exercises, then barbell, dumbbell, cable,
machine and the rest), listed in `BATCHES` in `review.mjs`. For each batch:

1. Write each exercise's entry in `poses.mjs`, `like:` another where it is a variant.
2. Trace the side-on lifts that have footage (Tracing a pose).
3. `npm run bake`. A pose that fails its checks stops the bake and says which check, on which frame,
   found what; fix the entry and bake again.
4. The owner reviews it in the studio (`npm run studio`, Reviewing a batch): tunes and saves, OKs
   each exercise or leaves a note. Read `review/notes.md`, fix what each note asks, bake, delete the
   note, and ask again, until `npm run review -- status <batch>` says the batch is done.
5. A batch that adds or changes a kit or a part is swiped once in the debug gallery (Settings →
   Debug → Mannequin gallery) before its pull request merges; then `npm run review -- phone
   <batch>`. A batch that only changes poses skips it: three.js and Filament differ in lighting and
   materials, not in joint angles.

## Adjustable-bench poses

An exercise the library gives an **Adjustable bench** (`adjustableBench` in `exercises.json`) is
baked at 0°, 15°, 30°, 45°, 60°, 75° and 85°, one clip each, `<id>_<angle>.glb`; a decline movement
at its one setting. Its entry has a
`bench` and a motion that follows the pad, so one entry fits every angle:

```js
db_incline_press: {
  motion: 'dumbbell_press',
  kit: { dumbbells: {}, bench: { hinge: -0.02 } },   // the backrest turns about this z
  bench: { typical: 45, cameras: { 0: { …camera }, 75: { …camera } }, perAngle: { 'feet.z': { 0: 0.2, 75: 0.3 } } },
  body: { sit: 0.15, seat: 0.06, lean: 8, head: 0 }, // see below
  top: { press: 0, over: [0.581, 0.283], rotate: 0, tilt: 0 },
  bottom: { press: 1, chest: [0.1592, -0.0137], rotate: 0, tilt: 0 },
  grip: { width: 0.4, lockout: 0.26, elbows: 0.7975 },
},
```

- **The backrest's frame.** The numbers are written against the backrest, so one set holds from
  0° to 85°. `body.sit` is how far the hips sit off the pad's face and `body.seat` how far down it
  from the seat-back corner (the body slides down the pad, never into the seat); `body.lean` leans
  the trunk off the pad's line and `body.head` tilts the head from the trunk (0 lies along the pad).
  The hands are measured from the shoulders in the trunk's frame: `top.over` is [out from the
  chest, up the trunk] (the arms as straight as they reach) and `bottom.chest` [out from the chest,
  down the trunk]. The feet stand in the world, where the bench is fixed.
- **The dumbbells turn.** `rotate` twists the grip (0 overhand, the handle across the body; 90
  neutral, along it; anything between) and `tilt` tips the handle in the hand, in the palm's plane
  (the ends up or down). Each is set at the top and the bottom and eases between them, so neutral
  at the bottom may turn overhand at the top. (`grip.rotate` and `grip.tilt` set one for the whole
  rep, for an entry with no `top` or `bottom` of its own.)
- **`bench.typical`** is the angle shown with none set; the bake fails without it. `bench.cameras`
  gives the framing at some angles: those between move steadily from one to the next, and past the
  last it holds. `prepare(id, poses, angle)` poses the entry at an angle, its typical one by default.
- **Per angle** (`bench.perAngle`): a number that one value can't fit at every angle is tuned at
  the angles that need it, `{ path: { angle: value } }` with the path of a single number (`top.over.0`).
  Between the tuned angles it interpolates, and beyond them it holds the nearest one's.
- The bench's back pad is the node `bench_back`, with its pivot at the hinge. In the index the
  entry's own `clip` and `camera` are those of the clip nearest the typical angle, and
  `bench: { typical, hinge: [y, z], clips: { "0": { clip, camera }, … } }` lists the rest.
- `node render.mjs <id> [out-dir] --angle <degrees>` renders one angle. In the studio `<` and `>`
  step the bench angle, and `p` makes the selected number **Per angle** (or the same at every angle
  again): a Per angle value is tuned at the angle shown, the angle carries a • where something is
  tuned, and a save writes it into `bench.perAngle`. The studio poses the entry at the angle shown.
  A verdict's `clip` fingerprint covers all seven clips.

## Wrists and grip turn

Every pose with hands keeps its wrists straight (SPEC, #127): the back of the hand in line with the
forearm, a wrist the pose doesn't set within 5° of it. A hand on something that lies its own way (a
bar, a handle the pose sets) takes it across the palm and rolls about it; the elbow goes round the
line from the shoulder to the wrist, keeping to the pose's pole, and closer to it where the arm was
traced or the entry sets `grip.pole`. Of all the arms that fit, the solve takes the straightest
wrist first, then the elbow nearest its pole, then the knuckles nearest along the bar: where a
straight wrist can't have its knuckles along it (a wide grip at a press's lockout), the bar slants
across the palm, as much as 35°, as a lifter's does. A free handle (a dumbbell, a stirrup) turns
with the hand. Where no straight arm reaches at all, the arm reaches as far as it can, the wrist
straight and the handle slanting in the hand. Near full reach an arm eases into straight over its
last 4 mm, so an elbow never snaps open at lockout.

The motion has no jumps: each side's arm, and each finger's closing, is followed through the rep
from both ends, the cheaper way kept, and averaged over a few frames, so where the best arm
changes branch the elbow travels there instead of jumping; and the knees give way smoothly as a
pull's bar passes them, the legs' front rounded over the kneecap and the give eased in. A test
fails any joint whose step between two frames stands out from the steps either side of it by more
than 2°, in every clip.

## Grips

Every grip is solved to what the hand holds (#162, `grip.mjs`, from `docs/research/grip.md`).
Each names where the bar sits in the hand, its landmark, and the thumb:

- **`grip.sits`**: `heel` (a press: the bench, the dumbbell, chest and cable shoulder presses, the
  low-bar squat), `mid` (a curl, a pressdown, the high-bar squat, a bridge) or `base` (a pull: a
  deadlift, a row, a pulldown, a raise, the machines' handles), each motion's by default; or `open`,
  a hand that holds nothing round (a cross-arm hold's, `grip.crossed`).
- **`grip.thumb`**: `wrapped` round the bar (the default) or `thumbless`, over it beside the fingers
  (the low-bar squat).

The landmarks lie along the palm from the wrist joint to the knuckles (122 mm): the heel at 0.65 of
the way, mid-palm at 0.72, the base of the fingers at 0.8. The research puts a lifter's heel at
0.2-0.3 and mid-palm at 0.45 of the same length, but this mannequin's palm is deep (4.5 cm, so a
bar rests 5-8 cm off its bones) and its fingers 3.5 cm thick: they close round nothing lower than
about 0.5, short by 24-31 mm at 0.3 and 6-9 mm at 0.45, and below 0.65 the thumb can't come round a
press's bar as well. Its heel of the palm is as low as every digit reaches, higher than a lifter's,
and the three landmarks lie close.

The bar's radius is the kit's part under the hand (a barbell's 14 mm, a stirrup's 16.5 mm sleeve
and the 12 mm bar past it, a chest press's 20.5 mm), and the hand is seated so its palm, the ball
of the thumb with it, rests on that part's skin at the landmark, for the way the bar slants and
tilts across the palm, measured on the hand as it closes round the bar, since the fingers and the
thumb carry some of the palm's skin with them. Then each finger closes joint by joint until it touches the part: its
joints together in a fist's proportions until a link touches, then the joints past that link,
out to the tip. The thumb's root swings out of the palm, as far as the landmark has it (60° at the
heel, 35° mid-palm, 25° at the base of the fingers, so it lies round the bar rather than standing
off it) or an entry's `grip.swing`, and its other joints bend about the bar until the thumb touches
it or, wrapped round a bar under 40 mm, the index and middle fingers where they come round it. It runs on every frame: the stage places the kit for the arms as they are and the hand
closes on the part where it is, along its own line, in its pieces (a sleeve and the bar under it).
Bone offsets still turn the fingers on top. The research's wrist angles (5–10° of extension for a
press, the upright row's flexion) are an open question for the owner; until then a wrist stays
straight unless the pose sets it (Wrists and grip turn, above).

The **contact** check fails a hand that stands off its part, or sinks into it, by more than 2 mm on
any frame: each finger and the thumb within 2 mm of its surface, and no part of the hand more than
2 mm inside it; the palm, measured on a grid over its large triangles, within 3 mm either way. In the studio a hand's close-up marks in red where it misses on the frame shown.
The hands check also fails fingers folded into one another (their joints under 1.3 cm apart).

`top.wrist` and `bottom.wrist`, [flexion toward the palm, deviation toward the index finger] in
degrees, set the wrist from straight, and then win: the elbow goes where the pole points it (one
given at the bottom alone eases from straight). The joints check fails a wrist bent more than 5°
in a pose that doesn't set its wrists. The grip's turn is `top.rotate` and `bottom.rotate`, each
from `grip.rotate`, which stands for both ends. In the studio the wrist's two numbers at each end are
labelled in words, "Wrist bend: + flexion / − extension" and "Wrist tilt: + radial (thumb side) /
− ulnar (little-finger side)", and start straight where the entry sets no wrist; a drag of the
fingers handle bends the wrist, and a squat's elbow handle sets `grip.pole`.

## Grips set by set

An exercise done with a different grip set by set has `grips: { <name>: { …changes } }`, in the
order the sets take them; each grip is merged over the entry as `like:` merges, and the first is
the entry's own. The bake makes a clip per grip (`<id>_<grip>.glb`), checks each, and the index
lists them in that order, `grips: [{ name, clip, camera }, …]`, the entry's own `clip` the first
set's. The phone's sheet shows a button per grip (`wide`, `narrow`) and opens on the grip of the
set being done. A verdict's fingerprint covers every grip's clip; the studio poses the first grip.

## Pose entries

An entry is data only, keyed by the exercise's id in `app/src/main/assets/exercises.json`.
Positions are in metres, y up, with the mannequin facing +z and its hips over the origin; angles
are in degrees.

```js
lat_pulldown: {
  equipment: 'cable',                   // the library's equipment key
  motion: 'pulldown',                   // how the body moves (motions.mjs)
  kit: { cable_tower: { column: 0.95, pulley: [2.25, 0.02], attachment: 'lat_bar',
    seat: [0.47, 0.02], thighPad: [0.71, 0.3] } },
  body: { hip: [0.56, 0] },             // where a seated or lying body sits: hip centre [y, z]
  feet: { stance: 0.16, z: 0.32, toeOut: 12 },
  top: { trunk: -18, bar: [1.62, 0.02] },       // the top of the rep
  bottom: { trunk: -27, bar: [1.03, 0.02] },    // and its bottom
  grip: { width: 0.4 },                 // the hands either side of the middle
  camera: { target: [0, 1.12, 0.38], halfHeight: 1.18, azimuth: 62, elevation: 8 },
},
close_grip_lat_pulldown: { like: 'lat_pulldown', grip: { width: 0.24 }, bottom: { bar: [1.14, 0.02] } },
```

- **`like: <id>`** takes another entry and changes only what this one gives. Objects merge key by
  key; anything else, a point or a list included, is replaced whole. Likes chain.
- **`top` and `bottom`** give the values the motion moves between, eased; only the lowering is
  baked, from the top (0 s) to the bottom (1 s).
- **`feet.y`** (standing motions) stands the feet on a platform that high, a deficit's; list the
  `platform` kit in `kit` too, and it stands under them at that height.
- **`body.arch`** (lying motions) bends the back: three angles, in degrees, added to the spine's
  lean for the lumbar, chest and upper-back segments. The lumbar and chest turn up and the upper
  back down, which lifts the chest and the lower back off the pad while the upper back and the
  hips stay on it.
- **`top.shrug`, `bottom.shrug`** (pulldown) lift the shoulders, in degrees: let up into the stretch
  at the top, down again as the elbows drive down. **`grip.elbows`** turns the elbows out from the
  sides: 1 out wide, 0 straight down by the sides (a pulldown's default is 0.8; the bench's, 0.9).
- **`kit: { dumbbells: { hand: 1 } }`** carries one dumbbell, in that hand (1 the left, -1 the
  right), for a lift with `grip.one`. Without it the kit has one in each hand. Any motion can hold
  dumbbells: one with a bar's two grips hangs them there instead, and they turn with the hands.
  **`steady: true`** lays each by the palm as well as the handle's line, so it turns with the hand
  and can't roll in it. Every dumbbell entry wants it. **`heavy: true`** gives it bigger heads, for a
  lift done with a big dumbbell, such as the Kroc row.
- **`body.kneel`** (`arms`, `free_arms`, `row`) puts the body on its knees at `feet.z`, the shins
  back along the floor and the toes tucked; `thigh` sets the hips over them.
- **`feet.lift`** (standing `arms`, `free_arms`, `row`) takes one foot off the floor (1 the left), and
  `top.leg`, `bottom.leg` set that leg: [back, out, bend] in degrees, the thigh from straight down,
  behind the hip and out to its side, and the knee's bend.
- **`grip.restOn`** closes `grip.one`'s resting hand round a bar of that radius, a tower's post, where
  it would otherwise lie relaxed. **`grip.restPole`** points that arm's elbow, as `grip.pole` does the
  working arm's.
- **`grip.joined`** (`free_arms`) holds one thing in both hands, that far either side of the point
  `hand` gives, which isn't mirrored. **`grip.via`** [x, y, z] (`free_arms`) curves the hand through
  that point at the middle of the rep, for a hand riding something that swings on an arc.
- **`grip.level`** (dumbbells) lets the wrists flow: their share of the forearms' lean, 0 to 1. The
  hand stays square to the handle, the wrist bends with that share, and the handle tips with the
  rest. 0 is a straight wrist, the handle tipping with the forearm; 1 holds the handle dead level.
  It counts as the pose setting its wrists.
- **`grip.rotate`** (any pose with hands) twists the forearms, in degrees: 0 overhand, 90 neutral
  (palms toward each other), 180 underhand (palms toward the face). The default is 0; the
  close-grip pulldown is 180.
- **`grip.sits`** and **`grip.thumb`** (any pose with hands) say where the bar sits in the hand and
  what the thumb does, where the motion's default doesn't suit; **`grip.swing`**, how far a wrapped
  thumb's root swings out of the palm, in degrees from 0 to 90, where the landmark's doesn't lay
  it round the bar (the curl's 40°, the squats' 50° and 55°) (Grips, above).
- **`tempo`** (optional) is the rep in four phases, in seconds: `{ lowering, bottom, lifting, top }`,
  the lowering, the pause at the bottom, the lifting and the pause at the top. An entry without
  one takes its motion's default (`tempo.mjs`). The bake writes it into `mannequins.json`, and the
  phone plays the baked lowering forward over `lowering`, holds `bottom`, plays it backward over
  `lifting` and holds `top`.
- **Side-on angles**, as `trace` measures them: `shin` leans the knee forward of the ankle, `thigh`
  puts the hip behind the knee (90 is a level thigh), `trunk` leans the line from the hips to the
  shoulders forward (negative leans back), `upperArm` and `forearm` swing the elbow and the hand
  forward from hanging straight down. A motion turns them into the mannequin's own joints.
- **`trace: <name>`** reads `traces/<name>.json`. Each angle it holds replaces that angle's `top`
  and `bottom`, sample by sample.
- **`top.offsets`, `bottom.offsets`** turn any bone on top of the solve (#164): `{ <bone>: [x, y, z] }`
  in degrees, about the axes the bone had in the bind pose (x across the body, y up, z forward),
  carried with it, so its children turn with it. They ease between the ends like the rest, and a
  bone turned at one end alone eases from none at the other. The trunk's and the shoulder blades'
  are turned before the limbs reach from them, so the hands and feet still hold. A limb's own are
  turned and the limb is solved again to its contact (`offsetLimb` in `rig.mjs`): the thigh's or
  the upper arm's swings the knee or the elbow round the line to the contact, the shin's or the
  forearm's keeps only its twist (the contact sets how far the joint bends), and the foot or the
  hand keeps its whole offset, the limb moving so the ankle stays put or the grip stays in the
  palm, before the fingers close. The fingers' and the toes' come last. A hand turned this way counts as the pose setting its wrist. An entry without offsets
  bakes byte for byte as it did.
- **`camera`** frames the stage: `target` is the point the camera looks at, `halfHeight` half the
  height it shows there, and `azimuth` and `elevation` the turn and tilt it opens on. A double-tap
  on the phone returns to them. An adjustable bench's `bench.cameras` gives framings by angle, and
  each angle's moves steadily between the nearest given either side. `shadow: { size: [w, d], at: [x, z] }` moves the floor shadow.

The motions, in `motions.mjs`, and what each reads besides the above:

| Motion | The body | Reads |
| --- | --- | --- |
| `squat` | a bar on the back, kept over the middle of the feet | `shin`, `thigh` (and `trunk` when traced: the body then keeps its shape and turns about the ankles); `bar: { up, back }` (`back` negative racks it in front); `feet.kneesOut`; `grip.pole` for the elbows (with `grip.crossed`, how high they ride), and `grip.crossed`, a cross-arm front squat: the bar rides on the front delts and each hand lies open on the other shoulder at `grip.spot` [across, up, forward] from its joint, not gripping the bar |
| `hinge` | a bar hanging in straight arms, straight up and down at `bar.z` in front of the ankles: an RDL, a deadlift, a shrug | `shin`, `thigh`, `trunk`; `shrug`, which lifts the bar with the shoulders; or, for a pull from the floor, `bar` (its height at the top and the bottom) in place of `trunk`: the trunk leans as far as the straight arms need, and the shins stand back where the bar would run into the shins or thighs |
| `row` | hinged over, pulling a bar straight up and down at `bar.z`: a bent-over row, a Pendlay row | `shin`, `thigh`, `trunk`; `bar`, its height at the top and the bottom; `grip.pole` for the elbows. A drag of the hands sets the end's `bar` and `bar.z` |
| `bench_press` | lying on a bench, pressing a bar | `bar: [y, z]`; `feet.z`; `grip.elbows`, 0 tucked to 1 flared, a light hint the hand on the bar outweighs; `grip.pole` [out, up, forward] points the elbows firmly instead, the bar slanting across the palm to let them; `body.head`, the head's tilt from the spine to rest it on the pad |
| `dumbbell_press` | on an adjustable bench at `bench.angle`, pressing dumbbells | `body.sit`, `body.seat`, `body.lean`, `body.head`; `top.over` [out, up] and `bottom.chest` [out, down] from the shoulders in the trunk's frame; `rotate`, `tilt`; `grip.lockout`, `grip.width`, `grip.elbows` |
| `pulldown` | seated under a thigh pad, pulling a bar down | `trunk`, `bar: [y, z]` |
| `pressdown` | standing at a high pulley, elbows still | `shin`, `thigh`, `trunk`, `upperArm`, `forearm` |
| `leg_press` | seated in a 45° sled, feet on its plate | `sled`, metres above its stops; `feet.down`, how far below the plate's centre the ankles sit |
| `chest_press` | seated, pressing horizontal handles on hung levers, held like a bar | `levers`, degrees forward of where they rest; `grip.width` |
| `arms` | the arms swinging side-on, standing or seated (`body.hip`): a curl, a preacher curl, an extension overhead | `upperArm`, `forearm` (on a machine, its `lever` in place of `forearm`: the bar is the machine's) (an angle past 180 swings the forearm on over the top, behind the head); `armOut`, degrees the arm leans out to the side about the shoulder, away from the body whether it is raised or hanging (a Y overhead, a lateral raise), easing from none at an end without one; `grip.width`; `grip.one` (1 the left hand) with `grip.rest` [x, y, z] for the other; `grip.pole` [out, up, forward], where the elbows point; `feet.stagger` stands the left foot that far ahead of the right (behind, if negative); `body.arch`, `body.look` lying on a bench, as a press has them |
| `free_arms` | the arms moving freely, standing or seated: a raise, a flye, a press between pulleys, an upright row (a bar lies between the hands) | `hand: [x, y, z]`, the left hand's point at the top and the bottom (the right mirrors it); `grip.one`, `grip.rest`, `grip.pole`, `feet.stagger` as for `arms`; `grip.arc` (a flye, a raise) sweeps the hand on an arc about its shoulder from the top's point to the bottom's, its reach between theirs, so the elbow holds its bend; `grip.across` [x, y, z] lays the knuckles along one line through the rep (a dumbbell raise's, front to back); `body.arch`, `body.look` lying on a bench, as a press has them |
| `knee_lever` | the knees bending against a roller: seated (a leg extension, a seated curl) or `body.prone`, face down (a lying curl) | `lever`, degrees from the stretch; `body.thigh` (below level); `grip.pole`. The shin lies against the roller, along the lever with the knee on its axis |
| `calf_raise` | seated, the knees under a lever's pads, the balls of the feet on a platform's edge | `lever`, degrees up from the stretch: the heels rise as far as it takes the knee to reach it |
| `hack_squat` | in a hack squat's sled, the back on its pad | `carriage`, metres above its stops; `body.trunk`; `feet.stance`, `feet.toeOut`, `feet.kneesOut`; `grip.pole` |
| `lever_handles` | seated, handles swinging on levers about a pivot: a chest-supported row, a machine pulldown | `levers`, degrees up from level ahead; `grip.width` |
| `chest_pad` | standing on a machine's foot plate, the spine along its inclined chest pad, the hands on grips its lever carries: a chest-supported T-bar row, a Kelso shrug | `lever`, degrees up from level ahead; `feet.stance`, `feet.toeOut`, `feet.kneesOut`; `body.look`, how far the head tips from the spine toward the chest (negative lifts it); `grip.pole`. The shoulder blades move by `offsets` on the clavicles |
| `hip_thrust` | seated on a low seat, the upper back on fixed pads, a belt over the hips on a carriage that turns about a pivot behind the body, the feet flat on a tilted platform, the hands on the carriage's handles: a machine hip thrust | `carriage`, degrees up from level ahead; `feet.stance`, `feet.toeOut`, `feet.kneesOut`; `body.look`; `grip.pole`. The hips ride the carriage and the spine runs from them along the pads |
| `hang` | hanging from the rack's bar by the hands, the feet off the floor: a pull-up, a chin-up, a hang, a leg raise | `kit.power_rack.handles`, the pair of grips; `rise`, metres the shoulders lift from straight arms under the grips; `back`, metres behind them; `trunk`, `shrug`; `thigh`, `shin`, `feet.stance`, `feet.point`, `feet.kneesOut` and `body.look`, as for `support`; `grip.pole` |
| `support` | resting on the rack's dip handles by the hands, the feet off the floor: a dip | `kit.power_rack.handles`; `sink`, metres the shoulders drop from locked arms over the grips; `over`, metres ahead of them; `trunk`, `shrug`; `thigh` swings the knee forward of the hip and `shin` the knee forward of the ankle, as standing; `feet.stance`; `feet.point`, degrees the feet turn down from square to the shin; `feet.kneesOut`; `body.look`, how far the head tips from the trunk toward the chest; `grip.pole` |
| `chair` | on a captain's chair: the forearms on the arm pads, the upper arms upright, the hands closed on the grips, the back to the back pad, the legs hanging free: a knee raise | `kit.captains_chair`; `back`, metres the shoulders stand ahead of where the hands' reach puts them, to set the back against the pad; `trunk`, `shrug`; `thigh`, `shin`, `feet.stance`, `feet.point`, `feet.kneesOut` and `body.look`, as for `support`; `grip.pole` |
| `glute_ham_raise` | kneeling on a glute-ham developer's pads, the soles on its plate, the body lowering about the knees with the hips straight; the hands hang, then rest on its handles | `lean`, the thigh's and spine's lean from upright, in degrees, at the top and the bottom; `body.look`; `feet.stance`. The knee, the ankle and the handles are the recipe's |
| `lunge` | a split stance, the feet planted through the rep, the dumbbells hanging by the sides | `hip: [y, z]`, `trunk` and `heel`, degrees the back heel stands off the floor, at the top and the bottom; `feet.front` and `feet.back`, the left ankle ahead and the right ball behind; `feet.cross`, how far the back foot steps across behind the front one (a curtsy); `feet.hips`, how far the hips and arms move across with it; `feet.stance`, `feet.toeOut`, `feet.kneesOut`; `grip.width`; `grip.pole`. The back toes bend as far as the heel rises, so they stay on the floor |
| `bench_row` | one knee and the same-side hand on a flat bench, the other foot on the floor: a 1-arm row. The right knee and hand, the left foot and arm | `body.hip`, `body.trunk`; `feet.knee` [y, z], the shin running back along the pad from it; `feet.stance`, `feet.point`, degrees the foot off the pad's end tips, `feet.out`, `feet.z`, `feet.toeOut`; `hand` [x, y, z] at the top and the bottom; `grip.one`, `grip.rest`, `grip.pole`. An offset on `hand_r` lays the resting hand flat |
| `bridge` | lying face up, the knees bent: a glute bridge, a hip thrust (the upper back across a bench) | `hip: [y, z]` at the top and bottom, each a trunk's length from `body.shoulders` [y, z], the shoulder joints, which the trunk turns about; `bar: [y, z]` at the top and bottom (or `bar.up` over the hips, square to the trunk); `arch` [3 degrees] at the top and bottom bends the spine's links, the shoulders kept where the straight back's are |

## Equipment

Every kind of equipment in the library, and the generic kits a pose in it can use (`EQUIPMENT` in
`kit.mjs`). A kind marked missing has no kit yet, so its exercises can't be posed until it has one.

| Equipment | Kits |
| --- | --- |
| `barbell` | `barbell`, `bench`, `platform` (a low one under the feet, for a deficit) |
| `dumbbell` | `dumbbells`, `bench` |
| `cable` | `cable_tower` (with a seat and thigh pad for a pulldown), `cable_column`, `cable_crossover`, `bench` |
| `machine` | the machine kits by kind, below; the kinds marked missing have none yet |
| `bodyweight` | `power_rack` for the pull-ups, chin-ups, hangs and dips, `captains_chair` for the roman chair crunch; none needed for the rest |
| `ez_bar` | `ez_bar` |
| `trap_bar` | `trap_bar` |
| `safety_bar` | `safety_bar` |
| `smith_machine` | `smith_machine`, `bench`, `platform` (a low one under the front foot, for the front-foot-elevated lunge) |
| `plate` | missing |
| `weighted_bodyweight` | `power_rack`, `dip_belt` (a plate on a chain, for the weighted lifts), `hyperextension_bench` for the 45° hyperextension |

The library's machines by kind (`MACHINES` in `kit.mjs`):

| Machine kind | Exercises | Kit |
| --- | --- | --- |
| `leg_press` | Leg Press, 1-Leg Leg Press, Leg Press Calf Press | `leg_press` |
| `chest_press` | Machine Chest Press, Incline Machine Press | `chest_press` |
| `squat_machine` | Hack Squat, Pendulum Squat, Machine Squat | `squat_machine` |
| `leg_extension` | Leg Extension | `leg_extension` |
| `leg_curl` | Lying and Seated Leg Curl | `leg_curl` (`lying` for the lying one) |
| `glute_ham_raise` | Glute-Ham Raise | `glute_ham_raise` (posed, #230) |
| `pec_deck` | Pec Deck, Reverse and 1-Arm Reverse Pec Deck | `pec_deck` |
| `row_machine` | Machine Row, Chest-Supported Machine Row | `row_machine` |
| `t_bar_row_machine` | Chest-Supported T-Bar Row, T-Bar Row, Machine Kelso Shrug | `t_bar_row_machine` |
| `pulldown_machine` | Machine Pulldown, Machine Lat Pullover | `pulldown_machine` |
| `shoulder_press` | Machine Shoulder Press | missing |
| `lateral_raise_machine` | Seated and Standing Machine Lateral Raise | `lateral_raise_machine` (seated) |
| `preacher_curl_machine` | Machine Preacher Curl | `preacher_curl_machine` |
| `dip_machine` | Seated Machine Dip | `dip_machine` |
| `hip_machine` | Machine Hip Abduction and Adduction | `hip_machine` |
| `hip_thrust_machine` | Machine Hip Thrust | `hip_thrust_machine` (posed, #229) |
| `calf_raise_machine` | Standing, Seated and Donkey Calf Raise | `calf_raise_machine` (seated) |
| `crunch_machine` | Machine Crunch | missing |
| `shrug_machine` | Machine Shrug | `shrug_machine` |

- `bench` stands at `at` [x, z], where a pose needs it elsewhere: its node moves, so its parts stay
  the ones every clip shares. It is flat, or adjustable with `angle` raising its back pad from flat, to 85° at most; `seat`
  tilts its seat's front from -15° to 40°, flat by default, on a joint the studio scrubs (a pose on a
  tilted seat places its own hips, as the motions assume a flat one); with `decline`, for a decline
  movement's entry and no other, its back pad carries the foot catch, a post out of the frame's far
  end with two pairs of rollers, which turns with the pad, and it stands at the decline setting
  (`DECLINE`, 15°, the lifter reversed), the one angle such a pose is baked at; with `across: z` it
  stands across the body, its middle at that z (a hip thrust's), and its own `z` then runs along it
  (so give `z: 0` to centre it). Its sizes are in `refs/bench/ref.md`.
- `cable_column` is one adjustable column (or two, `twin`, mirrored at [-x, z]) standing at `at`
  [x, z] and facing the middle of the room, its carriage `pulley` metres up, its pulley swivelling
  toward the cable. `attachment` is `handle` (a stirrup in the hand on side `hand`) or `rope`; with
  `twin`, a stirrup in each hand, each on its own column's cable, or the far one's with `crossed`.
  A stirrup lies toward the pulley, or with `through` (degrees) the forearm goes through it, as a
  low-pulley press holds it: its ring lies behind the wrist, that far back along the forearm from
  straight behind the palm (30 puts it about level with the wrist joint), and the cable leaves it
  there, behind the arm.
  Its stack is lowest where the cable is drawn least, at whichever end of the rep that is, so it
  rises through a raise or a flye whose top is the hands' far point, and even there it is held 6 cm
  off its rest (`HELD`), as a working stack is; the leg machines' stacks are held the same way. A
  kit's `place(P, top, bottom)` gets both ends of the rep for that.
- `cable_tower` takes the pulley's height and place, its column, and an `attachment`: `lat_bar`,
  `straight_bar`, `v_handle` or `handles` (two stirrups `spread` metres either side of the middle,
  their grips front to back). The cable meets the front of the pulley's wheel and runs over a
  second wheel above the stack, which stands far enough behind the pulley for it. Its weight stack
  rises as the cable is drawn out, and is held 6 cm off its rest even at the stretch (`HELD`), as
  every working stack is. A pose draws on its high pulley only (1.5 m or more) for now; without a
  seat, the tower also shows its low pulley, in a housing on the floor rail in front of the stack.
- A joint may have `settings`, the angles its holes allow: the studio's row then drags, steps and
  types onto them, and a pose or the phone can still stand it anywhere in its limits. The bench's
  pads have them.
- `bench` rolls on wheels at its head end, lifted by a handle under the seat's front. Its back pad
  turns about a hinge on its underside, so its face swings out as it rises; an arc of holes hangs
  from the pad through the slot in the spine, so nothing is set per angle and the phone can turn it
  to any angle.
- The machines take their seat, `seat` [y, z] (the top of its cushion), and fit the rest to the
  body: `leg_extension` and the seated `leg_curl` a seat tilted `tilt` degrees up toward the knees,
  a cam lever about the knees and a back pad `pad` metres behind the spine; the lying
  `leg_curl` (`lying: true`) a bed humped at `hips` [y, z], its thigh and chest pads sloping down
  `thigh` and `chest` degrees from there; `calf_raise_machine` the foot block under the balls and a
  lever on a post, its pads over the knees and its handle in the hands;
  `squat_machine` the platform, the rails and a carriage riding them with the hips on it, its
  back pad's face `pad` metres behind the hips and its handles along the grip;
  `row_machine` a chest pad, `chest` [up, front] from the trunk at the top of the rep;
  `pulldown_machine` a back pad `pad` metres behind the spine and thigh pads at `thighPad` [y, z],
  hung under their bar; `preacher_curl_machine` an arm pad `pad` metres deep under the upper arms,
  its front edge short of the elbows. The three are plate-loaded: `row_machine` and
  `pulldown_machine` turn their levers on hubs at their own pivot (the row's low in front of the
  knees, the pulldown's on a portal ahead of the head), each lever carrying a loaded horn on an
  arm out from its hub; `preacher_curl_machine` turns its bar's levers on hubs in line with the
  elbows, beyond the pad, each with a loaded horn on a stub below it. Every plate rises as the
  muscle works.
- `lateral_raise_machine` (#212, seated only; the standing lateral raise has its own geometry in the
  ref and no kit yet) sets its pivots on the mannequin's shoulders, 1.10 m up, whatever the pose,
  and the seat moves to them (`seat`, solved to 0.51 m for this mannequin, in its 0.40 to 0.56 m
  range). Each arm is a revolute lever, `arm_l` and `arm_r`, from 10° out of hanging to horizontal,
  with a pad under the upper arm and a grip ahead of it; the one stack stands in a tower beside the user's right, as the ref's photos have it, and hangs on `arm_l`. The base is 0.94 m wide by 1.07 m long, as the ref's footprint. Shown alone
  in the studio, its arms scrub through their limits: `npm run studio`, then the kit's row in the
  list, from the front and the side with the camera.
- `t_bar_row_machine` stands where its ref puts it, whatever the pose (the first machine built
  machine-first): a pad inclined 40° on a beam held up by two posts,
  a foot plate `footPlate` metres down the beam from the pad's bottom end on its pop-pin, and one
  lever pivoting 420 mm up on the rear post, beside the posts, with a yoke of neutral grips (fore
  and aft through the blocks atop its uprights) and wide grips (out from them), and a plate on a
  horn at its front end. Its lever rests on its catch, 3° below level, with no pose. Its `handles`
  option names the pair a pose holds, `neutral` or `wide`, and a pose must give it; its contacts tie
  the hands to that pair (to either with no pose), the chest to the pad and the feet to the foot plate. The pad puts
  the hips: the spine along it, the top of the sternum at its top end, the chest pressed 5 mm into
  the vinyl. The fit rule sets `footPlate` from the legs, for knees bent to 160°, as far down the
  beam as the pop-pin goes: 913 mm for the mannequin, near photo 07's 950. The plate stays on its
  holes, from 100 to 950 mm, wherever a slider asks for it. Its grips stand
  410 mm above the lever's line, which is where the ref's 780 mm ahead and 370 mm up put them with
  the lever on its catch, and a neutral grip is held 115 mm back from its block, where its rear half
  starts.
- `hip_thrust_machine` (#229) stands where its ref and the photos' measurements put it
  (`build/hip-thrust-refs/notes.md` while it was built, each part read off the photos against the
  450 mm plate, from the hip joint at z = 0): a seat 500 mm up, its front edge 140 mm ahead of the
  hips; a foot platform 800 mm wide, 450 mm up and 400 deep, 220 mm ahead of the seat and tilted 15°
  toes up; two round back pads 260 mm across, **fixed** on the frame 480 mm behind the hips with a
  gap for the spine, their top 830 mm up; and one carriage (`carriage`, a revolute joint, degrees up
  from level ahead) on stub axles through the tops of two rear posts, 880 mm up and 820 mm behind the
  hips, carrying the belt, two upright handles and two plate horns with a 450 mm plate each, resting
  on a stop at the bottom of the rep and turning 23° to the top. Two more horns on the rear posts store
  plates. The hips push the belt up while the back stays on the pads, so the body turns over them: the
  hip joint rises from 600 mm (the glutes on the seat) to 930 mm (the thighs level, the shins upright)
  as the torso goes from about 45° above level to about level. The belt is a soft padded band, so the
  checks don't take it for a solid and a test holds it to the body instead (3 cm either way). Its
  contacts tie the hands to the handles, the upper back to the pads (`back`) and the feet to the
  platform. It has no seat, pad or platform adjustment; its one fit rule, unsolved, is the `strap`
  option, `short` or `long`, the loop of the dual-loop waist strap, the long one lifting the belt 40
  mm higher. The `hip_thrust` motion hangs the body from it: the hips on the carriage, the spine
  along the pads, the feet flat on the platform, the hands on the handles. Where it departs from the
  photos: straight carriage arms and handles where the photos curve them, round pad cylinders for
  the half-rounds, the platform on four legs for an A-frame, and no band pegs.
- `cable_crossover` (#181) is the machine of the free-handle cable exercises no pose has set up on
  `cable_column` or `cable_tower` (`CABLE_MACHINES` in `kit.mjs`, 19 of its ref's 25; a posed one
  moves when it is posed machine-first, so no clip changes): two towers 2210 mm apart on the centre
  line, a 2724 mm crossmember resting on their front posts, each tower a rail up the post's face, a
  stack behind between its uprights and a floor beam under both. `position` (1 to 16, or one per
  tower) sets the carriage's hole, its pin 350 mm up at hole 1 and 132 mm higher for each. `at`
  [x, z] stands the middle of the towers' line there, since the body stays at the origin: back for a
  lifter a step ahead of them, to a side for one beside a tower. `hands` lists the hands that hold a
  handle (1 the left), and with `crossed` each takes the far tower's cable. Each
  tower has a carriage (prismatic, up the rail), a swivel (revolute about the vertical, 180°) and a
  stack (prismatic, the cable drawn out up to 2743 mm, the stack rising half as far, 2:1), and each
  hand's handle is a free body, hanging from the cable's end where no pose holds it. Contacts tie
  the hands to the handles, and `optional` ones to each carriage's fixed grip bar, which a free hand may use; the fit rule sets `position`.
  It departs from the ref: the stack's plates and the frame's tubes are the library's generic ones
  (twelve 26 cm plates, 76 mm tubes without the holes), the stack stands 500 mm behind the rail, the
  top wheels are 90 mm, and the 2210 mm is taken centre to centre. The handle is the
  stirrup. `attachment` hangs one thing on one tower's cable in its place: a `rope` or a `bar` in
  both hands, or a `cuff` on the `ankle` named (1 the left), and `tower` picks the cable (1 the
  left). A rope with one of `hands` is a single strand for that hand, and a `reversed` one runs through the
  fists the other way, its ends past the index fingers. `faces` turns each tower round where it stands, for a lifter facing the machine, and `turn`
  swings the whole machine that many degrees about the body, as if the lifter stood turned the other
  way. `position`
  takes one number for a two-arm lift, the same hole on both towers, and a pair only where the arms
  differ.
- `hip_machine` (#214, built, none of its two exercises posed yet, so no clip changes) is the one
  hip abduction and adduction machine: a 360 mm seat 520 mm up with a backrest leaning back 15°, a
  rear leg behind it, and a tall tower with the weight stack standing ahead of the posts, as the
  maker's photos show, on a floor beam 1.55 m long. Two arms turn on one vertical axle on the centre
  line under the seat, 140 mm behind its front edge. Each arm (`arm_l`, `arm_r`, revolute about the
  vertical, 0 to 50° from closed, positive opening) is an L out from the axle and 400 mm forward to a
  post with a 280 by 160 mm knee pad at 560 mm and a foot plate at 80 mm; the right arm rides 60 mm
  above the left on the shared axle. `pads` (`outer`, the default, on the outsides of the knees for
  abduction, or `inner`, between them for adduction) and `start` (degrees, snapped to the ratchet's
  10° steps; 0 for `outer`, 50 for `inner`, at least 10 for `inner` so the pads clear) set it up; the
  fit rule names `start`. It departs from its ref: the pads' faces are put 140 mm (`outer`) from the
  centre line from the mannequin's knee, which puts the posts about 450 mm apart rather than the
  ref's 250 (the photos show each post outside its pad, so 250 can't hold with an 80 mm pad); the
  joints stop at the ratchet's 50°, a 40 to 50° travel, the ref's 45° read from another make's video;
  the stack's travel isn't drawn. The studio shows it alone.
- `smith_machine` (#182, smith machine equipment, not a machine kind) stands where its ref puts it,
  whatever the pose, and none of its nine exercises is posed yet: two guide rods 1.3 m apart leaning
  7° with their tops toward the back, a front upright either side carrying a rod and a plate of 13
  hook notches 152 mm apart behind it, a rear upright either side wrapped in a pad with four plate
  horns through its outer face, on floor beams 1.24 m long. The bar is one body on a prismatic joint
  along the rods, its value the metres it has moved up them from 250 mm above the floor (0.04 to
  1.81 m); its carriages and their hook lugs are fixed on it, so no second joint is left behind by
  a scrub. Its options: `hook`, the notch the bar hangs on, numbered from the lowest (7 by default,
  at 1.35 m, one notch under a standing squat's bar), and `stops`, the notch the safety stops lie
  on (0), below which the bar can't go. Its contacts tie the hands and the shoulders (the bar on
  the upper back) to the bar. The bench and the front-foot-elevated lunge's platform are separate
  kits (`bench`, `platform`).
- `trap_bar` (#234, trap_bar equipment; `BARS` in `kit.mjs` maps its one exercise, none posed yet, so no
  clip changes) is a closed hexagonal trap bar from the ref's numbers, closed as the owner's second
  reference (a TB-1 page, a closed hex of 38 mm square tube) shows: one mitred ring of 38 mm square
  tube whose two corners are 934 mm apart on the sleeve axis, a front and a rear crossmember 510 mm
  wide, 652 mm across the frame, and two 406 mm sleeves to 1875 mm over all.
  The frame, the sleeves and the low handles' centres are one plane 225 mm up, so a 450 mm plate
  rests on the floor. Two 127 mm handles stand 635 mm apart, braced to the arms. Options:
  `handle`, `'low'` (225 mm) or `'high'` (327 mm, 102 mm above, on posts), and `plates`, the 450 mm
  plates a side (one). Nothing on it moves; a pose will carry the bar up with the hands. Contact:
  `grips` (the hands, at the handles' centres).
- `ez_bar` (#209, its own equipment kind; `BARS` in `kit.mjs` maps its 3 exercises, none posed yet,
  so no clip changes) is a short Olympic-sleeved curl bar with a zigzag shaft, along x, a free body
  the hands carry like `barbell`. It is 1384 mm long, the shaft dark 28.5 mm tube and 800 mm between
  the collars: a straight middle on the sleeve axis (120 mm corner to corner), then each side an
  inner and an outer grip segment (about 17 and 32 degrees, opposite ways) with rounded bends and a
  return to the axis at the collar, 80 mm from peak to trough in the horizontal plane, the outer bend
  forward (+z). Each sleeve is chrome, 50 mm across and 267 mm to load, behind a wider chrome flange. Nothing on it moves. Its option `grip` (`outer` by
  default, or `inner`) is the pair the pose's bar position is the middle of. Contacts, hands on the
  bar: `outer` (510 mm apart) and `inner` (250), on the segments' middles. Lying on the floor with no
  pose, it is shown alone in the studio (`npm run studio`, the `ez bar` row under kits).
- `safety_bar` (#219, safety_bar equipment; `BARS` in `kit.mjs` maps its two exercises, none posed yet,
  so no clip changes) is a cambered bar from the ref's numbers, unloaded: a 38 mm shaft 1310 mm
  between two camber bends that carry the sleeves 140 mm below its centre line, 2362 mm long over
  the sleeves, a yoke pad 330 by 100 mm round the shaft, and two handle arms from the yoke ends, each
  with a pad 270 mm long and a bare steel grip 175 mm long. Nothing on it moves and it fits every
  height. Options: `shaft`, the shaft's height (1.50 m), and `roll`, the degrees it is rolled forward
  about the shaft (0 as hung in a rack, about 25 as held). Contacts: `grips` (the hands) and `yoke`
  (the shoulders).
- `shrug_machine` (#223, the machine kind of `machine_shrug`, not yet posed, so no clip changes) stands
  where its ref puts it, whatever the pose: a rear cross beam and two side beams 1.02 m deep on the
  floor, a post at each end carrying the levers' axle 600 mm up, a back pad topping out at 1.24 m
  on a column up the middle, mitred into the beam to the pad, and a seat at 650 mm. The two levers are one body on a revolute joint
  about that axle, its value the angle up from level ahead of the line to the grips: 7.5° on the
  stop, the grips 700 mm up and 740 apart, 760 ahead of the pivot, to 26.3° at the top of a shrug,
  940 mm up. Each lever carries a handle post rising 270 mm with the grip pointing forward, and a
  plate horn under the post reaching out to 760 mm from the middle. The contact ties the hands to
  the grips. Its one option is `seat`, the seat's top (0.65 m, the ref's measured height). In the studio it stands alone, three-quarter from the front, and the joint's slider scrubs the
  levers through their limits.
- `power_rack` (#189, bodyweight and weighted bodyweight equipment, not a machine kind;
  `STATIONS` in `kit.mjs` maps its 16 exercises, all posed on it, #196) is a bolt-together
  power rack with a multi-grip pull-up bar and a pair of dip handles as options (`pullupBar`,
  `dipHandles`, both on by default). Its option `handles` names the pair of grips a pose holds, and
  the contacts tie the hands to that pair alone. It stands where its ref puts it and nothing on it
  moves, except that a pose on the dip handles turns it half round: the lifter stands between them
  with their back to the upright, as the close-grip dip's video shows, and the mannequin faces +z in
  every pose, so the rack stands behind them, the handles either side of x = 0. It has four hollow 3 in uprights 108 in tall with 5/8 in holes through every face (1 in apart
  through the bench zone, 2 in elsewhere), the front pair 43 in apart inside and the rear 24 in behind
  them, each on a 7 in plate; a side crossmember near the top and one near the floor on each side, and
  a back one, each hollow and holed with an end plate and two bolts, all on holes; a pin pipe safety each
  side. With no `pullupBar` it has the rack's supplied skinny and fat bar side to side; with it, a front
  crossmember at the 91 in hole carries a plate, outer prongs 813 mm apart (2 in) and inner ones 406 mm
  (1.25 in), a V of about 45° to the inner ones, and a cross tube 230 mm out joining each inner prong to
  its outer one and out past it, its ends curving back toward the plate. `dipHandles` hang on the +x
  front upright from a bracket at the 51 in hole: a stem, a crosshead and a handle either side, 440 mm
  long, 451 mm apart at the crosshead and 630 at the tips, at 1.305 m. The cross tube's hang line is the
  origin. Contacts, all hands on the frame, a pair of points each: `wide` (960 mm apart, on the cross
  tube outside the outer prongs), `standard` (610, on it between the prongs), `narrow` (544, on it hard by the inner prong, the
  nearest an overhand grip gets), `close` (280, on the V's arms, an underhand grip's: they run too far
  off square for an overhand hand), `neutral` (406, on the inner prongs, 80 mm out along them), and `dip` and `dipClose` (the
  handles' middle, 540 mm apart, and near the crosshead, 487 mm, as near as the hips clear it). It has no fit rule. It departs from the ref: the rack is a
  9 ft one so the bar sits at a hole near 2.30 m, the back crossmember isn't in the photos, the ref's
  close grip (400 mm on a cross tube between the inner prongs) is on the V because the photos show no
  tube there, the cross tube is 1150 mm, between the 1060 scaled from a photo and the page's 1194, so a
  hand on the wide grip stays on its straight run, and the end plates, safeties and bracket are read
  from photos.
- `dip_belt` (#196, weighted bodyweight equipment) is a belt round the hips and a 25 lb iron plate on
  its chain. A free body the hips carry, on no joint: the plate hangs plumb in front of the thighs at
  about mid-thigh however the body leans, and the chain runs to it from the belt's sides.
- `pec_deck` (#208, #241, a machine kind: `MACHINES` in `kit.mjs` maps its 3 exercises, all posed) is a
  seat and a 300 mm back pad leaning back 7° on one post, in front of a tower narrow across and deep
  fore and aft, the stack's broad face looking to the side, on a spine with a rear beam out each side. An
  arch from the tower's back carries an open overhead unit 2.0 m up, built to the ref's lifter and not its
  published 1.8 m: two flat plates on two axles, 0.2 m either side of the centre line, over the shoulders
  of a back on the pad, and a rail back to the tower. A lever 0.55 m long swings from each axle 1.8 m up,
  a pin across its end and a drop arm hanging from it. A vertical grip hangs like a cane 100 mm off the
  drop arm, square to the lever, its middle at 1.1 m, and a short level grip stands
  on the other side. Each arm is its own body on a revolute joint (`left`, `right`), its value how far it
  has swept from its rest. The stack hangs on `left`, as the arms move together. The pec fly rests a
  little behind the shoulder line and sweeps 0 to 110°, the hands meeting at 104°. With `facing: 'pad'`
  the kit stands turned round the mannequin, which always faces +z: the rear delt fly rests 45° behind
  that line, the levers past the tower's sides, and sweeps 0 to 60°, the arms in line with the shoulders
  at 57°. `hips` sets how far along the seat that lifter sits, and `one` keeps the right arm at rest for
  a one-arm lift. Contacts: the hands on `leftGrip` and `rightGrip`, or `leftShort` and `rightShort`
  facing the pad, each naming its `hand`, `backPad` on the frame, the back or the chest, and `seatTop`, the thighs. Its one fit
  rule sets `seat`, `[y, z]`, so the shoulders are level with the grips, 0.44 to 0.62 m up. It departs
  from the ref: it has one start for each exercise (the ref's knob has five, 15° apart, chosen), the
  drop arms are rigid on their pins, and the seat range and lever radius are chosen numbers.
- `captains_chair` (#237, bodyweight equipment; posed by `roman_chair_crunch`, a knee raise on the `chair`
  motion) is a captain's chair from `refs/captains_chair/ref.md`, its parts placed from the photos
  written there. Nothing on it moves: two floor rails 1054 mm long, 640 apart, with a foot brace; in each
  side plane an X of 65 mm mitered tubes, a straight one from the rail's front half to a rear post and a bent
  one from its rear half, forward and upright under the grip's hub; a 65 mm top rail under each arm pad from
  the post to the hub; a back pad 290 by 550 mm, reclined 10°, bolted to one upright standing on a cross tube
  between the rear posts; two flat arm pads (250 by 120 by 80 mm, 560 apart, tops at 1.38 m); a knee raise
  grip 230 mm long rising from a hub at each pad's front end, leaning back 15° and inward, with a dip handle
  forward of the hub (the dips are `power_rack`'s); a foot peg each side at 280 mm. The elbows' place, on the
  arm pads' rear halves, is the origin, the lifter facing +z. Contacts, on the frame: `armPads` (forearms,
  a pair), `grips` (hands, a pair, on the grips at 1.43 m) and `backPad` (back). It has no fit rule and no
  joints, so no seat, no floor contact and no pivot to hold: the feet hang 0.24 m clear of the floor. It
  stands in the studio under its exercise. Heights are rough (±50 mm, one three-quarter render in the ref)
  and the arm pads are flat.
- `hyperextension_bench` (#220, weighted bodyweight equipment; `STATIONS` in `kit.mjs` maps its one
  exercise, `45_hyperextension`, not posed yet, so no clip changes) is a rigid 45° back extension
  bench. The lifter lies face down and faces -z; the origin is on the floor under the hip pads' top
  end, which is 825 mm high, with the feet toward +z. A 1248 mm rail of 75 mm tube lies on two cross
  feet 666 mm across; a column leans up from it parallel to the slope to a bracket under two hip pads
  (210 mm wide, 55 mm apart, 330 mm long, 90 mm thick, sloping down at 45° toward the feet), with a
  strut under it and a pair of grab handles at 690 mm reaching 50 mm past the back end. A foot plate
  (470 by 330 mm) sits square to the body line, its surface 900 mm down the slope from the pads' top
  end on a post from the rail, and two heel rollers (190 mm long, 130 across) on an axle through a
  post from the plate's heel end, 195 mm up the body line from the plate and 210 mm off it. Contacts,
  on the frame: `thighPad` (thighs, each pad's middle), `footPlate` (feet) and `heelRollers`
  (shins, each roller's centre). It has no fit rule and no options. It departs from the ref: the body
  line and pad height are chosen (the real bench telescopes, 844-1119 mm), so the plate stands about
  180 mm nearer the pads than in the drawing, which is at 40°; and the angle and height collars, the
  heel lock, the holes down the rail and the pads' rolled top end are left out. The studio shows it
  alone: `npm run studio`, then the `hyperextension bench` row under Kits.
- `dip_machine` (#226, a machine kind; `seated_dip_machine`, its one exercise, is posed on it, in the
  `dip-machine` batch; serve it with `npm run studio`, `f`, `dip_machine`, and `j` to its kit row)
  is laid out from the ref's photos, read again for where each part sits against the seat
  (`refs/dip_machine/ref.md`, Where the parts sit). Two joints, both about x: `arms` (0 to 40°, the
  radians the handles have gone down from the start; the pose drives it, the top of the rep at 40°,
  the arms straight down) and `carriage` (0 to 12°, the radians the seat, back pad and footplates have
  rocked back about a pivot under the seat; the pose drives it at 0.3 of the arms'). The grip bars run
  up and forward 40° at the start and level at the finish, as the ref's do: square enough to the forearm
  through the rep for a straight wrist, and with the elbows back and under the shoulders (a bar at 15°
  put them 21 cm out). The arms hang on a pivot 880 mm up on a leaning rear upright, each
  one tube out to a collar and a 0.28 m bar, the grips 440 mm apart, 790 up at the start and 500 at the finish. The
  seat top is 480 mm, the back pad 0.72 m tall from the seat's rear edge, the footplates 180 mm up at
  the start. The stack (twelve plates, 0.3 m a radian of the arms) stands on the user's right (-x) in a
  shroud open toward the seat, its plates facing it. Contacts: `handles` (hands, a pair, on the arms),
  `backPad` (back) and `footplates` (feet, a pair), the last two on the carriage. It has no fit rule:
  the seat is fixed at the measured height. The frame stands where the ref puts it and the parts are
  laid by a rigid move at each joint's value, so a scrub never leaves one behind. It departs from the
  ref where the mannequin's body asks: the arms are 0.5 m to the grip, not 0.72, so a hand can reach
  the grip at the finish with the wrists straight; the footplates 0.42 m ahead of the seat's front,
  not 0.5; the carriage rocks 12°, not 20°, since the photo's pad leans about 15° at the finish; and
  the tower, tubes, seat and base are simplified from the ref's shroud and sloped frame.
- `glute_ham_raise` (#230, a machine kind: `MACHINES` in `kit.mjs`; posed, one clip) is a glute-ham developer from its ref,
  standing with the lifter facing +z and the origin on the floor under the pads' rear edge. Nothing on it moves, so it
  is a **static** machine: its recipe has a `linkage` and no `rep`, and `fitted()` solves its options for the mannequin
  once and gives the motion one place (the knee, the ankle, the sole, the handles). The frame is 2 by 3 in steel: two floor
  rails 45 in apart outside and 1740 mm long with rubber caps, a crossmember under each upright, a front upright up to
  the pad bracket (the manual's 29 in part) with a handle each side under it at 820 mm, out past both ends, a rear upright
  19 in tall, and a spine joining them. Two half-round pads (230 wide with 40 between, 500 long, 230 tall, crest 1092 mm up)
  each stand in a row of five slices, a box each, which is how the checks follow their curve. The ankle assembly's sleeve
  slides on the spine, pinned: a mast between two pairs of foam rollers (216 by 127 mm, 203 apart, a 76 mm gap for the
  ankles, their centres 150 mm ahead of the plate's middle) and the 345 mm foot plate, its top 1245 mm up. The option
  `slide` (0 to 12) counts 1 in steps back from the most forward hole, the plate's face 300 mm behind the pads' rear edge
  (chosen, the ref gives none), 1 by default. Its fit rule sets `slide` from the mannequin's shin and ankle height: the knee
  joint 50 mm above the crest and 50 mm behind it, the ankle in the roller gap, the sole on the plate. Contacts, all on the
  frame, a pair of points each: `thighPad` (the `knees` landmark, on the crest), `ankleRollers`, `footPlate`, and
  `handles` (optional: the hands are free at the top). The `glute_ham_raise` motion lowers the body about the knees, the
  thighs and the spine one line (`lean`, degrees from upright, 0 to 78: the mannequin's knee straightens no further than
  its legs' cap allows, so the body ends about 12° above level, the thighs on the crest), the hands hanging until the body
  is nearly level, then resting on the handles' ends. `npm run movement -- glute_ham_raise` judges its clip. It departs
  from the ref: the rails, crossmembers, braces and bracket are read from the photos, with no brand or mark on them, the
  rollers sit further from the plate than the ref's first reading (the foot needs the room), and the knee's height over
  the crest is chosen. Nothing pivots on it, so it uses no `pivotJoint`; its tubes meet square, not bent, so no `mitered`.
- The leg extension and the leg curls are laid out as the Watson line's (#149): a selectorised tower
  beside the user, its stack facing them and its cable over two wheels under the cap, down its end
  to wheels on the floor rail and up to a cam on the lever at the knees. The lever, with its
  adjuster plate and pop-pin and a long roller with end caps, turns on a post on the near side, the
  camera's. The pads follow the body in them: the
  seats rise with the thighs, the back pads run from the seat to the shoulder blades and slide on a
  bar set by a pop-pin, and the seated curl's thigh pad lies along the thighs and carries upright
  grips. The extension's handles run forward beside the hips. The lying curl's bed is humped at the
  hips, its thigh pad dropping more steeply to the knees than its chest pad to the shoulders, on
  legs leaning toward the head, with handles under its head end and a plate stored low on the post.
  `knee_lever`'s `grip.across` lays a handle that isn't along the body across the palm.
- The leg press's sled and the chest press's levers and stack move with the rep; their back pads sit
  behind the body's back at the top of the rep (`pad`, metres behind the spine).

### Recipes

Every kit is a recipe (ADR 0007): `recipes/<kit>.mjs`, over the part library in `parts.mjs`
(tubing with its caps and base plates, pads, seats, rollers, pulleys in housings, cables, stacks
with guide rods and a pin, pop-pins, bars and handles, each a function of its dimensions and tone).
A pivot is `pivotJoint(M, at, axis)`: a bearing housing on the moving arm round an axle the frame holds
(#212, used by `lateral_raise_machine` first). Bent tubing is `mitered(points, w, material, { ends })`: one
square length swept along the points, each bend cut on its bisector, not two lapped lengths.
`recipe.mjs` is the format. A recipe declares, as data:

- `bodies`: each moving body and its joint, `revolute` (a lever turning about its pivot, with its
  `settings` where holes set it) or
  `prismatic` (a sled, a carriage, a stack sliding along its rails), with its `limits`; or `free`,
  on no joint, for what the mannequin carries (a barbell, a cable's attachment, a dip belt's plate). A joint's value is how far
  it has moved from where it is measured: its rest where it has a stack, or a lever's angle up from
  level ahead. A lever's limits are where the body stops, not only the machine: a leg extension's
  where the knee is straight, a curl's at the checks' deepest bend, a lever in the hands where the
  arms are straight, the calf raise's where the heels are as high as they rise (45° from flat).
- `contacts`: where a body (or the `frame`) ties to the mannequin: a named point and its landmark,
  `hands`, `feet`, `shins`, `thighs`, `knees` (a thigh's lower end and the shin's top, for a pad the knees kneel on), `shoulders`, `chest`, `back` or `upperArms`. An `optional` one
  on the hands is a grip a free hand may use, so no hand is held to it.
- `stacks`: each weight stack, the joint whose travel raises it and the `ratio` (metres a radian, or
  a metre). A working stack is held 6 cm off its rest (`HELD`), and rises `ratio` times its joint's
  value from there; a cable machine's stack rides its own joint, the cable drawn out, one for one.
- `fit`: the fit rules, each naming the option it `sets` (the seat, a pad) and how to `solve` it from
  the mannequin's measurements. An entry that sets the option overrides its rule, and a rule with no
  `solve` is only declared.
- `rep` (a machine's): the joint the rep drives, and which limit (`from`) is the top of the rep.
- `linkage(options, L)` (a machine's): the machine with no pose, the `hip` its seat or its pad
  puts for a mannequin of measurements `L`, and `at(value)`, where everything the body meets is with
  its joint at a value.

Its `build` makes the parts in the order the clip names them and says which nodes move with which
body. Its `drive` places the parts from the pose frame and returns each joint's value and where it
is, and each contact's point. A kit's `scrub(body, value)` moves a
joint's body rigidly to a value within its limits, its stack with it. `recipes.test.mjs` checks
every posed exercise against its recipes: the joints stay within their limits through the rep, a
scrub from the bottom of the rep to the top's value puts each body where the top has it, and each
contact but an `optional` one meets its landmark.

### Solving a machine

An exercise in a recipe with a `linkage` is solved from it (`fit.mjs`, ADR 0007); a free weight,
or a cable's attachment, stays body-first. First the entry is fitted: each fit rule sets its option
for the mannequin, unless the entry sets it, and the seat puts `body.hip`. Then the rep drives the
joint, eased from the top's value to the bottom's. Those are the limits by default; an entry gives its
own range under the joint's name, in degrees or metres (`top: { levers: 75.43 }`). On each frame the
motion reads `s.linkage`, what the recipe has at that value, and hangs the body from it: the hands on
the handles, the feet on the plate, the shin against the roller. Everything the machine decides stays
out of the entry, so `body.hip`, a pivot, a handle's path and the handles' place are the recipe's. A
rep's range, where the feet go, where the hands go along a bar, and how the trunk leans are the
entry's. The frame stands where the recipe has it whatever the entry says. The pads an adjustment
brings to the body are the exception: a back pad or a chest pad follows the trunk, as it does on the
gym floor, and the fit rule sets how far off it stands.

`buildStage` returns what the fit solved as `fit`, by the path an entry would set it at, and the
studio offers a slider for each. A slider saved writes the override into `poses.mjs`; with none, the
rule solves it again.

Every kit is built at one level of detail (SPEC, #127): rounded tubing on base plates, soft pads,
a stack of twelve plates on guide rods with a selector pin, pulleys in housings with the cable over
them, and shaped bars and handles with grips. Nothing smaller shows at phone size, so there are no
bolts or seams. Each shape is made once per size and shared by every part of that size (`shape`
in `parts.mjs`), with positions and normals only, so a kit's file stays small.

Materials are named for the phone, which colours them from the theme: `body` and `joint` on the
mannequin, the kits' five tones, and `shadow`. The tones are `frame` (frames and uprights), `vinyl`
(pads, rollers and grips), `plate` (a stack's plates and every other weight), `metal` (rods, bars,
cables and pins) and `pulley` (pulleys and their housings). Each tone's roughness ships in the clip;
the colours in it are the light theme's, which the phone replaces.

## Checks

`checks.mjs` checks every frame of a pose before it bakes, and a pose that fails any check doesn't
bake (nor does anything else in that bake):

- **Hands:** each hand holds its grip, within 1.5 cm.
- **Feet:** each foot stands where the pose puts it, flat on the floor or on its plate, within 2 cm; a
  foot on nothing, lifted or tucked behind a knee, only has to be where the pose puts it.
  A body held up by its hands (`hang`, `support`) keeps its feet at least 2 cm off the floor.
- **Equipment:** no limb is more than 2 cm inside a solid part of the kit (a pad gives that much),
  and none passes into a thin part like a bar at all. Only the hands close round what they hold.
  The floor counts as solid: nothing goes more than 1.5 cm into it. Every cable stays 2 cm clear of
  the head and the neck.
- **Joints:** knees and elbows bend no more than 155°, a knee turns no more than 60° from the hips'
  front, a hip flexes no more than 140° or extends past 30°, the head turns no more than 60° from
  the trunk, the ankle stays between 55° and 160° from shin to foot, and no forearm or hand goes more
  than 1 cm into the head or the neck.
- **Contact:** every finger and the thumb of a hand on a bar or a handle meet its surface within
  2 mm, and no part of the hand is more than 2 mm inside it; the palm within 3 mm (Grips, above).
- **Linkage:** each joint stays within its limits, each point a joint carries stays on the path the
  joint gives it (a handle on its arc) within 1 mm, each hand within 1.5 cm of the handle it holds,
  and each pad, roller and plate within 2 cm of the limb it bears on.
- **Trace:** every traced angle is within 10° of its trace. Contacts come first (the bar over the
  feet, the hands on the bar), so a pose can drift from its trace; past 10° it fails.

## Tracing a pose

`npm run trace -- <pose> <footage> <from>-<to>` runs MediaPipe Pose Landmarker on side-on footage of
the lift, on the CPU, over the time range (seconds, or `m:ss`). It measures the side-on angles from
the side nearer the camera, smooths them (Savitzky–Golay, 0.25 s), finds the reps along the pose's
own way from its `top` to its `bottom`, takes the middle one that reaches the bottom, and writes its
lowering, sampled once per baked frame, to `traces/<pose>.json`. Add `trace: '<pose>'` to the entry
to use them.

- **Only angles are kept.** Feet, hands, equipment contacts and tempo stay authored in the entry,
  and IK holds the contacts: a trace's feet drift 8–17 cm within a rep. The footage, its frames and
  MediaPipe's landmarks are never written into the repo (`.gitignore` keeps videos, images and the
  model out), so baking, and CI, never trace.
- **`--angles`** picks which angles to keep (`shin,thigh,trunk,upperArm,forearm`, all by default).
  Keep only what the footage shows well: a landmark MediaPipe barely sees is filled from the frames
  either side, but a knee passing behind the plates for half a second still throws the shin off.
- **Use it for side-on lifts:** squat, deadlift, RDL, lunge, hip thrust, leg press, calf raise,
  pressdown, curl, row. Lifts whose arms move out of the camera's plane (raises, flyes, grip width,
  elbow flare), front-only footage, machines that hide the body and lying lifts stay hand-authored.
- **The best source** is the owner filming the lift side-on, with one camera and no cuts. The
  library's technique videos may be traced too; only about a third have a usable side-on stretch.

It needs Python with `mediapipe` (0.10.21 traced the first batch) and `opencv-python`, and the
heavy model, `pose_landmarker_heavy.task`, from MediaPipe's model page:

```sh
MANNEQUIN_PYTHON=~/mediapipe/.venv/bin/python \
MANNEQUIN_POSE_MODEL=~/mediapipe/pose_landmarker_heavy.task \
npm run trace -- barbell_squat ~/footage/squat.mp4 0-20 --angles shin,thigh,trunk
```

`MANNEQUIN_PYTHON` defaults to `python3`, and `MANNEQUIN_POSE_MODEL` to
`trace/pose_landmarker_heavy.task` here, which git ignores.

The first batch's traces:

| Pose | Footage | Angles |
| --- | --- | --- |
| `barbell_squat` | the library's technique video, UGwmUJkTIx8, 0–20 s | shin, thigh, trunk (the arms hold the bar out of sight) |
| `triceps_pressdown` | the library's technique video, B5S2mbg0g5c, 0–11 s | all |

## Rendering a pose

`node render.mjs <id> [out-dir]` writes `<id>-side.png` and `<id>-front.png`, the top of the rep
beside its bottom, without a GPU: a flat-shaded software rasteriser, orthographic, in the clay
colours. A lying lift is seen from the head end, and equipment standing well in front of the body
(a cable tower's column) is cut away. It shows the pose and its equipment, not the phone's lighting.
Use it for a pull request's screenshots. `node render.mjs --clip <clip> [out-dir] [--at <commit>]`
renders a baked clip instead, at the top, the middle and the bottom of its rep, as that commit baked
it.

## The studio

`npm run studio` serves the tool at `http://127.0.0.1:5190/studio/` (Vite, pinned in
`package-lock.json`, with the plugin in `studio/plugin.mjs`). It is for the owner's machine and
listens on 127.0.0.1 only.

- **Loading:** the list and the item on stage come first. Opening an item starts the checks, that
  item's first, then every other pose's one at a time in the background, and opening another item
  or saving moves that pose to the front. The header's message line says `Loading…`, then
  `Checking 12 of 38`, the rows keep their `checking` tag until theirs are done, and the list fills
  in every couple of seconds. A plain message has the line for four seconds, then the count takes it
  back; an error, or a save under way, keeps it. `studio/checking.mjs` is the queue.
- **The sidebar** lists every exercise in the library, posed or not, grouped by equipment, then
  every kit. Each row shows its status: `no pose`, `failing` (a check fails), `notes` (an open note
  in `review/notes.md`), `ok` (OK'd for the clip it has now), `phone-checked`, `changed` (OK'd for
  a clip since re-baked) or `waiting`; a kit shows `notes`, `kit` or `unused`. `f` filters it by
  name, id, equipment or status (`no pose`, `notes`).
- **The stage** shows one item. An exercise is posed live with the tool's own modules, looping at
  its tempo or held at the top or the bottom, with drag handles (`handles.mjs`) when held, and the
  checks re-run on what it shows. `c` shows the baked clip instead. A kit is shown alone, set up as
  the exercise that gives it the most joints sets it up (an adjustable bench, a twin column), the
  first by id of those. A kit no pose uses yet is shown as its recipe stands alone (`standsAlone`,
  `shownAlone` in `kit.mjs`), its joints at rest, where its recipe can: the smith machine, the lateral raise machine, the EZ bar, the hip machine, the safety bar, the hyperextension bench, the trap bar and the shrug machine. A change to a recipe, a part or `poses.mjs` on disk reloads the page on the
  same item, its unsaved numbers kept.
- **A kit's joints** (`studio/joints.mjs`) are its numbers: a row each, with its type and limits.
  `h` `l` move the selected one a degree (a revolute joint) or a centimetre (a prismatic one), `H`
  `L` a tenth of that, `=` types it in degrees or millimetres, and `^` `$` send it to its lower or
  upper limit; no step goes past them. A joint with `settings` lands on them however it is moved,
  and a step is a hole. A joint scrubbed is held where it is set, its stack with it,
  while the rep its exercise poses plays round it, and `R` gives it back to the rep. A scrub is a
  view, kept nowhere: a recipe's dimensions are edited as text.
- **The numbers** are the exercise's pose entry, one row each (`tune.mjs` says which, and the levers
  its motion offers that the entry leaves at the default), lengths shown in millimetres. `h` and `l`
  step the selected one a degree, a millimetre or a hundredth of a ratio, `H` and `L` a tenth of
  that, and `=` types a value (`412.5`, or `+2` and `+-2` to move it). A carriage's `position` lands
  on a hole of the 16, however it is set, and a resting hand's `grip.rest` reaches a metre either
  way. A drag of a handle sets the
  same numbers (`top.bar` and `grip.width`, `grip.elbows`, `feet.z`, `body.hip`, `trunk`,
  `body.arch`), a point whole.
- **The technique video** of an exercise with a video link in `exercises.json` is beside the
  stage, opened at the link's timestamp. The first time it is opened the studio fetches it with
  `yt-dlp` (the picture only, 720p at most, played muted) into a cache outside the repo,
  `~/.cache/hyperlog/videos` (under `$XDG_CACHE_HOME` when it is set), and reuses that
  copy, so a video shared by several exercises is fetched once. Without a link, or offline, the
  panel says so and the rest of the studio works as before. `[`, `]` and `}` mark the loop: A at
  the top of a rep, B at its bottom and C back at the top, saved in `review/loops.json`; the video
  reopens on them. Once two are marked the scrub bar zooms to the loop, a quarter of it either side
  (0.5 s at least), and `z` shows the whole video instead. With sync on (`gv`) and A, B and C
  marked in that order, the loop is the rep: the lowering is A to B and the lift B to C, both
  played forward. A step or a scrub of the video moves the stage, the stage's own play, holds and
  steps move the video, and both play at the clip's tempo, slowed to the video's speed.
- **Bones** (#164): `[` `]` walk to a bone's parent and its first child, `{` `}` to its siblings,
  `m` to the mirror bone, and `/` finds one by name; the pelvis is the first. `x` `y` `z` pick the
  axis it turns about, drawn strong on the stage with the other two faint. With the stage focused
  and the rep held at the top or the bottom, `h` `l` `H` `L` `=` `R` turn the bone as they step a
  number, into that end's `offsets`; every frame is solved again and the checks re-run. The offset
  is a number in the numbers panel too, under Bones. `Esc` lets the bone go.
- **The camera** is free in the studio (`view.mjs`): it orbits on both axes, over the top and under
  the floor, round the selected bone or the body's centre, and pans, dollies and rolls, from the
  keys or the mouse (drag, right-drag, wheel). `gh` `gl` close up on the left and the right hand
  where it holds and follow it through the rep, looking from the way (in front and outside, in
  front, outside, above, below, or behind and outside) that sees most of its fingertips with
  nothing of the body or the kit shown in between, and mark in red where the hand misses its bar
  past the contact check's 2 mm on the frame shown, `gc` goes back to the clip's framing, and `gp`
  hides the plates (every part in the plate tone) to see the hands on the bar; that is a view
  setting only. `:frame` saves the view as the clip's `camera` (an adjustable bench's
  `bench.cameras` at the angle shown) and re-bakes: the target, how much it shows, the turn and the
  tilt, the view brought the right way up if it is over the top; the phone has no roll.
- **Saving** (`:w`) writes the tuned numbers into `poses.mjs` as text (`patch.mjs`, so the comments
  stay) and re-bakes that exercise's clips in a process of its own, with those of any pose that
  takes it by `like:`, then shows the new clip. The other clips aren't baked: they are read back and
  shared again with the new ones (`bakeOnly`), so every file comes out as a full bake writes it, and
  only a file that differs is written. A kit file changes, and the clips that read it with it, only
  if the save changed the kit. A save bakes every clip when the clips are behind
  (`studio/behind.mjs`): when anything a bake reads, the code, a recipe, a trace, the mannequin, the
  library or `poses.mjs`, has changed since the last bake, by the files' dates. So does a save with a
  pose not baked yet. Only the saved poses' checks run again; a change to the tool's code runs them
  all. A pose that fails its checks doesn't bake, and then `poses.mjs` goes back to what it was and
  the studio names the pose that failed. Read the diff after: a comment may now describe the old
  number.

The page imports the tool's modules through Vite, with node's `fs`, `url` and `path` stubbed out:
the server (`ssrLoadModule`, fresh on every change) prepares each entry, so the page never reads a
file. Nothing the Vite config imports may reach `poses.mjs`, `kit.mjs` or the recipes: Vite
restarts its server when one of those changes.

### Keys

Everything can be done from the keyboard, vim style, and the mouse is optional. The keymap is
`studio/keys.json`, in vim's notation (`gg`, `<C-r>`); `studio/keys.mjs` reads it, every control
shows its key beside it, and `?` lists every one. A count typed first repeats a command that takes
one (`5l`, `3j`). A new key goes into `keys.json`, under its group, with what it does in words.

| Key | Does |
| --- | --- |
| `j` `k`, `gg` `G` | Next / previous item in the focused list (sidebar, numbers); first / last |
| `Tab` | Cycle focus: sidebar → stage → video → numbers |
| `/` then `n` `N` | Find an exercise, a kit or a number; next / previous match |
| `f` | Filter the sidebar |
| `t` / `b`, `Space`, `,` `.` | Hold at the top / the bottom; play or pause; step a frame |
| `c` | The baked clip, or the live pose |
| `h` `l`, `H` `L`, `=`, `R` | Step the selected number, a tenth of a step, type a value, reset it |
| `^` `$` | A kit's selected joint to its lower / upper limit (`h` `l` `H` `L` `=` `R` move it, on a kit) |
| `<` `>`, `p` | Bench angle down / up; tune the number per angle |
| `gf` `gs` `gt`, `←` `→` | Front, side and top cameras; turn 10° |
| `[` `]`, `{` `}`, `m` | The selected bone's parent, child; previous, next sibling; the mirror bone |
| `x` `y` `z` | The axis the bone turns about (`h` `l` `H` `L` `=` `R` turn it, on the stage) |
| `gh` `gl`, `gc` | Close up on the left, the right hand; back to the clip's framing |
| `gp` | Hide the plates, or show them |
| `Alt+h` `Alt+l`, `Alt+j` `Alt+k` | Orbit left, right; down, up: 5°, or a count's degrees (`15 Alt+l`) |
| `Alt+H` `Alt+J` `Alt+K` `Alt+L` | Pan left, down, up, right, a tenth of what the view shows |
| `Alt+i` `Alt+o`, `Alt+,` `Alt+.` | Dolly in, out; roll left, right |
| `:frame` | Save the view as the clip's framing, and re-bake |
| `u` / `Ctrl+r` | Undo / redo |
| `:w` | Save and re-bake |
| `o` / `a` | OK the exercise (again takes it back) / add a note |

With the video focused, its own keys take over (a group in `keys.json` with `"focus": "video"`), and
`[` `]` `}` `z` `h` `l` `H` `L` are the video's there, not the bones':

| Key | Does |
| --- | --- |
| `Space` | Play / pause (the rep and the video together when synced) |
| `h` `l` | One frame back / forward; a count multiplies |
| `H` `L` | 1 s back / forward |
| `<` `>` | Speed down / up: 0.25×, 0.5×, 1× |
| `[` `]` `}` | Mark A (the top of the rep), B (the bottom), C (back at the top) at this frame |
| `z` | Zoom the scrub bar to the loop, or show the whole video |
| `gv` | Sync with the rep, or not |

## Reviewing a batch

The owner's OK and notes are files in the repo, written by the studio:

- **An OK** (`o`) goes into `review/<batch>.json` for the latest batch in `BATCHES` that lists the
  exercise, as `{ "<id>": { "ok": true, "clip": "<fingerprint>", "at": "<time>" } }`, sorted by id.
  `clip` is the fingerprint of the clip it was given on: what the clip shows, with the parts it
  reads from the files it shares, so a kit file that grows for another exercise leaves the OK in
  place (`clipContent` in `glb.mjs`). An OK needs the exercise saved first, and an exercise in no
  batch can't be OK'd until it is added to one. Older verdicts also carry the review page's `note`
  and `tuned`, which nothing reads any more.
- **A note** (`a`) goes into `review/notes.md`, under a heading for the exercise (`## barbell_row`)
  or the kit (`## kit:cable_tower`), as a bullet with the day and, for a posed exercise, the clip
  it was left on. Read it, fix what it asks, and delete the bullet.
- **A loop** (`[`, `]` and `}` on the technique video) goes into `review/loops.json` as
  `{ "<id>": { "a": <s>, "b": <s>, "c": <s> } }`, the times in the video of the rep's top, its
  bottom and its top again, each null until marked. A loop saved before C has no `c`, and loads.
- **A phone check**: `npm run review -- phone <batch>` puts `phone`, the fingerprint of each
  exercise's clip now, into the batch's file, once the owner has swiped it in the debug gallery.

`npm run review -- status <batch>` lists each exercise as `ok`, `phone-checked`, `notes` (an open
note, and no OK for the clip it has now), `changed` (OK'd on a clip since re-baked) or `waiting`,
with its open notes, and exits non-zero until the batch is done: every exercise OK for the clip it
has now.

## Reviewing a movement

The checks prove contact and geometry and know nothing of the movement. A **movement spec** says
what a lift should do, and a **movement reviewer**, an agent that didn't pose it, checks a clip
against it before the owner looks (`docs/agents/movement-reviewer.md`). A clear failure goes back to
the worker, a doubt goes to the owner, and a clean lift needs only a sample.

`specs/<id>.json` holds a lift's `sources`, the video and notes it was written from, and its
`lines`. Each line has `says`, the movement in words, and one kind:

| Kind | Example | Fails when |
| --- | --- | --- |
| `moves` | `{ "moves": "elbow_*", "top": [160, 180], "bottom": [30, 70] }` | the first or the last frame is out of its range; with `"ends": [[160, 180], [30, 70]]` in their place, the two ends fit the ranges in neither order |
| `still` | `{ "still": "pelvis.at", "within": 0.01 }` | a point travels further from its first place, in metres, or a number further from its first value |
| `path` | `{ "path": "hand_*.at", "within": 0.05 }` | a point strays further off the straight line between its ends |
| `never` | `{ "never": "dumbbell_*.tilt", "past": 20 }` | a number passes the limit, either way; with `"perFrame": true`, its change between two frames does |
| `reaches` | `{ "reaches": "wrist_*", "least": 10 }` | a number never gets that far |
| `travels` | `{ "travels": "ankle_l.at", "least": 0.3 }` | a point never gets that far from its first place, in metres |
| `even` | `{ "even": "roll_*", "within": 10 }` | a number strays further from the straight run between its ends: a rock or a twist mid-rep |
| `place` | `{ "place": "hand_*.at", "from": "shoulder_*.at", "axis": "z", "at": "top", "range": [-0.06, 0.06] }` | a point stands out of its range from another, in metres along x, y or z, at the `top`, the `bottom` or `either` |
| `look` | `{ "look": "forearms", "at": ["bottom"], "view": "front" }` | never here: it waits for the reviewer's eyes |

`*` stands for each side, and the worse one counts. On a one-sided lift `"works": "ankle"` or
`"hand"` finds the side that point travels further on, and `{work}` and `{other}` in a line stand
for it and the other. The top is the clip's first frame and the bottom its last, as the pose calls
them: a pulldown's top is the arm long, and a row's the arm bent. Whatever a spec can say exactly
is a line of an exact kind and not a `look`, because `movement.test.mjs` holds every clip to those
lines. A lift whose clip isn't baked yet is skipped. `"open"` on a line marks a known failure and
says what waits on whom. It reads `OPEN`, goes to the owner and not back to the worker, and doesn't
fail the command, and the test fails once that line passes, so the mark can't outlive the fault.

**A cold spec** is written before its lift is posed, by someone who hasn't seen a pose, to test the
review itself (#197). It says `"cold": "<why>"`, so `movement.test.mjs` skips it and CI can't show
the worker what it says; a test still runs its lines on another clip, to catch a mistyped number.
It can't know which end the worker calls the top or which side works, so it uses `ends`, `either`
and `works`. Don't read one while posing its lift, and don't tune a pose or a cold spec to the
other. Take the mark off once the owner has compared the verdicts.

`npm run numbers -- <clip>` prints every frame's numbers from a baked clip, any clip: a name such as
`db_incline_press_45`, or an exercise's id. `--at <commit>` reads it as that commit baked it, so an
old version needs no checkout, `--only <name,name>` keeps those numbers, and `--json` prints JSON.
The numbers:

- **Angles:** `elbow_l`, `knee_l` and `hip_l` read 180° straight; `shoulder_l` is the upper arm off
  the trunk, 0° hanging; `wrist_l` is the bend off the forearm's line; `trunk` is the lean off
  straight up.
- **`roll_l`:** the hand's roll about the forearm, summed from the top.
- **Points, in metres:** `pelvis.at`, `head.at`, and `shoulder_l.at`, `elbow_l.at`, `hand_l.at`
  (the wrist), `knuckles_l.at` and `ankle_l.at` on each side.
- **Each kit part that moves and keeps its size:** `dumbbell_l.at`, `.tilt` (its length off level,
  positive with the end away from the body's middle up), `.turn` (how far it turned since the last
  frame) and `.spin` (the part of that about its own length). A cable stretches, so it has none.

`npm run movement -- <id>` judges every clip of the exercise and names the worst one on each line,
and takes `--at` too. `node render.mjs --clip <clip> <dir>` renders a clip's top, middle and bottom,
side and front.

A limit is only as good as what set it. `even` on a bell's tilt tells a rocking press from a clean
one better than `never` does, and a tilt limit fits presses alone: a curl's bell turns with the
forearm, so an OK'd curl stands far past any limit a press could keep.

## Files

- `render.mjs`: PNGs of a pose or a baked clip, with no GPU.
- `studio/`: the studio. `plugin.mjs` its server, `checking.mjs` its queue of checks, `studio.js` and
  `index.html` its page,
  `keys.json` and `keys.mjs` its keymap, `video.mjs` the technique video's steps, speeds and sync,
  `view.mjs` the free camera, the hands' close-ups and the plates' toggle, `bones.mjs` the walk of
  the skeleton, `vite.config.mjs` what `npm run studio` serves.
- `tune.mjs`, `handles.mjs`, `patch.mjs`: the studio's numbers, its drag handles, and the text patch
  a save writes into `poses.mjs`.
- `poses.mjs`: the pose entries. `entries.mjs` resolves `like:` and reads traces.
- `motions.mjs`: how a body moves through a rep, one function per motion.
- `fit.mjs`: fits an entry on a machine to the mannequin, and gives the joint it drives.
- `rig.mjs`: the rig. It measures the mannequin in its bind pose, aims each bone from it, and uses
  two-bone IK for the legs and arms and a grip solve that lays the bar across the palm.
- `grip.mjs`: how a hand meets what it holds: each motion's grip landmark and thumb, the hand's
  skin, the seat of a bar on the palm, the fingers and the thumb closed joint by joint, and the
  measure the contact check takes. `seats.json`: each seat the poses use, solved once.
- `kit.mjs`: the generic equipment the library uses, each kit built from its recipe, and the floor
  shadow. `recipe.mjs`: the recipe format. `recipes/`: a recipe per kit. `parts.mjs`: the part
  library, built in code from extruded, turned and swept shapes, and its five tones.
- `stage.mjs`: a pose's scene, posed at any point of the rep; the checks and the bake both use it.
- `checks.mjs`: the checks.
- `bake.mjs`: samples the lowering (31 frames, 0 s at the top to 1 s at the bottom) into one
  animation, `lowering`, and writes the files. `glb.mjs` moves the shared mannequin into
  `mannequin.bin` and each kit's parts into `kit_<name>.bin`.
- `trace.mjs` and `trace/landmarks.py`: tracing a pose from footage. `traces/`: the traced angles.
- `review.mjs`: the batches, the OKs, notes and loops, and a batch's status. `review/<batch>.json`:
  each batch's verdicts; `review/notes.md`: the open notes; `review/loops.json`: the technique
  videos' loops.
- `numbers.mjs`: every frame's numbers from a baked clip. `movement.mjs`: a clip judged against its
  movement spec. `specs/<id>.json`: the movement specs.
- `model/mannequin.glb`: the mannequin, stripped to its mesh and skeleton (see `CREDITS.md`).

## A clip

One `.glb` holds the skinned mannequin, the equipment and the shadow, with one animation of the
rep's lowering: bone rotations, the pelvis's position, and each part of the kit that moves, turns
or stretches (a cable is a rod scaled along its length). A bone or part that doesn't move is left
at its pose at the top. The phone plays the lowering forward, holds, plays it backward and holds
again.

The mannequin's mesh and skin are the same in every clip, so they are baked once, into
`mannequin.bin`, and each kit's parts are baked once, into `kit_<name>.bin` (the kit's node is
marked with `extras.kit`). Each clip's glTF reads those buffers by URI, and holds only its shadow
and animation (`glb.mjs`). A kit file holds each distinct part once, however many clips read it, and
grows when a clip's options make a part of a new size. The bake checks every clip's mannequin is
byte for byte the same before it shares it. The phone hands each file a clip lists to gltfio's
`ResourceLoader` (`addResourceData`) before loading the clip. The tests merge the shared files into
each clip (`inlineResources`) and parse it from memory, and `bakeOnly` merges them to share the clips
again; the studio's page fetches them beside it.

`mannequins.json`:

```json
{ "clips": { "barbell_squat": { "clip": "barbell_squat.glb", "equipment": "barbell",
  "camera": { "target": [0, 0.87, 0], "halfHeight": 1.02, "azimuth": 62, "elevation": 8 },
  "resources": ["mannequin.bin", "kit_barbell.bin"] } } }
```

`resources` names the files a clip reads by URI, beside it in `assets/mannequins/`: the mannequin's,
then its kits' by name.
