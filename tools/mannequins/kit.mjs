// Generic equipment, built in code from three.js shapes, and the floor shadow under the mannequin.
// Every kit is at one level of detail (#127): rounded tubing on base plates, soft pads, a stack of
// separate plates on guide rods with a selector pin, pulleys in housings with the cable over them,
// and shaped bars and handles with grips; nothing smaller than a phone shows (no bolts or seams).
//
// Each part is in one of five tones, a material the phone colours by name from the theme (`frame`,
// `vinyl`, `plate`, `metal`, `pulley`), plus `shadow`; the colours here are the light theme's, for
// viewing a clip anywhere else, and each tone's roughness ships as it is. Nothing is metallic: the
// stage has no reflections for metal to show.
//
// A kit is built once per clip and placed on every frame from the pose frame (`place(P, top)`,
// with the frame at the top of the rep for what moves relative to it, like a weight stack). A
// part the body must not pass through is `solid`; one the hands hold is `held` too, so only the
// hands may close round it. The checks read both, testing a cylinder as one and anything else as
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
  squat_machine: { kit: null, exercises: ['hack_squat', 'pendulum_squat', 'machine_squat'] },
  leg_extension: { kit: null, exercises: ['leg_extension'] },
  leg_curl: { kit: null, exercises: ['lying_leg_curl', 'seated_leg_curl'] },
  glute_ham_raise: { kit: null, exercises: ['glute_ham_raise'] },
  pec_deck: { kit: null, exercises: ['pec_deck', 'reverse_pec_deck', '1_arm_reverse_pec_deck'] },
  row_machine: { kit: null, exercises: ['machine_row', 'chest_supported_machine_row', 'chest_supported_t_bar_row', 't_bar_row'] },
  pulldown_machine: { kit: null, exercises: ['machine_pulldown', 'machine_lat_pullover'] },
  shoulder_press: { kit: null, exercises: ['machine_shoulder_press'] },
  lateral_raise_machine: { kit: null, exercises: ['machine_lateral_raise', 'standing_machine_lateral_raise'] },
  preacher_curl_machine: { kit: null, exercises: ['machine_preacher_curl'] },
  dip_machine: { kit: null, exercises: ['seated_dip_machine'] },
  hip_machine: { kit: null, exercises: ['machine_hip_abduction', 'machine_adduction'] },
  hip_thrust_machine: { kit: null, exercises: ['machine_hip_thrust'] },
  calf_raise_machine: { kit: null, exercises: ['standing_calf_raise', 'seated_calf_raise', 'donkey_calf_raise'] },
  crunch_machine: { kit: null, exercises: ['machine_crunch'] },
  shrug_machine: { kit: null, exercises: ['machine_shrug', 'kelso_shrug'] },
};

/**
 * The library's equipment, each with the kits a pose in it may use. A kind with no kit yet is
 * `null`, and is listed as missing in the README; a pose can't be made in it until it has one.
 * Bodyweight needs no kit for most of its exercises.
 */
export const EQUIPMENT = {
  barbell: ['barbell', 'bench'],
  dumbbell: ['dumbbells', 'bench'],
  cable: ['cable_tower', 'bench'],
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
  let n = 0;
  g.traverse((o) => { if (o !== g && !o.name) o.name = `${name}_${n++}`; });
  return g;
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
 * The pads stand on legs of tubing with feet across, a spine along the floor and a rail under them.
 */
function bench({ angle = 0, height = 0.44, z = -0.15, length = 1.2, hinge = null, adjustable = false } = {}) {
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
    for (const s of [-1, 1]) parts.push(pad(0.02, 0.06, 0.07, M.vinyl, V(s * 0.22, 0.03, e), { soft: 0.006 }));
  }
  parts.push(tube(0.05, V(0, 0.085, ends[0]), V(0, 0.085, ends[1]), M.frame));
  parts.push(tube(0.04, V(0, under, ends[0] - 0.03), V(0, under, ends[1] + 0.03), M.frame));
  const g = group('bench', parts);
  if (turning) {
    g.add(turning);
    let n = 0;
    turning.traverse((o) => { if (o !== turning && !o.name) o.name = `bench_back_${n++}`; });
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
    },
  };
}

/** A seat: a cushion `w` × `d` whose top is at `top`, on a pan and a post of tubing up from `floor`. */
function seatOn(top, z, M, { w = 0.36, d = 0.38, floor = 0.07 } = {}) {
  return [
    pad(w, 0.075, d, M.vinyl, V(0, top - 0.0375, z), { soft: 0.024 }),
    pad(w - 0.06, 0.012, d - 0.06, M.frame, V(0, top - 0.081, z), { soft: 0.003 }),
    tube(0.06, V(0, floor, z), V(0, top - 0.087, z), M.frame),
  ];
}

/** The radius of a cable's pulley wheels. */
const WHEEL = 0.045;

/** A pulley wheel at `at`, about x, between the two sides of its housing, which reaches `up` above the axle. */
function pulley(at, M, up = WHEEL + 0.02) {
  const parts = [turnedPart(wheelGeometry(WHEEL, 0.022), M.pulley, at, {})];
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
 * `lat_bar`, `straight_bar` or `v_handle`; `seat` [y, z] adds a seat and `thighPad` [y, z] the
 * rollers over the knees, for a pulldown.
 */
function cableTower({ column = 0.9, pulley: [py, pz] = [2.2, 0], attachment = 'straight_bar', seat = null, thighPad = null } = {}) {
  if (py < 1.5) throw new Error(`The cable tower's pulley at ${py} m is low; it has only a high pulley yet`);
  const M = tones();
  // The stack stands behind the pulley, far enough back for its own wheel.
  const sz = Math.max(column - 0.12, pz + 4 * WHEEL + 0.08);
  const ux = 0.19;
  const wheelAt = V(0, py, pz);
  const frontWheel = V(0, py, pz + WHEEL);
  const backWheel = V(0, py, sz - WHEEL);
  const boomY = py + WHEEL + 0.02 + 0.035;
  const rodTop = Math.min(2.0, boomY - 0.3);
  const frontZ = seat ? seat[1] - 0.25 : sz - 0.32;
  const parts = [
    ...[-1, 1].flatMap((s) => [
      tube(0.07, V(s * ux, 0.082, sz), V(s * ux, boomY + 0.035, sz), M.frame),
      pad(0.11, 0.012, 0.11, M.frame, V(s * ux, 0.076, sz), { soft: 0.003 }),
    ]),
    tube(0.07, V(-ux - 0.035, boomY, sz), V(ux + 0.035, boomY, sz), M.frame),
    tube(0.07, V(0, boomY, sz + 0.07), V(0, boomY, Math.min(pz, sz) - 0.02), M.frame),
    tube(0.05, V(-ux, rodTop + 0.025, sz), V(ux, rodTop + 0.025, sz), M.frame),
    tube(0.07, V(-ux - 0.15, 0.035, sz), V(ux + 0.15, 0.035, sz), M.frame),
    tube(0.07, V(0, 0.035, sz - 0.035), V(0, 0.035, frontZ - 0.035), M.frame),
    tube(0.07, V(-0.3, 0.035, frontZ), V(0.3, 0.035, frontZ), M.frame),
    ...pulley(frontWheel, M),
    ...pulley(backWheel, M),
    // The cable over the wheels' tops, from the front one back to the one over the stack.
    rod(0.0045, V(0, py + WHEEL, frontWheel.z), V(0, py + WHEEL, backWheel.z), M.metal),
  ];
  const cable = part(rodGeometry(0.0045, 8), M.metal);
  const drop = part(rodGeometry(0.0045, 8), M.metal);
  const weights = weightStack(V(0, 0, sz), M, { rodTop, toward: -1 });
  if (seat) parts.push(...seatOn(seat[0], seat[1], M));
  if (thighPad) {
    const [ty, tz] = thighPad;
    parts.push(tube(0.05, V(0, 0.07, tz + 0.1), V(0, ty + 0.025, tz + 0.1), M.frame));
    parts.push(tube(0.04, V(0, ty, tz + 0.12), V(0, ty, tz), M.frame));
    parts.push(along(0.012, -0.235, 0.235, M.metal, 12).translateY(ty).translateZ(tz));
    for (const s of [-1, 1]) parts.push(turnedPart(rollerGeometry(0.05, 0.2), M.vinyl, V(s * 0.13, ty, tz)));
  }
  if (!ATTACHMENTS[attachment]) throw new Error(`No cable attachment "${attachment}"`);
  const { parts: grip, hook: above } = ATTACHMENTS[attachment](M);
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
      const lift = at.distanceTo(wheelAt) - hook(P0).distanceTo(wheelAt);
      weights.lift(lift);
      span(drop, V(0, py, sz), weights.top(lift));
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
  const parts = [
    seatPad, seatBoard, backRest.mesh,
    tube(0.07, V(0, 0.035, -0.6), V(0, 0.035, 1.7), M.frame),
    ...[-0.6, 1.7].map((z) => tube(0.07, V(-0.35, 0.035, z), V(0.35, 0.035, z), M.frame)),
    tube(0.08, V(0, 0.07, seat[1] - 0.05), V(0, seat[0] - 0.1, seat[1] - 0.05), M.frame),
  ];
  const rails = [0, 1].map(() => part(tubeGeometry(0.06, 0.8), M.frame, { solid: true }));
  const legs = [0, 1].map(() => stretchedTube(0.06, M.frame));
  const plate = pad(0.62, 0.04, 0.52, M.frame, V(0, 0, 0), { soft: 0.008 });
  const sled = pad(0.5, 0.08, 0.7, M.frame, V(0, 0, 0), { soft: 0.01, solid: false });
  const horns = [0, 1].map(() => part(rodGeometry(0.025), M.metal));
  const weights = [0, 1].map(() => part(plateGeometry(0.17, 0.04), M.plate, { solid: true }));
  const handles = [0, 1].map(() => part(rodGeometry(0.016), M.metal, { held: true }));
  const grips = [0, 1].map(() => part(rodGeometry(0.0195), M.vinyl, { held: true }));
  const posts = [0, 1].map(() => stretchedTube(0.04, M.frame));
  const g = group('leg_press', [...parts, ...rails, ...legs, plate, sled, ...horns, ...weights, ...handles, ...grips, ...posts]);
  return {
    group: g,
    place(P, P0) {
      backRest.fit(P0);
      const along = P.sled;
      const up = P.plateUp;
      const under = P0.plate.clone().addScaledVector(up, -0.3);
      [-1, 1].forEach((s, i) => {
        const lo = under.clone().addScaledVector(along, -0.45).setX(s * 0.24);
        const hi = under.clone().addScaledVector(along, 0.35).setX(s * 0.24);
        orient(rails[i], lo, hi);
        span(legs[i], hi, hi.clone().setY(0.05));
        const horn = P.plate.clone().addScaledVector(along, 0.3).addScaledVector(up, -0.22);
        span(horns[i], horn.clone().setX(s * 0.25), horn.clone().setX(s * 0.5));
        weights[i].position.copy(horn).setX(s * 0.36);
        const h = P.hand(s);
        span(handles[i], h.clone().setZ(h.z - 0.09), h.clone().setZ(h.z + 0.09));
        span(grips[i], h.clone().setZ(h.z - 0.06), h.clone().setZ(h.z + 0.06));
        span(posts[i], h.clone().setZ(h.z - 0.08), V(s * 0.3, 0.42, h.z - 0.08));
      });
      // The plate's face is square to the sled: its thickness runs along the sled.
      const q = new THREE.Quaternion().setFromUnitVectors(UP, along.clone().negate());
      plate.position.copy(P.plate).addScaledVector(along, 0.02);
      plate.quaternion.copy(q);
      sled.position.copy(P.plate).addScaledVector(along, 0.18).addScaledVector(up, -0.12);
      sled.quaternion.copy(q);
    },
  };
}

/**
 * A seated chest press: the seat and back pad (its face `pad` metres behind the spine), a column
 * behind with a boom out to the pivot, and two levers hung from it, swinging the handles through
 * their arc. Behind the column a weight stack in its frame, its cable over a pulley to the column,
 * rises as the levers swing forward. `P.pivot`, `P.handle(s)` and `P.swing` (the levers' angle,
 * radians from hanging straight down) place them.
 */
function chestPress({ seat = [0.47, 0.02], pad: behind = 0.2 } = {}) {
  const M = tones();
  const backRest = backPad(0.36, 0.64, M, { up: 0.36, behind });
  const rear = -0.55;
  const stackZ = -0.71;
  const ux = 0.19;
  const frameTop = 1.72;
  const wheel = V(0, frameTop - 0.035 - WHEEL - 0.02, stackZ + WHEEL);
  const parts = [
    ...seatOn(seat[0], seat[1], M),
    backRest.mesh,
    tube(0.07, V(0, 0.035, stackZ - 0.12), V(0, 0.035, seat[1] + 0.2), M.frame),
    tube(0.07, V(-ux - 0.15, 0.035, stackZ), V(ux + 0.15, 0.035, stackZ), M.frame),
    tube(0.07, V(-0.3, 0.035, seat[1] + 0.2), V(0.3, 0.035, seat[1] + 0.2), M.frame),
    ...[-1, 1].flatMap((s) => [
      tube(0.07, V(s * ux, 0.082, stackZ), V(s * ux, frameTop + 0.035, stackZ), M.frame),
      pad(0.11, 0.012, 0.11, M.frame, V(s * ux, 0.076, stackZ), { soft: 0.003 }),
    ]),
    tube(0.07, V(-ux - 0.035, frameTop, stackZ), V(ux + 0.035, frameTop, stackZ), M.frame),
    ...pulley(wheel, M),
    // The cable from the wheel's top forward into the column.
    rod(0.0045, V(0, wheel.y + WHEEL, wheel.z), V(0, wheel.y + WHEEL, rear), M.metal),
  ];
  const column = stretchedTube(0.1, M.frame);
  const boom = stretchedTube(0.08, M.frame);
  const axle = part(rodGeometry(0.022), M.metal);
  const hubs = [0, 1].map(() => part(wheelGeometry(0.05, 0.05), M.pulley));
  // Each lever hangs outside the body and ends in a horizontal handle reaching in across the
  // palm, held like a bar, with a grip on it.
  const levers = [0, 1].map(() => ({
    arm: stretchedTube(0.045, M.frame),
    grip: part(rodGeometry(0.017), M.metal, { held: true }),
    sleeve: part(rodGeometry(0.0205), M.vinyl, { held: true }),
  }));
  const drop = part(rodGeometry(0.0045, 8), M.metal);
  const weights = weightStack(V(0, 0, stackZ), M, { rodTop: 1.5, toward: 1 });
  const g = group('chest_press', [
    ...parts, column, boom, axle, ...hubs, ...levers.flatMap((l) => [l.arm, l.grip, l.sleeve]), drop, ...weights.parts,
  ]);
  return {
    group: g,
    place(P, P0) {
      backRest.fit(P0);
      const top = P.pivot.y + 0.05;
      span(column, V(0, 0.07, rear), V(0, top + 0.04, rear));
      span(boom, V(0, top, rear - 0.05), V(0, top, P.pivot.z + 0.04));
      span(axle, P.pivot.clone().setX(-0.52), P.pivot.clone().setX(0.52));
      levers.forEach((l, i) => {
        const s = i ? 1 : -1;
        const h = P.handle(s);
        const out = 0.5;
        hubs[i].position.copy(P.pivot).setX(s * out);
        span(l.arm, P.pivot.clone().setX(s * out), h.clone().setX(s * out));
        span(l.grip, h.clone().setX(s * out), h.clone().setX(s * (Math.abs(h.x) - 0.1)));
        span(l.sleeve, h.clone().setX(s * (out - 0.04)), h.clone().setX(s * (Math.abs(h.x) - 0.08)));
      });
      const lift = 0.5 * (P0.swing - P.swing);
      weights.lift(lift);
      span(drop, V(0, wheel.y, stackZ), weights.top(lift));
    },
  };
}

export const KITS = { barbell, dumbbells, bench, cable_tower: cableTower, leg_press: legPress, chest_press: chestPress };

/** Builds a pose's kit: each kit it names, with its options, in one group placed together. */
export function buildKit(entry) {
  const allowed = EQUIPMENT[entry.equipment];
  if (allowed === undefined) throw new Error(`No equipment "${entry.equipment}" in the library`);
  if (allowed === null) throw new Error(`"${entry.equipment}" has no kit yet`);
  const kits = Object.entries(entry.kit ?? {}).map(([name, options]) => {
    if (!KITS[name]) throw new Error(`No kit "${name}"`);
    if (!allowed.includes(name)) throw new Error(`Kit "${name}" isn't ${entry.equipment} equipment`);
    // An adjustable bench stands at the angle the pose is prepared at.
    const kit = KITS[name](name === 'bench' && entry.bench ? { ...options, adjustable: true, angle: entry.bench.angle } : options);
    // The bake shares the parts under this node with every clip in the same kit.
    kit.group.userData.kit = name;
    return kit;
  });
  const g = new THREE.Group();
  g.name = 'kit';
  kits.forEach((k) => g.add(k.group));
  return { group: g, place: (P, P0) => kits.forEach((k) => k.place(P, P0)) };
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
