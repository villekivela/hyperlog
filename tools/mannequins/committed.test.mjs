// The clips committed under app/src/main/assets/mannequins/ are what the tool bakes: CI runs this
// to catch a pose edited without re-baking, or clips baked by hand.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, readFile, readdir } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { bakeAll, CLIPS } from './bake.mjs';

test('the committed clips and index are byte-identical to a fresh bake', async () => {
  const fresh = await mkdtemp(path.join(tmpdir(), 'mannequins-'));
  await bakeAll(fresh);
  const baked = (await readdir(fresh)).sort();
  assert.deepEqual((await readdir(CLIPS)).sort(), baked, 'the committed files and the baked ones differ');
  for (const f of baked) {
    assert.ok((await readFile(path.join(CLIPS, f))).equals(await readFile(path.join(fresh, f))), `${f} differs from its bake: run npm run bake and commit`);
  }
});
