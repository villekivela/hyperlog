# Mannequins

Bakes the exercise mannequin's clips (SPEC, Technique videos → The mannequin). Each exercise has a
pose entry in `poses.mjs`; the tool poses the Quaternius mannequin through the rep's lowering, adds
the exercise's equipment and a floor shadow, and writes one `.glb` per exercise and the
`mannequins.json` index into `app/src/main/assets/mannequins/`. The phone only plays them.

```sh
cd tools/mannequins
npm ci          # three.js, pinned in package-lock.json
npm run bake    # node bake.mjs [out-dir]
npm test        # bakes into a temp dir and reads the squat back
```

Commit the baked files with the entry that made them. Baking is deterministic: the same entries
give byte-identical files.

## Files

- `poses.mjs`: the pose entries, keyed by exercise id in `app/src/main/assets/exercises.json`.
  `equipment` is the library's equipment key (an exercise edited to other equipment on the phone
  loses its clip); `motion` names how the body moves; `camera` frames the stage.
- `motions.mjs`: how a body moves through a rep, one function per motion (`squat` so far).
- `rig.mjs`: the rig. It measures the mannequin in its bind pose, aims each bone from it, and uses
  two-bone IK for the legs and arms, a grip solve that lays the bar across the palm, and finger
  curls round the bar.
- `kit.mjs`: equipment built from primitives (the barbell so far) and the floor shadow. Materials
  are named (`body`, `joint`, `bar`, `plate`, `shadow`) for the phone, which colours them from the
  theme.
- `bake.mjs`: samples the lowering (31 frames, 0 s at the top to 1 s at the bottom) into one
  animation, `lowering`, and writes the files.
- `model/mannequin.glb`: the mannequin, stripped to its mesh and skeleton (see `CREDITS.md`).

## A clip

One `.glb` holds the skinned mannequin, the equipment and the shadow, with one animation of the
rep's lowering: bone rotations, the pelvis's position and the equipment's movement. A bone that
doesn't move is left at its pose at the top. The phone plays the lowering forward, holds, plays it
backward and holds again.

`mannequins.json`:

```json
{ "clips": { "barbell_squat": { "clip": "barbell_squat.glb", "equipment": "barbell",
  "camera": { "target": [0, 0.87, 0], "half": 1.02, "azimuth": 62, "elevation": 8 } } } }
```

`target` is the point the stage's camera looks at (metres, y up, the mannequin facing +z), `half`
is half the height it shows there, and `azimuth` and `elevation` are the turn and tilt it opens on,
in degrees. A double-tap on the phone returns to them.
