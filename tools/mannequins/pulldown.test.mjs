// The close-grip lat pulldown, from the owner's notes (#124): at the top the arms are fully
// extended with the shoulders let up into the stretch, and the pull drives the elbows straight
// down toward the sides.
import { test, before } from 'node:test';
import assert from 'node:assert/strict';
import { prepare } from './entries.mjs';
import { buildStage } from './stage.mjs';
import { turnOf, wpos } from './rig.mjs';

let stage;
before(async () => { stage = await buildStage(prepare('close_grip_lat_pulldown')); });

const deg = (r) => (r * 180) / Math.PI;
function arm(d) {
  stage.pose(d);
  const at = (n) => wpos(stage.rig.bones[n]);
  const upper = at('lowerarm_l').sub(at('upperarm_l'));
  const fore = at('hand_l').sub(at('lowerarm_l'));
  return { upper, fore, shoulder: at('upperarm_l') };
}

test('at the top the arms are fully extended and the shoulders are up in the stretch', () => {
  const top = arm(0);
  assert.ok(deg(top.upper.angleTo(top.fore)) < 8, `elbow bends ${deg(top.upper.angleTo(top.fore))}°`);
  assert.ok(top.shoulder.y - arm(1).shoulder.y > 0.04, `shoulders ${top.shoulder.y} against ${arm(1).shoulder.y}`);
});

test('at the bottom the elbows are down by the sides, not out', () => {
  const { upper } = arm(1);
  // Front-on, the upper arm is within 18° of straight down: the elbow gives a little to keep the
  // underhand wrist straight.
  const out = deg(Math.atan2(upper.x, -upper.y));
  assert.ok(Math.abs(out) < 18, `elbow out ${out}°`);
});

test('the grip is underhand: the palms face the lifter', () => {
  stage.pose(0.5);
  for (const k of ['l', 'r']) {
    const palm = stage.rig.palms[k].clone().applyQuaternion(turnOf(stage.rig, 'hand_' + k));
    assert.ok(palm.z < -0.7, `${k} palm ${palm.toArray()}`);
  }
});
