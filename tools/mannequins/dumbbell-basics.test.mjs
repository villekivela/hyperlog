// The dumbbell batch (#127): ten lifts on the dumbbells and the bench, each passing every check and
// doing what the library's note on it asks.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import * as THREE from 'three';
import { BENCH_ANGLES, benchAngles, prepare, resolve } from './entries.mjs';
import { buildStage } from './stage.mjs';
import { checkPose, CHECKS } from './checks.mjs';
import { heldAt, wpos } from './rig.mjs';
import { rangeOf, tunables, withoutChoices } from './tune.mjs';
import { patchPoses } from './patch.mjs';
import { BATCHES } from './review.mjs';
import { MOTION_TEMPOS, tempoOf } from './tempo.mjs';

const deg = (r) => (r * 180) / Math.PI;

/** Every check on `id` passes, at bench `angle` if it has one, or the test names the ones that don't. */
async function passes(id, angle) {
  const { checks } = await checkPose(id, { angle });
  const failed = CHECKS.filter((c) => !checks[c].ok).map((c) => `${angle ?? ''} ${c}: ${checks[c].found} (frame ${checks[c].frame})`);
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
  assert.ok(top.x > 2 * Math.abs(top.z), `out, not forward: ${top.x.toFixed(2)} m out, ${top.z.toFixed(2)} m forward`);
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

test('db_lateral_raise: palms in by the sides', async () => {
  const turn = await handleTurn('db_lateral_raise', 1);
  assert.ok(turn > 60, `at the bottom: ${turn.toFixed(0)}°`);
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

test('single_arm_db_row: the elbow drives back past the trunk, palm in', async () => {
  const [elbow, shoulder] = [await boneAt('single_arm_db_row', 'lowerarm_l', 0), await boneAt('single_arm_db_row', 'upperarm_l', 0)];
  assert.ok(elbow.z < shoulder.z - 0.08, `the elbow ${(elbow.z - shoulder.z).toFixed(3)} m from the shoulder`);
  assert.ok((await handleTurn('single_arm_db_row', 0)) > 70, 'neutral');
});

test('db_bench_press: passes every check', () => passes('db_bench_press'));

test('db_bench_press: the dumbbells come in over the shoulders at the top, out and down beside the chest', async () => {
  const [top, bottom] = [await bellAt('db_bench_press', 0), await bellAt('db_bench_press', 1)];
  assert.ok(top.y - bottom.y > 0.25, `they travel ${(top.y - bottom.y).toFixed(2)} m`);
  assert.ok(bottom.x > top.x, `from ${top.x.toFixed(2)} out to ${bottom.x.toFixed(2)} m`);
});

test('db_flye: passes every check', () => passes('db_flye'));

test('db_flye: palms in, the arms open wide on an arc, the elbows easing open as tuned', async () => {
  const stage = await buildStage(prepare('db_flye'));
  const bends = [0, 0.5, 1].map((d) => {
    stage.pose(d);
    const [sh, el, wr] = ['upperarm_l', 'lowerarm_l', 'hand_l'].map((n) => wpos(stage.rig.bones[n]));
    return deg(sh.clone().sub(el).angleTo(wr.clone().sub(el)));
  });
  assert.ok(Math.max(...bends) - Math.min(...bends) < 50, `the elbow goes ${bends.map((b) => b.toFixed(0)).join(', ')}°`);
  const [top, bottom] = [await bellAt('db_flye', 0), await bellAt('db_flye', 1)];
  assert.ok(bottom.x > 0.5 && top.x < 0.25, `from ${top.x.toFixed(2)} to ${bottom.x.toFixed(2)} m out`);
  assert.ok((await handleTurn('db_flye', 0)) > 70, 'neutral');
});

test('seated_db_shoulder_press: passes every check at each bench angle', async () => {
  for (const angle of BENCH_ANGLES) await passes('seated_db_shoulder_press', angle);
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
const MAX_TURN = 10;
for (const id of BATCHES.dumbbell) {
  test(`${id}: no dumbbell spins about its handle`, async () => {
    for (const angle of benchAngles(id) ?? [undefined]) {
      const stage = await buildStage(prepare(id, undefined, angle));
      const before = {};
      for (let f = 0; f <= 30; f++) {
        stage.pose(f / 30);
        for (const n of ['dumbbell_l', 'dumbbell_r']) {
          const q = stage.kit.group.getObjectByName(n)?.quaternion.clone();
          const turn = before[n] ? deg(q.angleTo(before[n])) : 0;
          assert.ok(turn < MAX_TURN, `${n} at ${angle ?? 'its'}°, frame ${f}: ${turn.toFixed(1)}°`);
          before[n] = q;
        }
      }
    }
  });
}

test('the studio offers the back heel in degrees', () => {
  assert.equal(rangeOf('top.heel', 25).unit, '°');
});

test('db_lunge: the back toes stay down at whatever height the heel is tuned to', async () => {
  const entry = prepare('db_lunge');
  const poses = { t: { ...entry, top: { ...entry.top, heel: 40 } } };
  const { checks } = await checkPose('t', { poses });
  assert.ok(checks.equipment.ok, checks.equipment.found);
});

test('db_lunge: the hips drop straight down, over the front half of the stance', async () => {
  const [top, bottom] = [await boneAt('db_lunge', 'pelvis', 0), await boneAt('db_lunge', 'pelvis', 1)];
  assert.ok(Math.abs(top.z - bottom.z) < 0.02, `they drift ${(bottom.z - top.z).toFixed(3)} m`);
  assert.ok(bottom.z >= 0, `at z ${bottom.z.toFixed(3)}, toward the back foot`);
});

const PAD_TOP = 0.457;

test('single_arm_db_row: the right knee and shin lie on the flat bench, the right hand planted ahead of them', async () => {
  const stage = await buildStage(prepare('single_arm_db_row'));
  const padX = prepare('single_arm_db_row').kit.bench.at[0];
  for (const d of [0, 1]) {
    stage.pose(d);
    const [knee, ankle, wrist] = [wpos(stage.rig.bones.calf_r), wpos(stage.rig.bones.foot_r), wpos(stage.rig.bones.hand_r)];
    assert.ok(knee.y > PAD_TOP && knee.y < PAD_TOP + 0.09, `at ${d}: the knee ${(knee.y - PAD_TOP).toFixed(3)} m over the pad`);
    assert.ok(Math.abs(knee.x - padX) < 0.15, `at ${d}: the knee ${(knee.x - padX).toFixed(3)} m across, off the pad`);
    assert.ok(Math.abs(ankle.y - knee.y) < 0.08, `at ${d}: the shin drops ${(knee.y - ankle.y).toFixed(3)} m`);
    assert.ok(wrist.z > knee.z + 0.3, `at ${d}: the hand ${(wrist.z - knee.z).toFixed(2)} m ahead of the knee`);
    assert.ok(wrist.y > PAD_TOP && wrist.y < PAD_TOP + 0.12, `at ${d}: the wrist ${(wrist.y - PAD_TOP).toFixed(3)} m over the pad`);
    assert.ok(Math.abs(wrist.x - padX) < 0.17, `at ${d}: the wrist ${(wrist.x - padX).toFixed(3)} m across, off the pad`);
  }
});

test('single_arm_db_row: the left foot stands flat on the floor beside the bench, the trunk near level', async () => {
  const stage = await buildStage(prepare('single_arm_db_row'));
  stage.pose(1);
  const [ankle, hips, neck] = [wpos(stage.rig.bones.foot_l), wpos(stage.rig.bones.pelvis), wpos(stage.rig.bones.neck_01)];
  assert.ok(ankle.x > 0.17, `the foot ${ankle.x.toFixed(3)} m out, under the pad`);
  assert.ok(ankle.y < 0.12, `the ankle ${ankle.y.toFixed(3)} m up`);
  const lean = deg(Math.atan2(neck.y - hips.y, neck.z - hips.z));
  assert.ok(Math.abs(lean) < 25, `the trunk ${lean.toFixed(0)}° off level`);
});

test('seated_db_shoulder_press: a setting tuned per angle in the studio leaves it no one else\'s', async () => {
  const text = await readFile(new URL('./poses.mjs', import.meta.url), 'utf8');
  const saved = patchPoses(text, 'seated_db_shoulder_press', { '@angles': { 'grip.width': { 0: 0.4, 75: 0.44 } } });
  const { POSES } = await import(`data:text/javascript,${encodeURIComponent(saved)}`);
  assert.deepEqual(Object.keys(resolve('seated_db_shoulder_press', POSES).bench.perAngle), ['grip.width']);
});

test('seated_db_shoulder_press: the arms lock out straight up at every bench angle, 85° among them', async () => {
  assert.ok(benchAngles('seated_db_shoulder_press').includes(85));
  for (const angle of BENCH_ANGLES) {
    const stage = await buildStage(prepare('seated_db_shoulder_press', undefined, angle));
    stage.pose(0);
    const [shoulder, hand] = [wpos(stage.rig.bones.upperarm_l), wpos(stage.rig.bones.hand_l)];
    assert.ok(hand.y - shoulder.y > 0.4, `at ${angle}°: the hand ${(hand.y - shoulder.y).toFixed(2)} m over the shoulder`);
    assert.ok(Math.abs(hand.z - shoulder.z) < 0.15, `at ${angle}°: the hand ${(hand.z - shoulder.z).toFixed(2)} m from over the shoulder`);
  }
});

// `grip.level` on a press with no tune of its own, so what it shows is the setting's and nobody's numbers.
async function pressAt(level) {
  const entry = prepare('db_bench_press');
  const t = {
    ...entry,
    top: { hand: [0.2, 1.147, -0.2], rotate: 20 },
    bottom: { hand: [0.42, 0.777, -0.1], rotate: 20 },
    grip: { sits: 'heel', pole: [0.9, -1, 0.1], level },
  };
  const stage = await buildStage(prepare('t', { t }));
  const [tips, bends] = [[], []];
  for (let f = 0; f <= 30; f++) {
    stage.pose(f / 30);
    const handle = new THREE.Vector3(1, 0, 0).applyQuaternion(stage.kit.group.getObjectByName('dumbbell_l').quaternion);
    tips.push(deg(Math.asin(Math.abs(handle.y))));
    const [fore, hand, knuckle] = ['lowerarm_l', 'hand_l', 'middle_01_l'].map((n) => wpos(stage.rig.bones[n]));
    bends.push(deg(knuckle.clone().sub(hand).angleTo(hand.clone().sub(fore))));
  }
  return { tips, bends };
}

test('grip.level 0: the wrist stays straight and the dumbbell tips with the forearm', async () => {
  const { tips, bends } = await pressAt(0);
  assert.ok(Math.max(...bends) < 1, `the wrist bends ${Math.max(...bends).toFixed(1)}°`);
  assert.ok(Math.max(...tips) > 15, `it tips ${Math.max(...tips).toFixed(1)}°`);
});

test('grip.level 1: the dumbbell holds level and the wrist takes all the lean', async () => {
  const { tips, bends } = await pressAt(1);
  assert.ok(Math.max(...tips) < 1, `it tips ${Math.max(...tips).toFixed(1)}°`);
  assert.ok(Math.max(...bends) > 15, `the wrist bends ${Math.max(...bends).toFixed(1)}°`);
});

test('grip.level 0.7: the wrist flows, bending most mid-rep, and the dumbbell rocks a little', async () => {
  const { tips, bends } = await pressAt(0.7);
  const [top, bottom, most] = [bends[0], bends[30], Math.max(...bends)];
  assert.ok(most > 10 && most < 25, `the wrist bends at most ${most.toFixed(1)}°`);
  assert.ok(most > top + 2 && most > bottom + 2, `${top.toFixed(1)}° at the top, ${most.toFixed(1)}° at most, ${bottom.toFixed(1)}° at the bottom`);
  assert.ok(Math.max(...tips) > 3 && Math.max(...tips) < 10, `it tips at most ${Math.max(...tips).toFixed(1)}°`);
  assert.ok(Math.max(...tips) - Math.min(...tips) > 2, 'it moves through the rep');
});

test('the studio offers the wrist\'s share of the forearm\'s lean, from none to all of it', () => {
  assert.deepEqual(rangeOf('grip.level', 0.7), { min: 0, max: 1, step: 0.05, unit: '' });
});

test('db_incline_press: at every bench angle no dumbbell spins, and the wrist flows', async () => {
  for (const angle of BENCH_ANGLES) {
    const stage = await buildStage(prepare('db_incline_press', undefined, angle));
    const before = {};
    let bend = 0;
    for (let f = 0; f <= 30; f++) {
      stage.pose(f / 30);
      for (const n of ['dumbbell_l', 'dumbbell_r']) {
        const q = stage.kit.group.getObjectByName(n).quaternion.clone();
        const turn = before[n] ? deg(q.angleTo(before[n])) : 0;
        assert.ok(turn < MAX_TURN, `${n} at ${angle}°, frame ${f}: ${turn.toFixed(1)}°`);
        before[n] = q;
      }
      const [fore, hand, knuckle] = ['lowerarm_l', 'hand_l', 'middle_01_l'].map((b) => wpos(stage.rig.bones[b]));
      bend = Math.max(bend, deg(knuckle.clone().sub(hand).angleTo(hand.clone().sub(fore))));
    }
    assert.ok(bend > 10, `at ${angle}° the wrist bends at most ${bend.toFixed(1)}°`);
  }
});

// A gripped bell turns with the hand. `tilt` may tip it a little in the palm between the ends.
for (const id of [...BATCHES.dumbbell, 'db_incline_press']) {
  test(`${id}: the dumbbell doesn't turn in the hand`, async () => {
    const stage = await buildStage(prepare(id));
    for (const k of ['l', 'r']) {
      const bell = stage.kit.group.getObjectByName(`dumbbell_${k}`);
      if (!bell) continue;
      const inHand = () => {
        stage.scene.updateMatrixWorld(true);
        return stage.rig.bones[`hand_${k}`].getWorldQuaternion(new THREE.Quaternion()).invert().multiply(bell.getWorldQuaternion(new THREE.Quaternion()));
      };
      stage.pose(0);
      const top = inHand();
      stage.pose(1);
      const turn = deg(inHand().angleTo(top));
      assert.ok(turn < 20, `the ${k} dumbbell turns ${turn.toFixed(0)}° in the hand`);
    }
  });
}

test('the bench stands where `at` [x, z] puts it, and Tune offers both numbers for the row', async () => {
  const entry = prepare('single_arm_db_row');
  const poses = { t: { ...entry, kit: { ...entry.kit, bench: { at: [0.12, -0.2] } } } };
  const stage = await buildStage(prepare('t', poses));
  stage.pose(0);
  const bench = stage.kit.kits.bench.group;
  assert.deepEqual([bench.position.x, bench.position.z], [0.12, -0.2]);
  const paths = tunables(entry).map((t) => t.path);
  for (const p of ['kit.bench.at.0', 'kit.bench.at.1']) assert.ok(paths.includes(p), p);
});

test('a hand choice left in the studio\'s unsaved numbers is dropped, and the rest kept', () => {
  assert.deepEqual(withoutChoices({ 'kit.dumbbells.hand': 0.4, 'top.hand.0': 0.3, 'kit.bench.at.0': 0.1 }), { 'top.hand.0': 0.3, 'kit.bench.at.0': 0.1 });
});
