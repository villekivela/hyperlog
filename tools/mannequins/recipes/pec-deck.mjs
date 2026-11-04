// A seated pec deck (refs/pec_deck/ref.md): a seat and back pad in front of a column, and on the
// column an overhead unit with a vertical axis either side, 0.4 m apart. A lever arm swings from each
// in a horizontal plane, 0.48 m out to a pin, and a drop arm hangs from the pin to a vertical grip at
// shoulder height, with a short horizontal grip across its foot. One weight stack stands behind the
// column. The same grips serve the pec fly (arms swept in front of the chest) and the rear delt fly
// (arms swept out to the sides), so nothing is adjusted between them but the seat.
//
// Each arm is its own body on a revolute joint at its axis, its value how far it has swept in from
// the rest the recipe measures it from: out at the sides, a little behind the shoulder line. The
// stack stays down: two arms pull it and the recipe format ties a stack to one joint.
import { recipe, revolute } from '../recipe.mjs';
import {
  UPRIGHTS, WHEEL, basePlate, endCap, pad, part, pulley, rod, rodGeometry, rollerGeometry, seatOn, span, stackFrame, tube, turnedPart,
  weightStack, wheelGeometry,
} from '../parts.mjs';
import { V, rad } from '../rig.mjs';

// The axes: 1.62 m up, either side of the centre line, over the shoulders' place (z 0). A lever is
// 0.48 m, and at rest points this far behind straight out to its side.
const AXIS = { x: 0.2, y: 1.62, z: 0 };
const LEVER = 0.48;
const REST = rad(-10);
// From at the sides to the hands nearly touching in front of the chest (the ref's ROM, a little over).
const SWEEP = rad(120);
// The grips are fixed at 1.05 m; the seat moves.
const GRIP_Y = 1.05;
const GRIP = 0.017;
const GRIP_OFF = 0.07;
// The seat's top and z ([y, z]), its range, and how far the hips sit above it.
const SEAT = [0.42, 0.02];
const SEAT_RANGE = [0.4, 0.56];
const SITS = 0.09;
// The back pad: 0.3 wide, 0.59 tall, its bottom 0.05 above the seat, its face 0.16 behind the hips.
const BACK = { width: 0.3, height: 0.59, thick: 0.08, above: 0.05, face: -0.16 };
const COLUMN_Z = -0.34;
const STACK_Z = -0.56;
const FRAME_TOP = 1.55;
const FRONT_Z = 0.47;
const TOP = 1.8;

export default recipe({
  name: 'pec_deck',
  // Shown alone in the studio until a pose uses it: three-quarter from the front.
  standsAlone: { camera: { target: [0, 0.9, -0.1], halfHeight: 1.2, azimuth: 145, elevation: 12 } },
  bodies: {
    left: revolute({ axis: V(0, 1, 0), limits: [0, SWEEP] }),
    right: revolute({ axis: V(0, -1, 0), limits: [0, SWEEP] }),
  },
  contacts: [
    { body: 'left', point: 'leftGrip', landmark: 'hands' },
    { body: 'right', point: 'rightGrip', landmark: 'hands' },
    { body: 'frame', point: 'backPad', landmark: 'back' },
  ],
  fit: [
    {
      sets: 'seat',
      rule: 'the shoulders level with the grips',
      solve: ({ L }) => [Math.min(SEAT_RANGE[1], Math.max(SEAT_RANGE[0], GRIP_Y - L.trunkUp - SITS)), SEAT[1]],
    },
  ],
  build({ seat = SEAT } = {}, M) {
    const sides = [-1, 1];
    const axisOf = (s) => V(s * AXIS.x, AXIS.y, AXIS.z);
    const padY = seat[0] + BACK.above + BACK.height / 2;
    const padZ = BACK.face - BACK.thick / 2;
    const unit = V(0, TOP - 0.07, 0);
    const wheel = V(0, FRAME_TOP - 0.035 - WHEEL - 0.02, STACK_Z + WHEEL);
    const frame = [
      ...seatOn(seat[0], seat[1], M, { pin: 1 }),
      pad(BACK.width, BACK.height, BACK.thick, M.vinyl, V(0, padY, padZ), { soft: 0.025 }),
      pad(BACK.width - 0.04, BACK.height - 0.04, 0.012, M.frame, V(0, padY, BACK.face - BACK.thick - 0.006), { soft: 0.003 }),
      tube(0.05, V(0, padY, BACK.face - BACK.thick - 0.012), V(0, padY, COLUMN_Z), M.frame),
      // The floor: a beam out to the front foot, a foot at each end.
      tube(0.07, V(0, 0.035, STACK_Z - 0.12), V(0, 0.035, FRONT_Z), M.frame),
      tube(0.07, V(-UPRIGHTS - 0.15, 0.035, STACK_Z), V(UPRIGHTS + 0.15, 0.035, STACK_Z), M.frame),
      tube(0.07, V(-0.3, 0.035, FRONT_Z), V(0.3, 0.035, FRONT_Z), M.frame),
      ...sides.flatMap((s) => [endCap(s * (UPRIGHTS + 0.15), STACK_Z, M), endCap(s * 0.3, FRONT_Z, M)]),
      // The column behind the pad, and the boom out from its top to the overhead unit.
      tube(0.09, V(0, 0.035, COLUMN_Z), V(0, unit.y, COLUMN_Z), M.frame),
      tube(0.08, V(0, unit.y, COLUMN_Z), V(0, unit.y, unit.z), M.frame),
      pad(2 * AXIS.x + 0.14, 0.14, 0.18, M.frame, unit, { soft: 0.02 }),
      // An axle under the unit at each axis, and a round cap on its foot.
      ...sides.flatMap((s) => [
        rod(0.012, V(s * AXIS.x, AXIS.y - 0.03, AXIS.z), V(s * AXIS.x, unit.y - 0.07, AXIS.z), M.metal),
        turnedPart(wheelGeometry(0.03, 0.012), M.pulley, V(s * AXIS.x, AXIS.y - 0.035, AXIS.z), { solid: false }),
      ]),
      // The stack behind, tied to the column at its top, its cable over a wheel to the column.
      ...stackFrame(STACK_Z, FRAME_TOP, M),
      tube(0.06, V(0, FRAME_TOP, STACK_Z + 0.035), V(0, FRAME_TOP, COLUMN_Z - 0.045), M.frame),
      ...pulley(wheel, M),
      rod(0.0045, V(0, wheel.y + WHEEL, wheel.z), V(0, wheel.y + WHEEL, COLUMN_Z - 0.045), M.metal),
      basePlate(0, STACK_Z - 0.12, M),
      ...weightStack(V(0, 0, STACK_Z), M, { rodTop: 1.5, toward: 1 }).parts,
    ];
    // Each arm at rest: the lever out from its axis, a round pin at its end, the drop arm down to the
    // vertical grip, and the short horizontal grip across the foot, reaching toward the middle.
    const rest = (s) => V(s * AXIS.x + s * LEVER * Math.cos(REST), AXIS.y, AXIS.z + LEVER * Math.sin(REST));
    const arms = sides.map((s) => {
      const end = rest(s);
      const foot = end.clone().setY(GRIP_Y - 0.12);
      const top = end.clone().setY(GRIP_Y + 0.12);
      // The grip stands this far in along the lever from the drop arm, joined to it by a stub at each end.
      const inward = axisOf(s).sub(end).setY(0).normalize().multiplyScalar(GRIP_OFF);
      const lever = part(rodGeometry(0.025), M.frame);
      span(lever, axisOf(s), end);
      const drop = part(rodGeometry(0.022), M.frame);
      span(drop, end, foot);
      const vertical = part(rodGeometry(GRIP), M.vinyl, { held: true });
      span(vertical, foot.clone().add(inward), top.clone().add(inward));
      const stubs = [foot, top].map((from) => {
        const stub = part(rodGeometry(GRIP), M.vinyl, { held: true });
        span(stub, from, from.clone().add(inward));
        return stub;
      });
      const hub = turnedPart(wheelGeometry(0.035, 0.05), M.pulley, axisOf(s), { solid: false });
      const pin = turnedPart(rollerGeometry(0.03, 0.05), M.pulley, end, { solid: false });
      return { body: [hub, lever, pin, drop, vertical, ...stubs], point: end.clone().add(inward).setY(GRIP_Y) };
    });
    return {
      parts: [...frame, ...arms.flatMap((a) => a.body)],
      bodies: { left: arms[0].body, right: arms[1].body },
      drive() {
        return {
          joints: Object.fromEntries(sides.map((s, i) => [['left', 'right'][i], { value: 0, origin: axisOf(s) }])),
          points: { leftGrip: [arms[0].point.clone()], rightGrip: [arms[1].point.clone()], backPad: [V(0, padY, padZ)] },
        };
      },
    };
  },
});
