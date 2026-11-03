// Which numbers of a pose entry the studio's numbers panel offers, and how a tuned value goes into
// an entry.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { prepare } from './entries.mjs';
import { tunables, applyTuned, atAngle, baseAt, headingOf, HEADINGS, withTuned, labelOf } from './tune.mjs';
import { MODEL, buildStage, parseMannequin } from './stage.mjs';
import { turnOf } from './rig.mjs';
import { POSES } from './poses.mjs';

const byPath = (list) => Object.fromEntries(list.map((t) => [t.path, t]));

test('the bench press offers its own numbers, points one element at a time, and not the camera', () => {
  const t = byPath(tunables(prepare('barbell_bench_press')));
  assert.equal(t['grip.width'].value, 0.4);
  assert.equal(t['feet.z'].value, 0.445);
  assert.equal(t['top.bar.0'].value, 1.1265);
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
  assert.deepEqual(entry.top.bar, [1.1265, -0.2]);
});

// The studio's h and l step a number by one step, H and L by a tenth of one (owner, 2026-11-03).
test('each number steps by what suits it: a millimetre, a degree, a hundredth of a ratio', () => {
  const t = byPath(tunables(prepare('barbell_bench_press')));
  assert.equal(t['grip.width'].step, 0.001);
  assert.equal(t['feet.z'].step, 0.001);
  assert.equal(t['body.head'].step, 1);
  assert.equal(t['grip.elbows'].step, 0.01);
  const kit = byPath(tunables(prepare('lat_pulldown')));
  assert.equal(kit['kit.cable_tower.column'].step, 0.001);
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
  assert.deepEqual(withTuned(entry, {}, 'top.bar.0', 1.2), { 'top.bar': [1.2, -0.2] });
});

// The owner, #127: the wrist sliders say in words which way they bend the hand, and which sign is which.
test('the wrist sliders are labelled in words, with the sign of each direction', () => {
  assert.equal(labelOf('top.wrist.0'), 'Top · Wrist bend: + flexion / − extension');
  assert.equal(labelOf('bottom.wrist.1'), 'Bottom · Wrist tilt: + radial (thumb side) / − ulnar (little-finger side)');
  assert.equal(labelOf('grip.width'), 'grip.width');
});

// The owner, #127: on close_grip_bench_press the wrist sliders did nothing in the live preview.
// Every pose with hands: each wrist number, moved from one side to the other, turns the hand.
test('each wrist number turns the hand as the studio poses it, on every pose with hands', async () => {
  const bytes = await readFile(MODEL);
  const body = await parseMannequin(bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength));
  const still = [];
  for (const id of Object.keys(POSES)) {
    const entry = prepare(id);
    if (!entry.grip) continue;
    // As the studio poses it: an adjustable bench at its typical angle.
    const live = (tuned) => {
      const e = applyTuned(entry, tuned);
      return buildStage(e.bench ? atAngle(e, e.bench.typical) : e, body);
    };
    const wrists = tunables(entry).filter((t) => /^(top|bottom)\.wrist\.[01]$/.test(t.path));
    assert.equal(wrists.length, 4, `${id} offers ${wrists.map((t) => t.path)}`);
    for (const t of wrists) {
      const d = t.path.startsWith('top') ? 0 : 1;
      const hand = async (v) => {
        const stage = await live(withTuned(entry, {}, t.path, v));
        stage.pose(d);
        return turnOf(stage.rig, 'lowerarm_l').invert().multiply(turnOf(stage.rig, 'hand_l'));
      };
      const turned = ((await hand(15)).angleTo(await hand(-15)) * 180) / Math.PI;
      if (turned < 20) still.push(`${id} ${t.path}: ${turned.toFixed(0)}°`);
    }
  }
  assert.deepEqual(still, [], 'from -15 to 15 the hand turned only');
});

// A bone's offset (#164) is three numbers at each end, [x, y, z] in degrees: the bones the entry
// turns are numbers like any other, under their own heading, and a bone it doesn't turn starts at none.
test('a bone\'s offset is a number per axis, in degrees under Bones, and a bone not turned starts at none', () => {
  const entry = applyTuned(prepare('barbell_squat'), { 'bottom.offsets': { Head: [10, 0, -4] } });
  const t = byPath(tunables(entry));
  assert.equal(t['bottom.offsets.Head.0'].value, 10);
  assert.equal(t['bottom.offsets.Head.2'].value, -4);
  assert.equal(t['bottom.offsets.Head.1'].unit, '°');
  assert.equal(t['bottom.offsets.Head.1'].step, 1);
  assert.ok(t['bottom.offsets.Head.1'].min <= -180 && t['bottom.offsets.Head.1'].max >= 180);
  assert.equal(headingOf('bottom.offsets.Head.0').name, 'Bones');
  assert.equal(labelOf('bottom.offsets.Head.0'), 'Bottom · Head · x');
  assert.equal(labelOf('top.offsets.spine_02.2'), 'Top · spine_02 · z');
  // A turn of a bone the entry leaves alone puts its offset in whole; turned back, it comes out again.
  const tuned = withTuned(entry, {}, 'top.offsets.clavicle_l.1', 5);
  assert.deepEqual(tuned, { 'top.offsets.clavicle_l': [0, 5, 0] });
  assert.deepEqual(withTuned(entry, tuned, 'top.offsets.clavicle_l.1', 0), {});
  // Reset, an axis goes back to what the entry has: none for a bone it doesn't turn.
  assert.equal(baseAt(entry, 'bottom.offsets.Head.0'), 10);
  assert.equal(baseAt(entry, 'top.offsets.clavicle_l.1'), 0);
});
