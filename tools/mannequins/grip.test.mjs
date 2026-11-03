// The grip's rotation (`grip.rotate`, #124): degrees of forearm twist, 0 overhand (palms away from
// the lifter), 90 neutral (palms toward each other), 180 underhand (palms toward the face).
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { prepare } from './entries.mjs';
import { buildStage } from './stage.mjs';
import { turnOf } from './rig.mjs';
import { checkStage } from './checks.mjs';
import { applyTuned, tunables } from './tune.mjs';

const entry = prepare('close_grip_lat_pulldown');

/** Which way the palm of the hand on side `k` faces, with the grip rotated `rotate` degrees. */
async function palmAt(rotate, k = 'l') {
  const stage = await buildStage(applyTuned(entry, { 'grip.rotate': rotate }));
  stage.pose(0);
  return { stage, palm: stage.rig.palms[k].clone().applyQuaternion(turnOf(stage.rig, 'hand_' + k)) };
}

test('0° is an overhand grip: the palms face away from the lifter', async () => {
  const { palm } = await palmAt(0);
  assert.ok(palm.z > 0.7, `palm ${palm.toArray()}`);
});

test('180° is underhand: the palms face the lifter', async () => {
  for (const k of ['l', 'r']) {
    const { palm } = await palmAt(180, k);
    assert.ok(palm.z < -0.7, `${k} palm ${palm.toArray()}`);
  }
});

test('90° is neutral: the palms face each other', async () => {
  for (const k of ['l', 'r']) {
    const { palm, stage } = await palmAt(90, k);
    const toward = -Math.sign(stage.rig.rest['hand_' + k].p.x);
    assert.ok(palm.x * toward > 0.7, `${k} palm ${palm.toArray()}`);
  }
});

test('the hands still hold the bar whichever way they turn', async () => {
  for (const rotate of [0, 90, 180]) {
    const tuned = applyTuned(entry, { 'grip.rotate': rotate });
    const checks = checkStage(tuned, await buildStage(tuned));
    assert.ok(checks.hands.ok, `${rotate}°: ${checks.hands.found}`);
  }
});

test('every pose with hands offers a rotation slider at the top and the bottom, set to overhand', () => {
  for (const end of ['top', 'bottom']) {
    const t = tunables(prepare('barbell_bench_press')).find((x) => x.path === `${end}.rotate`);
    assert.equal(t?.value, 0);
    assert.ok(t.max >= 180 && t.min <= 0);
  }
});
