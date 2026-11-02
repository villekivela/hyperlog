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
import { clipContent, inlineResources } from './glb.mjs';
import { execFileSync } from 'node:child_process';
import { bundleTune } from './bundle.mjs';
import { patchPoses } from './patch.mjs';
import { MODEL } from './stage.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const LIBRARY = path.join(CLIPS, '..', 'exercises.json');

/** The batches poses are made and reviewed in, in order (SPEC, The mannequin). */
export const BATCHES = {
  // The prototype's seven exercises, and the close-grip pulldown that shows `like:`.
  prototype: [
    'barbell_squat', 'barbell_bench_press', 'barbell_rdl', 'lat_pulldown', 'close_grip_lat_pulldown',
    'triceps_pressdown', 'leg_press', 'machine_chest_press',
  ],
  // The adjustable-bench pose, baked at six angles (#125).
  'adjustable-bench': ['db_incline_press'],
  // The starter program's exercises that had no pose (#127), with the machines and the cable
  // column they needed.
  starter: [
    'close_grip_bench_press', '2_grip_lat_pulldown', 'machine_pulldown', 'chest_supported_machine_row', 'hack_squat',
    'leg_extension', 'seated_leg_curl', 'lying_leg_curl', 'seated_calf_raise', 'machine_preacher_curl', 'bayesian_cable_curl',
    'overhead_cable_triceps_extension', 'cable_lateral_raise', 'cable_shoulder_press', 'cable_flye', 'reverse_cable_crossover',
  ],
  // Every pose above again, on the kits at their full detail and in their tones (#127).
  kits: [
    'barbell_squat', 'barbell_bench_press', 'barbell_rdl', 'db_incline_press', 'lat_pulldown', 'close_grip_lat_pulldown',
    'triceps_pressdown', 'leg_press', 'machine_chest_press',
  ],
};

const batchOf = (name) => {
  const ids = BATCHES[name];
  if (!ids) throw new Error(`No batch "${name}": ${Object.keys(BATCHES).join(', ')}`);
  return ids;
};

/**
 * The files of the clip `id`, from the index in `clips`: its own `clip` (an adjustable bench's
 * typical angle), the clip of each bench angle (`angles`, by degrees, none for another pose), the
 * files they read (`shared`), and every clip file an OK names (`all`).
 */
async function filesOf(clips, id) {
  const index = JSON.parse(await readFile(path.join(clips, 'mannequins.json'), 'utf8'));
  const entry = index.clips[id];
  const at = (f) => path.join(clips, f);
  const angles = Object.fromEntries(Object.entries(entry.bench?.clips ?? {}).map(([a, c]) => [a, { file: at(c.clip), camera: c.camera }]));
  const shared = (entry.resources ?? []).map(at);
  return { clip: at(entry.clip), angles, shared, all: entry.bench ? Object.values(angles).map((c) => c.file) : [at(entry.clip)] };
}

/** A clip's file as the page holds it: whole, with the files it shares (the mannequin, its kits) merged in, as a data URL. */
async function wholeClip(file, shared) {
  const files = Object.fromEntries(await Promise.all(shared.map(async (f) => [path.basename(f), await readFile(f)])));
  return `data:model/gltf-binary;base64,${inlineResources(await readFile(file), files).toString('base64')}`;
}

/**
 * A clip's fingerprint, which a verdict names: what each of its files shows, with the parts it
 * reads from the files it shares (`clipContent`), so a kit file that grows for another exercise
 * leaves it as it was.
 */
async function hashOf({ all, shared }) {
  const files = Object.fromEntries(await Promise.all(shared.map(async (f) => [path.basename(f), await readFile(f)])));
  const hash = createHash('sha256');
  for (const f of all) hash.update(clipContent(await readFile(f), files));
  return hash.digest('hex').slice(0, 16);
}


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
    const files = await filesOf(clips, id);
    const { clip, angles, shared } = files;
    // The page may fetch nothing, data: URLs included, so each clip goes in whole, with the
    // mannequin and the kits it shares merged into it. The OK still names the files that ship.
    exercises.push({
      id, name: names[id] ?? id, equipment: entry.equipment, clip: await wholeClip(clip, shared),
      hash: await hashOf(files), camera: entry.camera, traced: Boolean(entry.trace), checks, entry,
      // An adjustable bench shows at each angle it is baked at, from its typical one.
      ...(entry.bench ? {
        typical: entry.bench.typical,
        angles: Object.fromEntries(await Promise.all(Object.entries(angles).map(async ([a, c]) => [a, { clip: await wholeClip(c.file, shared), camera: c.camera }]))),
      } : {}),
    });
  }
  const template = await readFile(path.join(HERE, 'review', 'page.html'), 'utf8');
  // The data sits in the page as JSON; `<` is escaped so nothing in it can close the script.
  const mannequin = `data:model/gltf-binary;base64,${(await readFile(MODEL)).toString('base64')}`;
  const data = JSON.stringify({ batch, exercises, mannequin }).replace(/</g, '\\u003c');
  // The Tune panel's code is the tool's own, bundled into the page's script (bundle.mjs).
  const code = await bundleTune();
  if (code.includes('</script')) throw new Error("The tune bundle has a </script in it");
  const tune = `const tune = (function (THREE, GLTFLoader) {\n${code}})(THREE, GLTFLoader);`;
  const page = template.replace('/*BATCH*/{}', () => data).replace('/*TUNE*/', () => tune);
  await writeFile(path.join(out, 'index.html'), page);
  return out;
}

/** The tuned values of a verdict (`{ path: value }`): the page saves them as JSON text. */
function tunedOf(verdict) {
  const t = verdict?.tuned;
  if (!t) return {};
  return typeof t === 'string' ? JSON.parse(t || '{}') : t;
}

/** Where one exercise stands, from its verdict and the fingerprint of the clip it has now. */
function stateOf(verdict, now) {
  if (verdict?.ok) return verdict.clip === now ? (Object.keys(tunedOf(verdict)).length ? 'tuned' : 'ok') : 'changed';
  if (verdict?.note) return 'notes';
  return 'waiting';
}

/**
 * How each exercise of the batch stands against the owner's verdicts (`{ id: { ok, note, clip } }`,
 * as the page saves them): `ok` for the clip it has now, `tuned` when the OK came with tuned values
 * that `review apply` hasn't yet put in poses.mjs, `changed` when the OK was for an earlier clip,
 * `notes` when the owner left a note and no OK, or `waiting`.
 */
export async function batchStatus(batch, verdicts, { clips = CLIPS } = {}) {
  const exercises = [];
  for (const id of batchOf(batch)) {
    const v = verdicts[id];
    const now = await hashOf(await filesOf(clips, id));
    const tuned = tunedOf(v);
    exercises.push({ id, state: stateOf(v, now), note: v?.note ?? '', ...(Object.keys(tuned).length ? { tuned } : {}) });
  }
  return { done: exercises.every((e) => e.state === 'ok'), exercises };
}

/**
 * Puts the tuned values of each OK'd exercise of the batch into poses.mjs (`posesFile`) and returns
 * the ids it changed. It doesn't bake: `review apply` does that, then gives each its new OK.
 */
export async function applyTunedValues(batch, verdicts, posesFile) {
  let source = await readFile(posesFile, 'utf8');
  const applied = [];
  for (const id of batchOf(batch)) {
    const tuned = tunedOf(verdicts[id]);
    if (!verdicts[id]?.ok || !Object.keys(tuned).length) continue;
    source = patchPoses(source, id, tuned);
    applied.push(id);
  }
  if (applied.length) await writeFile(posesFile, source);
  return applied;
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const [verb, name] = process.argv.slice(2);
  if (verb === 'status') {
    const file = path.join(HERE, 'review', `${name}.json`);
    const verdicts = await readFile(file, 'utf8').then(JSON.parse, () => ({}));
    const { done, exercises } = await batchStatus(name, verdicts);
    for (const e of exercises) {
      const tuned = e.tuned ? ` [tuned: ${Object.entries(e.tuned).map(([p, v]) => `${p}=${JSON.stringify(v)}`).join(', ')}]` : '';
      console.log(`${e.state.padEnd(8)} ${e.id}${e.note ? `: ${e.note}` : ''}${tuned}`);
    }
    console.log(done ? `${name} is done` : `${name} isn't done`);
    process.exit(done ? 0 : 1);
  } else if (verb === 'apply') {
    const file = path.join(HERE, 'review', `${name}.json`);
    const verdicts = await readFile(file, 'utf8').then(JSON.parse, () => ({}));
    const applied = await applyTunedValues(name, verdicts, path.join(HERE, 'poses.mjs'));
    if (!applied.length) { console.log('no tuned values to apply'); process.exit(0); }
    // Bake in a process of its own: this one has the old poses loaded.
    execFileSync(process.execPath, [path.join(HERE, 'bake.mjs')], { stdio: 'inherit' });
    for (const id of applied) {
      verdicts[id] = { ...verdicts[id], clip: await hashOf(await filesOf(CLIPS, id)) };
      delete verdicts[id].tuned;
    }
    await writeFile(file, JSON.stringify(verdicts, null, 2) + '\n');
    console.log(`applied to poses.mjs and re-baked: ${applied.join(', ')}`);
    console.log('Give them their new OK in the page database (verdicts/<id>: clip = the new hash, tuned = ""):');
    for (const id of applied) console.log(`  ${id}: ${verdicts[id].clip}`);
  } else if (verb) {
    const out = await buildReview(verb);
    console.log(`built ${path.relative(process.cwd(), path.join(out, 'index.html'))}`);
  } else {
    console.error('usage: node review.mjs <batch> | node review.mjs status <batch> | node review.mjs apply <batch>');
    process.exit(2);
  }
}
