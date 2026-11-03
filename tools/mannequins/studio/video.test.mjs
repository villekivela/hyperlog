// The technique video beside the stage (#171): it opens at its link's timestamp, steps a frame or a
// second at a time at 0.25×, 0.5× or 1×, and with sync on its loop is the rep: A the top, B the
// bottom and C the top again, the lowering A to B and the lift B to C, both played forward, so
// either one scrubbed moves the other.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { videoOf, faster, slower, stepFrames, stepSeconds, timeAt, repAt, synced, spanOf, zoomOf } from './video.mjs';

test('a link names its video and the second it opens at', () => {
  assert.deepEqual(videoOf('https://youtu.be/jLvqKgW-_G8?t=278'), { id: 'jLvqKgW-_G8', start: 278 });
  assert.deepEqual(videoOf('https://youtu.be/01pnZt1uVFI'), { id: '01pnZt1uVFI', start: 0 });
  assert.deepEqual(videoOf('https://www.youtube.com/watch?v=B5S2mbg0g5c&t=1m5s'), { id: 'B5S2mbg0g5c', start: 65 });
  assert.equal(videoOf(null), null);
  assert.equal(videoOf(''), null);
  assert.equal(videoOf('https://example.com/squat.mp4'), null);
});

test('the speed steps through 0.25×, 0.5× and 1×, and stops at either end', () => {
  assert.equal(slower(1), 0.5);
  assert.equal(slower(0.5), 0.25);
  assert.equal(slower(0.25), 0.25);
  assert.equal(faster(0.25), 0.5);
  assert.equal(faster(0.5), 1);
  assert.equal(faster(1), 1);
});

test('a frame step lands in the middle of the next frame, and never before the start or past the end', () => {
  // At 30 fps frame 300 runs from 10 s to 10.033 s.
  assert.equal(stepFrames(10.01, 1, 30, 60), 301.5 / 30);
  assert.equal(stepFrames(10.01, -1, 30, 60), 299.5 / 30);
  assert.equal(stepFrames(10.01, 5, 30, 60), 305.5 / 30);
  // From the middle of a frame, a step back and on again is where it was.
  assert.equal(stepFrames(stepFrames(300.5 / 30, -1, 30, 60), 1, 30, 60), 300.5 / 30);
  assert.equal(stepFrames(0.01, -3, 30, 60), 0.5 / 30);
  assert.equal(stepFrames(59.99, 4, 30, 60), 1799.5 / 30);
  assert.equal(stepSeconds(10, 1, 60), 11);
  assert.equal(stepSeconds(10, -3, 60), 7);
  assert.equal(stepSeconds(1, -3, 60), 0);
  assert.equal(stepSeconds(59.5, 1, 60), 60);
});

test('synced, the lowering is A to B and the lift B to C, both forward, and either one finds the other', () => {
  const loop = { a: 280, b: 281.5, c: 283.5 };
  const down = (d) => ({ d, lifting: false });
  const up = (d) => ({ d, lifting: true });
  assert.equal(timeAt(loop, down(0)), 280);
  assert.equal(timeAt(loop, down(0.5)), 280.75);
  assert.equal(timeAt(loop, down(1)), 281.5);
  // The lift runs on from the bottom, B, to the top again, C: the video never plays backwards.
  assert.equal(timeAt(loop, up(1)), 281.5);
  assert.equal(timeAt(loop, up(0.5)), 282.5);
  assert.equal(timeAt(loop, up(0)), 283.5);
  assert.deepEqual(repAt(loop, 280.75), down(0.5));
  assert.deepEqual(repAt(loop, 282.5), up(0.5));
  assert.deepEqual(repAt(loop, 283.5), up(0));
  // Before A the rep holds at its top, past C at its top again.
  assert.deepEqual(repAt(loop, 279), down(0));
  assert.deepEqual(repAt(loop, 290), up(0));
});

test('sync needs the whole rep: A, B and C set, in that order in the video', () => {
  assert.equal(synced({ a: 280, b: 281.5, c: 283 }, true), true);
  assert.equal(synced({ a: 280, b: 281.5, c: 283 }, false), false);
  // A loop saved with only A and B loads, and waits for C.
  assert.equal(synced({ a: 280, b: 281.5 }, true), false);
  assert.equal(synced({ a: 280, b: 281.5, c: null }, true), false);
  assert.equal(synced({ a: 280, b: 280, c: 281 }, true), false);
  assert.equal(synced({ a: 283, b: 281.5, c: 280 }, true), false);
  assert.equal(synced(null, true), false);
});

test('the loop spans A to its last mark, and the scrub bar zooms to it with a margin', () => {
  assert.deepEqual(spanOf({ a: 280, b: 281.5, c: 283.5 }), [280, 283.5]);
  assert.deepEqual(spanOf({ a: 280, b: 281.5, c: null }), [280, 281.5]);
  assert.deepEqual(spanOf({ a: 280, b: null, c: null }), null);
  assert.equal(spanOf(null), null);
  // A quarter of the loop either side, half a second at least, within the video.
  assert.deepEqual(zoomOf({ a: 280, b: 281.5, c: 284 }, 600), [279, 285]);
  assert.deepEqual(zoomOf({ a: 280, b: 280.5 }, 600), [279.5, 281]);
  assert.deepEqual(zoomOf({ a: 0.2, b: 1, c: 2.2 }, 2.4), [0, 2.4]);
  // No loop yet: the whole video.
  assert.deepEqual(zoomOf({ a: 280, b: null }, 600), [0, 600]);
  assert.deepEqual(zoomOf(null, 600), [0, 600]);
});
