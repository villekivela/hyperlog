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
  // A made-up kind that has none, so no kit a real kind gets later can break this.
  EQUIPMENT.kind_without_a_kit = null;
  try {
    assert.throws(() => buildKit({ equipment: 'kind_without_a_kit', kit: {} }), /"kind_without_a_kit" has no kit yet/);
  } finally {
    delete EQUIPMENT.kind_without_a_kit;
  }
});

// The owner's bar (#127): every machine and bench at the lat tower's detail, built in the tones
// table's parts: a frame, vinyl pads and grips, plates, metal rods and cables, pulleys and housings.
// A bench carries no plates of its own.
const WEARS = {
  cable_tower: [{ equipment: 'cable', kit: { cable_tower: { attachment: 'straight_bar' } } }, ['frame', 'vinyl', 'plate', 'metal', 'pulley']],
  chest_press: [{ equipment: 'machine', kit: { chest_press: {} } }, ['frame', 'vinyl', 'plate', 'metal', 'pulley']],
  leg_press: [{ equipment: 'machine', kit: { leg_press: {} } }, ['frame', 'vinyl', 'plate', 'metal', 'pulley']],
  bench: [{ equipment: 'barbell', kit: { bench: {} } }, ['frame', 'vinyl', 'metal', 'pulley']],
  adjustable_bench: [{ equipment: 'dumbbell', kit: { bench: {} }, bench: { angle: 30 } }, ['frame', 'vinyl', 'metal', 'pulley']],
  cable_column: [{ equipment: 'cable', kit: { cable_column: {} } }, ['frame', 'vinyl', 'plate', 'metal', 'pulley']],
  cable_crossover: [{ equipment: 'cable', kit: { cable_crossover: {} } }, ['frame', 'vinyl', 'plate', 'metal', 'pulley']],
  ...Object.fromEntries(['leg_extension', 'leg_curl', 'calf_raise_machine', 'squat_machine', 'row_machine', 'pulldown_machine', 'preacher_curl_machine', 't_bar_row_machine', 'hip_machine', 'lateral_raise_machine', 'shrug_machine', 'crunch_machine']
    .map((k) => [k, [{ equipment: 'machine', kit: { [k]: {} } }, ['frame', 'vinyl', 'plate', 'metal', 'pulley']]])),
  lying_leg_curl: [{ equipment: 'machine', kit: { leg_curl: { lying: true } } }, ['frame', 'vinyl', 'plate', 'metal', 'pulley']],
  smith_machine: [{ equipment: 'smith_machine', kit: { smith_machine: {} } }, ['frame', 'vinyl', 'metal', 'pulley']],
  platform: [{ equipment: 'barbell', kit: { platform: {} } }, ['frame', 'vinyl']],
};

test('every machine and bench wears each tone of its parts', () => {
  for (const [name, [entry, tones]] of Object.entries(WEARS)) {
    const worn = new Set();
    buildKit(entry).group.traverse((o) => { if (o.isMesh) worn.add(o.material.name); });
    assert.deepEqual([...worn].filter((t) => tones.includes(t)).sort(), [...tones].sort(), `${name} wears ${[...worn].sort().join(', ')}`);
  }
});

// #168's refs: the T-bar row and the Kelso shrug are done chest-supported on the T-bar row machine.
test('the T-bar row machine serves the chest-supported T-bar row, the T-bar row and the Kelso shrug', () => {
  assert.deepEqual(MACHINES.t_bar_row_machine, { kit: 't_bar_row_machine', exercises: ['chest_supported_t_bar_row', 't_bar_row', 'kelso_shrug'] });
  assert.deepEqual(MACHINES.row_machine.exercises, ['machine_row', 'chest_supported_machine_row']);
  assert.deepEqual(MACHINES.shrug_machine.exercises, ['machine_shrug']);
});
