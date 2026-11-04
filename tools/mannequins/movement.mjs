// A clip judged against its movement spec (README, Reviewing a movement):
//
//   node movement.mjs <id> [--at <commit>]     each line of specs/<id>.json, with what it measured
//
// A spec's lines say what the lift should do, each as one kind of line. All but `look` are exact, so
// they are worked out here, on every frame of every clip the exercise has; `look` lines wait for
// the reviewer's eyes (docs/agents/movement-reviewer.md). `--at` judges the clips of an old commit.
// It exits non-zero on a line that fails, unless the line is marked `open`.
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

/** A value and where, and how far `over` what it may reach. */
const worstOf = (name, values, limit) => {
  const { frame, value } = furthest(values);
  return { over: value - limit, measured: `${shown(name, value)} at frame ${frame}` };
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
    const fit = (a, b) => Math.max(outside(top, a), outside(bottom, b));
    const over = line.ends ? Math.min(fit(...line.ends), fit(line.ends[1], line.ends[0])) : fit(line.top, line.bottom);
    return { over, measured: `${shown(name, top)} at the top, ${shown(name, bottom)} at the bottom` };
  },
  still(line, name, values) {
    const moved = values.map((v) => (isPoint(name) ? gap(v, values[0]) : Math.abs(v - values[0])));
    return worstOf(name, moved, line.within);
  },
  never(line, name, values) {
    const sizes = line.perFrame ? values.map((v, i) => (i ? Math.abs(v - values[i - 1]) : 0)) : values.map(Math.abs);
    return worstOf(name, sizes, line.past);
  },
  travels(line, name, values) {
    const { frame, value } = furthest(values.map((v) => gap(v, values[0])));
    return { over: line.least - value, measured: `${shown(name, value)} at frame ${frame}` };
  },
  reaches(line, name, values) {
    const { frame, value } = furthest(values.map(Math.abs));
    return { over: line.least - value, measured: `${shown(name, value)} at frame ${frame}` };
  },
  even(line, name, values) {
    const n = values.length - 1;
    return worstOf(name, values.map((v, i) => Math.abs(v - (values[0] + ((values[n] - values[0]) * i) / n))), line.within);
  },
  path(line, name, values) {
    return worstOf(name, offLine(values), line.within);
  },
  place(line, name, values, frames) {
    const axis = 'xyz'.indexOf(line.axis);
    const from = line.from.replace('*', /_([lr])\b/.exec(name)?.[1]);
    const at = (end) => {
      const frame = end === 'top' ? 0 : frames.length - 1;
      const distance = values[frame][axis] - frames[frame][from][axis];
      return { over: outside(distance, line.range), measured: `${(distance * 100).toFixed(1)} cm along ${line.axis} at the ${end}` };
    };
    if (line.at !== 'either') return at(line.at);
    const [top, bottom] = [at('top'), at('bottom')];
    return bottom.over < top.over ? bottom : top;
  },
};

/** What each kind of line reads: a point, a number, or either. */
const READS = { path: 'point', place: 'point', travels: 'point', still: 'either' };

const kindOf = (line) => {
  const kinds = [...Object.keys(KINDS), 'look'];
  const kind = kinds.find((k) => k in line);
  if (!kind) throw new Error(`"${line.says}" has no kind: ${kinds.join(', ')}`);
  return kind;
};

/** Throws on a line that reads a point where it takes a number, or the other way round. */
function mustRead(kind, name) {
  const reads = READS[kind] ?? 'number';
  const is = isPoint(name) ? 'point' : 'number';
  if (reads !== 'either' && reads !== is) throw new Error(`A ${kind} line takes a ${reads}, not the ${is} ${name}`);
}

/** The numbers a line names: `*` stands for each side. */
const namesOf = (name) => (name.includes('*') ? ['l', 'r'].map((k) => name.replace('*', k)) : [name]);

/** One line of a spec on `frames`, its worst side when it names both. */
function judgeLine(line, frames) {
  const kind = kindOf(line);
  if (kind === 'look') return { says: line.says, kind, ok: null, measured: `${line.at.join(', ')}, ${line.view}` };
  const found = namesOf(line[kind]).map((name) => {
    mustRead(kind, name);
    if (frames[0][name] === undefined) return { name, over: Infinity, measured: `no ${name} in this clip` };
    return { name, ...KINDS[kind](line, name, frames.map((f) => f[name]), frames) };
  });
  const worst = found.reduce((a, b) => (b.over > a.over ? b : a));
  const measured = found.length > 1 ? `${worst.name}: ${worst.measured}` : worst.measured;
  return { says: line.says, kind, ok: worst.over <= 0, over: worst.over, measured, open: line.open };
}

/** The side of point `name` that travels further through the rep, as `{ work, rest }`. */
function sidesOf(name, frames) {
  const travel = (k) => Math.max(...frames.map((f) => gap(f[`${name}_${k}.at`], frames[0][`${name}_${k}.at`])));
  return travel('l') >= travel('r') ? { work: 'l', rest: 'r' } : { work: 'r', rest: 'l' };
}

/** `line` with `{work}` and `{rest}` put as the sides `spec.works` found on `frames`. */
function onSides(line, sides) {
  return JSON.parse(JSON.stringify(line).replace(/\{(work|rest)\}/g, (_, side) => sides[side]));
}

/** What each line of `spec` finds on `frames`: `ok` true or false, or null for a line that waits for eyes. */
export function judge(spec, frames) {
  const sides = spec.works && sidesOf(spec.works, frames);
  return spec.lines.map((line) => judgeLine(sides ? onSides(line, sides) : line, frames));
}

/** The exercises with a movement spec. */
export async function specified() {
  return (await readdir(SPECS)).filter((f) => f.endsWith('.json')).map((f) => path.basename(f, '.json')).sort();
}

export const readSpec = async (id) => JSON.parse(await readFile(path.join(SPECS, `${id}.json`), 'utf8'));

/** Why the test doesn't hold exercise `id` to `spec`, by the index's `clips`, or null when it does. */
export function waitsFor(spec, id, clips) {
  if (spec.cold) return 'a cold spec judges nothing here';
  return clips[id] ? null : 'no clip yet';
}

/** Each line of exercise `id`'s spec, on every clip it has in `clips`: the worst clip's verdict, and which it was. */
export async function reviewMovement(id, clips, spec) {
  const { all } = await filesOf(clips, id);
  const verdicts = [];
  for (const file of all) {
    judge(spec, await numbersOf(file)).forEach((v, i) => {
      const clip = path.basename(file, '.glb');
      if (!verdicts[i] || v.over > verdicts[i].over) verdicts[i] = { ...v, clip };
    });
  }
  return verdicts;
}

/** How a verdict reads: a failure marked open is known, and doesn't send the lift back. */
export const markOf = (v) => (v.ok === null ? 'look' : v.ok ? 'pass' : v.open ? 'OPEN' : 'FAIL');

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const { values: { at }, positionals: [id] } = parseArgs({ allowPositionals: true, options: { at: { type: 'string' } } });
  if (!id) throw new Error('usage: node movement.mjs <id> [--at <commit>]');
  const verdicts = await reviewMovement(id, await clipsAt(at), await readSpec(id));
  for (const v of verdicts) console.log(`${markOf(v)}  ${v.says}\n      ${v.measured}${v.clip === id ? '' : ` (${v.clip})`}${v.open ? `\n      open: ${v.open}` : ''}`);
  process.exit(verdicts.some((v) => markOf(v) === 'FAIL') ? 1 : 0);
}
