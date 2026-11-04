// A pose judged against its movement spec (README, Reviewing a movement):
//
//   node movement.mjs <id> [--at <commit>]     each line of specs/<id>.json, pass or fail, with what it measured
//
// A spec's lines say what the lift should do, each as one kind of line. All but `look` are exact, so
// they are worked out here, on every frame of every clip the exercise has; `look` lines wait for
// the reviewer's eyes (docs/agents/movement-reviewer.md). `--at` judges the clips of an old commit.
import { readFile, readdir } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseArgs } from 'node:util';
import { clipsAt, numbersOf } from './numbers.mjs';
import { filesOf } from './review.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));
/** Where each exercise's movement spec is, `<id>.json`. */
export const SPECS = path.join(HERE, 'specs');

const isPoint = (name) => name.endsWith('.at');
const shown = (name, value) => (isPoint(name) ? `${(value * 100).toFixed(1)} cm` : `${value.toFixed(1)}°`);
const gap = (a, b) => Math.hypot(...a.map((v, i) => v - b[i]));
const outside = (v, [min, max]) => Math.max(min - v, v - max);

/** The frame where `values` is largest, and its value. */
function furthest(values) {
  const frame = values.reduce((best, v, i) => (v > values[best] ? i : best), 0);
  return { frame, value: values[frame] };
}

/** A value and where, against what it may reach. */
const worstOf = (name, values, limit) => {
  const { frame, value } = furthest(values);
  return { past: value - limit, measured: `${shown(name, value)} at frame ${frame}` };
};

/** Each point's distance off the straight line from the first to the last. */
function offLine(points) {
  const [from, to] = [points[0], points.at(-1)];
  const run = to.map((v, i) => v - from[i]);
  const length = Math.hypot(...run) || 1;
  return points.map((p) => {
    const d = p.map((v, i) => v - from[i]);
    const along = d.reduce((sum, v, i) => sum + (v * run[i]) / length, 0);
    return gap(d, run.map((v) => (v / length) * along));
  });
}

/** What each kind of line works out from `values`, its number on every frame. */
const KINDS = {
  moves(line, name, values) {
    const [top, bottom] = [values[0], values.at(-1)];
    return { past: Math.max(outside(top, line.top), outside(bottom, line.bottom)), measured: `${shown(name, top)} at the top, ${shown(name, bottom)} at the bottom` };
  },
  still(line, name, values) {
    const moved = values.map((v) => (isPoint(name) ? gap(v, values[0]) : Math.abs(v - values[0])));
    return worstOf(name, moved, line.within);
  },
  never(line, name, values) {
    const sizes = line.perFrame ? values.map((v, i) => (i ? Math.abs(v - values[i - 1]) : 0)) : values.map(Math.abs);
    return worstOf(name, sizes, line.past);
  },
  reaches(line, name, values) {
    const { frame, value } = furthest(values.map(Math.abs));
    return { past: line.least - value, measured: `${shown(name, value)} at frame ${frame}` };
  },
  even(line, name, values) {
    const n = values.length - 1;
    return worstOf(name, values.map((v, i) => Math.abs(v - (values[0] + ((values[n] - values[0]) * i) / n))), line.within);
  },
  path(line, name, values) {
    return worstOf(name, offLine(values), line.within);
  },
};

const kindOf = (line) => {
  const kind = [...Object.keys(KINDS), 'look'].find((k) => k in line);
  if (!kind) throw new Error(`"${line.says}" has no kind: moves, still, never, reaches, even, path or look`);
  return kind;
};

/** The numbers a line names: `*` stands for each side. */
const namesOf = (name) => (name.includes('*') ? ['l', 'r'].map((k) => name.replace('*', k)) : [name]);

/** One line of a spec on `frames`, its worst side when it names both. */
function judgeLine(line, frames) {
  const kind = kindOf(line);
  if (kind === 'look') return { says: line.says, kind, ok: null, measured: `${line.at.join(', ')}, ${line.view}` };
  const found = namesOf(line[kind]).map((name) => {
    if (frames[0][name] === undefined) return { name, past: Infinity, measured: `no ${name} in this clip` };
    return { name, ...KINDS[kind](line, name, frames.map((f) => f[name])) };
  });
  const worst = found.reduce((a, b) => (b.past > a.past ? b : a));
  const measured = found.length > 1 ? `${worst.name}: ${worst.measured}` : worst.measured;
  return { says: line.says, kind, ok: !(worst.past > 0), past: worst.past, measured, open: line.open };
}

/** What each line of `spec` finds on `frames`: `ok` true or false, or null for a line that waits for eyes. */
export const judge = (spec, frames) => spec.lines.map((line) => judgeLine(line, frames));

/** The exercises with a movement spec. */
export async function specified(dir = SPECS) {
  return (await readdir(dir)).filter((f) => f.endsWith('.json')).map((f) => path.basename(f, '.json')).sort();
}

export const readSpec = async (id, dir = SPECS) => JSON.parse(await readFile(path.join(dir, `${id}.json`), 'utf8'));

/** Each line of exercise `id`'s spec, on every clip it has in `clips`: the worst clip's verdict, and which it was. */
export async function reviewMovement(id, clips, spec) {
  const { all } = await filesOf(clips, id);
  const verdicts = [];
  for (const file of all) {
    const { frames } = await numbersOf(file);
    judge(spec, frames).forEach((v, i) => {
      const clip = path.basename(file, '.glb');
      if (!verdicts[i] || v.past > verdicts[i].past) verdicts[i] = { ...v, clip };
    });
  }
  return verdicts;
}

const MARK = { true: 'pass', false: 'FAIL', null: 'look' };

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const { values: { at }, positionals: [id] } = parseArgs({ allowPositionals: true, options: { at: { type: 'string' } } });
  if (!id) throw new Error('usage: node movement.mjs <id> [--at <commit>]');
  const verdicts = await reviewMovement(id, await clipsAt(at), await readSpec(id));
  for (const v of verdicts) console.log(`${MARK[v.ok]}  ${v.says}\n      ${v.measured}${v.clip === id ? '' : ` (${v.clip})`}${v.open ? `\n      open: ${v.open}` : ''}`);
  process.exit(verdicts.some((v) => v.ok === false) ? 1 : 0);
}
