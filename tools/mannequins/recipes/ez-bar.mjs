// A short Olympic-sleeved curl bar with a zigzag shaft (refs/ez_bar/ref.md), along x: a free body
// the hands carry. The shaft is 28.5 mm tube, 800 mm between the collars, a straight 120 mm in the
// middle on the sleeve axis, then each side an inner and an outer grip segment bending opposite
// ways and returning to the axis at the collar; the zigzag lies in the bar's horizontal plane, 80 mm
// from peak to trough, with the outer kinks forward (+z, away from the lifter). Each sleeve is
// 267 mm to load and 50 mm across, with a collar ring at its shaft end. Nothing on it moves.
//
// Options: `grip` ('outer' by default, or 'inner') is the pair the hands are on, which the pose's
// bar position is the middle of. The hands are on the segments' middles: 510 mm apart on the outer
// pair, 250 mm on the inner.
import { recipe, free } from '../recipe.mjs';
import { knob, rod } from '../parts.mjs';
import { V } from '../rig.mjs';

// The shaft's centre line from the middle out to the collar, x along the bar and z forward (the
// ref's y reversed), in metres.
const SHAFT = [[0, 0], [0.06, 0], [0.19, -0.04], [0.32, 0.04], [0.4, 0]];
const RADIUS = 0.01425;
// Where the bar's centre line rests on the floor: on its collars.
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
    // The centre section is one tube across.
    const parts = [rod(RADIUS, at(-1, SHAFT[1]), at(1, SHAFT[1]), M.metal, { held: true })];
    for (const s of [-1, 1]) {
      SHAFT.slice(2).forEach((p, i) => {
        parts.push(rod(RADIUS, at(s, SHAFT[i + 1]), at(s, p), M.metal, { held: true }));
        parts.push(Object.assign(knob(M, at(s, SHAFT[i + 1]), RADIUS), { material: M.metal }));
      });
      const end = SHAFT[SHAFT.length - 1][0];
      const collarTo = end + SLEEVE.collarLength;
      parts.push(rod(SLEEVE.collar, V(s * end, 0, 0), V(s * collarTo, 0, 0), M.frame, { solid: true }));
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
