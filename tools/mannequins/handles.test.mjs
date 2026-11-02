// The Tune panel's drag handles: each is a point on the body (a hand, an elbow, a foot, the hips,
// the chest) that, dragged, becomes the pose numbers the sliders set, and the rig solves the rest.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import * as THREE from 'three';
import { prepare } from './entries.mjs';
import { buildStage } from './stage.mjs';
import { applyTuned } from './tune.mjs';
import { handlesAt } from './handles.mjs';
import { wpos, gripTarget } from './rig.mjs';

const posed = async (id, d, tuned = {}) => {
  const entry = applyTuned(prepare(id), tuned);
  const stage = await buildStage(entry);
  const P = stage.pose(d);
  return { entry, stage, P, handles: handlesAt(entry, stage, P, d) };
};
const named = (list) => Object.fromEntries(list.map((h) => [h.id, h]));

test('the bench press offers a handle wherever its entry has numbers for one', async () => {
  const { handles } = await posed('barbell_bench_press', 0);
  assert.deepEqual(handles.map((h) => h.id).sort(), ['chest', 'elbow', 'feet', 'hand', 'hips']);
});

test('dragging the hand sets the bar at the top or the bottom, and the grip width, and the hand lands there', async () => {
  const before = await posed('barbell_bench_press', 0);
  const hand = named(before.handles).hand;
  const to = hand.at.clone().add(new THREE.Vector3(0.03, 0.04, -0.02));
  const changes = hand.drag(to);
  assert.deepEqual(Object.keys(changes).sort(), ['grip.width', 'top.bar']);
  const after = await posed('barbell_bench_press', 0, changes);
  assert.ok(gripTarget(after.P, 1).distanceTo(to) < 0.005, `${gripTarget(after.P, 1).distanceTo(to)}`);
  // At the bottom the same handle sets the bottom's bar.
  const bottom = named((await posed('barbell_bench_press', 1)).handles).hand;
  assert.ok('bottom.bar' in bottom.drag(bottom.at.clone().add(new THREE.Vector3(0, 0.02, 0))));
});

test('dragging a foot sets where the ankles stand, and they land there', async () => {
  const { handles } = await posed('barbell_bench_press', 0);
  const foot = named(handles).feet;
  const to = foot.at.clone().add(new THREE.Vector3(0.04, 0, -0.06));
  const changes = foot.drag(to);
  assert.deepEqual(Object.keys(changes).sort(), ['feet.stance', 'feet.z']);
  const { P } = await posed('barbell_bench_press', 0, changes);
  assert.ok(P.ankles[1].distanceTo(new THREE.Vector3(to.x, P.ankles[1].y, to.z)) < 0.002);
});

test('dragging the hips of a seated lift sets body.hip, and the chest sets the trunk\'s lean', async () => {
  const { handles, entry } = await posed('lat_pulldown', 1);
  const hips = named(handles).hips;
  assert.deepEqual(hips.drag(hips.at.clone().add(new THREE.Vector3(0, 0.03, -0.02))), { 'body.hip': [Number((entry.body.hip[0] + 0.03).toFixed(4)), Number((entry.body.hip[1] - 0.02).toFixed(4))] });
  const chest = named(handles).chest;
  const lean = chest.drag(chest.at.clone().add(new THREE.Vector3(0, 0, -0.05)));
  assert.ok(lean['bottom.trunk'] < entry.bottom.trunk, `${lean['bottom.trunk']} against ${entry.bottom.trunk}`);
});

test('dragging an elbow out sets how far the elbows flare', async () => {
  const { handles, entry } = await posed('barbell_bench_press', 1);
  const elbow = named(handles).elbow;
  const out = elbow.drag(elbow.at.clone().add(new THREE.Vector3(0.05, 0, 0)));
  assert.ok(out['grip.elbows'] > entry.grip.elbows, `${out['grip.elbows']}`);
});
