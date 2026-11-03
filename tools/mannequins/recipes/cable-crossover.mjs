// A free-standing dual adjustable-pulley machine (refs/cable_crossover/ref.md): two towers 2210 mm
// apart, joined at the top by a crossmember that overhangs them, each tower a front post with a
// rail up its face, a weight stack behind it between its own uprights, and a floor beam under both.
// A pulley carriage slides on the rail to any of 16 holes, 132 mm apart, the lowest with its pin
// 350 mm up. Its pulley swivels about the rail's own line, 180° in all, and the cable leaves the
// lower of its two wheels: it comes down the rail from a wheel under the top beam, which it reached
// from the stack's, and the handle on its free end is the hands'.
//
// The user stands in front of the towers (z ahead), facing away from them. The tower at +x is the
// left one (`_l`), as the mannequin's left hand is. A tower's pieces are built in its own frame,
// the rail on its centre line at z = 0 and its stack behind.
//
// Each tower has three bodies: its carriage, whose joint is how far it stands above the lowest
// hole; its swivel, turned about the vertical to face the cable; and its stack, whose joint is how
// far the cable is drawn out, the stack rising half as far (2:1). Each handle is a free body,
// carried by a hand, or hanging from its cable's end where no pose holds it.
import * as THREE from 'three';
import { recipe, free, prismatic, revolute } from '../recipe.mjs';
import {
  STIRRUP, WHEEL, floorRail, knob, nameAll, pad, pulley, rod, span, stackFrame, stirrup, tube, turnTo, turnedPart, weightStack, wheelGeometry, wire,
} from '../parts.mjs';
import { V, X, UP } from '../rig.mjs';

/** The towers' centre lines, 2210 mm apart. */
const TOWER_X = 1.105;
/** The towers' height, to the crossmember's top, and the crossmember's length. */
const HEIGHT = 2.731;
const BRIDGE = 2.724;
const POST = 0.076;
const BEAM_Y = HEIGHT - POST / 2;
/** The rail's front, and the stack's place behind it. */
const RAIL = 0.051;
const RAIL_Z = POST / 2 + RAIL / 2;
const STACK_Z = -0.5;
/** The 16 carriage positions: the first pin's height, and the hole pitch. */
const FIRST = 0.35;
const PITCH = 0.132;
const POSITIONS = 16;
const TRAVEL = (POSITIONS - 1) * PITCH;
/** The swivel's axis, ahead of the rail, and where its two wheels sit about the pin. */
const SWIVEL_Z = 0.19;
const WHEELS = 0.085;
/** The swivel's two wheels are 152 mm across, side by side in one figure-eight housing. */
const SWIVEL_WHEEL = 0.076;
/** The cable's travel, 108 in, and the stack's rise for it (2:1). */
const CABLE = 2.743;
const RATIO = 0.5;
/** How far the cable hangs below the lower wheel with the handle on it, no hand holding. */
const HANG = 0.45;
/** The top wheels' axle height. */
const TOP_Y = BEAM_Y - POST / 2 - WHEEL - 0.04;

/** A cable's radius. */
const WIRE = 0.0045;

const sides = [{ k: 'l', s: 1 }, { k: 'r', s: -1 }];

export default recipe({
  name: 'cable_crossover',
  // Shown alone in the studio until a pose uses it: three-quarter from the front and the left.
  standsAlone: { camera: { target: [0, 1.25, 0], halfHeight: 1.75, azimuth: -35, elevation: 8 } },
  bodies: (o) => ({
    ...Object.fromEntries(sides.flatMap(({ k }) => [
      [`carriage_${k}`, prismatic({ axis: UP, limits: [0, TRAVEL] })],
      [`swivel_${k}`, revolute({ axis: UP, limits: [-Math.PI / 2, Math.PI / 2] })],
      [`stack_${k}`, prismatic({ axis: UP, limits: [0, CABLE] })],
    ])),
    ...Object.fromEntries(handsOf(o).map(({ k }) => [`handle_${k}`, free()])),
  }),
  // The hands hold the handles; a free hand may steady itself on a carriage's fixed grip bar.
  contacts: (o) => [
    ...handsOf(o).map(({ k }) => ({ body: `handle_${k}`, point: `handle_${k}`, landmark: 'hands' })),
    ...sides.map(({ k }) => ({ body: `carriage_${k}`, point: `steady_${k}`, landmark: 'hands' })),
  ],
  stacks: sides.map(({ k }) => ({ joint: `stack_${k}`, ratio: RATIO })),
  // A pose sets both carriages (or each) at the hole its exercise wants, and the machine's placard
  // is the fit rule.
  fit: [{
    sets: 'position',
    rule: 'the carriage at the hole where the cable meets the hand level with its start: hole 1 for a curl or a kickback, 4 to 6 at the thigh, 10 at the standing shoulder (7 seated), 15 or 16 for a crossover or a pushdown',
  }],
  build({ position = 10, hands = [1, -1] } = {}, M) {
    const holes = Array.isArray(position) ? position : [position, position];
    for (const h of holes) if (!(Number.isInteger(h) && h >= 1 && h <= POSITIONS)) throw new Error(`The cable crossover has no position ${h}; its carriage has holes 1 to ${POSITIONS}`);
    const towers = sides.map(({ k, s }, i) => buildTower(k, s, (holes[i] - 1) * PITCH, M));
    // The crossmember rests on the front posts' tops and runs past them, centred on the machine.
    const bridge = tube(POST, V(-BRIDGE / 2, BEAM_Y, 0), V(BRIDGE / 2, BEAM_Y, 0), M.frame);
    bridge.userData.bridge = true;
    const handles = Object.fromEntries(handsOf({ hands }).map(({ k }) => [k, stirrup(M, `cable_handle_${k}`)]));
    const cables = Object.fromEntries(sides.map(({ k }) => [k, wire(M)]));
    // Each cable's far end: the handle's ring, in the kit's frame.
    const rings = {};
    const ringOf = (handle) => { handle.updateMatrix(); return V(0, STIRRUP, 0).applyMatrix4(handle.matrix); };
    const hanging = (t) => t.exit().add(V(0, -HANG, 0));
    // Lays the cable from the lower wheel to the handle's ring (a hanging handle's, with none held).
    const lay = (tower) => {
      const exit = tower.exit();
      tower.rise();
      span(cables[tower.k], rings[tower.k] ?? hanging(tower), exit);
    };
    return {
      parts: [...towers.map((t) => t.group), bridge, ...towers.flatMap((t) => t.wires), ...Object.values(handles), ...Object.values(cables)],
      // Each tower's stack is raised by its own lift, not moved as a body.
      bodies: Object.fromEntries(towers.flatMap((t) => [[`carriage_${t.k}`, [t.carriage]], [`swivel_${t.k}`, [t.swivel]], [`stack_${t.k}`, []]])),
      cables: towers.map((t) => ({ cable: cables[t.k], hook: () => rings[t.k] ?? hanging(t) })),
      stacks: Object.fromEntries(towers.map((t) => [`stack_${t.k}`, t])),
      follow() {
        for (const t of towers) lay(t);
      },
      drive(P) {
        const joints = {};
        const points = {};
        for (const tower of towers) {
          const { k, s } = tower;
          tower.carriage.position.y = tower.at;
          const exit = tower.exit();
          const held = handles[k];
          let hook;
          if (held && P.hand) {
            // The handle in the hand, turned toward the pulley as square to the grip allows: the cable
            // runs from its ring, wherever that comes to.
            held.position.copy(P.hand(s));
            turnTo(held, P.held?.[s] ?? X, exit.clone().sub(P.hand(s)));
            points[`handle_${k}`] = [P.hand(s).clone()];
            hook = ringOf(held);
          } else {
            // Hanging on the cable from the lower wheel's front, its grip off the floor.
            const ring = Math.max(exit.y - HANG, 0.05 + STIRRUP);
            const hang = V(exit.x, ring - STIRRUP, exit.z + SWIVEL_WHEEL);
            if (held) {
              held.position.copy(hang);
              held.quaternion.identity();
              points[`handle_${k}`] = [hang.clone()];
            }
            hook = V(hang.x, ring, hang.z);
          }
          rings[k] = hook;
          const aim = hook.clone().sub(exit);
          const turn = Math.max(-Math.PI / 2, Math.min(Math.PI / 2, Math.atan2(aim.x, aim.z)));
          tower.swivel.rotation.y = turn;
          lay(tower);
          // The cable drawn out past the length that lets a hanging handle hang.
          const drawn = held && P.hand ? Math.max(0, hook.distanceTo(exit) - Math.min(HANG, exit.y - 0.05 - STIRRUP)) : 0;
          joints[`carriage_${k}`] = { value: tower.at, origin: tower.group.position.clone() };
          joints[`swivel_${k}`] = { value: turn, origin: V(tower.group.position.x, 0, SWIVEL_Z) };
          joints[`stack_${k}`] = { value: drawn, origin: V(tower.group.position.x, 0, STACK_Z) };
          points[`steady_${k}`] = [tower.steady()];
        }
        return { joints, points };
      },
    };
  },
});

/** The hands that hold a handle: both, or the one named (1 the left). */
function handsOf({ hands = [1, -1] } = {}) {
  return sides.filter(({ s }) => hands.includes(s));
}

/** One tower, side `k` (`s` its sign in x), its carriage `at` metres above the lowest hole. */
function buildTower(k, s, at, M) {
  const tower = new THREE.Group();
  tower.name = `cable_crossover_${k}`;
  tower.position.x = s * TOWER_X;
  const top = BEAM_Y;
  // The base: a floor beam from a front foot under the rail to behind the stack, a cross beam under
  // the foot and one under the stack, the post and the stack's uprights standing on them.
  const rear = STACK_Z - 0.17;
  const front = 0.3;
  const base = [
    ...floorRail(V(0, 0.035, rear), V(0, 0.035, front), M),
    ...floorRail(V(-0.4, 0.035, front - 0.035), V(0.4, 0.035, front - 0.035), M),
    ...floorRail(V(-0.4, 0.035, STACK_Z), V(0.4, 0.035, STACK_Z), M),
  ];
  const post = tube(POST, V(0, 0.07, 0), V(0, top - POST / 2, 0), M.frame);
  post.userData.post = true;
  const frame = [
    post,
    tube(RAIL, V(0, 0.14, RAIL_Z), V(0, top - POST / 2 - 0.05, RAIL_Z), M.metal),
    // The uprights end under the top beam, which rests on their crossbar.
    ...stackFrame(STACK_Z, top - POST / 2 - 0.035, M),
    // The top beam, from the front post back to the stack's crossbar.
    tube(POST, V(0, top, 0), V(0, top, STACK_Z), M.frame),
  ];
  // The wheels under the top beam: the first over the rail, the second over the stack.
  const first = V(0, TOP_Y, SWIVEL_Z - WHEEL);
  const second = V(0, TOP_Y, STACK_Z + WHEEL);
  const wheels = [...pulley(first, M), ...pulley(second, M)];
  // The cable's runs, in the kit's frame since the carriage and the stack move them: over the top
  // wheels, up the spindle, and down to the stack's head.
  const x = s * TOWER_X;
  const over = rod(WIRE, V(x, TOP_Y + WHEEL, first.z), V(x, TOP_Y + WHEEL, second.z), M.metal);
  const weights = weightStack(V(0, 0, STACK_Z), M, { rodTop: top - POST / 2 - 0.2, toward: 1 });
  const drop = wire(M);
  const rise = wire(M);

  // The carriage, in the frame of the lowest hole: a block round the rail, an arm out to the swivel,
  // a pull-pin, and the fixed grip bar beside the pin.
  const pin = FIRST;
  const carriage = new THREE.Group();
  const arm = tube(0.03, V(0, pin, RAIL_Z + 0.04), V(0, pin, SWIVEL_Z), M.frame);
  arm.userData.pin = k;
  const bar = rod(0.0155, V(0.125, pin - 0.12, 0.09), V(0.125, pin + 0.12, 0.09), M.vinyl, { held: true });
  carriage.add(
    pad(0.15, 0.22, 0.1, M.frame, V(0, pin, RAIL_Z), { soft: 0.008 }),
    arm,
    rod(0.006, V(0.075, pin - 0.07, RAIL_Z), V(0.1, pin - 0.07, RAIL_Z), M.metal),
    knob(M, V(0.11, pin - 0.07, RAIL_Z)),
    rod(0.008, V(0.125, pin - 0.12, 0.06), V(0.125, pin - 0.12, 0.09), M.metal),
    rod(0.008, V(0.125, pin + 0.12, 0.06), V(0.125, pin + 0.12, 0.09), M.metal),
    bar,
  );
  // The swivel, round its spindle: two wheels in their housing, one above the pin and one below.
  const swivel = new THREE.Group();
  swivel.position.set(0, pin, SWIVEL_Z);
  swivel.add(
    rod(0.008, V(-0.024, 0, 0), V(0.024, 0, 0), M.metal),
    ...[WHEELS, -WHEELS].map((y) => turnedPart(wheelGeometry(SWIVEL_WHEEL, 0.022), M.pulley, V(0, y, 0), { solid: false })),
    ...[-1, 1].map((x) => pad(0.006, 2 * WHEELS + 2 * SWIVEL_WHEEL, 2 * SWIVEL_WHEEL, M.pulley, V(x * 0.017, 0, 0), { soft: 0.05, solid: false })),
  );
  carriage.add(swivel);
  tower.add(...base, ...frame, ...wheels, ...weights.parts, carriage);
  nameAll(tower, tower.name);

  const api = {
    k,
    s,
    at,
    group: tower,
    carriage,
    swivel,
    wires: [over, drop, rise],
    /** The lower wheel's middle, where the cable leaves, in the kit's frame. */
    exit: () => V(x, pin + carriage.position.y - WHEELS, SWIVEL_Z),
    /** Lays the cable up the spindle to the first wheel. */
    rise() {
      span(rise, V(x, pin + carriage.position.y + WHEELS, SWIVEL_Z), V(x, TOP_Y, SWIVEL_Z));
    },
    steady: () => V(x + 0.125, pin + carriage.position.y, 0.09),
    // Raises the stack `l` metres, the cable down to its head with it.
    lift(l) {
      weights.lift(l);
      span(drop, V(x, TOP_Y, STACK_Z), weights.top(l).add(V(x, 0, 0)));
    },
  };
  return api;
}
