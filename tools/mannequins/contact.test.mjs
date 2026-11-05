// Every grip is solved to its bar (#162): the palm rests on the bar's surface at its real
// diameter, and each finger and the thumb close until they touch it. The contact check fails a hand
// that stands off its bar, or sinks into it, by more than 2 mm, on any frame.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { prepare } from './entries.mjs';
import { buildStage } from './stage.mjs';
import { checkStage, contactsOf } from './checks.mjs';
import { frameAt } from './motions.mjs';
import { drawn, holdOf, partUnder } from './grip.mjs';
import { POSES } from './poses.mjs';
import { gripTarget, wpos } from './rig.mjs';

// The motions read only the palm's length and the limbs' from the rig; any mannequin's will do.
const rigDims = (await buildStage(prepare('deadlift'))).rig.dims;

test('the deadlift\'s hands meet the bar on every frame: the contact check passes', async () => {
  const entry = prepare('deadlift');
  const checks = checkStage(entry, await buildStage(entry));
  assert.ok(checks.contact, 'no contact check');
  assert.ok(checks.contact.ok, `frame ${checks.contact.frame}: ${checks.contact.found}`);
});

// Each grip names where the bar sits in the hand and the thumb (docs/research/grip.md, section 5).
test('every grip has a bar line and a thumb, by default or from its entry', () => {
  const holdAt = (id, grip) => frameAt(prepare(id, POSES, undefined, grip), 0, rigDims).hold;
  for (const [id, e] of Object.entries(POSES)) {
    for (const grip of Object.keys(e.grips ?? {}).length ? Object.keys(e.grips) : [undefined]) {
      const { sits, thumb } = holdAt(id, grip);
      assert.ok(['heel', 'mid', 'base', 'open'].includes(sits), `${id}: sits ${sits}`);
      assert.ok(['wrapped', 'thumbless', 'open'].includes(thumb), `${id}: thumb ${thumb}`);
    }
  }
  assert.deepEqual(holdAt('floor_press'), { sits: 'heel', thumb: 'wrapped' });
  assert.deepEqual(holdAt('deadlift'), { sits: 'base', thumb: 'wrapped' });
  assert.deepEqual(holdAt('barbell_curl'), { sits: 'mid', thumb: 'wrapped', swing: 40 });
  assert.deepEqual(holdAt('triceps_pressdown'), { sits: 'mid', thumb: 'wrapped' });
  assert.deepEqual(holdAt('back_squat_low_bar'), { sits: 'heel', thumb: 'thumbless' });
  assert.deepEqual(holdAt('front_squat').sits, 'base');
});

test('a grip that names no landmark or thumb the hand knows won\'t pose', () => {
  assert.throws(() => holdOf({ motion: 'hinge', grip: { sits: 'wrist' } }), /No grip landmark "wrist"/);
  assert.throws(() => holdOf({ motion: 'hinge', grip: { thumb: 'hooked' } }), /No thumb "hooked"/);
  assert.throws(() => holdOf({ motion: 'hinge', grip: { swing: 91 } }), /grip.swing 91/);
  assert.throws(() => holdOf({ motion: 'hinge', grip: { swing: -1 } }), /grip.swing -1/);
  assert.deepEqual(holdOf({ motion: 'squat', grip: { swing: 50 } }), { sits: 'mid', thumb: 'wrapped', swing: 50 });
});

test('the bar sits lower in the palm for a press than for a pull, and each hand closes to its own part\'s radius', async () => {
  const bench = await buildStage(prepare('barbell_bench_press'));
  const pull = await buildStage(prepare('deadlift'));
  const press = await buildStage(prepare('machine_chest_press'));
  bench.pose(0.5);
  pull.pose(0.5);
  const P = press.pose(0.5);
  assert.ok(bench.rig.depth.l < pull.rig.depth.l - 0.01, `bench ${bench.rig.depth.l}, deadlift ${pull.rig.depth.l}`);
  // The chest press's handles are 41 mm vinyl sleeves; a barbell is 28 mm.
  assert.ok(Math.abs(P.radius[1] - 0.0205) < 1e-4, `chest press handle radius ${P.radius[1]}`);
  const checks = checkStage(prepare('machine_chest_press'), press);
  assert.ok(checks.contact.ok, `frame ${checks.contact.frame}: ${checks.contact.found}`);
});

// The studio's hand close-up marks where the hand misses its bar.
test('where a finger stands off its bar, the close-up has a mark on that finger', async () => {
  const opened = { index_02_l: [0, 0, 30] };
  const entry = prepare('opened', { ...POSES, opened: { like: 'deadlift', top: { offsets: opened }, bottom: { offsets: opened } } });
  const stage = await buildStage(entry);
  const P = stage.pose(0);
  // The index finger, and the thumb if it rested on it.
  const misses = contactsOf(stage, P, 'l').filter((c) => c.past > 0);
  assert.ok(misses.every((c) => /the l (index finger|thumb)/.test(c.found)), misses.map((c) => c.found).join('; '));
  const index = misses.find((c) => /index/.test(c.found));
  const near = Math.min(...['01', '02', '03', '04_leaf'].map((j) => wpos(stage.rig.bones[`index_${j}_l`]).distanceTo(index.at)));
  // On the finger's skin: its joints lie near its back, and it is 3.5 cm thick.
  assert.ok(near < 0.035, `the mark is ${(near * 100).toFixed(1)} cm from the index finger's nearest joint`);
  assert.deepEqual(contactsOf(stage, P, 'r').filter((c) => c.past > 0), []);
});

// The coordinator's look at the curl (#184): a wrapped thumb lies round the bar, its first phalanx
// on it and its tip on it or on the index and middle fingers where they come round it (research,
// section 1.2), not standing up off it with only its tip on the fingers.
for (const [id, d] of [['barbell_curl', 1], ['deadlift', 1], ['barbell_bench_press', 0], ['lat_pulldown', 1]]) {
  test(`${id}'s thumb wraps the bar at the ${d ? 'bottom' : 'top'}: its first phalanx on it, its tip on it or the fingers`, async () => {
    const stage = await buildStage(prepare(id));
    const P = stage.pose(d);
    const at = drawn();
    const skin = (k, bone) => stage.rig.skin[k][bone].map((p) => at(p));
    for (const [k, s] of [['l', 1], ['r', -1]]) {
      const part = partUnder(stage.kit, gripTarget(P, s));
      const off = (bone) => Math.min(...skin(k, bone).map((p) => {
        const v = p.clone().sub(part.at);
        const ax = v.dot(part.along);
        return Math.sqrt(v.lengthSq() - ax * ax) - part.radius;
      }));
      const first = off(`thumb_02_${k}`);
      assert.ok(first < 0.003, `thumb_02_${k} ${(first * 1000).toFixed(1)} mm off the bar`);
      const fingers = ['index_02', 'index_03', 'middle_02', 'middle_03'].flatMap((b) => skin(k, `${b}_${k}`));
      const onFingers = Math.min(...skin(k, `thumb_03_${k}`).map((p) => Math.min(...fingers.map((q) => p.distanceTo(q)))));
      const tip = Math.min(off(`thumb_03_${k}`), onFingers);
      assert.ok(tip < 0.003, `thumb_03_${k} ${(tip * 1000).toFixed(1)} mm off the bar and the fingers`);
    }
  });
}
