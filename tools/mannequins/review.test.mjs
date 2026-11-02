// One command builds a batch's review page; the owner's OK and notes come back from it, and a
// batch is done once every exercise in it is OK for the clip it has now.
import { test, before } from 'node:test';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { mkdtemp, readFile, readdir, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { bakeAll } from './bake.mjs';
import { BATCHES, buildReview, batchStatus, applyTunedValues } from './review.mjs';
import { readGlb } from './glb.mjs';

let clips;
let page;
let out;
before(async () => {
  clips = await mkdtemp(path.join(tmpdir(), 'mannequins-'));
  await bakeAll(clips);
  out = await mkdtemp(path.join(tmpdir(), 'review-'));
  await buildReview('prototype', { clips, out });
  page = await readFile(path.join(out, 'index.html'), 'utf8');
});

/** A clip's fingerprint: its own file, and the mannequin it shares. */
const sha = async (file) => {
  const hash = createHash('sha256');
  for (const f of [file, path.join(path.dirname(file), 'mannequin.bin')]) hash.update(await readFile(f));
  return hash.digest('hex').slice(0, 16);
};
const pageData = () => JSON.parse(page.match(/<script type="application\/json" id="batch">([\s\S]*?)<\/script>/)[1]);

test('the first batch is the prototype\'s seven exercises and the close-grip pulldown', () => {
  assert.deepEqual([...BATCHES.prototype].sort(), [
    'barbell_bench_press', 'barbell_rdl', 'barbell_squat', 'close_grip_lat_pulldown', 'lat_pulldown',
    'leg_press', 'machine_chest_press', 'triceps_pressdown',
  ]);
});

test('the page holds each exercise of the batch, its clip and its check results', async () => {
  const data = pageData();
  assert.equal(data.batch, 'prototype');
  assert.deepEqual(data.exercises.map((e) => e.id), BATCHES.prototype);
  const squat = data.exercises.find((e) => e.id === 'barbell_squat');
  assert.equal(squat.name, 'Barbell Back Squat');
  // The clip is in the page whole, the shared mannequin merged into it: a page may fetch
  // nothing, data: URLs included, so every clip parses from the bytes it holds.
  const [kind, bytes] = squat.clip.split(',');
  assert.equal(kind, 'data:model/gltf-binary;base64');
  for (const e of data.exercises) {
    const { json, bin } = readGlb(Buffer.from(e.clip.split(',')[1], 'base64'));
    assert.deepEqual(json.buffers, [{ byteLength: bin.length }], `${e.id} reads nothing by URI`);
  }
  const own = readGlb(await readFile(path.join(clips, 'barbell_squat.glb')));
  const whole = readGlb(Buffer.from(bytes, 'base64'));
  assert.ok(whole.bin.subarray(0, own.bin.length).equals(own.bin));
  assert.ok(whole.bin.subarray(whole.bin.length - (await readFile(path.join(clips, 'mannequin.bin'))).length).equals(await readFile(path.join(clips, 'mannequin.bin'))));
  // The OK still names the shipped clip and the mannequin it shares.
  assert.equal(squat.hash, await sha(path.join(clips, 'barbell_squat.glb')));
  assert.deepEqual(Object.keys(squat.checks), ['hands', 'feet', 'equipment', 'joints', 'trace']);
  assert.ok(Object.values(squat.checks).every((c) => c.ok && typeof c.found === 'string'));
  assert.deepEqual(squat.camera, { target: [0, 0.87, 0], halfHeight: 1.02, azimuth: 62, elevation: 8 });
  assert.deepEqual(await readdir(out), ['index.html']);
});

test('a batch is done only when every exercise is OK for the clip it has now', async () => {
  const hashes = Object.fromEntries(pageData().exercises.map((e) => [e.id, e.hash]));
  const all = Object.fromEntries(BATCHES.prototype.map((id) => [id, { ok: true, note: '', clip: hashes[id] }]));
  assert.equal((await batchStatus('prototype', all, { clips })).done, true);

  const status = await batchStatus('prototype', {
    ...all,
    barbell_squat: { ok: false, note: 'Bar a touch lower on the back', clip: hashes.barbell_squat },
    leg_press: { ok: true, note: '', clip: 'an-older-clip' },
    machine_chest_press: undefined,
  }, { clips });
  assert.equal(status.done, false);
  const by = Object.fromEntries(status.exercises.map((e) => [e.id, e]));
  assert.deepEqual(by.barbell_squat, { id: 'barbell_squat', state: 'notes', note: 'Bar a touch lower on the back' });
  assert.deepEqual(by.leg_press, { id: 'leg_press', state: 'changed', note: '' });
  assert.deepEqual(by.machine_chest_press, { id: 'machine_chest_press', state: 'waiting', note: '' });
  assert.deepEqual(by.barbell_rdl, { id: 'barbell_rdl', state: 'ok', note: '' });
});

test('an OK with tuned values is not done until review apply has put them in poses.mjs', async () => {
  const hash = await sha(path.join(clips, 'barbell_squat.glb'));
  const verdicts = Object.fromEntries(BATCHES.prototype.map((id) => [id, { ok: true, clip: 'x' }]));
  const none = await batchStatus('prototype', { barbell_squat: { ok: true, clip: hash } }, { clips });
  assert.equal(none.exercises.find((e) => e.id === 'barbell_squat').state, 'ok');
  const tuned = JSON.stringify({ 'feet.stance': 0.2 });
  const { exercises } = await batchStatus('prototype', { ...verdicts, barbell_squat: { ok: true, clip: hash, tuned } }, { clips });
  assert.equal(exercises.find((e) => e.id === 'barbell_squat').state, 'tuned');
  assert.deepEqual(exercises.find((e) => e.id === 'barbell_squat').tuned, { 'feet.stance': 0.2 });
});

test('the page carries the Tune panel: the bundled modules, each exercise\'s entry and the mannequin', () => {
  const data = pageData();
  assert.ok(data.mannequin.startsWith('data:model/gltf-binary;base64,'));
  assert.equal(data.exercises.find((e) => e.id === 'barbell_bench_press').entry.grip.width, 0.4);
  assert.ok(page.includes('const tune = (function (THREE, GLTFLoader)'));
  assert.ok(!page.includes('/*TUNE*/') && !page.includes('/*BATCH*/'));
});

test('review apply puts the tuned values of the OK\'d exercises into poses.mjs and nothing else', async () => {
  const dir = await mkdtemp(path.join(tmpdir(), 'apply-'));
  const file = path.join(dir, 'poses.mjs');
  const original = await readFile(new URL('./poses.mjs', import.meta.url), 'utf8');
  await writeFile(file, original);
  const verdicts = {
    barbell_bench_press: { ok: true, clip: 'x', tuned: JSON.stringify({ 'grip.width': 0.45 }) },
    barbell_squat: { ok: false, clip: 'x', tuned: JSON.stringify({ 'feet.stance': 0.2 }) },
  };
  assert.deepEqual(await applyTunedValues('prototype', verdicts, file), ['barbell_bench_press']);
  const after = await readFile(file, 'utf8');
  assert.equal(after, original.replace('grip: { width: 0.4, elbows: 0.9 }', 'grip: { width: 0.45, elbows: 0.9 }'));
});

test('the adjustable-bench batch is the incline DB press', () => {
  assert.deepEqual(BATCHES['adjustable-bench'], ['db_incline_press']);
});

test('the page of an adjustable-bench pose holds a clip per angle to show, and an OK names every one of them', async () => {
  const dir = await mkdtemp(path.join(tmpdir(), 'review-'));
  await buildReview('adjustable-bench', { clips, out: dir });
  const data = JSON.parse((await readFile(path.join(dir, 'index.html'), 'utf8')).match(/<script type="application\/json" id="batch">([\s\S]*?)<\/script>/)[1]);
  const [press] = data.exercises;
  assert.equal(press.typical, 30);
  assert.deepEqual(Object.keys(press.angles), ['0', '15', '30', '45', '60', '75']);
  for (const { clip, camera } of Object.values(press.angles)) {
    const { json, bin } = readGlb(Buffer.from(clip.split(',')[1], 'base64'));
    assert.deepEqual(json.buffers, [{ byteLength: bin.length }]);
    assert.ok(camera.halfHeight > 0);
  }
  // The pose's entry is prepared at its typical angle, for the Tune panel.
  assert.equal(press.entry.bench.angle, 30);

  // The OK is for all six clips: it holds until any of them is baked differently.
  const verdicts = { db_incline_press: { ok: true, clip: press.hash } };
  assert.equal((await batchStatus('adjustable-bench', verdicts, { clips })).exercises[0].state, 'ok');
  const copy = await mkdtemp(path.join(tmpdir(), 'mannequins-'));
  for (const f of await readdir(clips)) await writeFile(path.join(copy, f), await readFile(path.join(clips, f)));
  const file = path.join(copy, 'db_incline_press_75.glb');
  await writeFile(file, Buffer.concat([await readFile(file), Buffer.alloc(4)]));
  assert.equal((await batchStatus('adjustable-bench', verdicts, { clips: copy })).exercises[0].state, 'changed');
});
