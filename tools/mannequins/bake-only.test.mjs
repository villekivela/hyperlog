// A save bakes only the poses it changed (#191), and what it leaves must be what a full bake leaves,
// byte for byte. Here on a few poses, so a full bake to compare with takes seconds: a pose and one
// that takes it by `like:`, sharing a kit, and an adjustable bench with its clip per angle.
import { test, before } from 'node:test';
import assert from 'node:assert/strict';
import { cp, readFile, readdir, rm, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { bakeAll, bakeOnly } from './bake.mjs';
import { POSES } from './poses.mjs';
import { scratch as makeScratch } from './tmp.mjs';

const pick = (...ids) => Object.fromEntries(ids.map((id) => [id, POSES[id]]));
const POSED = pick('barbell_squat', 'back_squat_high_bar', 'db_incline_press');

/** `poses` with `entry` merged into pose `id`. */
const tuned = (poses, id, entry) => ({ ...poses, [id]: { ...poses[id], ...entry } });
/** Lowers the squat's framing and its top a little: what a save does to a pose. */
const lowered = (poses) => tuned(poses, 'barbell_squat', {
  camera: { ...POSES.barbell_squat.camera, azimuth: 50 },
  top: { ...POSES.barbell_squat.top, rotate: (POSES.barbell_squat.top.rotate ?? 0) + 0.5 },
});

const scratch = () => makeScratch('bake-only');
let baked;
before(async () => {
  baked = await scratch();
  await bakeAll(baked, { poses: POSED });
});

/** A copy of the bake of `POSED`, to save into. */
async function copy() {
  const dir = await scratch();
  await cp(baked, dir, { recursive: true });
  return dir;
}

/** Fails unless `dir` holds byte for byte what `fresh` does. */
async function assertSame(dir, fresh) {
  const files = (await readdir(fresh)).sort();
  assert.deepEqual((await readdir(dir)).sort(), files);
  for (const f of files) assert.ok((await readFile(path.join(dir, f))).equals(await readFile(path.join(fresh, f))), `${f} isn't what a full bake writes`);
}

/** A full bake of `poses`, in a directory of its own. */
async function fullBake(poses) {
  const dir = await scratch();
  await bakeAll(dir, { poses });
  return dir;
}

const SQUATS = ['barbell_squat', 'back_squat_high_bar'];
let loweredBake;
/** A full bake of the squat lowered, the one the tests that save it compare with. */
const fullBakeLowered = () => (loweredBake ??= fullBake(lowered(POSED)));

test('a save leaves what a full bake leaves, a pose that takes it by like: included', async () => {
  const dir = await copy();
  await bakeOnly(SQUATS, dir, { poses: lowered(POSED) });
  await assertSame(dir, await fullBakeLowered());
});

test('a save bakes only the poses it is given, and says which files it wrote', async () => {
  const dir = await copy();
  // The bench's hinge moved too: if its clips were baked, they would change. Its per-angle settings
  // keep most of its pose from reaching them, so it is the machine that changes.
  const poses = tuned(lowered(POSED), 'db_incline_press', { kit: { ...POSES.db_incline_press.kit, bench: { hinge: -0.1 } } });
  const held = await readFile(path.join(dir, 'db_incline_press_30.glb'));
  const written = await bakeOnly(SQUATS, dir, { poses });
  assert.deepEqual(written.sort(), ['back_squat_high_bar.glb', 'barbell_squat.glb', 'mannequins.json']);
  assert.ok((await readFile(path.join(dir, 'db_incline_press_30.glb'))).equals(held));
});

test('a save of nothing new writes nothing, a clip per bench angle included', async () => {
  assert.deepEqual(await bakeOnly(['db_incline_press'], await copy(), { poses: POSED }), []);
});

test('a save that changes a shared kit rewrites it, and moves the clips that read it without baking them', async () => {
  // The bench press's bench drops, and the close grip's stays as it was.
  const pinned = { ...POSES.barbell_bench_press.kit, bench: { height: 0.457 } };
  const poses = { ...pick('barbell_bench_press', 'close_grip_bench_press'), close_grip_bench_press: { ...POSES.close_grip_bench_press, kit: pinned } };
  const grown = tuned(poses, 'barbell_bench_press', { kit: { ...POSES.barbell_bench_press.kit, bench: { height: 0.45 } } });
  const dir = await fullBake(poses);
  const written = await bakeOnly(['barbell_bench_press'], dir, { poses: grown });
  assert.deepEqual(written.sort(), ['barbell_bench_press.glb', 'close_grip_bench_press.glb', 'kit_bench.bin']);
  await assertSame(dir, await fullBake(grown));
});

test('a pose that fails its checks stops a save before anything is written', async () => {
  const dir = await copy();
  const poses = tuned(POSED, 'barbell_squat', { grip: { ...POSES.barbell_squat.grip, width: 1.4 } });
  await assert.rejects(bakeOnly(['barbell_squat'], dir, { poses }), /barbell_squat fails its checks/);
  await assertSame(dir, baked);
});

test('a pose not baked yet makes a save a full bake', async () => {
  const dir = await copy();
  const file = path.join(dir, 'mannequins.json');
  const { clips: { back_squat_high_bar, ...rest } } = JSON.parse(await readFile(file, 'utf8'));
  await writeFile(file, JSON.stringify({ clips: rest }, null, 2) + '\n');
  await rm(path.join(dir, back_squat_high_bar.clip));
  await bakeOnly(['barbell_squat'], dir, { poses: lowered(POSED) });
  await assertSame(dir, await fullBakeLowered());
});

test('a mannequin that has changed since the clips were baked makes a save a full bake', async () => {
  const dir = await copy();
  const shared = path.join(dir, 'mannequin.bin');
  const bytes = await readFile(shared);
  bytes[0] ^= 0xff;
  await writeFile(shared, bytes);
  await bakeOnly(SQUATS, dir, { poses: lowered(POSED) });
  await assertSame(dir, await fullBakeLowered());
});
