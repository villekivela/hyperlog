// A bolt-together power rack (refs/power_rack/ref.md): four 3 in square uprights 108 in tall,
// each on a 7 in base plate, the front pair 43 in apart inside and the rear pair 24 in behind them
// inside, each side joined by a crossmember near the top and one near the floor and a pin pipe safety
// between them. Nothing else joins the sides but a back crossmember (not in the photos) and the supplied pull-up bars, a skinny one and a fat one
// side to side at the top, which a multi-grip bar replaces. The lifter faces the rack: the front uprights' face
// is 230 mm ahead of the origin's z, the hang line.
//
// Options: `pullupBar` (on by default) bolts a multi-grip pull-up bar to the front crossmember's face:
// a 38 in plate, outer prongs 813 mm apart and inner ones 406 mm, a V of about 45° to the inner
// ones, and a cross tube 230 mm out joining each inner prong to its outer one and out past it, its
// ends bent back toward the plate; nothing joins the inner prongs but the V. Its grips are level with
// the crossmember's middle. `dipHandles` (on by default) hangs a pair of dip handles on the +x front upright,
// from a bracket round it: a stem 3 in square out from its face, a crosshead across the stem's end, and a
// handle either side of it, running on out (451 mm apart at the crosshead, 630 at the tips, 440 mm
// long), level at 1.30 m. The lifter stands between the handles with their back to the upright, facing
// out along them. `handles` names the pair of grips a pose holds; with no pose, every one the rack has.
// A pose on the dip handles turns the rack half round behind the lifter, who faces +z in every pose.
import { recipe } from '../recipe.mjs';
import { bent, holedTube, knob, pad, part, rod, rodGeometry, span, tube } from '../parts.mjs';
import { V } from '../rig.mjs';

const IN = 0.0254;
// The uprights: 3 in square, 108 in (9 ft) tall, the front pair 43 in apart inside and the rear 24 in
// behind the front inside, each on a 7 in plate.
const UPRIGHT = { w: 3 * IN, height: 108 * IN, inside: 43 * IN, depth: 24 * IN, plate: 7 * IN };
// The front crossmembers' face, ahead of the hang line.
const FRONT = 0.23;
const X = (UPRIGHT.inside + UPRIGHT.w) / 2;
const Z_FRONT = FRONT + UPRIGHT.w / 2;
const Z_REAR = Z_FRONT + UPRIGHT.depth + UPRIGHT.w;
// Everything bolts on at a hole: the front crossmember the multi-grip bar mounts to at the 91 in one, the side
// crossmembers at the 101 in and the 8 in ones, the pin pipe safeties at 24 in, the supplied bars level with the
// top side crossmembers.
const HIGH = 91 * IN;
const SIDE_HIGH = 101 * IN;
const LOW = 8 * IN;
// The uprights' 5/8 in holes, through all four faces: 2 in apart up to 11 in, 1 in apart through the
// bench and clean pull zone to 35 in (the zone's ends are estimated from the photos), 2 in apart above.
const range = (from, to, step) => Array.from({ length: Math.floor((to - from) / step + 1e-9) + 1 }, (_, i) => (from + i * step) * IN);
const UPRIGHT_HOLES = [...range(2, 10, 2), ...range(11, 35, 1), ...range(37, 107, 2)];
// A crossmember's holes: 2 in apart along its length, centred.
const beamHoles = (length) => Array.from({ length: Math.floor(length / (2 * IN)) }, (_, i) => length / 2 + (i - (Math.floor(length / (2 * IN)) - 1) / 2) * 2 * IN);
// A crossmember's end plate, welded square to its end and bolted to the upright's face: a little
// wider than the tube and taller, with a 5/8 in bolt through the holes 4 in above and below its middle,
// its hex head on the upright's far face.
const END = { thick: 0.008, width: 0.086, height: 0.22, bolt: { spacing: 4 * IN, radius: 0.0165, head: 0.011 } };
const PIPE = { height: 24 * IN, radius: 0.0254, pin: 0.006, out: 0.04 };
const BARS = [{ at: 0.25, radius: 0.0159 }, { at: 0.5, radius: 0.0254 }];

// The bar's grips: 1.25 in on the inner prongs and the cross tube, 2 in on the outer prongs, with a
// 3 in sphere on the inner prongs and a 4 in one on the outer, 230 mm of cross tube from the plate.
const GRIP = 0.01588;
const OUTER = { x: 0.4065, reach: 0.32, sphere: 0.0508, radius: 0.0254 };
const INNER = { x: 0.203, reach: 0.405, sphere: 0.0381 };
const CROSS = { z: FRONT - 0.23, half: 0.575, bend: 0.06 };
// Where the hands go (half the spacing): wide on the cross tube outside the outer prongs, standard
// on it between the prongs, close on the V's arms, and neutral on the inner prongs, a little out
// along them from the cross tube.
const HANDS = { wide: 0.48, standard: 0.305, close: 0.14, neutral: INNER.x, neutralOut: 0.08 };
// The dip handles: from the crosshead out to the tip, 47.6 mm across; the hands rest in the middle,
// the close grip nearer the crosshead, as near as the hips clear it. The stem and crosshead are 3 in
// square; the bracket is 254 mm tall.
const DIP = { height: 51 * IN + 0.01, base: 0.2255, tip: 0.315, length: 0.44, radius: 0.0238, hand: 0.5, close: 0.35, stem: 0.17, bracket: 0.254, crosshead: 0.55 };
const PLATE = { width: 0.965, height: 0.0762, thick: 0.0095 };
// Where the cross tube's straight run, which a hand can close on, ends and its bend starts.
const CROSS_RUN = 0.54;

const crossZ = FRONT - DIP.stem;
const dipBase = (s) => V(X + s * DIP.base, DIP.height, crossZ);
const dipTip = (s) => V(X + s * DIP.tip, DIP.height, crossZ - DIP.length);
const onDip = (t) => (s) => dipBase(s).lerp(dipTip(s), t);
const towardBase = (s) => dipBase(s).sub(dipTip(s)).normalize();
const onCross = (half) => (s) => V(s * half, HIGH, CROSS.z);
const toMiddle = (s) => V(-s, 0, 0);
// On the V's arm, as far along it from the middle as across.
const onV = (s) => V(s * HANDS.close, HIGH, FRONT - ((FRONT - CROSS.z) * HANDS.close) / INNER.x);
const towardApex = (s) => V(-s * INNER.x, 0, FRONT - CROSS.z).normalize();
/**
 * Each pair of grips, by the name a pose's `handles` gives it: `at(side)`, where that side's hand
 * holds, and `across(side)`, the way the part runs there, thumb first in the grip's own turn: along
 * the bar toward the middle, back toward the lifter down a prong, on toward the crosshead on a dip
 * handle. `on` is the add-on that carries the pair.
 */
export const GRIPS = {
  wide: { on: 'bar', at: onCross(HANDS.wide), across: toMiddle },
  standard: { on: 'bar', at: onCross(HANDS.standard), across: toMiddle },
  close: { on: 'bar', at: onV, across: towardApex },
  neutral: { on: 'bar', at: (s) => V(s * HANDS.neutral, HIGH, CROSS.z - HANDS.neutralOut), across: () => V(0, 0, -1) },
  dip: { on: 'dips', at: onDip(DIP.hand), across: towardBase },
  dipClose: { on: 'dips', at: onDip(DIP.close), across: towardBase },
};

// Where the rack stands for a pose on the dip handles: half a turn about the upright's line, the
// handles either side of x = 0 and the `dip` grips level with the origin.
const TURNED = { x: X, z: onDip(DIP.hand)(1).z };
const turned = (p) => V(TURNED.x - p.x, p.y, TURNED.z - p.z);

/** The pair of grips `handles` names, as the lifter meets it: the rack turned, for the dip handles, the sides swapped with it. */
export function gripsOf(handles) {
  const grip = GRIPS[handles];
  if (!grip) throw new Error(`No handles "${handles}": ${Object.keys(GRIPS).join(', ')}`);
  if (grip.on !== 'dips') return grip;
  return { ...grip, at: (s) => turned(grip.at(-s)), across: (s) => grip.across(-s).multiply(V(-1, 1, -1)) };
}

export default recipe({
  name: 'power_rack',
  bodies: {},
  // A pose holds one pair of grips, its `handles`; with no pose, any the rack has.
  contacts: ({ pullupBar = true, dipHandles = true, handles } = {}) => {
    if (handles) gripsOf(handles);
    const carried = { bar: pullupBar, dips: dipHandles };
    return Object.keys(GRIPS).filter((point) => carried[GRIPS[point].on] && (!handles || point === handles))
      .map((point) => ({ body: 'frame', point, landmark: 'hands' }));
  },
  build({ pullupBar = true, dipHandles = true, handles } = {}, M) {
    const sides = [-1, 1];
    // A crossmember between two uprights, from one's face to the other's, with its end plates against them.
    const crossmember = (A, B) => {
      const d = B.clone().sub(A).normalize();
      const along = Math.abs(d.x) > 0.5 ? 'x' : 'z';
      const from = A.clone().addScaledVector(d, UPRIGHT.w / 2 + END.thick);
      const to = B.clone().addScaledVector(d, -(UPRIGHT.w / 2 + END.thick));
      const plate = (at) => (along === 'x'
        ? pad(END.thick, END.height, END.width, M.frame, at, { soft: 0.001 })
        : pad(END.width, END.height, END.thick, M.frame, at, { soft: 0.001 }));
      // A bolt's hex head on the face of the upright at `centre` away from the beam, `out` being the way it points.
      const heads = (centre, out) => [-1, 1].map((k) => {
        const face = centre.clone().addScaledVector(out, UPRIGHT.w / 2).add(V(0, k * END.bolt.spacing, 0));
        const head = part(rodGeometry(END.bolt.radius, 6), M.metal);
        span(head, face, face.clone().addScaledVector(out, END.bolt.head));
        return head;
      });
      return [
        holedTube(UPRIGHT.w, from, to, beamHoles(from.distanceTo(to)), M.frame),
        plate(A.clone().addScaledVector(d, UPRIGHT.w / 2 + END.thick / 2)),
        plate(B.clone().addScaledVector(d, -(UPRIGHT.w / 2 + END.thick / 2))),
        ...heads(A, d.clone().negate()),
        ...heads(B, d),
      ];
    };
    const upright = (x, z) => [
      holedTube(UPRIGHT.w, V(x, 0.0095, z), V(x, UPRIGHT.height, z), UPRIGHT_HOLES.map((h) => h - 0.0095), M.frame, { capped: true }),
      pad(UPRIGHT.plate, 0.0095, UPRIGHT.plate, M.frame, V(x, 0.00475, z), { soft: 0.002, solid: false }),
    ];
    const frame = [
      ...sides.flatMap((s) => [
        ...upright(s * X, Z_FRONT),
        ...upright(s * X, Z_REAR),
        // The side crossmembers, between the front and rear uprights, high and low.
        ...crossmember(V(s * X, SIDE_HIGH, Z_FRONT), V(s * X, SIDE_HIGH, Z_REAR)),
        ...crossmember(V(s * X, LOW, Z_FRONT), V(s * X, LOW, Z_REAR)),
        // A pin pipe safety between the front and rear uprights, from one's face to the other's, black like
        // the frame, and its silver pin through the front upright, out past its outer face.
        rod(PIPE.radius, V(s * X, PIPE.height, Z_FRONT + UPRIGHT.w / 2), V(s * X, PIPE.height, Z_REAR - UPRIGHT.w / 2), M.frame),
        rod(PIPE.pin, V(s * (X - UPRIGHT.w / 2), PIPE.height, Z_FRONT), V(s * (X + UPRIGHT.w / 2 + PIPE.out), PIPE.height, Z_FRONT), M.metal),
      ]),
      // With no pull-up bar on it the rack has the supplied ones, side to side at the side crossmembers' height; a multi-grip bar instead
      // goes on a 43 in crossmember across the front, its top level with the uprights'.
      // A back crossmember between the rear uprights at the same hole, so the sides are tied together at the back too.
      ...crossmember(V(-X, HIGH, Z_REAR), V(X, HIGH, Z_REAR)),
      ...(pullupBar ? crossmember(V(-X, HIGH, Z_FRONT), V(X, HIGH, Z_FRONT))
        : BARS.map(({ at, radius }) => rod(radius, V(-X, SIDE_HIGH, Z_FRONT + at), V(X, SIDE_HIGH, Z_FRONT + at), M.frame, { held: true }))),
    ];
    // The bar: a plate on the front crossmember's face, the prongs out from it to a sphere each, the
    // V, and the cross tube from each inner prong out past its outer one.
    const prongEnd = (p) => V(0, HIGH, FRONT - p.reach);
    const bar = !pullupBar ? [] : [
      pad(PLATE.width, PLATE.height, PLATE.thick, M.frame, V(0, HIGH, FRONT - PLATE.thick / 2), { soft: 0.002 }),
      ...sides.flatMap((s) => [
        rod(OUTER.radius, V(s * OUTER.x, HIGH, FRONT), prongEnd(OUTER).setX(s * OUTER.x), M.frame, { held: true }),
        rod(GRIP, V(s * INNER.x, HIGH, FRONT), prongEnd(INNER).setX(s * INNER.x), M.frame, { held: true }),
        rod(GRIP, V(0, HIGH, FRONT), V(s * INNER.x, HIGH, CROSS.z), M.frame, { held: true }),
        // The cross tube from the inner prong out, straight where the hands hold it, then curving
        // back toward the plate at its end, capped round.
        rod(GRIP, V(s * INNER.x, HIGH, CROSS.z), V(s * CROSS_RUN, HIGH, CROSS.z), M.frame, { held: true }),
        bent(`pdip_cross:${s}`, [
          V(s * CROSS_RUN, HIGH, CROSS.z), V(s * (CROSS.half - 0.012), HIGH, CROSS.z + 0.004),
          V(s * CROSS.half, HIGH, CROSS.z + CROSS.bend / 2), V(s * CROSS.half, HIGH, CROSS.z + CROSS.bend),
        ], GRIP, M.frame, { held: true }),
        knob(M, V(s * CROSS.half, HIGH, CROSS.z + CROSS.bend), GRIP),
      ]),
      ...[OUTER, INNER].flatMap((p) => sides.map((s) => knob(M, prongEnd(p).setX(s * p.x), p.sphere))),
    ];
    // The dip handles on the right front upright: a bracket round it, a stem out from its face to a
    // crosshead, and a handle from each end of the crosshead splaying toward its tip.
    const dipBar = !dipHandles ? [] : [
      pad(UPRIGHT.w + 0.024, DIP.bracket, 0.012, M.frame, V(X, DIP.height - 0.01, FRONT - 0.006), { soft: 0.002 }),
      ...sides.map((s) => pad(0.012, DIP.bracket, UPRIGHT.w + 0.012, M.frame, V(X + s * (UPRIGHT.w / 2 + 0.006), DIP.height - 0.01, Z_FRONT), { soft: 0.002 })),
      tube(UPRIGHT.w, V(X, DIP.height - 0.01, FRONT), V(X, DIP.height - 0.01, crossZ), M.frame),
      tube(UPRIGHT.w, V(X - DIP.crosshead / 2, DIP.height - 0.01, crossZ), V(X + DIP.crosshead / 2, DIP.height - 0.01, crossZ), M.frame),
      ...sides.flatMap((s) => [rod(DIP.radius, dipBase(s), dipTip(s), M.vinyl, { held: true }), knob(M, dipTip(s), DIP.radius)]),
    ];
    const carried = { bar: pullupBar, dips: dipHandles };
    const points = Object.fromEntries(Object.keys(GRIPS).filter((name) => carried[GRIPS[name].on]).map((name) => {
      const held = name === handles ? gripsOf(name) : GRIPS[name];
      return [name, sides.map(held.at)];
    }));
    return {
      parts: [...frame, ...bar, ...dipBar],
      stand(g) {
        if (GRIPS[handles]?.on !== 'dips') return;
        g.rotation.y = Math.PI;
        g.position.set(TURNED.x, 0, TURNED.z);
      },
      drive() {
        return { points };
      },
    };
  },
});
