// The stage follows each arm through the rep from both ends and keeps the cheaper way. Closing the
// fingers is most of what a stage costs (#187), so they close only on the way kept.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { prepare } from './entries.mjs';
import { POSES } from './poses.mjs';
import { buildStage, MODEL, parseMannequin } from './stage.mjs';

/** How many times building `id`'s stage moves a finger: what closing a hand costs, and steady where a clock isn't. */
async function fingerMoves(id) {
  const bytes = await readFile(MODEL);
  const body = await parseMannequin(bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength));
  let bone;
  body.traverse((o) => { if (o.isBone && o.name === 'index_01_l') bone = o; });
  let moves = 0;
  const { updateMatrixWorld } = bone;
  bone.updateMatrixWorld = function count(force) {
    moves++;
    updateMatrixWorld.call(this, force);
  };
  await buildStage(prepare(id, POSES), body);
  return moves;
}

test('the fingers close on one way through the rep, not both', async () => {
  // Both ways take 5587, the way kept 3940: following the arms moves the fingers with them.
  const moves = await fingerMoves('barbell_squat');
  assert.ok(moves < 4500, `${moves} moves`);
});
