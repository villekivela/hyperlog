// An entry's own tempo wins over its movement's.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { tempoOf, MOTION_TEMPOS } from './tempo.mjs';

test('an entry without a tempo takes its movement\'s, and one with a tempo keeps it', () => {
  assert.deepEqual(tempoOf({ motion: 'squat' }), MOTION_TEMPOS.squat);
  const own = { lowering: 3, bottom: 0, lifting: 3, top: 0 };
  assert.deepEqual(tempoOf({ motion: 'squat', tempo: own }), own);
});

test('a movement without a default and an entry without a tempo is an error', () => {
  assert.throws(() => tempoOf({ motion: 'nope' }), /No tempo for motion "nope"/);
});
