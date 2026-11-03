# Mannequins

Makes the exercise mannequin's clips (SPEC, Technique videos → The mannequin). Each exercise has a
pose entry in `poses.mjs`. The tool poses the Quaternius mannequin through the rep's lowering in
generic equipment, checks every frame, and writes one `.glb` per exercise and the `mannequins.json`
index into `app/src/main/assets/mannequins/`. The phone only plays them.

```sh
cd tools/mannequins
npm ci                                  # three.js, pinned in package-lock.json
npm run bake                            # node bake.mjs [out-dir]: check every pose, then bake them all
npm test                                # entries, kits, checks, bake, trace, review and the studio
npm run trace -- <pose> <footage> <from>-<to> [--angles …]   # angles from footage (Tracing a pose)
npm run studio                          # the studio, at http://localhost:5190/studio/ (The studio)
npm run review -- status <batch>        # whether every exercise in it is OK (Reviewing a batch)
npm run review -- phone <batch>         # the owner has swiped the batch in the debug gallery
```

Commit the baked files with the entries that made them. Baking is deterministic: the same entries
give byte-identical files. CI re-bakes every pose when `tools/mannequins/` or the clips change and
fails on any difference (`committed.test.mjs`), so bake and commit together.

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
baked at 0°, 15°, 30°, 45°, 60° and 75°, one clip each, `<id>_<angle>.glb`. Its entry has a
`bench` and a motion that follows the pad, so one entry fits every angle:

```js
db_incline_press: {
  motion: 'dumbbell_press',
  kit: { dumbbells: {}, bench: { hinge: -0.02 } },   // the backrest turns about this z
  bench: { typical: 30, cameras: { 0: { …camera }, 75: { …camera } }, perAngle: { 'feet.z': { 0: 0.2, 75: 0.3 } } },
  body: { sit: 0.15, seat: 0.06, lean: 8, head: 0 }, // see below
  top: { press: 0, over: [0.581, 0.283], rotate: 0, tilt: 0 },
  bottom: { press: 1, chest: [0.1592, -0.0137], rotate: 0, tilt: 0 },
  grip: { width: 0.4, lockout: 0.26, elbows: 0.7975 },
},
```

- **The backrest's frame.** The numbers are written against the backrest, so one set holds from
  0° to 75°. `body.sit` is how far the hips sit off the pad's face and `body.seat` how far down it
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
  gives the framing at the lowest and highest angle, and the angles between move steadily from one
  to the other. `prepare(id, poses, angle)` poses the entry at an angle, its typical one by default.
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
  A verdict's `clip` fingerprint covers all six clips.

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

A press or a squat holds the bar low in the palm, near its heel; a pull at the fingers' roots, the
palm against it. Each finger closes round what the hand holds only until its tip touches it, and
the hands check fails fingers folded into one another (their joints under 1.3 cm apart).

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
- **`grip.rotate`** (any pose with hands) twists the forearms, in degrees: 0 overhand, 90 neutral
  (palms toward each other), 180 underhand (palms toward the face). The default is 0; the
  close-grip pulldown is 180.
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
- **`camera`** frames the stage: `target` is the point the camera looks at, `halfHeight` half the
  height it shows there, and `azimuth` and `elevation` the turn and tilt it opens on. A double-tap
  on the phone returns to them. `shadow: { size: [w, d], at: [x, z] }` moves the floor shadow.

The motions, in `motions.mjs`, and what each reads besides the above:

| Motion | The body | Reads |
| --- | --- | --- |
| `squat` | a bar on the back, kept over the middle of the feet | `shin`, `thigh` (and `trunk` when traced: the body then keeps its shape and turns about the ankles); `bar: { up, back }` (`back` negative racks it in front); `feet.kneesOut`; `grip.pole` for the elbows (with `grip.crossed`, how high they ride), and `grip.crossed`, a cross-arm front squat: the bar rides on the front delts and each hand lies open on the other shoulder at `grip.spot` [across, up, forward] from its joint, not gripping the bar |
| `hinge` | a bar hanging in straight arms, straight up and down at `bar.z` in front of the ankles: an RDL, a deadlift, a shrug | `shin`, `thigh`, `trunk`; `shrug`, which lifts the bar with the shoulders; or, for a pull from the floor, `bar` (its height at the top and the bottom) in place of `trunk`: the trunk leans as far as the straight arms need, and the shins stand back where the bar would run into the shins or thighs |
| `row` | hinged over, pulling a bar straight up and down at `bar.z`: a bent-over row, a Pendlay row | `shin`, `thigh`, `trunk`; `bar`, its height at the top and the bottom; `grip.pole` for the elbows. A drag of the hands sets the end's `bar` and `bar.z` |
| `bench_press` | lying on a bench, pressing a bar | `bar: [y, z]`; `feet.z`; `grip.elbows`, 0 tucked to 1 flared; `body.head`, the head's tilt from the spine to rest it on the pad |
| `dumbbell_press` | on an adjustable bench at `bench.angle`, pressing dumbbells | `body.sit`, `body.seat`, `body.lean`, `body.head`; `top.over` [out, up] and `bottom.chest` [out, down] from the shoulders in the trunk's frame; `rotate`, `tilt`; `grip.lockout`, `grip.width`, `grip.elbows` |
| `pulldown` | seated under a thigh pad, pulling a bar down | `trunk`, `bar: [y, z]` |
| `pressdown` | standing at a high pulley, elbows still | `shin`, `thigh`, `trunk`, `upperArm`, `forearm` |
| `leg_press` | seated in a 45° sled, feet on its plate | `knee`; `feet: { line, down }` place the plate; `grip.at` the handles |
| `chest_press` | seated, pressing horizontal handles on hung levers, held like a bar | `handle: [y, z]` at the top and bottom, `press` 0 to 1; `lever`, the pivot's height above the path |
| `arms` | the arms swinging side-on, standing or seated (`body.hip`): a curl, a preacher curl, an extension overhead | `upperArm`, `forearm` (an angle past 180 swings the forearm on over the top, behind the head); `grip.width`; `grip.one` (1 the left hand) with `grip.rest` [x, y, z] for the other; `grip.pole` [out, up, forward], where the elbows point; `feet.stagger` stands the left foot that far ahead of the right (behind, if negative) |
| `free_arms` | the arms moving freely, standing or seated: a raise, a flye, a press between pulleys, an upright row (a bar lies between the hands) | `hand: [x, y, z]`, the left hand's point at the top and the bottom (the right mirrors it); `grip.one`, `grip.rest`, `grip.pole` as for `arms`; `grip.arc` (a flye, a raise) sweeps the hand on an arc about its shoulder from the top's point to the bottom's, its reach between theirs, so the elbow holds its bend |
| `knee_lever` | the knees bending against a roller: seated (a leg extension, a seated curl) or `body.prone`, face down (a lying curl) | `knee`, degrees from straight; `body.hip`, `body.thigh` (below level); `grip.at` [y, z] the handles, `grip.across` [out, up, forward] the way their knuckles run |
| `calf_raise` | seated, the knees under a lever's pads, the balls of the feet on a platform's edge | `ankle`, degrees from flat, heels up positive; `feet.ball` [y, z]; `pivot` [y, z], where the lever turns with the knees; `grip.at` [y, z], the hands on its handle at the stretch, `grip.width` |
| `hack_squat` | in a hack squat's sled, the back on its pad | `hip: [y, z]` at the top and bottom (the rails run between them); `body.trunk`; `feet: { y, z, tilt }` place the platform; `grip.at` [up, out] from the shoulders; `grip.pole` |
| `lever_handles` | seated, handles swinging on levers about a pivot: a chest-supported row, a machine pulldown | `pivot: [y, z]`; `handle: [y, z]` at the top and bottom, `press` 0 to 1 |
| `bridge` | lying face up, the knees bent: a glute bridge, a hip thrust (the upper back across a bench) | `hip: [y, z]` at the top and bottom, each a trunk's length from `body.shoulders` [y, z], the shoulder joints, which the trunk turns about; `bar: [y, z]` at the top and bottom (or `bar.up` over the hips, square to the trunk); `arch` [3 degrees] at the top and bottom bends the spine's links, the shoulders kept where the straight back's are |

## Equipment

Every kind of equipment in the library, and the generic kits a pose in it can use (`EQUIPMENT` in
`kit.mjs`). A kind marked missing has no kit yet, so its exercises can't be posed until it has one.

| Equipment | Kits |
| --- | --- |
| `barbell` | `barbell`, `bench`, `platform` (a low one under the feet, for a deficit) |
| `dumbbell` | `dumbbells`, `bench` |
| `cable` | `cable_tower` (with a seat and thigh pad for a pulldown), `cable_column`, `bench` |
| `machine` | the machine kits by kind, below; the kinds marked missing have none yet |
| `bodyweight` | none needed |
| `ez_bar` | missing |
| `trap_bar` | missing |
| `safety_bar` | missing |
| `smith_machine` | missing |
| `plate` | missing |
| `weighted_bodyweight` | missing |

The library's machines by kind (`MACHINES` in `kit.mjs`):

| Machine kind | Exercises | Kit |
| --- | --- | --- |
| `leg_press` | Leg Press, 1-Leg Leg Press, Leg Press Calf Press | `leg_press` |
| `chest_press` | Machine Chest Press, Incline Machine Press | `chest_press` |
| `squat_machine` | Hack Squat, Pendulum Squat, Machine Squat | `squat_machine` |
| `leg_extension` | Leg Extension | `leg_extension` |
| `leg_curl` | Lying and Seated Leg Curl | `leg_curl` (`lying` for the lying one) |
| `glute_ham_raise` | Glute-Ham Raise | missing |
| `pec_deck` | Pec Deck, Reverse and 1-Arm Reverse Pec Deck | missing |
| `row_machine` | Machine Row, Chest-Supported Machine and T-Bar Rows, T-Bar Row | `row_machine` |
| `pulldown_machine` | Machine Pulldown, Machine Lat Pullover | `pulldown_machine` |
| `shoulder_press` | Machine Shoulder Press | missing |
| `lateral_raise_machine` | Seated and Standing Machine Lateral Raise | missing |
| `preacher_curl_machine` | Machine Preacher Curl | `preacher_curl_machine` |
| `dip_machine` | Seated Machine Dip | missing |
| `hip_machine` | Machine Hip Abduction and Adduction | missing |
| `hip_thrust_machine` | Machine Hip Thrust | missing |
| `calf_raise_machine` | Standing, Seated and Donkey Calf Raise | `calf_raise_machine` (seated) |
| `crunch_machine` | Machine Crunch | missing |
| `shrug_machine` | Machine Shrug, Machine Kelso Shrug | missing |

- `bench` is flat, or adjustable with `angle` raising its back pad from flat; with `across: z` it
  stands across the body, its middle at that z (a hip thrust's), and its own `z` then runs along it
  (so give `z: 0` to centre it).
- `cable_column` is one adjustable column (or two, `twin`, mirrored at [-x, z]) standing at `at`
  [x, z] and facing the middle of the room, its carriage `pulley` metres up, its pulley swivelling
  toward the cable. `attachment` is `handle` (a stirrup in the hand on side `hand`) or `rope`; with
  `twin`, a stirrup in each hand, each on its own column's cable, or the far one's with `crossed`.
  Its stack is lowest where the cable is drawn least, at whichever end of the rep that is, so it
  rises through a raise or a flye whose top is the hands' far point, and even there it is held 6 cm
  off its rest (`HELD`), as a working stack is; the leg machines' stacks are held the same way. A kit's `place(P, top, bottom)` gets
  both ends of the rep for that.
- `cable_tower` takes the pulley's height and place, its column, and an `attachment`: `lat_bar`,
  `straight_bar`, `v_handle` or `handles` (two stirrups `spread` metres either side of the middle,
  their grips front to back). The cable meets the front of the pulley's wheel and runs over a
  second wheel above the stack, which stands far enough behind the pulley for it. Its weight stack
  rises as the cable is drawn out, and is held 6 cm off its rest even at the stretch (`HELD`), as
  every working stack is. A pose draws on its high pulley only (1.5 m or more) for now; without a
  seat, the tower also shows its low pulley, in a housing on the floor rail in front of the stack.
- `bench` rolls on wheels at its front foot and has a handle out of its back; the adjustable one's
  back pad rests on a strut set in a ladder of rungs on the rail, placed for the angle the clip is
  baked at (the phone turns the pad a few degrees either side of it at most).
- The machines take their seat, `seat` [y, z] (the top of its cushion), and place the rest from the
  pose: `leg_extension` and the seated `leg_curl` a seat tilted `tilt` degrees up toward the knees,
  a cam lever about the knees and a back pad `pad` metres behind the spine; the lying
  `leg_curl` (`lying: true`) a bed humped at `hips` [y, z], its thigh and chest pads sloping down
  `thigh` and `chest` degrees from there; `calf_raise_machine` the foot block under the balls and a
  lever on a post at the pose's pivot, its pads over the knees and its handle in the hands;
  `squat_machine` the platform, the rails along the hips' path and a carriage riding them, its
  back pad's face `pad` metres behind the hips and its handles along the grip;
  `row_machine` a chest pad, `chest: { up, front }` from the trunk at the top of the rep;
  `pulldown_machine` a back pad `pad` metres behind the spine and thigh pads at `thighPad` [y, z],
  hung under their bar; `preacher_curl_machine` an arm pad `pad` metres deep under the upper arms,
  its front edge short of the elbows. The three are plate-loaded: `row_machine` and
  `pulldown_machine` turn their levers on hubs at the pose's `pivot` (the row's low in front of the
  knees, the pulldown's on a portal ahead of the head), each lever carrying a loaded horn on an
  arm out from its hub; `preacher_curl_machine` turns its bar's levers on hubs in line with the
  elbows, beyond the pad, each with a loaded horn on a stub below it. Every plate rises as the
  muscle works.
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

Every kit is built at one level of detail (SPEC, #127): rounded tubing on base plates, soft pads,
a stack of twelve plates on guide rods with a selector pin, pulleys in housings with the cable over
them, and shaped bars and handles with grips. Nothing smaller shows at phone size, so there are no
bolts or seams. Each shape is made once per size and shared by every part of that size (`shape`
in `kit.mjs`), with positions and normals only, so a kit's file stays small.

Materials are named for the phone, which colours them from the theme: `body` and `joint` on the
mannequin, the kits' five tones, and `shadow`. The tones are `frame` (frames and uprights), `vinyl`
(pads, rollers and grips), `plate` (a stack's plates and every other weight), `metal` (rods, bars,
cables and pins) and `pulley` (pulleys and their housings). Each tone's roughness ships in the clip;
the colours in it are the light theme's, which the phone replaces.

## Checks

`checks.mjs` checks every frame of a pose before it bakes, and a pose that fails any check doesn't
bake (nor does anything else in that bake):

- **Hands:** each hand holds its grip, within 1.5 cm.
- **Feet:** each foot stands where the pose puts it, flat on the floor or on its plate, within 2 cm.
- **Equipment:** no limb is more than 2 cm inside a solid part of the kit (a pad gives that much),
  and none passes into a thin part like a bar at all. Only the hands close round what they hold.
  The floor counts as solid: nothing goes more than 1.5 cm into it.
- **Joints:** knees and elbows bend no more than 155°, a knee turns no more than 60° from the hips'
  front, a hip flexes no more than 140° or extends past 30°, the head turns no more than 60° from
  the trunk, and the ankle stays between 55° and 160° from shin to foot.
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
Use it for a pull request's screenshots.

## The studio

`npm run studio` serves the tool at `http://localhost:5190/studio/` (Vite, pinned in
`package-lock.json`, with the plugin in `studio/plugin.mjs`). It is for the owner's machine and
listens on localhost only.

- **The sidebar** lists every exercise in the library, posed or not, grouped by equipment, then
  every kit. Each row shows its status: `no pose`, `failing` (a check fails), `notes` (an open note
  in `review/notes.md`), `ok` (OK'd for the clip it has now), `phone-checked`, `changed` (OK'd for
  a clip since re-baked) or `waiting`; a kit shows `notes`, `kit` or `unused`. `f` filters it by
  name, id, equipment or status (`no pose`, `notes`).
- **The stage** shows one item. An exercise is posed live with the tool's own modules, looping at
  its tempo or held at the top or the bottom, with drag handles (`handles.mjs`) when held, and the
  checks re-run on what it shows. `c` shows the baked clip instead. A kit is shown alone, set up as
  the first exercise that uses it (by id) sets it up. A change to `kit.mjs` or `poses.mjs` on disk
  reloads the page on the same item, its unsaved numbers kept.
- **The numbers** are the exercise's pose entry, one row each (`tune.mjs` says which, and the levers
  its motion offers that the entry leaves at the default), lengths shown in millimetres. `h` and `l`
  step the selected one a degree, a millimetre or a hundredth of a ratio, `H` and `L` a tenth of
  that, and `=` types a value (`412.5`, or `+2` and `+-2` to move it). A drag of a handle sets the
  same numbers (`top.bar` and `grip.width`, `grip.elbows`, `feet.z`, `body.hip`, `trunk`,
  `body.arch`), a point whole.
- **The technique video** of an exercise with a video link in `exercises.json` is beside the
  stage, opened at the link's timestamp. The first time it is opened the studio fetches it with
  `yt-dlp` (the picture only, 720p at most, played muted) into a cache outside the repo,
  `~/.cache/hyperlog/videos` (`$XDG_CACHE_HOME`, or `MANNEQUIN_VIDEOS` to move it), and reuses that
  copy, so a video shared by several exercises is fetched once. Without a link, or offline, the
  panel says so and the rest of the studio works as before. `[` and `]` set the loop's start (A,
  the top of the rep) and end (B, the bottom), saved in `review/loops.json`; the video reopens on
  them. With sync on (`gv`) and both ends set, the loop is the rep: a step or a scrub of the video
  moves the stage, the stage's own play, holds and steps move the video, and both play at the
  clip's tempo, slowed to the video's speed.
- **Saving** (`:w`) writes the tuned numbers into `poses.mjs` as text (`patch.mjs`, so the comments
  stay) and re-bakes every clip in a process of its own (the kit files are shared, so one clip
  can't bake alone), then shows the new clip. A pose that fails its checks doesn't bake, any pose,
  and then `poses.mjs` goes back to what it was and the studio names the pose that failed. Read the diff after: a
  comment may now describe the old number.

The page imports the tool's modules through Vite, with node's `fs`, `url` and `path` stubbed out:
the server (`ssrLoadModule`, fresh on every change) prepares each entry, so the page never reads a
file. Nothing the Vite config imports may reach `poses.mjs` or `kit.mjs`: Vite restarts its server
when one of those changes.

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
| `<` `>`, `p` | Bench angle down / up; tune the number per angle |
| `gf` `gs` `gt`, `←` `→` | Front, side and top cameras; turn 10° |
| `u` / `Ctrl+r` | Undo / redo |
| `:w` | Save and re-bake |
| `o` / `a` | OK the exercise (again takes it back) / add a note |

With the video focused, its own keys take over (a group in `keys.json` with `"focus": "video"`):

| Key | Does |
| --- | --- |
| `Space` | Play / pause (the rep and the video together when synced) |
| `h` `l` | One frame back / forward; a count multiplies |
| `H` `L` | 1 s back / forward |
| `<` `>` | Speed down / up: 0.25×, 0.5×, 1× |
| `[` `]` | Set the loop's start (A, the top of the rep) / end (B, the bottom) at this frame |
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
- **A loop** (`[` and `]` on the technique video) goes into `review/loops.json` as
  `{ "<id>": { "a": <s>, "b": <s> } }`, the times in the video of the rep's top and bottom.
- **A phone check**: `npm run review -- phone <batch>` puts `phone`, the fingerprint of each
  exercise's clip now, into the batch's file, once the owner has swiped it in the debug gallery.

`npm run review -- status <batch>` lists each exercise as `ok`, `phone-checked`, `notes` (an open
note, and no OK for the clip it has now), `changed` (OK'd on a clip since re-baked) or `waiting`,
with its open notes, and exits non-zero until the batch is done: every exercise OK for the clip it
has now.

## Files

- `render.mjs`: PNGs of a pose, with no GPU.
- `studio/`: the studio. `plugin.mjs` its server, `studio.js` and `index.html` its page,
  `keys.json` and `keys.mjs` its keymap, `video.mjs` the technique video's steps, speeds and sync,
  `vite.config.mjs` what `npm run studio` serves.
- `tune.mjs`, `handles.mjs`, `patch.mjs`: the studio's numbers, its drag handles, and the text patch
  a save writes into `poses.mjs`.
- `poses.mjs`: the pose entries. `entries.mjs` resolves `like:` and reads traces.
- `motions.mjs`: how a body moves through a rep, one function per motion.
- `rig.mjs`: the rig. It measures the mannequin in its bind pose, aims each bone from it, and uses
  two-bone IK for the legs and arms, a grip solve that lays the bar across the palm, and finger
  curls round the bar.
- `kit.mjs`: the generic equipment, built in code from extruded, turned and swept shapes, its five
  tones, the equipment the library uses, and the floor shadow.
- `stage.mjs`: a pose's scene, posed at any point of the rep; the checks and the bake both use it.
- `checks.mjs`: the checks.
- `bake.mjs`: samples the lowering (31 frames, 0 s at the top to 1 s at the bottom) into one
  animation, `lowering`, and writes the files. `glb.mjs` moves the shared mannequin into
  `mannequin.bin` and each kit's parts into `kit_<name>.bin`.
- `trace.mjs` and `trace/landmarks.py`: tracing a pose from footage. `traces/`: the traced angles.
- `review.mjs`: the batches, the OKs, notes and loops, and a batch's status. `review/<batch>.json`:
  each batch's verdicts; `review/notes.md`: the open notes; `review/loops.json`: the technique
  videos' A–B loops.
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
each clip (`inlineResources`) and parse it from memory; the studio's page fetches them beside it.

`mannequins.json`:

```json
{ "clips": { "barbell_squat": { "clip": "barbell_squat.glb", "equipment": "barbell",
  "camera": { "target": [0, 0.87, 0], "halfHeight": 1.02, "azimuth": 62, "elevation": 8 },
  "resources": ["mannequin.bin", "kit_barbell.bin"] } } }
```

`resources` names the files a clip reads by URI, beside it in `assets/mannequins/`: the mannequin's,
then its kits' by name.
