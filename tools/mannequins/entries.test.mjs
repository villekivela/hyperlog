// Pose entries are data; `like: <id>` inherits another entry and changes only what differs.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { resolve } from './entries.mjs';

const POSES = {
  pull: {
    equipment: 'cable',
    motion: 'pulldown',
    kit: { cable_tower: { pulley: 2.2, attachment: 'lat_bar' } },
    grip: { width: 0.4 },
    top: { trunk: -6, bar: [0, 1.9, 0.1] },
    bottom: { trunk: -20, bar: [0, 1.2, 0.2] },
  },
  narrow: { like: 'pull', grip: { width: 0.2 }, kit: { cable_tower: { attachment: 'v_handle' } } },
  narrower: { like: 'narrow', bottom: { bar: [0, 1.25, 0.2] } },
};

test('an entry like another inherits it and changes only what differs', () => {
  assert.deepEqual(resolve('narrow', POSES), {
    equipment: 'cable',
    motion: 'pulldown',
    kit: { cable_tower: { pulley: 2.2, attachment: 'v_handle' } },
    grip: { width: 0.2 },
    top: { trunk: -6, bar: [0, 1.9, 0.1] },
    bottom: { trunk: -20, bar: [0, 1.2, 0.2] },
  });
});

test('likes chain, and a point is replaced whole', () => {
  const e = resolve('narrower', POSES);
  assert.equal(e.grip.width, 0.2);
  assert.deepEqual(e.bottom, { trunk: -20, bar: [0, 1.25, 0.2] });
});

test('an entry like one that is missing, or like itself through a chain, is an error', () => {
  assert.throws(() => resolve('a', { a: { like: 'nope' } }), /a: like "nope", which has no entry/);
  assert.throws(() => resolve('a', { a: { like: 'b' }, b: { like: 'a' } }), /a: like goes round in a circle/);
});

test('Close-Grip Lat Pulldown is the Lat Pulldown with a narrower grip, pulled less low', () => {
  const wide = resolve('lat_pulldown');
  const close = resolve('close_grip_lat_pulldown');
  assert.ok(close.grip.width < wide.grip.width, `${close.grip.width} against ${wide.grip.width}`);
  assert.ok(close.bottom.bar[0] > wide.bottom.bar[0], `${close.bottom.bar} against ${wide.bottom.bar}`);
  assert.equal(close.bottom.trunk, wide.bottom.trunk);
  // The arms hang fully extended at the top with the shoulders let up, and the elbows drive straight down.
  assert.deepEqual({ ...close, grip: wide.grip, top: wide.top, bottom: wide.bottom }, wide);
});
