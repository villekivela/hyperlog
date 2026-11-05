// The trap bar kit (#234): a hexagonal frame the lifter stands inside, gripping two parallel handles
// at the sides. Nothing on it moves, and its one exercise is not posed yet.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import * as THREE from 'three';
import { EQUIPMENT, KITS, BARS, buildKit, shownAlone } from './kit.mjs';
import { buildStage } from './stage.mjs';
import { POSES } from './poses.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const library = JSON.parse(await readFile(path.join(HERE, '..', '..', 'app', 'src', 'main', 'assets', 'exercises.json'), 'utf8'));

test('it is the kit of the trap bar exercise the ref names, which is not posed', async () => {
  const ref = await readFile(path.join(HERE, 'refs', 'trap_bar', 'ref.md'), 'utf8');
  const named = [...ref.match(/Serves (.+)\. The bar is/)[1].matchAll(/`([^`]+)`/g)].map((m) => m[1]);
  assert.equal(named.length, 1);
  assert.deepEqual(BARS.trap_bar, { kit: 'trap_bar', exercises: named });
  assert.ok(KITS.trap_bar, 'a recipe named trap_bar');
  assert.deepEqual(library.exercises.filter((e) => e.equipment === 'trap_bar').map((e) => e.id), named);
  for (const id of named) assert.equal(POSES[id], undefined, `${id} is posed already`);
  assert.deepEqual(EQUIPMENT.trap_bar, ['trap_bar']);
});

const bar = (options = {}) => {
  const kit = buildKit({ equipment: 'trap_bar', kit: { trap_bar: options } }).kits.trap_bar;
  kit.place({}, {}, {});
  kit.group.updateMatrixWorld(true);
  return kit;
};
const close = (a, b, within, why) => assert.ok(Math.abs(a - b) <= within, `${why}: ${a.toFixed(3)}, not ${b} ± ${within}`);
const boxOf = (o) => new THREE.Box3().setFromObject(o, true);
const meshes = (kit, wanted = () => true) => {
  const out = [];
  kit.group.traverse((o) => { if (o.isMesh && wanted(o)) out.push({ mesh: o, box: boxOf(o) }); });
  return out;
};
const size = (b) => b.getSize(new THREE.Vector3());
const centre = (b) => b.getCenter(new THREE.Vector3());
const held = (kit) => meshes(kit, (o) => o.userData.held);
const contact = (kit, point) => kit.contacts.find((c) => c.point === point);

test('the handles are two parallel 127 mm grips, 635 mm apart centre to centre, the low ones with their centres at 225 mm', () => {
  const grips = held(bar());
  assert.equal(grips.length, 2);
  for (const { box } of grips) {
    close(size(box).z, 0.127, 2e-3, 'the handle length, front to back');
    close(size(box).y, 0.034, 1e-3, 'the handle diameter');
    close(centre(box).y, 0.225, 1e-3, 'the low handle centre');
    close(centre(box).z, 0, 1e-3, 'the handle centred on the sleeve axis');
  }
  close(Math.abs(centre(grips[0].box).x - centre(grips[1].box).x), 0.635, 1e-3, 'the handle spacing');
});

test('the high handles are 102 mm above the low ones, straight above them', () => {
  const low = held(bar()).map(({ box }) => centre(box));
  const high = held(bar({ handle: 'high' })).map(({ box }) => centre(box));
  high.forEach((c, i) => {
    close(c.y - low[i].y, 0.102, 1e-3, 'the offset');
    close(c.x, low[i].x, 1e-3, 'straight above');
    close(c.y, 0.327, 1e-3, 'the high handle centre');
  });
});

test('it is 1875 mm long over the sleeves and 652 mm across the frame, with the sleeves 406 mm long', () => {
  const kit = bar();
  const all = meshes(kit, (o) => o.material.name !== 'plate').reduce((u, { box }) => u.union(box), new THREE.Box3());
  close(size(all).x, 1.875, 5e-3, 'the overall length');
  close(size(all).z, 0.652, 5e-3, 'the width front to back');
  const sleeves = meshes(kit, (o) => o.material.name === 'metal' && size(boxOf(o)).x > 0.3);
  assert.equal(sleeves.length, 2);
  for (const { box } of sleeves) close(size(box).x, 0.406, 5e-3, 'the sleeve length');
});

test('the frame and the sleeves lie in one plane, level with the low handle centres', () => {
  const kit = bar();
  const sleeves = meshes(kit, (o) => o.material.name === 'metal' && size(boxOf(o)).x > 0.3);
  for (const { box } of sleeves) close(centre(box).y, 0.225, 1e-3, 'the sleeve axis');
  const frame = meshes(kit, (o) => o.material.name === 'frame');
  assert.ok(frame.length >= 5, 'a frame of several tubes');
  for (const { box } of frame) close(centre(box).y, 0.225, 0.05, 'a frame tube near the plane');
});

test('450 mm plates on the sleeves rest on the floor', () => {
  const plates = meshes(bar(), (o) => o.material.name === 'plate');
  assert.ok(plates.length >= 2);
  for (const { box } of plates) {
    close(box.min.y, 0, 1e-3, 'the plate on the floor');
    close(size(box).y, 0.45, 1e-3, 'the plate diameter');
  }
  assert.equal(meshes(bar({ plates: 0 }), (o) => o.material.name === 'plate').length, 0);
});

test('the hands hold the handles at their centres, a point each', () => {
  const kit = bar();
  const hands = contact(kit, 'grips');
  assert.equal(hands.landmark, 'hands');
  assert.equal(hands.body, 'frame');
  assert.equal(hands.at.length, 2);
  for (const p of hands.at) assert.ok(held(kit).some(({ box }) => box.clone().expandByScalar(0.005).containsPoint(p)), 'a grip point off its handle');
});

test('its fit rule names the handle height it sets', () => {
  assert.deepEqual(KITS.trap_bar.fit.map((f) => f.sets), ['handle']);
  assert.ok(KITS.trap_bar.fit[0].rule.length > 20);
});

test('nothing on it moves, and it draws only in the frame, metal and plate tones', () => {
  const kit = bar();
  assert.deepEqual(Object.keys(kit.bodies), []);
  const tones = new Set(['frame', 'plate', 'metal']);
  for (const { mesh } of meshes(kit)) assert.ok(tones.has(mesh.material.name), `${mesh.name} is ${mesh.material.name}`);
});

test('its recipe names no brand', async () => {
  const source = await readFile(path.join(HERE, 'recipes', 'trap-bar.mjs'), 'utf8');
  assert.doesNotMatch(source, /rogue|tb-3|titan|hoist|\blogo\b|decal/i);
});

test('the studio shows it alone, with nothing to scrub', async () => {
  const entry = shownAlone('trap_bar');
  assert.ok(entry?.camera, 'a framing to open on');
  const stage = await buildStage(entry);
  stage.pose(0);
  assert.ok(stage.kit.kits.trap_bar);
});
