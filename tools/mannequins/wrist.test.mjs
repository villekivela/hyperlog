// The wrists (owner, #127): a hand turns at the wrist until its knuckles lie along what it holds, as
// far as a wrist bends, and Tune bends it further (`wrist`: flexion and deviation, per end).
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { prepare } from './entries.mjs';
import { buildStage } from './stage.mjs';
import { applyTuned } from './tune.mjs';
import { handlesAt } from './handles.mjs';
import { X, turnOf, wpos } from './rig.mjs';

const deg = (r) => (r * 180) / Math.PI;
const posed = async (id, d, tuned = {}) => {
  const entry = applyTuned(prepare(id), tuned);
  const stage = await buildStage(entry);
  const P = stage.pose(d);
  const at = (n) => wpos(stage.rig.bones[n]);
  return {
    entry, stage, P,
    // The hand's own knuckle line, square to its fingers, as the rig holds a bar across it.
    knuckles: (() => {
      const { dir, across } = stage.rig.rest.hand_l;
      const d = dir.clone().normalize();
      return across.clone().addScaledVector(d, -across.dot(d)).applyQuaternion(turnOf(stage.rig, 'hand_l')).normalize();
    })(),
    fingers: at('middle_01_l').sub(at('hand_l')).normalize(),
    forearm: at('hand_l').sub(at('lowerarm_l')).normalize(),
    // The hand's turn from the forearm's.
    atWrist: turnOf(stage.rig, 'lowerarm_l').invert().multiply(turnOf(stage.rig, 'hand_l')),
  };
};

for (const [id, d] of [['barbell_squat', 0], ['barbell_bench_press', 1], ['lat_pulldown', 0], ['lat_pulldown', 1]]) {
  test(`${id}'s knuckles lie along the bar at ${d ? 'the bottom' : 'the top'}, the wrist within its reach`, async () => {
    const { knuckles, fingers, forearm } = await posed(id, d);
    const off = deg(Math.acos(Math.min(1, Math.abs(knuckles.dot(X)))));
    assert.ok(off < 12, `the knuckles cross the bar ${off.toFixed(0)}° off its line`);
    const bend = deg(fingers.angleTo(forearm));
    assert.ok(bend < 45, `the wrist bends ${bend.toFixed(0)}°`);
  });
}

test('wrist flexion and deviation at an end turn the hand that far from where it would be', async () => {
  const base = await posed('barbell_squat', 0);
  const flexed = await posed('barbell_squat', 0, { 'top.wrist': [20, 0], 'bottom.wrist': [20, 0] });
  const turn = deg(flexed.atWrist.angleTo(base.atWrist));
  assert.ok(Math.abs(turn - 20) < 3, `the hand turned ${turn.toFixed(0)}° at the wrist`);
  // An end that gives no wrist is at neutral: one given at the bottom alone leaves the top alone.
  const bottomOnly = await posed('barbell_squat', 0, { 'bottom.wrist': [20, 0] });
  assert.ok(deg(bottomOnly.atWrist.angleTo(base.atWrist)) < 1, 'the top moved');
});

test('dragging the fingers turns the wrist', async () => {
  const { entry, stage, P, fingers } = await posed('barbell_squat', 0);
  const handle = handlesAt(entry, stage, P, 0).find((h) => h.id === 'wrist');
  assert.ok(handle, 'no wrist handle');
  const changes = handle.drag(handle.at.clone().addScaledVector(X.clone().cross(fingers).normalize(), 0.03));
  assert.deepEqual(Object.keys(changes), ['top.wrist']);
  assert.ok(changes['top.wrist'].some((v) => Math.abs(v) > 5), `${changes['top.wrist']}`);
});

test('a grip turned at one end eases from grip.rotate at the other', async () => {
  const { frameAt } = await import('./motions.mjs');
  const entry = applyTuned(prepare('cable_flye'), { 'grip.rotate': 30, 'bottom.rotate': 150 });
  const stage = await buildStage(entry);
  const L = stage.rig.dims;
  assert.equal(frameAt(entry, 0, L).gripRotate, 30);
  assert.equal(frameAt(entry, 1, L).gripRotate, 150);
});
