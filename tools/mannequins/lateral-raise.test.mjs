// The lateral raise machine (#212), from refs/lateral_raise_machine/ref.md: two arm levers turning
// about the shoulders' axes, a seat that moves to the pivots, a chest pad and one weight stack.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import * as THREE from 'three';
import { KITS, MACHINES, buildKit, shownAlone } from './kit.mjs';
import { MODEL, parseMannequin } from './stage.mjs';
import { buildRig, rad } from './rig.mjs';

const machine = () => {
  const kit = buildKit({ equipment: 'machine', kit: { lateral_raise_machine: {} } }).kits.lateral_raise_machine;
  // With no pose to drive it, the arms rest.
  kit.place({}, {}, {});
  kit.group.updateMatrixWorld(true);
  return kit;
};
const contact = (kit, point) => kit.contacts.find((c) => c.point === point);
const deg = (r) => (r * 180) / Math.PI;
const close = (a, b, within, why) => assert.ok(Math.abs(a - b) <= within, `${why}: ${a.toFixed(3)}, not ${b} ± ${within}`);
const SIDES = [['l', 1], ['r', -1]];

test('each arm turns about a front-to-back axis 1.10 m up, from 10° out of hanging to horizontal', () => {
  const kit = machine();
  for (const [k, s] of SIDES) {
    const joint = kit.joints[`arm_${k}`];
    assert.equal(joint.type, 'revolute');
    close(deg(joint.limits[0]), 10, 1e-9, `${k}'s lower limit, in degrees`);
    close(deg(joint.limits[1]), 90, 1e-9, `${k}'s upper limit, in degrees`);
    close(joint.value, joint.limits[0], 1e-9, `${k} unposed, at rest`);
    close(joint.origin.y, 1.1, 1e-9, `${k}'s pivot height`);
    // On the mannequin's shoulders (0.192 m out), where the ref lets the recipe set the spacing.
    close(joint.origin.x, s * 0.192, 0.005, `${k}'s pivot, out from the middle`);
    close(Math.abs(joint.axis.z), 1, 1e-9, `${k}'s axis, front to back`);
  }
});

test('the pads hang about 350 mm below the pivots at rest and ride up to horizontal, out at the sides', () => {
  const kit = machine();
  for (const [k, s] of SIDES) {
    const { origin } = kit.joints[`arm_${k}`];
    const [rest] = contact(kit, `pad_${k}`).at;
    // The pad's middle on the arm: 350 mm from the pivot, a hand's breadth out of the arm's line.
    close(Math.hypot(rest.x - origin.x, rest.y - origin.y), 0.364, 0.02, `${k}'s pad from the pivot, in the levers' plane`);
    assert.ok(rest.y < origin.y - 0.3, `${k}'s pad at y ${rest.y.toFixed(2)}, not below the pivot at rest`);
    kit.scrub(`arm_${k}`, rad(90));
    const [top] = contact(kit, `pad_${k}`).at;
    close(top.y, origin.y + 0.1, 0.03, `${k}'s pad at the top, on top of the arm`);
    assert.ok(s * (top.x - origin.x) > 0.3, `${k}'s pad ${(s * (top.x - origin.x)).toFixed(2)} m out at the top`);
    kit.scrub(`arm_${k}`, rad(120));
    close(deg(kit.joints[`arm_${k}`].value), 90, 1e-9, `${k}, held at its upper limit`);
    kit.scrub(`arm_${k}`, rad(-5));
    close(deg(kit.joints[`arm_${k}`].value), 10, 1e-9, `${k}, held at its lower limit`);
  }
});

test('one stack: the arms lift it, and it hangs on one arm', () => {
  const kit = machine();
  const heads = [];
  kit.group.traverse((o) => { if (o.userData.stack) heads.push(o); });
  assert.equal(heads.length, 1);
  const at = () => heads[0].getWorldPosition(new THREE.Vector3()).y;
  const low = at();
  kit.scrub('arm_l', rad(90));
  assert.ok(at() > low + 0.15, `the stack rose ${((at() - low) * 100).toFixed(0)} cm`);
});

test('the hands hold a grip at each pad\'s front end, the upper arms lie on the pads, the chest on its pad', () => {
  const kit = machine();
  const held = [];
  kit.group.traverse((o) => { if (o.isMesh && o.userData.held) held.push(o); });
  for (const [k, s] of SIDES) {
    const grip = contact(kit, `grip_${k}`);
    assert.equal(grip.landmark, 'hands');
    assert.equal(grip.body, `arm_${k}`);
    const [p] = grip.at;
    assert.ok(held.some((m) => new THREE.Box3().setFromObject(m).distanceToPoint(p) < 1e-6), `no held part at ${k}'s grip`);
    const [pad] = contact(kit, `pad_${k}`).at;
    assert.ok(p.z > pad.z + 0.1, `${k}'s grip at z ${p.z.toFixed(2)}, not ahead of its pad at ${pad.z.toFixed(2)}`);
    assert.equal(contact(kit, `pad_${k}`).landmark, 'upperArms');
    assert.equal(contact(kit, `pad_${k}`).body, `arm_${k}`);
    assert.ok(s * pad.x > 0);
  }
  assert.deepEqual([contact(kit, 'chestPad').body, contact(kit, 'chestPad').landmark], ['frame', 'chest']);
});

test('its fit rule sets the seat so the shoulder meets the fixed pivot, in the seat\'s range', async () => {
  assert.deepEqual(KITS.lateral_raise_machine.fit.map((f) => f.sets), ['seat']);
  const bytes = await readFile(MODEL);
  const { dims } = buildRig(await parseMannequin(bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength)));
  const seat = KITS.lateral_raise_machine.fit[0].solve({ L: dims });
  // The ref measures the seat at 0.48 m and has the mannequin's setting at about 0.47.
  close(seat, 0.48, 0.05, 'the seat top, in metres');
  assert.ok(seat >= 0.4 && seat <= 0.56, `the seat at ${seat}, outside its 0.40 to 0.56 range`);
});

test('the kit is the machine of both lateral raises, shown alone, with no brand in the recipe', async () => {
  assert.deepEqual(MACHINES.lateral_raise_machine, { kit: 'lateral_raise_machine', exercises: ['machine_lateral_raise', 'standing_machine_lateral_raise'] });
  const alone = shownAlone('lateral_raise_machine');
  assert.deepEqual(alone.kit, { lateral_raise_machine: {} });
  assert.equal(alone.alone, true);
  assert.equal(alone.equipment, 'machine');
  const source = await readFile(new URL('./recipes/lateral-raise-machine.mjs', import.meta.url), 'utf8');
  assert.doesNotMatch(source, /hammer|life ?fitness|atlantis|strength/i);
});
