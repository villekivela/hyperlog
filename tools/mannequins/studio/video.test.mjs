// The technique video beside the stage (#171): it opens at its link's timestamp, steps a frame or a
// second at a time at 0.25×, 0.5× or 1×, and with sync on its A–B loop is the rep: A the top, B the
// bottom, so either one scrubbed moves the other.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { videoOf, faster, slower, stepFrames, stepSeconds, timeAt, repAt, synced } from './video.mjs';

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

test('with a loop set, the rep\'s top is A and its bottom is B, and either one finds the other', () => {
  const loop = { a: 280, b: 281.5 };
  assert.equal(timeAt(loop, 0), 280);
  assert.equal(timeAt(loop, 1), 281.5);
  assert.equal(timeAt(loop, 0.5), 280.75);
  assert.equal(repAt(loop, 280.75), 0.5);
  assert.equal(repAt(loop, 281.5), 1);
  // Outside the loop the rep holds at its top or its bottom.
  assert.equal(repAt(loop, 279), 0);
  assert.equal(repAt(loop, 290), 1);
  // A bottom filmed before its top: B before A in the video.
  const up = { a: 12, b: 10 };
  assert.equal(timeAt(up, 0.25), 11.5);
  assert.equal(repAt(up, 11.5), 0.25);
});

test('sync needs a whole loop: both ends set, and not at the same frame', () => {
  assert.equal(synced({ a: 280, b: 281.5 }, true), true);
  assert.equal(synced({ a: 280, b: 281.5 }, false), false);
  assert.equal(synced({ a: 280, b: null }, true), false);
  assert.equal(synced({ a: 280, b: 280 }, true), false);
  assert.equal(synced(null, true), false);
});
