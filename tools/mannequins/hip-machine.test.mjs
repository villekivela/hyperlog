// The hip machine (#214), from refs/hip_machine/ref.md: two arms under the seat on one vertical
// axis, each an L out to a post with a knee pad and a foot plate, swinging apart (abduction) or
// together (adduction) on a start angle set in 10° steps.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import * as THREE from 'three';
import { EQUIPMENT, KITS, MACHINES, buildKit, shownAlone } from './kit.mjs';
import { rad } from './rig.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const machine = (options = {}) => {
  const kit = buildKit({ equipment: 'machine', kit: { hip_machine: options } }).kits.hip_machine;
  kit.place({}, {}, {});
  kit.group.updateMatrixWorld(true);
  return kit;
};
const contact = (kit, point) => kit.contacts.find((c) => c.point === point);
const close = (a, b, within, why) => assert.ok(Math.abs(a - b) <= within, `${why}: ${a.toFixed(3)}, not ${b} ± ${within}`);
const boxes = (kit, wanted) => {
  const out = [];
  kit.group.traverse((o) => { if (o.isMesh && wanted(o)) out.push(new THREE.Box3().setFromObject(o)); });
  return out;
};
const unite = (bs) => bs.reduce((u, b) => u.union(b), new THREE.Box3());
const centre = (b) => b.getCenter(new THREE.Vector3());
const role = (name) => (o) => o.userData.role === name;
const DEG = Math.PI / 180;

test('the hip machine is the kit of the machine kind that serves abduction and adduction', () => {
  assert.ok(KITS.hip_machine, 'a recipe named hip_machine');
  assert.equal(MACHINES.hip_machine.kit, 'hip_machine');
  assert.deepEqual(MACHINES.hip_machine.exercises, ['machine_hip_abduction', 'machine_adduction']);
  assert.ok(EQUIPMENT.machine.includes('hip_machine'));
  const alone = shownAlone('hip_machine');
  assert.equal(alone.equipment, 'machine');
  assert.deepEqual(Object.keys(alone.kit), ['hip_machine']);
});

test('it stands 1550 by 660 mm on the floor and 1400 mm high', () => {
  const all = unite(boxes(machine(), () => true));
  close(all.max.z - all.min.z, 1.55, 0.05, 'the footprint\'s length');
  close(all.max.x - all.min.x, 0.66, 0.05, 'the footprint\'s width');
  close(all.max.y, 1.4, 0.03, 'the height');
  close(all.min.y, 0, 0.005, 'the floor');
});

test('the seat top is 520 mm up and 360 deep, its front edge 140 mm ahead of the pivot', () => {
  const seat = unite(boxes(machine(), role('seat')));
  close(seat.max.y, 0.52, 0.005, 'the seat top');
  close(seat.max.z - seat.min.z, 0.36, 0.02, 'the seat depth');
  close(seat.max.z, 0.14, 0.01, 'the seat\'s front edge ahead of the axis');
});

test('each arm turns about the one vertical axis on the centre line, 0 to 50° from closed, opening outward', () => {
  const kit = machine();
  assert.deepEqual(Object.keys(kit.joints).sort(), ['arm_l', 'arm_r']);
  for (const [name, s] of [['arm_l', 1], ['arm_r', -1]]) {
    const j = kit.joints[name];
    assert.equal(j.type, 'revolute');
    close(Math.abs(j.axis.y), 1, 1e-9, `${name}'s axis, vertical`);
    close(j.origin.x, 0, 1e-9, `${name}'s axis on the centre line`);
    close(j.origin.z, 0, 1e-9, `${name}'s axis`);
    assert.deepEqual(j.limits, [0, rad(50)]);
    // Opening is positive for both: the pad moves out, away from the centre line.
    const before = contact(kit, `pad_${name.at(-1)}`).at[0].x;
    kit.scrub(name, rad(45));
    const after = contact(kit, `pad_${name.at(-1)}`).at[0].x;
    assert.ok(s * (after - before) > 0.1, `${name} opening moves its pad outward`);
  }
  assert.equal(kit.joints.arm_l.axis.y, -kit.joints.arm_r.axis.y);
});

test('the arm\'s pad swings 400 mm out from the axis in a circle, and the scrub stops at the limits', () => {
  const kit = machine();
  const at = () => contact(kit, 'pad_l').at[0];
  const closed = at().clone();
  kit.scrub('arm_l', rad(45));
  const open = at();
  close(Math.hypot(open.x, open.z) , Math.hypot(closed.x, closed.z), 1e-6, 'a pad\'s radius from the axis');
  close(Math.atan2(open.x, open.z) - Math.atan2(closed.x, closed.z), rad(45), 1e-6, 'the pad\'s turn');
  assert.equal(kit.joints.arm_l.value, rad(45));
  kit.scrub('arm_l', rad(500));
  assert.equal(kit.joints.arm_l.value, rad(50));
  kit.scrub('arm_l', -1);
  assert.equal(kit.joints.arm_l.value, 0);
});

test('closed, the pads press the outsides of the knees 400 mm ahead of the axis and 560 mm up, the plates 80 mm up', () => {
  const kit = machine();
  for (const [k, s] of [['l', 1], ['r', -1]]) {
    const pad = contact(kit, `pad_${k}`);
    assert.equal(pad.landmark, 'shins');
    close(pad.at[0].x, s * 0.14, 0.01, 'the pad\'s face, on the knee\'s outside');
    close(pad.at[0].y, 0.56, 0.01, 'the pad\'s height');
    close(pad.at[0].z, 0.4, 0.02, 'the pad\'s reach');
    const plate = contact(kit, `plate_${k}`);
    assert.equal(plate.landmark, 'feet');
    close(plate.at[0].y, 0.08, 0.01, 'the plate\'s height');
    close(plate.at[0].z, pad.at[0].z, 0.03, 'the plate under the pad\'s post');
  }
  assert.equal(contact(kit, 'seat').landmark, 'thighs');
  assert.equal(contact(kit, 'back').landmark, 'back');
  assert.equal(contact(kit, 'seat').body, 'frame');
});

test('the start angle moves the arms in 10° steps; adduction puts the pads between the knees, starting open', () => {
  assert.deepEqual(KITS.hip_machine.fit.map((f) => f.sets), ['start']);
  const kit = machine({ start: 30 });
  close(kit.joints.arm_l.value, rad(30), 1e-9, 'the arm at its start');
  assert.equal(machine({ start: 34 }).joints.arm_l.value, rad(30));
  assert.equal(machine({ start: 999 }).joints.arm_l.value, rad(50));
  const inner = machine({ pads: 'inner', start: 40 });
  assert.equal(inner.joints.arm_l.value, rad(40));
  const pad = contact(inner, 'pad_l').at[0];
  const plain = contact(machine({ start: 40 }), 'pad_l').at[0];
  assert.ok(pad.x < plain.x - 0.05, 'an inner pad lies nearer the centre line than an outer one');
  assert.throws(() => machine({ pads: 'upside' }), /pads/);
});

test('the weight stack tower stands ahead of the lifter past the posts, and is the tallest part; the backrest leans back', () => {
  const kit = machine();
  const plates = unite(boxes(kit, (o) => o.material?.name === 'plate'));
  assert.ok(centre(plates).z > 0.8, `the stack at z ${centre(plates).z.toFixed(2)}, not ahead of the posts`);
  const back = unite(boxes(kit, role('backrest')));
  assert.ok(back.max.y > 1.0 && back.max.y < 1.4, 'a backrest short of the tower');
  const lean = machine().contacts.find((c) => c.point === 'back').at[0];
  assert.ok(lean.z < -0.22, 'the back pad\'s middle leans behind its foot');
});

test('no brand or maker appears in the recipe', async () => {
  const text = await readFile(path.join(HERE, 'recipes', 'hip-machine.mjs'), 'utf8');
  assert.doesNotMatch(text.replace(/refs\/hip_machine\/ref\.md/g, ''), /hammer|strength|hoist|life ?fitness|HS-HA|RS-140|logo|decal/i);
});
