// One command builds a batch's review page; the owner's OK and notes come back from it, and a
// batch is done once every exercise in it is OK for the clip it has now.
import { test, before } from 'node:test';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { mkdtemp, readFile, readdir } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { bakeAll } from './bake.mjs';
import { BATCHES, buildReview, batchStatus } from './review.mjs';

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

const sha = async (file) => createHash('sha256').update(await readFile(file)).digest('hex').slice(0, 16);
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
  // The clip is in the page itself: the page is published alone.
  const [kind, bytes] = squat.clip.split(',');
  assert.equal(kind, 'data:model/gltf-binary;base64');
  assert.ok(Buffer.from(bytes, 'base64').equals(await readFile(path.join(clips, 'barbell_squat.glb'))));
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
