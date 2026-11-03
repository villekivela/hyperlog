// Any bone can be selected from the keyboard (owner, 2026-11-03, #164): `[` `]` walk to the parent and
// the child, `{` `}` to the previous and next sibling, and `m` to the mirror bone on the other side.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { prepare } from '../entries.mjs';
import { buildStage } from '../stage.mjs';
import { skeletonOf } from './bones.mjs';

const { rig } = await buildStage(prepare('barbell_squat'));
const bones = skeletonOf(rig.bones);

test('the skeleton walks from a bone to its parent and its first child, and stays put at either end', () => {
  assert.equal(bones.parent('spine_02'), 'spine_01');
  assert.equal(bones.child('spine_02'), 'spine_03');
  assert.equal(bones.child('spine_03'), 'neck_01');
  assert.equal(bones.parent('hand_l'), 'lowerarm_l');
  assert.equal(bones.child('hand_l'), 'index_01_l');
  // The pelvis is the top: the rig's root holds the body up and isn't turned.
  assert.equal(bones.parent('pelvis'), 'pelvis');
  // A fingertip's leaf is no bone to turn.
  assert.equal(bones.child('index_03_l'), 'index_03_l');
  assert.equal(bones.child('ball_l'), 'ball_l');
});

test('siblings are walked in turn, round from the last to the first, and the mirror bone is on the other side', () => {
  assert.equal(bones.sibling('neck_01', 1), 'clavicle_l');
  assert.equal(bones.sibling('clavicle_l', 1), 'clavicle_r');
  assert.equal(bones.sibling('clavicle_r', 1), 'neck_01');
  assert.equal(bones.sibling('neck_01', -1), 'clavicle_r');
  assert.equal(bones.sibling('index_01_r', -1), 'thumb_01_r');
  assert.equal(bones.sibling('thigh_l', 1), 'thigh_r');
  assert.equal(bones.sibling('Head', 1), 'Head');
  assert.equal(bones.mirror('upperarm_l'), 'upperarm_r');
  assert.equal(bones.mirror('ring_02_r'), 'ring_02_l');
  assert.equal(bones.mirror('Head'), 'Head');
});

test('every bone can be turned but the root and the leaves: the spine, neck, head, shoulder blades, hips, knees, ankles, wrists and fingers', () => {
  for (const n of ['pelvis', 'spine_01', 'spine_03', 'neck_01', 'Head', 'clavicle_l', 'thigh_r', 'calf_l', 'foot_r', 'ball_l', 'hand_r', 'thumb_03_l', 'pinky_01_r']) {
    assert.ok(bones.names.includes(n), n);
  }
  assert.ok(!bones.names.includes('root'));
  assert.ok(!bones.names.some((n) => n.includes('leaf')));
  assert.equal(bones.names[0], 'pelvis');
});
