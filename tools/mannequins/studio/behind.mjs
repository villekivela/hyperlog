// Whether the clips are behind what they are baked from. A save bakes only what it changed, which
// is right only if nothing else has changed since the last bake, so the files' dates say: they
// hold through a restart of the studio, and see what was changed with it closed.
import { readdir, stat, utimes } from 'node:fs/promises';
import path from 'node:path';

/** The file whose date is the last bake's. */
const INDEX = 'mannequins.json';
/** What a bake reads under the tool besides its code. seats.json isn't one: the bake writes it. */
const READS = ['recipes', 'traces', 'model'];
const isTest = (file) => /\.(test|fixture)\.mjs$/.test(file);

/** Each file a bake reads in the tool at `tool`. The studio's own code bakes nothing. */
async function sourcesOf(tool) {
  const code = (await readdir(tool)).filter((f) => f.endsWith('.mjs'));
  const read = await Promise.all(READS.map(async (dir) => (await readdir(path.join(tool, dir), { recursive: true })).map((f) => path.join(dir, f))));
  return [...code, ...read.flat()].filter((f) => !isTest(f)).map((f) => path.join(tool, f));
}

/** Whether a file a bake reads, in the tool at `tool` or among `also`, has changed since `clips` were baked. */
export async function clipsBehind(clips, { tool, also = [] }) {
  const baked = await stat(path.join(clips, INDEX)).catch(() => null);
  if (!baked) return true;
  const files = [...await sourcesOf(tool), ...also];
  const dates = await Promise.all(files.map(async (f) => (await stat(f)).mtimeMs));
  // To the millisecond, which is all `datedBake` can give: a file saved just before a bake began isn't newer.
  return dates.some((d) => Math.floor(d) > baked.mtimeMs);
}

/** Runs `bake`, then dates the index in `clips` from when it began: what changes while it runs is newer than the clips. */
export async function datedBake(clips, bake) {
  const began = new Date();
  await bake();
  await utimes(path.join(clips, INDEX), began, began);
}
