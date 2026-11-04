// A movement spec's lines, judged against every frame's numbers. The frames here are written by
// hand, so what each line should find can be read off them.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { execFile } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { promisify } from 'node:util';
import { CLIPS } from './bake.mjs';
import { judge, markOf, readSpec, reviewMovement, specified } from './movement.mjs';
import { readIndex } from './review.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));

/** Frames with `name` running through `values`. */
const frames = (name, values) => values.map((v, i) => ({ t: i / (values.length - 1), [name]: v }));
const one = (line, rows) => judge({ lines: [{ says: 'it', ...line }] }, rows)[0];

test('a moves line passes when the top and the bottom are in their ranges, and says what it measured', () => {
  const found = one({ moves: 'elbow_l', top: [160, 180], bottom: [80, 95] }, frames('elbow_l', [170, 130, 85]));
  assert.equal(found.ok, true);
  assert.equal(found.measured, '170.0° at the top, 85.0° at the bottom');
});

test('a moves line fails on a bottom out of its range', () => {
  assert.equal(one({ moves: 'elbow_l', top: [160, 180], bottom: [80, 95] }, frames('elbow_l', [170, 110, 100])).ok, false);
});

test('a still line takes a point\'s furthest travel from where it started, in cm', () => {
  const found = one({ still: 'elbow_l.at', within: 0.02 }, frames('elbow_l.at', [[0, 0, 0], [0, 0.03, 0.04], [0, 0, 0.01]]));
  assert.equal(found.ok, false);
  assert.equal(found.measured, '5.0 cm at frame 1');
});

test('a still line takes a number\'s furthest move from its first', () => {
  const found = one({ still: 'trunk', within: 5 }, frames('trunk', [10, 12, 14]));
  assert.equal(found.ok, true);
  assert.equal(found.measured, '4.0° at frame 2');
});

test('a never line fails on the first frame past its limit, either way', () => {
  const found = one({ never: 'dumbbell_l.tilt', past: 20 }, frames('dumbbell_l.tilt', [0, -26.7, 5]));
  assert.equal(found.ok, false);
  assert.equal(found.measured, '26.7° at frame 1');
});

test('a never line with perFrame judges the change from frame to frame', () => {
  const found = one({ never: 'roll_l', perFrame: true, past: 7 }, frames('roll_l', [0, 3, 12, 14]));
  assert.equal(found.ok, false);
  assert.equal(found.measured, '9.0° at frame 2');
});

test('an even line measures how far a number strays from the straight run between its ends', () => {
  // The run from 15 to 8 is 11.5 at the middle, so 26.5 strays 15.
  const found = one({ even: 'dumbbell_l.tilt', within: 10 }, frames('dumbbell_l.tilt', [15, 26.5, 8]));
  assert.equal(found.ok, false);
  assert.equal(found.measured, '15.0° at frame 1');
});

test('an even line passes a number that runs steadily, however far', () => {
  assert.equal(one({ even: 'roll_l', within: 2 }, frames('roll_l', [0, 45, 90])).ok, true);
});

test('a path line measures how far a point strays from the straight line between its ends', () => {
  const found = one({ path: 'hand_l.at', within: 0.02 }, frames('hand_l.at', [[0, 1, 0], [0.03, 0.5, 0], [0, 0, 0]]));
  assert.equal(found.ok, false);
  assert.equal(found.measured, '3.0 cm at frame 1');
});

test('a look line has no verdict: it waits for eyes', () => {
  const found = one({ look: 'the forearms stand under the bar', at: ['bottom'], view: 'side' }, frames('elbow_l', [170, 85]));
  assert.equal(found.ok, null);
  assert.equal(found.measured, 'bottom, side');
});

test('a line on a number the clip doesn\'t have fails and says so', () => {
  const found = one({ never: 'dumbbell_r.tilt', past: 20 }, frames('trunk', [0, 1]));
  assert.equal(found.ok, false);
  assert.match(found.measured, /no dumbbell_r\.tilt in this clip/);
});

test('a line of no known kind is refused', () => {
  assert.throws(() => one({ wobbles: 'elbow_l' }, frames('elbow_l', [0, 1])), /"it" has no kind/);
});

test('a star stands for each side, and the worse one is what counts', () => {
  const rows = [{ t: 0, 'dumbbell_l.tilt': 2, 'dumbbell_r.tilt': 3 }, { t: 1, 'dumbbell_l.tilt': 4, 'dumbbell_r.tilt': 26 }];
  const found = one({ never: 'dumbbell_*.tilt', past: 20 }, rows);
  assert.equal(found.ok, false);
  assert.equal(found.measured, 'dumbbell_r.tilt: 26.0° at frame 1');
});

test('the command refuses an option it doesn\'t know, so it can\'t judge the wrong clips', async () => {
  const run = promisify(execFile)(process.execPath, ['movement.mjs', 'db_bench_press', '--at acbebfb'], { cwd: HERE });
  await assert.rejects(run, /Unknown option/);
});

test('a reaches line passes once a number gets that far on some frame', () => {
  const found = one({ reaches: 'wrist_l', least: 10 }, frames('wrist_l', [0, 12, 3]));
  assert.equal(found.ok, true);
  assert.equal(found.measured, '12.0° at frame 1');
});

test('a reaches line fails a number that never gets there: a rigid wrist', () => {
  assert.equal(one({ reaches: 'wrist_l', least: 10 }, frames('wrist_l', [0, 0, 0])).ok, false);
});

test('a line marked open keeps its verdict and says why', () => {
  const found = one({ never: 'trunk', past: 5, open: 'waits for the owner' }, frames('trunk', [0, 9]));
  assert.equal(found.ok, false);
  assert.equal(found.open, 'waits for the owner');
});

// The exact lines are automatic checks: every clip here holds to its spec. A spec's lift that isn't
// baked yet waits for its clip. A line marked `open` is a known failure, so it must still fail: one
// that passes has been fixed, and its mark should go.
for (const id of await specified()) {
  test(`${id} moves as its spec says`, async (t) => {
    const { clips } = await readIndex(CLIPS);
    if (!clips[id]) return t.skip('no clip yet');
    const spec = await readSpec(id);
    const verdicts = await reviewMovement(id, CLIPS, spec);
    assert.deepEqual(verdicts.filter((v) => v.ok === false && !v.open).map((v) => `${v.says}: ${v.measured}`), []);
    assert.deepEqual(verdicts.filter((v) => v.open && v.ok).map((v) => v.says), [], 'passes now: take its open mark off');
  });
}

test('a line that needs a number refuses a point, rather than passing on nothing', () => {
  const rows = frames('hand_l.at', [[0, 0, 0], [1, 1, 1]]);
  for (const line of [{ never: 'hand_l.at', past: 1 }, { moves: 'hand_l.at', top: [0, 1], bottom: [0, 1] }, { reaches: 'hand_l.at', least: 1 }, { even: 'hand_l.at', within: 1 }]) {
    assert.throws(() => one(line, rows), /takes a number, not the point hand_l\.at/);
  }
});

test('a path line refuses a number', () => {
  assert.throws(() => one({ path: 'trunk', within: 1 }, frames('trunk', [0, 1])), /takes a point, not the number trunk/);
});

test('a place line measures one point from another along an axis, at the top or the bottom', () => {
  const rows = [{ t: 0, 'hand_l.at': [-0.16, 1.4, 0.3], 'pelvis.at': [0.04, 0.8, 0] }, { t: 1, 'hand_l.at': [0.3, 1.2, 0.1], 'pelvis.at': [0.04, 0.8, 0] }];
  const found = one({ place: 'hand_l.at', from: 'pelvis.at', axis: 'x', at: 'top', range: [-0.5, -0.02] }, rows);
  assert.equal(found.ok, true);
  assert.equal(found.measured, '-20.0 cm along x at the top');
  assert.equal(one({ place: 'hand_l.at', from: 'pelvis.at', axis: 'x', at: 'bottom', range: [-0.5, -0.02] }, rows).ok, false);
});

test('a failure reads FAIL, and OPEN once it is marked open', () => {
  assert.equal(markOf(one({ never: 'trunk', past: 5 }, frames('trunk', [0, 9]))), 'FAIL');
  assert.equal(markOf(one({ never: 'trunk', past: 5, open: 'known' }, frames('trunk', [0, 9]))), 'OPEN');
  assert.equal(markOf(one({ never: 'trunk', past: 10, open: 'known' }, frames('trunk', [0, 9]))), 'pass');
});

test('an open failure doesn\'t fail the command', async () => {
  const { stdout } = await promisify(execFile)(process.execPath, ['movement.mjs', 'barbell_bench_press'], { cwd: HERE });
  assert.match(stdout, /^OPEN {2}the upper arms keep their angle/m);
});

test('a star in a place line\'s from follows the side it is measuring', () => {
  const rows = [{ t: 0, 'hand_l.at': [0.3, 1, 0], 'hand_r.at': [-0.3, 1, 0.5], 'shoulder_l.at': [0.2, 1, 0], 'shoulder_r.at': [-0.2, 1, 0] }];
  const found = one({ place: 'hand_*.at', from: 'shoulder_*.at', axis: 'z', at: 'top', range: [-0.1, 0.1] }, rows);
  assert.equal(found.ok, false);
  assert.equal(found.measured, 'hand_r.at: 50.0 cm along z at the top');
});
