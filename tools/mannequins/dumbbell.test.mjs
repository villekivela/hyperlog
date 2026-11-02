// The dumbbells turn in the hands (README, Adjustable-bench poses): `rotate` twists the grip from
// overhand (0) through neutral (90), `tilt` tips the handle in the hand, and either may differ
// between the top and the bottom of the rep.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import * as THREE from 'three';
import { FIXTURE } from './adjustable.fixture.mjs';
import { prepare } from './entries.mjs';
import { buildStage } from './stage.mjs';
import { checkPose, CHECKS } from './checks.mjs';
import { tunables } from './tune.mjs';

const ID = 'db_incline_press';
const deg = (r) => (r * 180) / Math.PI;
/** The grip twisted and tipped alike through the rep. */
const grip = (rotate, tilt = 0) => ({ top: { rotate, tilt }, bottom: { rotate, tilt } });

/** The posed dumbbells' handle directions (left, right) at `d` down the rep, and the stage. */
async function axes(entryOver, d, angle = 30) {
  const poses = { fixture: FIXTURE, t: { like: 'fixture', ...entryOver } };
  const stage = await buildStage(prepare('t', poses, angle));
  stage.pose(d);
  return ['dumbbell_l', 'dumbbell_r'].map((n) => new THREE.Vector3(1, 0, 0).applyQuaternion(stage.kit.group.getObjectByName(n).quaternion));
}
/** How far the handle is turned from lying across the body, in degrees (0 to 90). */
const turned = (v) => deg(Math.acos(Math.min(1, Math.abs(v.x))));

test('the handle lies across the body when overhand, along it when neutral, and between at 45°', async () => {
  for (const [rotate, expected] of [[0, 0], [45, 45], [90, 90]]) {
    for (const v of await axes(grip(rotate), 0)) assert.ok(Math.abs(turned(v) - expected) < 6, `rotate ${rotate}: handle turned ${turned(v).toFixed(1)}°`);
  }
});

test('tilt tips the handle in the hand by that much, the two hands alike', async () => {
  const [flat] = await axes(grip(90, 0), 0);
  for (const tilt of [20, -20]) {
    const [l, r] = await axes(grip(90, tilt), 0);
    assert.ok(Math.abs(deg(l.angleTo(flat)) - 20) < 3, `${tilt}: ${deg(l.angleTo(flat))}°`);
    // Mirrored: the same tip up or down in each hand.
    assert.ok(Math.abs(Math.abs(l.y) - Math.abs(r.y)) < 0.02 && Math.sign(l.y * (flat.y || 1)) === Math.sign(r.y * (flat.y || 1)), `${l.toArray()} against ${r.toArray()}`);
  }
  const [up] = await axes(grip(90, 25), 0);
  const [down] = await axes(grip(90, -25), 0);
  assert.ok(Math.sign(up.y * up.z) !== Math.sign(down.y * down.z), 'opposite ways');
});

test('the grip may be neutral at the bottom and turn overhand by the top', async () => {
  const over = { top: { press: 0, rotate: 0 }, bottom: { press: 1, rotate: 90 } };
  const [top] = await axes(over, 0);
  const [bottom] = await axes(over, 1);
  assert.ok(turned(top) < 6, `top ${turned(top)}°`);
  assert.ok(turned(bottom) > 80, `bottom ${turned(bottom)}°`);
  const [middle] = await axes(over, 0.5);
  assert.ok(turned(middle) > 20 && turned(middle) < 70, `middle ${turned(middle)}°`);
});

test('the hands still hold their grip, at every angle, turned and tipped', async () => {
  const poses = { fixture: FIXTURE, t: { like: 'fixture', top: { press: 0, rotate: 0, tilt: 15 }, bottom: { press: 1, rotate: 90, tilt: -15 } } };
  for (const angle of [0, 30, 75]) {
    const { checks } = await checkPose('t', { poses, angle });
    assert.ok(checks.hands.ok, `${angle}°: ${checks.hands.found}`);
  }
});

test('Tune offers the turn and tilt at the top and the bottom, and not the one grip.rotate', () => {
  const paths = tunables(prepare(ID)).map((t) => t.path);
  for (const p of ['top.rotate', 'bottom.rotate', 'top.tilt', 'bottom.tilt']) assert.ok(paths.includes(p), p);
  assert.ok(!paths.includes('grip.rotate') && !paths.includes('grip.tilt'));
});
