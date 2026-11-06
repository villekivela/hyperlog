// The library's last machine exercises (#127), on kits the other lifts built. What each asks of the
// body beyond its machine's first pose is in its own test.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { prepare } from './entries.mjs';
import { buildStage, FRAMES } from './stage.mjs';
import { CHECKS, checkStage } from './checks.mjs';
import { wpos } from './rig.mjs';

const posed = async (id) => {
  const stage = await buildStage(prepare(id));
  const at = (d) => {
    stage.pose(d);
    stage.scene.updateMatrixWorld(true);
    return (name) => wpos(stage.scene.getObjectByName(name));
  };
  return { stage, at };
};
const frames = Array.from({ length: FRAMES }, (_, f) => f / (FRAMES - 1));
const failing = (id, stage) => {
  const checks = checkStage(prepare(id), stage);
  return CHECKS.filter((c) => !checks[c].ok).map((c) => `${c}: ${checks[c].found}`);
};

test('the 1-leg leg press drives one foot on the plate, the other flat on the floor beside the seat', async () => {
  const { stage, at } = await posed('single_leg_leg_press');
  assert.deepEqual(failing('single_leg_leg_press', stage), []);
  const rest = frames.map((d) => at(d)('foot_r').clone());
  for (const p of rest) {
    assert.ok(p.y < 0.13, `the resting ankle ${p.y.toFixed(3)} up`);
    assert.ok(p.distanceTo(rest[0]) < 0.002, 'the resting foot stays where it is');
  }
  const [top, bottom] = [at(0)('foot_l').clone(), at(1)('foot_l').clone()];
  assert.ok(top.distanceTo(bottom) > 0.2, `the plate foot travels ${top.distanceTo(bottom).toFixed(3)}`);
  assert.ok(top.y > 0.8, `the plate foot ${top.y.toFixed(3)} up`);
});
