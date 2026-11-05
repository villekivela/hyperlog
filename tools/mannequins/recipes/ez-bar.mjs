// A short Olympic-sleeved curl bar with a zigzag shaft (refs/ez_bar/ref.md), along x: a free body
// the hands carry. The shaft is dark 28.5 mm tube, 800 mm between the collars, a straight middle on
// the sleeve axis (120 mm corner to corner), then each side an inner and an outer grip segment bending
// opposite ways, the bends rounded, and returning to the axis at the collar; the zigzag lies in the
// bar's horizontal plane, 80 mm from peak to trough as built (the corners are 44 mm off the axis, the
// rounding takes the rest), with the outer bend forward (+z, away from the lifter). Each sleeve is
// chrome, 267 mm to load and 50 mm across, behind a wider chrome collar flange. Nothing on it moves.
//
// Options: `grip` ('outer' by default, or 'inner') is the pair the hands are on, which the pose's
// bar position is the middle of. The hands are on the segments' middles: 510 mm apart on the outer
// pair, 250 mm on the inner.
import { recipe, free } from '../recipe.mjs';
import { bent, rod } from '../parts.mjs';
import { V } from '../rig.mjs';

// The shaft's centre line from the middle out to the collar, x along the bar and z forward (the
// ref's y reversed), in metres.
const SHAFT = [[0, 0], [0.06, 0], [0.19, -0.044], [0.32, 0.044], [0.4, 0]];
const RADIUS = 0.01425;
// How far each side of a bend's corner the straight tube stops and the rounded bend begins.
const BEND = 0.02;
// The sleeve and the collar flange at its shaft end; the bar rests on the flange's radius.
const SLEEVE = { radius: 0.025, collar: 0.032, collarLength: 0.025, loadable: 0.267 };
// Where a hand sits on the inner segment and on the outer one: its middle.
const mid = (a, b) => [(a[0] + b[0]) / 2, (a[1] + b[1]) / 2];
const GRIPS = { inner: mid(SHAFT[1], SHAFT[2]), outer: mid(SHAFT[2], SHAFT[3]) };

export default recipe({
  name: 'ez_bar',
  // Lies on the floor, the zigzag seen from above, until a pose carries it.
  standsAlone: { camera: { target: [0, 0.03, 0], halfHeight: 0.45, azimuth: 150, elevation: 35 } },
  bodies: { bar: free() },
  contacts: [{ body: 'bar', point: 'outer', landmark: 'hands' }, { body: 'bar', point: 'inner', landmark: 'hands' }],
  build({ grip = 'outer' } = {}, M) {
    const at = (s, [x, z]) => V(s * x, 0, z);
    // The centre section is one tube across. Each bend is a short rounded tube between the straight
    // segments, which stop BEND short of it.
    const parts = [rod(RADIUS, V(-(SHAFT[1][0] - BEND), 0, 0), V(SHAFT[1][0] - BEND, 0, 0), M.frame, { held: true })];
    for (const s of [-1, 1]) {
      const toward = (from, to, by) => at(s, from).lerp(at(s, to), by / Math.hypot(to[0] - from[0], to[1] - from[1]));
      const bends = SHAFT.slice(1, -1);
      SHAFT.slice(1).forEach((_, i) => {
        // A straight segment, its ends pulled back where a bend is.
        const from = SHAFT[i + 1];
        const to = SHAFT[i + 2];
        if (!to) return;
        const a = bends.includes(from) ? toward(from, to, BEND) : at(s, from);
        const b = bends.includes(to) ? toward(to, from, BEND) : at(s, to);
        parts.push(rod(RADIUS, a, b, M.frame, { held: true }));
      });
      bends.forEach((p, i) => {
        const prev = SHAFT[i];
        const next = SHAFT[i + 2];
        const [p0, p2] = [toward(p, prev, BEND), toward(p, next, BEND)];
        // A quadratic curve from one cut to the other, the vertex its control point.
        const curve = [0, 0.25, 0.5, 0.75, 1].map((t) => p0.clone().multiplyScalar((1 - t) ** 2).addScaledVector(at(s, p), 2 * t * (1 - t)).addScaledVector(p2, t * t));
        parts.push(bent(`ez_bend:${s}:${i}`, curve, RADIUS, M.frame, { held: true }));
      });
      const end = SHAFT[SHAFT.length - 1][0];
      const collarTo = end + SLEEVE.collarLength;
      parts.push(rod(SLEEVE.collar, V(s * end, 0, 0), V(s * collarTo, 0, 0), M.metal, { solid: true }));
      parts.push(rod(SLEEVE.radius, V(s * collarTo, 0, 0), V(s * (collarTo + SLEEVE.loadable), 0, 0), M.metal, { solid: true }));
    }
    let bar;
    const points = (to) => Object.fromEntries(Object.entries(GRIPS).map(([name, g]) => [name, [-1, 1].map((s) => at(s, g).add(to))]));
    return {
      parts,
      stand(g) { bar = g; },
      drive(P) {
        const gripZ = GRIPS[grip][1];
        // On the floor with no pose; otherwise the hands' line is the grip segments' middle.
        if (P.bar) bar.position.copy(P.bar).setZ(P.bar.z - gripZ);
        else bar.position.set(0, SLEEVE.collar, 0);
        return { points: points(bar.position) };
      },
    };
  },
});
