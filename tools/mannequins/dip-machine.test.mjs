// The seated dip machine (#226), from refs/dip_machine/ref.md: two handle arms on a pivot at the top of
// a rear upright, and a carriage of seat, back pad and footplates that rocks back as they go down.
// None of its exercise is posed yet, so it stands where the ref puts it, with both joints at their start.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import * as THREE from 'three';
import { EQUIPMENT, KITS, MACHINES, buildKit, shownAlone } from './kit.mjs';
import { POSES } from './poses.mjs';
import { rad } from './rig.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));

const machine = (options = {}) => {
  const kit = buildKit({ equipment: 'machine', kit: { dip_machine: options } }).kits.dip_machine;
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
const named = (kit, role) => meshes(kit, (o) => o.userData.role === role);
const centre = (b) => b.getCenter(new THREE.Vector3());

test('the dip machine is the kit of the seated dip machine kind, and none of its exercise is posed', () => {
  assert.equal(MACHINES.dip_machine.kit, 'dip_machine');
  assert.deepEqual(MACHINES.dip_machine.exercises, ['seated_dip_machine']);
  assert.ok(KITS.dip_machine, 'a recipe named dip_machine');
  assert.ok(EQUIPMENT.machine.includes('dip_machine'));
  assert.equal(POSES.seated_dip_machine, undefined, 'seated_dip_machine is posed already');
});

test('the studio shows it alone, with its own framing', () => {
  const entry = shownAlone('dip_machine');
  assert.equal(entry.equipment, 'machine');
  assert.deepEqual(Object.keys(entry.kit), ['dip_machine']);
  assert.ok(entry.camera.halfHeight > 0);
});

test('it has two joints, the handle arms and the carriage, both about a transverse axis', () => {
  const kit = machine();
  assert.deepEqual(Object.keys(kit.joints).sort(), ['arms', 'carriage']);
  for (const j of Object.values(kit.joints)) {
    assert.equal(j.type, 'revolute');
    close(Math.abs(j.axis.clone().normalize().x), 1, 1e-9, 'the axis along x');
  }
});

test('the arms turn 40° and the carriage 20°, each from its start', () => {
  const { arms, carriage } = machine().joints;
  assert.deepEqual(arms.limits, [0, rad(40)]);
  assert.deepEqual(carriage.limits, [0, rad(20)]);
  assert.equal(arms.value, 0);
  assert.equal(carriage.value, 0);
});

test('its joints can be scrubbed through their limits and are held there', () => {
  const kit = machine();
  for (const body of ['arms', 'carriage']) {
    const [min, max] = kit.joints[body].limits;
    kit.scrub(body, max);
    assert.equal(kit.joints[body].value, max);
    kit.scrub(body, max + 1);
    assert.equal(kit.joints[body].value, max, 'held at its upper limit');
    kit.scrub(body, min - 1);
    assert.equal(kit.joints[body].value, min, 'held at its lower limit');
  }
});

test('no brand, logo or decal is named in the recipe', async () => {
  const src = await readFile(path.join(HERE, 'recipes', 'dip-machine.mjs'), 'utf8');
  for (const word of ['hoist', 'roc-it', 'rs-1101', 'atlantis', 'hammer', 'logo', 'decal']) {
    assert.ok(!src.toLowerCase().replace(/no (brand|logo|decal)[^\n]*/g, '').includes(word), `"${word}" in the recipe`);
  }
});

const grips = (kit) => contact(kit, 'handles').at;
const at = (kit, point) => contact(kit, point).at;
const midY = (ps) => ps.reduce((s, p) => s + p.y, 0) / ps.length;

test('the handles are a pair of points 500 mm apart on the arms, held grips, 940 mm up at the start', () => {
  const kit = machine();
  const c = contact(kit, 'handles');
  assert.equal(c.body, 'arms');
  assert.equal(c.landmark, 'hands');
  assert.equal(c.at.length, 2);
  close(Math.abs(c.at[0].x - c.at[1].x), 0.5, 1e-3, 'the grip spread');
  close(midY(c.at), 0.94, 0.02, 'the grips\' height at the start');
  const held = meshes(kit, (o) => o.userData.held);
  assert.ok(held.length >= 2, 'a held grip each side');
  for (const p of c.at) assert.ok(Math.min(...held.map(({ box }) => box.distanceToPoint(p))) < 0.015, 'a grip at the point');
});

test('scrubbing the arms to their limit drops the grips 470 mm, to beside the hips', () => {
  const kit = machine();
  kit.scrub('arms', kit.joints.arms.limits[1]);
  close(midY(grips(kit)), 0.47, 0.02, 'the grips\' height at the finish');
  close(kit.joints.arms.origin.y, 0.88, 0.01, 'the arms\' pivot height');
  close(Math.hypot(grips(kit)[0].y - 0.88, grips(kit)[0].z - kit.joints.arms.origin.z), 0.72, 0.01, 'the arm\'s length');
});

test('the carriage carries the seat, back pad and footplates: the footplates at 120 mm, 420 at the finish, and the seat top at 480, 580 at its front edge', () => {
  const kit = machine();
  for (const point of ['backPad', 'footplates']) assert.equal(contact(kit, point).body, 'carriage');
  close(midY(at(kit, 'footplates')), 0.12, 0.02, 'the footplates at the start');
  const [seat] = named(kit, 'seat');
  close(seat.box.max.y, 0.48, 0.02, 'the seat top');
  kit.scrub('carriage', kit.joints.carriage.limits[1]);
  kit.group.updateMatrixWorld(true);
  close(midY(at(kit, 'footplates')), 0.42, 0.03, 'the footplates at the finish');
  close(named(kit, 'seat')[0].box.max.y, 0.58, 0.04, 'the seat\'s front edge');
});

test('the back pad tilts back with the carriage', () => {
  const kit = machine();
  const before = centre(named(kit, 'backPad')[0].box);
  kit.scrub('carriage', kit.joints.carriage.limits[1]);
  kit.group.updateMatrixWorld(true);
  assert.ok(centre(named(kit, 'backPad')[0].box).z < before.z - 0.05, 'the pad\'s middle moved back');
});

test('it stands 1.52 m long, 1.26 m wide and 1.39 m tall, on the floor', () => {
  const box = meshes(machine()).reduce((u, { box }) => u.union(box), new THREE.Box3());
  const size = box.getSize(new THREE.Vector3());
  close(size.z, 1.52, 0.05, 'the length');
  close(size.x, 1.26, 0.05, 'the width');
  close(size.y, 1.39, 0.03, 'the height');
  close(box.min.y, 0, 1e-3, 'on the floor');
});

test('the stack is raised by the arms, and the arms and the carriage are independent', () => {
  const kit = machine();
  const plate = () => meshes(kit, (o) => o.userData.stack)[0].box.max.y;
  const rest = plate();
  kit.scrub('arms', kit.joints.arms.limits[1]);
  assert.ok(plate() > rest + 0.1, 'the head plate rose');
  const grip = midY(grips(kit));
  kit.scrub('carriage', kit.joints.carriage.limits[1]);
  close(midY(grips(kit)), grip, 1e-9, 'the carriage left the grips where they were');
});

test('the stack stands on the user\'s right (-x), and everything above the back pad, its pulley with it, is over the stack', () => {
  const kit = machine();
  const head = meshes(kit, (o) => o.userData.stack)[0].box;
  assert.ok(centre(head).x < -0.3, 'the stack on the right of a user facing +z');
  const high = meshes(kit).filter(({ box }) => box.min.y > 1.2);
  assert.ok(high.length >= 3, 'the cross bar and the pulley up there');
  for (const { box } of high) assert.ok(Math.abs(centre(box).x - centre(head).x) < 0.3, `a part at x ${centre(box).x.toFixed(2)} high above the machine, away from the stack`);
});
