// A batch's review page, and whether the batch is done (README, Reviewing a batch):
//
//   node review.mjs <batch>            builds review/out/<batch>/index.html, the page to publish
//   node review.mjs status <batch>     how the batch stands, from review/<batch>.json
//
// The page plays each exercise's baked clip in a loop, turns it on a drag, lists its check results,
// and keeps the owner's OK and note for each in its database. Each verdict names the clip it was
// given on, so a re-baked clip needs a new OK. A batch is done when every exercise in it is OK for
// the clip it has now.
import { createHash } from 'node:crypto';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { prepare } from './entries.mjs';
import { checkPose } from './checks.mjs';
import { CLIPS } from './bake.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const LIBRARY = path.join(CLIPS, '..', 'exercises.json');

/** The batches poses are made and reviewed in, in order (SPEC, The mannequin). */
export const BATCHES = {
  // The prototype's seven exercises, and the close-grip pulldown that shows `like:`.
  prototype: [
    'barbell_squat', 'barbell_bench_press', 'barbell_rdl', 'lat_pulldown', 'close_grip_lat_pulldown',
    'triceps_pressdown', 'leg_press', 'machine_chest_press',
  ],
};

const batchOf = (name) => {
  const ids = BATCHES[name];
  if (!ids) throw new Error(`No batch "${name}": ${Object.keys(BATCHES).join(', ')}`);
  return ids;
};

/** A clip's fingerprint, which a verdict names. */
const hashOf = async (file) => createHash('sha256').update(await readFile(file)).digest('hex').slice(0, 16);

/** Builds the batch's review page into `out`/index.html, its clips in it: the page is published alone. */
export async function buildReview(batch, { clips = CLIPS, out = path.join(HERE, 'review', 'out', batch) } = {}) {
  const ids = batchOf(batch);
  const library = JSON.parse(await readFile(LIBRARY, 'utf8'));
  const names = Object.fromEntries(library.exercises.map((e) => [e.id, e.name]));
  await mkdir(out, { recursive: true });
  const exercises = [];
  for (const id of ids) {
    const entry = prepare(id);
    const { checks } = await checkPose(id);
    const file = path.join(clips, `${id}.glb`);
    const clip = `data:model/gltf-binary;base64,${(await readFile(file)).toString('base64')}`;
    exercises.push({
      id, name: names[id] ?? id, equipment: entry.equipment, clip,
      hash: await hashOf(file), camera: entry.camera, traced: Boolean(entry.trace), checks,
    });
  }
  const template = await readFile(path.join(HERE, 'review', 'page.html'), 'utf8');
  // The data sits in the page as JSON; `<` is escaped so nothing in it can close the script.
  const data = JSON.stringify({ batch, exercises }).replace(/</g, '\\u003c');
  await writeFile(path.join(out, 'index.html'), template.replace('/*BATCH*/{}', data));
  return out;
}

/** Where one exercise stands, from its verdict and the fingerprint of the clip it has now. */
function stateOf(verdict, now) {
  if (verdict?.ok) return verdict.clip === now ? 'ok' : 'changed';
  if (verdict?.note) return 'notes';
  return 'waiting';
}

/**
 * How each exercise of the batch stands against the owner's verdicts (`{ id: { ok, note, clip } }`,
 * as the page saves them): `ok` for the clip it has now, `changed` when the OK was for an earlier
 * clip, `notes` when the owner left a note and no OK, or `waiting`.
 */
export async function batchStatus(batch, verdicts, { clips = CLIPS } = {}) {
  const exercises = [];
  for (const id of batchOf(batch)) {
    const v = verdicts[id];
    const now = await hashOf(path.join(clips, `${id}.glb`));
    exercises.push({ id, state: stateOf(v, now), note: v?.note ?? '' });
  }
  return { done: exercises.every((e) => e.state === 'ok'), exercises };
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const [verb, name] = process.argv.slice(2);
  if (verb === 'status') {
    const file = path.join(HERE, 'review', `${name}.json`);
    const verdicts = await readFile(file, 'utf8').then(JSON.parse, () => ({}));
    const { done, exercises } = await batchStatus(name, verdicts);
    for (const e of exercises) console.log(`${e.state.padEnd(8)} ${e.id}${e.note ? `: ${e.note}` : ''}`);
    console.log(done ? `${name} is done` : `${name} isn't done`);
    process.exit(done ? 0 : 1);
  } else if (verb) {
    const out = await buildReview(verb);
    console.log(`built ${path.relative(process.cwd(), path.join(out, 'index.html'))}`);
  } else {
    console.error('usage: node review.mjs <batch> | node review.mjs status <batch>');
    process.exit(2);
  }
}
