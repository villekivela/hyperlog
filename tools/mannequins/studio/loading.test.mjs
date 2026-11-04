// The studio loads in seconds (#187): the list never waits for a check, and opening an item starts
// them, that item first and the rest one at a time behind it, so the server keeps answering. Here
// against a check the test holds and lets go.
import { test, afterEach } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, readFile, stat, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createServer } from 'vite';
import { studio } from './plugin.mjs';
import { POSES } from '../poses.mjs';

const TOOL = path.dirname(path.dirname(fileURLToPath(import.meta.url)));
const POSED = Object.keys(POSES);
const PASSES = { hands: { ok: true, frame: 0, found: 'nothing to check' } };
const FAILS = { hands: { ok: false, frame: 3, found: 'the l hand 9.0 cm from its grip' } };

let server;
afterEach(async () => {
  await server?.close();
  server = null;
});

/** A studio on copies of the repo's files, with the pose entries `added`, checking with `check(id)`. */
async function serve(check, { bake = async () => {}, behind = async () => false, added = '' } = {}) {
  const poses = path.join(await mkdtemp(path.join(tmpdir(), 'studio-')), 'poses.mjs');
  await writeFile(poses, (await readFile(path.join(TOOL, 'poses.mjs'), 'utf8')).replace(/(\n  barbell_rdl: \{)/, `\n${added}$1`));
  const plugin = studio({ posesFile: poses, reviewDir: await mkdtemp(path.join(tmpdir(), 'studio-review-')), bake, behind, check });
  server = await createServer({
    configFile: false,
    root: TOOL,
    logLevel: 'silent',
    server: { port: 0, host: '127.0.0.1' },
    plugins: [plugin],
  });
  await server.listen();
  const base = server.resolvedUrls.local[0].replace(/\/$/, '');
  return {
    plugin,
    poses,
    get: async (url) => (await fetch(base + url)).json(),
    post: async (url, body) => (await fetch(base + url, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(body) })).json(),
  };
}

/** A check that holds each pose until `next()` lets the oldest go, and says which it began, in order. */
function held() {
  const began = [];
  const holding = [];
  const check = (id) => new Promise((resolve) => {
    began.push(id);
    holding.push(() => resolve(PASSES));
  });
  return { began, holding, check, next: () => holding.shift()?.() };
}

const until = async (done) => {
  for (let i = 0; i < 1000 && !done(); i++) await new Promise((r) => setTimeout(r, 5));
  assert.ok(done(), 'never happened');
};

const rowsOf = ({ exercises }) => exercises.flatMap((g) => g.items);

// With the timeout a list that waits for a held check fails, not hangs.
test('the first list answers while no check has finished, each posed row checking', { timeout: 30000 }, async () => {
  const { check, began } = held();
  const { get } = await serve(check);
  const items = await get('/api/items');
  assert.deepEqual(began, []);
  const posed = rowsOf(items).filter((r) => r.status !== 'no pose');
  assert.equal(posed.length, POSED.length);
  assert.ok(posed.every((r) => r.checking), 'a posed row isn\'t marked as checking');
});

test('opening an item checks it first, then the rest one at a time', async () => {
  const hold = held();
  const { get } = await serve(hold.check);
  await get('/api/items');
  const opening = get('/api/item/exercise/deadlift');
  await until(() => hold.began.length === 1);
  assert.equal(hold.began[0], 'deadlift');
  hold.next();
  assert.equal((await opening).checks.hands.ok, true);
  for (let n = 2; n <= POSED.length; n++) {
    await until(() => hold.began.length === n);
    // One at a time: the next hasn't begun while this one is held.
    assert.equal(hold.holding.length, 1);
    hold.next();
  }
  assert.deepEqual([...hold.began].sort(), [...POSED].sort());
});

test('opening another item moves it to the front of those waiting', async () => {
  const hold = held();
  const { get } = await serve(hold.check);
  const first = get('/api/item/exercise/deadlift');
  await until(() => hold.began.length === 1);
  hold.next();
  await first;
  await until(() => hold.began.length === 2);
  const last = POSED.at(-1);
  const second = get(`/api/item/exercise/${last}`);
  // The studio takes the request while a pose in the background is under way.
  await new Promise((r) => setTimeout(r, 200));
  hold.next();
  await until(() => hold.began.length === 3);
  assert.equal(hold.began[2], last);
  hold.next();
  await second;
});

test('after a save only the saved pose is checked again', async () => {
  const began = [];
  const { get, post } = await serve(async (id) => { began.push(id); return PASSES; });
  await get('/api/items?wait=1');
  began.length = 0;
  await post('/api/save', { id: 'leg_press', tuned: { 'camera.azimuth': 12 } });
  await get('/api/items?wait=1');
  assert.deepEqual(began, ['leg_press']);
});

test('after a save the poses that take it by like: are checked again too, however far along', async () => {
  const began = [];
  const { get, post } = await serve(async (id) => { began.push(id); return PASSES; }, { added: "  paused_front_squat: { like: 'front_squat' }," });
  await get('/api/items?wait=1');
  began.length = 0;
  await post('/api/save', { id: 'barbell_squat', tuned: { 'camera.azimuth': 12 } });
  await get('/api/items?wait=1');
  assert.equal(began[0], 'barbell_squat');
  for (const id of ['back_squat_high_bar', 'back_squat_low_bar', 'front_squat', 'paused_front_squat']) assert.ok(began.includes(id), `${id} wasn't checked again`);
  assert.ok(!began.includes('deadlift'), 'a pose that takes nothing from it was checked again');
});

test('a save bakes the pose it saved and the poses that take it by like:', async () => {
  const baked = [];
  const { post } = await serve(async () => PASSES, { bake: async (ids) => { baked.push(ids); } });
  await post('/api/save', { id: 'leg_press', tuned: { 'camera.azimuth': 12 } });
  await post('/api/save', { id: 'deadlift', tuned: { 'camera.azimuth': 12 } });
  assert.deepEqual(baked, [['leg_press'], ['deadlift', 'deficit_deadlift']]);
});

test('a change to the tool\'s code checks every pose again', async () => {
  const began = [];
  const { plugin, get } = await serve(async (id) => { began.push(id); return PASSES; });
  await get('/api/items?wait=1');
  began.length = 0;
  await plugin.hotUpdate.call({ environment: { name: 'client' } }, { file: path.join(TOOL, 'motions.mjs'), read: async () => '' });
  await get('/api/items?wait=1');
  assert.deepEqual([...began].sort(), [...POSED].sort());
});

test('a save with the clips behind bakes every clip, and then only what it changed', async () => {
  const baked = [];
  let behind = true;
  const { post } = await serve(async () => PASSES, {
    behind: async () => behind,
    bake: async (ids) => {
      baked.push(ids);
      behind = false;
    },
  });
  await post('/api/save', { id: 'leg_press', tuned: { 'camera.azimuth': 12 } });
  await post('/api/save', { id: 'leg_press', tuned: { 'camera.azimuth': 13 } });
  assert.deepEqual(baked, [undefined, ['leg_press']]);
});

test('a save refused because the tool doesn\'t load leaves poses.mjs as it was', async () => {
  const baked = [];
  const { post, poses } = await serve(async () => PASSES, { added: '  half_typed: {,', bake: async (ids) => { baked.push(ids); } });
  const was = await readFile(poses, 'utf8');
  const res = await post('/api/save', { id: 'leg_press', tuned: { 'camera.azimuth': 12 } });
  assert.ok(res.error, JSON.stringify(res));
  assert.equal(await readFile(poses, 'utf8'), was);
  assert.deepEqual(baked, []);
});

test('a save whose bake fails leaves poses.mjs dated as it was, so the clips aren\'t behind for it', async () => {
  const { post, poses } = await serve(async () => PASSES, { bake: async () => { throw new Error('a pose fails its checks'); } });
  const { mtimeMs } = await stat(poses);
  await new Promise((r) => setTimeout(r, 20));
  await post('/api/save', { id: 'leg_press', tuned: { 'camera.azimuth': 12 } });
  // To the millisecond, as a date holds it.
  assert.ok(Math.abs((await stat(poses)).mtimeMs - mtimeMs) <= 1, 'poses.mjs was dated afresh');
});

test('after a bake that fails the pose is checked first again, as it was', async () => {
  const began = [];
  let at = null;
  const { get, post } = await serve(async (id) => { began.push(id); return PASSES; }, {
    bake: async () => {
      // Long enough for the saved pose to have been checked, and others after it.
      await new Promise((r) => setTimeout(r, 200));
      at = began.length;
      throw new Error('a pose fails its checks');
    },
  });
  await get('/api/item/exercise/deadlift');
  await until(() => began.length === POSED.length);
  const id = POSED.at(-1);
  await post('/api/save', { id, tuned: { 'camera.azimuth': 12 } });
  await until(() => at !== null && began.length > at);
  assert.equal(began[at], id);
});

test('an item that can\'t be opened still starts the checks', async () => {
  const began = [];
  const { get } = await serve(async (id) => { began.push(id); return PASSES; });
  await get('/api/item/kit/no_such_kit');
  await until(() => began.length > 0);
});

test('a bake that adds a seat doesn\'t reload the page, which would lose the save', async () => {
  const update = studio().hotUpdate.call({ environment: { name: 'client' } }, { file: path.join(TOOL, 'seats.json'), read: async () => '{}' });
  assert.deepEqual(await update, []);
});

test('the list fills in as the checks finish, and the state counts them', async () => {
  const hold = held();
  const { get } = await serve(async (id) => { await hold.check(id); return id === 'deadlift' ? FAILS : PASSES; });
  const opening = get('/api/item/exercise/deadlift');
  await until(() => hold.began.length === 1);
  assert.deepEqual((await get('/api/state')).checks, { done: 0, total: POSED.length });
  hold.next();
  await opening;
  await until(() => hold.began.length === 2);
  assert.deepEqual((await get('/api/state')).checks, { done: 1, total: POSED.length });
  const rows = rowsOf(await get('/api/items'));
  const row = (id) => rows.find((r) => r.id === id);
  assert.equal(row('deadlift').status, 'failing');
  assert.equal(row('deadlift').checking, undefined);
  assert.equal(row(hold.began[1]).checking, true);
});

const spin = (ms) => { const end = performance.now() + ms; while (performance.now() < end); };

test('an item answers as soon as its checks are done, not behind the poses after it', async () => {
  let spun = 0;
  const { get } = await serve(async (id) => {
    if (id !== 'deadlift' && spun < 6) {
      spin(200);
      spun++;
    }
    return PASSES;
  });
  await get('/api/item/exercise/deadlift');
  assert.ok(spun <= 2, `${spun} poses ran before it answered`);
});

test('a list made while the checks run waits for one pose at most', async () => {
  let began = 0;
  const { get } = await serve(async () => { began++; spin(60); return PASSES; });
  await get('/api/item/exercise/deadlift');
  const before = began;
  await get('/api/items');
  assert.ok(began - before <= 1, `${began - before} poses began while the list was made`);
});

test('the studio answers between checks that hold its one thread', async () => {
  let done = 0;
  const { get } = await serve(async () => { spin(40); done++; return PASSES; });
  await get('/api/item/exercise/deadlift');
  const { checks } = await get('/api/state');
  assert.ok(done < POSED.length, `all ${done} checks ran before the studio answered`);
  assert.ok(checks.done < checks.total, JSON.stringify(checks));
});
