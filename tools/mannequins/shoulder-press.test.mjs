// The shoulder press machine (#225), from refs/shoulder_press/ref.md: a yoke of two side arms joined
// behind the back pad, pivoting about one transverse axis at grip height, loaded by one weight stack.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import * as THREE from 'three';
import { EQUIPMENT, KITS, MACHINES, buildKit, shownAlone } from './kit.mjs';

const machine = (options = {}) => {
  const kit = buildKit({ equipment: 'machine', kit: { shoulder_press: options } }).kits.shoulder_press;
  // With no pose to drive it, the yoke rests at the bottom of the rep.
  kit.place({}, {}, {});
  kit.group.updateMatrixWorld(true);
  return kit;
};
const close = (a, b, within, why) => assert.ok(Math.abs(a - b) <= within, `${why}: ${a.toFixed(3)}, not ${b} ± ${within}`);
const grips = (kit) => kit.contacts.find((c) => c.point === 'grips').at;
const box = (kit) => new THREE.Box3().setFromObject(kit.group);
const role = (kit, name) => { const found = []; kit.group.traverse((o) => { if (o.isMesh && o.userData.role === name) found.push(o); }); return found; };

test('the shoulder press is the kit of the shoulder press kind, and serves machine_shoulder_press', () => {
  assert.ok(KITS.shoulder_press, 'a recipe named shoulder_press');
  assert.equal(MACHINES.shoulder_press.kit, 'shoulder_press');
  assert.deepEqual(MACHINES.shoulder_press.exercises, ['machine_shoulder_press']);
  assert.ok(EQUIPMENT.machine.includes('shoulder_press'));
});

test('the studio shows it alone while no pose uses it', () => {
  const entry = shownAlone('shoulder_press');
  assert.equal(entry.alone, true);
  assert.deepEqual(entry.kit, { shoulder_press: {} });
});

test('the yoke is one revolute joint about a transverse axis at grip height, 0° to 40° up from level ahead', () => {
  const { yoke } = machine().joints;
  assert.equal(yoke.type, 'revolute');
  assert.equal(Math.abs(yoke.axis.x), 1);
  assert.equal(yoke.axis.y, 0);
  assert.equal(yoke.axis.z, 0);
  close(THREE.MathUtils.radToDeg(yoke.limits[0]), 0, 1e-6, 'the bottom, degrees above level');
  close(THREE.MathUtils.radToDeg(yoke.limits[1]), 40, 0.5, 'the top, degrees above level');
  close(yoke.origin.y, 0.98, 1e-9, 'the pivot above the floor');
});

test('the grips rest at 980 mm, 700 apart, on a 900 mm arm, and scrub up 580 mm and back about 200', () => {
  const kit = machine();
  const [left, right] = grips(kit);
  close(left.y, 0.98, 0.005, 'the left grip at rest');
  close(Math.abs(left.x - right.x), 0.7, 1e-6, 'the grips apart');
  close(left.z - kit.joints.yoke.origin.z, 0.9, 0.005, 'the grips ahead of the pivot');
  const start = left.clone();
  kit.scrub('yoke', 99);
  const top = grips(kit)[0];
  close(top.y - start.y, 0.58, 0.01, 'the grips rise');
  close(start.z - top.z, 0.21, 0.02, 'the grips end behind where they started');
  kit.scrub('yoke', -99);
  close(grips(kit)[0].y, 0.98, 0.005, 'back at the bottom');
});

test('scrubbing moves the yoke and the stack, and the frame stays', () => {
  const kit = machine();
  const centre = (node) => new THREE.Box3().setFromObject(node).getCenter(new THREE.Vector3());
  const arm = role(kit, 'barGrip')[0];
  const frame = role(kit, 'backPad')[0];
  const plates = role(kit, 'stackHead')[0];
  const [arm0, frame0, head0] = [centre(arm), centre(frame), centre(plates)];
  kit.scrub('yoke', 0.5);
  kit.group.updateMatrixWorld(true);
  assert.ok(centre(arm).distanceTo(arm0) > 0.01, 'a yoke part did not move');
  assert.ok(centre(plates).y > head0.y + 0.01, 'the stack did not rise');
  assert.ok(centre(frame).distanceTo(frame0) < 1e-9, 'the frame moved with the yoke');
});

test('it stands 1.52 m deep, 1.63 m tall and 1.42 m across, a bar grip and a neutral grip a side', () => {
  const kit = machine();
  const size = box(kit).getSize(new THREE.Vector3());
  close(size.z, 1.52, 0.06, 'front to back');
  close(size.y, 1.63, 0.08, 'tall');
  close(size.x, 1.42, 0.08, 'across');
  assert.equal(role(kit, 'barGrip').length, 2);
  assert.equal(role(kit, 'neutralGrip').length, 2);
});

test('the seat top is 350 mm, the back pad runs about 850 mm up to 1.27 m, a little reclined, and the recipe carries no brand', async () => {
  const kit = machine();
  close(new THREE.Box3().setFromObject(role(kit, 'seat')[0]).max.y, 0.35, 0.01, 'the seat top');
  const pad = new THREE.Box3().setFromObject(role(kit, 'backPad')[0]);
  close(pad.max.y, 1.27, 0.04, 'the pad top');
  close(pad.max.y - pad.min.y, 0.85, 0.06, 'the pad height');
  const source = await readFile(new URL('./recipes/shoulder-press.mjs', import.meta.url), 'utf8');
  assert.doesNotMatch(source, /hammer|life ?fitness|strength|hs-sp/i);
});

test('a fit rule sets the seat, at its 350 mm default, and the seat follows it', () => {
  assert.deepEqual(KITS.shoulder_press.fit.map((r) => r.sets), ['seat', 'pad']);
  close(new THREE.Box3().setFromObject(role(machine({ seat: 0.45 }), 'seat')[0]).max.y, 0.45, 0.01, 'the seat set to 450 mm');
});

test('the stack stands beside the lifter, on their right, clear of the legs ahead of the seat', () => {
  const kit = machine();
  const head = new THREE.Box3().setFromObject(role(kit, 'stackHead')[0]).getCenter(new THREE.Vector3());
  // The lifter faces +z, so their right is -x; the arms and the seat are within 0.45 m of the middle.
  assert.ok(head.x < -0.6, `the stack head at x ${head.x.toFixed(2)}, not out beside the arm`);
  // The shins and feet of a seated lifter fill the middle ahead of the seat, from 0.35 m on.
  kit.group.traverse((o) => {
    if (!o.isMesh) return;
    const part = new THREE.Box3().setFromObject(o);
    if (Math.abs(part.getCenter(new THREE.Vector3()).x) > 0.25) return;
    assert.ok(part.max.z < 0.6 || part.min.y > 0.5, 'a part stands in the legs\' way');
  });
});

test('the seat can go no lower than 330 mm and no higher than 500 mm', () => {
  const top = (seat) => new THREE.Box3().setFromObject(role(machine({ seat }), 'seat')[0]).max.y;
  close(top(0.2), 0.33, 0.01, 'a seat set too low');
  close(top(0.7), 0.5, 0.01, 'a seat set too high');
  close(top(0.4), 0.4, 0.01, 'a seat inside the range');
});

test('the tower is closed: a panel each side and behind, under a rounded header', () => {
  const kit = machine();
  assert.equal(role(kit, 'towerPanel').length, 3);
  const [header] = role(kit, 'header');
  const b = new THREE.Box3().setFromObject(header);
  close(b.max.y, 1.63, 0.03, 'the header\'s top');
  assert.ok(b.max.x - b.min.x > 0.38, 'the header spans the tower');
});

test('the yoke arms are flat bars, taller than wide, ending in a curved hook beside each hand', () => {
  const kit = machine();
  const arms = role(kit, 'yokeArm');
  assert.equal(arms.length, 2);
  for (const arm of arms) {
    const b = new THREE.Box3().setFromObject(arm);
    assert.ok(b.max.y - b.min.y > 1.4 * (b.max.x - b.min.x), 'a flat section');
  }
  const hooks = role(kit, 'hook');
  assert.equal(hooks.length, 2);
  for (const hook of hooks) {
    const b = new THREE.Box3().setFromObject(hook);
    assert.ok(b.max.y - b.min.y > 0.07 && b.max.z - b.min.z > 0.05, 'a hook that rises and runs forward');
  }
});
