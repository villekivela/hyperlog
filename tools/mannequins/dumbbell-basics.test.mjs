// The dumbbell batch (#127): twelve lifts on the dumbbells and the bench, each passing every check
// and doing what the library's note on it asks.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import * as THREE from 'three';
import { benchAngles, prepare } from './entries.mjs';
import { buildStage } from './stage.mjs';
import { checkPose, CHECKS } from './checks.mjs';
import { wpos } from './rig.mjs';
import { BATCHES } from './review.mjs';
import { MOTION_TEMPOS, tempoOf } from './tempo.mjs';

const deg = (r) => (r * 180) / Math.PI;

/** Every check on `id` passes, or the test names the ones that don't. */
async function passes(id) {
  const { checks } = await checkPose(id);
  const failed = CHECKS.filter((c) => !checks[c].ok).map((c) => `${c}: ${checks[c].found} (frame ${checks[c].frame})`);
  assert.deepEqual(failed, []);
}

/** How far the left dumbbell's handle is turned from lying across the body, at `d` down the rep (0 to 90). */
async function handleTurn(id, d) {
  const stage = await buildStage(prepare(id));
  stage.pose(d);
  const bell = stage.kit.group.getObjectByName('dumbbell_l');
  return deg(Math.acos(Math.min(1, Math.abs(new THREE.Vector3(1, 0, 0).applyQuaternion(bell.quaternion).x))));
}

test('db_bicep_curl: passes every check', () => passes('db_bicep_curl'));

test('db_bicep_curl: palms neutral at the bottom, turned up by the top', async () => {
  assert.ok((await handleTurn('db_bicep_curl', 1)) > 75, 'neutral at the bottom: the handle runs along the body');
  assert.ok((await handleTurn('db_bicep_curl', 0)) < 20, 'palms up at the top: the handle lies across the body');
});

test('db_hammer_curl: passes every check', () => passes('db_hammer_curl'));

test('db_hammer_curl: palms neutral all the way', async () => {
  for (const d of [0, 0.5, 1]) assert.ok((await handleTurn('db_hammer_curl', d)) > 70, `at ${d}`);
});

/** Where the left hand's dumbbell is at `d` down the rep. */
async function bellAt(id, d) {
  const stage = await buildStage(prepare(id));
  stage.pose(d);
  stage.scene.updateMatrixWorld(true);
  return stage.kit.group.getObjectByName('dumbbell_l').getWorldPosition(new THREE.Vector3());
}

test('db_shrug: passes every check', () => passes('db_shrug'));

test('db_shrug: the dumbbells rise straight up by the sides, palms facing in', async () => {
  const [top, bottom] = [await bellAt('db_shrug', 0), await bellAt('db_shrug', 1)];
  assert.ok(top.y - bottom.y > 0.04, `they rise ${(top.y - bottom.y).toFixed(3)} m`);
  assert.ok(Math.abs(top.z - bottom.z) < 0.01, `they drift ${(top.z - bottom.z).toFixed(3)} m`);
  assert.ok((await handleTurn('db_shrug', 0)) > 75, 'neutral');
});

test('db_romanian_deadlift: passes every check', () => passes('db_romanian_deadlift'));

test('db_romanian_deadlift: the dumbbells run down the legs to mid-shin', async () => {
  const [top, bottom] = [await bellAt('db_romanian_deadlift', 0), await bellAt('db_romanian_deadlift', 1)];
  assert.ok(top.y > 0.7 && bottom.y < 0.4, `from ${top.y.toFixed(2)} to ${bottom.y.toFixed(2)} m`);
  assert.ok(Math.abs(top.z - bottom.z) < 0.01, 'straight down');
});

test('db_lateral_raise: passes every check', () => passes('db_lateral_raise'));

test('db_lateral_raise: the dumbbells go out to the sides, to shoulder height', async () => {
  const [top, bottom] = [await bellAt('db_lateral_raise', 0), await bellAt('db_lateral_raise', 1)];
  assert.ok(top.x > 0.6, `out ${top.x.toFixed(2)} m`);
  assert.ok(top.y > 1.3 && top.y < 1.55, `up ${top.y.toFixed(2)} m`);
  assert.ok(bottom.y < 0.95, `from ${bottom.y.toFixed(2)} m`);
  assert.ok(Math.abs(top.z) < 0.25, `out, not forward: ${top.z.toFixed(2)} m`);
});

/** Where bone `name` is at `d` down the rep of `id`. */
async function boneAt(id, name, d) {
  const stage = await buildStage(prepare(id));
  stage.pose(d);
  return wpos(stage.rig.bones[name]);
}

test('db_lunge: passes every check', () => passes('db_lunge'));

test('db_lunge: the feet stay planted, the left ahead, the back heel up on the ball of the foot', async () => {
  const [ballTop, ballBottom] = [await boneAt('db_lunge', 'ball_r', 0), await boneAt('db_lunge', 'ball_r', 1)];
  assert.ok(ballTop.distanceTo(ballBottom) < 0.01, `the back ball moves ${ballTop.distanceTo(ballBottom).toFixed(3)} m`);
  const [frontTop, frontBottom] = [await boneAt('db_lunge', 'foot_l', 0), await boneAt('db_lunge', 'foot_l', 1)];
  assert.ok(frontTop.distanceTo(frontBottom) < 0.01, 'the front ankle stays');
  assert.ok(frontTop.z - ballTop.z > 0.55, `the feet ${(frontTop.z - ballTop.z).toFixed(2)} m apart`);
  const heel = (await boneAt('db_lunge', 'foot_r', 1)).y;
  assert.ok(heel > frontBottom.y + 0.04, `the back ankle at ${heel.toFixed(3)} m, the front at ${frontBottom.y.toFixed(3)}`);
});

test('db_lunge: at the bottom the back knee hangs just off the floor and the front shin stands near upright', async () => {
  const backKnee = await boneAt('db_lunge', 'calf_r', 1);
  assert.ok(backKnee.y > 0.08 && backKnee.y < 0.2, `the back knee ${backKnee.y.toFixed(2)} m up`);
  const [knee, ankle] = [await boneAt('db_lunge', 'calf_l', 1), await boneAt('db_lunge', 'foot_l', 1)];
  const lean = deg(Math.atan2(knee.z - ankle.z, knee.y - ankle.y));
  assert.ok(Math.abs(lean) < 18, `the front shin leans ${lean.toFixed(0)}°`);
});

test('db_lunge: the dumbbells hang straight down by the sides, palms in', async () => {
  for (const d of [0, 1]) {
    const [bell, shoulder] = [await bellAt('db_lunge', d), await boneAt('db_lunge', 'upperarm_l', d)];
    assert.ok(Math.abs(bell.z - shoulder.z) < 0.04, `at ${d}: ${(bell.z - shoulder.z).toFixed(3)} m off the shoulder`);
    assert.ok((await handleTurn('db_lunge', d)) > 75, 'neutral');
  }
});

test('db_lateral_raise: the handle runs front to back all the way, palms in by the sides and down at the top', async () => {
  for (const d of [0, 0.5, 1]) assert.ok((await handleTurn('db_lateral_raise', d)) > 70, `at ${d}: ${(await handleTurn('db_lateral_raise', d)).toFixed(0)}°`);
});

test('db_bicep_curl: the dumbbells stop at the shoulders, below the neck', async () => {
  const [bell, neck] = [await bellAt('db_bicep_curl', 0), await boneAt('db_bicep_curl', 'neck_01', 0)];
  assert.ok(bell.y < neck.y - 0.03, `the bell ${(bell.y - neck.y).toFixed(3)} m from the neck's height`);
});

test('single_arm_db_row: passes every check', () => passes('single_arm_db_row'));

test('single_arm_db_row: one dumbbell, in the working hand', async () => {
  const stage = await buildStage(prepare('single_arm_db_row'));
  stage.pose(0);
  assert.ok(stage.kit.group.getObjectByName('dumbbell_l'), 'the left hand holds one');
  assert.equal(stage.kit.group.getObjectByName('dumbbell_r'), undefined, 'the right hand holds none');
});

test('the kit carries a bell in each hand unless the entry names one', async () => {
  const stage = await buildStage(prepare('db_bicep_curl'));
  for (const n of ['dumbbell_l', 'dumbbell_r']) assert.ok(stage.kit.group.getObjectByName(n), n);
});

test('single_arm_db_row: the elbow drives back past the trunk, palm in, the other hand braced on the front knee', async () => {
  const [elbow, shoulder] = [await boneAt('single_arm_db_row', 'lowerarm_l', 0), await boneAt('single_arm_db_row', 'upperarm_l', 0)];
  assert.ok(elbow.z < shoulder.z - 0.08, `the elbow ${(elbow.z - shoulder.z).toFixed(3)} m from the shoulder`);
  assert.ok((await handleTurn('single_arm_db_row', 0)) > 70, 'neutral');
  for (const d of [0, 1]) {
    const [hand, knee] = [await boneAt('single_arm_db_row', 'hand_r', d), await boneAt('single_arm_db_row', 'calf_r', d)];
    assert.ok(hand.distanceTo(knee) < 0.22, `at ${d}: the wrist ${hand.distanceTo(knee).toFixed(2)} m from the knee`);
  }
});

test('db_bench_press: passes every check', () => passes('db_bench_press'));

test('db_bench_press: the dumbbells come in over the shoulders at the top, out and down beside the chest', async () => {
  const [top, bottom] = [await bellAt('db_bench_press', 0), await bellAt('db_bench_press', 1)];
  assert.ok(top.y - bottom.y > 0.25, `they travel ${(top.y - bottom.y).toFixed(2)} m`);
  assert.ok(bottom.x > top.x + 0.05, `from ${top.x.toFixed(2)} out to ${bottom.x.toFixed(2)} m`);
});

test('db_flye: passes every check', () => passes('db_flye'));

test('db_flye: palms in, the arms open wide on an arc, the elbows holding their bend', async () => {
  const stage = await buildStage(prepare('db_flye'));
  const bends = [0, 0.5, 1].map((d) => {
    stage.pose(d);
    const [sh, el, wr] = ['upperarm_l', 'lowerarm_l', 'hand_l'].map((n) => wpos(stage.rig.bones[n]));
    return deg(sh.clone().sub(el).angleTo(wr.clone().sub(el)));
  });
  assert.ok(Math.max(...bends) - Math.min(...bends) < 15, `the elbow goes ${bends.map((b) => b.toFixed(0)).join(', ')}°`);
  const [top, bottom] = [await bellAt('db_flye', 0), await bellAt('db_flye', 1)];
  assert.ok(bottom.x > 0.5 && top.x < 0.25, `from ${top.x.toFixed(2)} to ${bottom.x.toFixed(2)} m out`);
  assert.ok((await handleTurn('db_flye', 0)) > 70, 'neutral');
});

test('seated_db_shoulder_press: passes every check at each bench angle', async () => {
  for (const angle of [0, 15, 30, 45, 60, 75]) {
    const { checks } = await checkPose('seated_db_shoulder_press', { angle });
    const failed = CHECKS.filter((c) => !checks[c].ok).map((c) => `${angle}°, ${c}: ${checks[c].found}`);
    assert.deepEqual(failed, []);
  }
});

test('seated_db_shoulder_press: upright by default, from beside the ears to overhead', async () => {
  assert.equal(prepare('seated_db_shoulder_press').bench.angle, 75);
  const [top, bottom] = [await bellAt('seated_db_shoulder_press', 0), await bellAt('seated_db_shoulder_press', 1)];
  const shoulder = await boneAt('seated_db_shoulder_press', 'upperarm_l', 1);
  assert.ok(top.y > shoulder.y + 0.4, `the top ${(top.y - shoulder.y).toFixed(2)} m over the shoulder`);
  assert.ok(Math.abs(bottom.y - shoulder.y) < 0.15, `the bottom ${(bottom.y - shoulder.y).toFixed(2)} m from the shoulder's height`);
});

test('the library\'s notes set the pauses: a second at the bottom of the press, a held shrug, no pause carried to the flye', () => {
  assert.equal(tempoOf(prepare('db_bench_press')).bottom, 1);
  assert.equal(tempoOf(prepare('db_flye')).bottom, MOTION_TEMPOS.free_arms.bottom);
  assert.equal(tempoOf(prepare('db_shrug')).top, 1);
});

test('the lunge lowers over two seconds and lifts in one', () => {
  assert.deepEqual(tempoOf(prepare('db_lunge')), { lowering: 2, bottom: 0.3, lifting: 1, top: 0.6 });
});

test('the batch lists ten lifts, each with an entry', () => {
  assert.equal(BATCHES.dumbbell.length, 10);
  for (const id of BATCHES.dumbbell) assert.ok(prepare(id).kit.dumbbells, id);
});

// A bell's turn between frames follows the forearm, about 8° at a curl's fastest. Past that it is
// spinning about its own handle, which the jump check misses where the spin is steady.
const STEP = 10;
for (const id of BATCHES.dumbbell) {
  test(`${id}: no dumbbell spins about its handle`, async () => {
    for (const angle of benchAngles(id) ?? [undefined]) {
      const stage = await buildStage(prepare(id, undefined, angle));
      const before = {};
      for (let f = 0; f <= 30; f++) {
        stage.pose(f / 30);
        for (const n of ['dumbbell_l', 'dumbbell_r']) {
          const q = stage.kit.group.getObjectByName(n)?.quaternion.clone();
          if (before[n]) assert.ok(deg(q.angleTo(before[n])) < STEP, `${n} at ${angle ?? 'its'}°, frame ${f}: ${deg(q.angleTo(before[n])).toFixed(1)}°`);
          before[n] = q;
        }
      }
    }
  });
}
