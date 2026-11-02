// An adjustable-bench pose is written in the backrest's frame (README, Adjustable-bench poses), so
// one set of numbers holds from 0° to 75°: the hips at the seat-back corner, the chest touch point
// and the press path measured from the torso, the head along the pad.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import * as THREE from 'three';
import { prepare, BENCH_ANGLES } from './entries.mjs';
import { buildStage } from './stage.mjs';
import { benchHinge } from './kit.mjs';

const ID = 'db_incline_press';
const rad = (d) => (d * Math.PI) / 180;

/** The posed stage at `angle`, at the top (0) or the bottom (1) of the rep. */
async function at(angle, d) {
  const entry = prepare(ID, undefined, angle);
  const stage = await buildStage(entry);
  return { entry, P: stage.pose(d) };
}

/** The backrest's face at `angle`: the corner, and the unit vectors up the pad and out of it ([y, z]). */
function backrest(entry, angle) {
  const { height, z } = benchHinge(entry.kit.bench);
  return { corner: [height, z], up: [Math.sin(rad(angle)), -Math.cos(rad(angle))], out: [Math.cos(rad(angle)), Math.sin(rad(angle))] };
}
const dot = (a, b) => a[0] * b[0] + a[1] * b[1];

test('the hips sit the same distance off the backrest, and against the seat, at every angle', async () => {
  const off = [];
  for (const a of BENCH_ANGLES) {
    const { entry, P } = await at(a, 0);
    const { corner, out } = backrest(entry, a);
    off.push(dot([P.hipC.y - corner[0], P.hipC.z - corner[1]], out));
    assert.ok(P.hipC.y - corner[0] > 0.05, `${a}°: hips ${P.hipC.y - corner[0]} above the seat`);
  }
  for (const o of off) assert.ok(Math.abs(o - off[0]) < 0.002, `distance off the pad ${off.map((v) => v.toFixed(3))}`);
});

test('the trunk lies along the backrest at every angle', async () => {
  for (const a of BENCH_ANGLES) {
    const { entry, P } = await at(a, 0);
    const { up } = backrest(entry, a);
    assert.ok(Math.abs(P.u.y * up[0] + P.u.z * up[1] - Math.cos(rad(entry.body.lean ?? 0))) < 1e-6, `${a}°: trunk off the pad by more than its lean`);
  }
});

test('the hands touch the chest in the same place against the torso at every angle', async () => {
  const seen = [];
  for (const a of BENCH_ANGLES) {
    const { P } = await at(a, 1);
    const off = P.hand(1).sub(P.shoulders);
    const front = new THREE.Vector3(1, 0, 0).cross(P.u).normalize();
    seen.push([off.dot(front), off.dot(P.u)]);
  }
  for (const s of seen) assert.ok(Math.abs(s[0] - seen[0][0]) < 0.01 && Math.abs(s[1] - seen[0][1]) < 0.01, JSON.stringify(seen));
});

test('the arms lock out vertically over the shoulders at every angle (the entry\'s per-angle top.over)', async () => {
  const seen = [];
  for (const a of BENCH_ANGLES) {
    const { P } = await at(a, 0);
    const off = P.hand(1).sub(P.shoulders);
    seen.push([off.y, off.z]);
  }
  for (const s of seen) assert.ok(Math.abs(s[0] - 0.64) < 0.03 && Math.abs(s[1] - 0.09) < 0.04, JSON.stringify(seen));
});

test('the head lies along the pad, tilted by body.head', async () => {
  for (const a of BENCH_ANGLES) {
    const { entry, P } = await at(a, 0);
    const between = Math.acos(Math.min(1, P.headDir.dot(P.u))) * (180 / Math.PI);
    assert.ok(Math.abs(between - Math.abs(entry.body.head ?? 0)) < 0.1, `${a}°: head ${between}° off the trunk`);
  }
});
