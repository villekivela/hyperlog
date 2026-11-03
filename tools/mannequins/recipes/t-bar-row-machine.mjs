// A plate-loaded chest-supported T-bar row (refs/t_bar_row_machine/ref.md): the body stands on a
// foot plate and lies on a pad inclined 40°, its top end 1.27 m up, on an inclined beam held up by
// two posts off a floor beam: a rear post, which the lever pivots on 420 mm up, and a tall one under
// the pad. The lever runs forward beside the posts to a horn with a 450 mm plate on it, and at rest
// lies on a catch post at the front, where the base's feet splay out. A yoke rises from it: a stalk,
// a cross tube and an upright either side of the pad, each topped with a block, through which a
// grip runs fore and aft for a neutral grip and from which another runs out wide.
//
// The frame stands where the ref puts it, whatever the pose: the pivot 16 cm behind the hips' place,
// where a body lying on the pad has them. The lever is one body on a revolute joint at the pivot,
// its value the lever's angle up from level ahead: `P.lever`, or on its catch where the pose frame
// gives none.
import * as THREE from 'three';
import { recipe, revolute } from '../recipe.mjs';
import { basePlate, floorRail, hub, knob, pad, padGeometry, part, plateGeometry, rod, tube } from '../parts.mjs';
import { V, X, rad } from '../rig.mjs';

// From on its catch, 3° below level, to 20° above (the ref's Motion).
const LIMITS = [rad(-3), rad(20)];
const PIVOT = V(0, 0.42, -0.16);
// The lever runs beside the posts, its middle this far to the side of theirs: on the near side in
// the ref's photos, -x, with the pop-pin.
const BESIDE = -0.075;
// The pad's top end, on its face, 620 mm ahead of the pivot; the pad 510 mm long and 250 wide.
const TOP = V(0, 1.27, PIVOT.z + 0.62);
const PAD = { length: 0.51, width: 0.25, thick: 0.08 };
const INCLINE = rad(40);
// Up the beam, and out of the pad's face.
const UP_BEAM = V(0, Math.sin(INCLINE), Math.cos(INCLINE));
const FACE = V(0, Math.cos(INCLINE), -Math.sin(INCLINE));
const BEAM = 0.08;
// The beam's middle, under the pad's board.
const UNDER = PAD.thick + 0.012 + BEAM / 2;
/** The point on the beam's middle `s` metres down it from under the pad's top end. */
const onBeam = (s) => TOP.clone().addScaledVector(UP_BEAM, -s).addScaledVector(FACE, -UNDER);
/** The point on the beam's middle at `z`. */
const beamAt = (z) => onBeam((TOP.z - UNDER * FACE.z - z) / UP_BEAM.z);
// The foot plate's face, square to the beam near enough: tilted 45°, toward the body. The pop-pin
// sets it along the beam as far as its holes reach, down to this far below the pad's bottom end.
const SOLE = V(0, Math.SQRT1_2, Math.SQRT1_2);
const LOWEST = 0.95;
// On the lever, in its own frame (z along it, y up from it): the yoke 760 mm along it, its cross
// tube 150 mm above its line and its uprights 445 mm apart, each topped with a block 410 mm above
// the line. Through each block runs a 230 mm neutral grip, fore and aft, held on its rear half (the
// ref's vertical grips: the palms upright); out from each runs a 140 mm wide grip, 660 mm apart.
const YOKE = { along: 0.76, cross: 0.15, top: 0.41 };
const NEUTRAL = { out: 0.2225, up: YOKE.top, length: 0.23, held: -0.075 };
const WIDE = { out: 0.33, up: YOKE.top, length: 0.14 };
const GRIP = 0.0155;
// The horn, 370 mm from 1.25 m along the lever to its end at 1.62 m, a stop on it at 1.27 m and the
// plate's middle at 1.30 m.
const HORN = { from: 1.25, stop: 1.27, to: 1.62, plate: 1.3 };
// Where the lever lies on its catch, and where the base's front feet splay from.
const CATCH = 1.15;

export default recipe({
  name: 't_bar_row_machine',
  // Shown alone in the studio until a pose uses it: three-quarter from the front, as the ref's
  // photos are.
  standsAlone: { camera: { target: [0, 0.62, 0.42], halfHeight: 1.2, azimuth: -40, elevation: 14 } },
  bodies: { lever: revolute({ axis: V(-1, 0, 0), limits: LIMITS }) },
  // A pose holds one pair of grips; #160 solves the body to them.
  contacts: [
    { body: 'lever', point: 'neutral', landmark: 'hands' },
    { body: 'lever', point: 'wide', landmark: 'hands' },
    { body: 'frame', point: 'pad', landmark: 'chest' },
    { body: 'frame', point: 'footPlate', landmark: 'feet' },
  ],
  fit: [{
    sets: 'footPlate',
    rule: 'the pad\'s top end at the top of the sternum, the shoulders and head clear of it, the arms hanging straight to the handles on the catch, the knees a little bent',
  }],
  // `footPlate`: how far down the beam the foot plate's middle is from the pad's bottom end, where
  // its pop-pin holds it.
  build({ footPlate = 0.79 } = {}, M) {
    // The pad and its board, along the beam.
    const alongBeam = (mesh, at) => {
      mesh.position.copy(at);
      mesh.quaternion.setFromRotationMatrix(new THREE.Matrix4().makeBasis(X, FACE, UP_BEAM));
      return mesh;
    };
    const padMiddle = TOP.clone().addScaledVector(UP_BEAM, -PAD.length / 2);
    const cushion = alongBeam(part(padGeometry(PAD.width, PAD.thick, PAD.length, 0.025), M.vinyl, { solid: true }), padMiddle.clone().addScaledVector(FACE, -PAD.thick / 2));
    const board = alongBeam(part(padGeometry(PAD.width - 0.03, 0.012, PAD.length - 0.04, 0.003), M.frame), padMiddle.clone().addScaledVector(FACE, -PAD.thick - 0.006));
    // The beam, from under the pad's top end down past the foot plate's lowest setting.
    const end = onBeam(PAD.length + LOWEST + 0.08);
    const beam = tube(BEAM, onBeam(0.04), end, M.frame);
    // The base: a floor beam along the middle from a rear foot under the foot plate to the catch,
    // where two feet splay out forward to the machine's width, 1.09 m.
    const rear = end.z - 0.05;
    const front = PIVOT.z + CATCH;
    const splay = V(0.545, 0.035, front + 0.32);
    const base = [
      ...floorRail(V(-0.3, 0.035, rear), V(0.3, 0.035, rear), M),
      tube(0.07, V(0, 0.035, rear + 0.035), V(0, 0.035, front), M.frame),
      ...[-1, 1].map((s) => tube(0.07, V(0, 0.035, front), splay.clone().setX(s * splay.x), M.frame)),
      ...[-1, 1].map((s) => basePlate(s * (splay.x - 0.03), splay.z - 0.02, M)),
    ];
    // The posts up from the floor beam to the inclined beam: the rear one, with the lever's axle
    // through it, and the tall one under the pad.
    const below = BEAM / 2 / Math.cos(INCLINE);
    const posts = [PIVOT.z, PIVOT.z + 0.55].map((z) => tube(0.06, V(0, 0.07, z), V(0, beamAt(z).y - below + 0.01, z), M.frame));
    const axle = rod(0.015, PIVOT.clone().setX(0.05), PIVOT.clone().setX(BESIDE - 0.06), M.metal);
    // The catch: a post beside the floor beam's end with a rubber bumper the lever lies on.
    const catchZ = PIVOT.z + CATCH * Math.cos(LIMITS[0]);
    const rest = PIVOT.y + CATCH * Math.sin(LIMITS[0]) - BEAM / 2 / Math.cos(LIMITS[0]);
    const catchPost = [
      tube(0.06, V(BESIDE, 0.07, catchZ - 0.06), V(BESIDE, rest - 0.03, catchZ - 0.06), M.frame),
      basePlate(BESIDE, catchZ - 0.06, M),
      pad(0.1, 0.03, 0.1, M.vinyl, V(BESIDE, rest - 0.015, catchZ - 0.06), { soft: 0.008 }),
    ];
    // The foot plate on a sleeve round the beam, its pop-pin on the side, standing off the beam on a
    // stem far enough that its lower edge clears it.
    const sleeveAt = onBeam(PAD.length + footPlate);
    const plateMiddle = sleeveAt.clone().addScaledVector(FACE, 0.28);
    const sole = plateMiddle.clone().addScaledVector(SOLE, 0.0125);
    const plate = part(padGeometry(0.6, 0.025, 0.445, 0.006), M.frame, { solid: true });
    plate.position.copy(plateMiddle);
    plate.quaternion.setFromRotationMatrix(new THREE.Matrix4().makeBasis(X, SOLE, X.clone().cross(SOLE)));
    const footPlateParts = [
      tube(0.11, sleeveAt.clone().addScaledVector(UP_BEAM, -0.08), sleeveAt.clone().addScaledVector(UP_BEAM, 0.08), M.frame),
      tube(0.05, sleeveAt.clone().addScaledVector(FACE, 0.05), plateMiddle.clone().addScaledVector(SOLE, -0.03), M.frame),
      plate,
      rod(0.006, sleeveAt.clone().setX(-0.055), sleeveAt.clone().setX(-0.075), M.metal),
      knob(M, sleeveAt.clone().setX(-0.085)),
    ];

    // The lever, built in its own frame about the pivot: z along it, y up from it.
    const lever = new THREE.Group();
    const loaded = part(plateGeometry(0.225, 0.06), M.plate, { solid: true });
    loaded.position.set(BESIDE, 0, HORN.plate);
    loaded.rotation.y = Math.PI / 2;
    const { along, cross, top } = YOKE;
    const yoke = [
      tube(0.06, V(BESIDE, 0.04, along), V(BESIDE, cross - 0.03, along), M.frame),
      tube(0.06, V(-NEUTRAL.out - 0.03, cross, along), V(NEUTRAL.out + 0.03, cross, along), M.frame),
      ...[-1, 1].flatMap((s) => [
        tube(0.05, V(s * NEUTRAL.out, cross + 0.03, along), V(s * NEUTRAL.out, top - 0.03, along), M.frame),
        pad(0.06, 0.06, 0.06, M.frame, V(s * NEUTRAL.out, top, along), { soft: 0.006 }),
        rod(GRIP, V(s * NEUTRAL.out, top, along - NEUTRAL.length / 2), V(s * NEUTRAL.out, top, along + NEUTRAL.length / 2), M.vinyl, { held: true }),
        rod(0.0125, V(s * (NEUTRAL.out + 0.03), top, along), V(s * (WIDE.out + WIDE.length / 2 + 0.005), top, along), M.metal),
        rod(GRIP, V(s * (WIDE.out - WIDE.length / 2), top, along), V(s * (WIDE.out + WIDE.length / 2), top, along), M.vinyl, { held: true }),
      ]),
    ];
    lever.add(
      hub(M, V(BESIDE + 0.01, 0, 0)),
      tube(BEAM, V(BESIDE, 0, 0.06), V(BESIDE, 0, HORN.from), M.frame),
      ...yoke,
      rod(0.045, V(BESIDE, 0, HORN.stop - 0.015), V(BESIDE, 0, HORN.stop), M.metal),
      rod(0.025, V(BESIDE, 0, HORN.from), V(BESIDE, 0, HORN.to), M.metal),
      loaded,
      rod(0.034, V(BESIDE, 0, HORN.plate + 0.035), V(BESIDE, 0, HORN.plate + 0.055), M.metal),
    );
    const grips = (g) => [-1, 1].map((s) => V(s * g.out, g.up, along + (g.held ?? 0)));

    return {
      parts: [cushion, board, beam, ...base, ...posts, axle, ...catchPost, ...footPlateParts, lever],
      bodies: { lever: [lever] },
      drive(P) {
        const value = P.lever ?? LIMITS[0];
        lever.position.copy(PIVOT);
        lever.rotation.set(-value, 0, 0);
        lever.updateMatrix();
        const onLever = (ps) => ps.map((p) => p.applyMatrix4(lever.matrix));
        return {
          joints: { lever: { value, origin: PIVOT.clone() } },
          points: {
            neutral: onLever(grips(NEUTRAL)),
            wide: onLever(grips(WIDE)),
            pad: [padMiddle.clone()],
            footPlate: [sole.clone()],
          },
        };
      },
    };
  },
});
