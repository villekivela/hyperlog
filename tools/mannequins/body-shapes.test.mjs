// The shapes a cable lift asks of the body beyond standing and sitting: kneeling, one leg off the
// floor, and both hands together on one thing.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { CHECKS, checkPose } from './checks.mjs';
import { prepare } from './entries.mjs';
import { buildStage } from './stage.mjs';
import { stateAt } from './motions.mjs';
import { POSES } from './poses.mjs';
import { wpos } from './rig.mjs';

const failing = (report) => CHECKS.filter((c) => !report.checks[c].ok);
const poses = (entry) => ({ poses: { ...POSES, shape: entry } });
const staged = async (entry) => {
  const stage = await buildStage(prepare('shape', poses(entry).poses));
  return (d) => {
    stage.pose(d);
    stage.scene.updateMatrixWorld(true);
    return stage.rig.bones;
  };
};
const arms = { upperArm: 10, forearm: 20 };
const STAND = { equipment: 'cable', motion: 'arms', feet: { stance: 0.12 }, grip: { width: 0.2, one: 1, rest: [-0.25, 0.9, 0.1] } };

test('a kneeling body rests on its knees, shins back along the floor, and passes its checks', async () => {
  const entry = {
    ...STAND, body: { kneel: true }, grip: { width: 0.2 },
    top: { thigh: 5, trunk: 20, ...arms }, bottom: { thigh: 5, trunk: 50, ...arms },
  };
  assert.deepEqual(failing(await checkPose('shape', poses(entry))), []);
  const bones = (await staged(entry))(0);
  for (const k of ['l', 'r']) {
    const [knee, ankle] = [wpos(bones[`calf_${k}`]), wpos(bones[`foot_${k}`])];
    assert.ok(knee.y < 0.12, `the ${k} knee ${knee.y.toFixed(3)} up`);
    assert.ok(ankle.y < 0.2, `the ${k} ankle ${ankle.y.toFixed(3)} up`);
    assert.ok(ankle.z < knee.z - 0.3, `the ${k} shin runs back along the floor`);
  }
});

test('a lifted leg swings back from the hip, the other foot still planted, and passes its checks', async () => {
  const entry = {
    ...STAND, feet: { stance: 0.1, lift: 1 },
    top: { shin: 8, thigh: 14, trunk: 30, leg: [40, 0, 20], ...arms },
    bottom: { shin: 8, thigh: 14, trunk: 30, leg: [-5, 0, 70], ...arms },
  };
  assert.deepEqual(failing(await checkPose('shape', poses(entry))), []);
  const at = await staged(entry);
  const back = wpos(at(0).foot_l).clone();
  const forward = wpos(at(1).foot_l).clone();
  assert.ok(back.z < forward.z - 0.2, `the lifted foot ${back.z.toFixed(3)} behind ${forward.z.toFixed(3)}`);
  assert.ok(back.y > 0.25, `the lifted foot ${back.y.toFixed(3)} off the floor`);
  assert.ok(wpos(at(0).foot_r).y < 0.12, 'the planted foot stays down');
});

test('a leg lifted out goes to its own side', async () => {
  const entry = {
    ...STAND, feet: { stance: 0.1, lift: -1 },
    top: { shin: 6, thigh: 10, trunk: 20, leg: [0, 35, 5], ...arms },
    bottom: { shin: 6, thigh: 10, trunk: 20, leg: [0, 8, 60], ...arms },
  };
  assert.deepEqual(failing(await checkPose('shape', poses(entry))), []);
  const at = await staged(entry);
  const out = wpos(at(0).foot_r).x;
  const in_ = wpos(at(1).foot_r).x;
  assert.ok(out < in_ - 0.3, `the right foot out to ${out.toFixed(3)} from ${in_.toFixed(3)}`);
});

test('joined hands sit either side of one point, off the middle, not mirrored', async () => {
  const entry = {
    equipment: 'cable', motion: 'free_arms', feet: { stance: 0.16 }, grip: { joined: 0.05, pole: [1, -0.3, -0.5] },
    top: { shin: 4, thigh: 6, trunk: 4, hand: [0.35, 1.45, 0.4] }, bottom: { shin: 4, thigh: 6, trunk: 4, hand: [-0.25, 1.1, 0.3] },
  };
  assert.deepEqual(failing(await checkPose('shape', poses(entry))), []);
  const at = await staged(entry);
  const b = at(0);
  const [l, r] = [wpos(b.hand_l).clone(), wpos(b.hand_r).clone()];
  assert.ok(l.x > 0.2 && r.x > 0.15, `both hands on the left at the top: ${l.x.toFixed(3)}, ${r.x.toFixed(3)}`);
  const c = at(1);
  assert.ok(wpos(c.hand_l).x < -0.1 && wpos(c.hand_r).x < -0.2, `both hands on the right at the bottom: ${wpos(c.hand_l).x.toFixed(3)}, ${wpos(c.hand_r).x.toFixed(3)}`);
});

test('a hand given a point to pass through curves through it at the middle of the rep', async () => {
  const entry = {
    equipment: 'cable', motion: 'free_arms', feet: { stance: 0.16 }, grip: { via: [0.3, 1.2, 0.5], pole: [1, -0.3, -0.5] },
    top: { shin: 4, thigh: 6, trunk: 4, hand: [0.3, 1.0, 0.3] }, bottom: { shin: 4, thigh: 6, trunk: 4, hand: [0.3, 1.0, 0.3] },
  };
  assert.deepEqual(stateAt(entry, 0.5).hand, [0.3, 1.2, 0.5]);
  assert.deepEqual(stateAt(entry, 0).hand, [0.3, 1.0, 0.3]);
  assert.deepEqual(stateAt(entry, 1).hand, [0.3, 1.0, 0.3]);
});

test('a resting hand given a bar to grip closes round it, and an open one stays open', async () => {
  const entry = {
    equipment: 'cable', motion: 'free_arms', feet: { stance: 0.12 }, grip: { one: 1, rest: [-0.25, 1.2, 0.35], pole: [1, -0.3, -0.5], rotate: 90 },
    top: { shin: 4, thigh: 6, trunk: 4, hand: [0.3, 1.0, 0.3] }, bottom: { shin: 4, thigh: 6, trunk: 4, hand: [0.3, 1.0, 0.3] },
  };
  const reach = (bones) => wpos(bones.middle_04_leaf_r).distanceTo(wpos(bones.hand_r));
  const relaxed = reach((await staged(entry))(0));
  const gripped = reach((await staged({ ...entry, grip: { ...entry.grip, restOn: 0.03 } }))(0));
  assert.ok(gripped < relaxed - 0.02, `fingers ${gripped.toFixed(3)} from the wrist, relaxed ${relaxed.toFixed(3)}`);
});
