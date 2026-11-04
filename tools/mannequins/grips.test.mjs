// An exercise done with a different grip set by set (owner, #127: the 2-Grip Lat Pulldown, set 1 wide
// overhand, set 2 narrow underhand) bakes a clip per grip, listed in the order the sets take them.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile, stat } from 'node:fs/promises';
import path from 'node:path';
import { bakeAll } from './bake.mjs';
import { prepare } from './entries.mjs';
import { POSES } from './poses.mjs';
import { scratch } from './tmp.mjs';

const ID = '2_grip_lat_pulldown';

test('a pose with grips bakes a clip per grip, listed in the order the sets take them', async () => {
  const out = await scratch('grips');
  await bakeAll(out, { poses: { lat_pulldown: POSES.lat_pulldown, [ID]: POSES[ID] } });
  const { clips } = JSON.parse(await readFile(path.join(out, 'mannequins.json'), 'utf8'));
  const grips = clips[ID].grips;
  assert.deepEqual(grips.map((g) => g.name), ['wide', 'narrow']);
  for (const g of grips) assert.ok((await stat(path.join(out, g.clip))).size > 0, `${g.clip} isn't there`);
  // The entry's own clip is the first set's.
  assert.equal(clips[ID].clip, grips[0].clip);
});

test('the wide grip is overhand at 1.5 shoulder widths, the narrow underhand at shoulder width', () => {
  const wide = prepare(ID, POSES, undefined, 'wide');
  const narrow = prepare(ID, POSES, undefined, 'narrow');
  assert.ok(wide.grip.width > narrow.grip.width * 1.4, `wide ${wide.grip.width}, narrow ${narrow.grip.width}`);
  assert.equal(wide.grip.rotate ?? 0, 0);
  assert.equal(narrow.grip.rotate, 180);
  assert.equal(wide.grips, undefined);
});
