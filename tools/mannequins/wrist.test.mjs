// The wrists (owner, #127): a hand turns at the wrist until its knuckles lie along what it holds, as
// far as a wrist bends, and Tune bends it further (`wrist`: flexion and deviation, per end).
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { prepare } from './entries.mjs';
import { buildStage } from './stage.mjs';
import { applyTuned, tunables } from './tune.mjs';
import { handlesAt } from './handles.mjs';
import * as THREE from 'three';
import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { bakeAll } from './bake.mjs';
import { loadClip } from './glb.mjs';
import { POSES } from './poses.mjs';
import { contactsOf } from './checks.mjs';
import { X, gripTarget, heldAt, turnOf, wpos } from './rig.mjs';
import { scratch } from './tmp.mjs';

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
    // The bar rests on the palm's skin (#162), 6-7 cm off this mannequin's deep palm, so a straight
    // wrist rolls a little further round it than it did when the bar sat in the palm: a back
    // squat's top crosses it 16°.
    const off = deg(Math.acos(Math.min(1, Math.abs(knuckles.dot(X)))));
    assert.ok(off < 18, `the knuckles cross the bar ${off.toFixed(0)}° off its line`);
    const bend = deg(fingers.angleTo(forearm));
    assert.ok(bend < 45, `the wrist bends ${bend.toFixed(0)}°`);
  });
}

test('wrist flexion and deviation at an end turn the hand that far from a straight wrist', async () => {
  const base = await posed('barbell_squat', 0, { 'top.wrist': [0, 0], 'bottom.wrist': [0, 0] });
  const flexed = await posed('barbell_squat', 0, { 'top.wrist': [20, 0], 'bottom.wrist': [20, 0] });
  const turn = deg(flexed.atWrist.angleTo(base.atWrist));
  assert.ok(Math.abs(turn - 20) < 5, `the hand turned ${turn.toFixed(0)}° at the wrist`);
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
  delete entry.top.rotate;
  const stage = await buildStage(entry);
  const L = stage.rig.dims;
  assert.equal(frameAt(entry, 0, L).gripRotate, 30);
  assert.equal(frameAt(entry, 1, L).gripRotate, 150);
});

// The owner, #127: a back squat's elbows can be set (Tune's grip.pole, or a drag of the elbow), back
// and down behind a low bar, and the hands still sit on the bar, knuckles along it.
test('a back squat offers its elbows in Tune, starting where the squat puts them', () => {
  const t = tunables(prepare('barbell_squat')).filter((x) => x.path.startsWith('grip.pole.'));
  assert.deepEqual(t.map((x) => x.path), ['grip.pole.0', 'grip.pole.1', 'grip.pole.2']);
});

// The owner, #127: a wrist the pose doesn't set stays straight, so the bar slants across the palm
// where the elbows put the hand off square to it.
test('elbows set back and down behind the bar keep the hands on it, the wrist straight', async () => {
  // The bar seated on the palm's skin (#162) holds the hand 6-7 cm off it, so the elbows go back
  // only a little way behind the shoulders before the hand would leave the bar.
  const back = await posed('barbell_squat', 0, { 'grip.pole': [0.3, -0.4, -1] });
  const elbow = wpos(back.stage.rig.bones.lowerarm_l);
  const shoulder = wpos(back.stage.rig.bones.upperarm_l);
  assert.ok(elbow.z < shoulder.z - 0.01 && elbow.y < shoulder.y, `the elbow at ${elbow.toArray().map((v) => v.toFixed(2))}, the shoulder at ${shoulder.toArray().map((v) => v.toFixed(2))}`);
  assert.ok(heldAt(back.stage.rig, 'l').distanceTo(gripTarget(back.P, 1)) < 0.015, 'the hand left the bar');
  const bend = deg(back.fingers.angleTo(back.forearm));
  assert.ok(bend < 5, `the wrist bends ${bend.toFixed(1)}°`);
  const off = deg(Math.acos(Math.min(1, Math.abs(back.knuckles.dot(X)))));
  assert.ok(off <= 35.5, `the bar slants ${off.toFixed(0)}° across the palm`);
});

test('dragging a squat\'s elbow sets where the elbows point', async () => {
  const { entry, stage, P } = await posed('barbell_squat', 0);
  const handle = handlesAt(entry, stage, P, 0).find((h) => h.id === 'elbow');
  assert.ok(handle, 'no elbow handle');
  const changes = handle.drag(handle.at.clone().add(new THREE.Vector3(0, -0.05, -0.08)));
  assert.deepEqual(Object.keys(changes), ['grip.pole']);
});

// The owner, #127: their wrist values win over the hand's turn to the bar, so a straight wrist is
// one they can set.
test('a straight wrist set on a back squat keeps the hand in line with the forearm', async () => {
  const straight = await posed('barbell_squat', 0, { 'top.wrist': [0, 0], 'bottom.wrist': [0, 0] });
  const bend = deg(straight.fingers.angleTo(straight.forearm));
  assert.ok(bend < 5, `the wrist bends ${bend.toFixed(1)}°`);
});

test('the wrist sliders reach 60° either way', () => {
  const t = tunables(prepare('barbell_squat')).find((x) => x.path === 'top.wrist.1');
  assert.ok(t.min <= -60 && t.max >= 60, `${t.min} to ${t.max}`);
});

// The owner, #127: the fingers close round the bar until they touch it and each other, no further.
for (const [id, d] of [['barbell_squat', 0], ['barbell_bench_press', 1], ['lat_pulldown', 1]]) {
  test(`${id}'s fingers wrap the bar, touching it, none folded into the next`, async () => {
    const { stage, P } = await posed(id, d);
    const at = (n) => wpos(stage.rig.bones[n]);
    // Each finger's skin on the bar's, within the contact check's 2 mm (#162).
    for (const c of contactsOf(stage, P, 'l')) assert.ok(c.past <= 0, c.found);
    const order = ['index', 'middle', 'ring', 'pinky'];
    for (let i = 0; i < 3; i++) {
      const gap = at(`${order[i]}_03_l`).distanceTo(at(`${order[i + 1]}_03_l`));
      assert.ok(gap > 0.013, `the ${order[i]} and ${order[i + 1]} fingers ${(gap * 100).toFixed(1)} cm apart`);
    }
  });
}

// The owner, #127: the tilt slider bends the hand toward the thumb or the little finger from a
// straight wrist, on a bar as anywhere, and neither the hand's roll about the bar nor the
// straight-wrist solve undoes it.
// On the low-bar squat, 12°: the hand, tilted on the bar it holds, still meets it within the contact
// check's 2 mm (#162); the barbell squat's wrapped thumb comes off its bar when tilted so.
test('12° of ulnar tilt at a low-bar squat\'s bottom is 12° in the baked clip', async () => {
  const out = await scratch('mannequins');
  const poses = { barbell_squat: POSES.barbell_squat, back_squat_low_bar: applyTuned(POSES.back_squat_low_bar, { 'bottom.wrist': [0, -12] }) };
  await bakeAll(out, { poses });
  const { clip, resources } = JSON.parse(await readFile(path.join(out, 'mannequins.json'), 'utf8')).clips.back_squat_low_bar;
  const gltf = await loadClip(path.join(out, clip), resources.map((r) => path.join(out, r)));
  const mixer = new THREE.AnimationMixer(gltf.scene);
  mixer.clipAction(gltf.animations[0]).play();
  mixer.setTime(0.9999 * gltf.animations[0].duration);
  gltf.scene.updateMatrixWorld(true);
  // The hand's tilt off the forearm, toward the thumb, in the plane of the knuckles.
  const at = (n) => gltf.scene.getObjectByName(n).getWorldPosition(new THREE.Vector3());
  const forearm = at('hand_l').sub(at('lowerarm_l')).normalize();
  const fingers = at('middle_01_l').sub(at('hand_l')).normalize();
  const thumbSide = at('index_01_l').sub(at('pinky_01_l'));
  thumbSide.addScaledVector(forearm, -thumbSide.dot(forearm)).normalize();
  const tilt = deg(Math.atan2(fingers.dot(thumbSide), fingers.dot(forearm)));
  assert.ok(Math.abs(tilt + 12) < 2, `the hand tilts ${tilt.toFixed(1)}° toward the thumb`);
});
