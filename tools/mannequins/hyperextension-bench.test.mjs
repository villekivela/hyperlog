// The 45-degree hyperextension bench (#220), from refs/hyperextension_bench/ref.md: a rigid frame
// with an angled hip pad, a foot plate and two heel rollers. Nothing on it moves, and the exercise
// it serves is not posed yet. Tested at two seams: what `kit.mjs` serves, and the built kit's
// measurements against the ref's own numbers (a literal each, from the spec sheet's drawing).
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import * as THREE from 'three';
import { EQUIPMENT, KITS, STATIONS, buildKit, shownAlone } from './kit.mjs';
import { buildStage } from './stage.mjs';
import { POSES } from './poses.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const library = JSON.parse(await readFile(path.join(HERE, '..', '..', 'app', 'src', 'main', 'assets', 'exercises.json'), 'utf8'));

test('it is the kit of the 45-degree hyperextension, the one exercise the ref serves, and that is not posed', async () => {
  const ref = await readFile(path.join(HERE, 'refs', 'hyperextension_bench', 'ref.md'), 'utf8');
  const named = [...ref.match(/Serves (`[^.]+?`)\./)[1].matchAll(/`([^`]+)`/g)].map((m) => m[1]);
  assert.deepEqual(named, ['45_hyperextension']);
  assert.deepEqual(STATIONS.hyperextension_bench, { kit: 'hyperextension_bench', exercises: named });
  assert.ok(KITS.hyperextension_bench, 'a recipe named hyperextension_bench');
  const kind = library.exercises.find((e) => e.id === '45_hyperextension').equipment;
  assert.ok(EQUIPMENT[kind].includes('hyperextension_bench'), `${kind} has no hyperextension_bench kit`);
  assert.equal(POSES['45_hyperextension'], undefined, 'it is posed already');
});

const bench = () => {
  const kit = buildKit({ equipment: 'weighted_bodyweight', kit: { hyperextension_bench: {} } }).kits.hyperextension_bench;
  kit.place({}, {}, {});
  kit.group.updateMatrixWorld(true);
  return kit;
};
const close = (a, b, within, why) => assert.ok(Math.abs(a - b) <= within, `${why}: ${a.toFixed(3)}, not ${b} ± ${within}`);
const meshes = (kit, wanted = () => true) => {
  const out = [];
  kit.group.traverse((o) => { if (o.isMesh && wanted(o)) out.push({ mesh: o, box: new THREE.Box3().setFromObject(o) }); });
  return out;
};
const size = (b) => b.getSize(new THREE.Vector3());
const centre = (b) => b.getCenter(new THREE.Vector3());
const contact = (kit, point) => kit.contacts.find((c) => c.point === point);

test('the frame is 1248 mm long and 666 mm across at its feet, 976 mm at most to the top of the pad, the pads\' top end at the chosen 825 mm', () => {
  const kit = bench();
  const all = meshes(kit).reduce((u, { box }) => u.union(box), new THREE.Box3());
  close(size(all).z, 1.248 + 0.05, 0.03, 'the length: the base 1248 mm and the handles 50 mm past its back end');
  assert.ok(size(all).y <= 0.976 + 1e-3, `taller than 976 mm: ${size(all).y}`);
  const feet = meshes(kit, (o) => o.material?.name === 'frame' && Math.abs(size(new THREE.Box3().setFromObject(o)).x - 0.666) < 0.005);
  assert.equal(feet.length, 2, 'two cross feet 666 mm across, one at each end');
  const top = contact(kit, 'thighPad').at[0];
  // The pad's middle is half its 330 mm down the 45 degree slope from the top end.
  close(top.y, 0.825 - 0.165 * Math.SQRT1_2, 0.02, 'the thigh pad\'s middle');
});

test('two hip pads about 210 mm wide with a 55 mm gap, 330 mm long, 90 mm thick, sloping down at 45 degrees toward the feet', () => {
  const pads = meshes(bench(), (o) => o.material?.name === 'vinyl' && o.userData.pad === 'hip');
  assert.equal(pads.length, 2);
  const xs = pads.map(({ box }) => centre(box).x).sort((a, b) => a - b);
  close(xs[1] - xs[0], 0.265, 0.005, 'the pads\' centres, 210 mm wide and 55 mm apart');
  const { box } = pads[0];
  // A pad 330 long and 90 thick tilted 45 degrees fills (330 + 90) / sqrt 2 in both y and z.
  close(size(box).y, (0.33 + 0.09) * Math.SQRT1_2, 0.02, 'the pad\'s height tilted at 45 degrees');
  close(size(box).z, (0.33 + 0.09) * Math.SQRT1_2, 0.02, 'the pad\'s reach tilted at 45 degrees');
});

test('the foot plate is 470 mm wide square to the body line, the body line 900 mm from the pad\'s top end along the slope', () => {
  const kit = bench();
  const plate = meshes(kit, (o) => o.userData.pad === 'foot')[0];
  close(size(plate.box).x, 0.47, 0.005, 'the plate\'s width');
  const feet = contact(kit, 'footPlate').at[0];
  const hip = contact(kit, 'thighPad').at[0];
  const along = feet.clone().sub(hip).setX(0);
  // From the pad's middle, the contact, so 165 mm short of the 900 mm from its top end.
  close(along.length(), 0.9 - 0.165, 0.02, 'the body line from the pad to the plate');
  close(Math.atan2(-along.y, along.z) * 180 / Math.PI, 45, 3, 'the slope of the body line');
});

test('two heel rollers 190 mm long and 130 mm across, 195 mm up the body line from the plate and 210 mm off the pad line', () => {
  const kit = bench();
  const rollers = meshes(kit, (o) => o.userData.pad === 'heel');
  assert.equal(rollers.length, 2);
  for (const { box } of rollers) {
    close(size(box).x, 0.19, 0.005, 'a roller\'s length');
    close(size(box).y, 0.13, 0.005, 'a roller\'s diameter');
  }
  const calves = contact(kit, 'heelRollers').at;
  assert.equal(calves.length, 2);
  const plate = contact(kit, 'footPlate').at[0];
  const hip = contact(kit, 'thighPad').at[0];
  // Side-on: the body line and the roller's offset from the plate, both in the y-z plane.
  const line = plate.clone().sub(hip).setX(0).normalize();
  const rel = calves[0].clone().sub(plate).setX(0);
  close(-rel.dot(line), 0.195, 0.02, 'the roller up the body line from the plate');
  const off = rel.clone().addScaledVector(line, -rel.dot(line)).length();
  close(off, 0.21, 0.03, 'the roller off the body line');
});

test('grab handles run horizontal at 690 mm, a pair reaching 50 mm past the base\'s back end', () => {
  const kit = bench();
  const handles = meshes(kit, (o) => o.userData.held);
  assert.equal(handles.length, 2);
  for (const { box } of handles) close(centre(box).y, 0.69, 0.01, 'a handle\'s height');
  const back = meshes(kit, (o) => o.material?.name === 'frame' && Math.abs(size(new THREE.Box3().setFromObject(o)).x - 0.666) < 0.005)
    .map(({ box }) => box.min.z).reduce((a, b) => Math.min(a, b));
  close(back - Math.min(...handles.map(({ box }) => box.min.z)), 0.05, 0.02, 'the handles past the back end');
});

test('nothing on it moves, and its three contacts are the thigh pad, the foot plate and the heel rollers', () => {
  const kit = bench();
  assert.deepEqual(Object.keys(kit.bodies ?? {}), []);
  assert.deepEqual(kit.contacts.map((c) => [c.point, c.landmark]), [['thighPad', 'thighs'], ['footPlate', 'feet'], ['heelRollers', 'shins']]);
});

test('its recipe names no brand and wears no logo or decal', async () => {
  const source = await readFile(path.join(HERE, 'recipes', 'hyperextension-bench.mjs'), 'utf8');
  assert.doesNotMatch(source, /bench ?fitness|revolve|rogue|titan|hoist|hammer|\blogo\b|decal/i);
});

test('the studio shows it alone, with nothing to scrub', async () => {
  const entry = shownAlone('hyperextension_bench');
  assert.ok(entry?.camera, 'a framing to open on');
  const stage = await buildStage(entry);
  stage.pose(0);
  assert.ok(stage.kit.kits.hyperextension_bench);
});
