// The queue of checks, and what the message line says of it while the studio loads (#187, #191).
import { test, mock } from 'node:test';
import assert from 'node:assert/strict';
import { checkQueue, mayNote, progressNote, rowAfter } from './checking.mjs';

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

// With the timeouts a queue that loses a pose fails, not hangs.
/** A queue whose checks wait for `next(found)`, which lets the oldest go with what it found. */
function heldQueue() {
  const began = [];
  const holding = [];
  const queue = checkQueue((id) => new Promise((resolve) => {
    began.push(id);
    holding.push(resolve);
  }));
  const until = async (n) => {
    for (let i = 0; i < 1000 && began.length < n; i++) await new Promise((r) => setTimeout(r, 5));
    assert.equal(began.length, n, began.join());
  };
  return { queue, began, until, next: (found) => holding.shift()(found) };
}

test('forgetting one pose leaves the check under way on another to stand', { timeout: 10000 }, async () => {
  const { queue, began, until, next } = heldQueue();
  queue.ask(['a', 'b']);
  await until(1);
  next('a, before');
  await until(2);
  queue.forget(['a']);
  next('b, kept');
  await until(3);
  assert.equal(queue.found('a'), null);
  next('a, after');
  await queue.ask(['a', 'b']);
  assert.equal(queue.found('a'), 'a, after');
  assert.equal(queue.found('b'), 'b, kept');
  assert.deepEqual(began, ['a', 'b', 'a']);
});

test('forgetting the pose under way checks it again, and only the new result counts', { timeout: 10000 }, async () => {
  const { queue, began, until, next } = heldQueue();
  const all = queue.ask(['a']);
  await until(1);
  queue.forget(['a']);
  next('a, before');
  await until(2);
  next('a, after');
  assert.deepEqual(await all, ['a, after']);
  assert.deepEqual(began, ['a', 'a']);
});

test('forgetting with no poses named starts every pose again', { timeout: 10000 }, async () => {
  const { queue, began, until, next } = heldQueue();
  const all = queue.ask(['a', 'b']);
  await until(1);
  next('a');
  await until(2);
  next('b');
  await all;
  queue.forget();
  await until(3);
  next('a');
  await until(4);
  next('b');
  assert.deepEqual(began, ['a', 'b', 'a', 'b']);
});

// The queue's gap between poses runs on a mocked clock, so nothing here waits on real time.
test('a paused queue begins no pose until the function pause() gave is called', async (t) => {
  t.after(() => mock.timers.reset());
  mock.timers.enable({ apis: ['setTimeout'] });
  const settle = () => new Promise((resolve) => { setImmediate(resolve); });
  const began = [];
  const queue = checkQueue(async (id) => { began.push(id); return { ok: true }; });
  const resume = queue.pause();
  queue.ask(['a']);
  for (let gap = 0; gap < 3; gap++) {
    mock.timers.tick(25);
    await settle();
    assert.deepEqual(began, [], `a pose began in gap ${gap} with the queue paused`);
  }
  resume();
  mock.timers.tick(25);
  await settle();
  assert.deepEqual(began, ['a']);
});
