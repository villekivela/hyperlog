// The donkey calf raise (#127), on its own machine: the trunk hinged forward onto a front rest, the hips
// under a pad on a lever, the balls of the feet on a bar. Sizes and ranges are from
// refs/donkey_calf/ref.md.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import * as THREE from 'three';
import { prepare } from './entries.mjs';
import { buildStage } from './stage.mjs';
import { CHECKS, checkStage } from './checks.mjs';
import { heldAt, wpos } from './rig.mjs';

const ID = 'donkey_calf_raise';
const stage = await buildStage(prepare(ID));
const at = (d) => {
  stage.pose(d);
  stage.scene.updateMatrixWorld(true);
  return (name) => wpos(stage.scene.getObjectByName(name));
};
const bend = (a, b, c) => (a.clone().sub(b).angleTo(c.clone().sub(b)) * 180) / Math.PI;
/** The sole's tilt, heel up positive: the line from the ankle to the ball, less its slope on a level foot. */
const soleOf = (g) => {
  const way = g('ball_l').sub(g('foot_l'));
  const { ankleY, ballY, ballAhead } = stage.rig.dims;
  return ((Math.atan2(-way.y, way.z) - Math.atan2(ankleY - ballY, ballAhead)) * 180) / Math.PI;
};
const byRole = (role) => {
  const found = [];
  stage.kit.group.traverse((o) => { if (o.userData.role === role) found.push(o); });
  return found;
};
const boxes = (role) => byRole(role).map((o) => new THREE.Box3().setFromObject(o));
const stackHead = () => {
  let head;
  stage.kit.group.traverse((o) => { if (o.userData.stack) head = o; });
  return head;
};

test('the lift passes every check', () => {
  const checks = checkStage(prepare(ID), stage);
  assert.deepEqual(CHECKS.filter((c) => !checks[c].ok).map((c) => `${c}: ${checks[c].found}`), []);
});

test('the machine stands on the floor at its published length and height', () => {
  at(0);
  const box = new THREE.Box3();
  const lever = stage.kit.group.getObjectByName('donkey_calf_lever');
  const onLever = (o) => { for (let p = o; p; p = p.parent) if (p === lever) return true; return false; };
  stage.kit.group.traverse((o) => { if (o.isMesh && !onLever(o)) box.expandByObject(o); });
  const size = box.getSize(new THREE.Vector3());
  assert.ok(Math.abs(box.min.y) < 0.005, `the lowest part ${box.min.y.toFixed(3)} off the floor`);
  assert.ok(Math.abs(size.y - 1.473) < 0.03, `${size.y.toFixed(3)} tall, published 1.473`);
  assert.ok(Math.abs(size.z - 2.21) < 0.13, `${size.z.toFixed(3)} long, published 2.21, the handles reading short`);
});

test('the heels drop at the stretch and rise past level at the top, the knees holding soft: the ankles do the work', () => {
  const top = at(0);
  const up = soleOf(top);
  const kneeTop = bend(top('thigh_l'), top('calf_l'), top('foot_l'));
  const bottom = at(1);
  const down = soleOf(bottom);
  const kneeBottom = bend(bottom('thigh_l'), bottom('calf_l'), bottom('foot_l'));
  assert.ok(up > 7 && up < 20, `the heel ${up.toFixed(0)}° up at the top, about 12.5`);
  assert.ok(down < -22 && down > -37, `the heel ${down.toFixed(0)}° at the stretch, about 29.5 below level`);
  for (const knee of [kneeTop, kneeBottom]) assert.ok(knee > 155 && knee < 174, `the knee ${knee.toFixed(0)}°: soft, neither locked nor sat down`);
  assert.ok(Math.abs(kneeTop - kneeBottom) < 8, `the knee ${kneeTop.toFixed(0)}° at the top and ${kneeBottom.toFixed(0)}° at the stretch: the knees are working`);
});

test('the trunk lies hinged a little above level, the shoulders above the hips', () => {
  for (const d of [0, 0.5, 1]) {
    const g = at(d);
    const spine = g('neck_01').sub(g('pelvis'));
    const rise = (Math.atan2(spine.y, spine.z) * 180) / Math.PI;
    assert.ok(rise > 4 && rise < 26, `the trunk ${rise.toFixed(0)}° above level at ${d}`);
  }
});

test('the balls of the feet stay on the bar', () => {
  const balls = [0, 0.5, 1].map((d) => at(d)('ball_l'));
  for (const b of balls) {
    assert.ok(b.distanceTo(balls[0]) < 0.012, `the ball moves ${b.distanceTo(balls[0]).toFixed(3)}`);
  }
});

test('the hips carry the pad up on the lever and the stack rises with it', () => {
  const pad = () => wpos(byRole('hipPad')[0]);
  const hipsLow = at(1)('pelvis');
  const padLow = pad();
  const stackLow = stackHead().position.y;
  const hipsHigh = at(0)('pelvis');
  const padHigh = pad();
  const stackHigh = stackHead().position.y;
  const rise = padHigh.y - padLow.y;
  // More than the video's 70 mm: a leg of the mannequin's length needs it for the knees to hold still.
  assert.ok(rise > 0.09 && rise < 0.14, `the pad rises ${rise.toFixed(3)}, about 0.12`);
  assert.ok(Math.abs(hipsHigh.y - hipsLow.y - rise) < 0.02, `the hips rise ${(hipsHigh.y - hipsLow.y).toFixed(3)} with it`);
  assert.ok(stackHigh - stackLow > 0.025, `the stack rises ${(stackHigh - stackLow).toFixed(3)}`);
});

test('the elbows and the hands hold still on the rest, the palms in', () => {
  const place = [0, 1].map((d) => { const g = at(d); return [g('lowerarm_l'), heldAt(stage.rig, 'l').clone()]; });
  assert.ok(place[0][1].distanceTo(place[1][1]) < 0.01, `the hand moves ${place[0][1].distanceTo(place[1][1]).toFixed(3)} on its grip`);
  // The hips rise about twice as far: the trunk pivots on the shoulders.
  assert.ok(place[0][0].distanceTo(place[1][0]) < 0.065, `the elbow moves ${place[0][0].distanceTo(place[1][0]).toFixed(3)}`);
  for (const d of [0, 1]) {
    const g = at(d);
    for (const [k, side] of [['l', 1], ['r', -1]]) {
      const fingers = g(`middle_01_${k}`).sub(g(`hand_${k}`)).normalize();
      const fold = g(`middle_04_leaf_${k}`).sub(g(`middle_01_${k}`)).projectOnPlane(fingers);
      assert.ok(fold.x * side < 0, `the ${k} palm faces ${fold.x.toFixed(3)} across at ${d}, away from the other hand`);
    }
  }
});

test('the hip pad joins the lever: its rod runs arm to arm', () => {
  at(0);
  const [rod] = boxes('padRod');
  assert.ok(rod, 'a rod for the pad');
  const arms = boxes('leverArm');
  assert.ok(arms.every((a) => a.intersectsBox(rod)), `the rod ${rod.min.x.toFixed(3)} to ${rod.max.x.toFixed(3)} stops short of an arm`);
  assert.ok(boxes('padLug').every((l) => l.intersectsBox(rod) && l.intersectsBox(boxes('hipPad')[0])), 'a lug stops short of the rod or the pad');
  assert.equal(boxes('padLug').length, 2, 'two lugs');
});

test('the rollers ride under the lever, and every support reaches the floor or its frame', () => {
  for (const d of [0, 1]) {
    at(d);
    const arms = boxes('leverArm');
    assert.equal(arms.length, 2, 'two lever arms');
    for (const roller of boxes('roller')) {
      assert.ok(arms.some((a) => a.intersectsBox(roller.clone().expandByScalar(0.006))), `a roller clear of the lever at ${d}`);
    }
  }
  assert.equal(boxes('roller').length, 2, 'two rollers');
  // The guide rods stand in the stack's own plane, so the axle crossing them has to stand clear of it.
  const [axle] = boxes('rollerAxle');
  const stackZ = stackHead().getWorldPosition(new THREE.Vector3()).z;
  assert.ok(axle.min.z - stackZ > 0.012, `the axle ${(axle.min.z - stackZ).toFixed(3)} off the guide rods' plane`);
  const sides = boxes('sideFrame');
  assert.equal(sides.length, 2, 'two side frames');
  for (const side of sides) assert.ok(side.min.y < 0.005, `a side frame stops ${side.min.y.toFixed(3)} off the floor`);
  for (const beam of boxes('crossbeam')) assert.ok(sides.every((s) => s.intersectsBox(beam)), 'a crossbeam stops short of a side frame');
});
