// A save bakes one pose only while the clips are up to date with everything else (#191), so a
// change the save didn't make has to be seen, whenever it came and whether the studio was open.
// Here on a tool of a few files, dated by hand.
import { test, beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import { mkdir, rm, utimes, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { clipsBehind, datedBake } from './behind.mjs';
import { scratch } from '../tmp.mjs';

const OLD = new Date('2026-01-01');
const BAKED = new Date('2026-02-01');
const NEW = new Date('2026-03-01');

let tool;
let clips;
let library;
const behind = () => clipsBehind(clips, { tool, also: [library] });
const date = (file, when) => utimes(file, when, when);
const at = (...parts) => path.join(tool, ...parts);

beforeEach(async () => {
  tool = await scratch('behind-tool');
  clips = await scratch('behind-clips');
  library = path.join(clips, 'exercises.json');
  for (const dir of ['recipes', 'traces', 'model', 'studio']) await mkdir(at(dir));
  const files = [at('motions.mjs'), at('motions.test.mjs'), at('seats.json'), at('recipes', 'bench.mjs'), at('traces', 'squat.json'), at('model', 'mannequin.glb'), at('studio', 'plugin.mjs'), library];
  for (const f of files) {
    await writeFile(f, '');
    await date(f, OLD);
  }
  await writeFile(path.join(clips, 'mannequins.json'), '{}');
  await date(path.join(clips, 'mannequins.json'), BAKED);
});

test('clips baked since their last change aren\'t behind', async () => {
  assert.equal(await behind(), false);
});

for (const [what, file] of [
  ['the tool\'s code', () => at('motions.mjs')],
  ['a recipe', () => at('recipes', 'bench.mjs')],
  ['a trace', () => at('traces', 'squat.json')],
  ['the mannequin', () => at('model', 'mannequin.glb')],
  ['the library', () => library],
]) {
  test(`a change to ${what} since the bake leaves the clips behind`, async () => {
    await date(file(), NEW);
    assert.equal(await behind(), true);
  });
}

for (const [what, file] of [
  ['a test', () => at('motions.test.mjs')],
  ['the studio\'s own code', () => at('studio', 'plugin.mjs')],
  ['the seats a bake adds to', () => at('seats.json')],
]) {
  test(`a change to ${what} doesn't`, async () => {
    await date(file(), NEW);
    assert.equal(await behind(), false);
  });
}

test('clips never baked are behind', async () => {
  await rm(path.join(clips, 'mannequins.json'));
  assert.equal(await behind(), true);
});

test('a bake brings the clips up to date', async () => {
  await date(at('motions.mjs'), NEW);
  await datedBake(clips, async () => {});
  assert.equal(await behind(), false);
});

test('a change made while a bake runs still leaves the clips behind', async () => {
  await datedBake(clips, async () => {
    await new Promise((r) => setTimeout(r, 20));
    await writeFile(at('motions.mjs'), 'changed');
  });
  assert.equal(await behind(), true);
});

test('a bake that fails leaves the clips as far behind as they were', async () => {
  await date(at('motions.mjs'), NEW);
  await assert.rejects(datedBake(clips, async () => { throw new Error('a pose fails its checks'); }), /fails its checks/);
  assert.equal(await behind(), true);
});
