// A bench along z: flat, with a fixed raised back pad, or adjustable, its back pad a body turning
// about the hinge to the angle the pose is prepared at, which the phone turns to the exact angle.
import * as THREE from 'three';
import { recipe, revolute } from '../recipe.mjs';
import { along, bent, mm, nameAll, pad, padGeometry, part, span, stretchedTube, tube, turnedPart, wheelGeometry } from '../parts.mjs';
import { V, X, rad } from '../rig.mjs';

/** How thick a bench's pads are. */
const PAD = 0.065;

/**
 * Where a bench's seat top and hinge are: the seat's top `height` above the floor, and the hinge,
 * in the middle of the pad's thickness, at `y` and `z` (by default 42 cm behind the seat's front).
 */
export function benchHinge({ height = 0.44, z = -0.15, length = 1.2, hinge = null } = {}) {
  return { height, y: height - PAD / 2, z: hinge ?? z + length / 2 - 0.42 };
}

/** The length of an adjustable bench's back pad from its hinge. */
const BACK = 0.8;

/**
 * A bench's pad, `length` long along z and centred on the middle of its thickness: a cushion
 * `PAD` thick, on a board under it. `name` names the cushion.
 */
function padded(length, M, name) {
  const g = new THREE.Group();
  const cushion = part(padGeometry(0.29, PAD, length, 0.02), M.vinyl, { solid: true });
  if (name) cushion.name = name;
  const board = part(padGeometry(0.25, 0.012, length - 0.03, 0.003), M.frame);
  board.position.y = -PAD / 2 - 0.006;
  g.add(cushion, board);
  return g;
}

/**
 * `length` long and centred on `z`, the top of its pad `height` above the floor: flat, or with its
 * back pad raised `angle` degrees about a hinge at `hinge` (z; by default 42 cm behind the seat's
 * front). With `adjustable`, the back pad is a body of its own, `bench_back`, on a revolute joint
 * at the hinge. With `across` it stands across the body instead, its middle at that z (`z` is then
 * along it). The pads stand on legs of tubing with feet across, a spine along the floor and a rail
 * under them.
 */
export default recipe({
  name: 'bench',
  bodies: ({ adjustable = false } = {}) => (adjustable ? { back: revolute({ axis: X, limits: [0, rad(85)] }) } : {}),
  build({ angle = 0, height = 0.44, z = -0.15, length = 1.2, hinge = null, adjustable = false, across = null } = {}, M) {
    const y = height - PAD / 2;
    const parts = [];
    let turning = null;
    const front = z + length / 2;
    const h = benchHinge({ height, z, length, hinge }).z;
    if (adjustable) {
      // The seat in front of the hinge, the back pad hanging from the hinge behind it.
      const seat = padded(front - h, M);
      seat.position.set(0, y, (front + h) / 2);
      parts.push(seat);
      turning = new THREE.Group();
      turning.name = 'bench_back';
      turning.position.set(0, y, h);
      turning.rotation.x = rad(angle);
      const slab = padded(BACK, M, 'bench_back_pad');
      slab.position.set(0, 0, -BACK / 2);
      turning.add(slab);
      for (const s of [-1, 1]) parts.push(pad(0.01, 0.07, 0.09, M.frame, V(s * 0.12, y - 0.06, h), { soft: 0.003 }));
    } else if (angle === 0 && hinge === null) {
      const slab = padded(length, M);
      slab.position.set(0, y, z);
      parts.push(slab);
    } else {
      // The seat in front of the hinge, the back pad behind it raised about it.
      const seat = padded(front - h, M);
      seat.position.set(0, y, (front + h) / 2);
      const a = rad(angle);
      const back = padded(BACK, M);
      back.position.set(0, y + (Math.sin(a) * BACK) / 2, h - (Math.cos(a) * BACK) / 2);
      back.rotation.x = a;
      parts.push(seat, back);
    }
    const ends = [z - length / 2 + 0.1, z + length / 2 - 0.1];
    const under = height - PAD - 0.032;
    for (const e of ends) {
      parts.push(tube(0.06, V(0, 0.06, e), V(0, height - PAD - 0.012, e), M.frame));
      parts.push(tube(0.06, V(-0.21, 0.03, e), V(0.21, 0.03, e), M.frame));
      // A bracket under the pad's board at the leg's head.
      parts.push(pad(0.14, 0.01, 0.1, M.frame, V(0, height - PAD - 0.017, e), { soft: 0.003, solid: false }));
    }
    // Rubber caps on the back foot; on the front one, a wheel either side on an axle, to roll it.
    for (const s of [-1, 1]) {
      parts.push(pad(0.02, 0.06, 0.07, M.vinyl, V(s * 0.22, 0.03, ends[0]), { soft: 0.006 }));
      parts.push(turnedPart(wheelGeometry(0.032, 0.024), M.pulley, V(s * 0.226, 0.032, ends[1]), { solid: false }));
    }
    parts.push(along(0.006, -0.245, 0.245, M.metal, 10).translateY(0.032).translateZ(ends[1]));
    parts.push(tube(0.05, V(0, 0.085, ends[0]), V(0, 0.085, ends[1]), M.frame));
    parts.push(tube(0.04, V(0, under, ends[0] - 0.03), V(0, under, ends[1] + 0.03), M.frame));
    // A handle out of the spine's back end, to lift that end by.
    const e0 = ends[0];
    parts.push(bent(`bench_handle:${mm(e0)}`, [V(0, 0.085, e0 - 0.02), V(0, 0.1, e0 - 0.09), V(0, 0.17, e0 - 0.15), V(0, 0.24, e0 - 0.17)], 0.014, M.metal));
    if (adjustable) {
      // The ladder: rungs on the rail behind the hinge, and the strut from the back pad's underside
      // down to one of them. The strut is set for the angle the clip is baked at; the phone turns
      // the pad a few degrees either side of it at most.
      for (let k = 0; k < 6; k++) parts.push(along(0.006, -0.03, 0.03, M.metal, 10).translateY(under + 0.024).translateZ(h - 0.12 - k * 0.07));
      if (angle >= 10) {
        const a = rad(angle);
        const d = 0.35;
        const lift = (p) => V(0, p.y * Math.cos(a) - p.z * Math.sin(a), p.y * Math.sin(a) + p.z * Math.cos(a));
        const topLocal = V(0, -PAD / 2 - 0.012, -d);
        const topWorld = lift(topLocal);
        const foot = V(0, under + 0.024 - y, Math.max(topWorld.z - 0.15, ends[0] + 0.05 - h));
        // Back into the pad's frame: turned by -angle.
        const footLocal = V(0, foot.y * Math.cos(a) + foot.z * Math.sin(a), -foot.y * Math.sin(a) + foot.z * Math.cos(a));
        const strut = stretchedTube(0.035, M.frame);
        span(strut, topLocal, footLocal);
        turning.add(strut, along(0.006, -0.03, 0.03, M.metal, 10).translateY(footLocal.y).translateZ(footLocal.z));
      }
      nameAll(turning, 'bench_back');
      parts.push(turning);
    }
    let bench;
    return {
      parts,
      bodies: turning ? { back: [turning] } : {},
      // Across the body, a hip thrust's: its length along x, its middle at z `across`.
      stand(g) {
        bench = g;
        if (across !== null) {
          g.rotation.y = Math.PI / 2;
          g.position.set(0, 0, across);
        }
      },
      drive() {
        if (!turning) return {};
        bench.updateWorldMatrix(true, false);
        return { joints: { back: { value: rad(angle), origin: bench.localToWorld(V(0, y, h)), axis: X.clone().applyQuaternion(bench.quaternion) } } };
      },
    };
  },
});
