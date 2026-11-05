// The glute-ham developer (#230), from refs/glute_ham_raise/ref.md: a hip pad pair and a sliding ankle
// assembly on a bolt-together base. Nothing on it moves, and its one exercise isn't posed yet.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import * as THREE from 'three';
import { EQUIPMENT, KITS, MACHINES, buildKit, shownAlone } from './kit.mjs';
import { buildStage, FRAMES } from './stage.mjs';
import { prepare } from './entries.mjs';
import { CHECKS, checkStage } from './checks.mjs';
import { wpos } from './rig.mjs';
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
// Each pad is a row of slices, a box each for the checks to measure its curve by: the pad is their union.
const pads = (kit) => [-1, 1].map((side) => {
  const slices = meshes(kit, (o) => o.material?.name === 'vinyl' && Math.abs(size(boxOf(o)).x - 0.23) < 0.01 && Math.sign(centre(boxOf(o)).x) === side);
  return { slices, box: slices.reduce((all, { box }) => all.union(box), new THREE.Box3()) };
});
const rollers = (kit) => meshes(kit, (o) => o.material?.name === 'vinyl' && Math.abs(size(boxOf(o)).x - 0.216) < 0.002);
const plate = (kit) => meshes(kit, (o) => Math.abs(size(boxOf(o)).z - 0.0048) < 1e-4)[0];

test('it is the kit of the glute-ham raise, the one exercise posed on it', () => {
  assert.deepEqual(MACHINES.glute_ham_raise, { kit: 'glute_ham_raise', exercises: ['glute_ham_raise'] });
  assert.ok(KITS.glute_ham_raise, 'a recipe named glute_ham_raise');
  assert.ok(EQUIPMENT.machine.includes('glute_ham_raise'));
  assert.equal(POSES.glute_ham_raise.kit.glute_ham_raise instanceof Object, true);
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
  const [left, right] = ps;
  for (const { box } of ps) {
    close(box.max.y, 1.092, 1e-3, 'a pad\'s top');
    close(size(box).y, 0.23, 0.01, 'a pad\'s height');
    close(box.min.z, 0, 1e-3, 'a pad\'s rear edge');
  }
  close(right.box.min.x - left.box.max.x, 0.04, 2e-3, 'the gap between the pads');
  // Half-round, flat side down: the rear edge is a corner at the base, so the pad is thinner there than at the crest.
  const rear = new THREE.Raycaster(new THREE.Vector3(centre(left.box).x, 1.092 - 0.02, -1), new THREE.Vector3(0, 0, 1));
  const front = rear.intersectObjects(left.slices.map(({ mesh }) => mesh))[0].point.z;
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

test('the foot plate is 4.8 mm thick and 345 mm tall to 1245, its face at a hole 300 mm behind the pads\' rear edge and 1 in a step back from it', () => {
  const { box } = plate(machine({ slide: 0 }));
  close(size(box).y, 0.345, 1e-3, 'its height');
  close(box.max.y, 1.245, 1e-3, 'its top');
  close(box.max.z, -0.3, 1e-3, 'its face at the most forward hole');
});

test('the rollers\' centres sit 150 mm ahead of the foot plate\'s middle, clear of the foot that lies on it', () => {
  const kit = machine();
  const mid = centre(plate(kit).box).z;
  for (const { box } of rollers(kit)) close(centre(box).z - mid, 0.15, 0.002, 'a roller\'s place');
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

test('nothing on it moves, and it ties the knees to the pads, the shins to the rollers, the feet to the plate and the hands to the handles', () => {
  const kit = machine();
  assert.deepEqual(Object.keys(kit.joints), []);
  assert.deepEqual(kit.contacts.map((c) => [c.point, c.landmark, c.body]), [
    ['thighPad', 'knees', 'frame'], ['ankleRollers', 'shins', 'frame'], ['footPlate', 'feet', 'frame'], ['handles', 'hands', 'frame'],
  ]);
  for (const c of kit.contacts) {
    assert.equal(c.at.length, 2, `${c.point}: a point a side`);
    assert.ok(c.at[0].x < c.at[1].x, `${c.point}: left first`);
  }
  // The lifter rests on the crest of each pad: the knees on it at the top, the thighs along it at the bottom.
  for (const p of contact(kit, 'thighPad').at) {
    assert.ok(pads(kit).some(({ box }) => box.distanceToPoint(p) < 1e-3), 'a knee point on a pad');
    close(p.y, 1.092, 1e-3, 'the pad\'s crest');
  }
  for (const p of contact(kit, 'footPlate').at) assert.ok(boxOf(plate(kit).mesh).distanceToPoint(p) < 1e-3, 'a foot point on the plate');
  for (const p of contact(kit, 'ankleRollers').at) {
    close(p.y, 1.0725, 2e-3, 'between the rollers');
    close(p.z, centre(rollers(kit)[0].box).z, 1e-3, 'at the rollers');
  }
});

// Fit rule (ref.md): the knee on the pads' crest, just behind it, the shin sloping down and back to an ankle in the roller
// gap, the sole on the plate. The mannequin's shin and ankle give the hole; 0.429 m and 0.104 m are its own.
test('its fit rule sets the slide from the shin: a longer shin slides back, a shorter one forward, to the holes\' ends', () => {
  const [rule] = KITS.glute_ham_raise.fit;
  assert.equal(rule.sets, 'slide');
  const mannequin = rule.solve({ L: { shin: 0.429, ankleY: 0.104 } });
  assert.equal(mannequin, 1);
  assert.ok(rule.solve({ L: { shin: 0.5, ankleY: 0.104 } }) > mannequin + 1, 'a longer shin slides back');
  assert.equal(rule.solve({ L: { shin: 0.3, ankleY: 0.104 } }), 0, 'no further forward than the first hole');
  assert.equal(rule.solve({ L: { shin: 0.9, ankleY: 0.104 } }), 12, 'no further back than the last');
});

test('it is a static machine: its linkage is one place, the knee on the pad, the ankle in the gap, the sole on the plate', () => {
  const at = KITS.glute_ham_raise.linkage({ slide: 1 }, { shin: 0.429, ankleY: 0.104 }).at();
  close(at.knee.z, 0.2, 0.015, 'the knee behind the crest');
  close(at.ankle.y, 1.0725, 1e-3, 'the ankle in the gap');
  close(at.sole.z, -0.3254, 1e-3, 'the sole on the plate, one hole back');
  close(at.ankle.z - at.sole.z, 0.104, 1e-3, 'the ankle a joint\'s height off the sole');
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

// The exercise on the kit (#230): the lifter kneels on the pads, the shins level back to the rollers with the soles
// on the plate, and the body, hips straight, lowers about the knees from upright to level.
const stood = async () => {
  const entry = prepare('glute_ham_raise');
  return { entry, stage: await buildStage(entry) };
};
const at = (stage, d, bone) => { stage.pose(d); stage.scene.updateMatrixWorld(true); return wpos(stage.rig.bones[bone]); };
const between = (a, b, c) => THREE.MathUtils.radToDeg(a.clone().sub(b).angleTo(c.clone().sub(b)));

test('it is posed on the kit, every frame passing every check', async () => {
  const { entry, stage } = await stood();
  assert.deepEqual(Object.keys(entry.kit), ['glute_ham_raise']);
  const checks = checkStage(entry, stage);
  for (const c of CHECKS) assert.ok(checks[c].ok, `${c}: frame ${checks[c].frame}, ${checks[c].found}`);
});

test('the knees bend 90° at the top, the thighs upright, and straighten to within 15° at the bottom, the body level', async () => {
  const { stage } = await stood();
  const knee = (d) => between(at(stage, d, 'thigh_l'), at(stage, d, 'calf_l'), at(stage, d, 'foot_l'));
  close(knee(0), 100, 8, 'the knee at the top');
  assert.ok(knee(1) > 160, `the knee at the bottom ${knee(1).toFixed(1)}°`);
  const lean = (d) => { const t = at(stage, d, 'thigh_l'); const k = at(stage, d, 'calf_l'); return THREE.MathUtils.radToDeg(Math.atan2(Math.hypot(t.x - k.x, t.z - k.z), t.y - k.y)); };
  assert.ok(lean(0) < 12, `the thighs lean ${lean(0).toFixed(1)}° from upright at the top`);
  assert.ok(lean(1) > 60, `the thighs lean ${lean(1).toFixed(1)}° from upright at the bottom`);
});

test('the hips stay straight through the rep: the trunk runs on from the thigh within 20°', async () => {
  const { stage } = await stood();
  for (let f = 0; f < FRAMES; f++) {
    const d = f / (FRAMES - 1);
    const angle = between(at(stage, d, 'calf_l'), at(stage, d, 'thigh_l'), at(stage, d, 'spine_03'));
    assert.ok(angle > 160, `frame ${f}: the hip at ${angle.toFixed(1)}°`);
  }
});

test('the ankles and knees stay where they are, the ankles between the rollers and the soles on the plate', async () => {
  const { stage } = await stood();
  const kit = stage.kit.kits.glute_ham_raise;
  for (const bone of ['foot_l', 'foot_r', 'calf_l', 'calf_r']) {
    const first = at(stage, 0, bone);
    for (let f = 1; f < FRAMES; f++) assert.ok(at(stage, f / (FRAMES - 1), bone).distanceTo(first) < 0.01, `${bone} moves at frame ${f}`);
  }
  for (const k of ['l', 'r']) {
    const ankle = at(stage, 0, `foot_${k}`);
    const [roller] = contact(kit, 'ankleRollers').at;
    assert.ok(Math.abs(ankle.x) > 0.027 && Math.abs(ankle.x) < 0.243, `the ${k} ankle at ${ankle.x.toFixed(3)} is out of the roller's length`);
    close(ankle.y, roller.y, 0.01, `the ${k} ankle between the rollers`);
    const [, plateAt] = contact(kit, 'footPlate').at;
    close(ankle.z - plateAt.z, 0.104, 0.005, `the ${k} ankle a joint's height ahead of the plate`);
  }
});
