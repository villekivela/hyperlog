// Resolves a pose entry: `like: <id>` takes another entry and changes only what this one gives.
// Objects merge key by key; anything else, a point or a list included, is replaced whole.
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { POSES } from './poses.mjs';

const LIBRARY = path.join(path.dirname(fileURLToPath(import.meta.url)), '..', '..', 'app', 'src', 'main', 'assets', 'exercises.json');

/** The bench angles an adjustable-bench pose is baked at, in degrees: 0 to 75 in 15° steps. */
export const BENCH_ANGLES = [0, 15, 30, 45, 60, 75];

let adjustable;

/** The angles `id` is baked at: all of them where the library gives the exercise an adjustable bench, otherwise null. */
export function benchAngles(id) {
  adjustable ??= new Set(JSON.parse(readFileSync(LIBRARY, 'utf8')).exercises.filter((e) => e.adjustableBench).map((e) => e.id));
  return adjustable.has(id) ? BENCH_ANGLES : null;
}

/** Where `trace` writes each pose's traced angles, as `<name>.json`. */
export const TRACES = path.join(path.dirname(fileURLToPath(import.meta.url)), 'traces');

const isObject = (v) => v !== null && typeof v === 'object' && !Array.isArray(v);

function merge(base, over) {
  const out = { ...base };
  for (const [k, v] of Object.entries(over)) out[k] = isObject(v) && isObject(base[k]) ? merge(base[k], v) : v;
  return out;
}

/** The entry for `id`, with its `like:` chain merged in. */
export function resolve(id, poses = POSES) {
  const seen = [];
  const chain = [];
  for (let at = id; at !== undefined; at = poses[at].like) {
    if (seen.includes(at)) throw new Error(`${id}: like goes round in a circle (${[...seen, at].join(' → ')})`);
    if (!poses[at]) {
      if (at === id) throw new Error(`No pose entry for ${id}`);
      throw new Error(`${seen.at(-1)}: like "${at}", which has no entry`);
    }
    seen.push(at);
    chain.unshift(poses[at]);
  }
  const { like, ...entry } = chain.reduce(merge, {});
  return entry;
}

/** The stage's framing at `angle`, from the framings given at the ends: each number moves steadily from one end's to the other's. */
function cameraAt(cameras, angle) {
  const at = Object.keys(cameras).map(Number).sort((a, b) => a - b);
  const [lo, hi] = [at[0], at.at(-1)];
  const t = Math.min(1, Math.max(0, (angle - lo) / (hi - lo)));
  const [a, b] = [cameras[lo], cameras[hi]];
  const mix = (x, y) => Math.round((x + (y - x) * t) * 1e4) / 1e4;
  return { target: a.target.map((v, i) => mix(v, b.target[i])), halfHeight: mix(a.halfHeight, b.halfHeight), azimuth: mix(a.azimuth, b.azimuth), elevation: mix(a.elevation, b.elevation) };
}

/**
 * The entry for `id`, resolved, with the trace it names read in: `trace: <name>` reads
 * `traces/<name>.json`. An adjustable-bench pose is prepared at `angle`, its typical one by default.
 */
export function prepare(id, poses = POSES, angle) {
  const entry = resolve(id, poses);
  if (entry.bench) {
    entry.bench = { ...entry.bench, angle: angle ?? entry.bench.typical };
    if (entry.bench.cameras) entry.camera = cameraAt(entry.bench.cameras, entry.bench.angle);
  }
  if (typeof entry.trace === 'string') {
    entry.trace = JSON.parse(readFileSync(path.join(TRACES, `${entry.trace}.json`), 'utf8'));
  }
  return entry;
}
