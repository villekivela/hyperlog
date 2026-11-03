// `npm run studio` serves every exercise and kit with its status, poses them with the tool's own
// modules, and writes what the owner does into the repo's files: a save into poses.mjs (its
// comments kept) and a re-bake, an OK into the batch's file, a note into notes.md. Here against
// copies of those files, with a bake that only says it ran.
import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, readFile, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createServer } from 'vite';
import { studio } from './plugin.mjs';
import { KITS } from '../kit.mjs';

const TOOL = path.dirname(path.dirname(fileURLToPath(import.meta.url)));
const LIBRARY = JSON.parse(await readFile(path.join(TOOL, '..', '..', 'app', 'src', 'main', 'assets', 'exercises.json'), 'utf8'));
const ORIGINAL = await readFile(path.join(TOOL, 'poses.mjs'), 'utf8');

let server;
let base;
let poses;
let review;
const baked = [];
let bakeFails = false;

before(async () => {
  const dir = await mkdtemp(path.join(tmpdir(), 'studio-'));
  poses = path.join(dir, 'poses.mjs');
  review = await mkdtemp(path.join(tmpdir(), 'studio-review-'));
  // The squat's hands a metre off its bar: the hands check fails it.
  await writeFile(poses, ORIGINAL.replace(/(\n  barbell_rdl: \{)/, "\n  barbell_lunge: { like: 'barbell_squat', grip: { width: 1.4 } },$1"));
  server = await createServer({
    configFile: false,
    root: TOOL,
    logLevel: 'silent',
    server: { port: 0, host: '127.0.0.1' },
    plugins: [studio({
      posesFile: poses,
      reviewDir: review,
      bake: async () => {
        if (bakeFails) throw new Error('barbell_bench_press failed hands on frame 0');
        baked.push(await readFile(poses, 'utf8'));
      },
    })],
  });
  await server.listen();
  base = server.resolvedUrls.local[0].replace(/\/$/, '');
});
after(() => server?.close());

const get = async (url) => {
  const res = await fetch(base + url);
  assert.equal(res.status, 200, `${url}: ${res.status}`);
  return res.headers.get('content-type')?.includes('json') ? res.json() : res.text();
};
const post = (url, body) => fetch(base + url, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(body) });

test('the sidebar lists every exercise in the library, grouped by equipment, and every kit, each with its status', async () => {
  const { exercises, kits } = await get('/api/items?wait=1');
  const listed = exercises.flatMap((g) => g.items);
  assert.deepEqual(listed.map((e) => e.id).sort(), LIBRARY.exercises.map((e) => e.id).sort());
  for (const g of exercises) assert.ok(g.items.every((e) => e.equipment === g.equipment), g.equipment);
  assert.equal(exercises[0].equipment, 'barbell');
  assert.deepEqual(kits.map((k) => k.name).sort(), Object.keys(KITS).sort());
  const by = Object.fromEntries(listed.map((e) => [e.id, e]));
  assert.deepEqual(by.barbell_squat, { id: 'barbell_squat', name: 'Barbell Back Squat', equipment: 'barbell', status: 'waiting', batch: 'kits', notes: 0 });
  assert.equal(by.pendulum_squat.status, 'no pose');
  const cableTower = kits.find((k) => k.name === 'cable_tower');
  assert.equal(cableTower.uses, '2_grip_lat_pulldown');
});

test('a pose that fails a check is listed as failing', async () => {
  const res = await get('/api/item/exercise/barbell_lunge');
  assert.equal(res.status, 'failing');
  assert.equal(res.checks.hands.ok, false);
});

test('an exercise comes with its prepared entry, its checks and its baked clip', async () => {
  const squat = await get('/api/item/exercise/barbell_squat');
  assert.equal(squat.entry.motion, 'squat');
  assert.deepEqual(Object.keys(squat.checks), ['hands', 'feet', 'equipment', 'joints', 'trace']);
  assert.equal(squat.clip.file, '/clips/barbell_squat.glb');
  assert.deepEqual(squat.clip.camera, { target: [0, 0.87, 0], halfHeight: 1.02, azimuth: 62, elevation: 8 });
  const glb = await fetch(base + squat.clip.file);
  assert.equal(glb.status, 200);
  assert.equal(Buffer.from(await glb.arrayBuffer()).subarray(0, 4).toString(), 'glTF');
  // An adjustable bench comes at its typical angle, with a clip for each angle.
  const press = await get('/api/item/exercise/db_incline_press');
  assert.equal(press.entry.bench.angle, 30);
  assert.deepEqual(Object.keys(press.clip.angles), ['0', '15', '30', '45', '60', '75']);
  // A kit comes as the first exercise that uses it sets it up.
  const kit = await get('/api/item/kit/leg_extension');
  assert.equal(kit.uses, 'leg_extension');
  assert.equal(kit.entry.motion, 'knee_lever');
  const none = await get('/api/item/exercise/pendulum_squat');
  assert.equal(none.entry, null);
  assert.equal(none.status, 'no pose');
});

test('a save writes the tuned numbers into poses.mjs with its comments kept, then bakes', async () => {
  const res = await post('/api/save', { id: 'barbell_bench_press', tuned: { 'grip.width': 0.45 } });
  assert.equal(res.status, 200, await res.clone().text());
  const after = await readFile(poses, 'utf8');
  const before = ORIGINAL.replace(/(\n  barbell_rdl: \{)/, "\n  barbell_lunge: { like: 'barbell_squat', grip: { width: 1.4 } },$1");
  assert.equal(after, before.replace('grip: { width: 0.4, elbows: 0.9 }', 'grip: { width: 0.45, elbows: 0.9 }'));
  assert.deepEqual(baked, [after]);
  // The studio poses from the file as it is now.
  assert.equal((await get('/api/item/exercise/barbell_bench_press')).entry.grip.width, 0.45);
});

test('a save whose bake fails leaves poses.mjs as it was and says why', async () => {
  const was = await readFile(poses, 'utf8');
  bakeFails = true;
  try {
    const res = await post('/api/save', { id: 'barbell_bench_press', tuned: { 'grip.width': 0.9 } });
    assert.equal(res.status, 422);
    assert.match((await res.json()).error, /failed hands on frame 0/);
    assert.equal(await readFile(poses, 'utf8'), was);
  } finally {
    bakeFails = false;
  }
});

test('OK and a note are written to the review files, and the sidebar shows them', async () => {
  let res = await post('/api/ok', { id: 'barbell_rdl', ok: true });
  assert.equal(res.status, 200, await res.clone().text());
  assert.equal((await res.json()).batch, 'kits');
  assert.equal(JSON.parse(await readFile(path.join(review, 'kits.json'), 'utf8')).barbell_rdl.ok, true);
  res = await post('/api/note', { item: 'barbell_row', text: 'the path arcs, it should not' });
  assert.equal(res.status, 200, await res.clone().text());
  res = await post('/api/note', { item: 'kit:cable_tower', text: 'the cap is missing' });
  assert.equal(res.status, 200);
  assert.match(await readFile(path.join(review, 'notes.md'), 'utf8'), /## barbell_row\n\n- \d{4}-\d\d-\d\d, clip \w{8}: the path arcs, it should not\n/);
  const { exercises, kits } = await get('/api/items?wait=1');
  const by = Object.fromEntries(exercises.flatMap((g) => g.items).map((e) => [e.id, e]));
  assert.equal(by.barbell_rdl.status, 'ok');
  assert.equal(by.barbell_row.status, 'notes');
  assert.equal(by.barbell_row.notes, 1);
  assert.equal(kits.find((k) => k.name === 'cable_tower').notes, 1);
  assert.deepEqual((await get('/api/item/kit/cable_tower')).notes, [`${new Date().toISOString().slice(0, 10)}: the cap is missing`]);
  // An exercise in no batch can't be OK'd yet.
  res = await post('/api/ok', { id: 'pendulum_squat', ok: true });
  assert.equal(res.status, 422);
});

test('the page is served, and the tool\'s modules reach it with node\'s files stubbed out', async () => {
  assert.match(await get('/'), /<title>Mannequin Studio<\/title>/);
  const stage = await get('/stage.mjs');
  assert.doesNotMatch(stage, /from "node:fs\/promises"|from 'node:fs\/promises'/);
  const entries = await get('/entries.mjs');
  assert.doesNotMatch(entries, /from "node:fs"|from 'node:fs'/);
});

// Vite restarts its server, dropping a save in flight, when a file its config imports changes.
test('the studio\'s config reaches neither poses.mjs nor kit.mjs, which change while it runs', async () => {
  const config = await createServer({ configFile: path.join(TOOL, 'studio', 'vite.config.mjs'), logLevel: 'silent', server: { port: 0, host: '127.0.0.1' } });
  try {
    const deps = config.config.configFileDependencies.map((f) => path.basename(f));
    assert.ok(deps.includes('plugin.mjs'), deps.join(', '));
    assert.deepEqual(deps.filter((f) => ['poses.mjs', 'kit.mjs', 'entries.mjs', 'stage.mjs'].includes(f)), []);
  } finally {
    await config.close();
  }
});

// The studio writes poses.mjs and bakes: another site open in the owner's browser must not.
test('a write from another site, or one that isn\'t JSON, is refused', async () => {
  const was = await readFile(poses, 'utf8');
  const elsewhere = await fetch(`${base}/api/save`, { method: 'POST', headers: { 'content-type': 'application/json', origin: 'https://example.com' }, body: JSON.stringify({ id: 'barbell_bench_press', tuned: { 'grip.width': 0.5 } }) });
  assert.equal(elsewhere.status, 403);
  const form = await fetch(`${base}/api/note`, { method: 'POST', headers: { 'content-type': 'text/plain' }, body: JSON.stringify({ item: 'barbell_row', text: 'x' }) });
  assert.equal(form.status, 415);
  assert.equal(await readFile(poses, 'utf8'), was);
  // The studio's own page may.
  const own = await fetch(`${base}/api/note`, { method: 'POST', headers: { 'content-type': 'application/json', origin: base }, body: JSON.stringify({ item: 'barbell_row', text: 'from the page' }) });
  assert.equal(own.status, 200);
});
