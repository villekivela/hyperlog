// How a hand meets what it holds (#162, docs/research/grip.md). Each grip names where the bar sits
// in the hand (`sits`: the heel of the palm, mid-palm, the base of the fingers, or `open`, a hand
// that holds nothing round) and what the thumb does (`thumb`: wrapped round the bar, or thumbless,
// over it beside the fingers). The hand is seated so its palm rests on the bar at the bar's own
// radius, read from the kit's part under the hand, and each finger and the thumb close joint by
// joint until they touch it. The contact check measures the same skin, as the body is drawn.
import * as THREE from 'three';

/** Each landmark's distance along the palm from the wrist, as a fraction of the palm's length. */
// This mannequin's fingers close round nothing lower in its palm than 0.6 of the way from the wrist
// joint to the knuckles (its palm is deep), so the heel of the palm is as low as they reach.
export const LANDMARKS = { heel: 0.65, mid: 0.72, base: 0.8 };
export const THUMBS = ['wrapped', 'thumbless'];

/** Where the bar sits by default in each motion's hands (the research's section 5.1). */
const SITS = {
  squat: 'mid', hinge: 'base', bench_press: 'heel', pulldown: 'base', pressdown: 'mid', leg_press: 'base',
  chest_press: 'heel', dumbbell_press: 'heel', arms: 'mid', row: 'base', free_arms: 'base', knee_lever: 'base',
  calf_raise: 'base', hack_squat: 'base', lever_handles: 'base', bridge: 'mid',
};

/**
 * A pose's grip: where the bar sits and the thumb, the motion's by default, or the entry's own
 * `grip.sits` and `grip.thumb`. A cross-arm rack's hands lie open.
 */
export function holdOf(e) {
  const sits = e.grip?.crossed ? 'open' : e.grip?.sits ?? SITS[e.motion] ?? 'base';
  if (sits !== 'open' && !LANDMARKS[sits]) throw new Error(`No grip landmark "${sits}": heel, mid, base or open`);
  if (sits === 'open') return { sits, thumb: 'open' };
  const thumb = e.grip?.thumb ?? 'wrapped';
  if (!THUMBS.includes(thumb)) throw new Error(`No thumb "${thumb}": wrapped or thumbless`);
  return { sits, thumb };
}

export const DIGITS = ['index', 'middle', 'ring', 'pinky', 'thumb'];
const HAND = /^(hand|index|middle|ring|pinky|thumb)_/;

/** Each vertex's bone: the one that moves it most. */
function vertexBones(mesh) {
  const { skinIndex, skinWeight } = mesh.geometry.attributes;
  const out = [];
  for (let i = 0; i < skinIndex.count; i++) {
    let best = 0;
    for (let j = 1; j < 4; j++) if (skinWeight.getComponent(i, j) > skinWeight.getComponent(i, best)) best = j;
    out.push(mesh.skeleton.bones[skinIndex.getComponent(i, best)].name);
  }
  return out;
}

/** The commonest of three names, the first on a tie. */
const commonest = (a, b, c) => (b === c ? b : a);

/**
 * Points on the skin of the hands, for the grip and the contact check: every vertex of a hand, each
 * triangle's middle, and on the palm, whose triangles are large, a grid over each. Each point is
 * the vertices and weights it is made of, so it can be measured as the body is drawn (`skinned`).
 * By side, by bone.
 */
export function skinPoints(meshes) {
  const out = { l: {}, r: {} };
  for (const mesh of meshes) {
    const bones = vertexBones(mesh);
    const add = (bone, pts) => {
      const k = bone.at(-1);
      (out[k][bone] ??= []).push({ mesh, pts });
    };
    bones.forEach((b, i) => { if (HAND.test(b)) add(b, [[i, 1]]); });
    const index = mesh.geometry.index;
    const tris = index ? index.count / 3 : mesh.geometry.attributes.position.count / 3;
    for (let t = 0; t < tris; t++) {
      const [a, b, c] = [0, 1, 2].map((j) => (index ? index.getX(3 * t + j) : 3 * t + j));
      const bone = commonest(bones[a], bones[b], bones[c]);
      if (!HAND.test(bone)) continue;
      if (!bone.startsWith('hand_')) { add(bone, [[a, 1 / 3], [b, 1 / 3], [c, 1 / 3]]); continue; }
      const n = 4;
      for (let i = 0; i <= n; i++) {
        for (let j = 0; j <= n - i; j++) {
          if ((i === 0 && j === 0) || (i === n) || (j === n)) continue;
          add(bone, [[a, (n - i - j) / n], [b, i / n], [c, j / n]]);
        }
      }
    }
  }
  return out;
}

const v2 = new THREE.Vector3();

/**
 * A way to place skin points as the body is drawn now (`at(point)`), each vertex skinned once
 * however many points share it.
 */
export function drawn() {
  const cache = new Map();
  const vertex = (mesh, i) => {
    let m = cache.get(mesh);
    if (!m) cache.set(mesh, m = new Map());
    let p = m.get(i);
    if (!p) m.set(i, p = mesh.getVertexPosition(i, new THREE.Vector3()).applyMatrix4(mesh.matrixWorld));
    return p;
  };
  return ({ mesh, pts }) => {
    const out = new THREE.Vector3();
    for (const [i, w] of pts) out.addScaledVector(vertex(mesh, i), w);
    return out;
  };
}

/**
 * The distance from a point (`x`, `y`, `z`) to the nearest of `points`, found through a grid of
 * `cell`-sized boxes: within a cell of it, or Infinity.
 */
export function nearestOf(points, cell = 0.006) {
  const grid = new Map();
  const key = (x, y, z) => ((x + 1024) * 2048 + (y + 1024)) * 2048 + (z + 1024);
  const lo = [Infinity, Infinity, Infinity];
  const hi = [-Infinity, -Infinity, -Infinity];
  for (const p of points) {
    const k = key(Math.floor(p.x / cell), Math.floor(p.y / cell), Math.floor(p.z / cell));
    if (!grid.has(k)) grid.set(k, []);
    grid.get(k).push(p.x, p.y, p.z);
    [p.x, p.y, p.z].forEach((c, i) => { lo[i] = Math.min(lo[i], c - cell); hi[i] = Math.max(hi[i], c + cell); });
  }
  return (x, y, z) => {
    // Nothing within a cell of a point outside the points' box.
    if (x < lo[0] || x > hi[0] || y < lo[1] || y > hi[1] || z < lo[2] || z > hi[2]) return Infinity;
    const [i0, j0, n0] = [Math.floor(x / cell), Math.floor(y / cell), Math.floor(z / cell)];
    let best = Infinity;
    for (let i = i0 - 1; i <= i0 + 1; i++) {
      for (let j = j0 - 1; j <= j0 + 1; j++) {
        for (let n = n0 - 1; n <= n0 + 1; n++) {
          const box = grid.get(key(i, j, n));
          if (!box) continue;
          for (let m = 0; m < box.length; m += 3) {
            const dx = box[m] - x;
            const dy = box[m + 1] - y;
            const dz = box[m + 2] - z;
            best = Math.min(best, dx * dx + dy * dy + dz * dz);
          }
        }
      }
    }
    return Math.sqrt(best);
  };
}

/**
 * The round parts the hands hold (a bar, a handle's grip, a rope), each as a line in the world:
 * through `at` along `along`, `half` either way, `radius` round it.
 */
export function heldParts(kit) {
  const out = [];
  kit.group.updateMatrixWorld(true);
  kit.group.traverse((m) => {
    if (!m.isMesh || !m.userData.held || m.geometry.type !== 'CylinderGeometry') return;
    m.geometry.computeBoundingBox();
    const box = m.geometry.boundingBox;
    const h = box.getSize(new THREE.Vector3()).multiplyScalar(0.5).toArray();
    // The axis is the extent least like the other two, which are its width either way round (a
    // cylinder of few sides is a little narrower one way).
    const axis = [0, 1, 2].reduce((best, i) => (Math.abs(h[(i + 1) % 3] - h[(i + 2) % 3]) < Math.abs(h[(best + 1) % 3] - h[(best + 2) % 3]) ? i : best));
    const unit = [0, 0, 0];
    unit[axis] = 1;
    const centre = box.getCenter(new THREE.Vector3()).applyMatrix4(m.matrixWorld);
    const along = new THREE.Vector3(...unit).transformDirection(m.matrixWorld);
    const scale = new THREE.Vector3().setFromMatrixScale(m.matrixWorld).toArray();
    const radius = Math.max(h[(axis + 1) % 3] * scale[(axis + 1) % 3], h[(axis + 2) % 3] * scale[(axis + 2) % 3]);
    out.push({ mesh: m, at: centre, along, half: h[axis] * scale[axis], radius });
  });
  return out;
}

/** How far `p` is from `part`'s axis, and how far along it from its middle. */
function fromAxis(part, p) {
  v2.copy(p).sub(part.at);
  const ax = v2.dot(part.along);
  return { radial: Math.sqrt(Math.max(0, v2.lengthSq() - ax * ax)), ax };
}

/** How far `p` is outside `part`, a capped cylinder: negative inside. */
function outside(part, p) {
  const { radial, ax } = fromAxis(part, p);
  const past = Math.abs(ax) - part.half;
  if (past <= 0) return Math.max(radial - part.radius, past);
  return radial <= part.radius ? past : Math.hypot(radial - part.radius, past);
}

/**
 * What a hand holding at `grip` holds: the round held parts whose axis runs within a centimetre of
 * it, one inside another (a grip's sleeve over its bar), as `with`, and the outermost of them, whose
 * radius the hand closes to. Null for a hand holding nothing.
 */
export function heldAt(parts, grip) {
  const under = parts.filter((part) => {
    const { radial, ax } = fromAxis(part, grip);
    return radial <= 0.01 && Math.abs(ax) <= part.half + 0.01;
  });
  if (!under.length) return null;
  const outer = under.reduce((a, b) => (b.radius > a.radius ? b : a));
  const coaxial = parts.filter((part) => Math.abs(part.along.dot(outer.along)) > 0.999 && fromAxis(outer, part.at).radial < 0.002);
  return { ...outer, with: coaxial };
}

/** How far `p` is outside what the hand holds (`held`, from `heldAt`): negative inside. */
const outsideHeld = (held, p) => Math.min(...held.with.map((part) => outside(part, p)));

/** How near two skin points must come to touch: the skin's points are a few millimetres apart. */
const TOUCHING = 0.002;

/**
 * How hand `k` meets `part` (what it holds, from `heldAt`), as the body is drawn now: each digit's gap to the bar's surface (its
 * nearest point; negative inside), with where; and the deepest point of the hand inside the bar.
 * A wrapped thumb on a bar thinner than the hand can close round rests on the fingers (research,
 * section 6), so its gap is the nearer of the bar and the index and middle fingers.
 */
export function contactOf(skin, k, part, thumb = 'wrapped') {
  const digits = {};
  let inside = { depth: -Infinity, at: null, bone: null };
  const points = {};
  const at = drawn();
  for (const [bone, list] of Object.entries(skin[k])) {
    const digit = DIGITS.find((d) => bone.startsWith(d + '_'));
    for (const s of list) {
      const p = at(s);
      if (digit) (points[digit] ??= []).push(p);
      const gap = outsideHeld(part, p);
      if (-gap > inside.depth) inside = { depth: -gap, at: p, bone };
      if (digit && (!digits[digit] || gap < digits[digit].gap)) digits[digit] = { gap, at: p };
    }
  }
  if (thumb === 'wrapped' && digits.thumb) {
    const nearest = nearestOf([...points.index, ...points.middle]);
    for (const p of points.thumb) {
      const d = nearest(p.x, p.y, p.z) - TOUCHING;
      if (d < digits.thumb.gap) digits.thumb = { gap: Math.max(d, 0), at: p, on: 'fingers' };
    }
  }
  return { digits, inside };
}

/**
 * The hands as the grip solve sees them, measured in the bind pose: each bone's skin points carried
 * rigidly in its own frame (the solve turns joints thousands of times, so it skips the skinning),
 * and the palm's skin, for seating a bar on it. `rest[k]` is the wrist, the way to the fingers, the
 * palm's normal and the knuckles' line, which a bar lies along.
 */
export function handModel(skin, bones, rest) {
  const at = drawn();
  const rigid = { l: {}, r: {} };
  const palm = { l: [], r: [] };
  const bind = {};
  for (const k of ['l', 'r']) {
    bind[k] = bones['hand_' + k].matrixWorld.clone();
    for (const [bone, list] of Object.entries(skin[k])) {
      const inverse = bones[bone].matrixWorld.clone().invert();
      if (bone.startsWith('hand_')) {
        for (const s of list) palm[k].push(at(s).sub(rest[k].wrist));
        continue;
      }
      // A finger's own vertices, its triangles being small; all of the thumb's root, which is the
      // ball of the thumb and part of the palm.
      const own = bone.startsWith('thumb_01') ? list : list.filter((s) => s.pts.length === 1);
      const pts = new Float32Array(own.length * 3);
      own.forEach((s, i) => at(s).applyMatrix4(inverse).toArray(pts, i * 3));
      rigid[k][bone] = pts;
    }
  }
  return { rigid, palm, bind, rest };
}

/**
 * The palm's skin with the thumb's root where grip `thumb` holds it and the fingers straight, from
 * the wrist in the bind pose: the ball of the thumb is part of what a bar rests on. Measured as the
 * body is drawn, since the thumb's root carries some of the palm's skin with it.
 */
function palmSkin(rig, k, thumb) {
  const { hand, digits } = rig;
  const spec = THUMB[thumb] ?? THUMB.wrapped;
  const joints = Object.values(digits[k]).flat();
  const kept = joints.map((j) => j.bone.quaternion.clone());
  for (const j of joints) j.bone.quaternion.copy(j.curl(0, spec));
  digits[k].thumb[0].bone.quaternion.copy(digits[k].thumb[0].curl(spec.start[0], spec));
  rig.bones['hand_' + k].updateMatrixWorld(true);
  const toBind = hand.bind[k].clone().multiply(rig.bones['hand_' + k].matrixWorld.clone().invert());
  const at = drawn();
  const out = [];
  for (const [bone, list] of Object.entries(rig.skin[k])) {
    if (!/^(hand|thumb_01)_/.test(bone)) continue;
    for (const s of list) out.push(at(s).applyMatrix4(toBind).sub(hand.rest[k].wrist));
  }
  joints.forEach((j, i) => j.bone.quaternion.copy(kept[i]));
  rig.bones['hand_' + k].updateMatrixWorld(true);
  return out;
}

/**
 * The ways a seat is measured at, in degrees: slanting across the palm from the knuckles' line
 * toward the fingers, and tilting out of the palm's plane toward its face.
 */
const SLANTS = Array.from({ length: 19 }, (_, i) => -45 + 5 * i);
const TILTS = Array.from({ length: 13 }, (_, i) => -30 + 5 * i);
const RAD = Math.PI / 180;
/** Every seat measured, by the mannequin's palm and the hand, landmark, radius and thumb. */
const SEATS = new Map();

/** `x` along `steps` (evenly spaced, ascending) as an index with a fraction, held to its ends. */
const stepOf = (steps, x) => Math.min(steps.length - 1.000001, Math.max(0, (x - steps[0]) / (steps[1] - steps[0])));

/**
 * Where a bar of `radius` sits in hand `k` at landmark `sits`, held with `thumb`: how far along the
 * palm from the wrist (`depth`), and how far off the palm's face so it rests on the palm's skin,
 * touching: `offAt(line)` for a bar along `line` ([along the knuckles, the fingers, the face] in the
 * hand's frame), as a straight wrist may slant it, and `off` for one along the knuckles.
 */
export function seatOf(rig, k, sits, radius, thumb = 'wrapped') {
  // A seat is the mannequin's, the same for every rig built from it.
  const key = `${rig.dims.palmLength}:${k}:${sits}:${radius}:${thumb}`;
  if (SEATS.has(key)) return SEATS.get(key);
  const { fingers, normal, knuckles } = rig.hand.rest[k];
  const depth = (LANDMARKS[sits] ?? LANDMARKS.base) * rig.dims.palmLength;
  // Only the skin a bar there can reach: within a few centimetres of the landmark.
  const skin = palmSkin(rig, k, thumb).filter((d) => Math.abs(d.dot(fingers) - depth) < radius + 0.06);
  const w = new THREE.Vector3();
  const offs = SLANTS.map((q) => TILTS.map((e) => {
    const line = knuckles.clone().multiplyScalar(Math.cos(q * RAD) * Math.cos(e * RAD))
      .addScaledVector(fingers, Math.sin(q * RAD)).addScaledVector(normal, Math.cos(q * RAD) * Math.sin(e * RAD));
    const clear = (off) => {
      let near = Infinity;
      for (const d of skin) {
        w.copy(d).addScaledVector(fingers, -depth).addScaledVector(normal, -off);
        w.addScaledVector(line, -w.dot(line));
        near = Math.min(near, w.length());
      }
      return near - radius;
    };
    // Between the palm's bones and well clear of its skin, home in on the touch.
    let lo = 0;
    let hi = 0.15;
    for (let i = 0; i < 15; i++) {
      const m = (lo + hi) / 2;
      if (clear(m) < 0) lo = m; else hi = m;
    }
    return hi;
  }));
  const offAt = ([kk, ff, nn]) => {
    // A line either way along is the same line.
    if (kk < 0) [kk, ff, nn] = [-kk, -ff, -nn];
    const a = stepOf(SLANTS, Math.asin(Math.max(-1, Math.min(1, ff))) / RAD);
    const b = stepOf(TILTS, Math.atan2(nn, kk) / RAD);
    const [i, j] = [Math.floor(a), Math.floor(b)];
    const [u, v] = [a - i, b - j];
    return (offs[i][j] * (1 - u) + offs[i + 1][j] * u) * (1 - v) + (offs[i][j + 1] * (1 - u) + offs[i + 1][j + 1] * u) * v;
  };
  const seat = { depth, off: offAt([1, 0, 0]), offAt };
  SEATS.set(key, seat);
  return seat;
}

/** How the joints of each digit close, from the knuckle out: in a fist's proportions, and how far each can. */
const FINGER = { base: [70, 75, 55], limit: [95, 110, 85] };
/**
 * The thumb, wrapped: its root turned most of the way to lie along the fingers and swung out of the
 * palm, its joints bending round the bar the other way from the fingers'. Thumbless: laid along the
 * fingers and bending the same way as them, over the bar beside the index finger. The root holds
 * where `start` puts it, as part of the palm the bar is seated on; the joints past it close.
 */
export const THUMB = {
  wrapped: { turn: 0.3, sense: -1, start: [60, 0, 0], base: [0, 40, 40], limit: [60, 90, 85] },
  thumbless: { turn: 1, sense: 1, start: [0, 0, 0], base: [0, 40, 40], limit: [0, 90, 85] },
};
/** How far the thumb's root may swing back toward the palm to clear a bar, in degrees. */
const SWING_BACK = 60;
/** A step of a digit's closing, in the fist's proportions. */
const STEP = 0.04;

/** Calls `each(x, y, z)` for each rigid point of `bone` where it is now. */
function eachPoint(rig, k, bone, each) {
  const pts = rig.hand.rigid[k][bone] ?? [];
  const e = rig.bones[bone].matrixWorld.elements;
  for (let i = 0; i < pts.length; i += 3) {
    const [x, y, z] = [pts[i], pts[i + 1], pts[i + 2]];
    each(e[0] * x + e[4] * y + e[8] * z + e[12], e[1] * x + e[5] * y + e[9] * z + e[13], e[2] * x + e[6] * y + e[10] * z + e[14]);
  }
}

/**
 * How far a point is outside a held part laid out along its line: `ax` along the line from the grip
 * and `radial` off it, against `pieces` ([{ from, to, radius }], a grip's sleeve and the bar under
 * it), or a bar of `radius` without end. Negative inside.
 */
function outsidePieces(ax, radial, radius, pieces) {
  if (!pieces?.length) return radial - radius;
  let best = Infinity;
  for (const { from, to, radius: r } of pieces) {
    const past = Math.max(from - ax, ax - to);
    best = Math.min(best, past <= 0 ? Math.max(radial - r, past) : radial <= r ? past : Math.hypot(radial - r, past));
  }
  return best;
}

/** How far point `p` is outside what the hand holds (`bar`): negative inside. */
function outsideBar({ at, along, radius, pieces }, p) {
  v2.copy(p).sub(at);
  const ax = v2.dot(along);
  return outsidePieces(ax, Math.sqrt(Math.max(0, v2.lengthSq() - ax * ax)), radius, pieces);
}

/** The gap from bone `bone`'s skin to what the hand holds (`bar`): the nearest of its points, negative inside. */
function barGap(rig, k, bone, { at, along, radius, pieces }) {
  let best = Infinity;
  eachPoint(rig, k, bone, (x, y, z) => {
    const px = x - at.x;
    const py = y - at.y;
    const pz = z - at.z;
    const ax = px * along.x + py * along.y + pz * along.z;
    best = Math.min(best, outsidePieces(ax, Math.sqrt(Math.max(0, px * px + py * py + pz * pz - ax * ax)), radius, pieces));
  });
  return best;
}

/**
 * What the hand holding `held` (from `heldAt`) at `grip` closes on, laid out along `line` from the
 * grip: each piece's span and radius.
 */
export function piecesOf(held, grip, line) {
  return held.with.flatMap((part) => {
    const centre = part.at.clone().sub(grip).dot(line);
    return Math.abs(centre) < part.half + 0.15 ? [{ from: centre - part.half, to: centre + part.half, radius: part.radius }] : [];
  });
}

/**
 * Closes one digit's joints (`joints`, from the knuckle out, each with its bone and `curl(deg)`)
 * until they touch: all of them together in the fist's proportions until one of its links touches,
 * then the joints past that link on, until the next does, out to the tip. `gapOf(i)` is the gap of
 * link `i` to what it closes on. A link that never touches closes as near as it comes. Returns the
 * angles.
 */
function closeDigit(joints, spec, gapOf) {
  const { start: open, base, limit } = spec;
  const angles = open ? open.slice() : joints.map(() => 0);
  const set = () => {
    joints.forEach((j, i) => j.bone.quaternion.copy(j.curl(angles[i], spec)));
    joints[0].bone.updateMatrixWorld(true);
  };
  // The nearest of the links from `from` on, and which.
  const nearest = (from) => {
    let best = { gap: Infinity, link: -1 };
    for (let i = from; i < joints.length; i++) {
      const gap = gapOf(i);
      if (gap < best.gap) best = { gap, link: i };
    }
    return best;
  };
  let from = base.findIndex((b) => b > 0);
  while (from >= 0 && from < joints.length) {
    const start = angles.slice();
    const top = Math.max(...joints.map((_, i) => (i >= from ? (limit[i] - start[i]) / base[i] : 0)));
    const at = (c) => {
      for (let i = from; i < joints.length; i++) angles[i] = Math.min(limit[i], start[i] + c * base[i]);
      set();
      return nearest(from);
    };
    let near = at(0);
    if (near.gap <= 0) { from = near.link + 1; continue; }
    let lo = 0;
    let hi = null;
    let closest = [near.gap, 0];
    for (let c = Math.min(STEP, top); c <= top && top > 0; c = c < top ? Math.min(c + STEP, top) : Infinity) {
      near = at(c);
      if (near.gap <= 0) { hi = c; break; }
      if (near.gap < closest[0]) closest = [near.gap, c];
      lo = c;
    }
    // Short of it all the way: as near as it comes, then the joints past this one on.
    if (hi === null) { at(closest[1]); from += 1; continue; }
    for (let i = 0; i < 12; i++) {
      const m = (lo + hi) / 2;
      if (at(m).gap <= 0) hi = m; else lo = m;
    }
    from = at(hi).link + 1;
  }
  set();
  return angles.slice();
}

/**
 * Closes hand `k` round the bar it holds (`bar`: a point on its axis `at`, the axis `along`, its
 * `radius`): each finger, then the thumb, joint by joint until it touches the bar's surface. A
 * wrapped thumb stops on the fingers if it meets them first, as it does on a bar thinner than the
 * hand closes round. Returns each digit's angles, by name; `given` sets each digit it names so instead.
 */
export function closeHand(rig, k, bar, thumb, given = null) {
  const out = {};
  const digits = rig.digits[k];
  // The thumb with its root swung `swing` degrees, its other joints bending about the bar's line in
  // the hand's bind pose as that turns it.
  const toBind = rig.hand.bind[k].clone().multiply(rig.bones['hand_' + k].matrixWorld.clone().invert());
  const root = digits.thumb[0];
  const thumbAt = (swing) => {
    const own = THUMB[thumb] ?? THUMB.wrapped;
    const line = bar.along.clone().transformDirection(toBind).applyQuaternion(root.turn(swing, own).invert());
    if (line.dot(rig.hand.rest[k].bend) < 0) line.negate();
    return { ...own, start: [swing, ...own.start.slice(1)], limit: [swing, ...own.limit.slice(1)], bar: line };
  };
  for (const f of DIGITS) {
    const joints = digits[f];
    if (given?.[f]) {
      const spec = f === 'thumb' ? thumbAt(given[f][0]) : FINGER;
      joints.forEach((j, i) => j.bone.quaternion.copy(j.curl(given[f][i], spec)));
      joints[0].bone.updateMatrixWorld(true);
      out[f] = given[f];
      continue;
    }
    if (f !== 'thumb') {
      out[f] = closeDigit(joints, FINGER, (i) => barGap(rig, k, joints[i].bone.name, bar));
      continue;
    }
    let near = null;
    if (thumb === 'wrapped') {
      const fingers = [];
      for (const g of ['index', 'middle']) for (const j of digits[g]) eachPoint(rig, k, j.bone.name, (x, y, z) => fingers.push(new THREE.Vector3(x, y, z)));
      near = nearestOf(fingers);
    }
    // The ball of the thumb is seated with the palm, but its skin bends with the thumb's next
    // joint, so that joint stops if bending it presses the ball into the bar.
    const ball = rig.skin[k][joints[0].bone.name].filter((s) => s.pts.length === 1);
    const ballGap = () => {
      const at = drawn();
      let gap = Infinity;
      for (const s of ball) gap = Math.min(gap, outsideBar(bar, at(s)));
      return gap;
    };
    const gapOf = (i) => {
      if (i === 0) return Infinity;
      const name = joints[i].bone.name;
      let gap = Math.min(barGap(rig, k, name, bar), i === 1 ? ballGap() : Infinity);
      if (near) eachPoint(rig, k, name, (x, y, z) => { gap = Math.min(gap, near(x, y, z) - TOUCHING); });
      return gap;
    };
    // The root swung out as far as its grip has it, or back toward the palm until the thumb and its
    // ball clear the bar, where a bar slanting across the palm runs into them there.
    const clearAt = (swing) => {
      const spec = thumbAt(swing);
      joints.forEach((j, i) => j.bone.quaternion.copy(j.curl(spec.start[i], spec)));
      joints[0].bone.updateMatrixWorld(true);
      return Math.min(ballGap(), ...[1, 2].map((i) => barGap(rig, k, joints[i].bone.name, bar))) >= 0;
    };
    const start = (THUMB[thumb] ?? THUMB.wrapped).start[0];
    let swing = start;
    if (!clearAt(start)) {
      // Back in steps until clear, then home in on where it just clears, so it moves smoothly.
      let hi = start;
      let lo = start - 10;
      while (lo > start - SWING_BACK && !clearAt(lo)) [hi, lo] = [lo, lo - 10];
      if (clearAt(lo)) {
        for (let i = 0; i < 8; i++) {
          const m = (lo + hi) / 2;
          if (clearAt(m)) lo = m; else hi = m;
        }
        swing = lo;
      }
    }
    out[f] = closeDigit(joints, thumbAt(swing), gapOf);
  }
  return out;
}

/** A hand that holds nothing: open, or its fingers relaxed in a loose curl. */
export function relaxHand(rig, k, open) {
  for (const f of DIGITS) {
    const joints = rig.digits[k][f];
    const spec = f === 'thumb' ? THUMB.wrapped : FINGER;
    joints.forEach((j, i) => j.bone.quaternion.copy(j.curl(open ? 0 : spec.base[i] * 0.5, spec)));
    joints[0].bone.updateMatrixWorld(true);
  }
}
