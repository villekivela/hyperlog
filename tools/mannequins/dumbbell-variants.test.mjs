// The second dumbbell batch (#127): variants of the lifts the owner has OK'd, each `like:` one of them
// or of another variant, each passing every check and doing what the library's note on it asks.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import * as THREE from 'three';
import { BENCH_ANGLES, benchAngles, prepare } from './entries.mjs';
import { buildStage } from './stage.mjs';
import { wpos } from './rig.mjs';
import { BATCHES } from './review.mjs';
import { bellAt, boneAt, deg, handleTurn, noBellSpins, passes } from './dumbbell.fixture.mjs';

const PAD_TOP = 0.457;

/** How far the trunk of `id` leans forward of upright at `d` down the rep, in degrees. */
async function leanAt(id, d) {
  const [hips, neck] = [await boneAt(id, 'pelvis', d), await boneAt(id, 'neck_01', d)];
  return deg(Math.atan2(neck.z - hips.z, neck.y - hips.y));
}

test('super_rom_db_lateral_raise: passes every check', () => passes('super_rom_db_lateral_raise'));

test('super_rom_db_lateral_raise: the dumbbells go on past the shoulders until they meet overhead', async () => {
  const [top, bottom] = [await bellAt('super_rom_db_lateral_raise', 0), await bellAt('super_rom_db_lateral_raise', 1)];
  const head = await boneAt('super_rom_db_lateral_raise', 'Head', 0);
  assert.ok(top.y > head.y + 0.15, `the bell ${(top.y - head.y).toFixed(2)} m over the head`);
  assert.ok(top.x < 0.2, `the bell ${top.x.toFixed(2)} m out at the top`);
  assert.ok(bottom.y < 0.95, `from ${bottom.y.toFixed(2)} m`);
});

test('bent_over_reverse_db_flye: passes every check', () => passes('bent_over_reverse_db_flye'));

test('bent_over_reverse_db_flye: hinged near level, the dumbbells sweep from under the shoulders out level with them', async () => {
  const id = 'bent_over_reverse_db_flye';
  const [lean, shoulder] = [await leanAt(id, 1), await boneAt(id, 'upperarm_l', 0)];
  assert.ok(lean > 55 && lean < 85, `the trunk leans ${lean.toFixed(0)}°`);
  const [top, bottom] = [await bellAt(id, 0), await bellAt(id, 1)];
  assert.ok(Math.abs(top.y - shoulder.y) < 0.12, `the bell ${(top.y - shoulder.y).toFixed(2)} m from the shoulder's height`);
  assert.ok(top.x > 0.6, `out ${top.x.toFixed(2)} m`);
  assert.ok(bottom.x < 0.3 && bottom.y < shoulder.y - 0.4, `from ${bottom.x.toFixed(2)} m out, ${bottom.y.toFixed(2)} m up`);
});

test('db_guillotine_press: passes every check', () => passes('db_guillotine_press'));

test('db_guillotine_press: the elbows flare and the dumbbells come down over the upper chest, nearer the head than the flat press', async () => {
  const id = 'db_guillotine_press';
  const [bell, flat] = [await bellAt(id, 1), await bellAt('db_bench_press', 1)];
  const [shoulder, elbow] = [await boneAt(id, 'upperarm_l', 1), await boneAt(id, 'lowerarm_l', 1)];
  assert.ok(bell.z < flat.z - 0.04, `the bell at z ${bell.z.toFixed(3)}, the flat press's at ${flat.z.toFixed(3)}`);
  assert.ok(Math.abs(bell.z - shoulder.z) < 0.08, `the bell ${(bell.z - shoulder.z).toFixed(3)} m from over the shoulder`);
  assert.ok(Math.abs(elbow.z - shoulder.z) < 0.07, `the elbow ${(elbow.z - shoulder.z).toFixed(3)} m along the trunk: flared, not tucked`);
});

test('db_skull_crusher: passes every check', () => passes('db_skull_crusher'));

test('db_skull_crusher: the upper arms hold still, leaning a little to the head, the dumbbells coming down beside it, palms in', async () => {
  const id = 'db_skull_crusher';
  const [elbowTop, elbowBottom] = [await boneAt(id, 'lowerarm_l', 0), await boneAt(id, 'lowerarm_l', 1)];
  assert.ok(elbowTop.distanceTo(elbowBottom) < 0.05, `the elbow travels ${elbowTop.distanceTo(elbowBottom).toFixed(3)} m`);
  const [bell, head] = [await bellAt(id, 1), await boneAt(id, 'Head', 1)];
  assert.ok(bell.y < head.y + 0.25, `the bell ${(bell.y - head.y).toFixed(2)} m over the head`);
  assert.ok(Math.abs(bell.z - head.z) < 0.22, `the bell ${(bell.z - head.z).toFixed(2)} m along the head from its base`);
  assert.ok(bell.x > 0.1, `the bell ${bell.x.toFixed(2)} m out, clear of the head`);
  assert.ok((await handleTurn(id, 0)) > 70 && (await handleTurn(id, 1)) > 60, 'neutral');
});

test('dumbbell_shoulder_press_standing: passes every check', () => passes('dumbbell_shoulder_press_standing'));

test('dumbbell_shoulder_press_standing: from beside the ears to overhead, palms forward, the trunk upright', async () => {
  const id = 'dumbbell_shoulder_press_standing';
  const [top, bottom, shoulder] = [await bellAt(id, 0), await bellAt(id, 1), await boneAt(id, 'upperarm_l', 1)];
  assert.ok(top.y > shoulder.y + 0.5, `the top ${(top.y - shoulder.y).toFixed(2)} m over the shoulder`);
  assert.ok(Math.abs(top.z - shoulder.z) < 0.1, `the top ${(top.z - shoulder.z).toFixed(2)} m from over the shoulder`);
  assert.ok(Math.abs(bottom.y - shoulder.y) < 0.15, `the bottom ${(bottom.y - shoulder.y).toFixed(2)} m from the shoulder's height`);
  for (const d of [0, 1]) assert.ok((await handleTurn(id, d)) < 25, `palms forward at ${d}`);
});

/** How far the left dumbbell turns between the top of the rep and the bottom, in degrees. */
async function bellSwing(id, angle) {
  const stage = await buildStage(prepare(id, undefined, angle));
  const bell = stage.kit.group.getObjectByName('dumbbell_l');
  stage.pose(0);
  const top = bell.quaternion.clone();
  stage.pose(1);
  return deg(bell.quaternion.angleTo(top));
}

test('standing_db_arnold_press: passes every check', () => passes('standing_db_arnold_press'));

test('standing_db_arnold_press: elbows in front, palms to the face at the bottom, turning to face forward overhead', async () => {
  const id = 'standing_db_arnold_press';
  const [elbow, shoulder] = [await boneAt(id, 'lowerarm_l', 1), await boneAt(id, 'upperarm_l', 1)];
  assert.ok(elbow.z > shoulder.z + 0.1, `the elbow ${(elbow.z - shoulder.z).toFixed(2)} m ahead of the shoulder`);
  assert.ok((await bellSwing(id)) > 140, `the bell turns ${(await bellSwing(id)).toFixed(0)}°`);
  assert.ok((await handleTurn(id, 0.5)) > 40, 'on its way round at mid-rep');
  const top = await bellAt(id, 0);
  assert.ok(top.y > shoulder.y + 0.5, `the top ${(top.y - shoulder.y).toFixed(2)} m over the shoulder`);
});

test('arnold_press_seated: passes every check at each bench angle', async () => {
  for (const angle of BENCH_ANGLES) await passes('arnold_press_seated', angle);
});

test('arnold_press_seated: the dumbbells turn from palms to the face to palms forward at every bench angle', async () => {
  for (const angle of BENCH_ANGLES) {
    const swing = await bellSwing('arnold_press_seated', angle);
    assert.ok(swing > 130, `at ${angle}°: the bell turns ${swing.toFixed(0)}°`);
  }
});

test('arnold_press_seated: upright by default, the elbows ahead of the shoulders at the bottom', async () => {
  const id = 'arnold_press_seated';
  assert.equal(prepare(id).bench.angle, 75);
  const [elbow, shoulder] = [await boneAt(id, 'lowerarm_l', 1), await boneAt(id, 'upperarm_l', 1)];
  assert.ok(elbow.z > shoulder.z + 0.1, `the elbow ${(elbow.z - shoulder.z).toFixed(2)} m ahead of the shoulder`);
});

test('db_walking_lunge: passes every check', () => passes('db_walking_lunge'));

test('db_walking_lunge: a longer stride than the lunge, the feet planted, the dumbbells by the sides', async () => {
  const id = 'db_walking_lunge';
  const stride = async (lift) => (await boneAt(lift, 'foot_l', 1)).z - (await boneAt(lift, 'ball_r', 1)).z;
  assert.ok((await stride(id)) > (await stride('db_lunge')) + 0.05, `a stride of ${(await stride(id)).toFixed(2)} m`);
  const [ballTop, ballBottom] = [await boneAt(id, 'ball_r', 0), await boneAt(id, 'ball_r', 1)];
  assert.ok(ballTop.distanceTo(ballBottom) < 0.01, `the back ball moves ${ballTop.distanceTo(ballBottom).toFixed(3)} m`);
  assert.ok((await handleTurn(id, 1)) > 75, 'neutral');
});

test('db_curtsy_lunge: passes every check', () => passes('db_curtsy_lunge'));

test('db_curtsy_lunge: the back foot steps across behind the front one, and stays there', async () => {
  const id = 'db_curtsy_lunge';
  const [front, ball, ballBottom] = [await boneAt(id, 'foot_l', 1), await boneAt(id, 'ball_r', 0), await boneAt(id, 'ball_r', 1)];
  assert.ok(ball.x > front.x + 0.05, `the back ball ${(ball.x - front.x).toFixed(2)} m past the front ankle`);
  assert.ok(ball.z < front.z - 0.4, `the back ball ${(front.z - ball.z).toFixed(2)} m behind`);
  assert.ok(ball.distanceTo(ballBottom) < 0.01, `it moves ${ball.distanceTo(ballBottom).toFixed(3)} m`);
});

test('a lunge with no feet.cross keeps its back foot on its own side', async () => {
  assert.ok((await boneAt('db_lunge', 'ball_r', 1)).x < 0);
});

test('db_walking_lunge: at the bottom the back knee hangs just off the floor', async () => {
  const knee = await boneAt('db_walking_lunge', 'calf_r', 1);
  assert.ok(knee.y > 0.08 && knee.y < 0.2, `the back knee ${knee.y.toFixed(2)} m up`);
});

test('1_arm_db_kroc_row: passes every check', () => passes('1_arm_db_kroc_row'));

test('1_arm_db_kroc_row: standing, both feet flat on the floor, the free hand braced on the pad, one dumbbell', async () => {
  const id = '1_arm_db_kroc_row';
  const stage = await buildStage(prepare(id));
  assert.equal(stage.kit.group.getObjectByName('dumbbell_r'), undefined, 'the right hand holds none');
  for (const d of [0, 1]) {
    stage.pose(d);
    const [left, right, wrist] = [wpos(stage.rig.bones.foot_l), wpos(stage.rig.bones.foot_r), wpos(stage.rig.bones.hand_r)];
    assert.ok(left.y < 0.12 && right.y < 0.12, `at ${d}: the ankles ${left.y.toFixed(3)} and ${right.y.toFixed(3)} m up`);
    assert.ok(right.z > left.z + 0.15, `at ${d}: the right foot ${(right.z - left.z).toFixed(2)} m ahead`);
    assert.ok(wrist.y > PAD_TOP && wrist.y < PAD_TOP + 0.123, `at ${d}: the wrist ${wrist.y.toFixed(3)} m up, off the pad`);
  }
});

test('1_arm_db_kroc_row: the trunk rocks up as the dumbbell comes in, the body English the note asks for', async () => {
  const id = '1_arm_db_kroc_row';
  const [top, bottom] = [await leanAt(id, 0), await leanAt(id, 1)];
  assert.ok(bottom - top > 6, `the trunk from ${top.toFixed(0)}° to ${bottom.toFixed(0)}°`);
  const [elbow, shoulder] = [await boneAt(id, 'lowerarm_l', 0), await boneAt(id, 'upperarm_l', 0)];
  assert.ok(elbow.z < shoulder.z - 0.05, `the elbow ${(elbow.z - shoulder.z).toFixed(3)} m from the shoulder`);
  assert.ok((await handleTurn(id, 0)) > 60, 'neutral');
});

test('db_triceps_kickback: passes every check', () => passes('db_triceps_kickback'));

test('db_triceps_kickback: one arm, the upper arm held back along the trunk, the forearm swinging out straight behind', async () => {
  const id = 'db_triceps_kickback';
  const stage = await buildStage(prepare(id));
  assert.equal(stage.kit.group.getObjectByName('dumbbell_r'), undefined, 'the right hand holds none');
  const [elbowTop, elbowBottom] = [await boneAt(id, 'lowerarm_l', 0), await boneAt(id, 'lowerarm_l', 1)];
  assert.ok(elbowTop.distanceTo(elbowBottom) < 0.05, `the elbow travels ${elbowTop.distanceTo(elbowBottom).toFixed(3)} m`);
  const [shoulder, top, bottom] = [await boneAt(id, 'upperarm_l', 0), await bellAt(id, 0), await bellAt(id, 1)];
  assert.ok(elbowTop.z < shoulder.z - 0.15, `the elbow ${(elbowTop.z - shoulder.z).toFixed(2)} m behind the shoulder`);
  assert.ok(top.z < elbowTop.z - 0.2, `at the top the bell ${(top.z - elbowTop.z).toFixed(2)} m behind the elbow`);
  assert.ok(Math.abs(bottom.z - elbowBottom.z) < 0.12, `at the bottom the forearm hangs: ${(bottom.z - elbowBottom.z).toFixed(2)} m off the elbow`);
});

test('1_arm_overhead_db_triceps_extension: passes every check', () => passes('1_arm_overhead_db_triceps_extension'));

test('1_arm_overhead_db_triceps_extension: seated, the elbow pointing up and still, the dumbbell lowered behind the head', async () => {
  const id = '1_arm_overhead_db_triceps_extension';
  const stage = await buildStage(prepare(id));
  assert.equal(stage.kit.group.getObjectByName('dumbbell_r'), undefined, 'the right hand holds none');
  const [elbowTop, elbowBottom, shoulder] = [await boneAt(id, 'lowerarm_l', 0), await boneAt(id, 'lowerarm_l', 1), await boneAt(id, 'upperarm_l', 1)];
  assert.ok(elbowTop.distanceTo(elbowBottom) < 0.05, `the elbow travels ${elbowTop.distanceTo(elbowBottom).toFixed(3)} m`);
  assert.ok(elbowBottom.y > shoulder.y + 0.2, `the elbow ${(elbowBottom.y - shoulder.y).toFixed(2)} m over the shoulder`);
  const [top, bottom, head] = [await bellAt(id, 0), await bellAt(id, 1), await boneAt(id, 'Head', 1)];
  assert.ok(top.y > elbowTop.y + 0.2, `at the top the bell ${(top.y - elbowTop.y).toFixed(2)} m over the elbow`);
  assert.ok(bottom.z < head.z - 0.05, `the bell ${(head.z - bottom.z).toFixed(2)} m behind the head`);
  assert.ok((await boneAt(id, 'pelvis', 0)).y < 0.6, 'seated');
});

test('the batch lists twelve lifts, each with an entry that holds its dumbbells steady', () => {
  assert.equal(BATCHES['dumbbell-variants'].length, 12);
  for (const id of BATCHES['dumbbell-variants']) assert.equal(prepare(id).kit.dumbbells.steady, true, id);
});

noBellSpins(BATCHES['dumbbell-variants']);

// The owner's OK'd 1-arm row sets the measure: its fingertip joints stand 2.4 to 3.3 cm over the pad.
for (const id of ['1_arm_db_kroc_row', 'db_triceps_kickback']) {
  test(`${id}: the braced hand lies level on the pad, as the 1-arm row's does`, async () => {
    const stage = await buildStage(prepare(id));
    for (const d of [0, 0.5, 1]) {
      stage.pose(d);
      const tips = ['index_03_r', 'middle_03_r', 'ring_03_r', 'pinky_03_r'].map((n) => wpos(stage.rig.bones[n]).y - PAD_TOP);
      const [low, high] = [Math.min(...tips), Math.max(...tips)];
      assert.ok(low > 0.015 && high < 0.045, `at ${d}: the fingertips ${low.toFixed(3)} to ${high.toFixed(3)} m over the pad`);
      assert.ok(high - low < 0.02, `at ${d}: the hand rolls ${(high - low).toFixed(3)} m across`);
    }
  });
}

test('db_skull_crusher: the back keeps the flat press\'s arch, though the arms move as a curl\'s do', async () => {
  for (const bone of ['spine_02', 'spine_03', 'neck_01']) {
    const [crusher, press] = [await boneAt('db_skull_crusher', bone, 1), await boneAt('db_bench_press', bone, 1)];
    assert.ok(crusher.distanceTo(press) < 0.005, `${bone} ${crusher.distanceTo(press).toFixed(3)} m from the press's`);
  }
});

test('db_curtsy_lunge: the hips sink over the front foot, not back to the middle', async () => {
  const id = 'db_curtsy_lunge';
  const [hips, front] = [await boneAt(id, 'pelvis', 1), await boneAt(id, 'foot_l', 1)];
  assert.ok(Math.abs(hips.x - front.x) < 0.08, `the hips ${(hips.x - front.x).toFixed(3)} m across from the front ankle`);
});

test('1_arm_overhead_db_triceps_extension: the dumbbell ends behind the head, not out beside it', async () => {
  const id = '1_arm_overhead_db_triceps_extension';
  const [bell, head] = [await bellAt(id, 1), await boneAt(id, 'Head', 1)];
  assert.ok(bell.x - head.x < 0.15, `the bell ${(bell.x - head.x).toFixed(3)} m out from the head's middle`);
});

// The presses' 20° limit (README, Reviewing a movement): `grip.level` is what holds these under it.
for (const id of ['db_guillotine_press', 'dumbbell_shoulder_press_standing', 'standing_db_arnold_press', 'arnold_press_seated', '1_arm_db_kroc_row']) {
  test(`${id}: the dumbbell stays near level through the rep`, async () => {
    for (const angle of benchAngles(id) ?? [undefined]) {
      const stage = await buildStage(prepare(id, undefined, angle));
      const bell = stage.kit.group.getObjectByName('dumbbell_l');
      for (let f = 0; f <= 30; f++) {
        stage.pose(f / 30);
        const tilt = Math.abs(deg(Math.asin(new THREE.Vector3(1, 0, 0).applyQuaternion(bell.quaternion).y)));
        assert.ok(tilt < 20, `at ${angle ?? 'its'}°, frame ${f}: ${tilt.toFixed(1)}°`);
      }
    }
  });
}
