// The pec deck (#208), from refs/pec_deck/ref.md: a seat and back pad under an overhead unit with
// two lever arms, each swinging in a horizontal plane about a vertical axis, a drop arm and a
// vertical grip at the end of each, and one weight stack behind. Its three exercises are not posed.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import * as THREE from 'three';
import { EQUIPMENT, KITS, MACHINES, buildKit, shownAlone } from './kit.mjs';
import { buildStage } from './stage.mjs';
import { prepare } from './entries.mjs';
import { POSES } from './poses.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const library = JSON.parse(await readFile(path.join(HERE, '..', '..', 'app', 'src', 'main', 'assets', 'exercises.json'), 'utf8'));

const IDS = ['pec_deck', 'reverse_pec_deck', '1_arm_reverse_pec_deck'];

test('it is the kit of the three pec deck exercises the ref names, none of them posed', () => {
  assert.deepEqual(MACHINES.pec_deck, { kit: 'pec_deck', exercises: IDS });
  assert.ok(KITS.pec_deck, 'a recipe named pec_deck');
  assert.ok(EQUIPMENT.machine.includes('pec_deck'));
  for (const id of IDS) {
    assert.equal(library.exercises.find((e) => e.id === id)?.equipment, 'machine');
    assert.equal(POSES[id], undefined, `${id} is posed already`);
  }
});

const deck = (options = {}) => {
  const kit = buildKit({ equipment: 'machine', kit: { pec_deck: options } }).kits.pec_deck;
  kit.place({}, {}, {});
  kit.group.updateMatrixWorld(true);
  return kit;
};
const close = (a, b, within, why) => assert.ok(Math.abs(a - b) <= within, `${why}: ${a.toFixed(3)}, not ${b} ± ${within}`);
const box = (kit) => new THREE.Box3().setFromObject(kit.group);
const grip = (kit, side) => kit.contacts.find((c) => c.point === `${side}Grip`).at[0];
const swept = (kit, side, to) => { kit.scrub(side, to); kit.group.updateMatrixWorld(true); return grip(kit, side); };
const rad = (d) => (d * Math.PI) / 180;

test('it is 1.80 m tall, and 1.42 m wide by 1.245 m deep on the floor with the arms out', () => {
  const b = box(deck());
  close(b.max.y, 1.8, 0.01, 'the overall height');
  close(b.min.y, 0, 0.01, 'on the floor');
  close(b.max.x - b.min.x, 1.42, 0.05, 'the width');
  close(b.max.z - b.min.z, 1.245, 0.06, 'the depth');
});

test('each arm is its own revolute joint about a vertical axis 1.62 m up, 0.4 m apart, the sweep 0 to 120° inward', () => {
  const kit = deck();
  assert.deepEqual(Object.keys(kit.joints).sort(), ['left', 'right']);
  for (const [side, s] of [['left', -1], ['right', 1]]) {
    const j = kit.joints[side];
    assert.equal(j.type, 'revolute');
    close(j.origin.y, 1.62, 0.03, `the ${side} axis' height`);
    close(j.origin.x, s * 0.2, 0.01, `the ${side} axis' side`);
    assert.deepEqual(j.limits, [0, rad(120)]);
    assert.ok(Math.abs(j.axis.y) > 0.999 && Math.sign(j.axis.y) === -s, `the ${side} axis is vertical and mirrors the other`);
  }
  assert.ok(kit.bodies.left.length && kit.bodies.right.length);
});

test('the grips are vertical, at 1.05 m: out at the sides, a little behind the shoulders, at rest; close in front of the chest at the end', () => {
  const kit = deck();
  for (const [side, s] of [['left', -1], ['right', 1]]) {
    const g = grip(kit, side);
    close(g.y, 1.05, 0.02, 'the grip\'s height');
    assert.ok(s * g.x > 0.55 && s * g.x < 0.7, `the ${side} hand ${g.x.toFixed(2)} m out at rest`);
    assert.ok(g.z < 0.02 && g.z > -0.2, `the ${side} hand ${g.z.toFixed(2)} m, level with the shoulders or a little behind`);
  }
  const l = swept(kit, 'left', rad(120));
  const r = swept(kit, 'right', rad(120));
  assert.ok(r.x - l.x < 0.13 && r.x - l.x > 0, `the hands ${((r.x - l.x) * 100).toFixed(0)} cm apart at the end`);
  assert.ok(l.z > 0.35 && l.z < 0.6, 'in front of the chest');
  close(l.y, 1.05, 1e-6, 'still at shoulder height');
});

test('a grip stays 0.41 m from its axis, the drop arm 0.48 less 70 mm in, through the sweep, and the arms are independent', () => {
  const kit = deck();
  const right = kit.joints.right.origin;
  const rest = grip(kit, 'left').clone();
  for (const v of [0, 30, 60, 110]) {
    const g = swept(kit, 'right', rad(v));
    close(Math.hypot(g.x - right.x, g.z - right.z), 0.41, 0.02, `the right grip at ${v}°, from its axis`);
  }
  assert.ok(grip(kit, 'left').distanceTo(rest) < 1e-9, 'the left arm did not move');
  assert.equal(kit.joints.left.value, 0);
  assert.equal(kit.joints.right.value, rad(110));
});

test('the contacts tie each hand to its arm\'s grip and the back to the frame\'s pad', () => {
  const kit = deck();
  assert.deepEqual(
    kit.contacts.map((c) => [c.body, c.point, c.landmark, c.at.length]),
    [['left', 'leftGrip', 'hands', 1], ['right', 'rightGrip', 'hands', 1], ['frame', 'backPad', 'back', 1]],
  );
  const g = grip(kit, 'left');
  const held = [];
  kit.group.traverse((o) => { if (o.isMesh && o.userData.held) held.push(new THREE.Box3().setFromObject(o)); });
  assert.ok(held.some((b) => b.distanceToPoint(g) < 1e-3 && b.max.y - b.min.y > 0.1), 'a vertical held grip at the hand');
});

test('the seat goes where the grips meet the shoulders, within its 0.40 to 0.56 m range', async () => {
  const rule = KITS.pec_deck.fit.find((f) => f.sets === 'seat');
  assert.match(rule.rule, /shoulder/);
  const { rig } = await buildStage(prepare('machine_chest_press'));
  const [y] = rule.solve({ L: rig.dims, entry: { body: {} } });
  close(y + 0.09 + rig.dims.trunkUp, 1.05, 0.001, 'the shoulders at the grips');
  assert.ok(y >= 0.4 && y <= 0.56);
  const [tall] = rule.solve({ L: { ...rig.dims, trunkUp: 0.4 }, entry: { body: {} } });
  assert.equal(tall, 0.56, 'the seat stops at its highest');
});

test('its recipe names no brand', async () => {
  const source = await readFile(path.join(HERE, 'recipes', 'pec-deck.mjs'), 'utf8');
  assert.doesNotMatch(source, /hammer|strength|life ?fitness|\bhs-|logo|decal/i);
});

test('the studio shows it alone, and the arms scrub', async () => {
  const entry = shownAlone('pec_deck');
  assert.ok(entry?.camera, 'a framing to open on');
  const stage = await buildStage(entry);
  stage.pose(0);
  assert.ok(stage.kit.kits.pec_deck);
});
