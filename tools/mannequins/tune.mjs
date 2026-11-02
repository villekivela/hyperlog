// What the review page's Tune panel does with a pose entry, with nothing of the page or of node in
// it (the page bundles this file, bundle.mjs). A slider is one number of the entry, named by its
// path (`grip.width`, `top.bar.0`); a tuned value is saved at the path of the number it changes,
// a point whole (`top.bar`), since an entry's lists are replaced whole, not merged.

/** What a motion does that an entry may leave unsaid: a lever with a default, offered all the same. */
export const OPTIONAL = {
  pulldown: { 'grip.elbows': 0.8, 'grip.elbowZ': -0.3, 'top.shrug': 0, 'bottom.shrug': 0 },
  bench_press: { 'body.head': 0, 'body.arch': [0, 0, 0] },
  dumbbell_press: { 'body.lean': 0 },
};

const SKIP = new Set(['camera', 'shadow', 'trace', 'tempo', 'like']);
const DEGREES = new Set(['trunk', 'thigh', 'shin', 'upperArm', 'forearm', 'head', 'toeOut', 'knee', 'arch', 'shrug', 'rotate']);
const RATIOS = new Set(['elbows', 'elbowZ', 'kneesOut', 'press', 'lever']);

const isNumber = (v) => typeof v === 'number';

/** The path's own steps: `top.bar.0` is `top`, `bar`, `0`. */
const steps = (path) => path.split('.');

/** The value at `path` in `entry`, or undefined. */
export function valueAt(entry, path) {
  return steps(path).reduce((v, k) => (v === undefined || v === null ? undefined : v[k]), entry);
}

/** What the entry has at `path`, or what its motion does there when it says nothing. */
export const baseAt = (entry, path) => valueAt(entry, path) ?? OPTIONAL[entry.motion]?.[path];

/** Puts `value` at `path` in `entry`, making the objects on the way. */
function setAt(entry, path, value) {
  const ks = steps(path);
  let at = entry;
  for (const k of ks.slice(0, -1)) at = at[k] ??= {};
  at[ks.at(-1)] = value;
}

/** A copy of `entry` with each tuned value (`{ path: value }`) put in. */
export function applyTuned(entry, tuned) {
  const copy = JSON.parse(JSON.stringify(entry));
  for (const [path, value] of Object.entries(tuned ?? {})) setAt(copy, path, JSON.parse(JSON.stringify(value)));
  return copy;
}

function leaves(value, path, out) {
  if (isNumber(value)) out.push([path, value]);
  else if (Array.isArray(value)) value.forEach((v, i) => leaves(v, `${path}.${i}`, out));
  else if (value && typeof value === 'object') for (const [k, v] of Object.entries(value)) leaves(v, path ? `${path}.${k}` : k, out);
  return out;
}

/** The range, step and unit that suit the number at `path`, by what it is called. */
function rangeOf(path, value) {
  const key = steps(path).filter((k) => !/^\d+$/.test(k)).at(-1);
  if (key === 'rotate') return { min: -30, max: 210, step: 1, unit: '°' };
  if (DEGREES.has(key)) return { min: Math.min(-40, value - 20), max: Math.max(40, value + 20), step: 1, unit: '°' };
  if (RATIOS.has(key)) return { min: -1, max: 1.5, step: 0.05, unit: '' };
  return { min: Math.round((value - 0.3) * 100) / 100, max: Math.round((value + 0.3) * 100) / 100, step: 0.01, unit: ' m' };
}

/** The headings the sliders are grouped under, in order; the less used start collapsed. */
export const HEADINGS = [
  { name: 'Grip', open: true },
  { name: 'Bar', open: true },
  { name: 'Torso', open: true },
  { name: 'Feet', open: true },
  { name: 'Arms', open: false },
  { name: 'Legs', open: false },
  { name: 'Equipment', open: false },
  { name: 'Other', open: false },
];

/** The heading the number at `path` goes under. */
export function headingOf(path) {
  const ks = steps(path).filter((k) => !/^\d+$/.test(k));
  const [section, key] = ks;
  const name = section === 'kit' ? 'Equipment'
    : ['width', 'lockout', 'rotate'].includes(key) && section === 'grip' ? 'Grip'
    : section === 'grip' ? 'Arms'
    : section === 'feet' ? 'Feet'
    : section === 'body' || ['trunk', 'shrug', 'head', 'arch', 'lean'].includes(key) ? 'Torso'
    : ['bar', 'over', 'chest', 'hand', 'handle', 'press'].includes(key) || section === 'bar' ? 'Bar'
    : ['upperArm', 'forearm'].includes(key) ? 'Arms'
    : ['shin', 'thigh', 'knee'].includes(key) ? 'Legs'
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
  // Every pose with hands can turn them: overhand 0°, neutral 90°, underhand 180°.
  if (entry.grip && !have.has('grip.rotate')) found.push(['grip.rotate', 0]);
  return found
    // `press` is the rep's own 0 and 1 (top and bottom), not a setting.
    .filter(([path]) => !/^(top|bottom)\.press$/.test(path))
    .filter(([path]) => !(/^(top|bottom)\./.test(path) && traced.has(steps(path)[1])))
    .map(([path, value]) => ({ path, value, ...rangeOf(path, value) }));
}
