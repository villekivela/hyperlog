// Resolves a pose entry: `like: <id>` takes another entry and changes only what this one gives.
// Objects merge key by key; anything else, a point or a list included, is replaced whole.
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { POSES } from './poses.mjs';

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

/** The entry for `id`, resolved, with the trace it names read in: `trace: <name>` reads `traces/<name>.json`. */
export function prepare(id, poses = POSES) {
  const entry = resolve(id, poses);
  if (typeof entry.trace === 'string') {
    entry.trace = JSON.parse(readFileSync(path.join(TRACES, `${entry.trace}.json`), 'utf8'));
  }
  return entry;
}
