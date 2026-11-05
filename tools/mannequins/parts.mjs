// The kits' part library (ADR 0007): the pieces a recipe puts together, each a function of its
// dimensions and its tone. Every part is at one level of detail (#127): rounded tubing on base
// plates, soft pads, a stack of separate plates on guide rods with a selector pin, pulleys in
// housings with the cable over them, and shaped bars and handles with grips; nothing that can't be
// seen at phone size (no bolts or seams).
//
// Each part is in one of five tones, a material the phone colours by name from the theme (`frame`,
// `vinyl`, `plate`, `metal`, `pulley`), plus `shadow`; the colours here are the light theme's, for
// viewing a clip anywhere else, and each tone's roughness ships as it is. Nothing is metallic: the
// stage has no reflections for metal to show.
//
// A part the body must not pass through is `solid`; one the hands hold is `held` too, so only the
// hands may close round it. The checks read both, testing a cylinder as one and anything else as
// its bounding box.
import * as THREE from 'three';
import { mergeGeometries } from 'three/addons/utils/BufferGeometryUtils.js';
import { V, X, UP, rad, sideOn } from './rig.mjs';

/**
 * The kits' tones by part (owner, #127), the graphite palette with no hue: the light theme's
 * colour, in sRGB, and the roughness.
 */
export const TONES = {
  frame: { color: 0x2e2e33, roughness: 0.5 }, // frames and uprights
  vinyl: { color: 0x1b1b1e, roughness: 0.95 }, // pads and grips
  plate: { color: 0x8a8a90, roughness: 0.8 }, // stack plates, and every other weight
  metal: { color: 0xc8c8cc, roughness: 0.3 }, // rods, bars, cables and pins
  pulley: { color: 0x5c5c62, roughness: 0.55 }, // pulleys and their housings
};

const standard = (name, color, roughness) => () => new THREE.MeshStandardMaterial({ name, color, roughness });

export const MATERIALS = {
  body: standard('body', 0xbabac1, 0.8),
  joint: standard('joint', 0x9a9aa1, 0.8),
  ...Object.fromEntries(Object.entries(TONES).map(([name, t]) => [name, standard(name, t.color, t.roughness)])),
};

/** One material of each tone, for one kit's parts. */
export const tones = () => Object.fromEntries(Object.keys(TONES).map((k) => [k, MATERIALS[k]()]));

/**
 * Each shape is made once per size and shared by every part of that size, in every kit, so a clip
 * writes it once and the bake shares it across clips (glb.mjs).
 */
const shapes = new Map();
const shape = (key, make) => {
  if (!shapes.has(key)) shapes.set(key, make());
  return shapes.get(key);
};

/** A length kept to a tenth of a millimetre, so the same size makes the same shape. */
export const mm = (v) => Math.round(v * 1e4) / 1e4;

/**
 * A shape with what the phone draws and nothing else: positions and normals, each vertex once.
 * Faces meeting at less than `crease` degrees share their normals, so a bevel or a rounded corner
 * shades smoothly and a flat face stays flat.
 */
export function soften(geometry, crease = 40) {
  const flat = geometry.index ? geometry.toNonIndexed() : geometry;
  const pos = flat.attributes.position;
  const key = (i) => `${Math.round(pos.getX(i) * 1e5)},${Math.round(pos.getY(i) * 1e5)},${Math.round(pos.getZ(i) * 1e5)}`;
  const corners = new Map();
  const faces = [];
  const a = new THREE.Vector3();
  const b = new THREE.Vector3();
  const c = new THREE.Vector3();
  for (let i = 0; i < pos.count; i += 3) {
    a.fromBufferAttribute(pos, i);
    b.fromBufferAttribute(pos, i + 1);
    c.fromBufferAttribute(pos, i + 2);
    // Its length is twice the face's area, which weights it.
    const n = b.clone().sub(a).cross(c.clone().sub(a));
    faces.push({ n, unit: n.clone().normalize(), area: n.length() });
  }
  for (let i = 0; i < pos.count; i++) {
    const k = key(i);
    if (!corners.has(k)) corners.set(k, []);
    corners.get(k).push(i);
  }
  const cos = Math.cos(rad(crease));
  const position = [];
  const normal = [];
  const index = [];
  const welded = new Map();
  for (let i = 0; i < pos.count; i++) {
    const own = faces[Math.floor(i / 3)];
    if (own.area < 1e-12) continue;
    const n = new THREE.Vector3();
    for (const j of corners.get(key(i))) {
      const f = faces[Math.floor(j / 3)];
      if (f.area >= 1e-12 && f.unit.dot(own.unit) > cos) n.add(f.n);
    }
    n.normalize();
    const w = `${key(i)}|${Math.round(n.x * 1e4)},${Math.round(n.y * 1e4)},${Math.round(n.z * 1e4)}`;
    if (!welded.has(w)) {
      welded.set(w, position.length / 3);
      position.push(pos.getX(i), pos.getY(i), pos.getZ(i));
      normal.push(n.x, n.y, n.z);
    }
    index.push(welded.get(w));
  }
  // A whole number of faces: a degenerate face is dropped with its three corners.
  const out = new THREE.BufferGeometry();
  out.setAttribute('position', new THREE.Float32BufferAttribute(position, 3));
  out.setAttribute('normal', new THREE.Float32BufferAttribute(normal, 3));
  out.setIndex(index);
  return out;
}

/** A cylinder kept a cylinder, for the checks, with no texture coordinates. */
function cylinder(radius, length, segments) {
  const g = new THREE.CylinderGeometry(radius, radius, length, segments);
  g.deleteAttribute('uv');
  return g;
}

/** A rounded rectangle `w` by `h`, centred, its corners rounded `r`. */
function roundedRect(w, h, r) {
  const s = new THREE.Shape();
  const x = -w / 2;
  const y = -h / 2;
  r = Math.min(r, w / 2, h / 2);
  s.moveTo(x + r, y);
  s.lineTo(x + w - r, y);
  s.quadraticCurveTo(x + w, y, x + w, y + r);
  s.lineTo(x + w, y + h - r);
  s.quadraticCurveTo(x + w, y + h, x + w - r, y + h);
  s.lineTo(x + r, y + h);
  s.quadraticCurveTo(x, y + h, x, y + h - r);
  s.lineTo(x, y + r);
  s.quadraticCurveTo(x, y, x + r, y);
  return s;
}

/**
 * A pad `w` (x) by `h` (y) by `d` (z), centred, every edge rounded `soft`: a cushion, a stack's
 * plate, a base plate or a housing's side.
 */
export function padGeometry(w, h, d, soft) {
  return shape(`pad:${mm(w)}:${mm(h)}:${mm(d)}:${soft}`, () => {
    const s = Math.min(soft, w / 2 - 1e-4, h / 2 - 1e-4, d / 2 - 1e-4);
    const g = new THREE.ExtrudeGeometry(roundedRect(w - 2 * s, d - 2 * s, Math.min(w, d) * 0.12), {
      depth: h - 2 * s, bevelEnabled: s > 0, bevelThickness: s, bevelSize: s, bevelSegments: 3, curveSegments: 4,
    });
    // Extruded along z, the bevel either side: centred, then stood up along y.
    g.translate(0, 0, -(h - 2 * s) / 2);
    g.rotateX(-Math.PI / 2);
    return soften(g);
  });
}

/** Square tubing `w` across, its corners rounded, `length` long along y and centred. */
export function tubeGeometry(w, length) {
  return shape(`tube:${mm(w)}:${mm(length)}`, () => {
    const g = new THREE.ExtrudeGeometry(roundedRect(w, w, w * 0.28), { depth: length, bevelEnabled: false, curveSegments: 3 });
    g.translate(0, 0, -length / 2);
    g.rotateX(-Math.PI / 2);
    return soften(g);
  });
}

/**
 * Hollow square tubing `w` across, `length` long along y and centred, its walls `wall` thick, with a
 * round hole `radius` across through each of its four faces at every height in `holes` (metres from its
 * middle), its top end closed when `capped`: an upright or a crossmember with its holes.
 */
export function holedTubeGeometry(w, length, holes, { wall = 0.003, radius = 0.0079, capped = false } = {}) {
  return shape(`holed:${mm(w)}:${mm(length)}:${wall}:${radius}:${capped}:${holes.map((h) => mm(h)).join(',')}`, () => {
    // One wall: a plate `across` wide and `length` tall, `wall` thick, with the holes through it, lying in the plane x-y with its outer face at z.
    const plate = (across) => {
      const s = new THREE.Shape();
      s.moveTo(-across / 2, -length / 2);
      s.lineTo(across / 2, -length / 2);
      s.lineTo(across / 2, length / 2);
      s.lineTo(-across / 2, length / 2);
      s.closePath();
      for (const h of holes) {
        const c = new THREE.Path();
        c.absarc(0, h, radius, 0, Math.PI * 2, true);
        s.holes.push(c);
      }
      const g = new THREE.ExtrudeGeometry(s, { depth: wall, bevelEnabled: false, curveSegments: 6 });
      g.translate(0, 0, -wall);
      return g;
    };
    const faces = [];
    // Front and back walls, the full width, outer faces at +z and -z.
    const front = plate(w);
    front.translate(0, 0, w / 2);
    const back = plate(w);
    back.rotateY(Math.PI);
    back.translate(0, 0, -w / 2);
    // Side walls, between them, outer faces at +x and -x.
    const side = plate(w - 2 * wall);
    side.rotateY(Math.PI / 2);
    side.translate(w / 2, 0, 0);
    const other = plate(w - 2 * wall);
    other.rotateY(-Math.PI / 2);
    other.translate(-w / 2, 0, 0);
    faces.push(front, back, side, other);
    if (capped) {
      const cap = new THREE.BoxGeometry(w, wall, w);
      cap.translate(0, length / 2 - wall / 2, 0);
      faces.push(cap);
    }
    for (const g of faces) g.deleteAttribute('uv');
    return soften(mergeGeometries(faces.map((g) => g.index ? g.toNonIndexed() : g)));
  });
}

/** Hollow square tubing with holes from A to B, a frame's member: see `holedTubeGeometry`, `holes` measured from A. */
export function holedTube(w, A, B, holes, material, opts = {}) {
  const length = A.distanceTo(B);
  const t = part(holedTubeGeometry(w, length, holes.map((h) => h - length / 2), opts), material, { solid: true });
  orient(t, A, B);
  return t;
}

/** A turned shape about x from a profile of [radius, x] points: a plate, a pulley wheel, a roller. */
function turned(key, profile, segments) {
  return shape(key, () => {
    const g = new THREE.LatheGeometry(profile.map(([r, a]) => new THREE.Vector2(r, a)), segments);
    g.rotateZ(Math.PI / 2);
    return soften(g);
  });
}

/** A weight plate `R` in radius and `t` thick about x: a rim, a recessed face and a raised hub. */
export function plateGeometry(R, t) {
  const h = t / 2;
  const bore = 0.026;
  const hub = 0.07;
  const rim = Math.min(0.025, R * 0.12);
  const face = t * 0.32;
  return turned(`plate:${R}:${t}`, [
    [bore, -h], [hub, -h], [hub + 0.006, -face], [R - rim, -face], [R - rim + 0.006, -h], [R - 0.004, -h], [R, -h + 0.004],
    [R, h - 0.004], [R - 0.004, h], [R - rim + 0.006, h], [R - rim, face], [hub + 0.006, face], [hub, h], [bore, h], [bore, -h],
  ], 48);
}

/** A pulley wheel `r` in radius and `w` wide about x, grooved for the cable. */
export const wheelGeometry = (r, w) => turned(`wheel:${r}:${w}`, [
  [0.012, -w / 2], [r, -w / 2], [r, -w * 0.3], [r * 0.8, 0], [r, w * 0.3], [r, w / 2], [0.012, w / 2], [0.012, -w / 2],
], 32);

/** A padded roller `r` in radius and `w` wide about x, its ends rounded. */
export const rollerGeometry = (r, w) => turned(`roller:${r}:${w}`, [
  [0.012, -w / 2], [r * 0.8, -w / 2], [r * 0.96, -w / 2 + r * 0.06], [r, -w / 2 + r * 0.22], [r, w / 2 - r * 0.22], [r * 0.96, w / 2 - r * 0.06], [r * 0.8, w / 2], [0.012, w / 2],
], 28);

/** A hex dumbbell's head, `r` to its corners and `length` long along x, its edges bevelled; flat on a flat face. */
export function hexGeometry(r, length) {
  return shape(`hex:${r}:${length}`, () => {
    const b = 0.004;
    const s = new THREE.Shape();
    for (let k = 0; k < 6; k++) {
      const a = (k * Math.PI) / 3;
      if (k) s.lineTo((r - b) * Math.cos(a), (r - b) * Math.sin(a));
      else s.moveTo(r - b, 0);
    }
    s.closePath();
    const g = new THREE.ExtrudeGeometry(s, { depth: length - 2 * b, bevelEnabled: true, bevelThickness: b, bevelSize: b, bevelSegments: 2 });
    g.translate(0, 0, -(length - 2 * b) / 2);
    g.rotateY(Math.PI / 2);
    return soften(g);
  });
}

/** A part: a mesh of `geometry` in `material`, named later by its kit. */
export function part(geometry, material, { solid = false, held = false } = {}) {
  const mesh = new THREE.Mesh(geometry, material);
  mesh.userData = { solid: solid || held, held };
  return mesh;
}

/** A unit-long cylinder along y, for a rod or cable laid between two points by `span`. */
export const rodGeometry = (radius, segments = 16) => shape(`rod:${radius}:${segments}`, () => cylinder(radius, 1, segments));

/** Stands a mesh along y from A to B, turned to the line between them. */
export function orient(mesh, A, B) {
  const d = B.clone().sub(A);
  const length = Math.max(d.length(), 1e-4);
  mesh.position.copy(A).addScaledVector(d, 0.5);
  mesh.quaternion.setFromUnitVectors(UP, d.divideScalar(length));
  return length;
}

/** Lays a unit rod (or unit tubing) from A to B, stretched to their distance. */
export function span(mesh, A, B) {
  mesh.scale.set(1, orient(mesh, A, B), 1);
}

/** A rod from A to B. */
export function rod(radius, A, B, material, opts) {
  const r = part(rodGeometry(radius), material, opts);
  span(r, A, B);
  return r;
}

/** Square tubing `w` across from A to B, made to its length: a frame's member. */
export function tube(w, A, B, material, opts = { solid: true }) {
  const t = part(tubeGeometry(w, A.distanceTo(B)), material, opts);
  orient(t, A, B);
  return t;
}

/** Tubing `w` across, a unit long, for a member whose ends are known only once the kit is placed (`span`). */
export const stretchedTube = (w, material) => part(tubeGeometry(w, 1), material, { solid: true });

/** A pad `w` × `h` × `d` at `at`, its edges rounded `soft`. */
export function pad(w, h, d, material, at, { soft = 0.02, solid = true } = {}) {
  const mesh = part(padGeometry(w, h, d, soft), material, { solid });
  mesh.position.copy(at);
  return mesh;
}

/** A base plate under a member standing on the floor at (x, z). */
export const basePlate = (x, z, M) => pad(0.11, 0.012, 0.11, M.frame, V(x, 0.006, z), { soft: 0.003, solid: false });

/** A rubber cap on the end at `x` of a floor rail 7 cm across lying along x at `z`. */
export const endCap = (x, z, M) => pad(0.016, 0.074, 0.074, M.vinyl, V(x + Math.sign(x) * 0.008, 0.037, z), { soft: 0.005, solid: false });

/** A cap's size and place on the end `at` of a floor rail running `d` (a unit vector along x or z). */
export function railCap(cap, at, d) {
  const alongX = Math.abs(d.x) > Math.abs(d.z);
  cap.scale.set(alongX ? 1 : 0.074 / 0.016, 1, alongX ? 0.016 / 0.074 : 1);
  cap.position.copy(at).addScaledVector(d, 0.008).setY(0.037);
}

/** A floor rail 7 cm across from A to B along x or z, a rubber cap on each end. */
export function floorRail(A, B, M) {
  const d = B.clone().sub(A).normalize();
  const caps = [0, 1].map(() => pad(0.016, 0.074, 0.074, M.vinyl, V(0, 0, 0), { soft: 0.005, solid: false }));
  railCap(caps[0], A, d.clone().negate());
  railCap(caps[1], B, d);
  return [tube(0.07, A, B, M.frame), ...caps];
}

/** A floor rail along x whose place is known only once the kit is placed: `set(z, half)` lays it at z, `half` either side. */
export function movableRail(M) {
  const rail = stretchedTube(0.07, M.frame);
  const caps = [0, 1].map(() => pad(0.016, 0.074, 0.074, M.vinyl, V(0, 0, 0), { soft: 0.005, solid: false }));
  return {
    parts: [rail, ...caps],
    set(z, half) {
      span(rail, V(-half, 0.035, z), V(half, 0.035, z));
      railCap(caps[0], V(-half, 0, z), V(-1, 0, 0));
      railCap(caps[1], V(half, 0, z), X);
    },
  };
}

/** A pop-pin's knob at `at`, sticking out along z toward `toward` (±1): what sets a seat's height. */
export function popPin(at, toward, M) {
  const g = shape('pop_pin', () => { const c = cylinder(0.014, 0.03, 16); c.rotateX(Math.PI / 2); return c; });
  const knob = part(g, M.vinyl);
  knob.position.copy(at).add(V(0, 0, toward * 0.015));
  return knob;
}

/** A cylinder along x, from `from` to `to`. */
export function along(radius, from, to, material, segments, opts) {
  const g = shape(`along:${radius}:${mm(from)}:${mm(to)}:${segments}`, () => {
    const c = cylinder(radius, to - from, segments);
    c.rotateZ(Math.PI / 2);
    c.translate((from + to) / 2, 0, 0);
    return c;
  });
  return part(g, material, opts);
}

/** A turned part about x at `at`. */
export function turnedPart(geometry, material, at, opts = { solid: true }) {
  const mesh = part(geometry, material, opts);
  mesh.position.copy(at);
  return mesh;
}

/**
 * A kit's group, `name`, holding `parts`. Every part and everything under it is named, the ones not
 * named already `<name>_<n>` in order, so the animation finds each by its name.
 */
export function group(name, parts) {
  const g = new THREE.Group();
  g.name = name;
  parts.forEach((m) => g.add(m));
  nameAll(g, name);
  return g;
}

/** Names everything under `root` that has no name yet `<prefix>_<n>`, in order. */
export function nameAll(root, prefix) {
  let n = 0;
  root.traverse((o) => { if (o !== root && !o.name) o.name = `${prefix}_${n++}`; });
}

/**
 * A back pad `w` wide and `length` long, laid along the body's trunk at the top of the rep: its
 * face `behind` metres behind the line from the hips up the spine, its middle `up` metres up it.
 * A board backs the cushion.
 */
export function backPad(w, length, M, { up, behind }) {
  const t = 0.08;
  const holder = new THREE.Group();
  const cushion = part(padGeometry(w, length, t, 0.025), M.vinyl, { solid: true });
  const board = part(padGeometry(w - 0.04, length - 0.04, 0.012, 0.003), M.frame);
  board.position.z = -(t / 2 + 0.006);
  holder.add(cushion, board);
  return {
    mesh: holder,
    fit(P0) {
      const f = X.clone().cross(P0.u).normalize();
      holder.position.copy(P0.hipC).addScaledVector(P0.u, up).addScaledVector(f, -(behind + t / 2));
      holder.quaternion.setFromRotationMatrix(new THREE.Matrix4().makeBasis(X, P0.u, f));
      // The middle of the board's back, where the pad's support meets it.
      return holder.position.clone().addScaledVector(f, -(t / 2 + 0.012));
    },
  };
}

/** A seat: a cushion `w` × `d` whose top is at `top`, on a pan and a post of tubing up from `floor`. */
export function seatOn(top, z, M, { w = 0.36, d = 0.38, floor = 0.07, pin = 0 } = {}) {
  return [
    pad(w, 0.075, d, M.vinyl, V(0, top - 0.0375, z), { soft: 0.024 }),
    pad(w - 0.06, 0.012, d - 0.06, M.frame, V(0, top - 0.081, z), { soft: 0.003 }),
    tube(0.06, V(0, floor, z), V(0, top - 0.087, z), M.frame),
    // With `pin` (±1), the pop-pin that sets its height, out of the post that way along z.
    ...(pin ? [popPin(V(0, (floor + top) / 2 - 0.05, z + pin * 0.03), pin, M)] : []),
  ];
}

/** The radius of a cable's pulley wheels. */
export const WHEEL = 0.045;

/** A pulley wheel at `at`, about x, between the two sides of its housing, which reaches `up` above the axle. */
export function pulley(at, M, up = WHEEL + 0.02) {
  const parts = [turnedPart(wheelGeometry(WHEEL, 0.022), M.pulley, at, { solid: false })];
  const h = up + 0.03;
  for (const s of [-1, 1]) parts.push(pad(0.006, h, 0.09, M.pulley, V(s * 0.017, at.y - 0.03 + h / 2, at.z), { soft: 0.002, solid: false }));
  parts.push(along(0.006, -0.024, 0.024, M.metal, 10).translateY(at.y).translateZ(at.z));
  return parts;
}

/**
 * A weight stack at `at` (x, z) on two guide rods up to `rodTop`: twelve plates, the selector pin
 * in the sixth from the top and its knob out toward `toward` (z, ±1), a head plate on top, and the
 * stem down through the plates from it. `lift(l)` raises the pinned plates, the head and the stem
 * `l` metres; `top(l)` is where the cable meets the head plate.
 */
export function weightStack(at, M, { rodTop, toward }) {
  const n = 12;
  const thick = 0.032;
  const step = thick + 0.003;
  const base = 0.1;
  const pinned = 6;
  const plates = [];
  for (let i = 0; i < n; i++) {
    const y = base + i * step + thick / 2;
    plates.push({ mesh: pad(0.26, thick, 0.11, M.plate, V(at.x, y, at.z), { soft: 0.003 }), y, moves: i >= n - pinned });
  }
  const headY = base + n * step + 0.025;
  const head = pad(0.28, 0.05, 0.12, M.plate, V(at.x, headY, at.z), { soft: 0.006 });
  head.userData.stack = true;
  const stem = part(rodGeometry(0.007, 12), M.metal);
  const pin = part(rodGeometry(0.005, 10), M.metal);
  const knob = part(shape('knob', () => { const g = new THREE.SphereGeometry(0.016, 14, 10); g.deleteAttribute('uv'); return g; }), M.vinyl);
  const pinY = plates[n - pinned].y;
  const fixed = [-1, 1].flatMap((s) => [
    rod(0.009, V(at.x + s * 0.1, 0.07, at.z), V(at.x + s * 0.1, rodTop, at.z), M.metal),
    pad(0.05, 0.03, 0.05, M.vinyl, V(at.x + s * 0.1, 0.085, at.z), { soft: 0.008 }),
  ]);
  const lift = (l) => {
    for (const p of plates) p.mesh.position.y = p.y + (p.moves ? l : 0);
    head.position.y = headY + l;
    span(stem, V(at.x, pinY - 0.02 + l, at.z), V(at.x, headY + l, at.z));
    span(pin, V(at.x, pinY + l, at.z - toward * 0.03), V(at.x, pinY + l, at.z + toward * 0.1));
    knob.position.set(at.x, pinY + l, at.z + toward * 0.1);
  };
  lift(0);
  return { parts: [...plates.map((p) => p.mesh), head, stem, pin, knob, ...fixed], lift, idle() { head.userData.stack = false; }, top: (l) => V(at.x, headY + 0.025 + l, at.z) };
}

/** How far either side of a weight stack its frame's uprights stand. */
export const UPRIGHTS = 0.19;

/** A weight stack's frame at z `z`: an upright either side on its base plate, up to a crossbar at `top`. */
export function stackFrame(z, top, M) {
  return [
    ...[-1, 1].flatMap((s) => [
      tube(0.07, V(s * UPRIGHTS, 0.082, z), V(s * UPRIGHTS, top + 0.035, z), M.frame),
      pad(0.11, 0.012, 0.11, M.frame, V(s * UPRIGHTS, 0.076, z), { soft: 0.003 }),
    ]),
    tube(0.07, V(-UPRIGHTS - 0.035, top, z), V(UPRIGHTS + 0.035, top, z), M.frame),
  ];
}

/** A shaped tube along a smooth path through `points`. */
export function bent(key, points, radius, material, { segments = 16, ...opts } = {}) {
  const g = shape(key, () => soften(new THREE.TubeGeometry(new THREE.CatmullRomCurve3(points, false, 'catmullrom', 0.3), segments, radius, 12, false)));
  return part(g, material, opts);
}

/** The ring the cable hooks onto, standing at `y` over an attachment's middle. */
export function clevis(M, y) {
  const ring = part(shape('clevis', () => soften(new THREE.TorusGeometry(0.016, 0.0045, 8, 18))), M.metal);
  ring.position.y = y;
  return ring;
}

/**
 * A cable's attachments, each built along x round its middle and held there, and how far above
 * that middle the cable hooks on.
 */
export const ATTACHMENTS = {
  // A straight bar, its ends capped.
  straight_bar: (M) => ({
    parts: [along(0.014, -0.25, 0.25, M.metal, 16, { held: true }), ...[-1, 1].map((s) => along(0.017, ...(s < 0 ? [-0.27, -0.25] : [0.25, 0.27]), M.vinyl, 16)), clevis(M, 0.03)],
    hook: 0.045,
  }),
  // A long bar with grips, its ends bending down and out.
  lat_bar: (M) => ({
    parts: [
      along(0.014, -0.46, 0.46, M.metal, 16, { held: true }),
      ...[-1, 1].flatMap((s) => [
        along(0.0165, ...(s < 0 ? [-0.46, -0.3] : [0.3, 0.46]), M.vinyl, 16, { held: true }),
        bent(`lat_bar_end:${s}`, [V(s * 0.46, 0, 0), V(s * 0.52, -0.012, 0), V(s * 0.57, -0.038, 0), V(s * 0.6, -0.07, 0)], 0.014, M.metal, { held: true }),
      ]),
      clevis(M, 0.03),
    ],
    hook: 0.045,
  }),
  // Two stirrups `spread` either side of the middle, their grips along z, on straps up to the hook.
  handles: (M, { spread = 0.2 } = {}) => ({
    parts: [-1, 1].flatMap((s) => {
      const handle = stirrup(M, `two_handles_${s < 0 ? 'r' : 'l'}`);
      handle.position.x = s * spread;
      handle.rotation.y = Math.PI / 2;
      return [handle, rod(0.006, V(s * spread, STIRRUP, 0), V(0, 0.3, 0), M.vinyl)];
    }),
    hook: 0.3,
  }),
  // A short handle with a grip, its sides meeting above it where the cable hooks on.
  v_handle: (M) => ({
    parts: [along(0.014, -0.07, 0.07, M.metal, 16, { held: true }), along(0.018, -0.055, 0.055, M.vinyl, 16, { held: true }), ...[-1, 1].map((s) => rod(0.012, V(s * 0.07, 0, 0), V(0, 0.12, 0), M.metal))],
    hook: 0.12,
  }),
};

/** The rollers over the knees of a seated pull, at [`ty`, `tz`], on a post up from the floor. */
export function thighRollers(ty, tz, M) {
  return [
    tube(0.05, V(0, 0.07, tz + 0.1), V(0, ty + 0.025, tz + 0.1), M.frame),
    tube(0.04, V(0, ty, tz + 0.12), V(0, ty, tz), M.frame),
    along(0.012, -0.235, 0.235, M.metal, 12).translateY(ty).translateZ(tz),
    ...[-1, 1].map((s) => turnedPart(rollerGeometry(0.05, 0.2), M.vinyl, V(s * 0.13, ty, tz))),
  ];
}

/**
 * A stirrup handle, built round its grip: the grip along x, held, with a loop from its ends up to
 * the ring the cable hooks on, `STIRRUP` above the grip.
 */
export function stirrup(M, name) {
  const g = new THREE.Group();
  g.name = name;
  g.add(along(0.012, -0.065, 0.065, M.metal, 12, { held: true }));
  g.add(along(0.0165, -0.05, 0.05, M.vinyl, 16, { held: true }));
  g.add(bent('stirrup', [V(-0.065, 0, 0), V(-0.062, 0.07, 0), V(0, 0.118, 0), V(0.062, 0.07, 0), V(0.065, 0, 0)], 0.006, M.metal));
  g.add(clevis(M, 0.13));
  nameAll(g, name);
  return g;
}
export const STIRRUP = 0.145;

/** Turns `object` so its x runs along `along` and its y toward `toward`, as near as square allows. */
export function turnTo(object, along, toward) {
  const x = along.clone().normalize();
  const y = toward.clone().addScaledVector(x, -toward.dot(x)).normalize();
  object.quaternion.setFromRotationMatrix(new THREE.Matrix4().makeBasis(x, y, x.clone().cross(y)));
}

/** A round knob, `r` across, at `at`. */
export function knob(M, at, r = 0.016) {
  const k = part(shape(`knob:${r}`, () => { const g = new THREE.SphereGeometry(r, 14, 10); g.deleteAttribute('uv'); return g; }), M.vinyl);
  k.position.copy(at);
  return k;
}

/**
 * A rope for the `hands` named: each strand from the ring to the fist, through it, and out to its end stop,
 * 28 mm rope with 60 mm stops (docs/research/grip.md, section 4.4). `lay` runs it from `hook` to `P`'s
 * hands, and `reversed` runs it through each fist the other way.
 */
export function ropeParts(M, { reversed = false, hands = [-1, 1] } = {}) {
  const strands = hands.map((s) => ({
    s, strand: part(rodGeometry(0.014, 12), M.vinyl), fist: part(rodGeometry(0.014, 12), M.vinyl, { held: true }), end: knob(M, V(0, 0, 0), 0.03),
  }));
  const ring = clevis(M, 0);
  return {
    ring,
    parts: [...strands.flatMap(({ strand, fist, end }) => [strand, fist, end]), ring],
    lay(hook, P) {
      ring.position.copy(hook);
      // The rope bends at the fist: it runs through it along the knuckles (`P.held`, from the
      // little finger's side to the index finger's), in at the index finger's side and out at
      // the little finger's, where its end stop sits against the hand.
      strands.forEach(({ s, strand, fist, end }) => {
        const h = P.hand(s);
        const across = (P.held?.[s] ?? h.clone().sub(hook)).clone().normalize().multiplyScalar(reversed ? -1 : 1);
        const into = h.clone().addScaledVector(across, 0.05);
        const out = h.clone().addScaledVector(across, -0.06);
        span(strand, hook, into);
        span(fist, into, out);
        end.position.copy(out).addScaledVector(across, -0.03);
      });
    },
  };
}

/**
 * Square tubing `w` across, its corners rounded, swept along the polyline `points`, each bend cut
 * on its bisector so the two lengths meet in a mitre, not a lap. Built in the world, where the
 * points are. `ends` gives the plane's normal each end is cut square to, where a length lands on a
 * face (`[start, end]`), in place of square to its own length.
 */
export function mitered(points, w, material, { solid = true, ends = [] } = {}) {
  const r = w * 0.28;
  const profile = [];
  for (const [cx, cy, from] of [[1, 1, 0], [-1, 1, 90], [-1, -1, 180], [1, -1, 270]]) {
    for (let k = 0; k <= 3; k++) {
      const a = ((from + k * 30) * Math.PI) / 180;
      profile.push([cx * (w / 2 - r) + r * Math.cos(a), cy * (w / 2 - r) + r * Math.sin(a)]);
    }
  }
  const n = points.length - 1;
  const dirs = points.slice(1).map((p, i) => p.clone().sub(points[i]).normalize());
  // A side and an up for each length, carried round each bend so the section follows it.
  const up = Math.abs(dirs[0].y) < 0.99 ? V(0, 1, 0) : V(0, 0, 1);
  const u0 = up.addScaledVector(dirs[0], -up.dot(dirs[0])).normalize();
  const frames = [{ u: u0, s: u0.clone().cross(dirs[0]) }];
  for (let i = 1; i < n; i++) {
    const q = new THREE.Quaternion().setFromUnitVectors(dirs[i - 1], dirs[i]);
    frames.push({ u: frames[i - 1].u.clone().applyQuaternion(q), s: frames[i - 1].s.clone().applyQuaternion(q) });
  }
  // Each point's section: the length before it (the first, at the start) laid on the bisector's
  // plane, or the end's own plane at the ends.
  const rings = points.map((P, k) => {
    const i = Math.max(k - 1, 0);
    const plane = k === 0 ? ends[0] ?? dirs[0] : k === n ? ends[1] ?? dirs[i] : dirs[i].clone().add(dirs[k]).normalize();
    return profile.map(([x, y]) => {
      const Q = P.clone().addScaledVector(frames[i].s, x).addScaledVector(frames[i].u, y);
      return Q.addScaledVector(dirs[i], P.clone().sub(Q).dot(plane) / dirs[i].dot(plane));
    });
  });
  const m = profile.length;
  const position = rings.flat().flatMap((p) => p.toArray());
  const index = [];
  for (let k = 0; k < n; k++) {
    for (let i = 0; i < m; i++) {
      const [a, b, c, d] = [k * m + i, k * m + ((i + 1) % m), (k + 1) * m + i, (k + 1) * m + ((i + 1) % m)];
      index.push(a, b, c, b, d, c);
    }
  }
  // Flat caps on their own vertices, so the ends keep a crisp edge.
  for (const [k, flip] of [[0, true], [n, false]]) {
    const at = position.length / 3;
    const centre = points[k];
    position.push(...centre.toArray(), ...rings[k].flatMap((p) => p.toArray()));
    for (let i = 0; i < m; i++) index.push(at, at + 1 + (flip ? (i + 1) % m : i), at + 1 + (flip ? i : (i + 1) % m));
  }
  const g = new THREE.BufferGeometry();
  g.setAttribute('position', new THREE.Float32BufferAttribute(position, 3));
  g.setIndex(index);
  g.computeVertexNormals();
  return part(g, material, { solid });
}

/** A lever's hub at `at`, about x: a turned boss in the pulley tone. */
export const hub = (M, at) => turnedPart(wheelGeometry(0.055, 0.05), M.pulley, at, { solid: false });


/**
 * A pivot at `at`, turning about `axis` (a unit vector): a bearing housing round the axle, `housing`,
 * which belongs to the moving body, and the axle through it, `fixed`, which the frame holds.
 */
export function pivotJoint(M, at, axis) {
  const on = (from, to) => [from, to].map((d) => at.clone().addScaledVector(axis, d));
  return {
    housing: rod(0.04, ...on(-0.03, 0.03), M.frame),
    fixed: [rod(0.014, ...on(-0.07, 0.08), M.metal)],
  };
}

/** A weight plate on a horn, the horn from `from` out along x on side `side`; `place(from)` moves both. */
export function loadedHorn(M, side) {
  const horn = part(rodGeometry(0.025), M.metal);
  const plate = part(plateGeometry(0.17, 0.04), M.plate, { solid: true });
  return {
    parts: [horn, plate],
    place(from) {
      span(horn, from, from.clone().add(V(side * 0.22, 0, 0)));
      plate.position.copy(from).add(V(side * 0.12, 0, 0));
    },
  };
}

/** A cable laid each frame between two points by `span`. */
export const wire = (M) => part(rodGeometry(0.0045, 8), M.metal);

/** A pulley wheel in its housing at `at`, turning about x, or about z with `aboutZ`. */
export function wheelAt(M, at, { aboutZ = false } = {}) {
  const g = new THREE.Group();
  g.add(...pulley(V(0, 0, 0), M));
  g.position.copy(at);
  if (aboutZ) g.rotation.y = Math.PI / 2;
  return g;
}

/** A floor wheel in its housing at (x, z) on a floor rail, turning about z: where a cable turns along the rail. */
export function floorWheel(M, x, z) {
  const at = V(x, 0.07 + 0.012 + WHEEL, z);
  return { at, parts: [wheelAt(M, at, { aboutZ: true }), pad(0.12, 0.012, 0.06, M.frame, V(x, 0.076, z), { soft: 0.003, solid: false })] };
}

/** How tall the leg machines' towers stand, to the top of their caps. */
export const TOWER = 2.05;

/**
 * A selectorised tower standing beside the user at (x, z), as on the reference machines (#149): its
 * uprights either side of the stack along z and the plates facing the user, a wheel over the stack
 * under its cap and one out past the upright toward `toward` (z, ±1), the cable over both and down
 * that end to a wheel on the floor rail at `dropZ`, which turns it along the rail toward the user.
 * `lift(l)` raises the stack and lays the cable up to it; `run` is where the cable leaves the floor
 * wheel.
 */
export function sideTower(M, { x, z, toward }) {
  const tower = new THREE.Group();
  const weights = weightStack(V(0, 0, 0), M, { rodTop: TOWER - 0.27, toward: Math.sign(x) });
  tower.add(
    ...stackFrame(0, TOWER - 0.035, M),
    tube(0.05, V(-UPRIGHTS, TOWER - 0.245, 0), V(UPRIGHTS, TOWER - 0.245, 0), M.frame),
    ...weights.parts,
  );
  // Turned a quarter, so the uprights stand along z and the plates face the user.
  tower.position.set(x, 0, z);
  tower.rotation.y = -Math.PI / 2;
  const wy = TOWER - 0.07 - WHEEL - 0.02;
  const over = V(x, wy, z + toward * WHEEL);
  const end = V(x, wy, z + toward * (UPRIGHTS + 0.035 + 0.02 + WHEEL));
  const dropZ = end.z + toward * WHEEL;
  const inward = -Math.sign(x);
  const low = floorWheel(M, x + inward * WHEEL, dropZ);
  const up = wire(M);
  return {
    dropZ,
    run: V(low.at.x, low.at.y - WHEEL, dropZ),
    parts: [
      tower,
      // The cap runs out over the end wheel.
      tube(0.07, V(x, TOWER - 0.035, z + toward * UPRIGHTS), V(x, TOWER - 0.035, end.z + toward * 0.06), M.frame),
      wheelAt(M, over), wheelAt(M, end), ...low.parts, up,
      rod(0.0045, V(x, wy + WHEEL, over.z), V(x, wy + WHEEL, end.z), M.metal),
      rod(0.0045, V(x, wy, dropZ), V(x, low.at.y, dropZ), M.metal),
    ],
    lift(l) {
      weights.lift(l);
      span(up, weights.top(l).add(V(x, 0, z)), V(x, wy, z));
    },
  };
}

/** The radius of the leg machines' rollers. */
export const ROLLER = 0.06;

/** How far a cam lever's roller stands off its line: forward of it, or `behind` it. */
const rollerOff = (behind) => (behind ? -1 : 1) * (0.05 + ROLLER);

/**
 * A cam lever turning about the knees, its arm `side` metres out (x) beside the legs: down the
 * shins `down` metres to a long roller on their front or, with `behind`, the backs of the legs, an
 * adjuster plate with its pop-pin at the pivot, and inboard the cam the stack's cable winds on, off
 * its axle so it is seen to turn. `place(P, P0)` turns it with the shins and returns the pivot, the
 * cam's centre and the roller's.
 */
export function camLever(M, side, { down, behind }) {
  const lever = new THREE.Group();
  const out = side + 0.05;
  const off = rollerOff(behind);
  const cam = V(side - 0.06, 0.03, behind ? 0.04 : -0.04);
  // Built about the pivot: y up the shins, z the way the knees straighten.
  lever.add(
    tube(0.05, V(out, 0, 0), V(out, -down, off), M.frame),
    turnedPart(wheelGeometry(0.055, 0.05), M.pulley, V(out, 0, 0), { solid: false }),
    pad(0.012, 0.2, 0.1, M.frame, V(out + 0.03, 0.06, 0), { soft: 0.004, solid: false }),
    knob(M, V(out + 0.05, 0.12, 0)),
    along(0.015, side - 0.09, out + 0.03, M.metal, 16),
    turnedPart(plateGeometry(0.12, 0.025), M.frame, cam, { solid: false }),
    along(0.012, -0.2, out, M.metal, 12).translateY(-down).translateZ(off),
    turnedPart(rollerGeometry(ROLLER, 0.36), M.vinyl, V(0, -down, off)),
    ...[-1, 1].map((s) => turnedPart(wheelGeometry(ROLLER + 0.006, 0.025), M.pulley, V(s * 0.19, -down, off), { solid: false })),
  );
  return {
    parts: [lever],
    camX: cam.x,
    /** Where the cam's centre is now, wherever the lever has been turned. */
    camAt() {
      lever.updateWorldMatrix(true, false);
      return lever.localToWorld(cam.clone());
    },
    place({ pivot, shin, front }) {
      lever.position.copy(pivot);
      lever.quaternion.setFromRotationMatrix(new THREE.Matrix4().makeBasis(X, shin.clone().negate(), front));
      return {
        pivot: pivot.clone().setX(side),
        cam: cam.clone().applyQuaternion(lever.quaternion).add(pivot),
        roller: V(0, -down, off).applyQuaternion(lever.quaternion).add(pivot),
      };
    },
  };
}

/** The cam's radius. */
export const CAM = 0.12;

/**
 * The cable from a tower's floor wheel along the rail to a second floor wheel under the cam, and up
 * to the cam's rim. `place(cam)` lays the last stretch to the cam where it is now.
 */
export function camCable(M, tower, camX) {
  const under = floorWheel(M, camX - WHEEL, tower.dropZ);
  const rise = wire(M);
  const from = V(camX, under.at.y, tower.dropZ);
  return {
    parts: [...under.parts, rise, rod(0.0045, tower.run, V(under.at.x, under.at.y - WHEEL, tower.dropZ), M.metal)],
    place(cam) {
      const toward = from.clone().sub(cam).setX(0).normalize();
      span(rise, from, cam.clone().addScaledVector(toward, CAM).setX(camX));
    },
  };
}

/**
 * The drive the leg extension and the leg curls share: the tower beside the user at `z`, its cable
 * leaving `toward` (±1 along z) over the floor rail across at `z0` to the cam lever about the knees
 * (`down` the shins to its roller, `behind` them for a curl), on a post on the near side, `side`.
 * `place` sets the lever for the rep and gives the pivot and the lever's joint; `tower` is the stack
 * the lever lifts.
 */
export function kneeDrive(M, { z, toward, down, behind }) {
  const side = 0.32;
  const tx = -0.5;
  const tower = sideTower(M, { x: tx, z, toward });
  const lever = camLever(M, side, { down, behind });
  const cable = camCable(M, tower, lever.camX);
  const z0 = tower.dropZ;
  const post = stretchedTube(0.06, M.frame);
  const pivotHub = hub(M, V(0, 0, 0));
  return {
    side, tx, z0, tower,
    lever: lever.parts,
    // The cable up to the cam, laid again where the lever is now.
    follow: () => cable.place(lever.camAt()),
    parts: [
      ...tower.parts, ...lever.parts, ...cable.parts, post, pivotHub,
      ...floorRail(V(tx - 0.12, 0.035, z0), V(side + 0.1, 0.035, z0), M),
      pad(0.11, 0.012, 0.11, M.frame, V(side, 0.076, z0), { soft: 0.003, solid: false }),
    ],
    place(P) {
      const { pivot, cam, roller } = lever.place(P.linkage);
      span(post, V(side, 0.082, z0), pivot);
      pivotHub.position.copy(pivot).setX(side - 0.01);
      cable.place(cam);
      // The lever turns from the stretch, where the stack rests.
      return { pivot, roller, joint: { value: P.linkage.value, origin: pivot.clone().setX(0) } };
    },
  };
}

/**
 * What a cam lever about `pivot` [y, z] has with its joint at a value: the way down it (`shin`, the
 * way a shin on its axis runs) and across it (`front`), its `roller` `down` along it, and how far
 * the roller stands off the lever's line (`off`, negative behind it). The lever's value turns it
 * from the side-on direction `rest`, the way `way` (1 toward down, -1 away from it).
 */
export const kneeLinkage = ([y, z], { rest, way, down, behind }) => (value) => {
  const pivot = V(0, y, z);
  const off = rollerOff(behind);
  const shin = sideOn(rest + way * value);
  const front = sideOn(rest + way * value - Math.PI / 2);
  return { pivot, shin, front, off, roller: pivot.clone().addScaledVector(shin, down).addScaledVector(front, off) };
};

/** The angle of `p` about the x axis through `pivot`, side-on: what a lever there has swung. */
export const swingOf = (pivot, p) => Math.atan2(p.y - pivot.y, p.z - pivot.z);

/** The point `length` metres from `pivot` at `angle` about the x axis, as `swingOf` measures. */
export const around = (pivot, angle, length) => pivot.clone().add(V(0, length * Math.sin(angle), length * Math.cos(angle)));

/** Handles `width` either side of the middle, `arm` metres round a pivot at [y, z], its levers at a value. */
export const swungHandles = ([y, z], arm, width) => (value) => {
  const pivot = V(0, y, z);
  return { pivot, handle: (s) => around(pivot, value, arm).setX(s * width) };
};

/**
 * A floor rail 7 cm across whose ends are known only once the kit is placed: `set(A, B)` lays it
 * from A to B along x or z.
 */
export function placedRail(M) {
  const rail = stretchedTube(0.07, M.frame);
  const caps = [0, 1].map(() => pad(0.016, 0.074, 0.074, M.vinyl, V(0, 0, 0), { soft: 0.005, solid: false }));
  return {
    parts: [rail, ...caps],
    set(A, B) {
      const d = B.clone().sub(A).normalize();
      span(rail, A.clone().setY(0.035), B.clone().setY(0.035));
      railCap(caps[0], A, d.clone().negate());
      railCap(caps[1], B, d);
    },
  };
}

/**
 * Two plate-loaded levers on hubs at `P.pivot` either side of the body, each ending in a handle held
 * at `P.handle(s)`: the arm out at `out` from the middle, a crossbar in to the grip, which lies along
 * the hand's knuckles. Each lever runs on from its hub `horn.length` metres, `horn.turn` degrees round
 * from its arm, to a horn out along x with a plate on it. `place(P)` returns the levers' joint: the
 * arms' angle about the pivot, up from level ahead, as `swingOf` measures it.
 */
export function levers(M, { out, horn: { length, turn } }) {
  const sides = [-1, 1].map((s) => ({
    s,
    boss: hub(M, V(0, 0, 0)),
    arm: stretchedTube(0.05, M.frame),
    cross: part(rodGeometry(0.016), M.metal),
    grip: part(rodGeometry(0.0165), M.vinyl, { held: true }),
    tail: stretchedTube(0.05, M.frame),
    load: loadedHorn(M, s),
  }));
  return {
    parts: sides.flatMap((l) => [l.boss, l.arm, l.cross, l.grip, l.tail, ...l.load.parts]),
    place(P) {
      let swung;
      for (const l of sides) {
        const pivot = P.pivot.clone().setX(l.s * out);
        const h = P.handle(l.s);
        const end = h.clone().setX(l.s * out);
        l.boss.position.copy(pivot);
        span(l.arm, pivot, end);
        span(l.cross, end, h);
        const along = (P.held?.[l.s] ?? X).clone().normalize();
        span(l.grip, h.clone().addScaledVector(along, -0.06), h.clone().addScaledVector(along, 0.06));
        const tip = around(pivot, swingOf(pivot, end) + rad(turn), length);
        span(l.tail, pivot, tip);
        l.load.place(tip);
        // Measured on round from level ahead, not wrapped where it reaches back past level behind.
        const a = swingOf(pivot, end);
        swung = { value: a < -Math.PI / 2 ? a + 2 * Math.PI : a, origin: pivot };
      }
      return swung;
    },
  };
}

/**
 * A pad in front of the chest at the top of the rep: its face `front` metres out from the line up
 * the spine, its middle `up` metres up it, on an arm from its back to the point `to(back)` returns.
 */
export function chestPad(M, { up, front }) {
  const t = 0.08;
  const holder = new THREE.Group();
  const cushion = part(padGeometry(0.34, 0.42, t, 0.025), M.vinyl, { solid: true });
  const board = part(padGeometry(0.3, 0.38, 0.012, 0.003), M.frame);
  board.position.z = t / 2 + 0.006;
  holder.add(cushion, board);
  const arm = stretchedTube(0.06, M.frame);
  return {
    parts: [holder, arm],
    fit(P0, to) {
      const f = X.clone().cross(P0.u).normalize();
      holder.position.copy(P0.hipC).addScaledVector(P0.u, up).addScaledVector(f, front + t / 2);
      holder.quaternion.setFromRotationMatrix(new THREE.Matrix4().makeBasis(X, P0.u, f));
      const back = holder.position.clone().addScaledVector(f, t / 2 + 0.012);
      span(arm, back, to(back));
    },
  };
}
