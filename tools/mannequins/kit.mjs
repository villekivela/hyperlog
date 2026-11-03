// Generic equipment, built in code from three.js shapes, and the floor shadow under the mannequin.
// Every kit is at one level of detail (#127): rounded tubing on base plates, soft pads, a stack of
// separate plates on guide rods with a selector pin, pulleys in housings with the cable over them,
// and shaped bars and handles with grips; nothing that can't be seen at phone size (no bolts or seams).
//
// Each part is in one of five tones, a material the phone colours by name from the theme (`frame`,
// `vinyl`, `plate`, `metal`, `pulley`), plus `shadow`; the colours here are the light theme's, for
// viewing a clip anywhere else, and each tone's roughness ships as it is. Nothing is metallic: the
// stage has no reflections for metal to show.
//
// A kit is built once per clip and placed on every frame from the pose frame (`place(P, top,
// bottom)`, with the frames at the top and the bottom of the rep for what moves relative to them,
// like a weight stack). A part the body must not pass through is `solid`; one the hands hold is
// `held` too, so only the hands may close round it. The checks read both, testing a cylinder as one and anything else as
// its bounding box.
import * as THREE from 'three';
import { V, X, UP, rad } from './rig.mjs';

/**
 * The kits' tones by part (owner, #127), the graphite palette with no hue: the light theme's
 * colour, in sRGB, and the roughness.
 */
const TONES = {
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
const tones = () => Object.fromEntries(Object.keys(TONES).map((k) => [k, MATERIALS[k]()]));

/**
 * The library's machines by kind: the kit a pose of that kind is made in, or `null` while it has
 * none (listed as missing in the README), and the library's exercises on that kind of machine.
 */
export const MACHINES = {
  leg_press: { kit: 'leg_press', exercises: ['leg_press', 'single_leg_leg_press', 'leg_press_calf_press'] },
  chest_press: { kit: 'chest_press', exercises: ['machine_chest_press', 'incline_machine_press'] },
  squat_machine: { kit: 'squat_machine', exercises: ['hack_squat', 'pendulum_squat', 'machine_squat'] },
  leg_extension: { kit: 'leg_extension', exercises: ['leg_extension'] },
  leg_curl: { kit: 'leg_curl', exercises: ['lying_leg_curl', 'seated_leg_curl'] },
  glute_ham_raise: { kit: null, exercises: ['glute_ham_raise'] },
  pec_deck: { kit: null, exercises: ['pec_deck', 'reverse_pec_deck', '1_arm_reverse_pec_deck'] },
  row_machine: { kit: 'row_machine', exercises: ['machine_row', 'chest_supported_machine_row', 'chest_supported_t_bar_row', 't_bar_row'] },
  pulldown_machine: { kit: 'pulldown_machine', exercises: ['machine_pulldown', 'machine_lat_pullover'] },
  shoulder_press: { kit: null, exercises: ['machine_shoulder_press'] },
  lateral_raise_machine: { kit: null, exercises: ['machine_lateral_raise', 'standing_machine_lateral_raise'] },
  preacher_curl_machine: { kit: 'preacher_curl_machine', exercises: ['machine_preacher_curl'] },
  dip_machine: { kit: null, exercises: ['seated_dip_machine'] },
  hip_machine: { kit: null, exercises: ['machine_hip_abduction', 'machine_adduction'] },
  hip_thrust_machine: { kit: null, exercises: ['machine_hip_thrust'] },
  calf_raise_machine: { kit: 'calf_raise_machine', exercises: ['standing_calf_raise', 'seated_calf_raise', 'donkey_calf_raise'] },
  crunch_machine: { kit: null, exercises: ['machine_crunch'] },
  shrug_machine: { kit: null, exercises: ['machine_shrug', 'kelso_shrug'] },
};

/**
 * The library's equipment, each with the kits a pose in it may use. A kind with no kit yet is
 * `null`, and is listed as missing in the README; a pose can't be made in it until it has one.
 * Bodyweight needs no kit for most of its exercises.
 */
export const EQUIPMENT = {
  barbell: ['barbell', 'bench', 'platform'],
  dumbbell: ['dumbbells', 'bench'],
  cable: ['cable_tower', 'cable_column', 'bench'],
  machine: Object.values(MACHINES).map((m) => m.kit).filter(Boolean),
  bodyweight: [],
  ez_bar: null,
  trap_bar: null,
  safety_bar: null,
  smith_machine: null,
  plate: null,
  weighted_bodyweight: null,
};

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
const mm = (v) => Math.round(v * 1e4) / 1e4;

/**
 * A shape with what the phone draws and nothing else: positions and normals, each vertex once.
 * Faces meeting at less than `crease` degrees share their normals, so a bevel or a rounded corner
 * shades smoothly and a flat face stays flat.
 */
function soften(geometry, crease = 40) {
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
function padGeometry(w, h, d, soft) {
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
function tubeGeometry(w, length) {
  return shape(`tube:${mm(w)}:${mm(length)}`, () => {
    const g = new THREE.ExtrudeGeometry(roundedRect(w, w, w * 0.28), { depth: length, bevelEnabled: false, curveSegments: 3 });
    g.translate(0, 0, -length / 2);
    g.rotateX(-Math.PI / 2);
    return soften(g);
  });
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
function plateGeometry(R, t) {
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
const wheelGeometry = (r, w) => turned(`wheel:${r}:${w}`, [
  [0.012, -w / 2], [r, -w / 2], [r, -w * 0.3], [r * 0.8, 0], [r, w * 0.3], [r, w / 2], [0.012, w / 2], [0.012, -w / 2],
], 32);

/** A padded roller `r` in radius and `w` wide about x, its ends rounded. */
const rollerGeometry = (r, w) => turned(`roller:${r}:${w}`, [
  [0.012, -w / 2], [r * 0.8, -w / 2], [r * 0.96, -w / 2 + r * 0.06], [r, -w / 2 + r * 0.22], [r, w / 2 - r * 0.22], [r * 0.96, w / 2 - r * 0.06], [r * 0.8, w / 2], [0.012, w / 2],
], 28);

/** A hex dumbbell's head, `r` to its corners and `length` long along x, its edges bevelled; flat on a flat face. */
function hexGeometry(r, length) {
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
function part(geometry, material, { solid = false, held = false } = {}) {
  const mesh = new THREE.Mesh(geometry, material);
  mesh.userData = { solid: solid || held, held };
  return mesh;
}

/** A unit-long cylinder along y, for a rod or cable laid between two points by `span`. */
const rodGeometry = (radius, segments = 16) => shape(`rod:${radius}:${segments}`, () => cylinder(radius, 1, segments));

/** Stands a mesh along y from A to B, turned to the line between them. */
function orient(mesh, A, B) {
  const d = B.clone().sub(A);
  const length = Math.max(d.length(), 1e-4);
  mesh.position.copy(A).addScaledVector(d, 0.5);
  mesh.quaternion.setFromUnitVectors(UP, d.divideScalar(length));
  return length;
}

/** Lays a unit rod (or unit tubing) from A to B, stretched to their distance. */
function span(mesh, A, B) {
  mesh.scale.set(1, orient(mesh, A, B), 1);
}

/** A rod from A to B. */
function rod(radius, A, B, material, opts) {
  const r = part(rodGeometry(radius), material, opts);
  span(r, A, B);
  return r;
}

/** Square tubing `w` across from A to B, made to its length: a frame's member. */
function tube(w, A, B, material, opts = { solid: true }) {
  const t = part(tubeGeometry(w, A.distanceTo(B)), material, opts);
  orient(t, A, B);
  return t;
}

/** Tubing `w` across, a unit long, for a member whose ends are known only once the kit is placed (`span`). */
const stretchedTube = (w, material) => part(tubeGeometry(w, 1), material, { solid: true });

/** A pad `w` × `h` × `d` at `at`, its edges rounded `soft`. */
function pad(w, h, d, material, at, { soft = 0.02, solid = true } = {}) {
  const mesh = part(padGeometry(w, h, d, soft), material, { solid });
  mesh.position.copy(at);
  return mesh;
}

/** A base plate under a member standing on the floor at (x, z). */
const basePlate = (x, z, M) => pad(0.11, 0.012, 0.11, M.frame, V(x, 0.006, z), { soft: 0.003, solid: false });

/** A rubber cap on the end at `x` of a floor rail 7 cm across lying along x at `z`. */
const endCap = (x, z, M) => pad(0.016, 0.074, 0.074, M.vinyl, V(x + Math.sign(x) * 0.008, 0.037, z), { soft: 0.005, solid: false });

/** A cap's size and place on the end `at` of a floor rail running `d` (a unit vector along x or z). */
function railCap(cap, at, d) {
  const alongX = Math.abs(d.x) > Math.abs(d.z);
  cap.scale.set(alongX ? 1 : 0.074 / 0.016, 1, alongX ? 0.016 / 0.074 : 1);
  cap.position.copy(at).addScaledVector(d, 0.008).setY(0.037);
}

/** A floor rail 7 cm across from A to B along x or z, a rubber cap on each end. */
function floorRail(A, B, M) {
  const d = B.clone().sub(A).normalize();
  const caps = [0, 1].map(() => pad(0.016, 0.074, 0.074, M.vinyl, V(0, 0, 0), { soft: 0.005, solid: false }));
  railCap(caps[0], A, d.clone().negate());
  railCap(caps[1], B, d);
  return [tube(0.07, A, B, M.frame), ...caps];
}

/** A floor rail along x whose place is known only once the kit is placed: `set(z, half)` lays it at z, `half` either side. */
function movableRail(M) {
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
function popPin(at, toward, M) {
  const g = shape('pop_pin', () => { const c = cylinder(0.014, 0.03, 16); c.rotateX(Math.PI / 2); return c; });
  const knob = part(g, M.vinyl);
  knob.position.copy(at).add(V(0, 0, toward * 0.015));
  return knob;
}

/** A cylinder along x, from `from` to `to`. */
function along(radius, from, to, material, segments, opts) {
  const g = shape(`along:${radius}:${mm(from)}:${mm(to)}:${segments}`, () => {
    const c = cylinder(radius, to - from, segments);
    c.rotateZ(Math.PI / 2);
    c.translate((from + to) / 2, 0, 0);
    return c;
  });
  return part(g, material, opts);
}

/** A turned part about x at `at`. */
function turnedPart(geometry, material, at, opts = { solid: true }) {
  const mesh = part(geometry, material, opts);
  mesh.position.copy(at);
  return mesh;
}

/**
 * A kit's group, `name`, holding `parts`. Every part and everything under it is named, the ones not
 * named already `<name>_<n>` in order, so the animation finds each by its name.
 */
function group(name, parts) {
  const g = new THREE.Group();
  g.name = name;
  parts.forEach((m) => g.add(m));
  nameAll(g, name);
  return g;
}

/** Names everything under `root` that has no name yet `<prefix>_<n>`, in order. */
function nameAll(root, prefix) {
  let n = 0;
  root.traverse((o) => { if (o !== root && !o.name) o.name = `${prefix}_${n++}`; });
}

/** A 20 kg barbell with a 20 and a 10 a side, along x, on the hands' bar: collars, sleeves and clamps. */
function barbell() {
  const M = tones();
  const parts = [along(0.014, -0.7, 0.7, M.metal, 24, { held: true })];
  for (const s of [-1, 1]) {
    const at = (a, b) => [Math.min(s * a, s * b), Math.max(s * a, s * b)];
    parts.push(along(0.038, ...at(0.69, 0.72), M.metal, 32, { solid: true }));
    parts.push(along(0.025, ...at(0.72, 1.075), M.metal, 24, { solid: true }));
    parts.push(along(0.021, ...at(1.075, 1.085), M.metal, 24, { solid: true }));
    parts.push(turnedPart(plateGeometry(0.225, 0.052), M.plate, V(s * 0.75, 0, 0)));
    parts.push(turnedPart(plateGeometry(0.17, 0.038), M.plate, V(s * 0.805, 0, 0)));
    parts.push(along(0.034, ...at(0.828, 0.85), M.metal, 24, { solid: true }));
  }
  const g = group('barbell', parts);
  return { group: g, place: (P) => g.position.copy(P.bar) };
}

/** A pair of hex dumbbells, one in each hand, the handle across the palm between collars. */
function dumbbells() {
  const M = tones();
  const bells = [-1, 1].map((s) => {
    const parts = [along(0.016, -0.075, 0.075, M.metal, 16, { held: true })];
    for (const e of [-1, 1]) {
      parts.push(along(0.024, ...(e < 0 ? [-0.079, -0.071] : [0.071, 0.079]), M.metal, 20, { solid: true }));
      parts.push(turnedPart(hexGeometry(0.06, 0.06), M.plate, V(e * 0.105, 0, 0)));
    }
    return group(`dumbbell_${s < 0 ? 'r' : 'l'}`, parts);
  });
  const g = new THREE.Group();
  g.name = 'dumbbells';
  bells.forEach((b) => g.add(b));
  return {
    group: g,
    place(P) {
      [-1, 1].forEach((s, i) => {
        bells[i].position.copy(P.hand(s));
        // The handle lies along the knuckles, as the hand turned and tipped it.
        bells[i].quaternion.setFromUnitVectors(X, (P.held?.[s] ?? P.across(s)).clone().normalize());
      });
    },
  };
}

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
 * A bench along z, `length` long and centred on `z`, the top of its pad `height` above the floor:
 * flat, or with its back pad raised `angle` degrees about a hinge at `hinge` (z; by default 42 cm
 * behind the seat's front), an adjustable bench. With `adjustable`, the back pad is a part of its
 * own, `bench_back`, standing at `angle` about the hinge, which the phone turns to the exact angle.
 * With `across` it stands across the body instead, its middle at that z (`z` is then along it).
 * The pads stand on legs of tubing with feet across, a spine along the floor and a rail under them.
 */
function bench({ angle = 0, height = 0.44, z = -0.15, length = 1.2, hinge = null, adjustable = false, across = null } = {}) {
  const M = tones();
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
  }
  const g = group('bench', parts);
  // Across the body, a hip thrust's: its length along x, its middle at z `across`.
  if (across !== null) {
    g.rotation.y = Math.PI / 2;
    g.position.set(0, 0, across);
  }
  if (turning) {
    g.add(turning);
    nameAll(turning, 'bench_back');
  }
  return { group: g, place() {} };
}

/**
 * A back pad `w` wide and `length` long, laid along the body's trunk at the top of the rep: its
 * face `behind` metres behind the line from the hips up the spine, its middle `up` metres up it.
 * A board backs the cushion.
 */
function backPad(w, length, M, { up, behind }) {
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
function seatOn(top, z, M, { w = 0.36, d = 0.38, floor = 0.07, pin = 0 } = {}) {
  return [
    pad(w, 0.075, d, M.vinyl, V(0, top - 0.0375, z), { soft: 0.024 }),
    pad(w - 0.06, 0.012, d - 0.06, M.frame, V(0, top - 0.081, z), { soft: 0.003 }),
    tube(0.06, V(0, floor, z), V(0, top - 0.087, z), M.frame),
    // With `pin` (±1), the pop-pin that sets its height, out of the post that way along z.
    ...(pin ? [popPin(V(0, (floor + top) / 2 - 0.05, z + pin * 0.03), pin, M)] : []),
  ];
}

/** The radius of a cable's pulley wheels. */
const WHEEL = 0.045;

/** A pulley wheel at `at`, about x, between the two sides of its housing, which reaches `up` above the axle. */
function pulley(at, M, up = WHEEL + 0.02) {
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
function weightStack(at, M, { rodTop, toward }) {
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
  return { parts: [...plates.map((p) => p.mesh), head, stem, pin, knob, ...fixed], lift, top: (l) => V(at.x, headY + 0.025 + l, at.z) };
}

/** How far either side of a weight stack its frame's uprights stand. */
const UPRIGHTS = 0.19;

/** A weight stack's frame at z `z`: an upright either side on its base plate, up to a crossbar at `top`. */
function stackFrame(z, top, M) {
  return [
    ...[-1, 1].flatMap((s) => [
      tube(0.07, V(s * UPRIGHTS, 0.082, z), V(s * UPRIGHTS, top + 0.035, z), M.frame),
      pad(0.11, 0.012, 0.11, M.frame, V(s * UPRIGHTS, 0.076, z), { soft: 0.003 }),
    ]),
    tube(0.07, V(-UPRIGHTS - 0.035, top, z), V(UPRIGHTS + 0.035, top, z), M.frame),
  ];
}

/**
 * How far a working stack is held off its rest at the stretch: only before the first rep does it sit
 * down.
 */
const HELD = 0.06;

/** A shaped tube along a smooth path through `points`. */
function bent(key, points, radius, material, opts) {
  const g = shape(key, () => soften(new THREE.TubeGeometry(new THREE.CatmullRomCurve3(points, false, 'catmullrom', 0.3), 16, radius, 12, false)));
  return part(g, material, opts);
}

/** The ring the cable hooks onto, standing at `y` over an attachment's middle. */
function clevis(M, y) {
  const ring = part(shape('clevis', () => soften(new THREE.TorusGeometry(0.016, 0.0045, 8, 18))), M.metal);
  ring.position.y = y;
  return ring;
}

/**
 * A cable's attachments, each built along x round its middle and held there, and how far above
 * that middle the cable hooks on.
 */
const ATTACHMENTS = {
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

/**
 * A cable station for a high pulley: two uprights either side of a weight stack `column` metres in
 * front (z, less 12 cm), a boom forward to the pulley at `pulley` [y, z], whose front the cable
 * meets, and a second wheel over the stack. The cable runs from the attachment in the hands over
 * both wheels and down to the stack, which rises as the cable is drawn out. `attachment` is
 * `lat_bar`, `straight_bar`, `v_handle` or `handles` (two stirrups `spread` either side); `seat`
 * [y, z] adds a seat and `thighPad` [y, z] the rollers over the knees, for a pulldown.
 */
function cableTower({ column = 0.9, pulley: [py, pz] = [2.2, 0], attachment = 'straight_bar', spread, seat = null, thighPad = null } = {}) {
  if (py < 1.5) throw new Error(`The cable tower's pulley at ${py} m is low; it has only a high pulley yet`);
  const M = tones();
  // The stack stands behind the pulley, far enough back for its own wheel.
  const stackZ = Math.max(column - 0.12, pz + 4 * WHEEL + 0.08);
  const ux = UPRIGHTS;
  const wheelAt = V(0, py, pz);
  const frontWheel = V(0, py, pz + WHEEL);
  const backWheel = V(0, py, stackZ - WHEEL);
  const boomY = py + WHEEL + 0.02 + 0.035;
  const rodTop = Math.min(2.0, boomY - 0.3);
  const frontZ = seat ? seat[1] - 0.25 : stackZ - 0.32;
  const parts = [
    ...stackFrame(stackZ, boomY, M),
    tube(0.07, V(0, boomY, stackZ + 0.07), V(0, boomY, Math.min(pz, stackZ) - 0.02), M.frame),
    tube(0.05, V(-ux, rodTop + 0.025, stackZ), V(ux, rodTop + 0.025, stackZ), M.frame),
    tube(0.07, V(-ux - 0.15, 0.035, stackZ), V(ux + 0.15, 0.035, stackZ), M.frame),
    tube(0.07, V(0, 0.035, stackZ - 0.035), V(0, 0.035, frontZ - 0.035), M.frame),
    tube(0.07, V(-0.3, 0.035, frontZ), V(0.3, 0.035, frontZ), M.frame),
    ...pulley(frontWheel, M),
    ...pulley(backWheel, M),
    // The cable over the wheels' tops, from the front one back to the one over the stack.
    rod(0.0045, V(0, py + WHEEL, frontWheel.z), V(0, py + WHEEL, backWheel.z), M.metal),
  ];
  const cable = part(rodGeometry(0.0045, 8), M.metal);
  const drop = part(rodGeometry(0.0045, 8), M.metal);
  const weights = weightStack(V(0, 0, stackZ), M, { rodTop, toward: -1 });
  if (seat) parts.push(...seatOn(seat[0], seat[1], M));
  else {
    // Standing at it, the tower has its low pulley too: a wheel in its housing on the floor rail in
    // front of the stack, its cable back under the stack and a ring at its front to hook on to, and
    // caps on the front rail's ends.
    const low = V(0, 0.07 + WHEEL + 0.012, stackZ - 0.17);
    parts.push(
      ...pulley(low, M),
      pad(0.06, 0.012, 0.12, M.frame, V(0, 0.076, low.z), { soft: 0.003, solid: false }),
      rod(0.0045, V(0, low.y - WHEEL, low.z), V(0, low.y - WHEEL, stackZ - 0.06), M.metal),
      clevis(M, 0).translateY(low.y).translateZ(low.z - WHEEL - 0.016).rotateY(Math.PI / 2),
      ...[-0.3, 0.3].map((x) => endCap(x, frontZ, M)),
    );
  }
  if (thighPad) parts.push(...thighRollers(thighPad[0], thighPad[1], M));
  if (!ATTACHMENTS[attachment]) throw new Error(`No cable attachment "${attachment}"`);
  const { parts: grip, hook: above } = ATTACHMENTS[attachment](M, { spread });
  const handle = new THREE.Group();
  handle.name = 'cable_handle';
  grip.forEach((m, i) => { m.name = `cable_handle_${i}`; handle.add(m); });
  const g = group('cable', [...parts, cable, drop, ...weights.parts, handle]);
  const hook = (P) => P.bar.clone().add(V(0, above, 0));
  return {
    group: g,
    place(P, P0) {
      handle.position.copy(P.bar);
      const at = hook(P);
      span(cable, at, wheelAt);
      const lift = HELD + Math.max(0, at.distanceTo(wheelAt) - hook(P0).distanceTo(wheelAt));
      weights.lift(lift);
      span(drop, V(0, py, stackZ), weights.top(lift));
    },
  };
}

/**
 * A 45° leg press: the seat and back pad (its face `pad` metres behind the spine), the rails, and
 * the sled with its foot plate, which rides the rails with the feet, loaded with a plate a side.
 * `P.plate` is the foot plate's centre, `P.sled` the sled's way up the rails and `P.plateUp` the
 * way up the plate's face.
 */
function legPress({ seat = [0.5, 0.04], pad: behind = 0.2 } = {}) {
  const M = tones();
  const backRest = backPad(0.42, 0.72, M, { up: 0.33, behind });
  const tilt = rad(-18);
  const seatPad = pad(0.42, 0.09, 0.38, M.vinyl, V(0, seat[0] - 0.03, seat[1]), { soft: 0.025 });
  seatPad.rotation.x = tilt;
  const seatBoard = pad(0.38, 0.012, 0.34, M.frame, V(0, seat[0] - 0.03, seat[1]).addScaledVector(V(0, Math.cos(tilt), Math.sin(tilt)), -0.051), { soft: 0.003, solid: false });
  seatBoard.rotation.x = tilt;
  // The base: a rail either side along the floor, tied across at the ends and under the seat, with
  // caps on the ends.
  const [back, front] = [-0.6, 1.7];
  const parts = [
    seatPad, seatBoard, backRest.mesh,
    ...[-0.3, 0.3].map((x) => tube(0.07, V(x, 0.035, back), V(x, 0.035, front), M.frame)),
    ...[back, seat[1] - 0.05, front].map((z) => tube(0.07, V(-0.38, 0.035, z), V(0.38, 0.035, z), M.frame)),
    ...[back, front].flatMap((z) => [endCap(-0.38, z, M), endCap(0.38, z, M)]),
    tube(0.08, V(0, 0.07, seat[1] - 0.05), V(0, seat[0] - 0.1, seat[1] - 0.05), M.frame),
  ];
  // Along the sled, from 70 cm below the foot plate's start to 45 cm above it: the rails, each on
  // a post at its foot and an upright at its head, with a brace, tied across at both ends.
  const RAIL = 1.15;
  const rails = [0, 1].map(() => part(tubeGeometry(0.06, RAIL), M.frame, { solid: true }));
  const posts = [0, 1].map(() => stretchedTube(0.06, M.frame));
  const uprights = [0, 1].map(() => stretchedTube(0.07, M.frame));
  const braces = [0, 1].map(() => stretchedTube(0.05, M.frame));
  const ties = [0, 1].map(() => stretchedTube(0.06, M.frame));
  // The carriage: a side plate over each rail, riding on two rollers a side, tied across under the
  // foot plate, which has a lip at its heel.
  const sides = [0, 1].map(() => pad(0.012, 0.14, 0.5, M.frame, V(0, 0, 0), { soft: 0.003 }));
  const rollers = [0, 1, 2, 3].map(() => turnedPart(wheelGeometry(0.035, 0.03), M.pulley, V(0, 0, 0), { solid: false }));
  const axles = [0, 1].map(() => part(rodGeometry(0.008, 10), M.metal));
  const cross = stretchedTube(0.06, M.frame);
  const plate = pad(0.62, 0.04, 0.52, M.frame, V(0, 0, 0), { soft: 0.008 });
  const lip = pad(0.62, 0.06, 0.03, M.frame, V(0, 0, 0), { soft: 0.006, solid: false });
  const horns = [0, 1].map(() => part(rodGeometry(0.025), M.metal));
  const weights = [0, 1].map(() => part(plateGeometry(0.17, 0.04), M.plate, { solid: true }));
  const collars = [0, 1].map(() => part(rodGeometry(0.034), M.metal));
  const handles = [0, 1].map(() => part(rodGeometry(0.016), M.metal, { held: true }));
  const grips = [0, 1].map(() => part(rodGeometry(0.0195), M.vinyl, { held: true }));
  const handlePosts = [0, 1].map(() => stretchedTube(0.04, M.frame));
  const support = stretchedTube(0.05, M.frame);
  const g = group('leg_press', [
    ...parts, ...rails, ...posts, ...uprights, ...braces, ...ties, ...sides, ...rollers, ...axles, cross, plate, lip,
    ...horns, ...weights, ...collars, ...handles, ...grips, ...handlePosts, support,
  ]);
  return {
    group: g,
    place(P, P0) {
      const board = backRest.fit(P0);
      // The back pad's support down from its board to the base's back tie.
      span(support, board, V(0, 0.07, back));
      const along = P.sled;
      const up = P.plateUp;
      const under = P0.plate.clone().addScaledVector(up, -0.3);
      const lo = under.clone().addScaledVector(along, -0.7);
      const hi = under.clone().addScaledVector(along, 0.45);
      [-1, 1].forEach((s, i) => {
        orient(rails[i], lo.clone().setX(s * 0.24), hi.clone().setX(s * 0.24));
        span(posts[i], lo.clone().setX(s * 0.24), V(s * 0.3, 0.07, lo.z));
        span(uprights[i], hi.clone().setX(s * 0.24).addScaledVector(along, 0.03), V(s * 0.3, 0.07, hi.z));
        span(braces[i], lo.clone().lerp(hi, 0.45).setX(s * 0.24), V(s * 0.3, 0.07, hi.z - 0.35));
        const end = i ? hi : lo;
        span(ties[i], end.clone().setX(-0.27), end.clone().setX(0.27));
        // The carriage rides the rail: its rollers on the rail's top, its side plate above them.
        const on = P.plate.clone().addScaledVector(up, -0.3 + 0.03 + 0.035).setX(s * 0.24);
        rollers[2 * i].position.copy(on).addScaledVector(along, -0.05);
        rollers[2 * i + 1].position.copy(on).addScaledVector(along, 0.32);
        sides[i].position.copy(P.plate).addScaledVector(along, 0.16).addScaledVector(up, -0.17).setX(s * 0.28);
        sides[i].quaternion.setFromRotationMatrix(new THREE.Matrix4().makeBasis(X, up, along));
        const horn = P.plate.clone().addScaledVector(along, 0.3).addScaledVector(up, -0.22);
        span(horns[i], horn.clone().setX(s * 0.29), horn.clone().setX(s * 0.5));
        weights[i].position.copy(horn).setX(s * 0.36);
        span(collars[i], horn.clone().setX(s * 0.39), horn.clone().setX(s * 0.41));
        const h = P.hand(s);
        span(handles[i], h.clone().setZ(h.z - 0.09), h.clone().setZ(h.z + 0.09));
        span(grips[i], h.clone().setZ(h.z - 0.06), h.clone().setZ(h.z + 0.06));
        span(handlePosts[i], h.clone().setZ(h.z - 0.08), V(s * 0.3, 0.07, h.z - 0.08));
      });
      [-0.05, 0.32].forEach((t, i) => {
        const c = P.plate.clone().addScaledVector(up, -0.235).addScaledVector(along, t);
        span(axles[i], c.clone().setX(-0.3), c.clone().setX(0.3));
      });
      const mid = P.plate.clone().addScaledVector(along, 0.16).addScaledVector(up, -0.17);
      span(cross, mid.clone().setX(-0.28), mid.clone().setX(0.28));
      // The plate's face is square to the sled: its thickness runs along the sled.
      const q = new THREE.Quaternion().setFromUnitVectors(UP, along.clone().negate());
      plate.position.copy(P.plate).addScaledVector(along, 0.02);
      plate.quaternion.copy(q);
      lip.position.copy(P.plate).addScaledVector(along, -0.02).addScaledVector(up, -0.245);
      lip.quaternion.copy(q);
    },
  };
}

/**
 * A seated chest press: the seat and back pad (its face `pad` metres behind the spine), a column
 * behind with a boom out to the pivot, and two levers hung from it, swinging the handles through
 * their arc. Behind the column a weight stack in its frame, its cable over a pulley to the column,
 * rests with the handles back at the chest and rises as the levers swing forward. `P.pivot`, `P.handle(s)` and `P.swing` (the levers' angle,
 * radians from hanging straight down) place them.
 */
function chestPress({ seat = [0.47, 0.02], pad: behind = 0.2 } = {}) {
  const M = tones();
  const backRest = backPad(0.36, 0.64, M, { up: 0.36, behind });
  const rear = -0.55;
  const stackZ = -0.71;
  const ux = UPRIGHTS;
  const frameTop = 1.72;
  const wheel = V(0, frameTop - 0.035 - WHEEL - 0.02, stackZ + WHEEL);
  const front = seat[1] + 0.2;
  const parts = [
    ...seatOn(seat[0], seat[1], M, { pin: 1 }),
    backRest.mesh,
    tube(0.07, V(0, 0.035, stackZ - 0.12), V(0, 0.035, front), M.frame),
    tube(0.07, V(-ux - 0.15, 0.035, stackZ), V(ux + 0.15, 0.035, stackZ), M.frame),
    tube(0.07, V(-0.3, 0.035, front), V(0.3, 0.035, front), M.frame),
    ...[-1, 1].flatMap((s) => [endCap(s * (ux + 0.15), stackZ, M), endCap(s * 0.3, front, M)]),
    ...stackFrame(stackZ, frameTop, M),
    // The stack's frame tied to the column at its top.
    tube(0.06, V(0, frameTop, stackZ + 0.035), V(0, frameTop, rear - 0.05), M.frame),
    ...pulley(wheel, M),
    // The cable from the wheel's top forward into the column.
    rod(0.0045, V(0, wheel.y + WHEEL, wheel.z), V(0, wheel.y + WHEEL, rear), M.metal),
    basePlate(0, rear, M),
  ];
  const column = stretchedTube(0.1, M.frame);
  const cap = pad(0.11, 0.012, 0.11, M.frame, V(0, 0, rear), { soft: 0.003, solid: false });
  const boom = stretchedTube(0.08, M.frame);
  const brace = stretchedTube(0.05, M.frame);
  const support = stretchedTube(0.05, M.frame);
  const axle = part(rodGeometry(0.022), M.metal);
  // The levers' pivot: a housing either side of the boom's end, round the axle.
  const housings = [-1, 1].map(() => pad(0.008, 0.14, 0.12, M.pulley, V(0, 0, 0), { soft: 0.003, solid: false }));
  const caps = [0, 1].map(() => turnedPart(wheelGeometry(0.03, 0.02), M.pulley, V(0, 0, 0), { solid: false }));
  const hubs = [0, 1].map(() => part(wheelGeometry(0.05, 0.05), M.pulley));
  // Each lever hangs outside the body and turns at its foot, in a block, into a horizontal handle
  // reaching in across the palm, held like a bar, with a grip on it and a cap on its end.
  const levers = [0, 1].map(() => ({
    arm: stretchedTube(0.045, M.frame),
    elbow: pad(0.06, 0.06, 0.06, M.frame, V(0, 0, 0), { soft: 0.012 }),
    grip: part(rodGeometry(0.017), M.metal, { held: true }),
    sleeve: part(rodGeometry(0.0205), M.vinyl, { held: true }),
    end: turnedPart(rollerGeometry(0.024, 0.02), M.vinyl, V(0, 0, 0), { solid: false }),
  }));
  const drop = part(rodGeometry(0.0045, 8), M.metal);
  const weights = weightStack(V(0, 0, stackZ), M, { rodTop: 1.5, toward: 1 });
  const g = group('chest_press', [
    ...parts, column, cap, boom, brace, support, axle, ...housings, ...caps, ...hubs,
    ...levers.flatMap((l) => [l.arm, l.elbow, l.grip, l.sleeve, l.end]), drop, ...weights.parts,
  ]);
  return {
    group: g,
    place(P, P0, P1) {
      const back = backRest.fit(P0);
      // The back pad's support runs straight back from its board into the column.
      span(support, back, V(0, back.y, rear - 0.05));
      const top = P.pivot.y + 0.05;
      span(column, V(0, 0.012, rear), V(0, top + 0.04, rear));
      cap.position.y = top + 0.046;
      span(boom, V(0, top, rear - 0.05), V(0, top, P.pivot.z + 0.04));
      // A brace from the column up under the boom.
      span(brace, V(0, top - 0.3, rear + 0.05), V(0, top - 0.04, rear + 0.3));
      span(axle, P.pivot.clone().setX(-0.52), P.pivot.clone().setX(0.52));
      housings.forEach((m, i) => m.position.copy(P.pivot).setX((i ? 1 : -1) * 0.045).add(V(0, 0.03, 0)));
      caps.forEach((m, i) => m.position.copy(P.pivot).setX((i ? 1 : -1) * 0.535));
      levers.forEach((l, i) => {
        const s = i ? 1 : -1;
        const h = P.handle(s);
        const out = 0.5;
        hubs[i].position.copy(P.pivot).setX(s * out);
        span(l.arm, P.pivot.clone().setX(s * out), h.clone().setX(s * out));
        l.elbow.position.copy(h).setX(s * out);
        l.elbow.quaternion.copy(l.arm.quaternion);
        span(l.grip, h.clone().setX(s * out), h.clone().setX(s * (Math.abs(h.x) - 0.1)));
        span(l.sleeve, h.clone().setX(s * (out - 0.04)), h.clone().setX(s * (Math.abs(h.x) - 0.08)));
        l.end.position.copy(h).setX(s * (Math.abs(h.x) - 0.1));
      });
      const lift = HELD + 0.5 * Math.abs(P.swing - P1.swing);
      weights.lift(lift);
      span(drop, V(0, wheel.y, stackZ), weights.top(lift));
    },
  };
}

/** The rollers over the knees of a seated pull, at [`ty`, `tz`], on a post up from the floor. */
function thighRollers(ty, tz, M) {
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
function stirrup(M, name) {
  const g = new THREE.Group();
  g.name = name;
  g.add(along(0.012, -0.065, 0.065, M.metal, 12, { held: true }));
  g.add(along(0.0165, -0.05, 0.05, M.vinyl, 16, { held: true }));
  g.add(bent('stirrup', [V(-0.065, 0, 0), V(-0.062, 0.07, 0), V(0, 0.118, 0), V(0.062, 0.07, 0), V(0.065, 0, 0)], 0.006, M.metal));
  g.add(clevis(M, 0.13));
  nameAll(g, name);
  return g;
}
const STIRRUP = 0.145;

/** Turns `object` so its x runs along `along` and its y toward `toward`, as near as square allows. */
function turnTo(object, along, toward) {
  const x = along.clone().normalize();
  const y = toward.clone().addScaledVector(x, -toward.dot(x)).normalize();
  object.quaternion.setFromRotationMatrix(new THREE.Matrix4().makeBasis(x, y, x.clone().cross(y)));
}

/**
 * A cable column: a weight stack between two uprights, with a carriage at `pulley` metres up its
 * front whose pulley swivels toward the cable, standing at `at` [x, z] and facing the middle of
 * the room. `attachment` is `handle` (a stirrup in the hand on side `hand`, 1 the left) or `rope`
 * (its two ends in both hands). With `twin`, a second column stands mirrored at [-x, z] for the
 * other hand, a stirrup in each; with `crossed` too, each hand takes the far column's cable.
 */
function cableColumn({ at = [0.8, 0], pulley: carriageY = 0.2, attachment = 'handle', hand = 1, twin = false, crossed = false } = {}) {
  const M = tones();
  const height = 2.15;
  const stackZ = -0.16;
  const columns = (twin ? [1, -1] : [1]).map((mirror) => {
    const column = new THREE.Group();
    column.name = `cable_column_${mirror < 0 ? 'r' : 'l'}`;
    const position = V(mirror * at[0], 0, at[1]);
    column.position.copy(position);
    // Its front faces the middle of the room.
    column.rotation.y = Math.atan2(-position.x, -position.z);
    const weights = weightStack(V(0, 0, stackZ), M, { rodTop: height - 0.25, toward: 1 });
    const carriage = pad(0.24, 0.12, 0.1, M.pulley, V(0, carriageY, 0.05), { soft: 0.01 });
    const swivel = new THREE.Group();
    swivel.position.set(0, carriageY, 0.13);
    swivel.add(...pulley(V(0, 0, 0), M));
    const parts = [
      ...stackFrame(stackZ, height, M),
      ...floorRail(V(-0.4, 0.035, stackZ), V(0.4, 0.035, stackZ), M),
      ...floorRail(V(0, 0.035, stackZ - 0.25), V(0, 0.035, 0.3), M),
      ...[-1, 1].map((s) => rod(0.012, V(s * 0.07, 0.12, 0.05), V(s * 0.07, height - 0.1, 0.05), M.metal)),
      // The carriage's rods end in brackets on the column, top and bottom.
      ...[0.11, height - 0.09].map((y) => pad(0.2, 0.02, 0.06, M.frame, V(0, y, 0.04), { soft: 0.004, solid: false })),
      carriage,
      knob(M, V(0, carriageY, 0.11)),
      ...weights.parts,
      swivel,
    ];
    const drop = part(rodGeometry(0.0045, 8), M.metal);
    const rise = part(rodGeometry(0.0045, 8), M.metal);
    parts.push(drop, rise);
    parts.forEach((p) => column.add(p));
    nameAll(column, column.name);
    const yaw = column.rotation.y;
    const local = (p) => p.clone().sub(position).applyAxisAngle(UP, -yaw);
    return {
      column, weights, swivel, drop, rise,
      exit: position.clone().add(V(0, carriageY, 0.13).applyAxisAngle(UP, yaw)),
      // Swivels the pulley toward the hook and raises the stack `lift`.
      set(hook, lift) {
        const h = local(hook);
        swivel.rotation.y = Math.atan2(h.x, h.z - 0.13);
        weights.lift(lift);
        span(rise, V(0, carriageY + 0.05, 0.05), V(0, height - 0.06, 0.05));
        span(drop, V(0, height - 0.06, stackZ), weights.top(lift));
      },
    };
  });
  // The attachments, in the room's own frame.
  const ends = [];
  let rope = null;
  let handles = null;
  if (attachment === 'rope') {
    const strands = [-1, 1].map((s) => ({ s, strand: part(rodGeometry(0.011, 10), M.vinyl, { held: true }), end: knob(M, V(0, 0, 0), 0.02) }));
    strands.forEach(({ strand, end }) => ends.push(strand, end));
    const ring = clevis(M, 0);
    ends.push(ring);
    rope = { strands, ring };
  } else if (attachment === 'handle') {
    handles = (twin ? [1, -1] : [hand]).map((s) => ({ s, handle: stirrup(M, `cable_handle_${s < 0 ? 'r' : 'l'}`) }));
    handles.forEach(({ handle }) => ends.push(handle));
  } else throw new Error(`No cable attachment "${attachment}" on a column`);
  const cables = columns.map(() => part(rodGeometry(0.0045, 8), M.metal));
  const g = group('cable_column', [...columns.map((c) => c.column), ...ends, ...cables]);
  // The column a hand's cable runs to.
  const columnOf = (s) => (twin ? columns[(crossed ? -s : s) > 0 ? 0 : 1] : columns[0]);
  // The stack rests where the cable is drawn least, at whichever end of the rep that is.
  let rests = null;
  const hooksAt = (P) => {
    if (rope) {
      const mid = P.hand(1).clone().add(P.hand(-1)).multiplyScalar(0.5);
      return [{ c: columns[0], hook: mid.addScaledVector(columns[0].exit.clone().sub(mid).normalize(), 0.18) }];
    }
    return handles.map(({ s }) => {
      const c = columnOf(s);
      const toward = c.exit.clone().sub(P.hand(s)).normalize();
      return { c, s, hook: P.hand(s).clone().addScaledVector(toward, STIRRUP) };
    });
  };
  return {
    group: g,
    place(P, P0, P1) {
      if (!rests) {
        const bottom = hooksAt(P1);
        rests = hooksAt(P0).map(({ c, hook }, i) => Math.min(hook.distanceTo(c.exit), bottom[i].hook.distanceTo(c.exit)));
      }
      const hooks = hooksAt(P);
      hooks.forEach(({ c, s, hook }, i) => {
        const lift = HELD + Math.max(0, hook.distanceTo(c.exit) - rests[i]);
        c.set(hook, lift);
        span(cables[columns.indexOf(c)], hook, c.exit);
        if (handles) {
          const { handle } = handles.find((h) => h.s === s);
          handle.position.copy(P.hand(s));
          turnTo(handle, P.held?.[s] ?? X, c.exit.clone().sub(P.hand(s)));
        }
      });
      if (rope) {
        const { hook } = hooks[0];
        rope.ring.position.copy(hook);
        rope.strands.forEach(({ s, strand, end }) => {
          const h = P.hand(s);
          const beyond = h.clone().addScaledVector(h.clone().sub(hook).normalize(), 0.06);
          span(strand, hook, beyond);
          end.position.copy(beyond);
        });
      }
    },
  };
}

/** A round knob, `r` across, at `at`. */
function knob(M, at, r = 0.016) {
  const k = part(shape(`knob:${r}`, () => { const g = new THREE.SphereGeometry(r, 14, 10); g.deleteAttribute('uv'); return g; }), M.vinyl);
  k.position.copy(at);
  return k;
}

/** A lever's hub at `at`, about x: a turned boss in the pulley tone. */
const hub = (M, at) => turnedPart(wheelGeometry(0.055, 0.05), M.pulley, at, { solid: false });

/** A pair of handles along z at the hands; `place(P, to)` stands each on a post down to `to(side, hand)`. */
function sideHandles(M) {
  const grips = [-1, 1].map(() => ({ bar: part(rodGeometry(0.016), M.metal, { held: true }), sleeve: part(rodGeometry(0.0195), M.vinyl, { held: true }), post: stretchedTube(0.04, M.frame) }));
  return {
    parts: grips.flatMap((g) => [g.bar, g.sleeve, g.post]),
    place(P, to) {
      grips.forEach((g, i) => {
        const s = i ? 1 : -1;
        const h = P.hand(s);
        span(g.bar, h.clone().setZ(h.z - 0.08), h.clone().setZ(h.z + 0.08));
        span(g.sleeve, h.clone().setZ(h.z - 0.055), h.clone().setZ(h.z + 0.055));
        span(g.post, h.clone().setZ(h.z - 0.07), to(s, h));
      });
    },
  };
}

/** A weight plate on a horn, the horn from `from` out along x on side `side`; `place(from)` moves both. */
function loadedHorn(M, side) {
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

/**
 * A lever turning about the knees, its hub `side` metres out, with a roller across the shins
 * `down` metres down them: on their front, or with `behind` on the backs of the calves.
 * `place(P, P0)` turns it with the shins and returns where its arm meets the roller's axle.
 */
function kneeLever(M, side, { down, behind }) {
  const arm = stretchedTube(0.05, M.frame);
  const boss = hub(M, V(0, 0, 0));
  const axle = part(rodGeometry(0.012), M.metal);
  const rollers = [-1, 1].map(() => part(rollerGeometry(0.045, 0.16), M.vinyl, { solid: true }));
  return {
    parts: [arm, boss, axle, ...rollers],
    place(P, P0) {
      const pivot = P0.knee.clone().setX(side);
      boss.position.copy(pivot);
      const r = P.knee.clone().addScaledVector(P.shin, down).addScaledVector(P.front, behind ? -0.1 : 0.1);
      span(arm, pivot, r.clone().setX(side));
      span(axle, r.clone().setX(-0.2), r.clone().setX(side));
      rollers.forEach((m, i) => m.position.copy(r).setX(i ? 0.1 : -0.1));
      return { pivot, end: r.setX(side) };
    },
  };
}

/** How far a seated curl's thigh pad tips toward the knees, in degrees, to lie along the thighs. */
const THIGH_TAPER = 10;

/**
 * A leg extension or, with `curl`, a seated leg curl: a seat and back pad (its face `pad` behind
 * the spine), handles beside the seat, and a lever turning about the knees with a roller on the
 * shins: on their front for the extension, behind the calves for the curl. A curl adds a pad over
 * the thighs. A weight stack behind the seat, its cable over a wheel to the lever, rests at the
 * stretch and rises as the lever turns.
 */
function legExtension({ seat = [0.5, 0], pad: behind = 0.12, curl = false } = {}) {
  const M = tones();
  const backRest = backPad(0.38, 0.5, M, { up: 0.27, behind });
  const stackZ = -0.62;
  const weights = weightStack(V(0, 0, stackZ), M, { rodTop: 1.15, toward: 1 });
  const handles = sideHandles(M);
  const side = 0.33;
  const lever = kneeLever(M, side, { down: 0.35, behind: curl });
  const sidePost = stretchedTube(0.06, M.frame);
  // The stack's cable: up over a wheel on its frame, out to the side and down to the lever's hub.
  const wheel = V(0, 1.3 - 0.035 - WHEEL - 0.02, stackZ + WHEEL);
  const drop = part(rodGeometry(0.0045, 8), M.metal);
  const across = part(rodGeometry(0.0045, 8), M.metal);
  const run = part(rodGeometry(0.0045, 8), M.metal);
  const thighPad = curl ? pad(0.42, 0.06, 0.14, M.vinyl, V(0, 0, 0), { soft: 0.02 }) : null;
  const thighPost = curl ? stretchedTube(0.05, M.frame) : null;
  // The back pad's support, back from its board to the stack's frame.
  const support = stretchedTube(0.05, M.frame);
  // Where the cable turns down to the hub: a second wheel in its housing, on an arm off the frame.
  const corner = new THREE.Group();
  corner.add(...pulley(V(0, 0, 0), M));
  const cornerArm = stretchedTube(0.05, M.frame);
  const g = group(curl ? 'leg_curl' : 'leg_extension', [
    ...seatOn(seat[0], seat[1], M, { pin: 1 }),
    backRest.mesh, support,
    ...floorRail(V(0, 0.035, stackZ - 0.1), V(0, 0.035, seat[1] + 0.25), M),
    ...floorRail(V(-0.4, 0.035, stackZ), V(0.4, 0.035, stackZ), M),
    ...floorRail(V(0.06, 0.035, seat[1] + 0.25), V(0.42, 0.035, seat[1] + 0.25), M),
    ...stackFrame(stackZ, 1.3, M),
    ...weights.parts, ...handles.parts, ...lever.parts, sidePost,
    ...pulley(wheel, M), drop, across, run, corner, cornerArm,
    ...(curl ? [thighPad, thighPost] : []),
  ]);
  return {
    group: g,
    place(P, P0, P1) {
      const board = backRest.fit(P0);
      span(support, board, V(0, board.y, stackZ + 0.035));
      handles.place(P, (s, h) => V(s * (Math.abs(h.x) - 0.02), 0.07, h.z - 0.07));
      const { pivot } = lever.place(P, P0);
      span(sidePost, V(side, 0.07, pivot.z + 0.25), pivot);
      if (curl) {
        // Over the thighs, a hand's breadth back from the knees.
        const above = P0.knee.clone().addScaledVector(P0.thigh, -0.14).addScaledVector(P0.kneeUp, 0.125);
        thighPad.position.copy(above);
        // Tipped toward the knees, along the thighs' taper.
        thighPad.quaternion.setFromUnitVectors(UP, P0.kneeUp).multiply(new THREE.Quaternion().setFromAxisAngle(X, rad(THIGH_TAPER)));
        span(thighPost, above.clone().setX(side), V(side, 0.07, above.z));
      }
      // The stack is held just off its rest at the stretch, the bottom of the rep, and rises as the
      // knees work.
      const lift = HELD + 0.22 * P.shin.angleTo(P1.shin);
      weights.lift(lift);
      span(drop, V(0, wheel.y, stackZ), weights.top(lift));
      // Out to the side over the wheel, clear of the body, and down to the hub.
      // The corner wheel turns across the body, so the cable leaves it straight down at the hub's side.
      const turn = V(side - WHEEL, wheel.y, wheel.z);
      corner.position.copy(turn);
      corner.rotation.y = Math.PI / 2;
      span(cornerArm, V(UPRIGHTS + 0.035, wheel.y + WHEEL + 0.06, turn.z), turn.clone().add(V(0, WHEEL + 0.06, 0)));
      span(across, V(0, wheel.y + WHEEL, wheel.z), turn.clone().add(V(0, WHEEL, 0)));
      span(run, V(side, wheel.y, wheel.z), pivot);
    },
  };
}

/**
 * A lying leg curl: a bench under the body from `bench` [from z, to z] at `height`, handles at its
 * head end, and a lever turning about the knees with a roller on the backs of the ankles, loaded
 * with a plate.
 */
function lyingLegCurl({ bench: [from, to] = [-0.45, 0.85], height = 0.62 } = {}) {
  const M = tones();
  const length = to - from;
  const cushion = pad(0.34, 0.07, length, M.vinyl, V(0, height - 0.035, (from + to) / 2), { soft: 0.025 });
  const handles = sideHandles(M);
  const side = 0.3;
  const lever = kneeLever(M, side, { down: 0.36, behind: true });
  const load = loadedHorn(M, 1);
  // The lever's hub stands on an arm out from under the bench's foot end.
  const hubArm = stretchedTube(0.05, M.frame);
  const g = group('leg_curl', [
    cushion, hubArm,
    pad(0.3, 0.012, length - 0.04, M.frame, V(0, height - 0.076, (from + to) / 2), { soft: 0.003 }),
    ...[from + 0.12, to - 0.12].flatMap((z) => [
      tube(0.06, V(0, 0.06, z), V(0, height - 0.082, z), M.frame),
      tube(0.06, V(-0.25, 0.03, z), V(0.25, 0.03, z), M.frame),
      ...[-1, 1].map((s) => pad(0.02, 0.06, 0.07, M.vinyl, V(s * 0.26, 0.03, z), { soft: 0.006, solid: false })),
      pad(0.14, 0.01, 0.1, M.frame, V(0, height - 0.087, z), { soft: 0.003, solid: false }),
    ]),
    tube(0.05, V(0, 0.085, from + 0.12), V(0, 0.085, to - 0.12), M.frame),
    ...handles.parts, ...lever.parts, ...load.parts,
  ]);
  return {
    group: g,
    place(P, P0) {
      handles.place(P, (s, h) => V(s * 0.12, height - 0.08, h.z - 0.05));
      const { pivot, end } = lever.place(P, P0);
      span(hubArm, V(0, height - 0.1, to - 0.12), pivot.clone().add(V(-0.03, 0, 0)));
      // The plate rides the lever, rising with the heels.
      load.place(pivot.clone().lerp(end, 0.45).setX(side + 0.03));
    },
  };
}

/**
 * A seated calf raise: a seat, a platform under the balls of the feet, and a pad over the knees on
 * a lever that pivots ahead of the platform, loaded with a plate a side, rising with the knees.
 */
function calfRaise({ seat = [0.48, -0.05] } = {}) {
  const M = tones();
  const kneePad = pad(0.42, 0.06, 0.14, M.vinyl, V(0, 0, 0), { soft: 0.02 });
  const arms = [-1, 1].map(() => stretchedTube(0.05, M.frame));
  const loads = [-1, 1].map((s) => loadedHorn(M, s));
  const handles = sideHandles(M);
  const bosses = [-1, 1].map(() => hub(M, V(0, 0, 0)));
  // The levers' pivot stands on a post either side, on a floor rail across.
  const pivotPosts = [-1, 1].map(() => stretchedTube(0.06, M.frame));
  const pivotRail = movableRail(M);
  const g = group('calf_raise', [
    ...seatOn(seat[0], seat[1], M, { pin: -1 }),
    ...floorRail(V(0, 0.035, seat[1] - 0.2), V(0, 0.035, seat[1] + 1.0), M),
    ...floorRail(V(-0.35, 0.035, seat[1] + 1.0), V(0.35, 0.035, seat[1] + 1.0), M),
    kneePad, ...arms, ...loads.flatMap((l) => l.parts), ...handles.parts, ...bosses, ...pivotPosts, ...pivotRail.parts,
  ]);
  let platform = null;
  return {
    group: g,
    place(P, P0) {
      if (!platform) {
        // The balls stand on its back edge; the heels hang off behind it.
        const at = P0.platform;
        platform = pad(0.5, 0.05, 0.2, M.frame, V(0, at.y - 0.025, at.z + 0.06), { soft: 0.006 });
        platform.name = 'calf_raise_platform';
        const legs = tube(0.06, V(0, 0.07, at.z + 0.06), V(0, at.y - 0.05, at.z + 0.06), M.frame);
        legs.name = 'calf_raise_platform_post';
        g.add(platform, legs);
      }
      const knee = P.knee;
      const top = knee.clone().add(V(0, 0.1, -0.02));
      kneePad.position.copy(top);
      const pivot = P0.platform.clone().add(V(0, -0.02, 0.45));
      pivotRail.set(pivot.z, 0.35);
      [-1, 1].forEach((s, i) => {
        const end = top.clone().setX(s * 0.26);
        const p = pivot.clone().setX(s * 0.26);
        bosses[i].position.copy(p);
        span(pivotPosts[i], p.clone().setX(s * 0.32), V(s * 0.32, 0.07, p.z));
        span(arms[i], end, p);
        loads[i].place(top.clone().lerp(pivot, 0.5).setX(s * 0.28));
      });
      handles.place(P, (s, h) => V(s * (Math.abs(h.x) - 0.02), 0.07, h.z - 0.07));
    },
  };
}

/**
 * A hack squat: a foot platform tilted toes up, two rails up the sled's line behind the back, and a
 * carriage riding them with the back pad, a roller over each shoulder and a handle by each, loaded
 * with a plate a side.
 */
function squatMachine() {
  const M = tones();
  const backRest = backPad(0.4, 0.62, M, { up: 0.3, behind: 0.21 });
  const plate = pad(0.7, 0.04, 0.55, M.frame, V(0, 0, 0), { soft: 0.008 });
  const rails = [-1, 1].map(() => stretchedTube(0.07, M.frame));
  const legs = [-1, 1].map(() => stretchedTube(0.06, M.frame));
  const shoulderPads = [-1, 1].map(() => part(rollerGeometry(0.05, 0.1), M.vinyl, { solid: true }));
  const handles = [-1, 1].map(() => part(rodGeometry(0.0165), M.vinyl, { held: true }));
  const carriage = [-1, 1].map(() => stretchedTube(0.05, M.frame));
  const loads = [-1, 1].map((s) => loadedHorn(M, s));
  // The carriage rides each rail on two rollers; the rails are tied across at both ends.
  const rollers = [0, 1, 2, 3].map(() => turnedPart(wheelGeometry(0.035, 0.03), M.pulley, V(0, 0, 0), { solid: false }));
  const ties = [0, 1].map(() => stretchedTube(0.06, M.frame));
  const g = group('squat_machine', [
    ...floorRail(V(0, 0.035, -0.9), V(0, 0.035, 0.8), M),
    ...[-0.9, 0.8].flatMap((z) => floorRail(V(-0.4, 0.035, z), V(0.4, 0.035, z), M)),
    plate, backRest.mesh, ...rails, ...legs, ...shoulderPads, ...handles, ...carriage, ...loads.flatMap((l) => l.parts),
    ...rollers, ...ties,
  ]);
  return {
    group: g,
    place(P, P0) {
      backRest.fit(P);
      const q = new THREE.Quaternion().setFromUnitVectors(UP, P.plateUp);
      plate.position.copy(P.face).addScaledVector(P.plateUp, -0.02).addScaledVector(X.clone().cross(P.plateUp), 0.1);
      plate.quaternion.copy(q);
      const back = X.clone().cross(P0.u).normalize().negate();
      const line = P0.hipC.clone().addScaledVector(back, 0.3);
      const f = X.clone().cross(P.u).normalize();
      [-1, 1].forEach((s, i) => {
        const lo = line.clone().addScaledVector(P.sled, -0.9).setX(s * 0.25);
        const hi = line.clone().addScaledVector(P.sled, 0.75).setX(s * 0.25);
        span(rails[i], lo, hi);
        span(legs[i], hi, hi.clone().setY(0.07));
        const over = P.shoulders.clone().add(V(s * 0.14, 0, 0)).addScaledVector(P.u, 0.115).addScaledVector(f, -0.01);
        shoulderPads[i].position.copy(over);
        const h = P.hand(s);
        span(handles[i], h.clone().addScaledVector(f, -0.05), h.clone().addScaledVector(f, 0.06));
        const rail = P.hipC.clone().addScaledVector(back, 0.3).setX(s * 0.25);
        span(carriage[i], rail.clone().addScaledVector(P.u, -0.1), rail.clone().addScaledVector(P.u, 0.62));
        // On the rail's face toward the body, the carriage between.
        [-0.05, 0.5].forEach((t, k) => rollers[2 * i + k].position.copy(rail).addScaledVector(P.u, t).addScaledVector(back, -0.06));
        const end = (i ? hi : lo).clone();
        span(ties[i], end.clone().setX(-0.25), end.clone().setX(0.25));
        loads[i].place(rail.clone().addScaledVector(P.u, 0.1).setX(s * 0.27));
      });
    },
  };
}

/**
 * Two levers on hubs at `P.pivot` either side of the body, each ending in a handle held at
 * `P.handle(s)`: the arm out at `out` from the middle, a crossbar in to the grip, which lies along
 * the hand's knuckles. With `counter`, each lever runs on past the pivot to a plate.
 */
function levers(M, { out = 0.45, counter = 0 } = {}) {
  const sides = [-1, 1].map((s) => ({
    s,
    boss: hub(M, V(0, 0, 0)),
    arm: stretchedTube(0.045, M.frame),
    cross: part(rodGeometry(0.016), M.metal),
    grip: part(rodGeometry(0.0165), M.vinyl, { held: true }),
    tail: counter ? stretchedTube(0.045, M.frame) : null,
    load: counter ? loadedHorn(M, s) : null,
  }));
  return {
    parts: sides.flatMap((l) => [l.boss, l.arm, l.cross, l.grip, ...(l.tail ? [l.tail, ...l.load.parts] : [])]),
    place(P) {
      for (const l of sides) {
        const pivot = P.pivot.clone().setX(l.s * out);
        const h = P.handle(l.s);
        const end = h.clone().setX(l.s * out);
        l.boss.position.copy(pivot);
        span(l.arm, pivot, end);
        span(l.cross, end, h);
        const along = (P.held?.[l.s] ?? X).clone().normalize();
        span(l.grip, h.clone().addScaledVector(along, -0.06), h.clone().addScaledVector(along, 0.06));
        if (l.tail) {
          const back = pivot.clone().addScaledVector(pivot.clone().sub(end).normalize(), counter);
          span(l.tail, pivot, back);
          l.load.place(back);
        }
      }
    },
  };
}

/**
 * A pad in front of the chest at the top of the rep: its face `front` metres out from the line up
 * the spine, its middle `up` metres up it, on a post down to the floor.
 */
function chestPad(M, { up, front }) {
  const t = 0.08;
  const holder = new THREE.Group();
  const cushion = part(padGeometry(0.34, 0.42, t, 0.025), M.vinyl, { solid: true });
  const board = part(padGeometry(0.3, 0.38, 0.012, 0.003), M.frame);
  board.position.z = t / 2 + 0.006;
  holder.add(cushion, board);
  const post = stretchedTube(0.06, M.frame);
  return {
    parts: [holder, post],
    fit(P0) {
      const f = X.clone().cross(P0.u).normalize();
      holder.position.copy(P0.hipC).addScaledVector(P0.u, up).addScaledVector(f, front + t / 2);
      holder.quaternion.setFromRotationMatrix(new THREE.Matrix4().makeBasis(X, P0.u, f));
      const behind = holder.position.clone().addScaledVector(f, t / 2 + 0.03);
      span(post, behind, behind.clone().setY(0.07));
    },
  };
}

/**
 * A chest-supported row: a seat, a pad for the chest, and two levers hung from hubs above and ahead
 * of it, each with a handle and loaded with a plate past the pivot, which rises as the handles are
 * pulled back.
 */
function rowMachine({ seat = [0.5, 0], chest = { up: 0.4, front: 0.1 } } = {}) {
  const M = tones();
  const pad_ = chestPad(M, chest);
  const arms = levers(M, { out: 0.42, counter: 0.22 });
  const posts = [-1, 1].map(() => stretchedTube(0.07, M.frame));
  // The posts stand on the front floor rail, ahead of the feet, and a crossbar ties their heads.
  const head = stretchedTube(0.06, M.frame);
  const g = group('row_machine', [
    ...seatOn(seat[0], seat[1], M, { pin: -1 }),
    ...floorRail(V(0, 0.035, seat[1] - 0.25), V(0, 0.035, seat[1] + 1.0), M),
    ...floorRail(V(-0.5, 0.035, seat[1] + 0.8), V(0.5, 0.035, seat[1] + 0.8), M),
    ...pad_.parts, ...arms.parts, ...posts, head,
  ]);
  return {
    group: g,
    place(P, P0) {
      pad_.fit(P0);
      arms.place(P);
      [-1, 1].forEach((s, i) => span(posts[i], P.pivot.clone().setX(s * 0.5).add(V(0, 0.08, 0)), V(s * 0.5, 0.07, seat[1] + 0.8)));
      span(head, P.pivot.clone().setX(-0.535).add(V(0, 0.08, 0)), P.pivot.clone().setX(0.535).add(V(0, 0.08, 0)));
    },
  };
}

/**
 * A machine pulldown: a seat under thigh rollers, two uprights up to a crossbar over the pivot,
 * and two levers hung from it, each with a handle and a plate on its tail past the pivot.
 */
function pulldownMachine({ seat = [0.47, 0], thighPad = [0.71, 0.3] } = {}) {
  const M = tones();
  const arms = levers(M, { out: 0.42, counter: 0.3 });
  const uprights = [-1, 1].map(() => stretchedTube(0.07, M.frame));
  const crossbar = stretchedTube(0.07, M.frame);
  // The uprights stand on a floor rail under the pivot.
  const footRail = movableRail(M);
  const g = group('pulldown_machine', [
    ...seatOn(seat[0], seat[1], M, { pin: -1 }),
    ...thighRollers(thighPad[0], thighPad[1], M),
    ...floorRail(V(0, 0.035, seat[1] - 0.3), V(0, 0.035, seat[1] + 0.9), M),
    ...floorRail(V(-0.6, 0.035, seat[1] + 0.6), V(0.6, 0.035, seat[1] + 0.6), M),
    ...arms.parts, ...uprights, crossbar, ...footRail.parts,
  ]);
  return {
    group: g,
    place(P) {
      arms.place(P);
      const top = P.pivot.clone().add(V(0, 0.1, 0));
      footRail.set(top.z, 0.62);
      [-1, 1].forEach((s, i) => span(uprights[i], V(s * 0.55, 0.07, top.z), top.clone().setX(s * 0.55)));
      span(crossbar, top.clone().setX(-0.59), top.clone().setX(0.59));
    },
  };
}

/**
 * A preacher curl machine: a seat, a pad under the upper arms, and a bar on two levers turning
 * about the elbows, `P.elbow` and `P.upper` placing the pad, the bar held at `P.bar`.
 */
function preacherCurl({ seat = [0.5, 0] } = {}) {
  const M = tones();
  // A pad under each upper arm, the chest free between them, on a bar across beneath.
  const armPads = [-1, 1].map(() => part(padGeometry(0.13, 0.06, 0.16, 0.02), M.vinyl, { solid: true }));
  const crossbar = stretchedTube(0.04, M.frame);
  const posts = [-1, 1].map(() => stretchedTube(0.05, M.frame));
  const arms = [-1, 1].map(() => stretchedTube(0.04, M.frame));
  const bosses = [-1, 1].map(() => hub(M, V(0, 0, 0)));
  const bar = along(0.014, -0.32, 0.32, M.metal, 16, { held: true });
  const grips = [-1, 1].map((s) => along(0.0175, ...(s < 0 ? [-0.25, -0.1] : [0.1, 0.25]), M.vinyl, 16, { held: true }));
  const handle = new THREE.Group();
  handle.add(bar, ...grips);
  // Each hub on a mount off the pad's post, and a plate on a horn out of each lever's end.
  const mounts = [-1, 1].map(() => stretchedTube(0.04, M.frame));
  const loads = [-1, 1].map((s) => loadedHorn(M, s));
  const footRail = movableRail(M);
  const g = group('preacher_curl', [
    ...seatOn(seat[0], seat[1], M, { pin: -1 }),
    ...floorRail(V(0, 0.035, seat[1] - 0.25), V(0, 0.035, seat[1] + 0.7), M),
    ...floorRail(V(-0.35, 0.035, seat[1] + 0.7), V(0.35, 0.035, seat[1] + 0.7), M),
    ...armPads, crossbar, ...posts, ...arms, ...bosses, handle, ...mounts, ...loads.flatMap((l) => l.parts), ...footRail.parts,
  ]);
  return {
    group: g,
    place(P, P0) {
      // Under the upper arms: on the side of them away from the forearms' swing.
      const under = V(0, -P0.upper.z, P0.upper.y);
      const at = P0.elbow.clone().addScaledVector(P0.upper, -0.08).addScaledVector(under, 0.1);
      const facing = new THREE.Matrix4().makeBasis(X, under.clone().negate(), X.clone().cross(under.clone().negate()));
      armPads.forEach((m, i) => {
        m.position.copy(at).setX(i ? 0.2 : -0.2);
        m.quaternion.setFromRotationMatrix(facing);
      });
      const bar = at.clone().addScaledVector(under, 0.05).addScaledVector(P0.upper, 0.07);
      span(crossbar, bar.clone().setX(-0.28), bar.clone().setX(0.28));
      posts.forEach((m, i) => span(m, bar.clone().setX(i ? 0.28 : -0.28), V(i ? 0.28 : -0.28, 0.07, bar.z)));
      footRail.set(bar.z, 0.36);
      handle.position.copy(P.bar);
      [-1, 1].forEach((s, i) => {
        const pivot = P0.elbow.clone().setX(s * 0.34);
        bosses[i].position.copy(pivot);
        span(mounts[i], pivot.clone().setX(s * 0.31), bar.clone().setX(s * 0.28));
        span(arms[i], pivot, P.bar.clone().setX(s * 0.34));
        loads[i].place(P.bar.clone().setX(s * 0.36));
      });
    },
  };
}

/** A low platform under the feet, `height` tall (the pose's `feet.y`) and `size` [w, d] at `at` [x, z]: a deficit. */
function platform({ height = 0.05, size = [0.8, 0.5], at = [0, 0.05] } = {}) {
  const M = tones();
  const g = group('platform', [pad(size[0], height, size[1], M.frame, V(at[0], height / 2, at[1]), { soft: 0.008 })]);
  return { group: g, place() {} };
}

export const KITS = {
  barbell, dumbbells, bench, platform, cable_tower: cableTower, cable_column: cableColumn, leg_press: legPress, chest_press: chestPress,
  leg_extension: legExtension, leg_curl: (o = {}) => (o.lying ? lyingLegCurl(o) : legExtension({ ...o, curl: true })),
  calf_raise_machine: calfRaise, squat_machine: squatMachine, row_machine: rowMachine, pulldown_machine: pulldownMachine,
  preacher_curl_machine: preacherCurl,
};

/** Builds a pose's kit: each kit it names, with its options, in one group placed together. */
export function buildKit(entry) {
  const allowed = EQUIPMENT[entry.equipment];
  if (allowed === undefined) throw new Error(`No equipment "${entry.equipment}" in the library`);
  if (allowed === null) throw new Error(`"${entry.equipment}" has no kit yet`);
  const kits = Object.entries(entry.kit ?? {}).map(([name, options]) => {
    if (!KITS[name]) throw new Error(`No kit "${name}"`);
    if (!allowed.includes(name)) throw new Error(`Kit "${name}" isn't ${entry.equipment} equipment`);
    // An adjustable bench stands at the angle the pose is prepared at, and a platform is as high as
    // the feet stand.
    const kit = KITS[name](
      name === 'bench' && entry.bench ? { ...options, adjustable: true, angle: entry.bench.angle }
        : name === 'platform' ? { height: entry.feet?.y, ...options } : options,
    );
    // The bake shares the parts under this node with every clip in the same kit.
    kit.group.userData.kit = name;
    return kit;
  });
  const g = new THREE.Group();
  g.name = 'kit';
  kits.forEach((k) => g.add(k.group));
  return { group: g, place: (P, P0, P1) => kits.forEach((k) => k.place(P, P0, P1)) };
}

/**
 * A soft shadow on the floor: an ellipse `width` by `depth` metres, darkest in the middle and
 * gone at the rim, drawn unlit with its alpha in the vertex colours.
 */
export function floorShadow(width, depth, darkest = 0.45) {
  const rings = 8;
  const segments = 48;
  const position = [0, 0, 0];
  const color = [1, 1, 1, darkest];
  for (let r = 1; r <= rings; r++) {
    const t = r / rings;
    const alpha = darkest * (1 - t * t) * (1 - t * t);
    for (let k = 0; k < segments; k++) {
      const a = (k / segments) * Math.PI * 2;
      position.push((Math.cos(a) * t * width) / 2, 0, (Math.sin(a) * t * depth) / 2);
      color.push(1, 1, 1, alpha);
    }
  }
  const index = [];
  const at = (r, k) => 1 + (r - 1) * segments + (k % segments);
  for (let k = 0; k < segments; k++) index.push(0, at(1, k + 1), at(1, k));
  for (let r = 1; r < rings; r++) {
    for (let k = 0; k < segments; k++) {
      index.push(at(r, k), at(r, k + 1), at(r + 1, k + 1), at(r, k), at(r + 1, k + 1), at(r + 1, k));
    }
  }
  const g = new THREE.BufferGeometry();
  g.setAttribute('position', new THREE.Float32BufferAttribute(position, 3));
  g.setAttribute('normal', new THREE.Float32BufferAttribute(position.map((_, i) => (i % 3 === 1 ? 1 : 0)), 3));
  g.setAttribute('color', new THREE.Float32BufferAttribute(color, 4));
  g.setIndex(index);
  const material = new THREE.MeshBasicMaterial({ name: 'shadow', color: 0x000000, vertexColors: true, transparent: true, depthWrite: false });
  const mesh = new THREE.Mesh(g, material);
  mesh.name = 'shadow';
  mesh.position.y = 0.001;
  return mesh;
}
