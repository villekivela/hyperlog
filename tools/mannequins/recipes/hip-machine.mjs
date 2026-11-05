// A selectorized hip abduction / adduction machine (refs/hip_machine/ref.md): the lifter sits on a
// seat 520 mm up, back on a reclined pad, knees bent and feet on a plate at the foot of each arm's
// post, a knee pad on the post at knee height. The two arms turn about one vertical axis on the centre
// line under the seat, 140 mm behind its front edge; each is an L, a bar out from the axle and a
// bar forward to a post 400 mm ahead of it, and the lower leg rides with it. A tall tower with the
// weight stack stands ahead of the posts, as the maker's photos show; the stack's travel isn't drawn.
//
// One machine: abduction has the pads on the outsides of the knees and the arms closed, adduction
// has them between the knees and the arms open. The `pads` option says which side, the `start`
// option how far open the arms stand, in the ratchet's 10° steps.
//
// The user sits facing +z, the hips over the axis. The left arm (`arm_l`, at +x) and the right one
// are each on a joint about the vertical. Its value is how far the arm is open, in radians from
// closed, where the arms lie parallel to the machine's length.
import * as THREE from 'three';
import { recipe, revolute } from '../recipe.mjs';
import { floorRail, pad, stackFrame, tube, weightStack, rod } from '../parts.mjs';
import { UP, V, rad } from '../rig.mjs';

/** The arms' range from closed, and the ratchet's step, in degrees. */
const OPEN = 50;
const STEP = 10;
/** The least an arm stands open with the pads between the knees, so the posts clear each other. */
const INNER_LEAST = 10;
/** The start the pads' side gives, in degrees, when none is asked for. */
const STARTS = { outer: 0, inner: 50 };
const PADS = Object.keys(STARTS);

/** The side handles: out from the centre line, up, and their length along z. */
const HANDLE = { x: 0.24, y: 0.7, length: 0.18 };

/** The seat's top, depth and front edge ahead of the axis. */
const SEAT = { top: 0.52, depth: 0.36, front: 0.14, width: 0.36 };
/** The backrest's foot, behind the axis, its length, and how far it leans back from upright. */
const BACK = { z: -0.22, depth: 0.08, from: 0.62, length: 0.5, lean: rad(15) };
/** The rear leg behind the backrest, and the tower's top. */
const LEG = { z: -0.5, top: 1.0 };
const TOWER = 1.365;
/** The tower with the stack stands ahead of the posts. */
const STACK_Z = 0.92;
/** The arms: each at its own height on the one axle, 400 mm from it to the post. */
const ARM_Y = { l: 0.18, r: 0.24 };
const REACH = 0.4;
const BAR = 0.05;
/** The post's top, and the foot plate's top above the floor. */
const POST_TOP = 0.72;
const PLATE_TOP = 0.08;
/** The knee pad: thick, 280 high and 160 across, its middle 560 mm up. */
const PAD = { thick: 0.06, height: 0.28, depth: 0.16, y: 0.56 };
/** The mannequin's knee, from the centre line, and the half width of it. */
const KNEE = { x: 0.089, half: 0.05 };
/** Where the pad meets the knee in the left arm's frame: its face, and the pad's and post's middle. */
const FACES = {
  outer: KNEE.x + KNEE.half,
  inner: KNEE.x - KNEE.half,
};
const sides = [{ k: 'l', s: 1 }, { k: 'r', s: -1 }];

/** Which side the pads are on and the start the arms are set to, snapped to the ratchet's steps. */
function setting({ pads = 'outer', start } = {}) {
  if (!PADS.includes(pads)) throw new Error(`The hip machine has no pads "${pads}": ${PADS.join(' or ')}`);
  const least = pads === 'inner' ? INNER_LEAST : 0;
  const steps = Math.round((start ?? STARTS[pads]) / STEP) * STEP;
  return { pads, least, start: Math.min(OPEN, Math.max(least, steps)) };
}

/** The pad's middle and its post's, in the left arm's frame. */
function padAt(pads) {
  const way = pads === 'outer' ? 1 : -1;
  const middle = FACES[pads] + way * PAD.thick / 2;
  return { middle, post: middle + way * (PAD.thick / 2 + BAR / 2) };
}

/** Marks a part for the tests and the studio. */
const role = (mesh, name) => { mesh.userData.role = name; return mesh; };

export default recipe({
  name: 'hip_machine',
  // Shown alone in the studio until a pose uses it: three-quarter from the front.
  standsAlone: { camera: { target: [0, 0.7, 0.2], halfHeight: 1.2, azimuth: -40, elevation: 14 } },
  bodies: (options) => {
    const { least } = setting(options);
    return Object.fromEntries(sides.map(({ k, s }) => [`arm_${k}`, revolute({ axis: UP.clone().multiplyScalar(s), limits: [rad(least), rad(OPEN)] })]));
  },
  contacts: [
    ...sides.flatMap(({ k }) => [
      { body: `arm_${k}`, point: `pad_${k}`, landmark: 'shins' },
      { body: `arm_${k}`, point: `plate_${k}`, landmark: 'feet' },
      { body: 'frame', point: `handle_${k}`, landmark: 'hands', optional: true },
    ]),
    { body: 'frame', point: 'seat', landmark: 'thighs' },
    { body: 'frame', point: 'back', landmark: 'back' },
  ],
  fit: [{
    sets: 'start',
    rule: 'abduction starts with the knees together, adduction with them apart: the ratchet\'s 10° step that puts the pads against the knees; the axis marker under the midpoint between the hips',
  }],
  // `pads`: `outer` (on the outsides of the knees, abduction) or `inner` (between them, adduction);
  // `start`: how far open the arms stand, in degrees.
  build(options, M) {
    const { pads, start } = setting(options);
    const at = padAt(pads);
    const value = rad(start);
    const frame = [];
    const mesh = (m, name) => { frame.push(role(m, name)); };
    // The base: a floor beam the length of the machine, a cross beam under the foot and one under the stack.
    frame.push(...floorRail(V(0, 0.035, -0.5), V(0, 0.035, 0.97), M));
    frame.push(...floorRail(V(-0.33, 0.035, STACK_Z), V(0.33, 0.035, STACK_Z), M));
    frame.push(...floorRail(V(-0.33, 0.035, -0.45), V(0.33, 0.035, -0.45), M));
    const backMiddle = V(0, BACK.from + BACK.length / 2 * Math.cos(BACK.lean), BACK.z - BACK.length / 2 * Math.sin(BACK.lean) - BACK.depth / 2);
    // The seat on its pan, a bracket back to the column, and the backrest on it.
    const seatMiddle = SEAT.front - SEAT.depth / 2;
    mesh(pad(SEAT.width, 0.075, SEAT.depth, M.vinyl, V(0, SEAT.top - 0.0375, seatMiddle), { soft: 0.024 }), 'seat');
    frame.push(pad(SEAT.width - 0.06, 0.012, SEAT.depth - 0.06, M.frame, V(0, SEAT.top - 0.081, seatMiddle), { soft: 0.003 }));
    // The rear leg leans with the backrest; the seat's bracket meets it.
    const legAt = (y) => LEG.z + (y - 0.07) / (LEG.top - 0.07) * (BACK.z - (LEG.top - BACK.from) * Math.tan(BACK.lean) - BACK.depth / Math.cos(BACK.lean) - 0.04 - LEG.z);
    frame.push(tube(0.05, V(0, SEAT.top - 0.09, legAt(SEAT.top - 0.09)), V(0, SEAT.top - 0.09, seatMiddle), M.frame));
    frame.push(tube(0.08, V(0, 0.07, LEG.z), V(0, LEG.top, legAt(LEG.top)), M.frame));
    const back = pad(SEAT.width, BACK.length, BACK.depth, M.vinyl, backMiddle, { soft: 0.024 });
    back.rotation.x = -BACK.lean;
    mesh(back, 'backrest');
    // The side handles beside the seat, and the brace back to the stack.
    for (const { s } of sides) {
      frame.push(role(rod(0.015, V(s * HANDLE.x, HANDLE.y, 0), V(s * HANDLE.x, HANDLE.y, HANDLE.length), M.vinyl, { held: true }), 'handle'));
      frame.push(tube(0.03, V(s * HANDLE.x, HANDLE.y, 0), V(s * HANDLE.x, SEAT.top - 0.09, 0), M.frame));
    }
    const weights = weightStack(V(0, 0, STACK_Z), M, { rodTop: 1.1, toward: -1 });
    weights.idle();
    frame.push(...stackFrame(STACK_Z, TOWER, M), ...weights.parts);
    // The one axle both arms turn on.
    frame.push(role(tube(0.04, V(0, 0.07, 0), V(0, ARM_Y.r + 0.04, 0), M.metal), 'axle'));

    // Each arm, in the frame of its closed self, turned about the axle by its value.
    const arms = Object.fromEntries(sides.map(({ k, s }) => {
      const y = ARM_Y[k];
      const post = s * at.post;
      const arm = new THREE.Group();
      arm.add(
        tube(BAR, V(0, y, 0), V(post, y, 0), M.frame),
        tube(BAR, V(post, y, 0), V(post, y, REACH), M.frame),
        tube(BAR, V(post, 0.06, REACH), V(post, POST_TOP, REACH), M.frame),
        role(pad(PAD.thick, PAD.height, PAD.depth, M.vinyl, V(s * at.middle, PAD.y, REACH), { soft: 0.015 }), 'pad'),
        // The foot plate on a bracket from the post's foot, under the foot.
        tube(0.03, V(post, 0.06, REACH), V(s * KNEE.x, 0.06, REACH), M.frame),
        role(pad(0.17, 0.016, 0.24, M.frame, V(s * KNEE.x, PLATE_TOP - 0.008, REACH), { soft: 0.003 }), 'footPlate'),
        pad(0.1, 0.03, 0.1, M.pulley, V(0, y, 0), { soft: 0.01 }),
      );
      return [k, arm];
    }));
    return {
      parts: [...frame, ...Object.values(arms)],
      bodies: { arm_l: [arms.l], arm_r: [arms.r] },
      drive() {
        const joints = {};
        const points = {
          seat: [V(0, SEAT.top, seatMiddle)],
          back: [V(0, backMiddle.y, backMiddle.z + BACK.depth / 2)],
        };
        for (const { k, s } of sides) {
          arms[k].rotation.y = s * value;
          const turn = (p) => p.applyAxisAngle(UP, s * value);
          points[`pad_${k}`] = [turn(V(s * FACES[pads], PAD.y, REACH))];
          points[`plate_${k}`] = [turn(V(s * KNEE.x, PLATE_TOP, REACH))];
          points[`handle_${k}`] = [V(s * HANDLE.x, HANDLE.y, HANDLE.length / 2)];
          joints[`arm_${k}`] = { value, origin: V(0, ARM_Y[k], 0) };
        }
        return { joints, points };
      },
    };
  },
});
