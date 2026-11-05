// The hip thrust machine (#229), from refs/hip_thrust_machine/ref.md: a carriage on a transverse
// pivot at the top of the rear frame, its back pads, waist belt, handles and two plate horns riding
// on it, over a low seat with a separate angled foot platform in front.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import * as THREE from 'three';
import { EQUIPMENT, KITS, MACHINES, buildKit, shownAlone } from './kit.mjs';
import { buildStage } from './stage.mjs';
import { rad } from './rig.mjs';

const machine = (options = {}) => {
  const kit = buildKit({ equipment: 'machine', kit: { hip_thrust_machine: options } }).kits.hip_thrust_machine;
  // With no pose to drive it, the carriage rests on its stop, the bottom of the rep.
  kit.place({}, {}, {});
  kit.group.updateMatrixWorld(true);
  return kit;
};
const contact = (kit, point) => kit.contacts.find((c) => c.point === point);
const deg = (r) => (r * 180) / Math.PI;
const close = (a, b, within, why) => assert.ok(Math.abs(a - b) <= within, `${why}: ${a.toFixed(3)}, not ${b} ± ${within}`);
const roles = (kit, role) => {
  const out = [];
  kit.group.traverse((o) => { if (o.isMesh && o.userData.role === role) out.push(o); });
  return out;
};
const box = (o) => new THREE.Box3().setFromObject(o);

test('hip_thrust_machine is the kit of the machine kind, serving the machine hip thrust', () => {
  assert.ok(KITS.hip_thrust_machine, 'a recipe named hip_thrust_machine');
  assert.deepEqual(MACHINES.hip_thrust_machine, { kit: 'hip_thrust_machine', exercises: ['machine_hip_thrust'] });
  assert.ok(EQUIPMENT.machine.includes('hip_thrust_machine'));
});

test('the carriage turns on a transverse pivot about 880 mm up and 800 mm behind the belt, through about 17°', () => {
  const kit = machine();
  const { carriage } = kit.joints;
  assert.equal(carriage.type, 'revolute');
  close(Math.abs(carriage.axis.x), 1, 1e-9, 'the pivot\'s axis is across the machine');
  close(carriage.origin.y, 0.88, 0.01, 'the pivot\'s height');
  const belt = contact(kit, 'belt').at[0];
  close(belt.z - carriage.origin.z, 0.8, 0.02, 'how far behind the belt the pivot is');
  close(deg(carriage.limits[1] - carriage.limits[0]), 17, 1, 'the range, in degrees');
  close(carriage.value, carriage.limits[0], 1e-9, 'unposed, on its stop at the bottom');
});

test('the belt holds the hips 700 mm up at the bottom of the rep and 930 mm at the top, the pads and handles with it', () => {
  const kit = machine();
  close(contact(kit, 'belt').at[0].y, 0.7, 0.01, 'the belt\'s height at the bottom');
  const [pad] = roles(kit, 'backPad');
  const padAt = box(pad).max.y;
  kit.scrub('carriage', 99);
  close(deg(kit.joints.carriage.value), deg(kit.joints.carriage.limits[1]), 1e-9, 'held at its upper limit');
  kit.group.updateMatrixWorld(true);
  close(contact(kit, 'belt').at[0].y, 0.93, 0.015, 'the belt\'s height at the top');
  assert.ok(box(pad).max.y > padAt + 0.15, 'the pad rose with the carriage');
  kit.scrub('carriage', -99);
  close(kit.joints.carriage.value, kit.joints.carriage.limits[0], 1e-9, 'held at its lower limit');
});

test('it stands 1730 mm long, 1490 wide and 1020 high', () => {
  const kit = machine();
  const size = box(kit.group).getSize(new THREE.Vector3());
  close(size.x, 1.49, 0.03, 'width');
  close(size.y, 1.02, 0.03, 'height');
  close(size.z, 1.73, 0.04, 'length');
});

test('the seat top is 470 mm up, and the foot platform 450 mm up and 400 deep on a 15° tilt, 220 mm ahead of the seat', () => {
  const kit = machine();
  const [seat] = roles(kit, 'seat');
  close(box(seat).max.y, 0.47, 0.01, 'the seat top');
  const [platform] = roles(kit, 'platform');
  platform.geometry.computeBoundingBox();
  close(platform.geometry.boundingBox.getSize(new THREE.Vector3()).z, 0.4, 1e-6, 'the platform\'s depth');
  const normal = new THREE.Vector3(0, 1, 0).applyQuaternion(platform.getWorldQuaternion(new THREE.Quaternion()));
  close(deg(Math.acos(normal.y)), 15, 0.01, 'the platform\'s tilt');
  // Toes up, the sole facing back toward the lifter.
  assert.ok(normal.z < 0, 'the platform\'s face turned back toward the seat');
  const feet = contact(kit, 'platform');
  assert.equal(feet.landmark, 'feet');
  assert.equal(feet.body, 'frame');
  close(feet.at[0].y, 0.45, 0.01, 'the platform\'s top, mid-depth');
  const back = platform.getWorldPosition(new THREE.Vector3()).z - 0.2 * Math.cos(rad(15));
  close(back - box(seat).max.z, 0.22, 0.02, 'the gap from the seat\'s front to the platform\'s back edge');
});

test('two half-round back pads 260 mm across leave a gap, their top 830 mm up at the bottom of the rep', () => {
  const kit = machine();
  const pads = roles(kit, 'backPad');
  assert.equal(pads.length, 2);
  const [a, b] = pads.map(box).sort((p, q) => p.min.x - q.min.x);
  close(a.max.y - a.min.y, 0.26, 0.01, 'a pad\'s diameter');
  close(Math.max(a.max.y, b.max.y), 0.83, 0.01, 'the pads\' top');
  assert.ok(b.min.x - a.max.x > 0.02, 'a gap between the pads for the spine');
  const [back] = [contact(kit, 'pad')];
  assert.equal(back.body, 'carriage');
  assert.equal(back.landmark, 'back');
});

test('the belt and the handles ride on the carriage, the hands holding two grips beside the hips', () => {
  const kit = machine();
  const belt = contact(kit, 'belt');
  assert.equal(belt.body, 'carriage');
  assert.equal(belt.landmark, 'hips');
  const handles = contact(kit, 'handles');
  assert.equal(handles.body, 'carriage');
  assert.equal(handles.landmark, 'hands');
  const [l, r] = handles.at;
  close(Math.abs(l.x), Math.abs(r.x), 1e-9, 'the grips are mirrored');
  assert.ok(Math.abs(l.x - r.x) > 0.5 && Math.abs(l.x - r.x) < 0.8, `the grips ${Math.abs(l.x - r.x).toFixed(2)} m apart`);
  const held = [];
  kit.group.traverse((o) => { if (o.isMesh && o.userData.held) held.push(o); });
  for (const p of handles.at) assert.ok(held.some((m) => box(m).distanceToPoint(p) < 1e-6), 'no held part at a grip');
  // Beside the hips, a little above the belt.
  for (const p of handles.at) assert.ok(p.y > belt.at[0].y && p.y < belt.at[0].y + 0.4, `a grip at ${p.y.toFixed(2)} m`);
});

test('four plate horns: two on the carriage, loaded with 450 mm plates, two on the fixed rear frame', () => {
  const kit = machine();
  const horns = roles(kit, 'horn');
  assert.equal(horns.length, 4);
  const plates = [];
  kit.group.traverse((o) => { if (o.isMesh && o.material?.name === 'plate') plates.push(o); });
  assert.equal(plates.length, 2);
  for (const p of plates) close(box(p).getSize(new THREE.Vector3()).y, 0.45, 0.005, 'a plate\'s diameter');
  const at = () => horns.map((h) => box(h).getCenter(new THREE.Vector3()));
  const before = at();
  kit.scrub('carriage', kit.joints.carriage.limits[1]);
  kit.group.updateMatrixWorld(true);
  const moved = at().map((p, i) => p.distanceTo(before[i]) > 0.05);
  assert.equal(moved.filter(Boolean).length, 2, 'two horns ride the carriage, two stay on the frame');
  const plateTop = Math.max(...plates.map((p) => box(p).max.y));
  assert.ok(plateTop > 0.6, 'the plates are off the floor');
});

test('its strap option is a fit rule: the long loop lifts the belt off higher', () => {
  assert.deepEqual(KITS.hip_thrust_machine.fit.map((f) => f.sets), ['strap']);
  const short = contact(machine({ strap: 'short' }), 'belt').at[0];
  const long = contact(machine({ strap: 'long' }), 'belt').at[0];
  assert.ok(long.y > short.y + 0.02, `the long loop at ${long.y.toFixed(3)}, the short at ${short.y.toFixed(3)}`);
});

test('the studio shows it on its own, the carriage scrubbed through its limits', async () => {
  const entry = shownAlone('hip_thrust_machine');
  assert.deepEqual(entry.kit, { hip_thrust_machine: {} });
  assert.equal(entry.alone, true);
  assert.equal(entry.equipment, 'machine');
  const stage = await buildStage(entry);
  stage.pose(0);
  const kit = stage.kit.kits.hip_thrust_machine;
  const [min, max] = kit.joints.carriage.limits;
  close(kit.joints.carriage.value, min, 1e-9, 'at rest on its stop');
  kit.scrub('carriage', max);
  close(kit.joints.carriage.value, max, 1e-9, 'scrubbed to its upper limit');
});

test('the recipe names no maker, model or finish', async () => {
  const source = await readFile(new URL('./recipes/hip-thrust-machine.mjs', import.meta.url), 'utf8');
  assert.doesNotMatch(source, /hammer|life ?fitness|glute ?drive|PL-GLD|line-x|strength|logo|decal/i);
});
