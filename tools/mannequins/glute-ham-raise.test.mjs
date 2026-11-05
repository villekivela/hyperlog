// The glute-ham developer (#230), from refs/glute_ham_raise/ref.md: a hip pad pair and a sliding ankle
// assembly on a bolt-together base. Nothing on it moves, and its one exercise isn't posed yet.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import * as THREE from 'three';
import { EQUIPMENT, KITS, MACHINES, buildKit, shownAlone } from './kit.mjs';
import { buildStage } from './stage.mjs';
import { POSES } from './poses.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));

const machine = (options = {}) => {
  const kit = buildKit({ equipment: 'machine', kit: { glute_ham_raise: options } }).kits.glute_ham_raise;
  kit.place({}, {}, {});
  kit.group.updateMatrixWorld(true);
  return kit;
};
const contact = (kit, point) => kit.contacts.find((c) => c.point === point);
const close = (a, b, within, why) => assert.ok(Math.abs(a - b) <= within, `${why}: ${a.toFixed(4)}, not ${b} ± ${within}`);
const boxOf = (o) => new THREE.Box3().setFromObject(o);
const meshes = (kit, wanted = () => true) => {
  const out = [];
  kit.group.traverse((o) => { if (o.isMesh && wanted(o)) out.push({ mesh: o, box: boxOf(o) }); });
  return out;
};
const centre = (b) => b.getCenter(new THREE.Vector3());
const size = (b) => b.getSize(new THREE.Vector3());
// The ref's pad is 500 long and about 230 wide and tall; a roller is 216 by 127.
const pads = (kit) => meshes(kit, (o) => o.material?.name === 'vinyl' && Math.abs(size(boxOf(o)).z - 0.5) < 0.01 && Math.abs(size(boxOf(o)).x - 0.23) < 0.01);
const rollers = (kit) => meshes(kit, (o) => o.material?.name === 'vinyl' && Math.abs(size(boxOf(o)).x - 0.216) < 0.002);
const plate = (kit) => meshes(kit, (o) => Math.abs(size(boxOf(o)).z - 0.0048) < 1e-4)[0];

test('it is the kit of the glute-ham raise, which is not posed yet', () => {
  assert.deepEqual(MACHINES.glute_ham_raise, { kit: 'glute_ham_raise', exercises: ['glute_ham_raise'] });
  assert.ok(KITS.glute_ham_raise, 'a recipe named glute_ham_raise');
  assert.ok(EQUIPMENT.machine.includes('glute_ham_raise'));
  assert.equal(POSES.glute_ham_raise, undefined);
});

test('it stands 1740 mm by 1143 mm on the floor and 1245 mm to the top of the foot plate', () => {
  const box = boxOf(machine().group);
  close(size(box).z, 1.74, 0.01, 'the footprint\'s length');
  close(size(box).x, 1.143, 0.01, 'the footprint\'s width');
  close(box.min.y, 0, 1e-3, 'it stands on the floor');
  close(box.max.y, 1.245, 0.005, 'its height');
});

test('two half-round pads 230 wide and 500 long, 40 mm apart, their tops 1092 mm up, the rear edge at the origin', () => {
  const ps = pads(machine());
  assert.equal(ps.length, 2);
  const [left, right] = ps.sort((a, b) => centre(a.box).x - centre(b.box).x);
  for (const { box } of ps) {
    close(box.max.y, 1.092, 1e-3, 'a pad\'s top');
    close(size(box).y, 0.23, 0.01, 'a pad\'s height');
    close(box.min.z, 0, 1e-3, 'a pad\'s rear edge');
  }
  close(right.box.min.x - left.box.max.x, 0.04, 2e-3, 'the gap between the pads');
  // Half-round, flat side down: the rear edge is a corner at the base, so the pad is thinner there than at the crest.
  const rear = new THREE.Raycaster(new THREE.Vector3(centre(left.box).x, 1.092 - 0.02, -1), new THREE.Vector3(0, 0, 1));
  const front = rear.intersectObject(left.mesh)[0].point.z;
  assert.ok(front > 0.03, `the pad curves back from its crest: ${front.toFixed(3)} m to its surface 2 cm down`);
});

test('four 216 by 127 mm rollers in two pairs 203 mm apart centre to centre, the ankles between them', () => {
  const rs = rollers(machine());
  assert.equal(rs.length, 4);
  const ys = [...new Set(rs.map(({ box }) => centre(box).y.toFixed(3)))].map(Number).sort((a, b) => a - b);
  assert.equal(ys.length, 2);
  close(ys[1] - ys[0], 0.203, 1e-3, 'the rollers\' spacing');
  close(ys[1], 1.175, 0.005, 'the upper rollers\' height');
  for (const { box } of rs) close(size(box).y, 0.127, 2e-3, 'a roller\'s diameter');
  const gap = ys[1] - ys[0] - 0.127;
  close(gap, 0.076, 2e-3, 'the gap an ankle sits in');
});

test('the foot plate is 4.8 mm thick and 345 mm tall to 1245, its face 450 mm behind the pad\'s rear edge at the ref\'s setting', () => {
  const { box } = plate(machine());
  close(size(box).y, 0.345, 1e-3, 'its height');
  close(box.max.y, 1.245, 1e-3, 'its top');
  close(box.max.z, -0.45, 1e-3, 'its face');
});

test('the rollers sit 120 mm ahead of the foot plate: the gap between them is at the plate\'s middle', () => {
  const kit = machine();
  const zs = rollers(kit).map(({ box }) => centre(box).z);
  for (const z of zs) close(z, -0.45 - 0.0024 + 0.12, 0.002, 'a roller\'s place');
});

test('the assembly slides back in 1 in steps from its most forward hole, rollers and plate together, 12 steps (about 300 mm)', () => {
  const forward = machine({ slide: 0 });
  const home = machine({ slide: 4 });
  close(centre(rollers(home)[0].box).z - centre(rollers(forward)[0].box).z, -4 * 0.0254, 1e-3, 'four steps back');
  close(plate(home).box.max.z - plate(forward).box.max.z, -4 * 0.0254, 1e-3, 'the plate goes with them');
  close(plate(machine({ slide: 12 })).box.max.z - plate(forward).box.max.z, -0.3048, 1e-3, 'twelve steps, 305 mm');
  assert.throws(() => machine({ slide: 13 }), /slide/);
  assert.throws(() => machine({ slide: 1.5 }), /slide/);
});

test('a handle each side under the pad at 820 mm, sticking out front and back of it', () => {
  const kit = machine();
  const hs = meshes(kit, (o) => o.userData.held);
  assert.equal(hs.length, 2);
  for (const { box } of hs) {
    close(centre(box).y, 0.82, 0.01, 'a handle\'s height');
    assert.ok(box.min.z < 0 && box.max.z > 0.5, 'it sticks out of both ends of the pad');
  }
  const [l, r] = contact(kit, 'handles').at;
  assert.ok(l.x < r.x, 'left hand first');
  for (const p of [l, r]) assert.ok(hs.some(({ box }) => box.distanceToPoint(p) < 1e-3), 'a hand point on a handle');
});

test('nothing on it moves, and it ties the thighs to the pads, the shins to the rollers, the feet to the plate and the hands to the handles', () => {
  const kit = machine();
  assert.deepEqual(Object.keys(kit.joints), []);
  assert.deepEqual(kit.contacts.map((c) => [c.point, c.landmark, c.body]), [
    ['thighPad', 'thighs', 'frame'], ['ankleRollers', 'shins', 'frame'], ['footPlate', 'feet', 'frame'], ['handles', 'hands', 'frame'],
  ]);
  for (const c of kit.contacts) {
    assert.equal(c.at.length, 2, `${c.point}: a point a side`);
    assert.ok(c.at[0].x < c.at[1].x, `${c.point}: left first`);
  }
  // The kneecaps rest just behind the crest, on the rear curve, of each pad.
  for (const p of contact(kit, 'thighPad').at) {
    assert.ok(pads(kit).some(({ box }) => box.distanceToPoint(p) < 1e-3), 'a thigh point on a pad');
    close(p.z, 0.06, 1e-3, 'the knee 60 mm ahead of the pad\'s rear edge');
  }
  for (const p of contact(kit, 'footPlate').at) assert.ok(boxOf(plate(kit).mesh).distanceToPoint(p) < 1e-3, 'a foot point on the plate');
  for (const p of contact(kit, 'ankleRollers').at) {
    close(p.y, 1.0725, 2e-3, 'between the rollers');
    close(p.z, centre(rollers(kit)[0].box).z, 1e-3, 'at the rollers');
  }
});

// Fit rule (ref.md): the knee just behind the pads' crest and the shin level, so the plate is the shin and
// the ankle-to-sole's length behind the knee, which is 60 mm ahead of the pads' rear edge. `fitted()` only
// runs a recipe with a linkage, so the rule is read here as it solves.
test('its fit rule sets the slide from the shin: the ref\'s 0.43 m shin and 80 mm ankle put the plate at 450 mm, a longer shin further back, a shorter one forward', () => {
  const [rule] = KITS.glute_ham_raise.fit;
  assert.equal(rule.sets, 'slide');
  assert.equal(rule.solve({ L: { shin: 0.43 } }), 4);
    assert.ok(rule.solve({ L: { shin: 0.5 } }) > 4, 'a longer shin slides back');
  assert.ok(rule.solve({ L: { shin: 0.38 } }) < 4, 'a shorter one slides forward');
  assert.equal(rule.solve({ L: { shin: 0.2 } }), 0, 'no further forward than the first hole');
  assert.equal(rule.solve({ L: { shin: 0.9 } }), 12, 'no further back than the last');
});

test('the lifter\'s hands are free at the top of the rep, so the handles are a grip a hand may steady itself on', () => {
  assert.equal(contact(machine(), 'handles').optional, true);
});

test('its recipe names no brand', async () => {
  const source = await readFile(path.join(HERE, 'recipes', 'glute-ham-raise.mjs'), 'utf8');
  assert.doesNotMatch(source, /rogue|legend|hammer|gh-?1|ghd|\blogo\b|decal/i);
});

test('the studio shows it alone, with nothing to scrub', async () => {
  const entry = shownAlone('glute_ham_raise');
  assert.ok(entry?.camera, 'a framing to open on');
  const stage = await buildStage(entry);
  stage.pose(0);
  assert.ok(stage.kit.kits.glute_ham_raise);
});
