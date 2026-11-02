// The automatic checks run on every frame of every pose, and a failing pose won't bake.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, readdir } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { checkPose, CHECKS } from './checks.mjs';
import { bakeAll } from './bake.mjs';
import { POSES } from './poses.mjs';

const failing = (report) => CHECKS.filter((c) => !report.checks[c].ok);

for (const id of Object.keys(POSES)) {
  test(`${id} passes every check on every frame`, async () => {
    const report = await checkPose(id);
    assert.deepEqual(failing(report), [], JSON.stringify(report.checks, null, 1));
  });
}

// Each pose here is a good one bent to break one thing.
const BROKEN = {
  // The hands grip the bar wider than the arms reach.
  hands: { like: 'barbell_squat', grip: { width: 0.9 } },
  // The feet are planted further away than the legs reach.
  feet: { like: 'barbell_bench_press', feet: { z: 1.7 } },
  // The hips sink into the bench.
  equipment: { like: 'barbell_bench_press', body: { hip: [0.36, 0.3] } },
  // The back sinks into the floor.
  floor: { like: 'floor_press', body: { hip: [0.03, 0.28] } },
  // The knees turn in and back.
  joints: { like: 'barbell_squat', feet: { kneesOut: -4 } },
  // A trace whose trunk is 30° more upright than the balanced squat can be.
  trace: { like: 'barbell_squat', trace: { angles: { trunk: Array(31).fill(-10) } } },
};

// The floor is a solid part like any other: the equipment check fails a body that sinks into it.
const CHECKED = { floor: 'equipment' };

for (const [broken, entry] of Object.entries(BROKEN)) {
  const check = CHECKED[broken] ?? broken;
  test(`a pose that breaks ${broken} fails the ${check} check`, async () => {
    const report = await checkPose('broken', { poses: { ...POSES, broken: entry } });
    assert.ok(failing(report).includes(check), `${check}: ${JSON.stringify(report.checks[check])}`);
    if (broken === 'floor') assert.match(report.checks.equipment.found, /into the floor/);
  });
}

test('a pose that fails a check won\'t bake, and nothing is written', async () => {
  const out = await mkdtemp(path.join(tmpdir(), 'mannequins-'));
  const poses = { ...POSES, broken: BROKEN.hands };
  await assert.rejects(bakeAll(out, { poses }), /broken fails its checks: hands/);
  assert.deepEqual(await readdir(out), []);
});
