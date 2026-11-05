// A seated pec deck (refs/pec_deck/ref.md): a seat and a back pad leaning back 7° in front of a tower,
// narrow across and deep fore and aft, with the weight stack's broad face looking to the side, and an
// arch from the tower's back over its top and forward to an overhead unit with a
// vertical axis either side, 0.4 m apart, between two flat plates. A lever swings from each in a
// horizontal plane, 0.55 m out to a pin, and a drop arm hangs from the pin. A vertical grip hangs like
// a cane out of the drop arm, square to the lever, ahead of it with the arm out, and a short level
// grip stands on the other side. One post leaning back from the floor carries the seat and the pad. The pec
// fly holds the vertical grips, back on the pad; the rear delt fly (`facing: 'pad'`) straddles the
// same seat with the chest on the pad and holds the short ones, so the kit stands turned round the
// mannequin, which always faces +z.
//
// Each arm is its own body on a revolute joint at its axis, its value how far it has swept from its
// rest: for the pec fly out at the sides, a little behind the shoulder line; for the rear delt fly
// back past the tower, 45° behind that line. Both arms move together in the ref's demo, so the stack
// hangs on the left joint, and `one` keeps the right arm at rest for a one-arm lift.
import * as THREE from 'three';
import { HELD, recipe, revolute } from '../recipe.mjs';
import {
  WHEEL, bent, knob, mitered, pad, part, pivotJoint, pulley, rod, rodGeometry, span, tube,
  weightStack, wire,
} from '../parts.mjs';
import { V, rad } from '../rig.mjs';

// The axes: 1.8 m up, either side of the centre line, over the shoulders of a back on the pad. A
// lever is 0.55 m, and at rest points this far behind straight out to its side.
const AXIS = { x: 0.2, y: 1.8, z: -0.1 };
const LEVER = 0.55;
// Each way of using it: the levers' rest, the sweep from there, to the grips about to meet or past
// the arms in line with the shoulders, the grip the hands hold, and what lies on the pad.
const MODES = {
  fly: { rest: rad(-10), sweep: rad(110), held: 'Grip', on: 'back' },
  rear: { rest: rad(-45), sweep: rad(60), held: 'Short', on: 'chest' },
};
// The vertical grip's middle is fixed at 1.1 m, and the seat moves. Each grip's middle stands this
// far off the drop arm, the short one a little lower, near the drop arm's foot.
const GRIP_Y = 1.1;
const SHORT_Y = 1.08;
const FOOT_Y = 1.02;
const GRIP_RADIUS = 0.017;
const GRIP_OFF = 0.1;
const SHORT_LENGTH = 0.15;
// The seat's top and z ([y, z]), its range, and how far the hips sit above it.
const SEAT = [0.5, 0.02];
const SEAT_RANGE = [0.44, 0.62];
const SITS = 0.095;
// The back pad: 0.3 wide, 0.65 tall, its bottom 0.05 above the seat, its face there at `face`.
const BACK = { width: 0.3, height: 0.65, thick: 0.08, above: 0.05, face: -0.16, lean: rad(7) };
// The tower: two uprights, one ahead of the other, the stack turned between them, and its top.
const TOWER = { width: 0.13, depth: 0.07, spread: 0.19 };
const STACK_Z = -0.53;
const FRAME_TOP = 1.66;
// The unit's two plates, above and below the levers, and how high the rear braces meet the tower.
const PLATES = [1.69, 1.9];
const BRACE_Y = 0.46;
// The base: a spine from a small foot under the seat to the rear beam, which runs out each side
// from the tower's back. The ref's 1.245 m footprint runs from there to the arms swept forward.
const FRONT_FOOT = { width: 0.24, depth: 0.12, z: 0.05 };
const REAR = { z: STACK_Z - TOWER.spread, out: 0.42 };
// The top of the arch, the machine's height.
const TOP = 2;
// How far the stack rises, in metres a radian of the left arm's sweep.
const STACK_RATIO = 0.09;
// The arch's tube.
const ARCH_RADIUS = 0.045;

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
/** How far the arm on side `s` has swept with the rep at `value`: with `one`, the right arm stays. */
const sweptBy = ({ one }, s, value) => (one && s > 0 ? 0 : value);
const jointsOf = (options) => {
  const limits = [0, MODES[mode(options)].sweep];
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
  bodies: jointsOf,
  contacts: (options) => {
    const rear = mode(options) === 'rear';
    const { held, on } = MODES[mode(options)];
    return [
      // Turned round, the kit's left arm is on the mannequin's left.
      { body: 'left', point: `left${held}`, landmark: 'hands', hand: rear ? 'l' : 'r' },
      { body: 'right', point: `right${held}`, landmark: 'hands', hand: rear ? 'r' : 'l', ...(options.one ? { optional: true } : {}) },
      { body: 'frame', point: 'backPad', landmark: on },
      { body: 'frame', point: 'seatTop', landmark: 'thighs' },
    ];
  },
  stacks: [{ joint: 'left', ratio: STACK_RATIO }],
  rep: { joint: 'left', from: 'min' },
  fit: [
    {
      sets: 'seat',
      rule: 'the shoulders level with the grips',
      solve: ({ L }) => [Math.min(SEAT_RANGE[1], Math.max(SEAT_RANGE[0], GRIP_Y - L.trunkUp - SITS)), SEAT[1]],
    },
  ],
  linkage: (options, L) => {
    const { seat = SEAT, hips = 0 } = options;
    const m = mode(options);
    const at = standing(options);
    // The kit's side each hand is on: turned round, the kit's left is the mannequin's.
    const kitSide = (side) => (m === 'rear' ? -side : side);
    const hip = m === 'rear' ? seat[1] + hips : BACK.face + L.trunkBack + 0.02;
    return {
      hip: [seat[0] + SITS, m === 'rear' ? -hip : hip],
      at: (value) => {
        const angle = (side) => MODES[m].rest + sweptBy(options, kitSide(side), value);
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
    const { rest, held } = MODES[m];
    const at = standing(options);
    const sides = [-1, 1];
    const axisOf = (s) => V(s * AXIS.x, AXIS.y, AXIS.z);
    const back = padOf(seat[0]);
    const archY = TOP - ARCH_RADIUS;
    const wheel = V(0, FRAME_TOP - 0.035 - WHEEL - 0.02, STACK_Z);
    const pivots = Object.fromEntries(sides.map((s) => [s, pivotJoint(M, axisOf(s), V(0, 1, 0))]));
    const padPart = pad(BACK.width, BACK.height, BACK.thick, M.vinyl, back.at, { soft: 0.025 });
    padPart.name = 'pec_deck_back';
    const padBoard = pad(BACK.width - 0.04, BACK.height - 0.04, 0.012, M.frame, back.at.clone().addScaledVector(back.out, -BACK.thick / 2 - 0.006), { soft: 0.003 });
    for (const p of [padPart, padBoard]) p.rotation.x = -BACK.lean;
    const weights = weightStack(V(0, 0, 0), M, { rodTop: FRAME_TOP - 0.05, toward: 1 });
    // The stack is built across the kit, so a group turns it to lie fore and aft.
    const stack = new THREE.Group();
    stack.add(...weights.parts);
    stack.position.z = STACK_Z;
    stack.rotation.y = Math.PI / 2;
    const drop = wire(M);
    const frame = [
      pad(0.36, 0.075, 0.38, M.vinyl, V(0, seat[0] - 0.0375, seat[1]), { soft: 0.024 }),
      pad(0.3, 0.012, 0.32, M.frame, V(0, seat[0] - 0.081, seat[1]), { soft: 0.003 }),
      padPart, padBoard,
      // The post from the floor beam up under the seat's back edge, behind the pad's face to its board.
      tube(0.07, V(0, 0.07, -0.065), V(0, seat[0] + 0.2, BACK.face - 0.11), M.frame),
      // The floor: the spine, its small foot, and the rear beam with a foot at each end.
      tube(0.07, V(0, 0.035, REAR.z), V(0, 0.035, FRONT_FOOT.z), M.frame),
      pad(FRONT_FOOT.width, 0.03, FRONT_FOOT.depth, M.frame, V(0, 0.015, FRONT_FOOT.z), { soft: 0.006 }),
      tube(0.07, V(-REAR.out, 0.035, REAR.z), V(REAR.out, 0.035, REAR.z), M.frame),
      ...sides.flatMap((s) => [
        pad(0.12, 0.03, 0.2, M.frame, V(s * REAR.out, 0.015, REAR.z), { soft: 0.006 }),
        // A brace curving out and down from the rear upright to the beam's end.
        bent(`pec_deck_brace:${s}`, [
          V(s * 0.06, BRACE_Y, REAR.z), V(s * 0.14, BRACE_Y - 0.02, REAR.z), V(s * 0.3, 0.24, REAR.z), V(s * REAR.out, 0.05, REAR.z),
        ], 0.03, M.frame),
      ]),
      // The tower with the stack in it, and the arch from its back up over and forward to the unit.
      ...[-1, 1].map((d) => pad(TOWER.width, FRAME_TOP, TOWER.depth, M.frame, V(0, FRAME_TOP / 2, STACK_Z + d * TOWER.spread), { soft: 0.01 })),
      pad(TOWER.width, 0.07, 2 * TOWER.spread + TOWER.depth, M.frame, V(0, FRAME_TOP, STACK_Z), { soft: 0.01 }),
      bent('pec_deck_arch', [
        V(0, FRAME_TOP, REAR.z), V(0, 1.8, REAR.z), V(0, 1.905, REAR.z + 0.05), V(0, archY, REAR.z + 0.17), V(0, archY, -0.3), V(0, archY, AXIS.z),
      ], ARCH_RADIUS, M.frame),
      // The unit: two flat plates on the axles, a bearing on each plate, a tab on the upper one, and a
      // rail from the lower plate back to the tower's top.
      ...PLATES.map((y) => pad(2 * AXIS.x + 0.14, 0.014, 0.14, M.frame, V(0, y, AXIS.z), { soft: 0.003 })),
      tube(0.05, V(0, PLATES[0], AXIS.z), V(0, PLATES[0], STACK_Z + TOWER.spread), M.frame),
      ...sides.flatMap((s) => {
        const on = (y) => V(s * AXIS.x, y, AXIS.z);
        return [
          ...pivots[s].fixed,
          rod(0.014, on(PLATES[0]), on(PLATES[1]), M.metal),
          rod(0.04, on(PLATES[0]), on(PLATES[0] + 0.05), M.frame),
          rod(0.04, on(PLATES[1] - 0.05), on(PLATES[1]), M.frame),
          pad(0.06, 0.09, 0.012, M.frame, on(PLATES[1] + 0.05), { soft: 0.003, solid: false }),
        ];
      }),
      ...pulley(wheel, M),
      stack,
      drop,
    ];
    // Each arm at its rest: the lever out from its axis, a round pin at its end, the drop arm down to
    // its foot, the vertical grip bent out of it one way and the short grip out of it the other.
    const arms = sides.map((s) => {
      const end = axisOf(s).addScaledVector(leverDir(s, rest), LEVER);
      const ahead = aheadOf(s, rest);
      const foot = end.clone().setY(FOOT_Y);
      const top = end.clone().setY(GRIP_Y + 0.12);
      // The lever and the drop arm are one square tube bent at the pin, cut on the bisector. A box
      // round both would stand in the body's way, so a tube inside the drop arm is what the body meets.
      const arm = mitered([axisOf(s), end, foot], 0.045, M.frame, { solid: false });
      const dropCore = tube(0.04, end, foot, M.frame);
      // One bent tube out of the drop arm at the top, along, and down the grip to its free foot. Its
      // shape is keyed by the facing, as the arm rests at a different angle in each.
      const off = (along, y) => end.clone().addScaledVector(ahead, along).setY(y);
      const handle = bent(`pec_deck_handle:${m}:${s}`, [
        off(0, top.y), off(0.04, top.y), off(GRIP_OFF, top.y - 0.03), off(GRIP_OFF, GRIP_Y), off(GRIP_OFF, GRIP_Y - 0.09),
      ], GRIP_RADIUS - 0.004, M.metal);
      const sleeve = rod(GRIP_RADIUS, off(GRIP_OFF, GRIP_Y - 0.08), off(GRIP_OFF, GRIP_Y + 0.08), M.vinyl, { held: true });
      const short = rod(GRIP_RADIUS, off(0, SHORT_Y), off(-GRIP_OFF - SHORT_LENGTH / 2 + 0.015, SHORT_Y), M.vinyl, { held: true });
      // The pin is a cylinder across the lever's end, centred on the drop arm, which hangs from it.
      const across = V(0, 1, 0).cross(end.clone().sub(axisOf(s))).setY(0).normalize().multiplyScalar(0.03);
      const pin = part(rodGeometry(0.03, 20), M.pulley);
      span(pin, end.clone().sub(across), end.clone().add(across));
      // The arm's foot is a dome under the handle.
      const dome = knob(M, foot, 0.022);
      dome.material = M.frame;
      const nodes = [pivots[s].housing, arm, dropCore, pin, dome, handle, sleeve, short];
      return { s, nodes, base: nodes.map((n) => ({ p: n.position.clone(), q: n.quaternion.clone() })) };
    });
    let posed = false;
    /** Lays an arm swept `value` from its rest, in the kit's own frame. */
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
          lift(raised) {
            // A machine no one is on has its stack sat down.
            const l = posed ? raised : raised - HELD;
            weights.lift(l);
            span(drop, V(0, wheel.y, STACK_Z), weights.top(l).setZ(STACK_Z));
          },
        },
      },
      stand(g) {
        if (m === 'rear') g.rotation.y = Math.PI;
      },
      drive(P) {
        // Without a pose the arms rest.
        posed = Boolean(P.linkage);
        const value = P.linkage?.value ?? 0;
        const named = (s) => (s < 0 ? 'left' : 'right');
        const values = Object.fromEntries(sides.map((s) => [named(s), sweptBy(options, s, value)]));
        arms.forEach((a) => swing(a, values[named(a.s)]));
        return {
          joints: Object.fromEntries(arms.map(({ s }) => [named(s), { value: values[named(s)], origin: at(axisOf(s)) }])),
          points: {
            ...Object.fromEntries(arms.map(({ s }) => [`${named(s)}${held}`, [at(gripAt(s, rest + values[named(s)], m))]])),
            backPad: [at(back.at)],
            seatTop: [at(V(0, seat[0] - 0.0375, seat[1]))],
          },
        };
      },
    };
  },
});
