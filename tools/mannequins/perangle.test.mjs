// Per-angle tuning (README, Adjustable-bench poses): a setting may be `Per angle`, its values saved
// keyed by angle in `bench.perAngle`, and the angles between the tuned ones interpolate.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { pathToFileURL } from 'node:url';
import { POSES } from './poses.mjs';
import { FIXTURE } from './adjustable.fixture.mjs';
import { prepare } from './entries.mjs';
import { perAngleValue, atAngle, applyTuned, tunables, tunedAngles } from './tune.mjs';
import { patchPoses } from './patch.mjs';
import { checkPose } from './checks.mjs';
import { scratch } from './tmp.mjs';

const ID = 'db_incline_press';
const withPer = (perAngle) => ({ fixture: FIXTURE, t: { like: 'fixture', bench: { perAngle } } });

test('between two tuned angles a value is interpolated, and beyond them it holds the nearest', () => {
  const tuned = { 15: 0.2, 45: 0.5, 75: 0.3 };
  assert.equal(perAngleValue(tuned, 15), 0.2);
  assert.ok(Math.abs(perAngleValue(tuned, 30) - 0.35) < 1e-9);
  assert.ok(Math.abs(perAngleValue(tuned, 60) - 0.4) < 1e-9);
  assert.equal(perAngleValue(tuned, 0), 0.2);
  assert.equal(perAngleValue(tuned, 75), 0.3);
  assert.equal(perAngleValue({ 30: 0.7 }, 60), 0.7);
});

test('an entry is posed with each per-angle setting at its value for the angle, and the rest the same at every angle', () => {
  const poses = withPer({ 'feet.z': { 0: 0.1, 60: 0.7 }, 'bottom.chest.0': { 30: 0.2 } });
  const at = (a) => prepare('t', poses, a);
  assert.equal(at(0).feet.z, 0.1);
  assert.ok(Math.abs(at(30).feet.z - 0.4) < 1e-9);
  assert.equal(at(75).feet.z, 0.7);
  // A point's one number is per angle, its other stays.
  assert.equal(at(0).bottom.chest[0], 0.2);
  assert.equal(at(0).bottom.chest[1], FIXTURE.bottom.chest[1]);
  // What isn't per angle is untouched.
  assert.equal(at(45).grip.width, FIXTURE.grip.width);
});

test('each baked angle gets its own value, and the pose still passes its checks', async () => {
  const poses = withPer({ 'feet.z': { 0: 0.2, 75: 0.24 } });
  for (const a of [0, 45, 75]) {
    const { checks } = await checkPose('t', { poses, angle: a });
    assert.ok(Object.values(checks).every((c) => c.ok), `${a}°: ${JSON.stringify(checks)}`);
  }
});

test('Tune applies tuned per-angle settings over the entry\'s, and says which angles are tuned', () => {
  const entry = prepare(ID);
  const tuned = { '@angles': { 'feet.z': { 15: 0.2, 60: 0.35 }, 'body.sit': { 0: 0.15 } } };
  const now = applyTuned(entry, tuned);
  assert.deepEqual(now.bench.perAngle['feet.z'], { 15: 0.2, 60: 0.35 });
  assert.equal(atAngle(now, 15).feet.z, 0.2);
  assert.equal(atAngle(now, 15).bench.angle, 15);
  assert.equal(atAngle(entry, 15).feet.z, entry.feet.z);
  assert.deepEqual(tunedAngles(now), [0, 15, 60]);
  // The entry's own: the arms lock out vertically at every angle.
  assert.deepEqual(tunedAngles(entry), [0, 15, 30, 45, 60, 75, 85]);
  assert.ok(!tunables(now).some((t) => t.path.startsWith('bench')));
});

test('a save writes per-angle values into the entry, keeping its comments', async () => {
  const real = await readFile(new URL('./poses.mjs', import.meta.url), 'utf8');
  const out = patchPoses(real, ID, { '@angles': { 'feet.z': { 0: 0.22, 75: 0.3 }, 'bottom.chest.0': { 30: 0.5 } } });
  assert.ok(out.includes('The grip turns'), 'comments kept');
  const dir = await scratch('poses');
  await writeFile(path.join(dir, 'poses.mjs'), out);
  const { POSES: patched } = await import(pathToFileURL(path.join(dir, 'poses.mjs')).href);
  assert.deepEqual(patched[ID].bench.perAngle, { 'feet.z': { 0: 0.22, 75: 0.3 }, 'bottom.chest.0': { 30: 0.5 } });
  assert.equal(patched[ID].bench.typical, 45);
});
