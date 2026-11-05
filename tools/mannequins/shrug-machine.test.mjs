// The shrug machine (#223), from refs/shrug_machine/ref.md: two levers, one a side, each turning
// about a transverse axis at the top of the rear upright behind the back pad, a handle post rising
// from each with the grip pointing forward and a plate horn under it, and a seat at 650 mm.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import * as THREE from 'three';
import { EQUIPMENT, KITS, MACHINES, buildKit, shownAlone } from './kit.mjs';

const machine = (options = {}) => {
  const kit = buildKit({ equipment: 'machine', kit: { shrug_machine: options } }).kits.shrug_machine;
  // With no pose to drive it, the levers rest on their stop.
  kit.place({}, {}, {});
  kit.group.updateMatrixWorld(true);
  return kit;
};
const close = (a, b, within, why) => assert.ok(Math.abs(a - b) <= within, `${why}: ${a.toFixed(3)}, not ${b} ± ${within}`);
const grips = (kit) => kit.contacts.find((c) => c.point === 'grips').at;
const box = (kit) => new THREE.Box3().setFromObject(kit.group);

test('the shrug machine is the kit of the shrug machine kind, and serves machine_shrug', () => {
  assert.ok(KITS.shrug_machine, 'a recipe named shrug_machine');
  assert.equal(MACHINES.shrug_machine.kit, 'shrug_machine');
  assert.deepEqual(MACHINES.shrug_machine.exercises, ['machine_shrug']);
  assert.ok(EQUIPMENT.machine.includes('shrug_machine'));
});

test('the studio shows it alone while no pose uses it', () => {
  const entry = shownAlone('shrug_machine');
  assert.equal(entry.alone, true);
  assert.deepEqual(entry.kit, { shrug_machine: {} });
});

test('both levers are one revolute joint about a transverse axis, 7.5° to 26.3° up from level ahead', () => {
  const { levers } = machine().joints;
  assert.equal(levers.type, 'revolute');
  assert.equal(Math.abs(levers.axis.x), 1);
  assert.equal(levers.axis.y, 0);
  assert.equal(levers.axis.z, 0);
  // The ref: the grip 700 above the floor on its stop and 940 at the top of a shrug, on a pivot 600 up.
  close(THREE.MathUtils.radToDeg(levers.limits[0]), 7.5, 0.1, 'the stop, degrees above level');
  close(THREE.MathUtils.radToDeg(levers.limits[1]), 26.3, 0.1, 'the top, degrees above level');
  close(levers.origin.y, 0.6, 1e-9, 'the pivot above the floor');
});

test('the grips rest at 700 mm, 740 apart, 760 ahead of the pivot, and scrub to 940', () => {
  const kit = machine();
  const [left, right] = grips(kit);
  close(left.y, 0.7, 0.005, 'the left grip at rest');
  close(Math.abs(left.x - right.x), 0.74, 1e-6, 'the grips apart');
  close(left.z - kit.joints.levers.origin.z, 0.76, 0.005, 'the grips ahead of the pivot');
  kit.scrub('levers', 99);
  close(grips(kit)[0].y, 0.94, 0.005, 'the grip at the top of the lever');
  kit.scrub('levers', -99);
  close(grips(kit)[0].y, 0.7, 0.005, 'back on its stop');
});

test('scrubbing moves the levers, and the frame stays', () => {
  const kit = machine();
  const centre = (node) => new THREE.Box3().setFromObject(node).getCenter(new THREE.Vector3());
  const lever = kit.group.getObjectByName(kit.bodies.levers[1].name);
  const frame = kit.group.getObjectByName(kit.group.children.find((o) => !kit.bodies.levers.some((b) => b.name === o.name)).name);
  const [lever0, frame0] = [centre(lever), centre(frame)];
  kit.scrub('levers', 0.4);
  kit.group.updateMatrixWorld(true);
  assert.ok(centre(lever).distanceTo(lever0) > 0.01, 'a lever part did not move');
  assert.ok(centre(frame).distanceTo(frame0) < 1e-9, 'the frame moved with the levers');
});

test('it stands 1.02 m deep, 1.24 m tall and about 1.52 m across, a plate horn on each lever', () => {
  const size = box(machine()).getSize(new THREE.Vector3());
  close(size.z, 1.02, 0.08, 'front to back');
  close(size.y, 1.24, 0.1, 'tall');
  close(size.x, 1.52, 0.1, 'across');
  const horns = [];
  machine().group.traverse((o) => { if (o.isMesh && o.userData.role === 'horn') horns.push(o); });
  assert.equal(horns.length, 2);
});

test('the seat top is 650 mm, and the recipe carries no brand', async () => {
  const kit = machine();
  let seat;
  kit.group.traverse((o) => { if (o.isMesh && o.userData.role === 'seat') seat = new THREE.Box3().setFromObject(o).max.y; });
  close(seat, 0.65, 0.01, 'the seat top');
  const source = await readFile(new URL('./recipes/shrug-machine.mjs', import.meta.url), 'utf8');
  assert.doesNotMatch(source, /hammer|life ?fitness|strength|pl-sh/i);
});

test('a fit rule sets the seat, at its measured 650 mm by default', () => {
  const rules = KITS.shrug_machine.fit;
  assert.deepEqual(rules.map((r) => r.sets), ['seat']);
  const top = (kit) => { let y; kit.group.traverse((o) => { if (o.isMesh && o.userData.role === 'seat') y = new THREE.Box3().setFromObject(o).max.y; }); return y; };
  close(top(machine({ seat: 0.7 })), 0.7, 0.01, 'the seat set to 700 mm');
});

const roled = (kit, name) => { const found = []; kit.group.traverse((o) => { if (o.isMesh && o.userData.role === name) found.push(o); }); return found; };

test('the backrest beam and its column are cut to meet on a mitre, flush at the back and the top', () => {
  const kit = machine();
  const [column] = roled(kit, 'backColumn');
  const [beam] = roled(kit, 'backBeam');
  assert.ok(column && beam, 'a backColumn and a backBeam');
  const [c, b] = [column, beam].map((o) => new THREE.Box3().setFromObject(o));
  close(c.max.y, b.max.y, 0.001, 'the column\'s top against the beam\'s top');
  close(c.min.z, b.min.z, 0.001, 'the column\'s back against the beam\'s back');
  // The beam runs from the column to the pad's back, and the column stands on the floor beam.
  assert.ok(b.max.z > c.max.z + 0.1, 'the beam reaches forward of the column');
});

test('each handle turns from its round post into the grip through a round elbow, with no tube end showing', () => {
  const kit = machine();
  const elbows = roled(kit, 'elbow');
  assert.equal(elbows.length, 2, 'an elbow a side');
  const ends = grips(kit);
  for (const [i, elbow] of elbows.entries()) {
    const at = new THREE.Box3().setFromObject(elbow).getCenter(new THREE.Vector3());
    const side = at.x < 0 ? 0 : 1;
    // On the grip's line, behind its middle by half its length.
    close(at.x, ends[side].x, 1e-6, `elbow ${i} across`);
    close(Math.hypot(at.y - ends[side].y, at.z - ends[side].z), 0.07, 0.003, `elbow ${i} behind the grip's middle`);
    // Wider than the grip and the post, so both end inside it.
    assert.ok(new THREE.Box3().setFromObject(elbow).getSize(new THREE.Vector3()).x > 0.04, 'the elbow is wider than the grip');
  }
});
