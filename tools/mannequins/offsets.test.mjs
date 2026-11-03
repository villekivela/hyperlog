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
