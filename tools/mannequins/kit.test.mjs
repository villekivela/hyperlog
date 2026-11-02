// Every kind of equipment in the library has a generic kit, or is listed as missing.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { EQUIPMENT, KITS, buildKit } from './kit.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const library = JSON.parse(await readFile(path.join(HERE, '..', '..', 'app', 'src', 'main', 'assets', 'exercises.json'), 'utf8'));
const readme = await readFile(path.join(HERE, 'README.md'), 'utf8');
const kinds = [...new Set(library.exercises.map((e) => e.equipment))].sort();

test('every equipment kind in the library has kits or is listed as missing', () => {
  assert.deepEqual(Object.keys(EQUIPMENT).sort(), kinds);
  for (const [kind, kits] of Object.entries(EQUIPMENT)) {
    if (kits === null) {
      assert.match(readme, new RegExp(`^\\| \`${kind}\` \\| missing`, 'm'), `${kind} isn't listed as missing in the README`);
    } else {
      for (const k of kits) assert.ok(KITS[k], `${kind}: no kit "${k}"`);
    }
  }
});

test('every kit builds, in materials the phone colours', () => {
  const named = new Set(['bar', 'plate']);
  for (const name of Object.keys(KITS)) {
    const kind = Object.keys(EQUIPMENT).find((k) => EQUIPMENT[k]?.includes(name));
    const kit = buildKit({ equipment: kind, kit: { [name]: {} } });
    let meshes = 0;
    kit.group.traverse((o) => {
      if (!o.isMesh) return;
      meshes++;
      assert.ok(named.has(o.material.name), `${name}: a part in "${o.material.name}"`);
    });
    assert.ok(meshes > 0, `${name} has no parts`);
  }
});

test('a pose can only use kits of its own equipment, and none for a kind without one', () => {
  assert.throws(() => buildKit({ equipment: 'barbell', kit: { cable_tower: {} } }), /Kit "cable_tower" isn't barbell equipment/);
  assert.throws(() => buildKit({ equipment: 'trap_bar', kit: {} }), /"trap_bar" has no kit yet/);
});
