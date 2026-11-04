// The owner OKs each exercise in the studio, or leaves a note, and both are files in the repo: the
// OK in its batch's review/<batch>.json, naming the clip it was given on, and the note in
// review/notes.md. A batch is done once every exercise in it is OK for the clip it has now.
import { test, before } from 'node:test';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { mkdtemp, readFile, readdir, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { bakeAll } from './bake.mjs';
import { BATCHES, addNote, batchStatus, batchReviewing, phoneChecked, readNotes, setOk } from './review.mjs';
import { clipContent, readGlb, writeGlb } from './glb.mjs';
import { POSES } from './poses.mjs';

let clips;
before(async () => {
  clips = await mkdtemp(path.join(tmpdir(), 'mannequins-'));
  await bakeAll(clips);
});

/** A clip's fingerprint: what it shows, with the parts it reads from the files it shares. */
const sha = async (...files) => {
  const hash = createHash('sha256');
  for (const file of files) {
    const index = JSON.parse(await readFile(path.join(path.dirname(file), 'mannequins.json'), 'utf8'));
    const { resources } = Object.values(index.clips).find((c) => [c, ...Object.values(c.bench?.clips ?? {})].some((x) => x.clip === path.basename(file)));
    const shared = Object.fromEntries(await Promise.all(resources.map(async (r) => [r, await readFile(path.join(path.dirname(file), r))])));
    hash.update(clipContent(await readFile(file), shared));
  }
  return hash.digest('hex').slice(0, 16);
};
const reviewDir = () => mkdtemp(path.join(tmpdir(), 'review-'));
const json = async (file) => JSON.parse(await readFile(file, 'utf8'));

test('the first batch is the prototype\'s seven exercises and the close-grip pulldown', () => {
  assert.deepEqual([...BATCHES.prototype].sort(), [
    'barbell_bench_press', 'barbell_rdl', 'barbell_squat', 'close_grip_lat_pulldown', 'lat_pulldown',
    'leg_press', 'machine_chest_press', 'triceps_pressdown',
  ]);
});

test('an OK goes into the latest batch that lists the exercise, naming the clip it was given on', async () => {
  const dir = await reviewDir();
  // The squat was posed in the prototype and re-reviewed on the detailed kits; the hack squat is the starter's.
  assert.equal(batchReviewing('barbell_squat'), 'kits');
  assert.equal(batchReviewing('hack_squat'), 'starter');
  assert.equal(batchReviewing('pendulum_squat'), null);
  const squat = await setOk('barbell_squat', true, { dir, clips, at: '2026-11-03T12:00:00.000Z' });
  assert.deepEqual(squat, { batch: 'kits', ok: true, clip: await sha(path.join(clips, 'barbell_squat.glb')) });
  assert.deepEqual(await json(path.join(dir, 'kits.json')), {
    barbell_squat: { ok: true, clip: squat.clip, at: '2026-11-03T12:00:00.000Z' },
  });
  // A second OK in the same batch keeps the first; the file stays sorted, for a short diff.
  await setOk('barbell_rdl', true, { dir, clips, at: '2026-11-03T12:01:00.000Z' });
  assert.deepEqual(Object.keys(await json(path.join(dir, 'kits.json'))), ['barbell_rdl', 'barbell_squat']);
  // Taking it back keeps what else the verdict holds.
  await setOk('barbell_squat', false, { dir, clips, at: '2026-11-03T12:02:00.000Z' });
  assert.deepEqual((await json(path.join(dir, 'kits.json'))).barbell_squat, { ok: false, clip: squat.clip, at: '2026-11-03T12:02:00.000Z' });
  await assert.rejects(setOk('pendulum_squat', true, { dir, clips }), /pendulum_squat is in no batch/);
});

test('a batch is done only when every exercise is OK for the clip it has now', async () => {
  const dir = await reviewDir();
  for (const id of BATCHES.prototype) await setOk(id, true, { dir, clips, batch: 'prototype' });
  assert.equal((await batchStatus('prototype', { dir, clips })).done, true);

  const verdicts = await json(path.join(dir, 'prototype.json'));
  verdicts.leg_press.clip = 'an-older-clip';
  delete verdicts.machine_chest_press;
  verdicts.barbell_squat.ok = false;
  await writeFile(path.join(dir, 'prototype.json'), JSON.stringify(verdicts));
  const status = await batchStatus('prototype', { dir, clips });
  assert.equal(status.done, false);
  const by = Object.fromEntries(status.exercises.map((e) => [e.id, e.state]));
  assert.deepEqual(by, {
    barbell_squat: 'waiting', barbell_bench_press: 'ok', barbell_rdl: 'ok', lat_pulldown: 'ok', close_grip_lat_pulldown: 'ok',
    triceps_pressdown: 'ok', leg_press: 'changed', machine_chest_press: 'waiting',
  });
});

test('a note goes into notes.md under its exercise or kit, for the agent to read, and status lists it', async () => {
  const dir = await reviewDir();
  const file = path.join(dir, 'notes.md');
  await addNote('barbell_squat', 'Bar a touch lower on the back', { dir, clips, at: '2026-11-03T12:00:00.000Z' });
  await addNote('kit:cable_tower', 'The pulley sits too high\nand the cap is missing', { dir, clips, at: '2026-11-03T12:01:00.000Z' });
  await addNote('barbell_squat', 'Knees further out', { dir, clips, at: '2026-11-03T12:02:00.000Z' });
  const clip = (await sha(path.join(clips, 'barbell_squat.glb'))).slice(0, 8);
  const text = await readFile(file, 'utf8');
  assert.ok(text.startsWith('# Notes\n'), 'it says what the file is');
  assert.ok(text.endsWith(`## barbell_squat

- 2026-11-03, clip ${clip}: Bar a touch lower on the back
- 2026-11-03, clip ${clip}: Knees further out

## kit:cable_tower

- 2026-11-03: The pulley sits too high
  and the cap is missing
`), text);
  assert.deepEqual(await readNotes({ dir }), {
    barbell_squat: [`2026-11-03, clip ${clip}: Bar a touch lower on the back`, `2026-11-03, clip ${clip}: Knees further out`],
    'kit:cable_tower': ['2026-11-03: The pulley sits too high\nand the cap is missing'],
  });

  // The agent deletes a note once it is fixed; what is left still reads.
  await writeFile(file, text.replace(`- 2026-11-03, clip ${clip}: Knees further out\n`, ''));
  assert.equal((await readNotes({ dir })).barbell_squat.length, 1);
  const { exercises } = await batchStatus('kits', { dir, clips });
  assert.deepEqual(exercises.find((e) => e.id === 'barbell_squat'), { id: 'barbell_squat', state: 'notes', notes: [`2026-11-03, clip ${clip}: Bar a touch lower on the back`] });
  assert.deepEqual(exercises.find((e) => e.id === 'barbell_rdl'), { id: 'barbell_rdl', state: 'waiting', notes: [] });
  // An OK for the clip it has now wins; the note is still listed.
  await setOk('barbell_squat', true, { dir, clips });
  const after = (await batchStatus('kits', { dir, clips })).exercises.find((e) => e.id === 'barbell_squat');
  assert.equal(after.state, 'ok');
  assert.equal(after.notes.length, 1);
});

test('a batch swiped in the debug gallery is phone-checked, for the clips it has now', async () => {
  const dir = await reviewDir();
  for (const id of BATCHES.prototype) await setOk(id, true, { dir, clips, batch: 'prototype' });
  await phoneChecked('prototype', { dir, clips });
  const { done, exercises } = await batchStatus('prototype', { dir, clips });
  assert.equal(done, true);
  assert.deepEqual([...new Set(exercises.map((e) => e.state))], ['phone-checked']);
  const verdicts = await json(path.join(dir, 'prototype.json'));
  assert.equal(verdicts.leg_press.phone, verdicts.leg_press.clip);
  // A clip baked since is neither OK nor phone-checked.
  verdicts.leg_press.clip = verdicts.leg_press.phone = 'an-older-clip';
  await writeFile(path.join(dir, 'prototype.json'), JSON.stringify(verdicts));
  assert.equal((await batchStatus('prototype', { dir, clips })).exercises.find((e) => e.id === 'leg_press').state, 'changed');
});

test('an OK holds when another exercise\'s parts join the kit file its clip shares', async () => {
  const dir = await reviewDir();
  for (const id of BATCHES.prototype) await setOk(id, true, { dir, clips, batch: 'prototype' });
  // A cable exercise baked before the pulldowns, at a column of its own: its parts go into the
  // cable tower's file ahead of theirs.
  const poses = { ...POSES, a_cable_row: { like: 'lat_pulldown', kit: { cable_tower: { column: 1.1 } } } };
  const again = await mkdtemp(path.join(tmpdir(), 'mannequins-'));
  await bakeAll(again, { poses });
  assert.notEqual((await readFile(path.join(again, 'kit_cable_tower.bin'))).length, (await readFile(path.join(clips, 'kit_cable_tower.bin'))).length);
  const { done, exercises } = await batchStatus('prototype', { dir, clips: again });
  assert.deepEqual(exercises.filter((e) => e.state !== 'ok'), []);
  assert.equal(done, true);
});

test('the adjustable-bench batch is the incline DB press', () => {
  assert.deepEqual(BATCHES['adjustable-bench'], ['db_incline_press']);
});

const STARTER = [
  '2_grip_lat_pulldown', 'bayesian_cable_curl', 'cable_flye', 'cable_lateral_raise', 'cable_shoulder_press',
  'chest_supported_machine_row', 'close_grip_bench_press', 'hack_squat', 'leg_extension', 'lying_leg_curl',
  'machine_preacher_curl', 'machine_pulldown', 'overhead_cable_triceps_extension', 'reverse_cable_crossover',
  'seated_calf_raise', 'seated_leg_curl',
];

test('every exercise of the starter program has a pose, the ones it didn\'t have in the starter batch', async () => {
  const program = JSON.parse(await readFile(new URL('../../app/src/main/assets/programs/full-body-3-day.json', import.meta.url), 'utf8'));
  const ids = new Set();
  const walk = (o) => {
    if (Array.isArray(o)) o.forEach(walk);
    else if (o && typeof o === 'object') for (const [k, v] of Object.entries(o)) (k === 'exercise' && typeof v === 'string' ? ids.add(v) : walk(v));
  };
  walk(program);
  assert.deepEqual([...ids].filter((id) => !POSES[id]).sort(), []);
  assert.deepEqual([...BATCHES.starter].sort(), STARTER);
});

test('the barbell batch is the library\'s next barbell exercises, bar three that wait for what they need', async () => {
  const library = JSON.parse(await readFile(new URL('../../app/src/main/assets/exercises.json', import.meta.url), 'utf8'));
  // The first sixteen barbell exercises without a pose before this batch, in the library's order.
  const before = new Set([...Object.keys(POSES)].filter((id) => !BATCHES.barbell.includes(id)));
  const next = library.exercises.filter((e) => e.equipment === 'barbell' && !before.has(e.id)).slice(0, 16).map((e) => e.id);
  // An adjustable bench is seven clips, and a decline needs the foot catch; a lunge needs a split stance.
  const waiting = ['barbell_incline_press', 'barbell_lunge', 'decline_bench_press'];
  assert.deepEqual([...BATCHES.barbell].sort(), next.filter((id) => !waiting.includes(id)).sort());
  for (const id of BATCHES.barbell) assert.ok(POSES[id], `${id} has no pose`);
});

test('the kits batch is every exercise posed before #127, re-reviewed on the detailed kits', () => {
  assert.deepEqual([...BATCHES.kits].sort(), [
    'barbell_bench_press', 'barbell_rdl', 'barbell_squat', 'close_grip_lat_pulldown', 'db_incline_press',
    'lat_pulldown', 'leg_press', 'machine_chest_press', 'triceps_pressdown',
  ]);
});

test('an adjustable-bench pose\'s OK names the clip of every angle it is baked at', async () => {
  const dir = await reviewDir();
  const files = ['0', '15', '30', '45', '60', '75', '85'].map((a) => path.join(clips, `db_incline_press_${a}.glb`));
  const { clip } = await setOk('db_incline_press', true, { dir, clips, batch: 'adjustable-bench' });
  assert.equal(clip, await sha(...files));
  assert.equal((await batchStatus('adjustable-bench', { dir, clips })).exercises[0].state, 'ok');
  // The OK holds until any of the seven is baked differently.
  const copy = await mkdtemp(path.join(tmpdir(), 'mannequins-'));
  for (const f of await readdir(clips)) await writeFile(path.join(copy, f), await readFile(path.join(clips, f)));
  const file = path.join(copy, 'db_incline_press_75.glb');
  const baked = readGlb(await readFile(file));
  baked.bin[0] ^= 1;
  await writeFile(file, writeGlb(baked.json, baked.bin));
  assert.equal((await batchStatus('adjustable-bench', { dir, clips: copy })).exercises[0].state, 'changed');
});
