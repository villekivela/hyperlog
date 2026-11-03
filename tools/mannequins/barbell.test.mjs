// The barbell batch's pulls and bridges (owner, #127): a row's or a deadlift's bar runs straight up
// and down, and the bar's place at the bottom of the rep, and a bridge's back arch there, are
// numbers Tune can set.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { prepare } from './entries.mjs';
import { buildStage, FRAMES } from './stage.mjs';
import { between } from './rig.mjs';
import { POSES } from './poses.mjs';

for (const id of ['barbell_row', 'deficit_pendlay_row', 'deadlift', 'deficit_deadlift']) {
  test(`${id}'s bar runs straight up and down`, async () => {
    const stage = await buildStage(prepare(id));
    const bars = Array.from({ length: FRAMES }, (_, f) => stage.pose(f / (FRAMES - 1)).bar.clone());
    for (const b of bars) {
      assert.ok(Math.abs(b.z - bars[0].z) < 0.005 && Math.abs(b.x) < 0.005, `the bar wanders to z ${b.z.toFixed(3)} from ${bars[0].z.toFixed(3)}`);
    }
    assert.ok(bars[0].y - bars.at(-1).y > 0.15, 'the bar hardly moves');
  });
}

test('a deadlift\'s bar sits where bottom.bar puts it, on the floor', async () => {
  const entry = prepare('deadlift', { ...POSES, deadlift: { ...POSES.deadlift, bottom: { ...POSES.deadlift.bottom, bar: 0.24 } } });
  const P = (await buildStage(entry)).pose(1);
  assert.ok(Math.abs(P.bar.y - 0.24) < 0.005, `the bar at ${P.bar.y.toFixed(3)}`);
});

test('a bridge\'s bar sits where top.bar and bottom.bar put it', async () => {
  const thrust = POSES.barbell_hip_thrust;
  const poses = { ...POSES, barbell_hip_thrust: { ...thrust, top: { ...thrust.top, bar: [0.66, 0.05] }, bottom: { ...thrust.bottom, bar: [0.4, -0.02] } } };
  const stage = await buildStage(prepare('barbell_hip_thrust', poses));
  assert.deepEqual(stage.pose(0).bar.toArray().map((v) => Number(v.toFixed(3))), [0, 0.66, 0.05]);
  assert.deepEqual(stage.pose(1).bar.toArray().map((v) => Number(v.toFixed(3))), [0, 0.4, -0.02]);
});

test('a hip thrust arches its back at the bottom, its shoulders still on the bench', async () => {
  const thrust = POSES.barbell_hip_thrust;
  const shouldersAt = async (arch) => {
    const entry = { ...thrust, top: { ...thrust.top, arch: [0, 0, 0] }, bottom: { ...thrust.bottom, arch } };
    const stage = await buildStage(prepare('barbell_hip_thrust', { ...POSES, barbell_hip_thrust: entry }));
    const P = stage.pose(1);
    return { P, at: between(stage.rig, 'upperarm_l', 'upperarm_r').sub(P.hipC) };
  };
  const straight = await shouldersAt([0, 0, 0]);
  const arched = await shouldersAt([12, 8, 4]);
  assert.ok(arched.P.spine, 'the spine is straight');
  // Seen from the hips, the shoulder joints are where the straight back puts them, on the bench.
  const turn = (Math.abs(Math.atan2(arched.at.z, arched.at.y) - Math.atan2(straight.at.z, straight.at.y)) * 180) / Math.PI;
  assert.ok(turn < 0.5, `the shoulders turned ${turn.toFixed(1)}° about the hips`);
});
