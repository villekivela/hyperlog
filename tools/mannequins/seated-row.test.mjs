// The seated row station (refs/seated_row/ref.md): a long low bench, two angled foot plates and a
// tower with its cable at the height the lifts' videos have it, and the four lifts on it.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import * as THREE from 'three';
import { prepare } from './entries.mjs';
import { buildStage } from './stage.mjs';
import { CHECKS, checkStage } from './checks.mjs';
import { heldAt, wpos } from './rig.mjs';

const posed = async (id) => {
  const stage = await buildStage(prepare(id));
  const at = (d) => {
    stage.pose(d);
    stage.scene.updateMatrixWorld(true);
    return (name) => wpos(stage.scene.getObjectByName(name));
  };
  return { stage, at };
};
const failing = (id, stage) => {
  const checks = checkStage(prepare(id), stage);
  return CHECKS.filter((c) => !checks[c].ok).map((c) => `${c}: ${checks[c].found}`);
};
const bend = (a, b, c) => (a.clone().sub(b).angleTo(c.clone().sub(b)) * 180) / Math.PI;

test('the station stands on the floor, its overall size the published one', async () => {
  const { stage, at } = await posed('seated_cable_row');
  at(0);
  const box = new THREE.Box3().setFromObject(stage.kit.group);
  const size = box.getSize(new THREE.Vector3());
  assert.ok(Math.abs(box.min.y) < 0.005, `the lowest part ${box.min.y.toFixed(3)} off the floor`);
  assert.ok(Math.abs(size.y - 2.413) < 0.03, `${size.y.toFixed(3)} tall, published 2.413`);
  assert.ok(Math.abs(size.z - 2.286) < 0.05, `${size.z.toFixed(3)} long, published 2.286`);
  assert.ok(size.x < 0.72, `${size.x.toFixed(3)} wide, published 0.686`);
});

test('the seated cable row reaches long and draws the elbows back, feet on the plates, the stack rising', async () => {
  const { stage, at } = await posed('seated_cable_row');
  assert.deepEqual(failing('seated_cable_row', stage), []);
  assert.equal(prepare('seated_cable_row').kit.seated_row.attachment, 'handles');
  const reach = at(0);
  const long = bend(reach('upperarm_l'), reach('lowerarm_l'), reach('hand_l'));
  assert.ok(long > 150, `the elbow ${long.toFixed(0)}° at the reach: the arms long`);
  const knee = bend(reach('thigh_l'), reach('calf_l'), reach('foot_l'));
  assert.ok(knee > 115 && knee < 145, `the knee ${knee.toFixed(0)}° at the reach, bent about 50`);
  const lean = (g) => {
    const up = g('neck_01').sub(g('pelvis'));
    return (Math.atan2(up.z, up.y) * 180) / Math.PI;
  };
  assert.ok(lean(reach) > 20, `the trunk ${lean(reach).toFixed(0)}° forward at the reach`);
  const pull = at(1);
  assert.ok(Math.abs(lean(pull)) < 18, `the trunk ${lean(pull).toFixed(0)}° at the finish, near upright`);
  const straighter = bend(pull('thigh_l'), pull('calf_l'), pull('foot_l'));
  assert.ok(straighter > knee + 12, `the knee opens from ${knee.toFixed(0)}° to ${straighter.toFixed(0)}° as the hips slide back`);
  assert.ok(pull('lowerarm_l').z < pull('upperarm_l').z, 'the elbow behind the shoulder at the finish');
  assert.ok(heldAt(stage.rig, 'l').y < pull('upperarm_l').y, 'the hands below the shoulders');
});
