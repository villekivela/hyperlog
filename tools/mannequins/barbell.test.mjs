// The barbell batch's pulls and bridges (owner, #127): a row's or a deadlift's bar runs straight up
// and down, and the bar's place at the bottom of the rep, and a bridge's back arch there, are
// numbers Tune can set.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { prepare } from './entries.mjs';
import { buildStage, FRAMES } from './stage.mjs';
import { between, heldAt, turnOf, wpos } from './rig.mjs';
import { POSES } from './poses.mjs';

for (const id of ['barbell_row', 'deficit_pendlay_row', 'deadlift', 'deficit_deadlift']) {
  test(`${id}'s bar runs straight up and down`, async () => {
    const stage = await buildStage(prepare(id));
    const bars = Array.from({ length: FRAMES }, (_, f) => stage.pose(f / (FRAMES - 1)).bar.clone());
    for (const b of bars) {
      assert.ok(Math.abs(b.z - bars[0].z) < 0.005 && Math.abs(b.x) < 0.005, `the bar wanders to z ${b.z.toFixed(3)} from ${bars[0].z.toFixed(3)}`);
    }
    assert.ok(bars[0].y - bars.at(-1).y > 0.15, 'the bar hardly moves');
  });
}

test('a deadlift\'s bar sits where bottom.bar puts it, on the floor', async () => {
  const entry = prepare('deadlift', { ...POSES, deadlift: { ...POSES.deadlift, bottom: { ...POSES.deadlift.bottom, bar: 0.24 } } });
  const P = (await buildStage(entry)).pose(1);
  assert.ok(Math.abs(P.bar.y - 0.24) < 0.005, `the bar at ${P.bar.y.toFixed(3)}`);
});

test('a bridge\'s bar sits where top.bar and bottom.bar put it', async () => {
  const thrust = POSES.barbell_hip_thrust;
  const poses = { ...POSES, barbell_hip_thrust: { ...thrust, top: { ...thrust.top, bar: [0.66, 0.05] }, bottom: { ...thrust.bottom, bar: [0.4, -0.02] } } };
  const stage = await buildStage(prepare('barbell_hip_thrust', poses));
  assert.deepEqual(stage.pose(0).bar.toArray().map((v) => Number(v.toFixed(3))), [0, 0.66, 0.05]);
  assert.deepEqual(stage.pose(1).bar.toArray().map((v) => Number(v.toFixed(3))), [0, 0.4, -0.02]);
});

test('a hip thrust arches its back at the bottom, its shoulders still on the bench', async () => {
  const thrust = POSES.barbell_hip_thrust;
  const shouldersAt = async (arch) => {
    const entry = { ...thrust, top: { ...thrust.top, arch: [0, 0, 0] }, bottom: { ...thrust.bottom, arch } };
    const stage = await buildStage(prepare('barbell_hip_thrust', { ...POSES, barbell_hip_thrust: entry }));
    const P = stage.pose(1);
    return { P, at: between(stage.rig, 'upperarm_l', 'upperarm_r').sub(P.hipC) };
  };
  const straight = await shouldersAt([0, 0, 0]);
  const arched = await shouldersAt([12, 8, 4]);
  assert.ok(arched.P.spine, 'the spine is straight');
  // Seen from the hips, the shoulder joints are where the straight back puts them, on the bench.
  const turn = (Math.abs(Math.atan2(arched.at.z, arched.at.y) - Math.atan2(straight.at.z, straight.at.y)) * 180) / Math.PI;
  assert.ok(turn < 0.5, `the shoulders turned ${turn.toFixed(1)}° about the hips`);
});

// A cross-arm front squat (owner, #127): the bar rides on the front delts and each hand lies open
// on the other shoulder, over the bar, not gripping it.
test('a front squat\'s bar rides on the front delts all through the rep', async () => {
  const stage = await buildStage(prepare('front_squat'));
  let first;
  for (let f = 0; f < FRAMES; f++) {
    const P = stage.pose(f / (FRAMES - 1));
    const front = P.hipC.clone().set(1, 0, 0).cross(P.u).normalize();
    const off = P.bar.clone().sub(between(stage.rig, 'upperarm_l', 'upperarm_r'));
    const up = off.dot(P.u);
    const forward = off.dot(front);
    // Over the front of the shoulders, as high on them as the entry puts it, and it stays there.
    assert.ok(up > -0.01 && up < 0.13 && forward > 0.03 && forward < 0.12, `frame ${f}: the bar ${(up * 100).toFixed(1)} cm up and ${(forward * 100).toFixed(1)} cm in front of the shoulders`);
    (first ??= [up, forward]);
    assert.ok(Math.abs(up - first[0]) < 0.01 && Math.abs(forward - first[1]) < 0.01, `frame ${f}: the bar slid on the shoulders`);
  }
});

test('a front squat\'s hands lie open on the other shoulder, not on the bar', async () => {
  const entry = prepare('front_squat');
  const stage = await buildStage(entry);
  const P = stage.pose(0.5);
  assert.ok(P.openHands, 'the hands grip');
  // As far from the joint as the entry's spot on the shoulder, give or take 2 cm.
  const spot = Math.hypot(...entry.grip.spot);
  for (const [k, s] of [['l', 1], ['r', -1]]) {
    const other = wpos(stage.rig.bones[`upperarm_${s > 0 ? 'r' : 'l'}`]);
    // The palm, at the base of the fingers, lies on the delt in front of and above the joint.
    const palm = heldAt(stage.rig, k);
    assert.ok(palm.distanceTo(other) < spot + 0.02, `the ${k} palm is ${(palm.distanceTo(other) * 100).toFixed(0)} cm from the other shoulder`);
  }
});

// The owner, #127: a straight wrist is one they can set on the low-bar squat, and the upright row's
// hands lie along its bar.
test('a straight wrist set on the low-bar squat keeps the hand in line with the forearm', async () => {
  const thrust = POSES.back_squat_low_bar;
  const entry = prepare('back_squat_low_bar', { ...POSES, back_squat_low_bar: { ...thrust, top: { ...thrust.top, wrist: [0, 0] }, bottom: { ...thrust.bottom, wrist: [0, 0] } } });
  const stage = await buildStage(entry);
  for (const d of [0, 0.5, 1]) {
    stage.pose(d);
    const at = (n) => wpos(stage.rig.bones[n]);
    const bend = (at('middle_01_l').sub(at('hand_l')).angleTo(at('hand_l').sub(at('lowerarm_l'))) * 180) / Math.PI;
    assert.ok(bend < 5, `at ${d} the wrist bends ${bend.toFixed(1)}°`);
  }
});

test('the upright row\'s hands lie along the bar', async () => {
  const stage = await buildStage(prepare('barbell_upright_row'));
  for (const d of [0, 1]) {
    stage.pose(d);
    const at = (n) => wpos(stage.rig.bones[n]);
    const { dir, across } = stage.rig.rest.hand_l;
    const k = across.clone().addScaledVector(dir.clone().normalize(), -across.dot(dir.clone().normalize())).applyQuaternion(turnOf(stage.rig, 'hand_l')).normalize();
    const off = (Math.acos(Math.min(1, Math.abs(k.x))) * 180) / Math.PI;
    assert.ok(off < 12, `at ${d} the knuckles cross the bar ${off.toFixed(0)}° off its line`);
  }
});

// The coordinator's look at the hands (#127): at the bottom of the curl the fingers wrap the bar.
test('the curl\'s fingers wrap the bar at the bottom, touching it', async () => {
  const stage = await buildStage(prepare('barbell_curl'));
  const P = stage.pose(1);
  const at = (n) => wpos(stage.rig.bones[n]);
  const { dir, across } = stage.rig.rest.hand_l;
  const d = dir.clone().normalize();
  const axis = across.clone().addScaledVector(d, -across.dot(d)).applyQuaternion(turnOf(stage.rig, 'hand_l')).normalize();
  const grip = P.bar.clone().addScaledVector(axis.clone().set(1, 0, 0), P.grip);
  for (const f of ['index', 'middle', 'ring', 'pinky']) {
    const v = at(`${f}_04_leaf_l`).sub(grip);
    const off = v.addScaledVector(axis, -v.dot(axis)).length();
    assert.ok(off > 0.016 && off < 0.035, `the ${f} fingertip ${(off * 100).toFixed(1)} cm off the bar's axis`);
  }
});
