// A bench along z (refs/bench/ref.md), the head toward -z: a short seat and a long back pad either
// side of a hinge, flat, with a fixed raised back, or adjustable, its back pad a body turning about
// the hinge to the angle the pose is prepared at, which the phone turns to the exact angle.
import * as THREE from 'three';
import { recipe, revolute } from '../recipe.mjs';
import { along, nameAll, pad, padGeometry, part, rod, rollerGeometry, soften, tube, turnedPart, wheelGeometry } from '../parts.mjs';
import { V, X, rad } from '../rig.mjs';

/** How thick and how wide the pads are, and how long the seat and the back pad. */
const PAD = 0.057;
const WIDE = 0.305;
const SEAT = 0.33;
const BACK = 0.945;
/** The bench's length, and the gap between the pads when flat. */
const LENGTH = 1.321;
const GAP = LENGTH - SEAT - BACK;
/** How far the hinge is behind the seat's front, and the seat's top below the back pad's. */
const HINGE = 0.418;
const DROP = 0.007;
/** The hinge boss, ahead of and below the hinge the pad turns about. */
const BOSS = [0.028, -0.029];
/** The back pad's settings, the chart's holes and the highest it goes (ref, Open gaps), in degrees. */
const BACK_SETTINGS = [0, 8, 18, 28, 38, 48, 58, 68, 78, 85];
/** The back pad's highest angle, in degrees. */
export const TOP = BACK_SETTINGS.at(-1);
/** The back pad's angle for a decline, in degrees: raised, the lifter lying the other way round. */
export const DECLINE = 15;
/** The back arc's radius about the hinge, and how far round it the pin in the spine holds it flat, in degrees. */
const BACK_ARC = 0.32;
const BACK_PINNED = 16.5;
/** The seat's range, in degrees: its front down and up about the hinge's boss. */
const SEAT_RANGE = [-15, 40];
/** The seat's settings, and how far round its arc the pin in the front leg holds it flat, in degrees. */
const SEAT_SETTINGS = [-15, 0, 10, 20, 30, 40];
const SEAT_PINNED = 40;
/** Twin tubes under the pads, either side of the slot the arcs run in. */
const TWIN = 0.053;
const STEEL = 0.051;
/** The top of a foot's beam above the floor, and how far into it a leg stops. */
const BEAM = 0.074;
const SUNK = 0.0015;

/**
 * Where a bench's back pad and hinge are: its top `height` above the floor, and the
 * hinge the pad turns about, on its underside, at `y` and `z` (by default 41.8 cm behind the
 * seat's front).
 */
export function benchHinge({ height = 0.457, z = -0.2105, hinge = null } = {}) {
  return { height, y: height - PAD, z: hinge ?? z + LENGTH / 2 - HINGE };
}

/** A cushion `length` long along z, centred on the middle of its thickness. */
const cushion = (length, M, name) => {
  const c = part(padGeometry(WIDE, PAD, length, 0.02), M.vinyl, { solid: true });
  if (name) c.name = name;
  return c;
};

/**
 * A steel plate `thick` across x, bent round a centre: an arc of radius `r` and width `wide` from
 * `from` to `to` radians, with a slot at each of `slots`. An angle runs from `way` (+1 ahead, -1
 * behind) of the centre round to below it.
 */
function arcPlate(M, { r, wide, from, to, slots, way, thick = 0.008 }) {
  const at = (radius, a) => [way * radius * Math.cos(a), -radius * Math.sin(a)];
  const plate = new THREE.Shape();
  const steps = 28;
  for (let i = 0; i <= steps; i++) plate[i ? 'lineTo' : 'moveTo'](...at(r + wide / 2, from + ((to - from) * i) / steps));
  for (let i = steps; i >= 0; i--) plate.lineTo(...at(r - wide / 2, from + ((to - from) * i) / steps));
  plate.closePath();
  for (const s of slots) {
    const slot = new THREE.Path();
    for (let i = 0; i < 10; i++) {
      const t = (i / 10) * Math.PI * 2;
      // Longer across the arc than along it.
      const [y, z] = at(r + 0.014 * Math.sin(t), s + (0.008 / r) * Math.cos(t));
      slot[i ? 'lineTo' : 'moveTo'](y, z);
    }
    slot.closePath();
    plate.holes.push(slot);
  }
  return profiled(plate, thick, M);
}

/** A flat `shape` of [z, y] points, `thick` across x and centred on it, its edges eased by `ease`. */
function profiled(shape, thick, M, ease = 0) {
  const g = new THREE.ExtrudeGeometry(shape, { depth: thick - 2 * ease, bevelEnabled: ease > 0, bevelThickness: ease, bevelSize: ease, bevelOffset: -ease, bevelSegments: 1 });
  g.translate(0, 0, -thick / 2 + ease);
  g.rotateY(-Math.PI / 2);
  return part(soften(g), M.frame, { solid: ease > 0 });
}

/**
 * One side's rail along `path` of [z, y] points: a length of tubing `STEEL` across for each leg of
 * the path, each cut to meet the next on a sharp mitre, a seam at the corner, and level at each
 * end, where it runs into a foot.
 */
function rail(path, M, name) {
  const half = STEEL / 2;
  const unit = ([az, ay], [bz, by]) => { const l = Math.hypot(bz - az, by - ay); return [(bz - az) / l, (by - ay) / l]; };
  const side = (s) => path.map((p, i) => {
    const before = i > 0 ? unit(path[i - 1], p) : null;
    const after = i < path.length - 1 ? unit(p, path[i + 1]) : null;
    if (before && after) {
      // On the line that halves the corner, as far out as keeps each side `half` off its tube.
      const n = [-(before[1] + after[1]), before[0] + after[0]];
      const l = Math.hypot(...n);
      const across = (half * l) / (n[0] * -before[1] + n[1] * before[0]);
      return [p[0] + (s * across * n[0]) / l, p[1] + (s * across * n[1]) / l];
    }
    // Slid along its tube to the level of the end.
    const d = before ?? after;
    const off = [p[0] - s * half * d[1], p[1] + s * half * d[0]];
    const back = (p[1] - off[1]) / d[1];
    return [off[0] + back * d[0], p[1]];
  });
  const [one, other] = [side(1), side(-1)];
  return path.slice(1).map((_, i) => {
    // Drawn along its own length and turned into place, so the box the checks test it by fits it.
    const [dz, dy] = unit(path[i], path[i + 1]);
    const mid = [(path[i][0] + path[i + 1][0]) / 2, (path[i][1] + path[i + 1][1]) / 2];
    const own = ([z, y]) => new THREE.Vector2((z - mid[0]) * dz + (y - mid[1]) * dy, -(z - mid[0]) * dy + (y - mid[1]) * dz);
    const mesh = profiled(new THREE.Shape([one[i], one[i + 1], other[i + 1], other[i]].map(own)), STEEL, M, 0.0015);
    mesh.name = `${name}_${i}`;
    mesh.position.set(0, mid[1], mid[0]);
    mesh.rotation.x = Math.atan2(-dy, dz);
    return mesh;
  });
}

/**
 * What turns with the back pad, about the hinge at the origin: the cushion, the frame under it, its
 * bumper and the arc of holes the pin sets it by. With `decline`, the foot catch: a post out of the
 * frame's far end, and a cross tube with a pair of rollers at each end.
 */
function backPad(M, name, decline) {
  const near = HINGE - SEAT - GAP;
  const far = near - BACK;
  const c = cushion(BACK, M, name);
  c.position.set(0, PAD / 2, near - BACK / 2);
  const under = -STEEL / 2;
  const frameEnd = -0.871;
  const parts = [
    c,
    tube(STEEL, V(0, under, 0), V(0, under, frameEnd), M.frame),
    pad(0.05, 0.075, 0.126, M.vinyl, V(0, -STEEL - 0.0375, -0.659), { soft: 0.01, solid: false }),
    // The arc runs round the hinge, from the frame to below the seat, a slot for each setting from
    // where the pin in the spine holds it flat.
    arcPlate(M, { r: BACK_ARC, wide: 0.057, from: rad(8), to: rad(110), way: -1, slots: BACK_SETTINGS.map((a) => rad(BACK_PINNED + a)) }),
  ];
  if (decline) {
    const knees = V(0, 0.03, far - 0.07);
    const ankles = knees.clone().add(V(0, -0.29 * Math.sin(rad(50)), -0.29 * Math.cos(rad(50))));
    parts.push(tube(0.04, V(0, under, frameEnd + 0.1), V(0, under, knees.z), M.frame));
    parts.push(tube(0.04, V(0, under, knees.z), knees, M.frame), tube(0.04, knees, ankles, M.frame));
    for (const at of [knees, ankles]) {
      for (const s of [-1, 1]) parts.push(turnedPart(rollerGeometry(0.06, 0.21), M.vinyl, V(s * 0.135, at.y, at.z)));
      parts.push(along(0.008, -0.25, 0.25, M.metal, 10).translateY(at.y).translateZ(at.z));
    }
  }
  return parts;
}

/**
 * Its front at `z` + half its length, the top of its back pad `height` above the floor: flat, or
 * with its back pad raised `angle` degrees about a hinge at `hinge` (z; by default 41.8 cm behind
 * the seat's front, which `hinge` moves with it). With `adjustable`, the back pad is a body of its
 * own, `bench_back`, on a revolute joint at the hinge, and the seat, `bench_seat`, on one at the
 * boss. `seat` tilts the seat's front that many degrees up (down below 0). With `decline` it
 * carries the foot catch, and stands at `DECLINE` unless given an angle.
 * With `across` it stands across the body instead, its middle at that z (`z` is then along it). The
 * pads stand on a spine of twin tubes, a raked leg at each end down to a foot across, the arcs in
 * the slot between the tubes. It rolls on wheels at the head end, lifted by the handle at the seat.
 */
export default recipe({
  name: 'bench',
  bodies: ({ adjustable = false } = {}) => (adjustable ? {
    back: revolute({ axis: X, limits: [0, rad(TOP)], settings: BACK_SETTINGS.map(rad) }),
    seat: revolute({ axis: X.clone().negate(), limits: SEAT_RANGE.map(rad), settings: SEAT_SETTINGS.map(rad) }),
  } : {}),
  build({ decline = false, angle = decline ? DECLINE : 0, seat: tilt = 0, height = 0.457, z = -0.2105, hinge = null, adjustable = false, across = null } = {}, M) {
    const { y, z: h } = benchHinge({ height, z, hinge });
    const front = h + HINGE;
    const boss = V(0, y + BOSS[1], h + BOSS[0]);
    const parts = [];
    // The seat turns about the boss with its frame, its arc and the handle under its front.
    const seat = new THREE.Group();
    seat.position.copy(boss);
    seat.rotation.x = -rad(tilt);
    const fromBoss = (at) => at.clone().sub(boss);
    const seatPad = cushion(SEAT, M, adjustable ? 'bench_seat_pad' : null);
    seatPad.position.copy(fromBoss(V(0, height - DROP - PAD / 2, front - SEAT / 2)));
    const belowSeat = height - DROP - PAD;
    const handleZ = boss.z + 0.31;
    seat.add(
      seatPad,
      pad(0.15, 0.048, 0.238, M.frame, fromBoss(V(0, belowSeat - 0.029, boss.z + 0.229)), { soft: 0.003 }),
      arcPlate(M, { r: 0.233, wide: 0.05, from: rad(8), to: rad(88), way: 1, slots: SEAT_SETTINGS.map((a) => rad(SEAT_PINNED + a)) }),
      pad(0.12, 0.06, 0.012, M.frame, fromBoss(V(0, belowSeat - 0.058, handleZ)), { soft: 0.002, solid: false }),
      rod(0.017, fromBoss(V(0, belowSeat - 0.07, handleZ)), fromBoss(V(0, belowSeat - 0.193, handleZ)), M.metal),
    );
    if (adjustable) {
      seat.name = 'bench_seat';
      nameAll(seat, 'bench_seat');
    }
    parts.push(seat);
    // The spine, and a leg at each end.
    const top = height - 0.128;
    const mid = top - STEEL / 2;
    const ends = [boss.z - 0.595, boss.z + 0.099];
    const feet = [h - 0.782, h + 0.347];
    // A leg aims at the middle of its foot's top and stops just inside it: raked, it is nearly as
    // long along the beam as the beam is deep, so any lower it would break out of the far side.
    const sunk = (i) => [feet[i] + ((feet[i] - ends[i]) * SUNK) / (mid - BEAM), BEAM - SUNK];
    for (const s of [-1, 1]) {
      for (const piece of rail([sunk(1), [ends[1], mid], [ends[0], mid], sunk(0)], M, `bench_rail_${s < 0 ? 'l' : 'r'}`)) {
        piece.position.x = s * TWIN;
        parts.push(piece);
      }
    }
    // A foot across under each leg, on a plate with rubber feet at its ends, a gusset closing each end.
    for (const f of feet) {
      parts.push(pad(0.572, 0.051, 0.076, M.frame, V(0, 0.0485, f), { soft: 0.004 }));
      parts.push(pad(0.572, 0.006, 0.104, M.frame, V(0, 0.02, f), { soft: 0.002, solid: false }));
      for (const s of [-1, 1]) {
        const gusset = profiled(new THREE.Shape([[f - 0.052, 0.023], [f + 0.052, 0.023], [f + 0.038, 0.08], [f - 0.038, 0.08]].map(([z, y]) => new THREE.Vector2(z, y))), 0.006, M);
        gusset.position.x = s * 0.283;
        parts.push(gusset);
      }
      for (const s of [-1, 1]) parts.push(pad(0.05, 0.017, 0.05, M.vinyl, V(s * 0.25, 0.0085, f), { soft: 0.005 }));
    }
    // The hinge's boss on its bracket, and the pins through the spine and the front leg.
    parts.push(pad(0.17, top - boss.y + 0.06, 0.071, M.frame, V(0, (top + boss.y + 0.06) / 2, boss.z), { soft: 0.004, solid: false }));
    parts.push(along(0.031, -0.09, 0.09, M.frame, 20).translateY(boss.y).translateZ(boss.z));
    parts.push(along(0.008, -0.1, 0.1, M.metal, 10).translateY(y).translateZ(h));
    const pin = (at) => {
      parts.push(along(0.006, -0.09, 0.09, M.metal, 10).translateY(at.y).translateZ(at.z));
      parts.push(pad(0.03, 0.028, 0.028, M.vinyl, at.clone().add(V(0.09, 0, 0)), { soft: 0.01, solid: false }));
    };
    pin(V(0, y - BACK_ARC * Math.sin(rad(BACK_PINNED)), h - BACK_ARC * Math.cos(rad(BACK_PINNED))));
    pin(V(0, boss.y - 0.15, boss.z + 0.178));
    // Wheels off the floor at the head end, on brackets out of the foot.
    for (const s of [-1, 1]) {
      const wheel = V(s * 0.175, 0.048, h - 0.865);
      parts.push(turnedPart(wheelGeometry(0.038, 0.03), M.pulley, wheel, { solid: false }));
      parts.push(pad(0.044, 0.04, 0.05, M.frame, V(s * 0.175, 0.052, feet[0] - 0.045), { soft: 0.003, solid: false }));
    }
    parts.push(...[-1, 1].map((s) => along(0.006, s * 0.15, s * 0.2, M.metal, 10).translateY(0.048).translateZ(h - 0.865)));

    const turning = new THREE.Group();
    turning.position.set(0, y, h);
    turning.rotation.x = rad(angle);
    turning.add(...backPad(M, adjustable ? 'bench_back_pad' : null, decline));
    if (adjustable) {
      turning.name = 'bench_back';
      nameAll(turning, 'bench_back');
    }
    parts.push(turning);
    let bench;
    return {
      parts,
      bodies: adjustable ? { back: [turning], seat: [seat] } : {},
      // Across the body, a hip thrust's: its length along x, its middle at z `across`.
      stand(g) {
        bench = g;
        if (across !== null) {
          g.rotation.y = Math.PI / 2;
          g.position.set(0, 0, across);
        }
      },
      drive() {
        if (!adjustable) return {};
        // Back where the options set them, as a scrub moves them from here.
        turning.position.set(0, y, h);
        turning.rotation.set(rad(angle), 0, 0);
        seat.position.copy(boss);
        seat.rotation.set(-rad(tilt), 0, 0);
        bench.updateWorldMatrix(true, false);
        const axis = X.clone().applyQuaternion(bench.quaternion);
        return {
          joints: {
            back: { value: rad(angle), origin: bench.localToWorld(V(0, y, h)), axis },
            seat: { value: rad(tilt), origin: bench.localToWorld(boss.clone()), axis: axis.clone().negate() },
          },
        };
      },
    };
  },
});
