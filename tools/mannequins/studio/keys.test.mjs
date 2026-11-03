// The studio is driven from the keyboard, vim style (owner, 2026-11-03): keys.json says what each
// key does, counts repeat a command, and the help sheet and every control's key come from the same file.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { keymap, tokenOf, labelOf, helpOf } from './keys.mjs';

const KEYS = JSON.parse(await readFile(new URL('./keys.json', import.meta.url), 'utf8'));
const typed = (keys, tokens) => tokens.map((t) => keys.feed(t)).at(-1);

test('a key does its command, and a count typed first goes with it', () => {
  const keys = keymap(KEYS);
  assert.deepEqual(keys.feed('j'), { command: 'down', count: null });
  assert.deepEqual(typed(keys, ['5', 'l']), { command: 'more', count: 5 });
  assert.deepEqual(typed(keys, ['1', '2', 'H']), { command: 'lessFine', count: 12 });
  assert.deepEqual(typed(keys, ['1', '0', 'j']), { command: 'down', count: 10 });
  // A command that takes no count drops it.
  assert.deepEqual(typed(keys, ['3', 't']), { command: 'holdTop', count: null });
  assert.deepEqual(typed(keys, ['4', 'G']), { command: 'last', count: 4 });
});

test('two keys pressed in turn are one command, and what is pending shows until it is done', () => {
  const keys = keymap(KEYS);
  assert.equal(keys.feed('g'), null);
  assert.equal(keys.pending, 'g');
  assert.deepEqual(keys.feed('g'), { command: 'first', count: null });
  assert.equal(keys.pending, '');
  assert.deepEqual(typed(keys, ['g', 'f']), { command: 'cameraFront', count: null });
  assert.deepEqual(typed(keys, ['g', 's']), { command: 'cameraSide', count: null });
  assert.deepEqual(typed(keys, ['g', 't']), { command: 'cameraTop', count: null });
  assert.equal(keys.feed('2'), null);
  assert.equal(keys.pending, '2');
});

test('a key nothing is bound to clears what was pending, and the next key starts afresh', () => {
  const keys = keymap(KEYS);
  assert.deepEqual(typed(keys, ['5', 'g', 'q']), { command: null, count: null });
  assert.equal(keys.pending, '');
  assert.deepEqual(keys.feed('k'), { command: 'up', count: null });
  // 0 starts no count: it is only a digit after another.
  assert.deepEqual(keys.feed('0'), { command: null, count: null });
});

test('named keys are in vim notation: Ctrl+r redoes, Tab cycles the focus, : opens the command line', () => {
  const keys = keymap(KEYS);
  assert.deepEqual(typed(keys, ['3', '<C-r>']), { command: 'redo', count: 3 });
  assert.deepEqual(keys.feed('<Tab>'), { command: 'focusNext', count: null });
  assert.deepEqual(keys.feed('<S-Tab>'), { command: 'focusPrevious', count: null });
  assert.deepEqual(keys.feed('<Space>'), { command: 'play', count: null });
  assert.deepEqual(keys.feed(':'), { command: 'ex', count: null });
  assert.equal(keys.ex('w'), 'save');
  assert.equal(keys.ex(' w '), 'save');
  assert.equal(keys.ex('q'), null);
});

test('a key event becomes its token', () => {
  assert.equal(tokenOf({ key: 'j' }), 'j');
  assert.equal(tokenOf({ key: 'L', shiftKey: true }), 'L');
  assert.equal(tokenOf({ key: 'r', ctrlKey: true }), '<C-r>');
  assert.equal(tokenOf({ key: 'h', altKey: true }), '<A-h>');
  assert.equal(tokenOf({ key: 'Tab' }), '<Tab>');
  assert.equal(tokenOf({ key: 'Tab', shiftKey: true }), '<S-Tab>');
  assert.equal(tokenOf({ key: ' ' }), '<Space>');
  assert.equal(tokenOf({ key: 'ArrowLeft' }), '<Left>');
  assert.equal(tokenOf({ key: 'Escape' }), '<Esc>');
  assert.equal(tokenOf({ key: 'Enter' }), '<Enter>');
  assert.equal(tokenOf({ key: '?', shiftKey: true }), '?');
  assert.equal(tokenOf({ key: 'Shift', shiftKey: true }), null);
  assert.equal(tokenOf({ key: 'Control', ctrlKey: true }), null);
});

test('each key is labelled as it is pressed, and each command shows its key', () => {
  assert.equal(labelOf('<C-r>'), 'Ctrl+r');
  assert.equal(labelOf('<S-Tab>'), 'Shift+Tab');
  assert.equal(labelOf('<Space>'), 'Space');
  assert.equal(labelOf('<Left>'), '←');
  assert.equal(labelOf('gg'), 'gg');
  const keys = keymap(KEYS);
  assert.equal(keys.keyOf('save'), ':w');
  assert.equal(keys.keyOf('redo'), 'Ctrl+r');
  assert.equal(keys.keyOf('ok'), 'o');
  assert.equal(keys.keyOf('cameraSide'), 'gs');
});

test('the help sheet lists every key in keys.json, the command line\'s too', () => {
  const sheet = helpOf(KEYS);
  const rows = sheet.flatMap((g) => g.rows);
  const bound = KEYS.groups.flatMap((g) => g.keys).length + KEYS.ex.length;
  assert.equal(rows.length, bound);
  const by = Object.fromEntries(rows.map((r) => [r.label, r.does]));
  assert.equal(by['Ctrl+r'], 'Redo');
  assert.equal(by[':w'], 'Save into poses.mjs and re-bake');
  assert.equal(by.gg, 'First item');
  assert.deepEqual(sheet.map((g) => g.name), ['Lists', 'The rep', 'Numbers', 'Cameras', 'Review', 'Commands']);
});

test('a keymap that binds a key twice, or a key that starts another, is refused', () => {
  const twice = { groups: [{ name: 'A', keys: [{ keys: 'j', command: 'down' }, { keys: 'j', command: 'up' }] }], ex: [] };
  assert.throws(() => keymap(twice), /j is bound twice/);
  const prefix = { groups: [{ name: 'A', keys: [{ keys: 'g', command: 'go' }, { keys: 'gg', command: 'first' }] }], ex: [] };
  assert.throws(() => keymap(prefix), /g starts gg/);
  const digit = { groups: [{ name: 'A', keys: [{ keys: '5', command: 'five' }] }], ex: [] };
  assert.throws(() => keymap(digit), /5 is a count/);
});
