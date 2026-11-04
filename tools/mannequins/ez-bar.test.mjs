// The EZ bar (#209), from refs/ez_bar/ref.md: a short Olympic-sleeved bar with a zigzag shaft, held
// and loaded by the mannequin. Nothing on it moves.
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

test('it is the kit of the 3 EZ bar exercises the ref names, none of them posed', async () => {
  const ref = await readFile(path.join(HERE, 'refs', 'ez_bar', 'ref.md'), 'utf8');
  const named = [...ref.match(/^.*Serves .*$/m)[0].matchAll(/`([^`]+)`/g)].map((m) => m[1]);
  assert.equal(named.length, 3);
  assert.deepEqual(BARS.ez_bar, { kit: 'ez_bar', exercises: named });
  assert.ok(KITS.ez_bar, 'a recipe named ez_bar');
  assert.deepEqual(EQUIPMENT.ez_bar, ['ez_bar']);
  for (const id of named) {
    assert.equal(library.exercises.find((e) => e.id === id)?.equipment, 'ez_bar');
    assert.equal(POSES[id], undefined, `${id} is posed already`);
  }
});

const bar = (options = {}) => {
  const kit = buildKit({ equipment: 'ez_bar', kit: { ez_bar: options } }).kits.ez_bar;
  kit.place({}, {}, {});
  kit.group.updateMatrixWorld(true);
  return kit;
};
const close = (a, b, within, why) => assert.ok(Math.abs(a - b) <= within, `${why}: ${a.toFixed(4)}, not ${b} ± ${within}`);
const meshes = (kit, wanted = () => true) => {
  const out = [];
  kit.group.traverse((o) => { if (o.isMesh && wanted(o)) out.push({ mesh: o, box: new THREE.Box3().setFromObject(o, true) }); });
  return out;
};
const size = (b) => b.getSize(new THREE.Vector3());
const centre = (b) => b.getCenter(new THREE.Vector3());
const contact = (kit, point) => kit.contacts.find((c) => c.point === point);

test('it is 1384 mm long with 267 mm sleeves 50 mm across, 800 mm of shaft between them', () => {
  const kit = bar();
  const whole = meshes(kit).reduce((u, { box }) => u.union(box), new THREE.Box3());
  close(size(whole).x, 1.384, 1e-3, 'the overall length');
  const sleeves = meshes(kit, (o) => o.material.name === 'metal' && size(new THREE.Box3().setFromObject(o, true)).x > 0.25 && size(new THREE.Box3().setFromObject(o, true)).y > 0.04);
  assert.equal(sleeves.length, 2);
  for (const { box } of sleeves) {
    close(size(box).x, 0.267, 1e-3, 'a sleeve\'s loadable length');
    close(size(box).y, 0.05, 1e-3, 'a sleeve\'s diameter');
  }
  
  // The collars stand between: the shaft is 800 mm from one collar to the other.
  const collars = meshes(kit, (o) => o.material.name === 'frame').map(({ box }) => box).sort((a, b) => a.min.x - b.min.x);
  assert.equal(collars.length, 2);
  close(collars[1].min.x - collars[0].max.x, 0.8, 1e-3, 'the shaft between the collars');

});

test('the shaft zigzags in the bar\'s horizontal plane: 28.5 mm tube, 80 mm peak to trough, forward kinks at the outer pair', () => {
  const kit = bar();
  const held = meshes(kit, (o) => o.userData.held);
  assert.equal(held.length, 7, 'the centre section and three segments a side: inner grip, outer grip, the return');
  const whole = held.reduce((u, { box }) => u.union(box), new THREE.Box3());
  // Peak to trough, tube included: the centre line's 80 mm plus a 28.5 mm tube.
  // A slanted tube's flat end sits a little inside its round bend.
  close(size(whole).z, 0.08 + 0.0285, 4e-3, 'the zigzag\'s depth');
  close(size(whole).y, 0.0285, 1e-3, 'the shaft\'s diameter');
  // The centre line is straight 120 mm in the middle, in line with the sleeves.
  const middle = held.filter(({ box }) => Math.abs(centre(box).x) < 0.01);
  assert.equal(middle.length, 1);
  close(centre(middle[0].box).z, 0, 1e-3, 'the centre section lies on the sleeve axis');
  close(size(middle[0].box).x, 0.12, 2e-3, 'the centre section\'s length');
});

test('the hands go on the outer pair 510 mm apart or the inner pair 250 mm apart, on the segments', () => {
  const kit = bar();
  assert.deepEqual(Object.keys(kit.joints), []);
  assert.deepEqual(kit.contacts.map((c) => c.point), ['outer', 'inner']);
  for (const [point, spread] of [['outer', 0.51], ['inner', 0.25]]) {
    const c = contact(kit, point);
    assert.equal(c.body, 'bar');
    assert.equal(c.landmark, 'hands');
    const [l, r] = c.at;
    assert.ok(l.x < r.x, `${point}: left hand first`);
    close(r.x - l.x, spread, 1e-3, `the ${point} grips' spread`);
    for (const p of c.at) {
      const on = meshes(kit, (o) => o.userData.held).filter(({ box }) => box.distanceToPoint(p) < 1e-6);
      assert.ok(on.length, `no held segment under the ${point} grip`);
    }
  }
});

test('it is a free body the hands carry, and lies on the floor when no pose holds it', () => {
  const kit = bar();
  assert.equal(kit.free, true);
  const whole = meshes(kit).reduce((u, { box }) => u.union(box), new THREE.Box3());
  close(whole.min.y, 0, 1e-3, 'the sleeves rest on the floor');
});

test('a pose carries it by its bar: the hands\' line, the bar level at the height the pose gives', () => {
  const kit = buildKit({ equipment: 'ez_bar', kit: { ez_bar: {} } }).kits.ez_bar;
  kit.place({ bar: new THREE.Vector3(0.1, 1.2, 0.3) }, {}, {});
  kit.group.updateMatrixWorld(true);
  const [l, r] = contact(kit, 'outer').at;
  close((l.y + r.y) / 2, 1.2, 1e-6, 'the grips at the height of the bar');
  close((l.x + r.x) / 2, 0.1, 1e-6, 'about the bar\'s x');
  close((l.z + r.z) / 2, 0.3, 1e-6, 'the hands\' z, on the outer pair');
});

test('its recipe names no brand', async () => {
  const source = await readFile(path.join(HERE, 'recipes', 'ez-bar.mjs'), 'utf8');
  assert.doesNotMatch(source, /rogue|e-coat|titan|hoist|hammer|\blogo\b|decal/i);
});

test('the studio shows it alone, with nothing to scrub', async () => {
  const entry = shownAlone('ez_bar');
  assert.ok(entry?.camera, 'a framing to open on');
  const stage = await buildStage(entry);
  stage.pose(0);
  assert.ok(stage.kit.kits.ez_bar);
});

test('with grip "inner" the bar is carried so the inner pair is at the hands\' line', () => {
  const kit = buildKit({ equipment: 'ez_bar', kit: { ez_bar: { grip: 'inner' } } }).kits.ez_bar;
  kit.place({ bar: new THREE.Vector3(0, 1.2, 0.3) }, {}, {});
  kit.group.updateMatrixWorld(true);
  const [l, r] = contact(kit, 'inner').at;
  close((l.z + r.z) / 2, 0.3, 1e-6, 'the hands\' z, on the inner pair');
  const [ol, or] = contact(kit, 'outer').at;
  assert.ok((ol.z + or.z) / 2 > 0.3 + 0.01, 'the outer kinks are forward of the inner pair');
});
