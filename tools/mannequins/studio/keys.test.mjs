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
  assert.deepEqual(sheet.map((g) => g.name), ['Lists', 'The rep', 'Numbers', 'Cameras', 'Video', 'Review', 'Commands']);
  assert.equal(sheet.find((g) => g.name === 'Video').focus, 'video');
});

test('a keymap that binds a key twice, or a key that starts another, is refused', () => {
  const twice = { groups: [{ name: 'A', keys: [{ keys: 'j', command: 'down' }, { keys: 'j', command: 'up' }] }], ex: [] };
  assert.throws(() => keymap(twice), /j is bound twice/);
  const prefix = { groups: [{ name: 'A', keys: [{ keys: 'g', command: 'go' }, { keys: 'gg', command: 'first' }] }], ex: [] };
  assert.throws(() => keymap(prefix), /g starts gg/);
  const digit = { groups: [{ name: 'A', keys: [{ keys: '5', command: 'five' }] }], ex: [] };
  assert.throws(() => keymap(digit), /5 is a count/);
});

// The technique video's keys (#171) are bound only while its panel is focused, where they take over
// the keys the stage and the numbers use; anywhere else those keys do what they did.
test('a group bound to a pane takes its keys over while that pane is focused, and only then', () => {
  const keys = keymap(KEYS);
  assert.deepEqual(keys.feed('l', 'video'), { command: 'videoFrameOn', count: null });
  assert.deepEqual(typed(keys, ['3', 'h']), { command: 'less', count: 3 });
  assert.deepEqual(['4', 'h'].map((t) => keys.feed(t, 'video')).at(-1), { command: 'videoFrameBack', count: 4 });
  assert.deepEqual(keys.feed('L', 'video'), { command: 'videoSecondOn', count: null });
  assert.deepEqual(keys.feed('H', 'video'), { command: 'videoSecondBack', count: null });
  assert.deepEqual(keys.feed('<Space>', 'video'), { command: 'videoPlay', count: null });
  assert.deepEqual(keys.feed('<', 'video'), { command: 'videoSlower', count: null });
  assert.deepEqual(keys.feed('>', 'video'), { command: 'videoFaster', count: null });
  assert.deepEqual(keys.feed('[', 'video'), { command: 'loopStart', count: null });
  assert.deepEqual(keys.feed(']', 'video'), { command: 'loopEnd', count: null });
  assert.equal(keys.feed('g', 'video'), null);
  assert.deepEqual(keys.feed('v', 'video'), { command: 'sync', count: null });
  // The rest of the keymap still works there, and the video's keys do nothing elsewhere.
  assert.deepEqual(keys.feed('j', 'video'), { command: 'down', count: null });
  assert.deepEqual(typed(keys, ['g', 'g']), { command: 'first', count: null });
  assert.deepEqual(keys.feed('<Space>', 'stage'), { command: 'play', count: null });
  assert.deepEqual(keys.feed('['), { command: null, count: null });
  assert.deepEqual(typed(keys, ['g', 'v']), { command: null, count: null });
  assert.equal(keys.keyOf('sync'), 'gv');
  assert.equal(keys.keyOf('videoSecondOn'), 'L');
});

test('a pane\'s group may rebind a key, but not twice, and not as the start of another there', () => {
  const twice = { groups: [{ name: 'A', focus: 'video', keys: [{ keys: 'j', command: 'a' }, { keys: 'j', command: 'b' }] }], ex: [] };
  assert.throws(() => keymap(twice), /j is bound twice/);
  const prefix = { groups: [{ name: 'A', keys: [{ keys: 'gg', command: 'first' }] }, { name: 'B', focus: 'video', keys: [{ keys: 'g', command: 'go' }] }], ex: [] };
  assert.throws(() => keymap(prefix), /g starts gg/);
  const over = { groups: [{ name: 'A', keys: [{ keys: 'l', command: 'more' }] }, { name: 'B', focus: 'video', keys: [{ keys: 'l', command: 'on' }] }], ex: [] };
  assert.equal(keymap(over).feed('l', 'video').command, 'on');
});
