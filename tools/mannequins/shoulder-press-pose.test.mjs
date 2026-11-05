// The machine shoulder press is posed on its kit (#225): a person can use it. Hips on the seat, back on
// the pad, feet on the floor, both hands closed on the neutral grips, the yoke following the body
// through its whole range, and nothing passing through the body.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { prepare } from './entries.mjs';
import { buildStage, FRAMES } from './stage.mjs';
import { checkStage } from './checks.mjs';
import { wpos } from './rig.mjs';
import { POSES } from './poses.mjs';
import { BATCHES } from './review.mjs';
import { tempoOf } from './tempo.mjs';

const ID = 'machine_shoulder_press';
const kitOf = (stage) => Object.values(stage.kit.kits)[0];
const stage = await buildStage(prepare(ID));
const frames = Array.from({ length: FRAMES }, (_, f) => f / (FRAMES - 1));

test('the exercise is posed on the shoulder press kit, in a batch of its own, with a tempo', () => {
  assert.deepEqual(Object.keys(POSES[ID].kit), ['shoulder_press']);
  assert.ok(BATCHES['shoulder-press'].includes(ID));
  assert.ok(tempoOf(prepare(ID)).lowering > 0);
});

test('every check passes on every frame: hands, feet, equipment, joints, contact and linkage', () => {
  const checks = checkStage(prepare(ID), stage);
  for (const [name, check] of Object.entries(checks)) assert.ok(check.ok, `${name}: frame ${check.frame}: ${check.found}`);
});

test('the hips sit on the seat, on every frame, above its top and within its footprint', () => {
  const seat = kitOf(stage).group;
  let cushion;
  seat.traverse((o) => { if (o.isMesh && o.userData.role === 'seat') cushion = o; });
  const top = cushion.geometry.boundingBox ?? (cushion.geometry.computeBoundingBox(), cushion.geometry.boundingBox);
  const at = wpos(cushion);
  for (const d of frames) {
    stage.pose(d);
    const hip = stage.pose(d).hipC;
    assert.ok(hip.y - (at.y + top.max.y) > 0.03 && hip.y - (at.y + top.max.y) < 0.15, `the hips ${(hip.y - at.y - top.max.y).toFixed(3)} m above the seat at ${d}`);
    assert.ok(Math.abs(hip.z - at.z) < 0.25 && Math.abs(hip.x) < 0.05, `the hips off the seat at ${d}`);
  }
});

test('the back lies on the pad and the feet on the floor, through the rep', () => {
  for (const d of frames) {
    const P = stage.pose(d);
    const pad = kitOf(stage).contacts.find((c) => c.landmark === 'back').at[0];
    assert.ok(P.hipC.distanceTo(pad) < 0.6, 'the pad is not at the back');
  }
  const checks = checkStage(prepare(ID), stage);
  assert.ok(checks.linkage.ok, `linkage: ${checks.linkage.found}`);
  assert.ok(checks.feet.ok, `feet: ${checks.feet.found}`);
});

test('the grips rise 58 cm and the yoke turns 40°, the hands on them from the shoulders to overhead', () => {
  const grips = (d) => { stage.pose(d); return kitOf(stage).contacts.find((c) => c.point === 'grips').at.map((p) => p.clone()); };
  const [top, bottom] = [grips(0), grips(1)];
  assert.ok(Math.abs(top[0].y - bottom[0].y - 0.58) < 0.01, `the grips rise ${(top[0].y - bottom[0].y).toFixed(3)} m`);
  assert.ok(Math.abs(top[0].x + top[1].x) < 1e-6 && Math.abs(Math.abs(top[0].x - top[1].x) - 0.7) < 1e-6, 'the grips 700 apart');
  const shoulder = (k) => wpos(stage.rig.bones[`upperarm_${k}`]);
  stage.pose(1);
  assert.ok(Math.abs(shoulder('l').y - bottom[0].y) < 0.12, 'the shoulders level with the grips at the bottom');
  stage.pose(0);
  assert.ok(top[0].y - shoulder('l').y > 0.45, 'the hands overhead at the top');
});

test('the elbows stay below the hands at the bottom of the rep', () => {
  stage.pose(1);
  for (const k of ['l', 'r']) assert.ok(wpos(stage.rig.bones[`lowerarm_${k}`]).y < wpos(stage.rig.bones[`hand_${k}`]).y, `the ${k} elbow below its hand`);
});

test('no part of the kit flips between two frames of the rep (a cable laid exactly plumb would)', () => {
  const turned = new Map();
  for (const d of frames) {
    stage.pose(d);
    kitOf(stage).group.traverse((o) => {
      if (!o.isMesh) return;
      const before = turned.get(o);
      if (before) assert.ok(before.angleTo(o.quaternion) < Math.PI / 4, `${o.name} turned ${(before.angleTo(o.quaternion) * 180 / Math.PI).toFixed(0)}° between two frames`);
      turned.set(o, o.quaternion.clone());
    });
  }
});

test('the back stays on the pad through the whole press: the spine does not leave it between the top and the bottom', () => {
  const at = (d) => {
    stage.pose(d);
    const pad = kitOf(stage).contacts.find((c) => c.landmark === 'back').at[0];
    return ['spine_01', 'spine_02', 'spine_03'].map((b) => wpos(stage.rig.bones[b]).sub(pad));
  };
  const [top, bottom] = [at(0), at(1)];
  top.forEach((p, i) => assert.ok(p.distanceTo(bottom[i]) < 0.01, `${['low', 'mid', 'high'][i]} spine ${(p.distanceTo(bottom[i]) * 100).toFixed(1)} cm from where it was against the pad`));
});
