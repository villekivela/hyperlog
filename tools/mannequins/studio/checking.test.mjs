// What the message line says while the studio loads (#187).
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mayNote, progressNote, rowAfter } from './checking.mjs';

test('the message line says Loading… until the checks have begun', () => {
  assert.equal(progressNote(null), 'Loading…');
  assert.equal(progressNote({ done: 0, total: 0 }), 'Loading…');
});

test('the message line counts the checks as they finish', () => {
  assert.equal(progressNote({ done: 0, total: 38 }), 'Checking 0 of 38');
  assert.equal(progressNote({ done: 12, total: 38 }), 'Checking 12 of 38');
});

test('the message line has nothing to say once every check is done', () => {
  assert.equal(progressNote({ done: 38, total: 38 }), '');
});

const line = { text: 'frame 12 of 30', tone: '', own: 'Checking 3 of 38', age: 500, note: 'Checking 4 of 38' };

test('the count takes an empty line, and goes on from its own', () => {
  assert.equal(mayNote({ ...line, text: '' }), true);
  assert.equal(mayNote({ ...line, text: 'Checking 3 of 38', tone: 'busy' }), true);
});

test('the count leaves a plain message to be read, then takes the line back', () => {
  assert.equal(mayNote(line), false);
  assert.equal(mayNote({ ...line, age: 5000 }), true);
});

test('the count never takes the line from an error or from a save under way', () => {
  assert.equal(mayNote({ ...line, tone: 'error', age: 60000 }), false);
  assert.equal(mayNote({ ...line, tone: 'busy', age: 60000 }), false);
});

test('finishing clears only the count, never a message that came after it', () => {
  assert.equal(mayNote({ ...line, note: '', age: 60000 }), false);
  assert.equal(mayNote({ ...line, note: '', text: 'Checking 3 of 38' }), true);
});

test('a list refreshed under the cursor leaves it on its row, not on the item on stage', () => {
  assert.equal(rowAfter(['a', 'b', 'c'], 'c', 'a'), 2);
});

test('a row the refreshed list no longer shows gives the cursor to the item on stage', () => {
  assert.equal(rowAfter(['a', 'c'], 'b', 'c'), 1);
  assert.equal(rowAfter(['a'], undefined, 'z'), -1);
});
