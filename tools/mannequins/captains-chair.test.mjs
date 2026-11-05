// The captain's chair (#237), from refs/captains_chair/ref.md: a fixed frame with an upright back pad,
// two arm pads and a knee raise grip at the front of each. Nothing on it moves. Measures are the ref's,
// from the rear end of the floor rails ("from the rear"); the kit's z is that less 0.41, the elbows' place.
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

const REAR = 0.41;
const chair = () => {
  const kit = buildKit({ equipment: 'bodyweight', kit: { captains_chair: {} } }).kits.captains_chair;
  kit.place({}, {}, {});
  kit.group.updateMatrixWorld(true);
  return kit;
};
const contact = (kit, point) => kit.contacts.find((c) => c.point === point);
const close = (a, b, within, why) => assert.ok(Math.abs(a - b) <= within, `${why}: ${a.toFixed(3)}, not ${b} ± ${within}`);
const meshes = (kit, wanted = () => true) => {
  const out = [];
  kit.group.traverse((o) => { if (o.isMesh && wanted(o)) out.push({ mesh: o, box: new THREE.Box3().setFromObject(o) }); });
  return out;
};
const size = (b) => b.getSize(new THREE.Vector3());
const centre = (b) => b.getCenter(new THREE.Vector3());
const rear = (z) => z + REAR;

test('it is the kit of roman_chair_crunch, which the ref names, and that exercise is not posed', async () => {
  const ref = await readFile(path.join(HERE, 'refs', 'captains_chair', 'ref.md'), 'utf8');
  const named = [...ref.match(/## Movement and exercises served[\s\S]*?\n## /)[0].matchAll(/^- `([^`]+)`:/gm)].map((m) => m[1]);
  assert.deepEqual(named, ['roman_chair_crunch']);
  assert.deepEqual(STATIONS.captains_chair, { kit: 'captains_chair', exercises: named });
  assert.ok(KITS.captains_chair, 'a recipe named captains_chair');
  const kind = library.exercises.find((e) => e.id === 'roman_chair_crunch')?.equipment;
  assert.ok(EQUIPMENT[kind].includes('captains_chair'), `${kind} has no captains_chair kit`);
  assert.equal(POSES.roman_chair_crunch, undefined, 'roman_chair_crunch is posed already');
});

test('it stands 1054 mm long by 736 wide on two floor rails 640 apart, 1550 tall to the back pad', () => {
  const kit = chair();
  const box = new THREE.Box3().setFromObject(kit.group);
  close(size(box).z, 1.054, 0.01, 'the footprint\'s length');
  close(size(box).x, 0.736, 0.02, 'the footprint\'s width');
  close(box.min.z, -REAR, 0.01, 'the rear end of the rails');
  close(box.max.y, 1.55, 0.02, 'the overall height');
  const rails = meshes(kit, (o) => { const s = size(new THREE.Box3().setFromObject(o)); return s.z > 1 && s.y < 0.15; });
  assert.equal(rails.length, 2, 'two floor rails');
  close(Math.abs(centre(rails[0].box).x - centre(rails[1].box).x), 0.64, 0.01, 'the rails\' centres apart');
});

test('nothing on it moves; the forearms, hands and back are contacts on the frame, a pair of points each but the back', () => {
  const kit = chair();
  assert.deepEqual(kit.bodies, {});
  assert.deepEqual(Object.keys(kit.joints), []);
  assert.deepEqual(kit.contacts.map((c) => [c.body, c.point, c.landmark, c.at.length]), [
    ['frame', 'armPads', 'forearms', 2],
    ['frame', 'grips', 'hands', 2],
    ['frame', 'backPad', 'back', 1],
  ]);
});

test('two arm pads 250 long, 120 wide and 80 thick, 560 apart, from 330 to 580 from the rear, their tops 1380 up', () => {
  const kit = chair();
  const pads = meshes(kit, (o) => o.material.name === 'vinyl' && Math.abs(size(new THREE.Box3().setFromObject(o)).z - 0.25) < 1e-3);
  assert.equal(pads.length, 2);
  for (const { box } of pads) {
    close(size(box).x, 0.12, 1e-3, 'a pad\'s width');
    close(size(box).y, 0.08, 1e-3, 'a pad\'s thickness');
    close(rear(box.min.z), 0.33, 1e-3, 'the pad\'s rear end');
    close(rear(box.max.z), 0.58, 1e-3, 'the pad\'s front end');
    close(box.max.y, 1.38, 1e-3, 'the pad\'s top');
  }
  close(Math.abs(centre(pads[0].box).x - centre(pads[1].box).x), 0.56, 1e-3, 'the pads\' centres apart');
  // The forearms lie on the pads' tops, the elbows on their rear halves.
  for (const [i, p] of contact(kit, 'armPads').at.entries()) {
    close(p.y, 1.38, 1e-3, 'the forearm contact on the pad top');
    close(Math.abs(p.x), 0.28, 1e-3, 'on the pad\'s middle');
    assert.ok(rear(p.z) > 0.33 && rear(p.z) < 0.455, `on the pad's rear half, at ${rear(p.z).toFixed(3)} (pad ${i})`);
  }
});

test('the back pad is 290 wide and 550 tall from 1000 to 1550 up, reclined 10 degrees, its face 360 from the rear at the bottom edge', () => {
  const kit = chair();
  const [back] = meshes(kit, (o) => o.material.name === 'vinyl' && size(new THREE.Box3().setFromObject(o)).y > 0.5);
  assert.ok(back, 'a back pad');
  close(size(back.box).x, 0.29, 1e-3, 'the back pad\'s width');
  close(back.box.min.y, 1.0, 0.03, 'its bottom');
  close(back.box.max.y, 1.55, 0.03, 'its top');
  const face = contact(kit, 'backPad').at[0];
  // The contact is on the face's middle, which leans back 10 degrees: 360 at 1000, 360 - 0.1763 * (y - 1000) above.
  close(rear(face.z), 0.36 - (face.y - 1.0) * Math.tan(THREE.MathUtils.degToRad(10)), 0.005, 'the face at the contact\'s height');
  close(face.y, 1.275, 0.02, 'the face\'s middle');
});

test('a knee raise grip each side rises 230 long from a hub 700 from the rear at 1320, leaning back 15 degrees and inward, the hands at 670 and 1430', () => {
  const kit = chair();
  const grips = meshes(kit, (o) => o.userData.held && size(new THREE.Box3().setFromObject(o)).y > 0.2);
  assert.equal(grips.length, 2);
  for (const { box } of grips) {
    close(size(box).y, 0.23, 0.02, 'a grip\'s height');
    close(rear(box.max.z), 0.70, 0.02, 'the grip\'s base from the rear');
    close(rear(box.min.z), 0.64, 0.02, 'the grip\'s top from the rear');
    close(box.min.y, 1.32, 0.03, 'the hub\'s height');
    close(box.max.y, 1.55, 0.03, 'the grip\'s top');
  }
  close(Math.abs(centre(grips[0].box).x - centre(grips[1].box).x), 0.535, 0.03, 'the grips between base (570) and top (500)');
  for (const p of contact(kit, 'grips').at) {
    close(rear(p.z), 0.67, 0.01, 'the hand from the rear');
    close(p.y, 1.43, 0.01, 'the hand\'s height');
    const on = grips.filter(({ box }) => box.distanceToPoint(p) < 1e-6);
    assert.equal(on.length, 1, 'the hand is on a held grip');
  }
});

test('two foot pegs 650 apart on the side frames, about 280 up, under the front half of the arm pads', () => {
  const kit = chair();
  const pegs = meshes(kit, (o) => o.userData.peg);
  assert.equal(pegs.length, 2);
  for (const { box } of pegs) {
    close(centre(box).y, 0.28, 0.02, 'a peg\'s height');
    assert.ok(rear(box.min.z) >= 0.58 && rear(box.max.z) <= 0.78, `under the front half, ${rear(box.min.z).toFixed(2)} to ${rear(box.max.z).toFixed(2)}`);
  }
  close(Math.abs(centre(pegs[0].box).x - centre(pegs[1].box).x), 0.65, 0.01, 'the pegs apart');
});

test('its recipe names no brand', async () => {
  const source = await readFile(path.join(HERE, 'recipes', 'captains-chair.mjs'), 'utf8');
  assert.doesNotMatch(source, /life ?fitness|lbr|axiom|atlantis|hammer|\blogo\b|decal/i);
});

test('the studio shows it alone, with nothing to scrub', async () => {
  const entry = shownAlone('captains_chair');
  assert.ok(entry?.camera, 'a framing to open on');
  const stage = await buildStage(entry);
  stage.pose(0);
  assert.ok(stage.kit.kits.captains_chair);
});
