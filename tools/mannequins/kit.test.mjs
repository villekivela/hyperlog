// Every kind of equipment in the library has a generic kit, or is listed as missing.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { EQUIPMENT, KITS, MACHINES, buildKit } from './kit.mjs';

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

test('every machine in the library is of one kind, which has a kit or is listed as missing', () => {
  const machines = library.exercises.filter((e) => e.equipment === 'machine').map((e) => e.id).sort();
  assert.deepEqual(Object.values(MACHINES).flatMap((m) => m.exercises).sort(), machines);
  for (const [kind, { kit }] of Object.entries(MACHINES)) {
    if (kit === null) {
      assert.match(readme, new RegExp(`^\\| \`${kind}\` \\| [^|]+ \\| missing`, 'm'), `${kind} isn't listed as missing in the README`);
    } else {
      assert.ok(KITS[kit], `${kind}: no kit "${kit}"`);
      assert.ok(EQUIPMENT.machine.includes(kit), `${kind}'s kit "${kit}" isn't machine equipment`);
    }
  }
});

// The kits' tones by part (#127): the light theme's colour, which the bake writes, and the roughness.
const TONES = {
  frame: ['2e2e33', 0.5],
  vinyl: ['1b1b1e', 0.95],
  plate: ['8a8a90', 0.8],
  metal: ['c8c8cc', 0.3],
  pulley: ['5c5c62', 0.55],
};

test('every kit builds, each part in one of the tones the phone colours', () => {
  for (const name of Object.keys(KITS)) {
    const kind = Object.keys(EQUIPMENT).find((k) => EQUIPMENT[k]?.includes(name));
    const kit = buildKit({ equipment: kind, kit: { [name]: {} } });
    let meshes = 0;
    kit.group.traverse((o) => {
      if (!o.isMesh) return;
      meshes++;
      const tone = TONES[o.material.name];
      assert.ok(tone, `${name}: ${o.name} in "${o.material.name}"`);
      assert.equal(o.material.color.getHexString(), tone[0], `${name}: ${o.material.name}'s colour`);
      assert.equal(o.material.roughness, tone[1], `${name}: ${o.material.name}'s roughness`);
      assert.equal(o.material.metalness, 0, `${name}: ${o.material.name} is metallic`);
    });
    assert.ok(meshes > 0, `${name} has no parts`);
  }
});

test('a pose can only use kits of its own equipment, and none for a kind without one', () => {
  assert.throws(() => buildKit({ equipment: 'barbell', kit: { cable_tower: {} } }), /Kit "cable_tower" isn't barbell equipment/);
  assert.throws(() => buildKit({ equipment: 'trap_bar', kit: {} }), /"trap_bar" has no kit yet/);
});
