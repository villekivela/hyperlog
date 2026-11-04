// A save in the studio writes tuned values into poses.mjs as text, so the owner's comments stay.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { pathToFileURL } from 'node:url';
import { patchPoses } from './patch.mjs';
import { scratch } from './tmp.mjs';

const SOURCE = `export const POSES = {
  one: {
    motion: 'squat',
    // The bar sits low.
    grip: { width: 0.4 },
    top: { bar: [1.15, -0.23], trunk: 4 },
    feet: { stance: 0.3 },
  },

  two: { like: 'one', grip: { width: 0.24 } },
};
`;

test('a number is replaced in place and the comments stay', () => {
  const out = patchPoses(SOURCE, 'one', { 'grip.width': 0.5 });
  assert.equal(out, SOURCE.replace('{ width: 0.4 }', '{ width: 0.5 }'));
});

test('a point is replaced whole', () => {
  assert.ok(patchPoses(SOURCE, 'one', { 'top.bar': [1.2, -0.2] }).includes('top: { bar: [1.2, -0.2], trunk: 4 }'));
});

test('a number the entry hasn\'t got is added where it belongs', () => {
  const out = patchPoses(SOURCE, 'one', { 'grip.elbowZ': 0.5 });
  assert.ok(out.includes('grip: { width: 0.4, elbowZ: 0.5 }'), out);
});

test('an object the entry hasn\'t got is added, in a like or not', () => {
  const out = patchPoses(SOURCE, 'two', { 'top.shrug': 9, 'bottom.shrug': 0 });
  assert.ok(out.includes('two: { like: \'one\', grip: { width: 0.24 }, top: { shrug: 9 }, bottom: { shrug: 0 } }'), out);
  const multi = patchPoses(SOURCE, 'one', { 'body.arch': [16, -16.5, -8] });
  assert.ok(multi.includes('    body: { arch: [16, -16.5, -8] },\n  },'), multi);
});

// The owner, #197: an id that starts with a digit is quoted in poses.mjs, and no save could find it.
test('an entry whose id is quoted patches like any other', () => {
  const source = SOURCE.replace('  two: {', "  '1_arm_two': {");
  const out = patchPoses(source, '1_arm_two', { 'grip.width': 0.3 });
  assert.equal(out, source.replace('{ width: 0.24 }', '{ width: 0.3 }'));
  assert.ok(patchPoses(source, '1_arm_two', { 'feet.stance': 0.2 }).includes("'1_arm_two': { like: 'one', grip: { width: 0.24 }"));
});

test('the crossover\'s quoted exercises save a hole, a hand and a turn of the trunk', () => {
  return readFile(new URL('./poses.mjs', import.meta.url), 'utf8').then((source) => {
    const tuned = { 'kit.cable_crossover.position': [5, 9], 'top.hand': [-0.2, 1.3, 0.4], 'top.offsets.Head': [0, -20, 0] };
    const out = patchPoses(source, '1_arm_cable_press_around', tuned);
    assert.ok(out.includes('position: [5, 9]'));
    assert.ok(out.includes('hand: [-0.2, 1.3, 0.4]'));
    assert.ok(out.includes('Head: [0, -20, 0]'));
  });
});

test('an entry or a path it can\'t place is an error naming it', () => {
  assert.throws(() => patchPoses(SOURCE, 'nine', { 'grip.width': 1 }), /nine/);
  assert.throws(() => patchPoses(SOURCE, 'one', { 'motion.speed': 1 }), /motion\.speed/);
});

test('the real poses patch and still load, with the owner\'s words kept', async () => {
  const real = await readFile(new URL('./poses.mjs', import.meta.url), 'utf8');
  const out = patchPoses(real, 'barbell_bench_press', { 'grip.width': 0.45, 'top.bar': [1.16, -0.23] });
  assert.ok(out.includes('A normal bench grip, about 1.5 shoulder widths'));
  const dir = await scratch('poses');
  await writeFile(path.join(dir, 'poses.mjs'), out);
  const { POSES } = await import(pathToFileURL(path.join(dir, 'poses.mjs')).href);
  assert.equal(POSES.barbell_bench_press.grip.width, 0.45);
  assert.deepEqual(POSES.barbell_bench_press.top.bar, [1.16, -0.23]);
});

test('a fit override goes into a kit that sets nothing yet', () => {
  const source = "export const POSES = {\n  a: {\n    kit: { leg_extension: {} },\n  },\n};\n";
  const out = patchPoses(source, 'a', { 'kit.leg_extension.seat': [0.6, 0.1] });
  assert.match(out, /kit: \{ leg_extension: \{ seat: \[0\.6, 0\.1\] \} \}/);
});
