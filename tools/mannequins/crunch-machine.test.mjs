// The crunch machine (#233), from refs/crunch_machine/ref.md: a reclined crunch on an inclined seat
// pad, the back pad on an arm turning about a cam axis beside the waist, the ankles hooked between
// rollers, a stack at the feet end. Built as a kit alone; no pose uses it.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import * as THREE from 'three';
import { readFile } from 'node:fs/promises';
import { KITS, MACHINES, EQUIPMENT, buildKit, shownAlone } from './kit.mjs';
import { buildStage } from './stage.mjs';
import { POSES } from './poses.mjs';
import { rad } from './rig.mjs';

const machine = () => {
  const kit = buildKit({ equipment: 'machine', kit: { crunch_machine: {} } }).kits.crunch_machine;
  kit.place({}, {}, {});
  kit.group.updateMatrixWorld(true);
  return kit;
};
const deg = (r) => (r * 180) / Math.PI;
const close = (a, b, within, why) => assert.ok(Math.abs(a - b) <= within, `${why}: ${a.toFixed(3)}, not ${b} ± ${within}`);
const contact = (kit, point) => kit.contacts.find((c) => c.point === point);

test('the arm turns about the cam axis 780 mm up, 40° from its rest with the back pad level', () => {
  const kit = machine();
  const { arm } = kit.joints;
  assert.equal(arm.type, 'revolute');
  close(deg(arm.limits[0]), 0, 1e-9, 'its lower limit, in degrees');
  close(deg(arm.limits[1]), 40, 1e-9, 'its upper limit, in degrees');
  close(arm.value, 0, 1e-9, 'unposed, at rest');
  close(arm.origin.y, 0.78, 0.03, 'the cam axis\'s height');
});

const byRole = (kit, role) => {
  const out = [];
  kit.group.traverse((o) => { if (o.isMesh && o.userData.role === role) out.push(o); });
  return out;
};
const only = (kit, role) => {
  const found = byRole(kit, role);
  assert.equal(found.length, 1, `${found.length} parts of role ${role}`);
  return found[0];
};
const boxOf = (kit) => new THREE.Box3().setFromObject(kit.group);
const centre = (m) => new THREE.Box3().setFromObject(m).getCenter(new THREE.Vector3());
/** A part's up (its local y) in the world, and how far it leans from vertical, in degrees, toward -z (+) or +z (-). */
const leanOf = (m) => {
  const up = new THREE.Vector3(0, 1, 0).applyQuaternion(m.getWorldQuaternion(new THREE.Quaternion()));
  return deg(Math.atan2(-up.z, up.y));
};

test('it stands 1.58 m by 890 mm and 1.4 m tall, the ref\'s footprint and height', () => {
  const size = boxOf(machine()).getSize(new THREE.Vector3());
  close(size.z, 1.58, 0.12, 'its length');
  close(size.x, 0.89, 0.06, 'its width');
  close(size.y, 1.4, 0.04, 'its height');
});

test('the seat pad is 600 mm long, inclined 15° rising toward the back, its high end 530 mm up and its low end 380', () => {
  const kit = machine();
  const seat = only(kit, 'seat');
  close(leanOf(seat), 15, 0.5, 'its incline, in degrees, rising toward +z');
  // Its top face, its two ends: 300 mm either side of the middle along the incline, `along` toward the high end.
  const up = new THREE.Vector3(0, 1, 0).applyQuaternion(seat.getWorldQuaternion(new THREE.Quaternion()));
  const along = new THREE.Vector3(0, -up.z, up.y).normalize().multiplyScalar(0.3);
  const top = centre(seat).addScaledVector(up, 0.04);
  close(top.clone().add(along).y, 0.53, 0.02, 'the high end\'s top');
  close(top.clone().sub(along).y, 0.38, 0.02, 'the low end\'s top');
});

test('the back pad lies level at rest 660 mm up, its head pad 600 mm from the axis, and tips 40° with the arm', () => {
  const kit = machine();
  const back = only(kit, 'backPad');
  const head = only(kit, 'headPad');
  const { origin } = kit.joints.arm;
  close(leanOf(back), 0, 0.5, 'the back pad\'s lean at rest');
  close(new THREE.Box3().setFromObject(back).max.y, 0.66, 0.02, 'its top');
  const rest = centre(head);
  close(rest.distanceTo(origin), 0.6, 0.05, 'the head pad from the axis');
  assert.ok(rest.z > origin.z + 0.4, 'the head pad ahead of the axis');
  kit.scrub('arm', rad(40));
  kit.group.updateMatrixWorld(true);
  close(leanOf(back), 40, 0.5, 'tipped toward the knees, in degrees');
  close(centre(head).distanceTo(rest), 0.41, 0.03, 'how far the head pad moved');
  assert.ok(centre(head).y > rest.y + 0.25, 'the head pad rose');
  kit.scrub('arm', rad(80));
  close(deg(kit.joints.arm.value), 40, 1e-9, 'held at its upper limit');
});

test('two vertical handles 200 mm long stand 400 mm apart either side of the head pad, held by the hands', () => {
  const kit = machine();
  const handles = byRole(kit, 'handle');
  assert.equal(handles.length, 2);
  for (const h of handles) {
    assert.ok(h.userData.held, 'held');
    const size = new THREE.Box3().setFromObject(h).getSize(new THREE.Vector3());
    close(size.y, 0.2, 0.02, 'a handle\'s length');
  }
  close(Math.abs(centre(handles[0]).x - centre(handles[1]).x), 0.4, 0.02, 'their spacing');
  const c = contact(kit, 'handles');
  assert.equal(c.landmark, 'hands');
  assert.equal(c.body, 'arm');
  for (const [i, p] of c.at.entries()) close(p.distanceTo(centre(handles[i])), 0, 0.01, `hand ${i} on its handle`);
});

test('two pairs of foot rollers on the front frame, at 360 and 600 mm, the ankles between them', () => {
  const kit = machine();
  const rollers = byRole(kit, 'roller');
  assert.equal(rollers.length, 4);
  const heights = [...new Set(rollers.map((r) => Math.round(centre(r).y * 100) / 100))].sort();
  assert.deepEqual(heights, [0.36, 0.6]);
  const feet = contact(kit, 'rollers');
  assert.equal(feet.landmark, 'feet');
  assert.equal(feet.body, 'frame');
  close(feet.at[0].y, 0.48, 0.02, 'the ankles, between the pairs');
});

test('the hips lie on the seat\'s high end and the upper back on the back pad', () => {
  const kit = machine();
  const seat = contact(kit, 'seat');
  assert.equal(seat.landmark, 'thighs');
  assert.equal(seat.body, 'frame');
  close(seat.at[0].y, 0.53, 0.03, 'the seat\'s high end, top');
  const back = contact(kit, 'backPad');
  assert.equal(back.landmark, 'shoulders');
  assert.equal(back.body, 'arm');
  close(back.at[0].distanceTo(centre(only(kit, 'backPad'))), 0, 0.05, 'on the back pad');
});

test('the ref has no adjustment, so it declares no fit rule', () => {
  assert.deepEqual(KITS.crunch_machine.fit ?? [], []);
});

test('the machine serves machine_crunch, on no pose, so no committed clip changes', () => {
  assert.deepEqual(MACHINES.crunch_machine, { kit: 'crunch_machine', exercises: ['machine_crunch'] });
  assert.ok(EQUIPMENT.machine.includes('crunch_machine'));
  assert.equal(POSES.machine_crunch, undefined, 'no pose uses it yet');
});

test('its cable follows the cam and its stack rises with the arm', () => {
  const kit = machine();
  const cable = byRole(kit, 'cable');
  assert.equal(cable.length, 1);
  const plates = () => { const y = []; kit.group.traverse((o) => { if (o.isMesh && o.material?.name === 'plate') y.push(o.position.y); }); return y; };
  const end = () => new THREE.Box3().setFromObject(only(kit, 'cable')).min.y;
  const [stack, top] = [plates(), end()];
  kit.scrub('arm', rad(40));
  kit.group.updateMatrixWorld(true);
  const risen = plates().map((y, i) => y - stack[i]);
  assert.ok(risen.some((d) => d > 0.1), `the stack rose ${Math.max(...risen).toFixed(3)} m`);
  assert.ok(Math.abs(end() - top) > 1e-3, 'the cable end moved with the cam');
});

test('no brand name appears in the recipe', async () => {
  const source = await readFile(new URL('./recipes/crunch-machine.mjs', import.meta.url), 'utf8');
  assert.doesNotMatch(source, /hammer|strength|life ?fitness|\blogo\b|decal/i);
});

test('the studio shows it alone, its arm scrubbed through its limits', async () => {
  const entry = shownAlone('crunch_machine');
  assert.equal(entry.equipment, 'machine');
  assert.ok(entry.camera, 'a framing to open on');
  const stage = await buildStage(entry);
  const kit = stage.kit.kits.crunch_machine;
  stage.pose(0);
  for (const limit of kit.joints.arm.limits) {
    kit.scrub('arm', limit);
    stage.scene.updateMatrixWorld(true);
    close(kit.joints.arm.value, limit, 1e-9, 'scrubbed to its limit');
  }
});
