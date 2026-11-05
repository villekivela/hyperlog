// The second dumbbell batch (#127): variants of the lifts the owner has OK'd, each `like:` one of them
// or of another variant, each passing every check and doing what the library's note on it asks.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import * as THREE from 'three';
import { BENCH_ANGLES, benchAngles, prepare } from './entries.mjs';
import { buildStage } from './stage.mjs';
import { wpos } from './rig.mjs';
import { BATCHES } from './review.mjs';
import { tempoOf } from './tempo.mjs';
import { bellAt, boneAt, deg, handleTurn, noBellSpins, passes } from './dumbbell.fixture.mjs';

const PAD_TOP = 0.457;

/** How far the trunk of `id` leans forward of upright at `d` down the rep, in degrees. */
async function leanAt(id, d) {
  const [hips, neck] = [await boneAt(id, 'pelvis', d), await boneAt(id, 'neck_01', d)];
  return deg(Math.atan2(neck.z - hips.z, neck.y - hips.y));
}

test('super_rom_db_lateral_raise: passes every check', () => passes('super_rom_db_lateral_raise'));

test('super_rom_db_lateral_raise: the dumbbells go on past the shoulders until they are overhead', async () => {
  const [top, bottom] = [await bellAt('super_rom_db_lateral_raise', 0), await bellAt('super_rom_db_lateral_raise', 1)];
  const head = await boneAt('super_rom_db_lateral_raise', 'Head', 0);
  assert.ok(top.y > head.y + 0.15, `the bell ${(top.y - head.y).toFixed(2)} m over the head`);
  assert.ok(top.x < 0.4, `the bell ${top.x.toFixed(2)} m out at the top`);
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

test('1_arm_db_kroc_row: the right knee and hand on the bench, as the 1-arm row\'s are, one dumbbell', async () => {
  const id = '1_arm_db_kroc_row';
  const stage = await buildStage(prepare(id));
  assert.equal(stage.kit.group.getObjectByName('dumbbell_r'), undefined, 'the right hand holds none');
  for (const d of [0, 1]) {
    stage.pose(d);
    const [knee, ankle, left, wrist] = ['calf_r', 'foot_r', 'foot_l', 'hand_r'].map((n) => wpos(stage.rig.bones[n]));
    assert.ok(knee.y > PAD_TOP && knee.y < PAD_TOP + 0.09, `at ${d}: the knee ${(knee.y - PAD_TOP).toFixed(3)} m over the pad`);
    assert.ok(Math.abs(ankle.y - knee.y) < 0.08, `at ${d}: the shin drops ${(knee.y - ankle.y).toFixed(3)} m, off the pad`);
    assert.ok(left.y < 0.12, `at ${d}: the left ankle ${left.y.toFixed(3)} m up`);
    assert.ok(wrist.y > PAD_TOP && wrist.y < PAD_TOP + 0.123, `at ${d}: the wrist ${wrist.y.toFixed(3)} m up, off the pad`);
  }
});

test('1_arm_db_kroc_row: the braced hand points along the bench, as the 1-arm row\'s does', async () => {
  const stage = await buildStage(prepare('1_arm_db_kroc_row'));
  for (const d of [0, 0.5, 1]) {
    stage.pose(d);
    const [wrist, knuckle] = [wpos(stage.rig.bones.hand_r), wpos(stage.rig.bones.middle_01_r)];
    const heading = deg(Math.atan2(knuckle.x - wrist.x, knuckle.z - wrist.z));
    assert.ok(Math.abs(heading) < 30, `at ${d}: the fingers point ${heading.toFixed(0)}° off the bench's line`);
  }
});

test('1_arm_db_kroc_row: the trunk rocks up as the dumbbell comes in, the body English the note asks for', async () => {
  const [top, bottom] = [await leanAt('1_arm_db_kroc_row', 0), await leanAt('1_arm_db_kroc_row', 1)];
  assert.ok(bottom - top > 18, `the trunk from ${top.toFixed(0)}° to ${bottom.toFixed(0)}°`);
});

test('1_arm_db_kroc_row: the elbow drives back past the trunk, palm in', async () => {
  const id = '1_arm_db_kroc_row';
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

/** How far a trunk near level is twisted about the spine at `d` down the rep, in degrees: positive with the left shoulder up. */
async function shouldersTurn(id, d) {
  const [left, right] = [await boneAt(id, 'upperarm_l', d), await boneAt(id, 'upperarm_r', d)];
  return deg(Math.atan2(left.y - right.y, left.x - right.x));
}

test('1_arm_db_kroc_row: the trunk twists, the chest open to the rowing arm at the top and the shoulder reaching at the bottom', async () => {
  const [top, bottom] = [await shouldersTurn('1_arm_db_kroc_row', 0), await shouldersTurn('1_arm_db_kroc_row', 1)];
  assert.ok(top > 22, `at the top the shoulders turn ${top.toFixed(1)}°`);
  assert.ok(bottom < -22, `at the bottom the shoulders turn ${bottom.toFixed(1)}°`);
});

test('db_triceps_kickback: the trunk stays square, taking no twist from the Kroc row', async () => {
  for (const d of [0, 1]) {
    const turn = await shouldersTurn('db_triceps_kickback', d);
    assert.ok(Math.abs(turn) < 3, `at ${d}: the shoulders turn ${turn.toFixed(1)}°`);
  }
});

test('1_arm_db_kroc_row: looser than the 1-arm row, the dumbbell travelling further and heaved up fast', async () => {
  const travel = async (id) => (await bellAt(id, 0)).distanceTo(await bellAt(id, 1));
  const [kroc, row] = [await travel('1_arm_db_kroc_row'), await travel('single_arm_db_row')];
  assert.ok(kroc > row + 0.08, `the bell travels ${kroc.toFixed(2)} m, the row's ${row.toFixed(2)} m`);
  const [fast, strict] = [tempoOf(prepare('1_arm_db_kroc_row')), tempoOf(prepare('single_arm_db_row'))];
  assert.ok(fast.lifting < strict.lifting * 0.75, `lifted in ${fast.lifting} s, the row in ${strict.lifting} s`);
});

/** The left dumbbell's size at rest: [along the handle, across its head], in metres. */
async function bellSize(id) {
  const stage = await buildStage(prepare(id));
  const bell = stage.kit.group.getObjectByName('dumbbell_l');
  bell.quaternion.identity();
  bell.updateMatrixWorld(true);
  const size = new THREE.Box3().setFromObject(bell).getSize(new THREE.Vector3());
  return [size.x, size.y];
}

test('1_arm_db_kroc_row: the dumbbell is a heavy one, its heads bigger with the handle left as it is', async () => {
  const [[long, wide], [rowLong, rowWide]] = [await bellSize('1_arm_db_kroc_row'), await bellSize('single_arm_db_row')];
  assert.ok(wide > rowWide * 1.3, `the head ${wide.toFixed(3)} m across, the row's ${rowWide.toFixed(3)} m`);
  assert.ok(long > rowLong + 0.05, `the bell ${long.toFixed(3)} m long, the row's ${rowLong.toFixed(3)} m`);
});

test('1_arm_overhead_db_triceps_extension: the resting arm lies across the body, its elbow down by the ribs', async () => {
  const id = '1_arm_overhead_db_triceps_extension';
  const [elbow, shoulder, wrist] = [await boneAt(id, 'lowerarm_r', 1), await boneAt(id, 'upperarm_r', 1), await boneAt(id, 'hand_r', 1)];
  assert.ok(elbow.y < shoulder.y - 0.15, `the elbow ${(elbow.y - shoulder.y).toFixed(2)} m from the shoulder's height`);
  assert.ok(elbow.z - shoulder.z < 0.2, `the elbow ${(elbow.z - shoulder.z).toFixed(2)} m ahead of the shoulder`);
  assert.ok(wrist.x > -0.05, `the hand ${wrist.x.toFixed(2)} m across`);
});

test('1_arm_overhead_db_triceps_extension: the resting palm lies on the chest, the fingers pointing across it', async () => {
  const stage = await buildStage(prepare('1_arm_overhead_db_triceps_extension'));
  stage.pose(1);
  const [wrist, knuckle, tip, index, pinky] = ['hand_r', 'middle_01_r', 'middle_03_r', 'index_01_r', 'pinky_01_r'].map((n) => wpos(stage.rig.bones[n]).clone());
  const fingers = knuckle.clone().sub(wrist).normalize();
  const palm = fingers.clone().cross(index.clone().sub(pinky)).normalize();
  if (palm.dot(tip.clone().sub(knuckle)) < 0) palm.negate();
  assert.ok(palm.z < -0.8, `the palm faces ${palm.toArray().map((v) => v.toFixed(2))}, not the chest`);
  assert.ok(fingers.x > 0.7, `the fingers point ${fingers.toArray().map((v) => v.toFixed(2))}, not across`);
  assert.ok(knuckle.y > 0.93 && knuckle.y < 1.1, `the hand ${knuckle.y.toFixed(2)} m up, not on the chest`);
  assert.ok(knuckle.z < 0.4, `the knuckles at z ${knuckle.z.toFixed(2)}, off the chest`);
});

test('db_curtsy_lunge: the back foot lands well out past the line through the hips', async () => {
  const id = 'db_curtsy_lunge';
  const [ball, hips] = [await boneAt(id, 'ball_r', 1), await boneAt(id, 'pelvis', 1)];
  assert.ok(ball.x - hips.x > 0.2, `the back ball ${(ball.x - hips.x).toFixed(2)} m past the hips`);
});

test('db_triceps_kickback: the bench\'s back stands near upright ahead, the right hand over its top, as in the video', async () => {
  const id = 'db_triceps_kickback';
  assert.ok(prepare(id).kit.bench.angle >= 80, 'the back pad near upright');
  const stage = await buildStage(prepare(id));
  const back = new THREE.Box3().setFromObject(stage.kit.group.getObjectByName('bench') ?? stage.kit.group);
  for (const d of [0, 1]) {
    stage.pose(d);
    const [wrist, shoulder] = [wpos(stage.rig.bones.hand_r), wpos(stage.rig.bones.upperarm_r)];
    assert.ok(Math.abs(wrist.y - back.max.y) < 0.12, `at ${d}: the wrist ${(wrist.y - back.max.y).toFixed(3)} m from the pad's top`);
    assert.ok(wrist.z > shoulder.z + 0.35, `at ${d}: the hand ${(wrist.z - shoulder.z).toFixed(2)} m ahead of the shoulder`);
    // Our back pad stands taller than the video's, so the arm reaches up a little to its top.
    assert.ok(Math.abs(wrist.y - shoulder.y) < 0.28, `at ${d}: the arm reaches out near level, ${(wrist.y - shoulder.y).toFixed(2)} m off`);
  }
});

test('db_triceps_kickback: a long split, the front knee bent and the back leg near straight', async () => {
  const id = 'db_triceps_kickback';
  const stage = await buildStage(prepare(id));
  stage.pose(1);
  const bend = (hip, knee, ankle) => deg(hip.clone().sub(knee).angleTo(ankle.clone().sub(knee)));
  const [hipL, kneeL, ankleL, hipR, kneeR, ankleR] = ['thigh_l', 'calf_l', 'foot_l', 'thigh_r', 'calf_r', 'foot_r'].map((n) => wpos(stage.rig.bones[n]).clone());
  assert.ok(ankleR.z - ankleL.z > 0.6, `the feet ${(ankleR.z - ankleL.z).toFixed(2)} m apart`);
  assert.ok(bend(hipL, kneeL, ankleL) > 155, `the back knee at ${bend(hipL, kneeL, ankleL).toFixed(0)}°`);
  assert.ok(bend(hipR, kneeR, ankleR) < 145, `the front knee at ${bend(hipR, kneeR, ankleR).toFixed(0)}°`);
});

test('db_triceps_kickback: the upper arm stays up level with the back, the forearm hanging at the bottom', async () => {
  const id = 'db_triceps_kickback';
  const [shoulder, elbow, wrist] = [await boneAt(id, 'upperarm_l', 1), await boneAt(id, 'lowerarm_l', 1), await boneAt(id, 'hand_l', 1)];
  assert.ok(elbow.y > shoulder.y - 0.05, `the elbow ${(elbow.y - shoulder.y).toFixed(2)} m from the shoulder's height`);
  const bendAt = deg(shoulder.clone().sub(elbow).angleTo(wrist.clone().sub(elbow)));
  assert.ok(bendAt < 110, `the elbow closes to ${bendAt.toFixed(0)}°`);
});
