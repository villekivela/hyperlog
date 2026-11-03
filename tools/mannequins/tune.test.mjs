// Which numbers of a pose entry the review page's Tune panel offers as sliders, and how a tuned
// value goes into an entry.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { prepare } from './entries.mjs';
import { tunables, applyTuned, headingOf, HEADINGS, withTuned, labelOf } from './tune.mjs';

const byPath = (list) => Object.fromEntries(list.map((t) => [t.path, t]));

test('the bench press offers its own numbers, points one element at a time, and not the camera', () => {
  const t = byPath(tunables(prepare('barbell_bench_press')));
  assert.equal(t['grip.width'].value, 0.4);
  assert.equal(t['feet.z'].value, 0.445);
  assert.equal(t['top.bar.0'].value, 1.1);
  assert.equal(t['body.arch.1'].value, -16.5);
  assert.ok(!Object.keys(t).some((p) => p.startsWith('camera') || p.startsWith('shadow')));
  // Metres, degrees and ratios get ranges that suit them.
  assert.ok(t['grip.width'].max - t['grip.width'].min < 1);
  assert.equal(t['body.head'].unit, '°');
});

test('a traced angle offers nothing, since the trace sets it', () => {
  const t = byPath(tunables(prepare('barbell_squat')));
  assert.ok(!('top.shin' in t) && !('bottom.thigh' in t));
  assert.ok('feet.stance' in t);
});

test('a pulldown offers the levers its entry hasn\'t set, at what they do by default', () => {
  const t = byPath(tunables(prepare('lat_pulldown')));
  assert.equal(t['grip.elbowZ'].value, -0.3);
  assert.equal(t['grip.elbows'].value, 0.8);
  assert.equal(t['top.shrug'].value, 0);
  // The close grip sets its own, which the slider starts at.
  const close = byPath(tunables(prepare('close_grip_lat_pulldown')));
  assert.equal(close['top.shrug'].value, 9);
  assert.equal(close['grip.elbows'].value, 0);
});

test('tuned values go into a copy, arrays whole, and the entry is untouched', () => {
  const entry = prepare('barbell_bench_press');
  const tuned = applyTuned(entry, { 'grip.width': 0.5, 'top.bar': [1.2, -0.2], 'grip.elbowZ': 0.5 });
  assert.equal(tuned.grip.width, 0.5);
  assert.deepEqual(tuned.top.bar, [1.2, -0.2]);
  assert.equal(tuned.grip.elbowZ, 0.5);
  assert.equal(entry.grip.width, 0.4);
  assert.deepEqual(entry.top.bar, [1.1, -0.23]);
});

test('each slider steps by what suits it: a centimetre, a degree, a twentieth of a ratio', () => {
  const t = byPath(tunables(prepare('barbell_bench_press')));
  assert.equal(t['grip.width'].step, 0.01);
  assert.equal(t['feet.z'].step, 0.01);
  assert.equal(t['body.head'].step, 1);
  assert.equal(t['grip.elbows'].step, 0.05);
});

test('the numbers are grouped under short headings, the commonly tuned ones open', () => {
  const heading = (path) => headingOf(path).name;
  assert.equal(heading('grip.width'), 'Grip');
  assert.equal(heading('grip.rotate'), 'Grip');
  assert.equal(heading('grip.elbows'), 'Arms');
  assert.equal(heading('top.upperArm'), 'Arms');
  assert.equal(heading('body.arch.0'), 'Torso');
  assert.equal(heading('bottom.trunk'), 'Torso');
  assert.equal(heading('feet.z'), 'Feet');
  assert.equal(heading('top.bar.1'), 'Bar');
  assert.equal(heading('top.over.0'), 'Bar');
  assert.equal(heading('bottom.chest.1'), 'Bar');
  assert.equal(heading('kit.cable_tower.column'), 'Equipment');
  assert.equal(heading('bottom.shin'), 'Legs');
  assert.equal(heading('lever'), 'Other');
  // Grip, Bar, Torso and Feet are open; the less used are collapsed.
  assert.deepEqual(HEADINGS.filter((h) => h.open).map((h) => h.name), ['Grip', 'Bar', 'Torso', 'Feet']);
});

test('equipment moves a metre either way, a body point only 30 cm', () => {
  const t = byPath(tunables(prepare('cable_shoulder_press')));
  const z = t['kit.cable_column.at.1'];
  assert.ok(z.max - z.value > 0.999 && z.value - z.min > 0.999, `the column moves ${z.min} to ${z.max}`);
  const hand = t['top.hand.1'];
  assert.ok(hand.max - hand.value <= 0.31, `a hand moves to ${hand.max}`);
});

test('every pose offers its grip\'s turn at the top and the bottom, each from grip.rotate', () => {
  const t = byPath(tunables(prepare('reverse_cable_crossover')));
  const rotate = prepare('reverse_cable_crossover').grip.rotate ?? 0;
  assert.equal(t['top.rotate']?.value, rotate);
  assert.equal(t['bottom.rotate']?.value, rotate);
  assert.equal(t['grip.rotate'], undefined);
});

// The owner, #127: on a pose whose entry sets no wrist (close_grip_bench_press, then), the wrist
// sliders did nothing, since there was no point to put the one number into.
test('a wrist slider moved on a pose that sets no wrist puts the whole wrist in, from straight', () => {
  const entry = prepare('barbell_bench_press');
  assert.deepEqual(withTuned(entry, {}, 'top.wrist.1', -15), { 'top.wrist': [0, -15] });
  assert.deepEqual(withTuned(entry, { 'top.wrist': [0, -15] }, 'top.wrist.0', 10), { 'top.wrist': [10, -15] });
  // Back at straight, it comes out again.
  assert.deepEqual(withTuned(entry, { 'top.wrist': [0, -15] }, 'top.wrist.1', 0), {});
  // Any other number, whole or in a point, goes in the same way.
  assert.deepEqual(withTuned(entry, {}, 'grip.width', 0.3), { 'grip.width': 0.3 });
  assert.deepEqual(withTuned(entry, {}, 'top.bar.0', 1.2), { 'top.bar': [1.2, -0.23] });
});

// The owner, #127: the wrist sliders say in words which way they bend the hand, and which sign is which.
test('the wrist sliders are labelled in words, with the sign of each direction', () => {
  assert.equal(labelOf('top.wrist.0'), 'Top · Wrist bend: + flexion / − extension');
  assert.equal(labelOf('bottom.wrist.1'), 'Bottom · Wrist tilt: + radial (thumb side) / − ulnar (little-finger side)');
  assert.equal(labelOf('grip.width'), 'grip.width');
});
