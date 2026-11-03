// A save in the studio writes tuned values into poses.mjs as text, so the owner's comments stay.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile, mkdtemp, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { pathToFileURL } from 'node:url';
import { patchPoses } from './patch.mjs';

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

test('an entry or a path it can\'t place is an error naming it', () => {
  assert.throws(() => patchPoses(SOURCE, 'nine', { 'grip.width': 1 }), /nine/);
  assert.throws(() => patchPoses(SOURCE, 'one', { 'motion.speed': 1 }), /motion\.speed/);
});

test('the real poses patch and still load, with the owner\'s words kept', async () => {
  const real = await readFile(new URL('./poses.mjs', import.meta.url), 'utf8');
  const out = patchPoses(real, 'barbell_bench_press', { 'grip.width': 0.45, 'top.bar': [1.16, -0.23] });
  assert.ok(out.includes('A normal bench grip, about 1.5 shoulder widths'));
  const dir = await mkdtemp(path.join(tmpdir(), 'poses-'));
  await writeFile(path.join(dir, 'poses.mjs'), out);
  const { POSES } = await import(pathToFileURL(path.join(dir, 'poses.mjs')).href);
  assert.equal(POSES.barbell_bench_press.grip.width, 0.45);
  assert.deepEqual(POSES.barbell_bench_press.top.bar, [1.16, -0.23]);
});
