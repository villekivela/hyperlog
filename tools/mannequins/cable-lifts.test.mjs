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
