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
    // Room for a whole hand between the collars.
    const parts = [along(0.016, -0.088, 0.088, M.metal, 16, { held: true })];
    for (const e of [-1, 1]) {
      parts.push(along(0.024, ...(e < 0 ? [-0.092, -0.084] : [0.084, 0.092]), M.metal, 20, { solid: true }));
      parts.push(turnedPart(hexGeometry(0.06, 0.06), M.plate, V(e * 0.118, 0, 0)));
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
    cables: [{ cable, hook: () => handle.localToWorld(V(0, above, 0)) }],
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
  // Where each cable hooks on: a stirrup's ring, or the rope's.
  const hookOn = rope
    ? [{ cable: cables[0], hook: () => rope.ring.getWorldPosition(V(0, 0, 0)) }]
    : handles.map(({ s, handle }) => ({ cable: cables[columns.indexOf(columnOf(s))], hook: () => handle.localToWorld(V(0, STIRRUP, 0)) }));
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
    cables: hookOn,
    place(P, P0, P1) {
      if (!rests) {
        const bottom = hooksAt(P1);
        rests = hooksAt(P0).map(({ c, hook }, i) => Math.min(hook.distanceTo(c.exit), bottom[i].hook.distanceTo(c.exit)));
      }
      const hooks = hooksAt(P);
      hooks.forEach(({ c, s, hook }, i) => {
        let at = hook;
        if (handles) {
          // The stirrup in the hand, turned toward the pulley as square to the grip allows: the
          // cable runs from its ring, wherever that comes to.
          const { handle } = handles.find((h) => h.s === s);
          handle.position.copy(P.hand(s));
          turnTo(handle, P.held?.[s] ?? X, c.exit.clone().sub(P.hand(s)));
          handle.updateMatrixWorld(true);
          at = handle.localToWorld(V(0, STIRRUP, 0));
        }
        const lift = HELD + Math.max(0, at.distanceTo(c.exit) - rests[i]);
        c.set(at, lift);
        span(cables[columns.indexOf(c)], at, c.exit);
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

/** A cable laid each frame between two points by `span`. */
const wire = (M) => part(rodGeometry(0.0045, 8), M.metal);

/** A pulley wheel in its housing at `at`, turning about x, or about z with `aboutZ`. */
function wheelAt(M, at, { aboutZ = false } = {}) {
  const g = new THREE.Group();
  g.add(...pulley(V(0, 0, 0), M));
  g.position.copy(at);
  if (aboutZ) g.rotation.y = Math.PI / 2;
  return g;
}

/** A floor wheel in its housing at (x, z) on a floor rail, turning about z: where a cable turns along the rail. */
function floorWheel(M, x, z) {
  const at = V(x, 0.07 + 0.012 + WHEEL, z);
  return { at, parts: [wheelAt(M, at, { aboutZ: true }), pad(0.12, 0.012, 0.06, M.frame, V(x, 0.076, z), { soft: 0.003, solid: false })] };
}

/** How tall the leg machines' towers stand, to the top of their caps. */
const TOWER = 2.05;

/**
 * A selectorised tower standing beside the user at (x, z), as on the reference machines (#149): its
 * uprights either side of the stack along z and the plates facing the user, a wheel over the stack
 * under its cap and one out past the upright toward `toward` (z, ±1), the cable over both and down
 * that end to a wheel on the floor rail at `dropZ`, which turns it along the rail toward the user.
 * `lift(l)` raises the stack and lays the cable up to it; `run` is where the cable leaves the floor
 * wheel.
 */
function sideTower(M, { x, z, toward }) {
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
const ROLLER = 0.06;

/**
 * A cam lever turning about the knees, its arm `side` metres out (x) beside the legs: down the
 * shins `down` metres to a long roller on their front or, with `behind`, the backs of the legs, an
 * adjuster plate with its pop-pin at the pivot, and inboard the cam the stack's cable winds on, off
 * its axle so it is seen to turn. `place(P, P0)` turns it with the shins and returns the pivot and
 * the cam's centre.
 */
function camLever(M, side, { down, behind }) {
  const lever = new THREE.Group();
  const out = side + 0.05;
  const off = (behind ? -1 : 1) * (0.05 + ROLLER);
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
    place(P, P0) {
      const pivot = P0.knee.clone().setX(0);
      lever.position.copy(pivot);
      lever.quaternion.setFromRotationMatrix(new THREE.Matrix4().makeBasis(X, P.shin.clone().negate(), P.front));
      return { pivot: pivot.clone().setX(side), cam: cam.clone().applyQuaternion(lever.quaternion).add(pivot) };
    },
  };
}

/** The cam's radius. */
const CAM = 0.12;

/**
 * The cable from a tower's floor wheel along the rail to a second floor wheel under the cam, and up
 * to the cam's rim. `place(cam)` lays the last stretch to the cam where it is now.
 */
function camCable(M, tower, camX) {
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
 * `place` sets the lever and the stack for the rep and gives the pivot.
 */
function kneeDrive(M, { z, toward, down, behind }) {
  const side = 0.32;
  const tx = -0.5;
  const tower = sideTower(M, { x: tx, z, toward });
  const lever = camLever(M, side, { down, behind });
  const cable = camCable(M, tower, lever.camX);
  const z0 = tower.dropZ;
  const post = stretchedTube(0.06, M.frame);
  const pivotHub = hub(M, V(0, 0, 0));
  return {
    side, tx, z0,
    parts: [
      ...tower.parts, ...lever.parts, ...cable.parts, post, pivotHub,
      ...floorRail(V(tx - 0.12, 0.035, z0), V(side + 0.1, 0.035, z0), M),
      pad(0.11, 0.012, 0.11, M.frame, V(side, 0.076, z0), { soft: 0.003, solid: false }),
    ],
    place(P, P0, P1) {
      const { pivot, cam } = lever.place(P, P0);
      span(post, V(side, 0.082, z0), pivot);
      pivotHub.position.copy(pivot).setX(side - 0.01);
      cable.place(cam);
      // The stack is held just off its rest at the stretch, the bottom of the rep, and rises as the
      // knees work.
      tower.lift(HELD + 0.22 * P.shin.angleTo(P1.shin));
      return pivot;
    },
  };
}

/**
 * A leg extension or, with `curl`, a seated leg curl, laid out as the reference machines (#149):
 * the tower beside the seat, level with it, on the far side from the camera; a seat whose top is at
 * `seat` [y, z], tilted `tilt` degrees up toward the knees, on crossed braces and a beam out from the
 * tower; and on the near side a post up to the cam lever about the knees, its roller on the front
 * of the shins for the extension and behind the ankles for the curl. Both have a back pad from the
 * seat up to the shoulder blades (its face `pad` behind the spine) on a bar through a sleeve on a
 * rear upright, set by a pop-pin. The extension's handles run forward beside the hips; the curl
 * adds a pad lying along the thighs on a bar out from the near post, with upright grips on it. The
 * stack rests at the stretch and rises as the lever turns.
 */
function legExtension({ seat = [0.59, 0.1], tilt = 10, pad: behind = 0.18, curl = false } = {}) {
  const M = tones();
  const [top, sz] = seat;
  const drive = kneeDrive(M, { z: sz, toward: 1, down: curl ? 0.38 : 0.35, behind: curl });
  const { side, tx, z0 } = drive;
  // The seat, its cushion and pan on a beam, tilted about its middle.
  const cushion = new THREE.Group();
  cushion.add(
    pad(0.36, 0.075, 0.4, M.vinyl, V(0, -0.0375, 0), { soft: 0.024 }),
    pad(0.3, 0.012, 0.34, M.frame, V(0, -0.081, 0), { soft: 0.003 }),
    tube(0.06, V(0, -0.117, -0.18), V(0, -0.117, 0.18), M.frame),
  );
  cushion.position.set(0, top, sz);
  cushion.rotation.x = -rad(tilt);
  cushion.updateMatrixWorld();
  // A point on the seat, `along` it toward the knees; the beam is 0.117 under the cushion's top.
  const seatAt = (x, y, along) => cushion.localToWorld(V(x, y, along));
  const beam = (along) => seatAt(0, -0.117, along);
  const parts = [
    cushion, ...drive.parts,
    ...floorRail(V(0, 0.035, sz - 0.62), V(0, 0.035, z0 - 0.035), M),
    ...floorRail(V(tx, 0.035, sz - 0.3), V(tx, 0.035, z0 - 0.035), M),
    // Crossed braces from the floor rail to the beam, and the beam's front out from the tower.
    tube(0.05, V(0, 0.07, sz - 0.24), beam(0.12), M.frame),
    tube(0.05, V(0, 0.07, sz + 0.2), beam(-0.12), M.frame),
    tube(0.06, V(tx + 0.035, beam(0.17).y, sz + UPRIGHTS), V(0, beam(0.17).y, sz + UPRIGHTS), M.frame),
  ];
  // The back pad, from the seat up to the shoulder blades, on a bar back from its board through a
  // sleeve on a rear upright, set by a pop-pin.
  const backRest = backPad(0.36, 0.58, M, { up: 0.21, behind });
  const upright = stretchedTube(0.06, M.frame);
  const bar = stretchedTube(0.04, M.frame);
  const end = part(rodGeometry(0.0195), M.vinyl);
  const sleeve = pad(0.08, 0.06, 0.1, M.frame, V(0, 0, 0), { soft: 0.008, solid: false });
  const pin = popPin(V(0, 0, 0), 1, M);
  parts.push(backRest.mesh, upright, bar, end, sleeve, pin);
  const fitBack = (P0) => {
    // Tipped back a little more than the spine, onto the shoulder blades and off the small of the back.
    const board = backRest.fit(P0);
    backRest.mesh.rotateX(-rad(7));
    const uz = board.z - 0.14;
    span(upright, V(0, 0.07, uz), V(0, board.y - 0.03, uz));
    span(bar, board, V(0, board.y, uz - 0.18));
    span(end, V(0, board.y, uz - 0.18), V(0, board.y, uz - 0.3));
    sleeve.position.set(0, board.y, uz);
    pin.position.set(0, board.y + 0.045, uz);
    pin.rotation.x = -Math.PI / 2;
  };
  let fit = () => {};
  if (!curl) {
    // The handles: a bar across under the seat's rear, risers up its sides and grips running forward
    // beside the hips, level with the cushion's top.
    parts.push(
      tube(0.04, seatAt(-0.25, -0.117, -0.16), seatAt(0.25, -0.117, -0.16), M.frame),
      ...[-1, 1].flatMap((s) => [
        tube(0.04, seatAt(s * 0.25, -0.117, -0.16), seatAt(s * 0.25, 0.02, -0.16), M.frame),
        rod(0.016, seatAt(s * 0.25, 0.02, -0.16), seatAt(s * 0.25, 0.02, 0.04), M.metal, { held: true }),
        rod(0.0195, seatAt(s * 0.25, 0.02, -0.12), seatAt(s * 0.25, 0.02, 0.03), M.vinyl, { held: true }),
      ]),
    );
  } else {
    const thighPad = pad(0.36, 0.06, 0.14, M.vinyl, V(0, 0, 0), { soft: 0.02 });
    const thighBar = stretchedTube(0.05, M.frame);
    const riser = stretchedTube(0.06, M.frame);
    const plate = pad(0.012, 0.22, 0.16, M.frame, V(0, 0, 0), { soft: 0.004, solid: false });
    const plateKnob = knob(M, V(0, 0, 0));
    const grips = [-1, 1].map(() => [part(rodGeometry(0.016), M.metal, { held: true }), part(rodGeometry(0.0195), M.vinyl, { held: true })]);
    parts.push(thighPad, thighBar, riser, plate, plateKnob, ...grips.flat());
    fit = (P0, pivot) => {
      // Over the lower half of the thighs, lying along them.
      const above = P0.knee.clone().addScaledVector(P0.thigh, -0.18).addScaledVector(P0.kneeUp, 0.125);
      thighPad.position.copy(above);
      thighPad.quaternion.setFromUnitVectors(UP, P0.kneeUp);
      const by = above.y + 0.06;
      span(thighBar, V(side + 0.03, by, above.z), V(-0.2, by, above.z));
      span(riser, pivot, V(side, by + 0.03, above.z));
      plate.position.set(side + 0.04, (pivot.y + by) / 2, (pivot.z + above.z) / 2);
      plateKnob.position.set(side + 0.06, (pivot.y + by) / 2 + 0.05, (pivot.z + above.z) / 2);
      grips.forEach(([grip, sleeveGrip], i) => {
        const x = (i ? 1 : -1) * 0.09;
        span(grip, V(x, by, above.z), V(x, by + 0.15, above.z));
        span(sleeveGrip, V(x, by + 0.04, above.z), V(x, by + 0.15, above.z));
      });
    };
  }
  const g = group(curl ? 'leg_curl' : 'leg_extension', parts);
  return {
    group: g,
    place(P, P0, P1) {
      const pivot = drive.place(P, P0, P1);
      fitBack(P0);
      fit(P0, pivot);
    },
  };
}

/**
 * A lying leg curl, laid out as the owner's reference (#149): a bed bent over the hips, its top at
 * `hips` [y, z], the thigh pad sloping down `thigh` degrees to just short of the knees and the chest
 * pad `chest` degrees down toward the head, on legs leaning toward the head; a bar under the bed's
 * head end with grips angled out and down; the tower beside the bed at the hips; and on the near
 * side a post up to the cam lever about the knees, its roller behind the ankles, with a plate on a
 * horn low on the post.
 */
function lyingLegCurl({ hips: [hy, hz] = [0.66, 0], thigh = 21, chest = 10 } = {}) {
  const M = tones();
  const tz = hz + 0.05;
  const drive = kneeDrive(M, { z: tz, toward: -1, down: 0.37, behind: true });
  const { side, tx, z0 } = drive;
  const apex = V(0, hy, hz);
  // A pad `length` long from the apex `way` along z (±1), sloping down `slope` degrees, on a board
  // and a beam; `at(d, h)` is the point `d` along it and `h` below its top.
  const bedPad = (length, way, slope) => {
    const a = rad(slope);
    const dir = V(0, -Math.sin(a), way * Math.cos(a));
    const normal = V(0, Math.cos(a), way * Math.sin(a));
    const holder = new THREE.Group();
    holder.add(
      pad(0.32, 0.07, length, M.vinyl, V(0, -0.035, 0), { soft: 0.025 }),
      pad(0.28, 0.012, length - 0.04, M.frame, V(0, -0.076, 0), { soft: 0.003 }),
      tube(0.06, V(0, -0.112, -length / 2 + 0.03), V(0, -0.112, length / 2 - 0.03), M.frame),
    );
    holder.position.copy(apex).addScaledVector(dir, length / 2 + 0.01);
    holder.rotation.x = way * a;
    return { mesh: holder, at: (d, h) => apex.clone().addScaledVector(dir, d).addScaledVector(normal, -h) };
  };
  const thighLength = 0.37;
  const chestLength = 0.62;
  const thighPad = bedPad(thighLength, -1, thigh);
  const chestPad = bedPad(chestLength, 1, chest);
  const head = chestPad.at(chestLength, 0);
  const kneeEnd = thighPad.at(thighLength - 0.04, 0.112);
  const hipLeg = chestPad.at(0.1, 0.112);
  const headLeg = chestPad.at(chestLength - 0.05, 0.112);
  // The handles: a bar across under the head end, hung from the chest pad's beam, its grips angled
  // out and down.
  const handleY = head.y - 0.24;
  const handleZ = head.z - 0.02;
  // A small plate stored on a horn low on the post.
  const horn = part(rodGeometry(0.02), M.metal);
  const stored = part(plateGeometry(0.1, 0.03), M.plate, { solid: true });
  // A bar from under the thigh pad's knee end out to the post.
  const kneeBar = stretchedTube(0.05, M.frame);
  const g = group('leg_curl', [
    thighPad.mesh, chestPad.mesh, ...drive.parts, horn, stored, kneeBar,
    ...floorRail(V(0, 0.035, z0 + 0.035), V(0, 0.035, head.z - 0.2), M),
    ...floorRail(V(-0.3, 0.035, head.z - 0.165), V(0.3, 0.035, head.z - 0.165), M),
    ...floorRail(V(tx, 0.035, z0 + 0.035), V(tx, 0.035, tz + 0.3), M),
    tube(0.06, V(0, 0.07, hipLeg.z - 0.22), hipLeg, M.frame),
    tube(0.06, V(0, 0.07, head.z - 0.2), headLeg, M.frame),
    tube(0.05, chestPad.at(chestLength - 0.05, 0.14), V(0, handleY, handleZ), M.frame),
    tube(0.04, V(-0.14, handleY, handleZ), V(0.14, handleY, handleZ), M.frame),
    ...[-1, 1].flatMap((s) => [
      rod(0.016, V(s * 0.14, handleY, handleZ), V(s * 0.3, handleY - 0.06, handleZ), M.metal, { held: true }),
      rod(0.0195, V(s * 0.17, handleY - 0.011, handleZ), V(s * 0.29, handleY - 0.056, handleZ), M.vinyl, { held: true }),
    ]),
  ]);
  return {
    group: g,
    place(P, P0, P1) {
      const pivot = drive.place(P, P0, P1);
      span(kneeBar, kneeEnd, kneeEnd.clone().setX(side));
      const low = V(side, 0.07, z0).lerp(pivot, 0.3);
      span(horn, low, low.clone().add(V(0.14, 0, 0)));
      stored.position.copy(low).add(V(0.08, 0, 0));
    },
  };
}

/**
 * A plate-loaded seated calf raise, after a Hammer Strength-style one (#150): a beam along the floor
 * with feet across both ends, the seat on its post at the back, a low foot block across the beam,
 * the balls of the feet on its back edge, and a post in front of the toes. The lever pivots on the
 * post's head, about as high as the pads, and runs back over the shins to the pads on the thighs
 * just behind the knees, a crossbar over them with a U-handle on top; a horn either side behind the
 * pivot is loaded with a 20. The lever is flat at the stretch, where a peg on the post sits a few
 * centimetres under it, and turns up with the knees, the pads riding the thighs.
 */
function calfRaise({ seat = [0.48, -0.05] } = {}) {
  const M = tones();
  const [seatY, seatZ] = seat;
  // What stands still: the base, the seat, the foot block, and the post with the pivot and its peg.
  const beam = stretchedTube(0.07, M.frame);
  const beamCaps = [0, 1].map(() => pad(0.016, 0.074, 0.074, M.vinyl, V(0, 0, 0), { soft: 0.005, solid: false }));
  const frontFeet = movableRail(M);
  const block = part(padGeometry(0.46, 0.12, 0.14, 0.006), M.frame, { solid: true });
  const deck = part(padGeometry(0.44, 0.006, 0.12, 0.002), M.metal);
  const post = stretchedTube(0.07, M.frame);
  const postPlate = pad(0.12, 0.012, 0.12, M.frame, V(0, 0, 0), { soft: 0.003, solid: false });
  const cheeks = [-1, 1].map(() => part(padGeometry(0.012, 0.13, 0.11, 0.003), M.frame));
  const boss = hub(M, V(0, 0, 0));
  const axle = part(rodGeometry(0.008, 12), M.metal);
  const peg = part(rodGeometry(0.014, 12), M.metal);
  const pegKnob = knob(M, V(0, 0, 0), 0.02);
  // The lever, built flat at the stretch about the pivot and turned there as one.
  const lever = new THREE.Group();
  lever.name = 'calf_raise_lever';
  const arm = stretchedTube(0.07, M.frame);
  const drop = stretchedTube(0.055, M.frame);
  const pin = popPin(V(0, 0, 0), 1, M);
  const crossbar = part(padGeometry(0.46, 0.014, 0.13, 0.003), M.frame);
  const kneePads = [-1, 1].map(() => part(padGeometry(0.19, 0.07, 0.14, 0.025), M.vinyl, { solid: true }));
  const handle = bent('calf_raise_u', [V(-0.13, 0, 0), V(-0.13, 0.1, 0), V(-0.1, 0.14, 0), V(0.1, 0.14, 0), V(0.13, 0.1, 0), V(0.13, 0, 0)], 0.016, M.frame);
  const grips = [-1, 1].map(() => part(rodGeometry(0.0195), M.vinyl, { held: true }));
  const horns = [-1, 1].map(() => ({
    horn: part(rodGeometry(0.025, 20), M.metal, { solid: true }),
    collar: part(rodGeometry(0.045, 24), M.metal),
    plate: part(plateGeometry(0.225, 0.05), M.plate, { solid: true }),
  }));
  lever.add(arm, drop, pin, crossbar, ...kneePads, handle, ...grips, ...horns.flatMap((h) => [h.horn, h.collar, h.plate]));
  const g = group('calf_raise', [
    ...seatOn(seatY, seatZ, M, { pin: -1 }),
    ...floorRail(V(-0.3, 0.035, seatZ - 0.17), V(0.3, 0.035, seatZ - 0.17), M),
    beam, ...beamCaps, ...frontFeet.parts, block, deck, post, postPlate, ...cheeks, boss, axle, peg, pegKnob, lever,
  ]);
  return {
    group: g,
    place(P, P0, P1) {
      // The foot block, the balls on its back edge and the heels hanging off behind it.
      const ball = P0.platform;
      block.position.set(0, ball.y - 0.006 - 0.06, ball.z + 0.03);
      deck.position.set(0, ball.y - 0.003, ball.z + 0.03);
      // The pads sit 6 cm over the knees at the stretch, on the thighs a hand's breadth back from
      // them; the pivot, the pose's, stands in front of the toes, a little above the pads' undersides.
      const knee = P1.knee;
      const padZ = knee.z - 0.05;
      const pivot = P0.pivot;
      lever.position.copy(pivot);
      // The base, from behind the seat to the front feet under the post, and the post up to the pivot.
      const rear = seatZ - 0.17;
      const front = pivot.z + 0.1;
      span(beam, V(0, 0.035, rear), V(0, 0.035, front));
      railCap(beamCaps[0], V(0, 0, rear - 0.035), V(0, 0, -1));
      railCap(beamCaps[1], V(0, 0, front + 0.035), V(0, 0, 1));
      frontFeet.set(front, 0.4);
      span(post, V(0, 0.07, pivot.z + 0.07), V(0, pivot.y + 0.05, pivot.z + 0.07));
      postPlate.position.set(0, 0.076, pivot.z + 0.07);
      cheeks.forEach((c, i) => c.position.set((i ? 1 : -1) * 0.042, pivot.y, pivot.z + 0.035));
      boss.position.set(0.06, pivot.y, pivot.z);
      span(axle, V(-0.06, pivot.y, pivot.z), V(0.09, pivot.y, pivot.z));
      // The peg out of the post's back, under the lever at the stretch: a working lever is held off it.
      const pegY = pivot.y - 0.035 - 0.04;
      span(peg, V(0, pegY, pivot.z + 0.035), V(0, pegY, pivot.z - 0.09));
      pegKnob.position.set(0, pegY, pivot.z - 0.09);
      // The lever in the pivot's frame: the arm back to the pads, a drop down to their crossbar, the
      // U-handle on it and the horns behind the pivot.
      const local = (y, z) => V(0, y - pivot.y, z - pivot.z);
      const padBottom = knee.y + 0.06;
      const bar = padBottom + 0.077;
      span(arm, local(pivot.y, pivot.z + 0.035), local(pivot.y, padZ + 0.03));
      span(drop, local(pivot.y + 0.035, padZ + 0.03), local(bar, padZ + 0.03));
      pin.position.copy(local(pivot.y - 0.01, padZ + 0.09));
      crossbar.position.copy(local(bar, padZ));
      kneePads.forEach((m, i) => m.position.copy(local(padBottom + 0.035, padZ)).setX((i ? 1 : -1) * 0.11));
      handle.position.copy(local(bar + 0.007, padZ));
      grips.forEach((m, i) => span(m, local(bar + 0.02, padZ).setX((i ? 1 : -1) * 0.13), local(bar + 0.1, padZ).setX((i ? 1 : -1) * 0.13)));
      horns.forEach(({ horn, collar, plate }, i) => {
        const s = i ? 1 : -1;
        const at = local(pivot.y, pivot.z - 0.12);
        span(horn, at, at.clone().setX(s * 0.42));
        span(collar, at.clone().setX(s * 0.2), at.clone().setX(s * 0.22));
        plate.position.copy(at).setX(s * 0.25);
      });
      // The lever turns about the pivot with the knees, as the pose turns it: the pads ride the thighs.
      lever.rotation.x = -P.turn;
    },
  };
}

/** How far either side of the middle a hack squat's rails run. */
const HACK_RAILS = 0.17;

/**
 * A plate-loaded hack squat, after a Hammer Strength-style machine (#150): a base along the floor
 * with feet across both ends; the foot platform low in front, tilted toes up on its legs; two chrome
 * rails along the hips' path, from a bracket just behind the platform up to a capped post at the
 * rear, braced back to the base and carrying a storage horn a side; and the carriage riding the
 * rails on bearings. The carriage holds the back pad, its face `pad` metres behind the hips' line,
 * a pad over each shoulder, a handle by each that lies along the hand's knuckles, and a horn a side
 * loaded with a 20. It moves as one, along the rails, with the hips.
 */
function squatMachine({ pad: behind = 0.2 } = {}) {
  const M = tones();
  // What stands still: the base, the platform and its legs, the rails, their bracket and cap, and
  // the rear post with its brace and horns.
  const beam = stretchedTube(0.07, M.frame);
  const beamCaps = [0, 1].map(() => pad(0.016, 0.074, 0.074, M.vinyl, V(0, 0, 0), { soft: 0.005, solid: false }));
  const feet = [0, 1].map(() => movableRail(M));
  const platform = part(padGeometry(0.76, 0.035, 0.58, 0.008), M.frame, { solid: true });
  const deck = part(padGeometry(0.7, 0.006, 0.52, 0.002), M.metal);
  const platformLegs = [-1, 1].map(() => stretchedTube(0.06, M.frame));
  const platformBack = stretchedTube(0.05, M.frame);
  const rails = [-1, 1].map(() => part(rodGeometry(0.025, 20), M.metal, { solid: true }));
  const bracket = part(padGeometry(0.46, 0.07, 0.1, 0.006), M.frame, { solid: true });
  const bracketPost = stretchedTube(0.06, M.frame);
  const cap = part(padGeometry(0.48, 0.08, 0.12, 0.008), M.frame, { solid: true });
  const post = stretchedTube(0.08, M.frame);
  const postPlate = pad(0.13, 0.012, 0.13, M.frame, V(0, 0, 0), { soft: 0.003, solid: false });
  const brace = stretchedTube(0.06, M.frame);
  const storage = [-1, 1].map(() => part(rodGeometry(0.025, 20), M.metal));
  // The carriage, placed at the top of the rep and moved as one with the hips.
  const carriage = new THREE.Group();
  carriage.name = 'squat_machine_carriage';
  const cushion = part(padGeometry(0.42, 0.62, 0.08, 0.025), M.vinyl, { solid: true });
  const board = part(padGeometry(0.38, 0.58, 0.012, 0.003), M.frame);
  const members = [-1, 1].map(() => stretchedTube(0.05, M.frame));
  const crossbars = [0, 1].map(() => stretchedTube(0.05, M.frame));
  const bearings = [0, 1, 2, 3].map(() => part(padGeometry(0.075, 0.14, 0.075, 0.01), M.pulley));
  const shoulderPads = [-1, 1].map(() => part(padGeometry(0.12, 0.08, 0.24, 0.025), M.vinyl, { solid: true }));
  const shoulderBoards = [-1, 1].map(() => part(padGeometry(0.1, 0.012, 0.22, 0.003), M.frame));
  const mounts = [-1, 1].map(() => stretchedTube(0.05, M.frame));
  const grips = [-1, 1].map(() => ({
    bar: part(rodGeometry(0.016), M.metal, { held: true }),
    sleeve: part(rodGeometry(0.0195), M.vinyl, { held: true }),
    end: knob(M, V(0, 0, 0), 0.022),
    arm: stretchedTube(0.04, M.frame),
  }));
  const horns = [-1, 1].map(() => ({
    horn: part(rodGeometry(0.025, 20), M.metal, { solid: true }),
    stop: part(rodGeometry(0.045, 24), M.metal),
    plate: part(plateGeometry(0.225, 0.05), M.plate, { solid: true }),
  }));
  carriage.add(cushion, board, ...members, ...crossbars, ...bearings, ...shoulderPads, ...shoulderBoards, ...mounts,
    ...grips.flatMap((h) => [h.bar, h.sleeve, h.end, h.arm]), ...horns.flatMap((h) => [h.horn, h.stop, h.plate]));
  const g = group('squat_machine', [
    beam, ...beamCaps, ...feet.flatMap((f) => f.parts), platform, deck, ...platformLegs, platformBack,
    ...rails, bracket, bracketPost, cap, post, postPlate, brace, ...storage, carriage,
  ]);
  return {
    group: g,
    place(P, P0) {
      // The carriage's frame at the top of the rep: `sled` up the rails, `face` out of the back pad's
      // face, from the `origin` on the pad's face at the hips.
      const sled = P0.sled.clone();
      const face = X.clone().cross(sled).normalize();
      const origin = P0.hipC.clone().addScaledVector(face, -behind);
      const at = (along, out, x = 0) => origin.clone().addScaledVector(sled, along).addScaledVector(face, out).setX(x);
      const facing = new THREE.Quaternion().setFromRotationMatrix(new THREE.Matrix4().makeBasis(X, sled, face));
      const lay = (mesh, along, out, x = 0) => { mesh.position.copy(at(along, out, x)); mesh.quaternion.copy(facing); };
      const shoulder = P0.shoulders.clone().sub(origin).dot(sled);
      const top = shoulder + 0.12;
      // The rails, behind the carriage, from just above the floor to a cap 30 cm past the carriage's
      // top at the top of the rep.
      const railOut = -0.21;
      const bottom = (0.17 - origin.y - face.y * railOut) / sled.y;
      const end = top + 0.3;
      [-1, 1].forEach((side, i) => span(rails[i], at(bottom, railOut, side * HACK_RAILS), at(end, railOut, side * HACK_RAILS)));
      lay(bracket, bottom - 0.03, railOut);
      lay(cap, end + 0.04, railOut);
      const low = at(bottom - 0.03, railOut);
      span(bracketPost, low, low.clone().setY(0.07));
      // The rear post stands under the cap; a brace runs back from it to the base's rear end.
      const head = at(end + 0.04, railOut - 0.06);
      const rearZ = head.z - 0.3;
      span(post, head, head.clone().setY(0.012));
      postPlate.position.set(0, 0.006, head.z);
      span(brace, V(0, head.y * 0.5, head.z), V(0, 0.07, rearZ + 0.04));
      storage.forEach((h, i) => span(h, V(0, head.y * 0.62, head.z), V((i ? 1 : -1) * 0.26, head.y * 0.62, head.z)));
      // The platform, its face under the feet, its back edge just ahead of the heels; two legs down
      // from its front to the front feet, and a strut from its back to the base.
      const up = P0.plateUp.clone();
      const ahead = X.clone().cross(up).normalize();
      const middle = P0.face.clone().addScaledVector(up, -0.0175).addScaledVector(ahead, 0.13);
      platform.position.copy(middle);
      platform.quaternion.setFromRotationMatrix(new THREE.Matrix4().makeBasis(X, up, ahead));
      deck.position.copy(middle).addScaledVector(up, 0.0205);
      deck.quaternion.copy(platform.quaternion);
      const front = middle.clone().addScaledVector(ahead, 0.18).addScaledVector(up, -0.02);
      const frontZ = front.z;
      platformLegs.forEach((leg, i) => span(leg, front.clone().setX((i ? 1 : -1) * 0.27), V((i ? 1 : -1) * 0.27, 0.07, frontZ)));
      const under = middle.clone().addScaledVector(ahead, -0.12).addScaledVector(up, -0.02);
      span(platformBack, under, V(0, 0.07, under.z));
      // The base: a beam from the rear feet to the front feet, capped at both ends.
      span(beam, V(0, 0.035, rearZ), V(0, 0.035, frontZ));
      railCap(beamCaps[0], V(0, 0, rearZ - 0.035), V(0, 0, -1));
      railCap(beamCaps[1], V(0, 0, frontZ + 0.035), V(0, 0, 1));
      feet[0].set(rearZ, 0.6);
      feet[1].set(frontZ, 0.45);
      // The carriage: the back pad on its board, two members behind it riding the rails on bearings,
      // a pad over each shoulder on a mount off the members' heads, and a loaded horn a side.
      const padMiddle = (shoulder - 0.03 - 0.15) / 2;
      lay(cushion, padMiddle, -0.04);
      lay(board, padMiddle, -0.086);
      [-1, 1].forEach((side, i) => {
        const x = side * HACK_RAILS;
        span(members[i], at(-0.2, -0.14, x), at(top, -0.14, x));
        [-0.12, shoulder].forEach((t, k) => lay(bearings[2 * i + k], t, railOut, x));
        lay(shoulderPads[i], shoulder + 0.11, 0.1, side * 0.155);
        lay(shoulderBoards[i], shoulder + 0.156, 0.1, side * 0.155);
        span(mounts[i], at(top, -0.14, x), at(shoulder + 0.16, -0.01, side * 0.155));
        // The handle runs the way the pose lays the grip at the top, fixed on the carriage: the
        // wrists turn the knuckles along it. It sits on an arm back to the shoulder pad's outer side.
        const { bar, sleeve, end: knobEnd, arm } = grips[i];
        const h = P.hand(side).sub(P.hipC).add(P0.hipC);
        const along = P0.across(side).normalize();
        const mount = at(shoulder + 0.11, 0.02, side * 0.225);
        const [near, far] = [-1, 1].map((k) => h.clone().addScaledVector(along, k * 0.1)).sort((a, b) => a.distanceTo(mount) - b.distanceTo(mount));
        span(bar, near, far);
        span(sleeve, h.clone().addScaledVector(along, -0.055), h.clone().addScaledVector(along, 0.055));
        knobEnd.position.copy(far);
        span(arm, near, mount);
        const { horn, stop, plate } = horns[i];
        const from = at(-0.05, -0.17, x);
        span(horn, from, from.clone().setX(side * 0.52));
        span(stop, from.clone().setX(side * 0.24), from.clone().setX(side * 0.26));
        plate.position.copy(from).setX(side * 0.3);
      });
      [-0.2, top].forEach((t, k) => span(crossbars[k], at(t, -0.14, -HACK_RAILS), at(t, -0.14, HACK_RAILS)));
      carriage.position.copy(P.hipC).sub(P0.hipC);
    },
  };
}

/** The angle of `p` about the x axis through `pivot`, side-on: what a lever there has swung. */
const swingOf = (pivot, p) => Math.atan2(p.y - pivot.y, p.z - pivot.z);

/** The point `length` metres from `pivot` at `angle` about the x axis, as `swingOf` measures. */
const around = (pivot, angle, length) => pivot.clone().add(V(0, length * Math.sin(angle), length * Math.cos(angle)));

/**
 * A floor rail 7 cm across whose ends are known only once the kit is placed: `set(A, B)` lays it
 * from A to B along x or z.
 */
function placedRail(M) {
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
 * from its arm, to a horn out along x with a plate on it.
 */
function levers(M, { out, horn: { length, turn } }) {
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
      }
    },
  };
}

/**
 * A pad in front of the chest at the top of the rep: its face `front` metres out from the line up
 * the spine, its middle `up` metres up it, on an arm from its back to the point `to(back)` returns.
 */
function chestPad(M, { up, front }) {
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

/**
 * A plate-loaded chest-supported row, laid out as the Gymleco 010 Seated Row: the seat, and ahead
 * of the knees a column up from the base to the chest pad's arm, with the levers' axle across it
 * low down at the pose's pivot, braced to the base. Each lever rises from its hub to a handle and
 * runs on forward and down to a loaded horn, whose plate rises as the handles come back.
 */
function rowMachine({ seat = [0.5, 0], chest = { up: 0.4, front: 0.1 } } = {}) {
  const M = tones();
  const out = 0.3;
  const pad_ = chestPad(M, chest);
  const arms = levers(M, { out, horn: { length: 0.45, turn: -100 } });
  const column = stretchedTube(0.08, M.frame);
  const housing = stretchedTube(0.06, M.frame);
  const axle = part(rodGeometry(0.02), M.metal);
  const braces = [-1, 1].map(() => stretchedTube(0.05, M.frame));
  const spine = placedRail(M);
  const front = placedRail(M);
  const g = group('row_machine', [
    ...seatOn(seat[0], seat[1], M, { pin: -1 }),
    ...floorRail(V(-0.25, 0.035, seat[1] - 0.18), V(0.25, 0.035, seat[1] - 0.18), M),
    ...spine.parts, ...front.parts, ...pad_.parts, ...arms.parts, column, housing, axle, ...braces,
  ]);
  return {
    group: g,
    place(P, P0) {
      arms.place(P);
      const { pivot } = P;
      // The braces lean from the axle's housing forward and down to the base's front rail.
      const foot = pivot.z + 0.35;
      spine.set(V(0, 0, seat[1] - 0.25), V(0, 0, foot + 0.1));
      front.set(V(-out, 0, foot), V(out, 0, foot));
      const base = V(0, 0.07, pivot.z);
      // The chest pad's arm runs forward from its board to the column's head.
      pad_.fit(P0, (back) => {
        const head = base.clone().setY(back.y);
        span(column, base, head);
        return head;
      });
      span(housing, pivot.clone().setX(-out + 0.04), pivot.clone().setX(out - 0.04));
      span(axle, pivot.clone().setX(-out - 0.03), pivot.clone().setX(out + 0.03));
      [-1, 1].forEach((s, i) => span(braces[i], pivot.clone().setX(s * (out - 0.06)), V(s * (out - 0.06), 0.07, foot)));
    },
  };
}

/**
 * A plate-loaded iso-lateral pulldown, laid out as the Gymleco 011: a seat with a back pad behind
 * the lifter (its face `pad` metres behind the spine) and pads over the thighs at `thighPad`
 * [y, z] hanging under their bar, facing a portal ahead whose beam carries the levers' axle at the
 * pose's pivot. Each lever runs back over the seat to a handle and on past the axle to a loaded
 * horn, whose plate rises as the handles come down. A brace from the beam to the floor
 * carries the thigh pads' bar.
 */
function pulldownMachine({ seat = [0.47, 0], thighPad: [ty, tz] = [0.71, 0.3], pad: behind = 0.13 } = {}) {
  const M = tones();
  const out = 0.42;
  const post = out - 0.07;
  const arms = levers(M, { out, horn: { length: 0.35, turn: 180 } });
  const backRest = backPad(0.32, 0.56, M, { up: 0.5, behind });
  const beam = stretchedTube(0.08, M.frame);
  const uprights = [-1, 1].map(() => stretchedTube(0.08, M.frame));
  const axle = part(rodGeometry(0.02), M.metal);
  const brace = stretchedTube(0.07, M.frame);
  const padArm = stretchedTube(0.05, M.frame);
  const backPost = stretchedTube(0.06, M.frame);
  const spine = placedRail(M);
  const front = placedRail(M);
  const thighPads = [-1, 1].map((s) => pad(0.2, 0.07, 0.11, M.vinyl, V(s * 0.12, ty, tz), { soft: 0.025 }));
  const barY = ty + 0.035 + 0.02;
  const g = group('pulldown_machine', [
    ...seatOn(seat[0], seat[1], M, { pin: -1 }),
    ...floorRail(V(-0.25, 0.035, seat[1] - 0.3), V(0.25, 0.035, seat[1] - 0.3), M),
    ...spine.parts, ...front.parts, ...thighPads,
    // The pads hang under their bar, on the lifter's side of it.
    tube(0.04, V(-0.22, barY, tz), V(0.22, barY, tz), M.frame),
    backRest.mesh, ...arms.parts, beam, ...uprights, axle, brace, padArm, backPost,
  ]);
  return {
    group: g,
    place(P, P0) {
      arms.place(P);
      const { pivot } = P;
      spine.set(V(0, 0, seat[1] - 0.35), V(0, 0, pivot.z + 0.1));
      front.set(V(-post - 0.15, 0, pivot.z), V(post + 0.15, 0, pivot.z));
      span(beam, pivot.clone().setX(-post - 0.04), pivot.clone().setX(post + 0.04));
      span(axle, pivot.clone().setX(-out - 0.03), pivot.clone().setX(out + 0.03));
      [-1, 1].forEach((s, i) => span(uprights[i], V(s * post, 0.07, pivot.z), pivot.clone().setX(s * post).add(V(0, 0.04, 0))));
      // The brace leans from under the beam down to the floor before the shins, the thigh pads' arm off it.
      const foot = V(0, 0.07, seat[1] + 0.62);
      const under = pivot.clone().setX(0).add(V(0, -0.04, 0));
      span(brace, foot, under);
      span(padArm, V(0, barY, tz), foot.clone().lerp(under, (barY - foot.y) / (under.y - foot.y)));
      const support = backRest.fit(P0);
      span(backPost, support, support.clone().setY(0.07));
    },
  };
}

/**
 * A plate-loaded preacher curl: the lifter leans onto one wide arm pad `pad` metres deep, on a
 * column leaning up from the floor ahead, the chest against its back edge and its front edge
 * stopping short of the elbows, so the upper arms lie on clear pad. A bar on two levers turns about
 * the elbows (`P0.elbow`), on hubs beyond the pad's front edge either side of the arms, and each
 * lever carries a loaded horn on a stub below it, whose plate swings forward and up as the bar
 * curls.
 */
function preacherCurl({ seat = [0.5, 0], pad: deep = 0.08 } = {}) {
  const M = tones();
  const side = 0.3;
  const frontZ = seat[1] + 0.85;
  const thick = 0.07;
  // One wide pad under both upper arms, on a board.
  const armPad = new THREE.Group();
  armPad.add(part(padGeometry(0.5, thick, deep, 0.025), M.vinyl, { solid: true }));
  armPad.add(part(padGeometry(0.46, 0.012, deep - 0.04, 0.003), M.frame).translateY(-thick / 2 - 0.006));
  const column = stretchedTube(0.1, M.frame);
  const crossbar = stretchedTube(0.05, M.frame);
  const mounts = [-1, 1].map(() => stretchedTube(0.04, M.frame));
  const bosses = [-1, 1].map(() => hub(M, V(0, 0, 0)));
  const arms = [-1, 1].map(() => stretchedTube(0.04, M.frame));
  const loads = [-1, 1].map((s) => loadedHorn(M, s));
  const stubs = [-1, 1].map(() => stretchedTube(0.04, M.frame));
  const bar = along(0.014, -0.32, 0.32, M.metal, 16, { held: true });
  const grips = [-1, 1].map((s) => along(0.0175, ...(s < 0 ? [-0.25, -0.1] : [0.1, 0.25]), M.vinyl, 16, { held: true }));
  const handle = new THREE.Group();
  handle.add(bar, ...grips);
  const g = group('preacher_curl', [
    ...seatOn(seat[0], seat[1], M, { pin: -1 }),
    ...floorRail(V(0, 0.035, seat[1] - 0.3), V(0, 0.035, frontZ), M),
    ...floorRail(V(-0.3, 0.035, frontZ), V(0.3, 0.035, frontZ), M),
    ...floorRail(V(-0.25, 0.035, seat[1] - 0.3), V(0.25, 0.035, seat[1] - 0.3), M),
    armPad, column, crossbar, ...mounts, ...bosses, ...arms, ...stubs, ...loads.flatMap((l) => l.parts), handle,
  ]);
  return {
    group: g,
    place(P, P0) {
      // Under the upper arms, on the side of them away from the forearms' swing, its front edge 3 cm
      // short of the elbows.
      const under = V(0, -P0.upper.z, P0.upper.y);
      const at = P0.elbow.clone().addScaledVector(P0.upper, -(0.03 + deep / 2)).addScaledVector(under, 0.045 + thick / 2);
      armPad.position.copy(at);
      armPad.quaternion.setFromRotationMatrix(new THREE.Matrix4().makeBasis(X, under.clone().negate(), X.clone().cross(under.clone().negate())));
      // Carried from under its front edge, clear of the lap.
      const below = at.clone().addScaledVector(P0.upper, deep / 2 + 0.01).addScaledVector(under, thick / 2 + 0.03);
      span(column, V(0, 0.07, frontZ), below);
      span(crossbar, below.clone().setX(-side), below.clone().setX(side));
      handle.position.copy(P.bar);
      [-1, 1].forEach((s, i) => {
        const pivot = P0.elbow.clone().setX(s * side);
        const end = P.bar.clone().setX(s * side);
        bosses[i].position.copy(pivot);
        span(mounts[i], pivot, below.clone().setX(s * side));
        span(arms[i], pivot, end);
        // The horn on a stub a quarter turn below the lever, swinging forward and up as the bar curls.
        const horn = around(pivot, swingOf(pivot, end) + rad(-90), 0.2);
        span(stubs[i], pivot, horn);
        loads[i].place(horn);
      });
    },
  };
}

/** A low platform under the feet, `height` tall (the pose's `feet.y`) and `size` [w, d] at `at` [x, z]: a deficit. */
function platform({ height = 0.05, size = [0.8, 0.5], at = [0, 0.05] } = {}) {
  const M = tones();
  // A frame with a rubber mat on top, where the feet stand.
  const mat = 0.012;
  const g = group('platform', [
    pad(size[0], height - mat, size[1], M.frame, V(at[0], (height - mat) / 2, at[1]), { soft: 0.006 }),
    pad(size[0] - 0.03, mat, size[1] - 0.03, M.vinyl, V(at[0], height - mat / 2, at[1]), { soft: 0.004 }),
  ]);
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
  // Each cable, with where on its handle it hooks on (`hook()`, in the world), for the checks.
  return { group: g, place: (P, P0, P1) => kits.forEach((k) => k.place(P, P0, P1)), cables: kits.flatMap((k) => k.cables ?? []) };
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
