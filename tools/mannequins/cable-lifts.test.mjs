// The library's last cable exercises (#127), on kits other lifts built. What each asks of the body
// beyond its kit's first pose is in its own test.
import { test } from 'node:test';
import assert from 'node:assert/strict';
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

test('the wide-grip pulldown holds the bar well outside the plain pulldown, from long arms to the chest', async () => {
  const { stage, at } = await posed('lat_pulldown_wide_grip');
  assert.deepEqual(failing('lat_pulldown_wide_grip', stage), []);
  const plain = await posed('lat_pulldown');
  const wide = at(1)('hand_l').x;
  const narrow = plain.at(1)('hand_l').x;
  assert.ok(wide > narrow + 0.08, `the hand ${wide.toFixed(3)} out, the plain pulldown's ${narrow.toFixed(3)}`);
  const top = at(0);
  assert.ok(top('hand_l').y > top('Head').y + 0.15, `the hands ${top('hand_l').y.toFixed(3)} up at the top, over the head`);
  const bottom = at(1);
  assert.ok(bottom('hand_l').y < bottom('neck_01').y + 0.05, `the hands ${bottom('hand_l').y.toFixed(3)} at the bottom, down to the chest`);
});

test('the neutral-grip pulldown holds a close handle, palms facing, the elbows driving down by the sides', async () => {
  const { stage, at } = await posed('neutral_grip_lat_pulldown');
  assert.deepEqual(failing('neutral_grip_lat_pulldown', stage), []);
  assert.equal(prepare('neutral_grip_lat_pulldown').kit.cable_tower.attachment, 'v_handle');
  const bottom = at(1);
  // Where the hands hold, not the wrists, which a palm's thickness puts further out.
  const apart = heldAt(stage.rig, 'l').x - heldAt(stage.rig, 'r').x;
  assert.ok(apart < 0.2, `the hands ${apart.toFixed(3)} apart on the handle`);
  const out = bottom('lowerarm_l').x - bottom('upperarm_l').x;
  assert.ok(Math.abs(out) < 0.1, `the elbow ${out.toFixed(3)} out from the shoulder: down by the sides`);
  assert.ok(bottom('lowerarm_l').y < bottom('upperarm_l').y - 0.15, 'the elbow below the shoulder at the bottom');
});

test('the rope hammer curl curls thumbs up, the elbows held at the sides', async () => {
  const { stage, at } = await posed('cable_rope_hammer_curl');
  assert.deepEqual(failing('cable_rope_hammer_curl', stage), []);
  const low = at(1);
  const [elbowLow, handLow] = [low('lowerarm_l').clone(), low('hand_l').clone()];
  const up = at(0);
  const [elbowUp, handUp] = [up('lowerarm_l').clone(), up('hand_l').clone()];
  assert.ok(elbowUp.distanceTo(elbowLow) < 0.06, `the elbow moves ${elbowUp.distanceTo(elbowLow).toFixed(3)}`);
  assert.ok(handUp.y - handLow.y > 0.3, `the hand rises ${(handUp.y - handLow.y).toFixed(3)}`);
  assert.ok(handUp.y > elbowUp.y + 0.1, 'the hand ends above the elbow');
  // Thumbs up: the knuckles run up and down, not across, through the rep.
  for (const d of [0, 0.5, 1]) {
    const g = at(d);
    const knuckles = g('index_01_l').sub(g('pinky_01_l')).normalize();
    assert.ok(Math.abs(knuckles.x) < 0.6, `the knuckles ${knuckles.x.toFixed(2)} across at ${d}: palms facing, not up`);
  }
});

test('the 1-arm pulldown kneels on one knee and drives the elbow from long overhead down to the side', async () => {
  const { stage, at } = await posed('1_arm_cable_pulldown');
  assert.deepEqual(failing('1_arm_cable_pulldown', stage), []);
  const top = at(0);
  assert.ok(top('calf_r').y < 0.12, `the right knee ${top('calf_r').y.toFixed(3)} up`);
  assert.ok(top('hand_l').y > top('Head').y, `the hand ${top('hand_l').y.toFixed(3)} at the top, over the head`);
  const reach = top('upperarm_l').sub(top('lowerarm_l')).angleTo(top('hand_l').sub(top('lowerarm_l'))) * 180 / Math.PI;
  assert.ok(reach > 145, `the elbow ${reach.toFixed(0)}° at the top: the arm long`);
  const bottom = at(1);
  const drop = bottom('upperarm_l').y - bottom('lowerarm_l').y;
  assert.ok(drop > 0.2, `the elbow ${drop.toFixed(3)} below the shoulder at the bottom`);
  assert.ok(bottom('hand_l').y < bottom('upperarm_l').y, 'the hand down below the shoulder');
});
