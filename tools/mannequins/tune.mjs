// What the studio's numbers panel does with a pose entry, with nothing of the page or of node in
// it. A number is one of the entry's, named by its path (`grip.width`, `top.bar.0`); a tuned value
// is saved at the path of the number it changes, a point whole (`top.bar`), since an entry's lists
// are replaced whole, not merged.

/** Which hand a kit serves, which tower or ankle it uses and which foot lifts are choices, not places, so they have no slider. */
const isChoice = (path) => /^kit\.[^.]+\.(hands?(\.\d+)?|tower|ankle)$/.test(path) || path === 'feet.lift';

/** Numbers not yet saved, without any choice among them, which an older slider may have left. */
export const withoutChoices = (tuned) => Object.fromEntries(Object.entries(tuned).filter(([path]) => !isChoice(path)));

/** What a motion does that an entry may leave unsaid: a lever with a default, offered all the same. */
export const OPTIONAL = {
  pulldown: { 'grip.elbows': 0.8, 'grip.elbowZ': -0.3, 'top.shrug': 0, 'bottom.shrug': 0 },
  bench_press: { 'body.head': 0, 'body.arch': [0, 0, 0], 'grip.pole': [0.2, -1, 0.55] },
  dumbbell_press: { 'body.lean': 0, 'body.head': 0, 'top.rotate': 0, 'bottom.rotate': 0, 'top.tilt': 0, 'bottom.tilt': 0 },
  arms: { 'grip.rotate': 0, 'grip.pole': [0.2, -1, -0.4], 'feet.y': 0, 'feet.stagger': 0, 'top.armOut': 0, 'bottom.armOut': 0 },
  free_arms: { 'grip.rotate': 0, 'grip.pole': [1, -0.3, -0.5], 'feet.stagger': 0 },
  row: { 'grip.pole': [0.35, 0.2, -1], 'feet.y': 0 },
  knee_lever: { 'grip.pole': [1, -0.2, -0.5], 'body.head': 0 },
  calf_raise: { 'grip.pole': [1, -0.3, -0.4] },
  hack_squat: { 'grip.pole': [0.6, -1, 0.3], 'feet.kneesOut': 0.35 },
  lever_handles: { 'grip.rotate': 0, 'grip.pole': [0.7, -1, -0.5] },
  chest_pad: { 'grip.pole': [0.5, 0.4, -1], 'body.look': -0.3, 'feet.kneesOut': 0.2 },
  hip_thrust: { 'grip.pole': [0.5, -1, -0.5], 'body.look': 0.3, 'feet.kneesOut': 0.15 },
  bridge: { 'body.head': 0, 'grip.pole': [0.6, -1, 0.2], 'feet.kneesOut': 0.2 },
  hinge: { 'top.shrug': 0, 'bottom.shrug': 0, 'feet.y': 0 },
  glute_ham_raise: { 'body.look': 0.25 },
  hang: { 'body.look': 0.1, 'feet.kneesOut': 0 },
  support: { 'body.look': 0.1, 'feet.kneesOut': 0 },
  // A back squat's elbows, [out, up, forward]: down and back by default, as the motion has them.
  squat: { 'feet.y': 0, 'grip.spot': [0.02, 0.06, 0.07], 'grip.pole': [0.3, -1, -0.8] },
};

const SKIP = new Set(['camera', 'shadow', 'trace', 'tempo', 'like', 'bench']);
const DEGREES = new Set(['wrist', 'trunk', 'thigh', 'shin', 'upperArm', 'forearm', 'armOut', 'leg', 'turn', 'head', 'toeOut', 'knee', 'arch', 'shrug', 'tilt', 'lean', 'ankle', 'angle', 'lever', 'levers', 'heel', 'point']);
const RATIOS = new Set(['elbows', 'elbowZ', 'kneesOut', 'press', 'pole']);

const isNumber = (v) => typeof v === 'number';

/** The path's own steps: `top.bar.0` is `top`, `bar`, `0`. */
const steps = (path) => path.split('.');

/** The value at `path` in `entry`, or undefined. */
export function valueAt(entry, path) {
  return steps(path).reduce((v, k) => (v === undefined || v === null ? undefined : v[k]), entry);
}

/** A bone's offset at an end (`top.offsets.Head`), [x, y, z] in degrees, and a bone's where the entry turns it not at all. */
const OFFSET = /^(top|bottom)\.offsets\.\w+$/;
const NO_OFFSET = [0, 0, 0];

/** A hand's wrist, [flexion, deviation], where the entry sets none: straight. */
const STRAIGHT_WRIST = [0, 0];

/**
 * What the entry has at `path`, or what its motion does there when it says nothing. A wrist the
 * entry leaves unset starts straight.
 */
export const baseAt = (entry, path) => valueAt(entry, path) ?? OPTIONAL[entry.motion]?.[path]
  ?? (entry.grip && /^(top|bottom)\.wrist$/.test(path) ? STRAIGHT_WRIST : undefined)
  ?? (OFFSET.test(path) ? NO_OFFSET : undefined)
  ?? (/\.\d+$/.test(path) ? baseAt(entry, path.replace(/\.\d+$/, ''))?.[Number(path.split('.').at(-1))] : undefined);

const same = (a, b) => JSON.stringify(a) === JSON.stringify(b);

/** `value` as the number at `path` can stand: on a whole step for one that has only those. */
function settled(path, value) {
  const { whole, min, max } = rangeOf(path, value);
  return whole ? Math.min(max, Math.max(min, Math.round(value))) : value;
}

/**
 * `tuned` with a slider's `value` put at its `path`: one number of a point goes into the point,
 * which is saved whole, and a value that is what the entry already has comes out again.
 */
export function withTuned(entry, tuned, path, value) {
  const m = path.match(/^(.*)\.(\d+)$/);
  if (m) {
    const [, point, i] = m;
    const whole = JSON.parse(JSON.stringify(tuned[point] ?? baseAt(entry, point)));
    whole[Number(i)] = settled(path, value);
    return withTuned(entry, tuned, point, whole);
  }
  if (typeof value === 'number') value = settled(path, value);
  const next = { ...tuned };
  if (same(value, baseAt(entry, path))) delete next[path]; else next[path] = value;
  return next;
}

/** Puts `value` at `path` in `entry`, making the objects on the way. */
function setAt(entry, path, value) {
  const ks = steps(path);
  let at = entry;
  for (const k of ks.slice(0, -1)) at = at[k] ??= {};
  at[ks.at(-1)] = value;
}

/**
 * A copy of `entry` with each tuned value (`{ path: value }`) put in. `@angles` is an adjustable
 * bench's per-angle settings, `{ path: { angle: value } }`, which replace the entry's own.
 */
export function applyTuned(entry, tuned) {
  const copy = JSON.parse(JSON.stringify(entry));
  for (const [path, value] of Object.entries(tuned ?? {})) {
    setAt(copy, path === '@angles' ? 'bench.perAngle' : path, JSON.parse(JSON.stringify(value)));
  }
  return copy;
}

/** A per-angle setting's value at `angle`: interpolated between the nearest tuned angles, the nearest one's beyond them. */
export function perAngleValue(tuned, angle) {
  const at = Object.keys(tuned).map(Number).sort((a, b) => a - b);
  if (!at.length) throw new Error('A per-angle setting needs a value at some angle');
  if (angle <= at[0]) return tuned[at[0]];
  if (angle >= at.at(-1)) return tuned[at.at(-1)];
  const hi = at.find((a) => a >= angle);
  const lo = at[at.indexOf(hi) - 1];
  const t = (angle - lo) / (hi - lo);
  return tuned[lo] + (tuned[hi] - tuned[lo]) * t;
}

/** A copy of an adjustable-bench entry posed at `angle`: each per-angle setting (`bench.perAngle`) at its value there. */
export function atAngle(entry, angle) {
  const copy = JSON.parse(JSON.stringify(entry));
  copy.bench = { ...copy.bench, angle };
  for (const [path, tuned] of Object.entries(copy.bench.perAngle ?? {})) {
    if (Object.keys(tuned).length) setAt(copy, path, perAngleValue(tuned, angle));
  }
  return copy;
}

/** The angles an entry has a per-angle setting tuned at, ascending. */
export function tunedAngles(entry) {
  const angles = new Set();
  for (const tuned of Object.values(entry.bench?.perAngle ?? {})) for (const a of Object.keys(tuned)) angles.add(Number(a));
  return [...angles].sort((a, b) => a - b);
}

function leaves(value, path, out) {
  if (isNumber(value)) out.push([path, value]);
  else if (Array.isArray(value)) value.forEach((v, i) => leaves(v, `${path}.${i}`, out));
  else if (value && typeof value === 'object') for (const [k, v] of Object.entries(value)) leaves(v, path ? `${path}.${k}` : k, out);
  return out;
}

/** The range, step and unit that suit the number at `path`, by what it is called: a step is a degree, a millimetre or a hundredth of a ratio. */
export function rangeOf(path, value) {
  const key = steps(path).filter((k) => !/^\d+$/.test(k)).at(-1);
  // A bone turns all the way round, either way.
  if (steps(path)[1] === 'offsets') return { min: -180, max: 180, step: 1, unit: '°' };
  if (key === 'rotate') return { min: -30, max: 210, step: 1, unit: '°' };
  // A wrist bends well past the hand's own turn to the bar.
  if (key === 'wrist') return { min: -60, max: 60, step: 1, unit: '°' };
  // A thumb's root swings from the palm to square off it, and no further (`holdOf`).
  if (key === 'swing') return { min: 0, max: 90, step: 1, unit: '°' };
  // A stirrup the forearm goes through lies from straight behind the palm to along the forearm.
  if (key === 'through') return { min: 0, max: 90, step: 1, unit: '°' };
  // The wrists' share of the forearms' lean, from none to all of it (`grip.level`).
  if (key === 'level') return { min: 0, max: 1, step: 0.05, unit: '' };
  if (DEGREES.has(key)) return { min: Math.min(-40, value - 20), max: Math.max(40, value + 20), step: 1, unit: '°' };
  if (RATIOS.has(key)) return { min: -1, max: 1.5, step: 0.01, unit: '' };
  // A carriage stands at a hole of the 16 on its rail, a whole number however it is dragged (`settled`).
  if (path.startsWith('kit.') && key === 'position') return { min: 1, max: 16, step: 1, unit: '', whole: true };
  // Equipment stands where the pose needs it, and a resting hand reaches it: a metre either way.
  if (path.startsWith('kit.') || key === 'rest') return { min: Math.round((value - 1) * 100) / 100, max: Math.round((value + 1) * 100) / 100, step: 0.001, unit: ' m' };
  return { min: Math.round((value - 0.3) * 100) / 100, max: Math.round((value + 0.3) * 100) / 100, step: 0.001, unit: ' m' };
}

/** What the wrist's numbers do, [flexion, deviation], and which way each sign bends the hand. */
const WRIST_WORDS = ['Wrist bend: + flexion / − extension', 'Wrist tilt: + radial (thumb side) / − ulnar (little-finger side)'];

const END_WORDS = { top: 'Top', bottom: 'Bottom' };

/** A slider's label: in words where its path doesn't say what it does, else the path. */
export function labelOf(path) {
  const bone = path.match(/^(top|bottom)\.offsets\.(\w+)\.([012])$/);
  if (bone) return `${END_WORDS[bone[1]]} · ${bone[2]} · ${'xyz'[Number(bone[3])]}`;
  const m = path.match(/^(top|bottom)\.wrist\.([01])$/);
  if (!m) return path;
  return `${END_WORDS[m[1]]} · ${WRIST_WORDS[Number(m[2])]}`;
}

/** The headings the sliders are grouped under, in order; the less used start collapsed. */
export const HEADINGS = [
  { name: 'Grip', open: true },
  { name: 'Bar', open: true },
  { name: 'Torso', open: true },
  { name: 'Feet', open: true },
  { name: 'Arms', open: false },
  { name: 'Legs', open: false },
  { name: 'Bones', open: false },
  { name: 'Equipment', open: false },
  { name: 'Other', open: false },
];

/** The heading the number at `path` goes under. */
export function headingOf(path) {
  const ks = steps(path).filter((k) => !/^\d+$/.test(k));
  const [section, key] = ks;
  const name = section === 'kit' ? 'Equipment'
    : key === 'offsets' ? 'Bones'
    : ['width', 'lockout'].includes(key) && section === 'grip' ? 'Grip'
    : ['rotate', 'tilt'].includes(key) ? 'Grip'
    : section === 'grip' ? 'Arms'
    : section === 'feet' ? 'Feet'
    : section === 'body' || ['trunk', 'shrug', 'head', 'arch', 'lean'].includes(key) ? 'Torso'
    : ['bar', 'over', 'chest', 'hand', 'handle', 'press', 'lever', 'levers', 'sled', 'carriage'].includes(key) || section === 'bar' ? 'Bar'
    : ['upperArm', 'forearm', 'armOut'].includes(key) ? 'Arms'
    : ['shin', 'thigh', 'knee', 'leg'].includes(key) ? 'Legs'
    : 'Other';
  return HEADINGS.find((h) => h.name === name);
}

/**
 * The sliders for a resolved entry: each number it has, and the levers its motion offers that it
 * hasn't set. The camera, the shadow and the tempo aren't the pose's, and an angle a trace sets
 * is moved by the trace, not by the entry.
 */
export function tunables(entry) {
  const traced = new Set(Object.keys(entry.trace?.angles ?? {}));
  const own = {};
  for (const [k, v] of Object.entries(entry)) if (!SKIP.has(k)) own[k] = v;
  const found = leaves(own, '', []).map(([path, value]) => [path.replace(/^\./, ''), value]);
  const have = new Set(found.map(([p]) => p));
  for (const [path, value] of Object.entries(OPTIONAL[entry.motion] ?? {})) {
    for (const [p, v] of leaves(value, path, [])) {
      if (!have.has(p) && valueAt(entry, p) === undefined) found.push([p, v]);
    }
  }
  // Every pose with hands bends its wrists, at the top and the bottom: [flexion, deviation].
  if (entry.grip) for (const end of ['top', 'bottom']) {
    if (valueAt(entry, `${end}.wrist`) === undefined) found.push([`${end}.wrist.0`, 0], [`${end}.wrist.1`, 0]);
  }
  // Every pose with hands can turn them, at the top and at the bottom: overhand 0°, neutral 90°,
  // underhand 180°, each from the entry's own `grip.rotate`, which stands for both ends.
  if (entry.grip) for (const end of ['top', 'bottom']) {
    if (valueAt(entry, `${end}.rotate`) === undefined) found.push([`${end}.rotate`, entry.grip.rotate ?? 0]);
  }
  return found
    // `press` is the rep's own 0 and 1 (top and bottom), not a setting.
    .filter(([path]) => !/^(top|bottom)\.press$/.test(path) && path !== 'grip.rotate' && !(entry.motion === 'dumbbell_press' && path === 'grip.tilt'))
    .filter(([path]) => !(/^(top|bottom)\./.test(path) && traced.has(steps(path)[1])))
    .filter(([path]) => !isChoice(path))
    .map(([path, value]) => ({ path, value, ...rangeOf(path, value) }));
}
