// Generic equipment, and the floor shadow under the mannequin. Every kit is a recipe (recipe.mjs)
// over the part library (parts.mjs), one in `recipes/` for each kit a pose entry can name.
//
// A kit is built once per clip and placed on every frame from the pose frame (`place(P, top,
// bottom)`, with the frames at the top and the bottom of the rep for what moves relative to them,
// like a weight stack).
import * as THREE from 'three';
import { RECIPES } from './recipes/index.mjs';
import { MOTION_TEMPOS } from './tempo.mjs';

export { MATERIALS } from './parts.mjs';

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
  row_machine: { kit: 'row_machine', exercises: ['machine_row', 'chest_supported_machine_row'] },
  t_bar_row_machine: { kit: 't_bar_row_machine', exercises: ['chest_supported_t_bar_row', 't_bar_row', 'kelso_shrug'] },
  pulldown_machine: { kit: 'pulldown_machine', exercises: ['machine_pulldown', 'machine_lat_pullover'] },
  shoulder_press: { kit: null, exercises: ['machine_shoulder_press'] },
  lateral_raise_machine: { kit: null, exercises: ['machine_lateral_raise', 'standing_machine_lateral_raise'] },
  preacher_curl_machine: { kit: 'preacher_curl_machine', exercises: ['machine_preacher_curl'] },
  dip_machine: { kit: null, exercises: ['seated_dip_machine'] },
  hip_machine: { kit: null, exercises: ['machine_hip_abduction', 'machine_adduction'] },
  hip_thrust_machine: { kit: null, exercises: ['machine_hip_thrust'] },
  calf_raise_machine: { kit: 'calf_raise_machine', exercises: ['standing_calf_raise', 'seated_calf_raise', 'donkey_calf_raise'] },
  crunch_machine: { kit: null, exercises: ['machine_crunch'] },
  shrug_machine: { kit: null, exercises: ['machine_shrug'] },
};

/**
 * The cable machines that serve exercises by a free handle at an adjustable height, with the
 * exercises each is the machine of. Only those no pose has set up on `cable_tower` or `cable_column`
 * are listed: a posed exercise keeps its kit (moving it would change its clip), and moves when it is
 * posed machine-first.
 */
export const CABLE_MACHINES = {
  cable_crossover: {
    kit: 'cable_crossover',
    exercises: [
      '1_arm_cable_press_around', '1_arm_katana_cable_triceps_extension', '1_leg_cable_glute_kickback', 'behind_the_back_cable_lateral_raise',
      'cable_chest_press', 'cable_crossover', 'cable_crunch', 'cable_face_pull', 'cable_hip_abduction', 'cable_pull_through', 'cable_shrug_in',
      'cable_triceps_kickback', 'cable_woodchopper', 'cable_wrist_curl', 'cable_wrist_extension', 'cable_y_raise', 'high_cable_lateral_raise',
      'low_to_high_cable_press', 'seated_cable_flye',
    ],
  },
};

/**
 * The stations that serve bodyweight exercises, with the exercises each is the station of. Only
 * those no pose has set up are listed; a station is not a machine kind, because the exercises on it
 * are classed `bodyweight` or `weighted_bodyweight`.
 */
export const STATIONS = {
  pullup_dip_station: {
    kit: 'pullup_dip_station',
    exercises: [
      'pullup', 'pullup_wide_grip', 'close_grip_pullup', 'neutral_grip_pullup', '2_grip_pullup', 'chinup', 'chinup_wide_grip', 'close_grip_chinup',
      'dead_hang', 'hanging_leg_raise', 'dip', 'close_grip_dip', 'weighted_pullup', 'weighted_chinup', '2_grip_pullup_weighted', 'weighted_dip',
    ],
  },
};

/**
 * The library's equipment, each with the kits a pose in it may use. A kind with no kit yet is
 * `null`, and is listed as missing in the README; a pose can't be made in it until it has one.
 * Bodyweight needs no kit for most of its exercises.
 */
export const EQUIPMENT = {
  barbell: ['barbell', 'bench', 'platform'],
  dumbbell: ['dumbbells', 'bench'],
  cable: ['cable_tower', 'cable_column', 'cable_crossover', 'bench'],
  machine: Object.values(MACHINES).map((m) => m.kit).filter(Boolean),
  bodyweight: ['pullup_dip_station'],
  ez_bar: null,
  trap_bar: null,
  safety_bar: null,
  smith_machine: ['smith_machine', 'bench', 'platform'],
  plate: null,
  weighted_bodyweight: ['pullup_dip_station'],
};

/** Every kit's recipe, by name. */
export const KITS = RECIPES;

/**
 * The entry the studio shows kit `name` by while no pose uses it: the kit alone, as its recipe
 * stands with no pose (`standsAlone: { camera }` on the recipe, the framing it opens on), or null
 * for a kit whose recipe is set up by its pose. The studio's loop runs on a tempo, so it has the
 * machines' usual one, though nothing moves.
 */
export function shownAlone(name) {
  const stands = KITS[name]?.standsAlone;
  if (!stands) return null;
  const equipment = Object.keys(EQUIPMENT).find((k) => EQUIPMENT[k]?.includes(name));
  return { equipment, kit: { [name]: {} }, alone: true, tempo: MOTION_TEMPOS.lever_handles, camera: stands.camera };
}

/**
 * Builds a pose's kit: each kit it names, with its options, in one group placed together. `kits`
 * is each one built, by name, with its joints, contacts and `scrub`.
 */
export function buildKit(entry) {
  const allowed = EQUIPMENT[entry.equipment];
  if (allowed === undefined) throw new Error(`No equipment "${entry.equipment}" in the library`);
  if (allowed === null) throw new Error(`"${entry.equipment}" has no kit yet`);
  const kits = Object.entries(entry.kit ?? {}).map(([name, options]) => {
    if (!KITS[name]) throw new Error(`No kit "${name}"`);
    if (!allowed.includes(name)) throw new Error(`Kit "${name}" isn't ${entry.equipment} equipment`);
    // An adjustable bench stands at the angle the pose is prepared at, and a platform is as high as
    // the feet stand.
    const kit = KITS[name].make(
      name === 'bench' && entry.bench ? { ...options, adjustable: true, angle: entry.bench.angle }
        : name === 'platform' ? { height: entry.feet?.y, ...options } : options,
    );
    // The bake shares the parts under this node with every clip in the same kit.
    kit.group.userData.kit = name;
    return [name, kit];
  });
  const g = new THREE.Group();
  g.name = 'kit';
  kits.forEach(([, k]) => g.add(k.group));
  return {
    group: g,
    kits: Object.fromEntries(kits),
    place: (P, P0, P1) => kits.forEach(([, k]) => k.place(P, P0, P1)),
    // Each cable, with where on its handle it hooks on (`hook()`, in the world), for the checks.
    cables: kits.flatMap(([, k]) => k.cables),
  };
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
