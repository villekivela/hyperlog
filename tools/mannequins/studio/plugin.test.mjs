// `npm run studio` serves every exercise and kit with its status, poses them with the tool's own
// modules, and writes what the owner does into the repo's files: a save into poses.mjs (its
// comments kept) and a re-bake, an OK into the batch's file, a note into notes.md. Here against
// copies of those files, with a bake that only says it ran, and a technique video fetched from
// nowhere.
import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createServer } from 'vite';
import { studio, VIDEOS } from './plugin.mjs';
import { KITS } from '../kit.mjs';
import { scratch } from '../tmp.mjs';

const TOOL = path.dirname(path.dirname(fileURLToPath(import.meta.url)));
const LIBRARY = JSON.parse(await readFile(path.join(TOOL, '..', '..', 'app', 'src', 'main', 'assets', 'exercises.json'), 'utf8'));
const ORIGINAL = await readFile(path.join(TOOL, 'poses.mjs'), 'utf8');

let server;
let base;
let poses;
let review;
const baked = [];
let bakeFails = false;
let videos;
const fetched = [];
let offline = false;
/** A video's bytes as the fake yt-dlp writes them. */
const BYTES = Buffer.from('not really an mp4, but bytes to serve');

before(async () => {
  const dir = await scratch('studio');
  poses = path.join(dir, 'poses.mjs');
  review = await scratch('studio-review');
  videos = await scratch('studio-videos');
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
      videos,
      fetchVideo: async (id, dir) => {
        fetched.push(id);
        await new Promise((r) => setTimeout(r, 50));
        if (offline) throw new Error('Unable to download webpage: <urlopen error [Errno -3] Temporary failure in name resolution>');
        await writeFile(path.join(dir, `${id}.mp4`), BYTES);
        return { file: `${id}.mp4`, fps: 30, duration: 600 };
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
  assert.deepEqual(Object.keys(squat.checks), ['hands', 'feet', 'equipment', 'joints', 'trace', 'contact', 'linkage']);
  assert.equal(squat.clip.file, '/clips/barbell_squat.glb');
  assert.deepEqual(squat.clip.camera, { target: [0, 0.87, 0], halfHeight: 1.02, azimuth: 62, elevation: 8 });
  const glb = await fetch(base + squat.clip.file);
  assert.equal(glb.status, 200);
  assert.equal(Buffer.from(await glb.arrayBuffer()).subarray(0, 4).toString(), 'glTF');
  // An adjustable bench comes at its typical angle, with a clip for each angle.
  const press = await get('/api/item/exercise/db_incline_press');
  assert.equal(press.entry.bench.angle, 45);
  assert.deepEqual(Object.keys(press.clip.angles), ['0', '15', '30', '45', '60', '75', '85']);
  // A kit comes as the exercise that moves most of it sets it up, the first of those by id.
  const kit = await get('/api/item/kit/leg_extension');
  assert.equal(kit.uses, 'leg_extension');
  assert.equal(kit.entry.motion, 'knee_lever');
  // So an adjustable bench's back and a twin column's two stacks can each be scrubbed (#159).
  const bench = await get('/api/item/kit/bench');
  assert.equal(typeof bench.entry.bench?.angle, 'number', `the bench as in ${bench.uses}, not adjustable`);
  const column = await get('/api/item/kit/cable_column');
  assert.equal(column.entry.kit.cable_column.twin, true, `the cable column as in ${column.uses}, one column`);
  // Posed, the T-bar row machine comes as its exercise sets it up (#188).
  const tBar = await get('/api/item/kit/t_bar_row_machine');
  assert.equal(tBar.uses, 'chest_supported_t_bar_row');
  // A kit no pose uses yet comes alone, if its recipe can stand without one (#176).
  const smith = await get('/api/item/kit/smith_machine');
  assert.equal(smith.uses, null);
  assert.equal(smith.status, 'unused');
  assert.deepEqual(smith.entry.kit, { smith_machine: {} });
  assert.equal(smith.entry.alone, true);
  // The shoulder press, unposed, comes alone too (#225).
  const shoulder = await get('/api/item/kit/shoulder_press');
  assert.equal(shoulder.uses, null);
  assert.equal(shoulder.entry.alone, true);
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
test('the studio\'s config reaches neither poses.mjs, kit.mjs nor the recipes and parts, which change while it runs', async () => {
  const config = await createServer({ configFile: path.join(TOOL, 'studio', 'vite.config.mjs'), logLevel: 'silent', server: { port: 0, host: '127.0.0.1' } });
  try {
    const deps = config.config.configFileDependencies.map((f) => path.basename(f));
    assert.ok(deps.includes('plugin.mjs'), deps.join(', '));
    const changing = ['poses.mjs', 'kit.mjs', 'recipe.mjs', 'parts.mjs', 'entries.mjs', 'stage.mjs'];
    assert.deepEqual(config.config.configFileDependencies.filter((f) => changing.includes(path.basename(f)) || f.includes(`${path.sep}recipes${path.sep}`)), []);
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

test('an exercise with a video link comes with it at its timestamp, fetched once into a cache outside the repo', async () => {
  const repo = path.join(TOOL, '..', '..');
  assert.match(path.relative(repo, VIDEOS), /^\.\.\//, VIDEOS);
  const item = await get('/api/item/exercise/1_arm_cable_lat_pull_around');
  assert.deepEqual(item.video, { link: 'https://youtu.be/jLvqKgW-_G8?t=278', start: 278, loop: null });
  // Two asking at once, and another exercise from the same video, fetch it once.
  const [one, two] = await Promise.all([get('/api/video/1_arm_cable_lat_pull_around'), get('/api/video/1_arm_cable_lat_pull_around')]);
  const kroc = await get('/api/video/1_arm_db_kroc_row');
  assert.deepEqual(fetched, ['jLvqKgW-_G8']);
  assert.deepEqual(one, { src: '/videos/jLvqKgW-_G8.mp4', fps: 30, duration: 600 });
  assert.deepEqual(two, one);
  assert.deepEqual(kroc, one);
  assert.equal((await get('/api/item/exercise/1_arm_db_kroc_row')).video.start, 477);
  // Served whole, and a range of it, which seeking asks for.
  const whole = await fetch(base + one.src);
  assert.equal(whole.headers.get('content-type'), 'video/mp4');
  assert.deepEqual(Buffer.from(await whole.arrayBuffer()), BYTES);
  const part = await fetch(base + one.src, { headers: { range: 'bytes=4-9' } });
  assert.equal(part.status, 206);
  assert.equal(part.headers.get('content-range'), `bytes 4-9/${BYTES.length}`);
  assert.equal(Buffer.from(await part.arrayBuffer()).toString(), 'really');
  // The last bytes, more than there are, is all of them; a range past the end is refused.
  const tail = await fetch(base + one.src, { headers: { range: 'bytes=-1000' } });
  assert.equal(tail.status, 206);
  assert.equal(tail.headers.get('content-range'), `bytes 0-${BYTES.length - 1}/${BYTES.length}`);
  assert.deepEqual(Buffer.from(await tail.arrayBuffer()), BYTES);
  assert.equal((await fetch(base + one.src, { headers: { range: 'bytes=1000-' } })).status, 416);
  // Only read.
  assert.notEqual((await fetch(base + one.src, { method: 'POST' })).status, 200);
  assert.deepEqual(JSON.parse(await readFile(path.join(videos, 'jLvqKgW-_G8.json'), 'utf8')), { file: 'jLvqKgW-_G8.mp4', fps: 30, duration: 600 });
  // A video fetched before, by a studio since stopped, is taken from the cache.
  await writeFile(path.join(videos, 'UGwmUJkTIx8.webm'), BYTES);
  await writeFile(path.join(videos, 'UGwmUJkTIx8.json'), JSON.stringify({ file: 'UGwmUJkTIx8.webm', fps: 25, duration: 40 }));
  assert.deepEqual(await get('/api/video/barbell_squat'), { src: '/videos/UGwmUJkTIx8.webm', fps: 25, duration: 40 });
  assert.deepEqual(fetched, ['jLvqKgW-_G8']);
});

test('without a link, or offline, the video says so, and a later try fetches again', async () => {
  const bench = await get('/api/item/exercise/barbell_bench_press');
  assert.equal(bench.video, null);
  let res = await fetch(`${base}/api/video/barbell_bench_press`);
  assert.equal(res.status, 422);
  assert.match((await res.json()).error, /no video link/);
  offline = true;
  try {
    res = await fetch(`${base}/api/video/triceps_pressdown`);
    assert.equal(res.status, 422);
    assert.match((await res.json()).error, /Couldn't fetch the video: .*name resolution/);
  } finally {
    offline = false;
  }
  assert.deepEqual(await get('/api/video/triceps_pressdown'), { src: '/videos/B5S2mbg0g5c.mp4', fps: 30, duration: 600 });
  assert.deepEqual(fetched.filter((id) => id === 'B5S2mbg0g5c').length, 2);
});

test('the loop is saved per exercise beside the OKs and notes, and comes back with it', async () => {
  let res = await post('/api/loop', { id: 'barbell_squat', loop: { a: 12.4, b: 13.9, c: 15.2 } });
  assert.equal(res.status, 200, await res.clone().text());
  res = await post('/api/loop', { id: 'triceps_pressdown', loop: { a: 3.1, b: null, c: null } });
  assert.equal(res.status, 200);
  assert.deepEqual((await get('/api/item/exercise/barbell_squat')).video.loop, { a: 12.4, b: 13.9, c: 15.2 });
  assert.deepEqual((await get('/api/item/exercise/triceps_pressdown')).video.loop, { a: 3.1, b: null, c: null });
  assert.deepEqual(JSON.parse(await readFile(path.join(review, 'loops.json'), 'utf8')), {
    barbell_squat: { a: 12.4, b: 13.9, c: 15.2 }, triceps_pressdown: { a: 3.1, b: null, c: null },
  });
  // Two set at once both land: marks pressed in turn.
  await Promise.all([
    post('/api/loop', { id: 'leg_extension', loop: { a: 202, b: null, c: null } }),
    post('/api/loop', { id: 'leg_press', loop: { a: 241, b: 242, c: 243 } }),
    post('/api/loop', { id: 'cable_flye', loop: { a: 169, b: 170, c: 171 } }),
  ]);
  const loops = JSON.parse(await readFile(path.join(review, 'loops.json'), 'utf8'));
  assert.deepEqual(Object.keys(loops), ['barbell_squat', 'cable_flye', 'leg_extension', 'leg_press', 'triceps_pressdown']);
  // A loop saved with only A and B, before C, still loads.
  await writeFile(path.join(review, 'loops.json'), JSON.stringify({ ...loops, leg_press: { a: 241, b: 242 } }));
  assert.deepEqual((await get('/api/item/exercise/leg_press')).video.loop, { a: 241, b: 242, c: null });
  // Cleared, and refused for what isn't a time or an exercise with a video.
  res = await post('/api/loop', { id: 'triceps_pressdown', loop: null });
  assert.equal(res.status, 200);
  assert.equal((await get('/api/item/exercise/triceps_pressdown')).video.loop, null);
  assert.equal((await post('/api/loop', { id: 'barbell_squat', loop: { a: 1, b: 2, c: 'top' } })).status, 422);
  assert.equal((await post('/api/loop', { id: 'barbell_squat', loop: { a: 'top', b: 2 } })).status, 422);
  assert.equal((await post('/api/loop', { id: 'barbell_bench_press', loop: { a: 1, b: 2 } })).status, 422);
});
