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

test('the deck rises from the plates to the tower, seated at both ends', async () => {
  const { stage, at } = await posed('seated_cable_row');
  at(0);
  let deck;
  const box = new THREE.Box3();
  stage.kit.group.traverse((m) => {
    if (!m.isMesh) return;
    box.setFromObject(m);
    if (box.max.x - box.min.x > 0.45 && box.max.x - box.min.x < 0.55 && box.max.z - box.min.z > 0.3) deck = box.clone();
  });
  assert.ok(deck, 'a deck');
  const rise = (Math.atan2(deck.max.y - deck.min.y, deck.max.z - deck.min.z) * 180) / Math.PI;
  assert.ok(rise > 20 && rise < 34, `the deck rises ${rise.toFixed(0)}°`);
  assert.ok(deck.max.z > 1.26, `the deck ends at z ${deck.max.z.toFixed(3)}, on the tower's tie`);
});

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
  // Beside the ribs, not across them: side and front views hide an elbow inside the chest.
  for (const d of [0.5, 0.75, 1]) {
    const g = at(d);
    const beside = g('lowerarm_l').x;
    assert.ok(beside > 0.17, `the elbow at x ${beside.toFixed(3)} at ${d}: beside the ribs`);
  }
  assert.ok(heldAt(stage.rig, 'l').y < pull('upperarm_l').y, 'the hands below the shoulders');
});

const leanOf = (g) => {
  const up = g('neck_01').sub(g('pelvis'));
  return (Math.atan2(up.z, up.y) * 180) / Math.PI;
};

test('the wide-grip seated row stays upright and draws a long bar in, the elbows out and back', async () => {
  const { stage, at } = await posed('wide_grip_seated_cable_row');
  assert.deepEqual(failing('wide_grip_seated_cable_row', stage), []);
  assert.equal(prepare('wide_grip_seated_cable_row').kit.seated_row.attachment, 'lat_bar');
  const reach = at(0);
  const leanReach = leanOf(reach);
  assert.ok(bend(reach('upperarm_l'), reach('lowerarm_l'), reach('hand_l')) > 145, 'the arms long at the reach');
  const pull = at(1);
  assert.ok(Math.abs(leanReach - leanOf(pull)) < 8, `the trunk swings ${Math.abs(leanReach - leanOf(pull)).toFixed(0)}°: no swing`);
  assert.ok(Math.abs(leanOf(pull)) < 8, `the trunk ${leanOf(pull).toFixed(0)}°: upright`);
  assert.ok(pull('lowerarm_l').x - pull('upperarm_l').x > 0.12, 'the elbows out');
  assert.ok(pull('lowerarm_l').z < pull('upperarm_l').z + 0.03, 'and back');
  const held = heldAt(stage.rig, 'l').x;
  assert.ok(held > 0.3 && held < 0.46, `the hand at ${held.toFixed(3)} on the bar's sleeve`);
  const chest = pull('hand_l').z - pull('spine_03').z;
  assert.ok(chest < 0.22, `the hands ${chest.toFixed(3)} off the chest at the finish`);
  const finish = pull('upperarm_l').sub(pull('upperarm_r')).length();
  const open = at(0);
  const start = open('upperarm_l').sub(open('upperarm_r')).length();
  assert.ok(start - finish > 0.05, `the shoulders ${start.toFixed(3)} apart at the reach, ${finish.toFixed(3)} at the squeeze: the blades work`);
  const pulled = at(1)('hand_l').clone();
  const travel = at(0)('hand_l').distanceTo(pulled);
  assert.ok(travel > 0.31, `the hands travel ${travel.toFixed(3)}`);
});

test('the seated Kelso shrug leans back on bent arms, the bar at the lower chest, and only the blades work', async () => {
  const { stage, at } = await posed('seated_cable_kelso_shrug');
  assert.deepEqual(failing('seated_cable_kelso_shrug', stage), []);
  const entry = prepare('seated_cable_kelso_shrug');
  assert.ok(entry.tempo.bottom >= 1, `a pause of ${entry.tempo.bottom} s at the top of the shrug`);
  const a = at(0);
  const [leanA, shoulderA, handA] = [leanOf(a), a('upperarm_l').clone(), a('hand_l').clone()];
  const b = at(1);
  assert.ok(Math.abs(leanA - leanOf(b)) < 6, `the trunk moves ${Math.abs(leanA - leanOf(b)).toFixed(0)}°: still`);
  assert.ok(shoulderA.z - b('upperarm_l').z > 0.025, `the shoulders draw ${(shoulderA.z - b('upperarm_l').z).toFixed(3)} back`);
  const travel = handA.z - b('hand_l').z;
  assert.ok(travel > 0.12 && travel < 0.34, `the hands travel ${travel.toFixed(3)}: a short pull`);
  const elbows = [0, 0.5, 1].map((d) => { const g = at(d); return bend(g('upperarm_l'), g('lowerarm_l'), g('hand_l')); });
  assert.ok(Math.max(...elbows) < 150 && Math.min(...elbows) > 85, `the elbows ${elbows.map((e) => e.toFixed(0)).join('/')}: bent through the rep`);
  const end = at(1);
  assert.ok(leanOf(end) < -5, `the trunk ${leanOf(end).toFixed(0)}°: leaning a little back`);
  const off = end('hand_l').z - end('spine_03').z;
  assert.ok(off < 0.3, `the hands ${off.toFixed(3)} off the chest: at the lower chest`);
});

test('the seated cable deadlift swings the trunk from over the legs to well back, on long arms', async () => {
  const { stage, at } = await posed('seated_cable_deadlift');
  assert.deepEqual(failing('seated_cable_deadlift', stage), []);
  const leans = [];
  const elbows = [];
  const hands = [];
  for (const d of [0, 0.25, 0.5, 0.75, 1]) {
    const g = at(d);
    leans.push(leanOf(g));
    elbows.push(bend(g('upperarm_l'), g('lowerarm_l'), g('hand_l')));
    hands.push(g('hand_l').clone());
  }
  assert.ok(leans[0] > 30, `the trunk ${leans[0].toFixed(0)}° forward at the start`);
  assert.ok(leans[4] < -60, `the trunk ${leans[4].toFixed(0)}° at the finish: well back`);
  assert.ok(Math.min(...elbows) > 140, `the elbows ${elbows.map((e) => e.toFixed(0)).join('/')}: the arms long`);
  assert.ok(hands[0].distanceTo(hands[4]) > 0.6, `the hands travel ${hands[0].distanceTo(hands[4]).toFixed(3)}`);
  const end = at(1);
  const crease = hands[4].z - end('pelvis').z;
  assert.ok(crease < 0.2, `the hands ${crease.toFixed(3)} ahead of the pelvis: at the hip's crease`);
});
