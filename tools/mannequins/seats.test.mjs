// A seat takes over a second to solve and is the mannequin's, the same every run, so seats.json
// holds each one the poses use (#187). Here: no pose has to solve one, and the file holds what the
// solve finds.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { variantsOf } from './bake.mjs';
import { prepare } from './entries.mjs';
import { forgetSeats, newSeats, seatOf } from './grip.mjs';
import { POSES } from './poses.mjs';
import { buildStage } from './stage.mjs';

/**
 * How far a solve may land from the file's: it homes in on a seat in steps of 4.6 µm, and where the
 * rig stands moves it up to 37 µm. A bar 0.1 mm thicker moves a seat 119 µm or more.
 */
const NOISE = 5e-5;

test('every pose finds its seats in seats.json, at every bench angle and grip', async () => {
  for (const id of Object.keys(POSES)) {
    for (const [angle, grip] of variantsOf(id, POSES)) await buildStage(prepare(id, POSES, angle, grip));
  }
  assert.deepEqual(Object.keys(newSeats()), []);
});

test('seats.json holds what the solve finds', async () => {
  const table = JSON.parse(await readFile(new URL('./seats.json', import.meta.url), 'utf8'));
  const { rig } = await buildStage(prepare('barbell_squat', POSES));
  forgetSeats();
  for (const key of Object.keys(table)) {
    const [, k, sits, radius, thumb, swing] = key.split(':');
    seatOf(rig, k, sits, Number(radius), thumb, swing === 'undefined' ? undefined : Number(swing));
  }
  const solved = newSeats();
  for (const [key, offs] of Object.entries(table)) {
    offs.forEach((row, i) => row.forEach((off, j) => {
      assert.ok(Math.abs(off - solved[key][i][j]) < NOISE, `${key} at slant ${i}, tilt ${j}: ${off} in the file, ${solved[key][i][j]} solved. Run npm run seats`);
    }));
  }
});

test('a seat the file doesn\'t hold is solved, and said to be new', async () => {
  const { rig } = await buildStage(prepare('barbell_squat', POSES));
  const before = Object.keys(newSeats()).length;
  const seat = seatOf(rig, 'l', 'mid', 0.0123, 'wrapped');
  assert.ok(seat.off > 0);
  assert.equal(Object.keys(newSeats()).length, before + 1);
});
