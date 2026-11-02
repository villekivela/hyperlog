// Generic equipment, built from primitives, and the floor shadow under the mannequin. Materials
// are named for the phone, which colours each by name from the theme: `bar` for metal (bars,
// frames, cables, handles) and `plate` for the heavy, padded parts (plates, stacks, pads), plus
// `shadow`; the colours here are the light theme's, for viewing a clip anywhere else. Nothing is
// metallic: the stage has no reflections for metal to show.
//
// A kit is built once per clip and placed on every frame from the pose frame (`place(P, top)`,
// with the frame at the top of the rep for what moves relative to it, like a weight stack). A
// part the body must not pass through is `solid`; one the hands hold is `held` too, so only the
// hands may close round it. The checks read both.
import * as THREE from 'three';
import { V, X, UP, rad } from './rig.mjs';

export const MATERIALS = {
  body: () => new THREE.MeshStandardMaterial({ name: 'body', color: 0xbabac1, roughness: 0.8 }),
  joint: () => new THREE.MeshStandardMaterial({ name: 'joint', color: 0x9a9aa1, roughness: 0.8 }),
  bar: () => new THREE.MeshStandardMaterial({ name: 'bar', color: 0x5c5c62, roughness: 0.35 }),
  plate: () => new THREE.MeshStandardMaterial({ name: 'plate', color: 0x48484e, roughness: 0.85 }),
};


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

/** A part: a mesh of `geometry` in `material`, named later by its kit. */
function part(geometry, material, { solid = false, held = false } = {}) {
  const mesh = new THREE.Mesh(geometry, material);
  mesh.userData = { solid: solid || held, held };
  return mesh;
}

/** A unit-long cylinder along y, for a rod or cable laid between two points by `span`. */
const rodGeometry = (radius, segments = 16) => new THREE.CylinderGeometry(radius, radius, 1, segments);

/** Lays a unit rod from A to B. */
function span(mesh, A, B) {
  const d = B.clone().sub(A);
  const length = Math.max(d.length(), 1e-4);
  mesh.position.copy(A).addScaledVector(d, 0.5);
  mesh.quaternion.setFromUnitVectors(UP, d.divideScalar(length));
  mesh.scale.set(1, length, 1);
}

/** A box `w` × `h` × `d` at `at`, tilted `tilt` degrees about x. */
function box(w, h, d, material, at, tilt = 0, opts = { solid: true }) {
  const mesh = part(new THREE.BoxGeometry(w, h, d), material, opts);
  mesh.position.copy(at);
  mesh.rotation.set(rad(tilt), 0, 0);
  return mesh;
}

/** A disc along x: a plate or a pulley wheel, `thickness` wide. */
function disc(radius, thickness, material, opts) {
  const g = new THREE.CylinderGeometry(radius, radius, thickness, 48);
  g.rotateZ(Math.PI / 2);
  return part(g, material, opts);
}

/** A cylinder along x, from `from` to `to`. */
function along(radius, from, to, material, segments, opts) {
  const g = new THREE.CylinderGeometry(radius, radius, to - from, segments);
  g.rotateZ(Math.PI / 2);
  g.translate((from + to) / 2, 0, 0);
  return part(g, material, opts);
}

function group(name, parts) {
  const g = new THREE.Group();
  g.name = name;
  parts.forEach((m, i) => { m.name = `${name}_${i}`; g.add(m); });
  return g;
}

/** A 20 kg barbell with a 20 and a 10 a side, along x, on the hands' bar. */
function barbell() {
  const bar = MATERIALS.bar();
  const plate = MATERIALS.plate();
  const parts = [along(0.014, -0.7, 0.7, bar, 24, { held: true })];
  for (const s of [-1, 1]) {
    const at = (a, b) => [Math.min(s * a, s * b), Math.max(s * a, s * b)];
    parts.push(along(0.025, ...at(0.7, 1.08), bar, 24, { solid: true }));
    parts.push(along(0.038, ...at(0.69, 0.72), bar, 32, { solid: true }));
    parts.push(along(0.225, ...at(0.724, 0.776), plate, 48, { solid: true }));
    parts.push(along(0.17, ...at(0.786, 0.824), plate, 48, { solid: true }));
  }
  const g = group('barbell', parts);
  return { group: g, place: (P) => g.position.copy(P.bar) };
}

/** A pair of hex dumbbells, one in each hand, the handle across the palm. */
function dumbbells() {
  const bar = MATERIALS.bar();
  const plate = MATERIALS.plate();
  const bells = [-1, 1].map((s) => {
    const parts = [along(0.016, -0.075, 0.075, bar, 16, { held: true })];
    for (const e of [-1, 1]) parts.push(along(0.06, ...(e < 0 ? [-0.135, -0.075] : [0.075, 0.135]), plate, 6, { solid: true }));
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
 * A bench along z, `length` long and centred on `z`, the top of its pad `height` above the floor:
 * flat, or with its back pad raised `angle` degrees about a hinge at `hinge` (z; by default 42 cm
 * behind the seat's front), an adjustable bench. With `adjustable`, the back pad is a part of its
 * own, `bench_back`, standing at `angle` about the hinge, which the phone turns to the exact angle.
 */
function bench({ angle = 0, height = 0.44, z = -0.15, length = 1.2, hinge = null, adjustable = false } = {}) {
  const pad = MATERIALS.plate();
  const frame = MATERIALS.bar();
  const t = PAD;
  const y = height - t / 2;
  const parts = [];
  let turning = null;
  if (adjustable) {
    // The seat in front of the hinge, the back pad hanging from the hinge behind it.
    const front = z + length / 2;
    const h = benchHinge({ height, z, length, hinge }).z;
    parts.push(box(0.29, t, front - h, pad, V(0, y, (front + h) / 2)));
    turning = new THREE.Group();
    turning.name = 'bench_back';
    turning.position.set(0, y, h);
    turning.rotation.x = rad(angle);
    const slab = box(0.29, t, BACK, pad, V(0, 0, -BACK / 2));
    slab.name = 'bench_back_pad';
    turning.add(slab);
  } else if (angle === 0 && hinge === null) {
    parts.push(box(0.29, t, length, pad, V(0, y, z)));
  } else {
    // The seat in front of the hinge, the back pad behind it raised about it.
    const front = z + length / 2;
    const h = hinge ?? z + length / 2 - 0.42;
    parts.push(box(0.29, t, front - h, pad, V(0, y, (front + h) / 2)));
    const back = 0.8;
    const a = rad(angle);
    parts.push(box(0.29, t, back, pad, V(0, y + Math.sin(a) * back / 2, h - Math.cos(a) * back / 2), angle));
  }
  const ends = [z - length / 2 + 0.1, z + length / 2 - 0.1];
  for (const e of ends) {
    parts.push(box(0.07, height - t - 0.05, 0.07, frame, V(0, (height - t + 0.05) / 2, e)));
    parts.push(box(0.42, 0.05, 0.07, frame, V(0, 0.025, e)));
  }
  parts.push(box(0.06, 0.05, ends[1] - ends[0], frame, V(0, 0.07, (ends[0] + ends[1]) / 2)));
  const g = group('bench', parts);
  if (turning) g.add(turning);
  return { group: g, place() {} };
}

/**
 * A back pad `w` wide and `length` long, laid along the body's trunk at the top of the rep: its
 * face `behind` metres behind the line from the hips up the spine, its middle `up` metres up it.
 */
function backPad(w, length, material, { up, behind }) {
  const t = 0.08;
  const mesh = part(new THREE.BoxGeometry(w, length, t), material, { solid: true });
  return {
    mesh,
    fit(P0) {
      const f = X.clone().cross(P0.u).normalize();
      mesh.position.copy(P0.hipC).addScaledVector(P0.u, up).addScaledVector(f, -(behind + t / 2));
      mesh.quaternion.setFromRotationMatrix(new THREE.Matrix4().makeBasis(X, P0.u, f));
    },
  };
}

/** A weight stack on two guide rods, its top `lift` metres up its rods. */
function stack(at) {
  const plate = MATERIALS.plate();
  const bar = MATERIALS.bar();
  const base = box(0.26, 0.42, 0.12, plate, V(at.x, 0.27, at.z));
  const top = box(0.26, 0.1, 0.12, plate, V(at.x, 0.54, at.z));
  const rods = [-0.1, 0.1].map((dx) => {
    const r = part(rodGeometry(0.008), bar);
    span(r, V(at.x + dx, 0.05, at.z), V(at.x + dx, 1.45, at.z));
    return r;
  });
  return { parts: [base, top, ...rods], lift: (l) => top.position.setY(0.54 + l) };
}

/** A rod from A to B. */
function rod(radius, A, B, material) {
  const r = part(rodGeometry(radius), material);
  span(r, A, B);
  return r;
}

/**
 * A cable's attachments, each built along x round its middle and held there, and how far above
 * that middle the cable hooks on.
 */
const ATTACHMENTS = {
  straight_bar: (bar) => ({ parts: [along(0.014, -0.25, 0.25, bar, 16, { held: true })], hook: 0 }),
  // A long bar whose ends bend down and out.
  lat_bar: (bar) => ({
    parts: [along(0.014, -0.48, 0.48, bar, 16, { held: true }), ...[-1, 1].map((s) => rod(0.014, V(s * 0.48, 0, 0), V(s * 0.6, -0.07, 0), bar))],
    hook: 0,
  }),
  // A short handle, its sides meeting above it where the cable hooks on.
  v_handle: (bar) => ({
    parts: [along(0.014, -0.07, 0.07, bar, 16, { held: true }), ...[-1, 1].map((s) => rod(0.012, V(s * 0.07, 0, 0), V(0, 0.12, 0), bar))],
    hook: 0.12,
  }),
};

/**
 * A cable column: a tower `column` metres in front (z), a pulley at `pulley` [y, z], the cable down
 * to the attachment in the hands, and back over the tower to its weight stack, which rises as the
 * cable is drawn out. `attachment` is `lat_bar`, `straight_bar` or `v_handle`; `seat` [y, z] adds
 * a seat pad and `thighPad` [y, z] the pad over the knees, for a pulldown.
 */
function cableTower({ column = 0.9, pulley = [2.2, 0], attachment = 'straight_bar', seat = null, thighPad = null } = {}) {
  const bar = MATERIALS.bar();
  const pad = MATERIALS.plate();
  const wheelAt = V(0, pulley[0], pulley[1]);
  const top = Math.max(pulley[0], 1.5) + 0.12;
  const parts = [
    box(0.14, top, 0.14, bar, V(0, top / 2, column)),
    box(0.5, 0.05, 0.5, bar, V(0, 0.025, column - 0.04)),
    box(0.1, 0.1, Math.abs(column - pulley[1]) + 0.1, bar, V(0, top - 0.05, (column + pulley[1]) / 2)),
  ];
  const wheel = disc(0.045, 0.025, bar);
  wheel.position.copy(wheelAt).add(V(0, 0.05, 0));
  const back = disc(0.045, 0.025, bar);
  back.position.set(0, top - 0.05, column - 0.12);
  const cable = part(rodGeometry(0.004, 8), bar);
  const drop = part(rodGeometry(0.004, 8), bar);
  const weights = stack(V(0, 0, column - 0.12));
  if (seat) {
    parts.push(box(0.36, 0.08, 0.4, pad, V(0, seat[0] - 0.04, seat[1])));
    parts.push(box(0.07, seat[0] - 0.08, 0.07, bar, V(0, (seat[0] - 0.08) / 2, seat[1])));
    parts.push(box(0.12, 0.05, column - seat[1] + 0.3, bar, V(0, 0.025, (column + seat[1]) / 2 - 0.1)));
  }
  if (thighPad) {
    parts.push(box(0.46, 0.1, 0.12, pad, V(0, thighPad[0], thighPad[1])));
    parts.push(box(0.06, 0.06, column - thighPad[1], bar, V(0, thighPad[0], (column + thighPad[1]) / 2)));
  }
  if (!ATTACHMENTS[attachment]) throw new Error(`No cable attachment "${attachment}"`);
  const { parts: grip, hook: above } = ATTACHMENTS[attachment](bar);
  const handle = new THREE.Group();
  grip.forEach((m) => handle.add(m));
  const all = [...parts, wheel, back, cable, drop, ...weights.parts];
  const g = group('cable', all);
  handle.name = 'cable_handle';
  grip.forEach((m, i) => { m.name = `cable_handle_${i}`; });
  g.add(handle);
  const hook = (P) => P.bar.clone().add(V(0, above, 0));
  return {
    group: g,
    place(P, P0) {
      handle.position.copy(P.bar);
      const at = hook(P);
      span(cable, at, wheelAt);
      const lift = at.distanceTo(wheelAt) - hook(P0).distanceTo(wheelAt);
      weights.lift(lift);
      span(drop, V(0, top - 0.1, column - 0.12), V(0, 0.6 + lift, column - 0.12));
    },
  };
}

/**
 * A 45° leg press: the seat and back pad (its face `pad` metres behind the spine), the rails, and
 * the sled with its foot plate, which rides the rails with the feet. `P.plate` is the foot plate's
 * centre, `P.sled` the sled's way up the rails and `P.plateUp` the way up the plate's face.
 */
function legPress({ seat = [0.5, 0.04], pad: behind = 0.2 } = {}) {
  const pad = MATERIALS.plate();
  const bar = MATERIALS.bar();
  const backRest = backPad(0.42, 0.72, pad, { up: 0.33, behind });
  const parts = [
    box(0.42, 0.09, 0.38, pad, V(0, seat[0] - 0.03, seat[1]), -18),
    backRest.mesh,
    box(0.12, 0.05, 2.3, bar, V(0, 0.025, 0.55)),
    box(0.1, seat[0] - 0.1, 0.1, bar, V(0, (seat[0] - 0.1) / 2, seat[1] - 0.05)),
  ];
  const rails = [0, 1].map(() => part(rodGeometry(0.03, 12), bar));
  const legs = [0, 1].map(() => part(rodGeometry(0.03, 12), bar));
  const plate = box(0.62, 0.04, 0.52, bar, V(0, 0, 0), 0, { solid: true });
  const sled = box(0.5, 0.08, 0.7, bar, V(0, 0, 0), 0, { solid: false });
  const horns = [0, 1].map(() => part(rodGeometry(0.02), bar));
  const weights = [0, 1].map(() => disc(0.17, 0.04, pad));
  const handles = [0, 1].map(() => part(rodGeometry(0.016), bar, { held: true }));
  const posts = [0, 1].map(() => part(rodGeometry(0.016), bar));
  const g = group('leg_press', [...parts, ...rails, ...legs, plate, sled, ...horns, ...weights, ...handles, ...posts]);
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
        span(rails[i], lo, hi);
        span(legs[i], hi, hi.clone().setY(0.05));
        const horn = P.plate.clone().addScaledVector(along, 0.3).addScaledVector(up, -0.22);
        span(horns[i], horn.clone().setX(s * 0.25), horn.clone().setX(s * 0.5));
        weights[i].position.copy(horn).setX(s * 0.36);
        const h = P.hand(s);
        span(handles[i], h.clone().setZ(h.z - 0.09), h.clone().setZ(h.z + 0.09));
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
 * behind, and two levers hung from a pivot above, swinging the handles through their arc.
 * `P.pivot`, `P.handle(s)` and `P.swing` (the levers' angle, radians from hanging straight down)
 * place them.
 */
function chestPress({ seat = [0.47, 0.02], pad: behind = 0.2 } = {}) {
  const pad = MATERIALS.plate();
  const bar = MATERIALS.bar();
  const backRest = backPad(0.36, 0.64, pad, { up: 0.36, behind });
  const parts = [
    box(0.36, 0.08, 0.4, pad, V(0, seat[0] - 0.04, seat[1])),
    box(0.07, seat[0] - 0.08, 0.07, bar, V(0, (seat[0] - 0.08) / 2, seat[1])),
    box(0.12, 0.05, 1.1, bar, V(0, 0.025, -0.05)),
    backRest.mesh,
  ];
  const column = box(0.12, 1, 0.12, bar, V(0, 0, 0));
  const boom = part(new THREE.BoxGeometry(0.08, 1, 0.08), bar, { solid: true });
  const axle = part(rodGeometry(0.022), bar);
  // Each lever hangs outside the body and ends in a horizontal handle reaching in across the
  // palm, held like a bar.
  const levers = [0, 1].map(() => ({ arm: part(rodGeometry(0.02), bar), grip: part(rodGeometry(0.017), bar, { held: true }) }));
  const weights = stack(V(0, 0, -0.71));
  const g = group('chest_press', [
    ...parts, column, boom, axle, ...levers.flatMap((l) => [l.arm, l.grip]), ...weights.parts,
  ]);
  const rear = -0.55;
  return {
    group: g,
    place(P, P0) {
      backRest.fit(P0);
      const top = P.pivot.y + 0.05;
      column.position.set(0, top / 2, rear);
      column.scale.set(1, top, 1);
      span(boom, V(0, top, rear), V(0, top, P.pivot.z));
      span(axle, P.pivot.clone().setX(-0.52), P.pivot.clone().setX(0.52));
      levers.forEach((l, i) => {
        const s = i ? 1 : -1;
        const h = P.handle(s);
        const out = 0.5;
        span(l.arm, P.pivot.clone().setX(s * out), h.clone().setX(s * out));
        span(l.grip, h.clone().setX(s * out), h.clone().setX(s * (Math.abs(h.x) - 0.1)));
      });
      weights.lift(0.5 * (P0.swing - P.swing));
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
    return KITS[name](name === 'bench' && entry.bench ? { ...options, adjustable: true, angle: entry.bench.angle } : options);
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
