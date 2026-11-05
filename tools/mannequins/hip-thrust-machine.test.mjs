// The hip thrust machine (#229), from refs/hip_thrust_machine/ref.md and the measurements read off
// its photos (build/hip-thrust-refs/notes.md): a carriage on a transverse pivot at the top of the
// rear frame, its back pads, waist belt, handles and two plate horns riding on it, over a low seat
// with a separate angled foot platform in front. The expected values are the ref's and the photos',
// not the recipe's constants.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import * as THREE from 'three';
import { EQUIPMENT, KITS, MACHINES, buildKit, shownAlone } from './kit.mjs';
import { MODEL, parseMannequin } from './stage.mjs';
import { buildRig, rad, wpos } from './rig.mjs';

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
  assert.equal(shownAlone('hip_thrust_machine'), null, 'a posed kit has nothing to stand alone by');
});

test('the carriage turns on a transverse pivot about 880 mm up and 820 mm behind the hips, through 17° to 23°', () => {
  const kit = machine();
  const { carriage } = kit.joints;
  assert.equal(carriage.type, 'revolute');
  close(Math.abs(carriage.axis.x), 1, 1e-9, 'the pivot\'s axis is across the machine');
  close(carriage.origin.y, 0.88, 0.01, 'the pivot\'s height');
  close(-carriage.origin.z, 0.82, 0.05, 'how far behind the hips the pivot is');
  // The ref: about 17° on the photographed machine, 23° on the video's.
  const range = deg(carriage.limits[1] - carriage.limits[0]);
  assert.ok(range >= 17 && range <= 24, `the range ${range.toFixed(1)}°, not 17° to 23°`);
  close(carriage.value, carriage.limits[0], 1e-9, 'unposed, on its stop at the bottom');
});

test('the pads rise with the carriage, and the carriage holds at its limits', () => {
  const kit = machine();
  const [pad] = roles(kit, 'backPad');
  const padAt = box(pad).max.y;
  kit.scrub('carriage', 99);
  close(deg(kit.joints.carriage.value), deg(kit.joints.carriage.limits[1]), 1e-9, 'held at its upper limit');
  kit.group.updateMatrixWorld(true);
  assert.ok(box(pad).max.y > padAt + 0.1, 'the pad rose with the carriage');
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

// Read from photo 5, scaled by its 450 mm plate: the seat's front edge about 140 mm ahead of the hips,
// the pad 440 behind, the plate's centre and the handles about 200 behind, the rear arch 880 behind.
test('the parts stand where the photos put them, from the hip joint', () => {
  const kit = machine();
  // The kit's frame has the hip joint at z = 0.
  const hip = 0;
  const [seat] = roles(kit, 'seat');
  close(box(seat).max.z - hip, 0.14, 0.05, 'the seat\'s front edge, ahead of the hips');
  const [pad] = roles(kit, 'backPad');
  close(hip - box(pad).getCenter(new THREE.Vector3()).z, 0.44, 0.06, 'the pad, behind the hips');
  const plates = [];
  kit.group.traverse((o) => { if (o.isMesh && o.material?.name === 'plate') plates.push(o); });
  for (const p of plates) {
    const c = box(p).getCenter(new THREE.Vector3());
    close(hip - c.z, 0.2, 0.08, 'a plate\'s centre, behind the hips');
    close(c.y, 0.5, 0.08, 'a plate\'s centre height');
  }
  for (const p of contact(kit, 'handles').at) close(hip - p.z, 0.2, 0.06, 'a handle, behind the hips');
  let rear = Infinity;
  kit.group.traverse((o) => { if (o.isMesh) rear = Math.min(rear, box(o).min.z); });
  close(hip - rear, 0.97, 0.1, 'the rear frame\'s back, behind the hips');
});

test('the seat top is 500 mm up, and the foot platform 450 mm up and 400 deep on a 15° tilt, 220 mm ahead of the seat', () => {
  const kit = machine();
  const [seat] = roles(kit, 'seat');
  close(box(seat).max.y, 0.5, 0.04, 'the seat top');
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

test('two round back pads 260 mm across leave a gap, their top 830 mm up at the bottom of the rep', () => {
  const kit = machine();
  const pads = roles(kit, 'backPad');
  assert.equal(pads.length, 2);
  const [a, b] = pads.map(box).sort((p, q) => p.min.x - q.min.x);
  close(a.max.y - a.min.y, 0.26, 0.01, 'a pad\'s diameter');
  close(Math.max(a.max.y, b.max.y), 0.83, 0.01, 'the pads\' top');
  assert.ok(b.min.x - a.max.x > 0.02, 'a gap between the pads for the spine');
  const back = contact(kit, 'pad');
  assert.equal(back.body, 'carriage');
  assert.equal(back.landmark, 'back');
});

test('the belt and the handles ride on the carriage, the hands holding two upright grips beside the hips', () => {
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
  // The handles' tops reach 1020 mm, upright.
  for (const m of held) close(box(m).max.y, 1.02, 0.01, 'a handle\'s top');
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
});

test('its strap option is a fit rule: the long loop lifts the belt off higher', () => {
  assert.deepEqual(KITS.hip_thrust_machine.fit.map((f) => f.sets), ['strap']);
  const short = contact(machine({ strap: 'short' }), 'belt').at[0];
  const long = contact(machine({ strap: 'long' }), 'belt').at[0];
  assert.ok(long.y > short.y + 0.02, `the long loop at ${long.y.toFixed(3)}, the short at ${short.y.toFixed(3)}`);
});

test('the recipe names no maker, model or finish', async () => {
  const source = await readFile(new URL('./recipes/hip-thrust-machine.mjs', import.meta.url), 'utf8');
  assert.doesNotMatch(source, /hammer|life ?fitness|glute ?drive|PL-GLD|line-x|strength|logo|decal/i);
});

// The ref: "at the top the shoulder blades are on the back pad's top edge", the hips at the belt. So
// the pad is a trunk's length behind the hips, not hard against them.
test('the back pad is a trunk\'s length behind the hips, where the mannequin\'s upper back lies', async () => {
  const bytes = await readFile(MODEL);
  const rig = buildRig(await parseMannequin(bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength)));
  const [hips, mid, neck] = ['pelvis', 'spine_02', 'neck_01'].map((n) => wpos(rig.bones[n]));
  const kit = machine();
  const back = contact(kit, 'pad').at[0];
  const behind = -back.z;
  assert.ok(behind > hips.distanceTo(mid) * 0.8 && behind < hips.distanceTo(neck), `the pad ${behind.toFixed(2)} m behind the hips, the trunk ${hips.distanceTo(mid).toFixed(2)} to ${hips.distanceTo(neck).toFixed(2)} m`);
});
