// Which numbers of a pose entry the studio's numbers panel offers, and how a tuned value goes into
// an entry.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { prepare } from './entries.mjs';
import { tunables, applyTuned, atAngle, baseAt, headingOf, HEADINGS, withTuned, labelOf, rangeOf } from './tune.mjs';
import { MODEL, buildStage, parseMannequin } from './stage.mjs';
import { turnOf } from './rig.mjs';
import { POSES } from './poses.mjs';
import { holdOf } from './grip.mjs';

const byPath = (list) => Object.fromEntries(list.map((t) => [t.path, t]));

test('the bench press offers its own numbers, points one element at a time, and not the camera', () => {
  const t = byPath(tunables(prepare('barbell_bench_press')));
  assert.equal(t['grip.width'].value, 0.4);
  assert.equal(t['feet.z'].value, 0.445);
  assert.equal(t['top.bar.0'].value, 1.1435);
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
  assert.deepEqual(entry.top.bar, [1.1435, -0.2]);
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
// Every pose with hands: each wrist number, moved from one side of where it sits to the other, turns
// the hand. Around its own value, since one wrist number can slow another far from it: the tuned
// squats' bend halves the tilt near straight, where no slider of theirs sits.
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
      const turned = ((await hand(t.value + 15)).angleTo(await hand(t.value - 15)) * 180) / Math.PI;
      if (turned < 20) still.push(`${id} ${t.path}: ${turned.toFixed(0)}°`);
    }
  }
  assert.deepEqual(still, [], '15 either way the hand turned only');
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

// A machine's fit (#160): each rule the entry leaves to the solve has a slider at what it solved, as
// has each end of the rep, and moving one writes the override.
const FIT = { 'kit.leg_extension.seat': [0.59, 0.1], 'kit.leg_extension.pad': 0.18, 'bottom.lever': 0 };

test('a machine offers a slider for each fit rule and each end of its rep, at what was solved', () => {
  const t = byPath(tunables(applyTuned(prepare('leg_extension'), FIT)));
  assert.equal(t['kit.leg_extension.seat.0'].value, 0.59);
  assert.equal(t['kit.leg_extension.pad'].value, 0.18);
  assert.equal(t['top.lever'].value, 85);
  assert.equal(t['top.lever'].unit, '°');
  assert.equal(t['bottom.lever'].value, 0);
  assert.equal(byPath(tunables(prepare('hack_squat')))['top.carriage'].unit, ' m');
  assert.equal(headingOf('top.lever').name, 'Bar');
  assert.equal(headingOf('top.sled').name, 'Bar');
});

test('fitting a slider leaves the entry alone: what isn\'t overridden is solved again', () => {
  const entry = prepare('leg_extension');
  const before = JSON.stringify(entry);
  applyTuned(entry, FIT);
  assert.equal(JSON.stringify(entry), before);
});

test('a fit slider moved is an override of the whole option, and back at the solve it comes out', () => {
  const base = applyTuned(prepare('leg_extension'), FIT);
  const tuned = withTuned(base, {}, 'kit.leg_extension.seat.0', 0.62);
  assert.deepEqual(tuned, { 'kit.leg_extension.seat': [0.62, 0.1] });
  assert.deepEqual(withTuned(base, tuned, 'kit.leg_extension.seat.0', 0.59), {});
});

test('every fit slider of every machine saves an entry that poses', async () => {
  for (const id of ['chest_supported_machine_row', 'machine_pulldown', 'leg_press']) {
    const entry = prepare(id);
    const { fit } = await buildStage(entry);
    const base = applyTuned(entry, fit);
    for (const { path, value } of tunables(base).filter((t) => t.path.startsWith('kit.'))) {
      const stage = await buildStage(applyTuned(entry, withTuned(base, {}, path, value + 0.001)));
      stage.pose(0);
      stage.kit.group.traverse((o) => assert.ok(o.position.toArray().every(Number.isFinite), `${id}: ${path} leaves ${o.name} at ${o.position.toArray()}`));
    }
  }
});

// #160's worker: the thumb's swing read 50000.0 mm, and a step off a whole five wouldn't pose.
test('a thumb\'s swing is an angle: a degree a step, 0 to 90, and every value the slider reaches poses', () => {
  const entry = prepare('barbell_squat');
  const swing = byPath(tunables(entry))['grip.swing'];
  assert.deepEqual(swing, { path: 'grip.swing', value: 50, min: 0, max: 90, step: 1, unit: '°' });
  const held = (v) => holdOf(applyTuned(entry, { 'grip.swing': v })).swing;
  assert.equal(held(51), 51);
  assert.equal(held(0), 0);
  assert.equal(held(90), 90);
  // A drag or a fine step lands between degrees: the hand takes the nearest whole one.
  assert.equal(held(52.37), 52);
});

test('Tune offers no slider for which hand a kit serves: it is a choice, not a place', () => {
  for (const id of ['single_arm_db_row', 'bayesian_cable_curl', 'cable_y_raise']) {
    const paths = tunables(prepare(id)).map((t) => t.path);
    assert.deepEqual(paths.filter((p) => /^kit\.[^.]+\.hands?(\.\d+)?$/.test(p)), [], id);
  }
});

// The owner, #197: a press-around's free arm braces straight out at the shoulder, on the far pulley.
test('a resting hand reaches as far as an arm does', () => {
  const rest = (i, value) => rangeOf(`grip.rest.${i}`, value);
  assert.ok(rest(0, -0.3).min <= -0.9, `sideways to ${rest(0, -0.3).min}`);
  assert.ok(rest(1, 0.9).max >= 1.6, `up to ${rest(1, 0.9).max}`);
  assert.equal(rest(0, -0.3).step, 0.001);
});

// The owner, #197: the machine vanished at the first touch of `position`, a hole taken for metres.
test('a carriage moves a hole at a time, from the first to the last', () => {
  const t = tunables(prepare('cable_crossover')).find((n) => n.path === 'kit.cable_crossover.position');
  assert.deepEqual({ min: t.min, max: t.max, step: t.step, unit: t.unit }, { min: 1, max: 16, step: 1, unit: '' });
});

test('a drag, a tenth of a step or a typed number lands a carriage on a hole', () => {
  const entry = prepare('cable_crossover');
  const at = (value) => withTuned(entry, {}, 'kit.cable_crossover.position', value)['kit.cable_crossover.position'];
  assert.equal(at(12.6), 13);
  assert.equal(at(13.2), 13);
  assert.equal(at(99), 16);
  assert.equal(at(-3), 1);
});

test('a carriage on each tower lands on a hole too', () => {
  const entry = { ...prepare('cable_crossover'), kit: { cable_crossover: { position: [4, 12] } } };
  const tuned = withTuned(entry, {}, 'kit.cable_crossover.position.1', 9.4);
  assert.deepEqual(tuned['kit.cable_crossover.position'], [4, 9]);
});

test('the stage still stands with a carriage dragged anywhere along its rail', async () => {
  const entry = prepare('cable_crossover');
  for (const value of [1.37, 8.5, 15.001]) {
    const stage = await buildStage(applyTuned(entry, withTuned(entry, {}, 'kit.cable_crossover.position', value)));
    assert.ok(stage.pose(0), `dragged to ${value}`);
  }
});
