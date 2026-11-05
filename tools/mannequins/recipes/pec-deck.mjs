// A seated pec deck (refs/pec_deck/ref.md): a seat and a back pad leaning back 7° in front of a tower
// that holds the weight stack, and an arch from the tower's top forward to an overhead unit with a
// vertical axis either side, 0.4 m apart. A lever swings from each in a horizontal plane, 0.48 m out
// to a pin, and a drop arm hangs from the pin. A vertical grip bends out of the drop arm square to
// the lever, ahead of it with the arm out, and a short level grip stands on the other side. The pec
// fly holds the vertical grips, back on the pad; the rear delt fly (`facing: 'pad'`) straddles the
// same seat with the chest on the pad and holds the short ones, so the kit stands turned round the
// mannequin, which always faces +z.
//
// Each arm is its own body on a revolute joint at its axis, its value how far it has swept from its
// rest: for the pec fly out at the sides, a little behind the shoulder line; for the rear delt fly
// back past the tower, 45° behind that line. Both arms move together in the ref's demo, so the stack
// hangs on the left joint, and `one` keeps the right arm at rest for a one-arm lift.
import * as THREE from 'three';
import { recipe, revolute } from '../recipe.mjs';
import {
  UPRIGHTS, WHEEL, bent, endCap, knob, pad, part, pivotJoint, pulley, rod, rodGeometry, seatOn, span, stackFrame, tube,
  weightStack, wire,
} from '../parts.mjs';
import { V, rad } from '../rig.mjs';

// The axes: 1.62 m up, either side of the centre line, over the shoulders of a back on the pad. A
// lever is 0.48 m, and at rest points this far behind straight out to its side.
const AXIS = { x: 0.2, y: 1.62, z: -0.1 };
const LEVER = 0.48;
const REST = { fly: rad(-10), rear: rad(-45) };
// From the rest to past the hands nearly touching in front of the chest (the ref's ROM, a little
// over), or past the arms in line with the shoulders.
const SWEEP = { fly: rad(120), rear: rad(60) };
// The vertical grip's middle is fixed at 1.05 m, and the seat moves. Each grip's middle stands this
// far off the drop arm, the short one a little lower, at the drop arm's foot.
const GRIP_Y = 1.05;
const SHORT_Y = 0.99;
const FOOT_Y = 0.93;
const GRIP = 0.017;
const GRIP_OFF = 0.1;
const SHORT_LENGTH = 0.15;
// The seat's top and z ([y, z]), its range, and how far the hips sit above it.
const SEAT = [0.42, 0.02];
const SEAT_RANGE = [0.4, 0.56];
const SITS = 0.09;
// The back pad: 0.3 wide, 0.59 tall, its bottom 0.05 above the seat, its face there at `face`.
const BACK = { width: 0.3, height: 0.59, thick: 0.08, above: 0.05, face: -0.16, lean: rad(7) };
// The tower's uprights and stack, the bar across their front that carries the pad, and its top.
const TOWER_Z = -0.4;
const BAR_Z = TOWER_Z + 0.095;
const FRAME_TOP = 1.5;
// The base's feet: its depth is the ref's 1.245 m footprint, feet included.
const FRONT_Z = 0.265;
const REAR_Z = -0.91;
const TOP = 1.8;
// How far the stack rises, in metres a radian of the left arm's sweep.
const STACK_RATIO = 0.09;

const mode = ({ facing }) => (facing === 'pad' ? 'rear' : 'fly');
/** The way a lever on side `s` runs at angle `a` from straight out, and the way square to it, ahead of it at 0. */
const leverDir = (s, a) => V(s * Math.cos(a), 0, Math.sin(a));
const aheadOf = (s, a) => V(-s * Math.sin(a), 0, Math.cos(a));
/** A point of the kit where it stands: turned round the mannequin when facing the pad. */
const standing = (options) => (p) => (mode(options) === 'rear' ? V(-p.x, p.y, -p.z) : p.clone());
/** Where side `s`'s grip is with the arm at angle `a`: the vertical one, or the short one. */
function gripAt(s, a, m) {
  const end = V(s * AXIS.x, 0, AXIS.z).addScaledVector(leverDir(s, a), LEVER);
  return m === 'rear' ? end.addScaledVector(aheadOf(s, a), -GRIP_OFF).setY(SHORT_Y) : end.addScaledVector(aheadOf(s, a), GRIP_OFF).setY(GRIP_Y);
}
const jointsOf = (options) => {
  const limits = [0, SWEEP[mode(options)]];
  return { left: revolute({ axis: V(0, 1, 0), limits }), right: revolute({ axis: V(0, -1, 0), limits }) };
};
/** The pad's middle on the seat at `seatY`, and the way out of its face. */
const padOf = (seatY) => {
  const up = V(0, Math.cos(BACK.lean), -Math.sin(BACK.lean));
  const out = V(0, Math.sin(BACK.lean), Math.cos(BACK.lean));
  const at = V(0, seatY + BACK.above, BACK.face).addScaledVector(up, BACK.height / 2).addScaledVector(out, -BACK.thick / 2);
  return { at, out };
};

export default recipe({
  name: 'pec_deck',
  // Shown alone in the studio until a pose uses it: three-quarter from the front.
  standsAlone: { camera: { target: [0, 0.9, -0.1], halfHeight: 1.2, azimuth: 145, elevation: 12 } },
  bodies: jointsOf,
  contacts: (options) => {
    const rear = mode(options) === 'rear';
    const held = rear ? 'Short' : 'Grip';
    return [
      // Turned round, the kit's left arm is on the mannequin's left.
      { body: 'left', point: `left${held}`, landmark: 'hands', hand: rear ? 'l' : 'r' },
      { body: 'right', point: `right${held}`, landmark: 'hands', hand: rear ? 'r' : 'l', ...(options.one ? { optional: true } : {}) },
      { body: 'frame', point: 'backPad', landmark: mode(options) === 'rear' ? 'chest' : 'back' },
    ];
  },
  stacks: [{ joint: 'left', ratio: STACK_RATIO }],
  rep: { joint: 'left', from: 'max' },
  fit: [
    {
      sets: 'seat',
      rule: 'the shoulders level with the grips',
      solve: ({ L }) => [Math.min(SEAT_RANGE[1], Math.max(SEAT_RANGE[0], GRIP_Y - L.trunkUp - SITS)), SEAT[1]],
    },
  ],
  linkage: (options, L) => {
    const { seat = SEAT } = options;
    const m = mode(options);
    const at = standing(options);
    // The kit's side each hand is on: turned round, the kit's left is the mannequin's.
    const kitSide = (side) => (m === 'rear' ? -side : side);
    const hip = m === 'rear' ? seat[1] + options.hips : BACK.face + L.trunkBack + 0.02;
    return {
      hip: [seat[0] + SITS, m === 'rear' ? -hip : hip],
      at: (value) => {
        const angle = (side) => REST[m] + (options.one && kitSide(side) > 0 ? 0 : value);
        return {
          pivot: at(V(0, AXIS.y, AXIS.z)),
          handle: (side) => at(gripAt(kitSide(side), angle(side), m)),
          // A neutral grip's index finger up; on the short grip, along it to its tip.
          across: (side) => (m === 'rear' ? at(aheadOf(kitSide(side), angle(side)).negate()) : V(0, 1, 0)),
        };
      },
    };
  },
  build(options, M) {
    const { seat = SEAT } = options;
    const m = mode(options);
    const rest = REST[m];
    const at = standing(options);
    const sides = [-1, 1];
    const axisOf = (s) => V(s * AXIS.x, AXIS.y, AXIS.z);
    const back = padOf(seat[0]);
    const barY = back.at.y;
    const unit = V(0, TOP - 0.07, AXIS.z);
    const wheel = V(0, FRAME_TOP - 0.035 - WHEEL - 0.02, TOWER_Z);
    const pivots = Object.fromEntries(sides.map((s) => [s, pivotJoint(M, axisOf(s), V(0, 1, 0))]));
    const padPart = pad(BACK.width, BACK.height, BACK.thick, M.vinyl, back.at, { soft: 0.025 });
    padPart.name = 'pec_deck_back';
    const padBoard = pad(BACK.width - 0.04, BACK.height - 0.04, 0.012, M.frame, back.at.clone().addScaledVector(back.out, -BACK.thick / 2 - 0.006), { soft: 0.003 });
    for (const p of [padPart, padBoard]) p.rotation.x = -BACK.lean;
    const weights = weightStack(V(0, 0, TOWER_Z), M, { rodTop: FRAME_TOP - 0.05, toward: 1 });
    const drop = wire(M);
    const frame = [
      ...seatOn(seat[0], seat[1], M, { pin: 1 }),
      padPart, padBoard,
      tube(0.05, back.at.clone().addScaledVector(back.out, -BACK.thick / 2 - 0.012), V(0, barY, BAR_Z), M.frame),
      tube(0.07, V(-UPRIGHTS - 0.035, barY, BAR_Z), V(UPRIGHTS + 0.035, barY, BAR_Z), M.frame),
      // The floor: a beam from the front foot to the rear one, a foot across each end.
      tube(0.07, V(0, 0.035, REAR_Z), V(0, 0.035, FRONT_Z), M.frame),
      ...[FRONT_Z, REAR_Z].map((z) => tube(0.07, V(-0.3, 0.035, z), V(0.3, 0.035, z), M.frame)),
      tube(0.07, V(-UPRIGHTS - 0.035, 0.035, TOWER_Z), V(UPRIGHTS + 0.035, 0.035, TOWER_Z), M.frame),
      ...sides.flatMap((s) => [
        endCap(s * 0.3, FRONT_Z, M), endCap(s * 0.3, REAR_Z, M),
        // A strut down and back from each upright to the rear foot.
        tube(0.06, V(s * UPRIGHTS, 0.75, TOWER_Z - 0.035), V(s * 0.27, 0.06, REAR_Z), M.frame),
      ]),
      // The tower with the stack in it, and the arch from its top forward to the overhead unit.
      ...stackFrame(TOWER_Z, FRAME_TOP, M),
      bent('pec_deck_arch', [V(0, FRAME_TOP, TOWER_Z - 0.04), V(0, 1.64, TOWER_Z - 0.04), V(0, unit.y, TOWER_Z + 0.08), V(0, unit.y, AXIS.z - 0.1), unit], 0.045, M.frame),
      pad(2 * AXIS.x + 0.14, 0.14, 0.18, M.frame, unit, { soft: 0.02 }),
      // The axle down from the unit at each axis, through the lever's housing.
      ...sides.flatMap((s) => pivots[s].fixed),
      ...pulley(wheel, M),
      ...weights.parts,
      drop,
    ];
    // Each arm at its rest: the lever out from its axis, a round pin at its end, the drop arm down to
    // its foot, the vertical grip bent out of it one way and the short grip out of it the other.
    const arms = sides.map((s) => {
      const end = axisOf(s).addScaledVector(leverDir(s, rest), LEVER);
      const ahead = aheadOf(s, rest);
      const foot = end.clone().setY(FOOT_Y);
      const top = end.clone().setY(GRIP_Y + 0.12);
      // The lever and the drop arm meet under the pin, each its own solid so the body is tested
      // against the tube and not the box round both.
      const lever = tube(0.045, axisOf(s), end, M.frame);
      const dropArm = tube(0.045, end, foot, M.frame);
      // One bent tube: out of the drop arm at the top, along, down the grip, and back into the arm at the foot.
      const off = (along, y) => end.clone().addScaledVector(ahead, along).setY(y);
      const handle = bent(`pec_deck_handle:${s}`, [
        off(0, top.y), off(0.04, top.y), off(GRIP_OFF, top.y - 0.03), off(GRIP_OFF, foot.y + 0.03), off(0.04, foot.y), off(0, foot.y),
      ], GRIP - 0.004, M.metal);
      const sleeve = rod(GRIP, off(GRIP_OFF, GRIP_Y - 0.08), off(GRIP_OFF, GRIP_Y + 0.08), M.vinyl, { held: true });
      const short = rod(GRIP, off(0, SHORT_Y), off(-GRIP_OFF - SHORT_LENGTH / 2 + 0.015, SHORT_Y), M.vinyl, { held: true });
      // The pin is a cylinder across the lever's end, centred on the drop arm, which hangs from it.
      const across = V(0, 1, 0).cross(end.clone().sub(axisOf(s))).setY(0).normalize().multiplyScalar(0.03);
      const pin = part(rodGeometry(0.03, 20), M.pulley);
      span(pin, end.clone().sub(across), end.clone().add(across));
      // The arm's foot is a dome under the handle.
      const dome = knob(M, foot, 0.022);
      dome.material = M.frame;
      const nodes = [pivots[s].housing, lever, dropArm, pin, dome, handle, sleeve, short];
      return { s, nodes, base: nodes.map((n) => ({ p: n.position.clone(), q: n.quaternion.clone() })) };
    });
    /** Lays arm `a` swept `value` from its rest, in the kit's own frame. */
    const swing = ({ s, nodes, base }, value) => {
      const turn = new THREE.Quaternion().setFromAxisAngle(V(0, -s, 0), value);
      const o = axisOf(s);
      nodes.forEach((n, i) => {
        n.position.copy(base[i].p).sub(o).applyQuaternion(turn).add(o);
        n.quaternion.copy(turn).multiply(base[i].q);
      });
    };
    return {
      parts: [...frame, ...arms.flatMap((a) => a.nodes)],
      bodies: { left: arms[0].nodes, right: arms[1].nodes },
      stacks: {
        left: {
          lift(l) {
            weights.lift(l);
            span(drop, V(0, wheel.y, TOWER_Z), weights.top(l));
          },
        },
      },
      stand(g) {
        if (m === 'rear') g.rotation.y = Math.PI;
      },
      drive(P) {
        // Without a pose the arms rest.
        const value = P.linkage?.value ?? 0;
        const values = { left: value, right: options.one ? 0 : value };
        const named = (s) => (s < 0 ? 'left' : 'right');
        const held = m === 'rear' ? 'Short' : 'Grip';
        arms.forEach((a) => swing(a, values[named(a.s)]));
        return {
          joints: Object.fromEntries(arms.map(({ s }) => [named(s), { value: values[named(s)], origin: at(axisOf(s)) }])),
          points: {
            ...Object.fromEntries(arms.map(({ s }) => [`${named(s)}${held}`, [at(gripAt(s, rest + values[named(s)], m))]])),
            backPad: [at(back.at)],
          },
        };
      },
    };
  },
});
