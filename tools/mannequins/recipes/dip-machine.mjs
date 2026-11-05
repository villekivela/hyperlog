// A seated dip machine (refs/dip_machine/ref.md): the user sits upright and pushes two handles down
// to the hips. The handle arms hang on a transverse pivot at the top of an inclined rear upright and
// reach forward, each ending in a swivel and a grip bar that points up and forward at the start. The
// seat, back pad and footplates are one carriage, rocking back about a low pivot under the seat as
// the handles go down, so the finish is reclined with the feet raised. Beside the seat, at the front,
// stands the weight stack the arms raise by a cable.
//
// Two bodies, each on a revolute joint, independent in the format: the arms (`arms`, radians down
// from the start, 0 to 40°) and the carriage (`carriage`, radians back from the start, 0 to 20°).
// A pose will drive the carriage at about half the arms' angle. The frame stands where the ref puts
// it whatever the pose, both joints at their start; no pose uses it yet, so the drive has no
// fit rules to solve. The seat is fixed at the ref's measured height.
//
// The weight stack stands to the user's right (-x, the user facing +z), as the ref's photos show it, its frame a pair of
// uprights on a base beam out from the middle one. No brand, logo or decal is drawn on it.
import { recipe, revolute } from '../recipe.mjs';
import {
  along, floorRail, pad, part, popPin, pulley, rod, rodGeometry, span, stackFrame, tube, turnedPart, wheelGeometry, WHEEL, weightStack,
} from '../parts.mjs';
import { V, rad } from '../rig.mjs';

// The arms' pivot, [y, z]: 880 above the floor, 740 behind the seat's front edge; the arm from it
// to the grip's middle is 720 long, 5° above level at the start; the grip bar 35° up from the arm.
const PIVOT = [0.88, -0.54];
const ARM = 0.72;
const START = rad(5);
const BAR = rad(35);
// The grips' middles 250 either side of the machine's middle; the arm tubes stand outside them.
const GRIPS = 0.25;
const ARMS_OUT = 0.31;
// The carriage rocks about a low pivot under the seat.
const ROCK = [0.2, -0.15];
const SEAT = { top: 0.48, z: -0.01, w: 0.4, d: 0.42 };
const FEET = { y: 0.12, z: 0.7, apart: 0.2 };
// The back pad leans 6° back of upright at the start.
const PAD = { lean: rad(6), up: 0.4, w: 0.36, length: 0.62 };
const TOWER = { x: -0.4, z: 0.45, top: 1.355 };
const FLOOR = { rear: -0.57, front: 0.86, half: 0.6 };
/** How far the stack's cable draws it up for a radian of the arms. */
const RATIO = 0.3;

/** Names what a part is, for the tests and the studio. */
const role = (mesh, name) => { mesh.userData.role = name; return mesh; };

/** The grip's middle on side `s` (±1) with the arms `value` radians down from the start. */
const gripAt = (value, s) => {
  const a = START - value;
  return V(s * GRIPS, PIVOT[0] + ARM * Math.sin(a), PIVOT[1] + ARM * Math.cos(a));
};

export default recipe({
  name: 'dip_machine',
  // Shown alone in the studio until a pose uses it: three-quarter from the front, as the ref's photo is.
  standsAlone: { camera: { target: [0, 0.75, 0.1], halfHeight: 1.1, azimuth: 40, elevation: 14 } },
  bodies: {
    arms: revolute({ axis: V(1, 0, 0), limits: [0, rad(40)] }),
    carriage: revolute({ axis: V(-1, 0, 0), limits: [0, rad(20)] }),
  },
  rep: { joint: 'arms', from: 'max' },
  contacts: [
    { body: 'arms', point: 'handles', landmark: 'hands' },
    { body: 'carriage', point: 'backPad', landmark: 'back' },
    { body: 'carriage', point: 'footplates', landmark: 'feet' },
  ],
  stacks: [{ joint: 'arms', ratio: RATIO }],
  build(_options, M) {
    const sides = [-1, 1];
    const rear = FLOOR.rear + 0.05;
    const wheel = V(TOWER.x, TOWER.top - 0.035 - WHEEL - 0.02, TOWER.z + WHEEL);

    // The frame: a floor beam along the machine, a rear cross beam and a front one, the rear upright
    // leaning back up to the arms' pivot with a brace to it, and the housing round the pivot.
    const frame = [];
    const [spine, ...spineCaps] = floorRail(V(0, 0.035, FLOOR.rear), V(0, 0.035, FLOOR.front), M);
    const [cross, ...crossCaps] = floorRail(V(-FLOOR.half + 0.016, 0.035, rear), V(FLOOR.half - 0.016, 0.035, rear), M);
    const [front, ...frontCaps] = floorRail(V(0.3, 0.035, FLOOR.front - 0.035), V(TOWER.x - 0.2, 0.035, FLOOR.front - 0.035), M);
    frame.push(spine, ...spineCaps, cross, ...crossCaps, front, ...frontCaps);
    frame.push(role(tube(0.08, V(0, 0.07, -0.4), V(0, PIVOT[0] - 0.05, PIVOT[1]), M.frame), 'upright'));
    frame.push(tube(0.05, V(0, 0.07, 0.0), V(0, PIVOT[0] - 0.2, PIVOT[1] + 0.05), M.frame));
    frame.push(role(along(0.022, -0.36, 0.36, M.metal, 16).translateY(PIVOT[0]).translateZ(PIVOT[1]), 'axle'));
    for (const s of sides) {
      frame.push(pad(0.05, 0.14, 0.12, M.pulley, V(s * 0.06, PIVOT[0], PIVOT[1]), { soft: 0.004, solid: false }));
    }
    // The carriage's pivot, a cross shaft in two blocks on the spine, covered.
    frame.push(along(0.02, -0.18, 0.18, M.metal, 12).translateY(ROCK[0]).translateZ(ROCK[1]));
    for (const s of sides) frame.push(pad(0.05, 0.12, 0.12, M.pulley, V(s * 0.12, ROCK[0] - 0.05, ROCK[1]), { soft: 0.004, solid: false }));

    // The stack's tower to the user's right, its base beam out from the spine, a cable wheel on top.
    const tower = [
      ...stackFrame(TOWER.z, TOWER.top, M).map((m) => { m.position.x += TOWER.x; return m; }),
      ...pulley(wheel.clone().setX(0), M).map((m) => { m.position.x += TOWER.x; return m; }),
      tube(0.07, V(0, 0.035, TOWER.z), V(TOWER.x - 0.19, 0.035, TOWER.z), M.frame),
    ];
    const weights = weightStack(V(TOWER.x, 0, TOWER.z), M, { rodTop: 1.3, toward: -1 });
    const drop = part(rodGeometry(0.0045, 8), M.metal);

    // The carriage: a spine from the pivot forward to the foot beam, the seat on its post, the back
    // pad on a stalk behind the seat, and a footplate either side, each with a pad.
    const carriage = [
      role(tube(0.06, V(0, ROCK[0], ROCK[1]), V(0, FEET.y + 0.05, FEET.z - 0.04), M.frame), 'spine'),
      role(pad(SEAT.w, 0.075, SEAT.d, M.vinyl, V(0, SEAT.top - 0.0375, SEAT.z), { soft: 0.024 }), 'seat'),
      pad(SEAT.w - 0.06, 0.012, SEAT.d - 0.06, M.frame, V(0, SEAT.top - 0.081, SEAT.z), { soft: 0.003 }),
      tube(0.06, V(0, ROCK[0] + 0.03, SEAT.z), V(0, SEAT.top - 0.087, SEAT.z), M.frame),
      popPin(V(0, 0.33, SEAT.z + 0.03), 1, M),
      along(0.02, -FEET.apart - 0.04, FEET.apart + 0.04, M.metal, 12).translateY(FEET.y - 0.03).translateZ(FEET.z - 0.03),
    ];
    // The pad stands up behind the seat on the lean, and a stalk runs back down from it to the carriage.
    const padAt = V(0, SEAT.top + PAD.up * Math.cos(PAD.lean), SEAT.z - 0.22 - PAD.up * Math.sin(PAD.lean) - 0.04);
    const cushion = role(pad(PAD.w, PAD.length, 0.08, M.vinyl, padAt, { soft: 0.025 }), 'backPad');
    cushion.rotation.x = -PAD.lean;
    carriage.push(cushion);
    carriage.push(tube(0.08, V(0, SEAT.top - 0.09, SEAT.z - 0.2), V(0, padAt.y - 0.1, padAt.z - 0.05), M.frame));
    for (const s of sides) {
      const plate = role(pad(0.14, 0.03, 0.22, M.vinyl, V(s * FEET.apart, FEET.y, FEET.z), { soft: 0.01 }), 'footplate');
      carriage.push(plate, rod(0.015, V(s * FEET.apart, FEET.y - 0.02, FEET.z - 0.05), V(s * FEET.apart, FEET.y + 0.0, FEET.z - 0.05), M.metal, { solid: false }));
    }

    // The arms: a tube each from the pivot to the grip's middle, with a swivel and a grip bar through it,
    // angled up from the arm and held in the hand, a cap on its end.
    const arms = sides.map(() => ({
      tube: part(rodGeometry(0.022), M.frame, { solid: true }),
      hub: turnedPart(wheelGeometry(0.05, 0.04), M.pulley, V(0, 0, 0), { solid: false }),
      swivel: part(rodGeometry(0.03), M.metal),
      grip: part(rodGeometry(0.017), M.metal, { held: true }),
      sleeve: part(rodGeometry(0.0205), M.vinyl, { held: true }),
      cap: part(rodGeometry(0.024), M.vinyl),
    }));
    const barDir = (value) => { const a = START - value + BAR; return V(0, Math.sin(a), Math.cos(a)); };
    const pivot = V(0, PIVOT[0], PIVOT[1]);
    sides.forEach((s, i) => {
      const a = arms[i];
      const g = gripAt(0, s);
      const b = barDir(0);
      const elbow = V(s * ARMS_OUT, PIVOT[0], PIVOT[1]);
      a.hub.position.copy(elbow);
      span(a.tube, elbow, g.clone().addScaledVector(b, -0.08));
      span(a.swivel, g.clone().addScaledVector(b, -0.1), g.clone().addScaledVector(b, -0.06));
      span(a.grip, g.clone().addScaledVector(b, -0.1), g.clone().addScaledVector(b, 0.12));
      span(a.sleeve, g.clone().addScaledVector(b, -0.07), g.clone().addScaledVector(b, 0.1));
      span(a.cap, g.clone().addScaledVector(b, 0.1), g.clone().addScaledVector(b, 0.125));
    });
    const armParts = arms.flatMap((a) => [a.tube, a.hub, a.swivel, a.grip, a.sleeve, a.cap]);

    return {
      parts: [...frame, ...tower, ...weights.parts, drop, ...carriage, ...armParts],
      bodies: { arms: armParts, carriage },
      stacks: {
        arms: {
          lift(l) {
            weights.lift(l);
            span(drop, V(TOWER.x, wheel.y, TOWER.z), weights.top(l));
          },
        },
      },
      drive() {
        const feet = sides.map((s) => V(s * FEET.apart, FEET.y, FEET.z));
        return {
          joints: {
            arms: { value: 0, origin: pivot.clone() },
            carriage: { value: 0, origin: V(0, ROCK[0], ROCK[1]) },
          },
          points: {
            handles: sides.map((s) => gripAt(0, s)),
            backPad: [padAt.clone()],
            footplates: feet,
          },
        };
      },
    };
  },
});
