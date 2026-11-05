// The safety squat bar (#219), from refs/safety_bar/ref.md: a cambered bar with a padded yoke on the
// shoulders and two forward handles. Nothing on it moves, and none of its exercises is posed yet.
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

test('it is the kit of the two safety bar exercises the ref names, neither of them posed', async () => {
  const ref = await readFile(path.join(HERE, 'refs', 'safety_bar', 'ref.md'), 'utf8');
  const named = [...ref.match(/Serves (.+)\. The bar is/)[1].matchAll(/`([^`]+)`/g)].map((m) => m[1]);
  assert.equal(named.length, 2);
  assert.deepEqual(BARS.safety_bar, { kit: 'safety_bar', exercises: named });
  assert.ok(KITS.safety_bar, 'a recipe named safety_bar');
  assert.deepEqual(library.exercises.filter((e) => e.equipment === 'safety_bar').map((e) => e.id).sort(), [...named].sort());
  for (const id of named) assert.equal(POSES[id], undefined, `${id} is posed already`);
  assert.deepEqual(EQUIPMENT.safety_bar, ['safety_bar']);
});

const bar = (options = {}) => {
  const kit = buildKit({ equipment: 'safety_bar', kit: { safety_bar: options } }).kits.safety_bar;
  kit.place({}, {}, {});
  kit.group.updateMatrixWorld(true);
  return kit;
};
const close = (a, b, within, why) => assert.ok(Math.abs(a - b) <= within, `${why}: ${a.toFixed(3)}, not ${b} ± ${within}`);
const meshes = (kit, wanted = () => true) => {
  const out = [];
  kit.group.traverse((o) => { if (o.isMesh && wanted(o)) out.push({ mesh: o, box: new THREE.Box3().setFromObject(o, true) }); });
  return out;
};
const size = (b) => b.getSize(new THREE.Vector3());
const centre = (b) => b.getCenter(new THREE.Vector3());
const whole = (kit) => meshes(kit).reduce((u, { box }) => u.union(box), new THREE.Box3());
const contact = (kit, point) => kit.contacts.find((c) => c.point === point);
const held = (kit) => meshes(kit, (o) => o.userData.held);
// A rod's diameter and length: the unit cylinder's width, and how far it is stretched.
const diameter = ({ mesh }) => new THREE.Box3().setFromBufferAttribute(mesh.geometry.attributes.position).getSize(new THREE.Vector3()).x * mesh.scale.x;
const lengthOf = ({ mesh }) => mesh.scale.y;
// The shaft: the 38 mm tube along x through the middle, the longest part of the frame's tone.
const shaft = (kit) => meshes(kit, (o) => o.material.name === 'frame' && Math.abs(size(new THREE.Box3().setFromObject(o, true)).y - 0.038) < 2e-3 && size(new THREE.Box3().setFromObject(o, true)).x > 1.2)[0];

test('it is 2362 mm long with the sleeves out, on a 38 mm shaft 1310 mm between the camber bends', () => {
  const kit = bar();
  close(size(whole(kit)).x, 2.362, 0.005, 'the overall length');
  const { box } = shaft(kit);
  close(size(box).y, 0.038, 1e-3, 'the shaft diameter');
  close(size(box).x, 1.31, 0.05, 'the straight shaft between the camber bends');
});

test('the shaft stands at 1.50 m, the height the ref chooses for a standing mannequin, and an option moves it', () => {
  close(centre(shaft(bar()).box).y, 1.5, 1e-3, 'the shaft centre');
  close(centre(shaft(bar({ shaft: 1.4 })).box).y, 1.4, 1e-3, 'the shaft centre, set');
});

test('each sleeve end is 140 mm below the shaft centre, 755 mm out to its collar face', () => {
  const kit = bar();
  const y = centre(shaft(kit).box).y;
  const sleeves = meshes(kit, (o) => o.material.name === 'metal' && size(new THREE.Box3().setFromObject(o, true)).x > 0.3);
  assert.equal(sleeves.length, 2);
  for (const { box } of sleeves) {
    close(y - centre(box).y, 0.14, 2e-3, 'the sleeve centre below the shaft');
    close(size(box).y, 0.05, 1e-3, 'the sleeve diameter');
    close(Math.min(Math.abs(box.min.x), Math.abs(box.max.x)), 0.775, 0.005, 'the sleeve\'s inner end');
    close(Math.max(Math.abs(box.min.x), Math.abs(box.max.x)), 1.181, 0.005, 'the sleeve\'s outer end');
  }
});

test('the yoke pad is 330 mm long and 100 mm across, round the shaft', () => {
  const kit = bar();
  const pad = meshes(kit, (o) => o.material.name === 'vinyl' && size(new THREE.Box3().setFromObject(o, true)).x > 0.3)[0];
  close(size(pad.box).x, 0.33, 2e-3, 'the pad length');
  close(size(pad.box).y, 0.1, 2e-3, 'the pad diameter');
  close(centre(pad.box).y, centre(shaft(kit).box).y, 1e-3, 'the pad round the shaft');
});

// The published 533 mm front to back and 254 mm high are the bar as it is held, rolled forward on the
// shoulders; hung in a rack (the photos) the handles drop steeper, about 410 mm below the shaft's centre.
test('hung as racked, two handles reach down and forward from the yoke ends, about 420 mm below the shaft and 345 mm ahead, tips 310 mm apart', () => {
  const kit = bar();
  const y = centre(shaft(kit).box).y;
  const grips = held(kit);
  assert.equal(grips.length, 2);
  for (const g of grips) close(diameter(g), 0.0254, 1e-3, 'the handle diameter');
  const tips = grips.map(({ box }) => ({ x: centre(box).x, drop: y - box.min.y, ahead: box.max.z }));
  close(Math.max(...tips.map((t) => t.drop)), 0.42, 0.02, 'the tips below the shaft');
  close(Math.max(...tips.map((t) => t.ahead)), 0.345, 0.02, 'the tips ahead of the shaft');
  const xs = tips.map((t) => t.x).sort((a, b) => a - b);
  close(xs[1] - xs[0], 0.32, 0.04, 'the grips apart');
});

test('rolled 25 degrees forward on the shoulders, as the lifter holds it, it is 533 mm front to back and 254 mm high, and the sleeves swing forward and up', () => {
  const kit = bar({ roll: 25 });
  const y = centre(shaft(kit).box).y;
  const box = whole(kit);
  close(size(box).z, 0.533, 0.01, 'front to back, the yoke\'s back to the handle tips');
  close(y + 0.019 - box.min.y - 0.0127, 0.254, 0.015, 'from the shaft\'s top to the handle tips');
  const sleeve = meshes(kit, (o) => o.material.name === 'metal' && size(new THREE.Box3().setFromObject(o, true)).x > 0.3)[0];
  close(centre(sleeve.box).z, 0.14 * Math.sin(25 * Math.PI / 180), 3e-3, 'the sleeve centre ahead of the shaft');
  close(y - centre(sleeve.box).y, 0.14 * Math.cos(25 * Math.PI / 180), 3e-3, 'the sleeve centre below the shaft');
  for (const p of contact(kit, 'grips').at) assert.ok(held(kit).some(({ box: b }) => b.clone().expandByScalar(0.01).containsPoint(p)), 'a grip point off its grip');
});

test('the hands hold each handle on its bare grip, 150 to 180 mm long, and the shoulders carry the yoke, a pair of points each', () => {
  const kit = bar();
  const hands = contact(kit, 'grips');
  assert.equal(hands.landmark, 'hands');
  assert.equal(hands.body, 'frame');
  const shoulders = contact(kit, 'yoke');
  assert.equal(shoulders.landmark, 'shoulders');
  const points = { grips: hands.at, yoke: shoulders.at };
  const grips = held(kit);
  for (const p of points.grips) {
    assert.ok(grips.some(({ box }) => box.clone().expandByScalar(0.005).containsPoint(p)), 'a grip point off the bare grip');
  }
  assert.equal(points.grips.length, 2);
  assert.equal(points.yoke.length, 2);
  for (const p of points.yoke) close(p.y, 1.5, 0.02, 'the yoke point at shaft height');
  for (const g of grips) {
    const length = lengthOf(g);
    assert.ok(length > 0.14 && length < 0.19, `a bare grip ${(length * 1000).toFixed(0)} mm long`);
  }
});

test('its fit rules name the two options it takes: the shaft height for the mannequin, and the roll the lifter\'s grip sets', () => {
  assert.deepEqual(KITS.safety_bar.fit.map((f) => f.sets).sort(), ['roll', 'shaft']);
  for (const f of KITS.safety_bar.fit) assert.ok(f.rule.length > 20, `a rule for ${f.sets}`);
});

test('nothing on it moves, and it draws only in the kits\' tones', () => {
  const kit = bar();
  assert.deepEqual(Object.keys(kit.bodies), []);
  const tones = new Set(['frame', 'vinyl', 'plate', 'metal', 'pulley']);
  for (const { mesh } of meshes(kit)) assert.ok(tones.has(mesh.material.name), `${mesh.name} is ${mesh.material.name}`);
});

test('its recipe names no brand', async () => {
  const source = await readFile(path.join(HERE, 'recipes', 'safety-bar.mjs'), 'utf8');
  assert.doesNotMatch(source, /rogue|rep fitness|\brep\b|elitefts|sb-1|\blogo\b|decal/i);
});

test('the studio shows it alone, with nothing to scrub', async () => {
  const entry = shownAlone('safety_bar');
  assert.ok(entry?.camera, 'a framing to open on');
  const stage = await buildStage(entry);
  stage.pose(0);
  assert.ok(stage.kit.kits.safety_bar);
});
