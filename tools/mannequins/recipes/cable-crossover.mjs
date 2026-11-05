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
// the rail on its centre line and its stack behind. `at` [x, z] is where the middle of the towers'
// line stands, since the body stays at the origin: back for a lifter a step ahead of them, to a
// side for one beside a tower. With `crossed`, each hand takes the far tower's cable. With `faces`
// each tower turns round where it stands, for a lifter facing the machine, its pulley still toward
// them and left still left. `turn` swings the whole machine that many degrees about the body, as
// if the lifter stood turned the other way. `attachment` is `handle` by default, a stirrup per hand, or one thing on
// the cable of the `tower` named (1 the left): a `rope` or a `bar` in both hands, or a `cuff` on the
// `ankle` named. A rope with one of `hands` is a single strand for it, and a `reversed` one runs
// through the fists the other way, its ends past the index fingers.
//
// Each tower has three bodies: its carriage, whose joint is how far it stands above the lowest
// hole; its swivel, turned about the vertical to face the cable; and its stack, whose joint is how
// far the cable is drawn out, the stack rising half as far (2:1). Each handle is a free body,
// carried by a hand, or hanging from its cable's end where no pose holds it.
import * as THREE from 'three';
import { recipe, free, prismatic, revolute, HELD } from '../recipe.mjs';
import {
  ATTACHMENTS, STIRRUP, WHEEL, clevis, floorRail, knob, nameAll, pad, part, pulley, rod, rodGeometry, ropeParts, span, stackFrame, stirrup, tube, turnTo, turnedPart, weightStack, wheelGeometry, wire,
} from '../parts.mjs';
import { V, X, UP, rad } from '../rig.mjs';

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
/** How far a rope's ring sits from the hands' middle, toward the pulley. */
const ROPE_RING = 0.18;
/** A cuff's width, how far up the shin from the ankle its middle sits, and how far off the leg its ring. */
const CUFF = 0.06;
const CUFF_UP = 0.08;
const CUFF_RING = 0.075;

const sides = [{ k: 'l', s: 1 }, { k: 'r', s: -1 }];

export default recipe({
  name: 'cable_crossover',
  bodies: (o) => ({
    ...Object.fromEntries(sides.flatMap(({ k }) => [
      [`carriage_${k}`, prismatic({ axis: UP, limits: [0, TRAVEL] })],
      [`swivel_${k}`, revolute({ axis: UP, limits: [-Math.PI / 2, Math.PI / 2] })],
      [`stack_${k}`, prismatic({ axis: UP, limits: [0, CABLE] })],
    ])),
    ...(isSingle(o) ? { attachment: free() } : Object.fromEntries(handsOf(o).map(({ k }) => [`handle_${k}`, free()]))),
  }),
  // The hands hold the handles or the one attachment, or the ankle wears the cuff; a free hand may
  // steady itself on a carriage's fixed grip bar.
  contacts: (o) => [
    ...(isSingle(o)
      ? [{ body: 'attachment', point: 'attachment', landmark: o.attachment === 'cuff' ? 'feet' : 'hands' }]
      : handsOf(o).map(({ k }) => ({ body: `handle_${k}`, point: `handle_${k}`, landmark: 'hands' }))),
    ...sides.map(({ k }) => ({ body: `carriage_${k}`, point: `steady_${k}`, landmark: 'hands', optional: true })),
  ],
  stacks: sides.map(({ k }) => ({ joint: `stack_${k}`, ratio: RATIO })),
  // A pose sets both carriages (or each) at the hole its exercise wants, and the machine's placard
  // is the fit rule.
  fit: [{
    sets: 'position',
    rule: 'the carriage at the hole where the cable meets the hand level with its start: hole 1 for a curl or a glute kickback, 2 or 3 for a shrug-in, 4 to 6 from the thigh to the hip (the high lateral raise cable is at hole 6), 9 or 10 at the chest and shoulder (7 seated), 12 for a crossover, 15 or 16 for a pushdown',
  }],
  build({ position = 10, hands = [1, -1], at = [0, 0], crossed = false, faces = false, turn = 0, attachment = 'handle', tower: carrier = 1, ankle = 1, reversed = false } = {}, M) {
    const holes = Array.isArray(position) ? position : [position, position];
    for (const h of holes) if (!(Number.isInteger(h) && h >= 1 && h <= POSITIONS)) throw new Error(`The cable crossover has no position ${h}; its carriage has holes 1 to ${POSITIONS}`);
    const yaw = rad(turn);
    const towers = sides.map(({ k, s }, i) => buildTower(k, s, (holes[i] - 1) * PITCH, { at, faces, yaw }, M));
    /** The side of the hand on the tower at side `s`. */
    const handOn = (s) => (crossed ? -s : s);
    const single = isSingle({ attachment }) ? attached(attachment, { ankle, reversed, hands }, M) : null;
    // A stack on no working cable stays down, so it isn't a working one.
    for (const t of towers) if (single ? t.s !== carrier : !hands.includes(handOn(t.s))) t.idle();
    // The crossmember rests on the front posts' tops and runs past them, centred on the machine.
    const bridge = tube(POST, swung(yaw, at[0] - BRIDGE / 2, BEAM_Y, at[1]), swung(yaw, at[0] + BRIDGE / 2, BEAM_Y, at[1]), M.frame);
    bridge.userData.bridge = true;
    const handles = single ? {} : Object.fromEntries(handsOf({ hands }).map(({ k }) => [k, stirrup(M, `cable_handle_${k}`)]));
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
      parts: [...towers.map((t) => t.group), bridge, ...towers.flatMap((t) => t.wires), ...Object.values(handles), ...(single?.parts ?? []), ...Object.values(cables)],
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
          tower.carriage.position.y = tower.raised;
          const exit = tower.exit();
          // The hand on this tower's cable, and its side's name.
          const { k: h, s: side } = sides.find((o) => o.s === handOn(s));
          const held = handles[h];
          const carrying = single && s === carrier;
          let hook;
          if (carrying && single.holds(P)) {
            const placed = single.place(P, exit);
            hook = placed.hook;
            points.attachment = placed.points;
          } else if (held && P.hand) {
            // The handle in the hand, turned toward the pulley as square to the grip allows: the cable
            // runs from its ring, wherever that comes to.
            held.position.copy(P.hand(side));
            turnTo(held, P.held?.[side] ?? X, exit.clone().sub(P.hand(side)));
            points[`handle_${h}`] = [P.hand(side).clone()];
            hook = ringOf(held);
          } else {
            // Hanging on the cable from the lower wheel's front, its grip off the floor.
            const ring = Math.max(exit.y - HANG, 0.05 + STIRRUP);
            const hang = exit.clone().setY(ring - STIRRUP).add(tower.ahead(SWIVEL_WHEEL));
            if (held) {
              held.position.copy(hang);
              held.quaternion.identity();
              points[`handle_${h}`] = [hang.clone()];
            }
            hook = V(hang.x, ring, hang.z);
            if (carrying) ({ hook, points: points.attachment } = single.rest(hang, exit));
          }
          rings[k] = hook;
          const aim = hook.clone().sub(exit).applyAxisAngle(UP, -yaw);
          const swivelled = Math.max(-Math.PI / 2, Math.min(Math.PI / 2, Math.atan2(tower.dz * aim.x, tower.dz * aim.z)));
          tower.swivel.rotation.y = swivelled;
          lay(tower);
          // The cable drawn out past the length that lets a hanging handle hang.
          const pulled = carrying ? single.holds(P) : held && P.hand;
          const drawn = pulled ? Math.max(0, hook.distanceTo(exit) - Math.min(HANG, exit.y - 0.05 - STIRRUP)) : 0;
          joints[`carriage_${k}`] = { value: tower.raised, origin: tower.group.position.clone() };
          joints[`swivel_${k}`] = { value: swivelled, origin: tower.at(SWIVEL_Z) };
          joints[`stack_${k}`] = { value: drawn, origin: tower.at(STACK_Z) };
          points[`steady_${k}`] = [tower.steady()];
        }
        return { joints, points };
      },
    };
  },
});

/** The point [x, y, z] of a machine standing square, with the whole machine turned `yaw` about the body. */
const swung = (yaw, x, y, z) => V(x, y, z).applyAxisAngle(UP, yaw);

/** Whether one tower's cable carries a single attachment, rather than each hand its own handle. */
const isSingle = ({ attachment = 'handle' } = {}) => attachment !== 'handle';

/**
 * The one thing on a tower's cable: its parts, whether a pose `holds` it, and `place(P, exit)` and
 * `rest(hang, exit)`, each giving its `hook` for the cable and the `points` it meets the body at.
 */
function attached(kind, { ankle, reversed, hands }, M) {
  const toward = (from, exit) => exit.clone().sub(from).normalize();
  const held = (P) => hands.map((s) => P.hand(s).clone());
  const midOf = (P) => held(P).reduce((sum, p) => sum.add(p), V(0, 0, 0)).multiplyScalar(1 / hands.length);
  const shapes = {
    rope() {
      const rope = ropeParts(M, { reversed, hands: [-1, 1].filter((s) => hands.includes(s)) });
      return {
        parts: rope.parts,
        holds: (P) => Boolean(P.hand),
        place(P, exit) {
          const mid = midOf(P);
          const hook = mid.clone().addScaledVector(toward(mid, exit), ROPE_RING);
          rope.lay(hook, P);
          return { hook, points: held(P) };
        },
      };
    },
    // A straight bar, turned toward the pulley as square to the hands' line allows.
    bar() {
      const { parts, hook: above } = ATTACHMENTS.straight_bar(M);
      const bar = new THREE.Group();
      bar.add(...parts);
      nameAll(bar, 'cable_bar');
      return {
        parts: [bar],
        holds: (P) => Boolean(P.hand),
        place(P, exit) {
          const mid = midOf(P);
          bar.position.copy(mid);
          turnTo(bar, P.hand(1).clone().sub(P.hand(-1)), exit.clone().sub(mid));
          bar.updateMatrix();
          return { hook: V(0, above, 0).applyMatrix4(bar.matrix), points: held(P) };
        },
      };
    },
    // A band round the leg above the ankle, the ring on the side toward the pulley.
    cuff() {
      const band = part(rodGeometry(0.055, 14), M.vinyl);
      const ring = clevis(M, 0);
      return {
        parts: [band, ring],
        holds: (P) => Boolean(P.ankles),
        place(P, exit) {
          const foot = P.ankles[ankle < 0 ? 0 : 1];
          // Up the shin: a lifted foot's top runs along it, and a planted shin stands near enough upright.
          const up = (P.foot?.(ankle)?.top ?? UP).clone().normalize();
          const at = foot.clone().addScaledVector(up, CUFF_UP);
          span(band, at.clone().addScaledVector(up, -CUFF / 2), at.clone().addScaledVector(up, CUFF / 2));
          const away = toward(at, exit);
          const hook = at.clone().addScaledVector(away.addScaledVector(up, -away.dot(up)).normalize(), CUFF_RING);
          ring.position.copy(hook);
          return { hook, points: [at] };
        },
      };
    },
  };
  if (!shapes[kind]) throw new Error(`No cable attachment "${kind}" on the crossover`);
  const made = shapes[kind]();
  const rest = (hang, exit) => made.place({
    hand: (s) => hang.clone().add(V(s * 0.12, -0.1, 0)), ankles: [hang, hang],
  }, exit);
  return { ...made, rest };
}

/** The hands that hold a handle: both, or the one named (1 the left). */
function handsOf({ hands = [1, -1] } = {}) {
  return sides.filter(({ s }) => hands.includes(s));
}

/** One tower, side `k` (`s` its sign in x), its carriage `raised` metres above the lowest hole, the machine standing at `at`, turned round if it `faces` the lifter. */
function buildTower(k, s, raised, { at: [atX, atZ], faces, yaw }, M) {
  // Which way the tower's own z runs in the kit's frame.
  const dz = faces ? -1 : 1;
  const tower = new THREE.Group();
  tower.name = `cable_crossover_${k}`;
  tower.position.copy(swung(yaw, atX + s * TOWER_X, 0, atZ));
  tower.rotation.y = yaw + (faces ? Math.PI : 0);
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
  const x = atX + s * TOWER_X;
  const over = rod(WIRE, swung(yaw, x, TOP_Y + WHEEL, atZ + dz * first.z), swung(yaw, x, TOP_Y + WHEEL, atZ + dz * second.z), M.metal);
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
    raised,
    dz,
    group: tower,
    carriage,
    swivel,
    wires: [over, drop, rise],
    /** The lower wheel's middle, where the cable leaves, in the kit's frame. */
    exit: () => swung(yaw, x, pin + carriage.position.y - WHEELS, atZ + dz * SWIVEL_Z),
    /** The way the tower's own front faces, `by` metres of it, and the floor `z` along it from the rail. */
    ahead: (by) => swung(yaw, 0, 0, dz * by),
    at: (z) => swung(yaw, x, 0, atZ + dz * z),
    /** Lays the cable up the spindle to the first wheel. */
    rise() {
      span(rise, swung(yaw, x, pin + carriage.position.y + WHEELS, atZ + dz * SWIVEL_Z), swung(yaw, x, TOP_Y, atZ + dz * SWIVEL_Z));
    },
    steady: () => swung(yaw, x + dz * 0.125, pin + carriage.position.y, atZ + dz * 0.09),
    idle: weights.idle,
    // Raises the stack `l` metres, less the hold the recipe format adds (`HELD`): the stack sits
    // down whole with no cable drawn, and rises half as far as the cable is drawn after that.
    lift(l) {
      weights.lift(Math.max(0, l - HELD));
      const head = weights.top(l);
      span(drop, swung(yaw, x, TOP_Y, atZ + dz * STACK_Z), swung(yaw, x + dz * head.x, head.y, atZ + dz * head.z));
    },
  };
  return api;
}
