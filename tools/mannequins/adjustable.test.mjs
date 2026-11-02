// Adjustable-bench poses (SPEC, Bench angle and the mannequin, #125): each is baked at 0° to 75° in
// 15° steps, with a typical angle in the index, and the back pad is a part of its own the phone
// turns to the exact angle.
import { test, before } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, readFile, readdir } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import * as THREE from 'three';
import { bakeAll } from './bake.mjs';
import { loadClip } from './glb.mjs';
import { benchAngles } from './entries.mjs';
import { checkPose, CHECKS } from './checks.mjs';
import { POSES } from './poses.mjs';

const ANGLES = [0, 15, 30, 45, 60, 75];
const ID = 'db_incline_press';
let out;
let index;
before(async () => {
  out = await mkdtemp(path.join(tmpdir(), 'mannequins-'));
  await bakeAll(out);
  index = JSON.parse(await readFile(path.join(out, 'mannequins.json'), 'utf8')).clips;
});

test('the library marks which exercises have an adjustable bench, and each is baked at six angles', () => {
  assert.deepEqual(benchAngles(ID), ANGLES);
  assert.equal(benchAngles('barbell_bench_press'), null);
  assert.equal(benchAngles('barbell_squat'), null);
});

test('an adjustable-bench pose bakes a clip per angle and a typical angle into the index', async () => {
  const { bench } = index[ID];
  assert.deepEqual(Object.keys(bench.clips), ANGLES.map(String));
  assert.ok(Number.isInteger(bench.typical) && bench.typical >= 0 && bench.typical <= 75, `typical ${bench.typical}`);
  assert.equal(bench.hinge.length, 2);
  for (const a of ANGLES) {
    assert.equal(bench.clips[a].clip, `${ID}_${a}.glb`);
    assert.equal(bench.clips[a].camera.halfHeight > 0, true);
  }
  // The entry's own clip is the one nearest the typical angle.
  const nearest = ANGLES.reduce((n, a) => (Math.abs(a - bench.typical) < Math.abs(n - bench.typical) ? a : n));
  assert.equal(index[ID].clip, bench.clips[nearest].clip);
  assert.deepEqual(index[ID].camera, bench.clips[nearest].camera);
  const files = (await readdir(out)).filter((f) => f.startsWith(ID));
  assert.deepEqual(files.sort(), ANGLES.map((a) => `${ID}_${a}.glb`));
});

test('each clip stands its bench back at its own angle, turning about the hinge the index gives', async () => {
  const { bench } = index[ID];
  for (const a of ANGLES) {
    const gltf = await loadClip(path.join(out, bench.clips[a].clip), [path.join(out, 'mannequin.bin')]);
    const back = gltf.scene.getObjectByName('bench_back');
    assert.ok(back, `${a}°: no bench_back`);
    const turned = new THREE.Euler().setFromQuaternion(back.quaternion);
    assert.ok(Math.abs((turned.x * 180) / Math.PI - a) < 0.01, `${a}°: turned ${turned.x}`);
    assert.ok(Math.abs(back.position.y - bench.hinge[0]) < 1e-4 && Math.abs(back.position.z - bench.hinge[1]) < 1e-4, `${a}°: hinge at ${back.position.toArray()}`);
  }
});

test('the pose passes every check at every angle', async () => {
  for (const a of ANGLES) {
    const { checks } = await checkPose(ID, { angle: a });
    const failing = CHECKS.filter((c) => !checks[c].ok).map((c) => `${c} ${JSON.stringify(checks[c])}`);
    assert.deepEqual(failing, [], `${a}°`);
  }
});

test('an adjustable-bench pose with no typical angle won\'t bake', async () => {
  const poses = { ...POSES, [ID]: { ...POSES[ID], bench: undefined } };
  await assert.rejects(bakeAll(await mkdtemp(path.join(tmpdir(), 'mannequins-')), { poses }), new RegExp(`${ID}: .*typical`));
});

test('the camera follows the bench angle: the upright 75° frames higher and wider than the lying 0°', () => {
  const { clips } = index[ID].bench;
  assert.ok(clips[75].camera.target[1] > clips[0].camera.target[1] + 0.2, 'looks higher at 75°');
  assert.ok(clips[75].camera.halfHeight > clips[0].camera.halfHeight, 'shows more at 75°');
  // Between the ends it moves steadily.
  const heights = ANGLES.map((a) => clips[a].camera.target[1]);
  assert.deepEqual(heights, [...heights].sort((a, b) => a - b));
});
