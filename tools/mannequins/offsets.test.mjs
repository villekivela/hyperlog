// Any bone can be turned at the top or the bottom of the rep (owner, #164): the turn is saved in the
// entry as a bone offset on top of the solve, every frame between is solved again from the two ends
// blended, and the limbs still reach what they hold and stand on.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import * as THREE from 'three';
import { prepare } from './entries.mjs';
import { buildStage } from './stage.mjs';
import { applyTuned } from './tune.mjs';
import { checkStage } from './checks.mjs';
import { wpos } from './rig.mjs';

const deg = (r) => (r * 180) / Math.PI;
const stageOf = async (id, tuned = {}) => {
  const entry = applyTuned(prepare(id), tuned);
  return { entry, stage: await buildStage(entry) };
};
const worldTurn = (stage, name) => stage.rig.bones[name].getWorldQuaternion(new THREE.Quaternion());

test('a bone turned at the top is turned that far there, about its own axis, and eases back to none at the bottom', async () => {
  const { stage: base } = await stageOf('barbell_squat');
  const { stage: turned } = await stageOf('barbell_squat', { 'top.offsets': { Head: [20, 0, 0] } });
  const at = (d) => {
    base.pose(d);
    turned.pose(d);
    const a = worldTurn(base, 'Head');
    const b = worldTurn(turned, 'Head');
    // The turn from the solve's head to the turned one, in the head's own frame as it stood in the bind pose.
    const rest = base.rig.rest.Head.wq;
    const own = rest.clone().multiply(a.clone().invert()).multiply(b).multiply(rest.clone().invert());
    return { angle: deg(a.angleTo(b)), axis: new THREE.Vector3(own.x, own.y, own.z).normalize() };
  };
  const top = at(0);
  assert.ok(Math.abs(top.angle - 20) < 0.01, `turned ${top.angle.toFixed(2)}° at the top`);
  // x is the bind pose's across: the head nods.
  assert.ok(Math.abs(Math.abs(top.axis.x) - 1) < 1e-4, `about ${top.axis.toArray().map((v) => v.toFixed(3))}`);
  // Halfway down the rep, half of it (the ends ease in and out as the rest of the entry does).
  assert.ok(Math.abs(at(0.5).angle - 10) < 0.01, `turned ${at(0.5).angle.toFixed(2)}° halfway`);
  assert.ok(at(1).angle < 0.01, `turned ${at(1).angle.toFixed(2)}° at the bottom`);
});

test('a trunk turned at the bottom moves the shoulders, and the hands still hold the bar and the feet stand, every frame', async () => {
  const { stage: base } = await stageOf('barbell_squat');
  const { entry, stage } = await stageOf('barbell_squat', { 'bottom.offsets': { spine_02: [0, 10, 0], pelvis: [0, 0, 4] } });
  base.pose(1);
  stage.pose(1);
  const moved = wpos(base.rig.bones.upperarm_l).distanceTo(wpos(stage.rig.bones.upperarm_l));
  assert.ok(moved > 0.03, `the left shoulder moved ${(moved * 100).toFixed(1)} cm`);
  const checks = checkStage(entry, stage);
  for (const c of ['hands', 'feet']) assert.ok(checks[c].ok, `${c}: frame ${checks[c].frame}, ${checks[c].found}`);
});

test('a hand turned by an offset is the pose setting its wrist, so the joints check lets it bend', async () => {
  const { entry, stage } = await stageOf('barbell_squat', { 'bottom.offsets': { hand_l: [0, 0, 12] } });
  const checks = checkStage(entry, stage);
  assert.ok(checks.joints.ok, `joints: frame ${checks.joints.frame}, ${checks.joints.found}`);
});

// A limb's own bones too (the spec review, #164): the limb is solved again to its contact, the knee or
// elbow swung where the turn points it and a hand or foot keeping its turn, so it still holds and stands.
// The thigh swings the knee out, the shin and the upper arm and forearm twist about their length,
// and the hand turns at the wrist.
for (const [bone, turn] of [['thigh_l', [0, 0, 10]], ['calf_r', [0, 12, 0]], ['upperarm_l', [15, 0, 0]], ['lowerarm_r', [15, 0, 0]], ['hand_l', [0, 25, 0]]]) {
  test(`${bone} turned at the bottom still holds what the limb holds and stands on, every frame`, async () => {
    const { stage: base } = await stageOf('barbell_squat');
    const { entry, stage } = await stageOf('barbell_squat', { 'bottom.offsets': { [bone]: turn } });
    base.pose(1);
    stage.pose(1);
    // The turn shows: the bone faces another way than the solve alone left it.
    const moved = deg(worldTurn(base, bone).angleTo(worldTurn(stage, bone)));
    assert.ok(moved > 3, `${bone} turned ${moved.toFixed(1)}°`);
    const checks = checkStage(entry, stage);
    for (const c of ['hands', 'feet']) assert.ok(checks[c].ok, `${c}: frame ${checks[c].frame}, ${checks[c].found}`);
  });
}

test('how far a knee bends is the contact\'s: a shin turned to bend it more still stands, the knee as the solve has it', async () => {
  const { stage: base } = await stageOf('barbell_squat');
  const { entry, stage } = await stageOf('barbell_squat', { 'bottom.offsets': { calf_r: [10, 0, 0] } });
  base.pose(1);
  stage.pose(1);
  const bend = (s) => deg(wpos(s.rig.bones.calf_r).sub(wpos(s.rig.bones.thigh_r)).angleTo(wpos(s.rig.bones.foot_r).sub(wpos(s.rig.bones.calf_r))));
  assert.ok(Math.abs(bend(stage) - bend(base)) < 0.01, `the knee bends ${bend(stage).toFixed(2)}°, the solve's ${bend(base).toFixed(2)}°`);
  assert.ok(checkStage(entry, stage).feet.ok);
});

test('a hand turned by an offset keeps the whole turn: the arm moves so the grip stays in the palm', async () => {
  const { stage: base } = await stageOf('barbell_squat');
  const { stage } = await stageOf('barbell_squat', { 'bottom.offsets': { hand_l: [0, 0, 12] } });
  base.pose(1);
  stage.pose(1);
  assert.ok(Math.abs(deg(worldTurn(base, 'hand_l').angleTo(worldTurn(stage, 'hand_l'))) - 12) < 0.01);
});
