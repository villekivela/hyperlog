// The cable crossover (#181), from refs/cable_crossover/ref.md: two towers 2210 mm apart joined by a
// crossmember, each with a stack behind a rail that a pulley carriage slides up to any of 16
// heights, its pulley swivelling toward the cable, and a free handle on the cable's end.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import * as THREE from 'three';
import { CABLE_MACHINES, EQUIPMENT, KITS, buildKit, shownAlone } from './kit.mjs';
import { buildStage } from './stage.mjs';
import { POSES } from './poses.mjs';
import { V } from './rig.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const crossover = (options = {}) => {
  const kit = buildKit({ equipment: 'cable', kit: { cable_crossover: options } }).kits.cable_crossover;
  // With no pose to drive it, the handles hang on their cables.
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
/** The pin's height: the arm that carries the swivel out from the carriage runs level with it. */
const pinAt = (kit, side) => centre(unite(boxes(kit, (o) => o.userData.pin === side))).y;

test('two towers 2210 mm apart on the centre line, the crossmember joining them, the whole 2.73 m high', () => {
  const kit = crossover();
  const all = unite(boxes(kit, () => true));
  close(all.max.y, 2.731, 0.01, 'the machine\'s height');
  const posts = boxes(kit, (o) => o.userData.post).map(centre).sort((a, b) => a.x - b.x);
  assert.equal(posts.length, 2, 'a front post to each tower');
  close(posts[1].x - posts[0].x, 2.21, 0.005, 'the towers\' spacing');
  close(posts[0].x + posts[1].x, 0, 1e-6, 'the towers about the centre line');
  const bridge = boxes(kit, (o) => o.userData.bridge);
  assert.equal(bridge.length, 1);
  close(bridge[0].max.x - bridge[0].min.x, 2.724, 0.005, 'the crossmember\'s length');
  close(centre(bridge[0]).x, 0, 1e-6, 'the crossmember\'s middle');
});

test('the posts stand on the floor and hold the crossmember, each stack stands on a base under it', () => {
  const kit = crossover();
  const bridge = boxes(kit, (o) => o.userData.bridge)[0];
  for (const post of boxes(kit, (o) => o.userData.post)) {
    assert.ok(post.min.y < 0.1, 'a post off the floor');
    close(post.max.y, bridge.min.y, 0.005, 'the post\'s top, against the crossmember\'s underside');
    assert.ok(post.max.x - post.min.x < 0.2 && post.max.x > bridge.min.x && post.min.x < bridge.max.x, 'a post out from under the crossmember');
  }
  const frame = boxes(kit, (o) => o.material?.name === 'frame');
  for (const stack of [-1, 1]) {
    const plates = unite(boxes(kit, (o) => o.material?.name === 'plate' && Math.sign(centre(new THREE.Box3().setFromObject(o)).x) === stack));
    close(centre(plates).x, stack * 1.105, 0.01, 'a stack in its tower');
    const under = frame.filter((b) => b.max.y < plates.min.y + 0.02 && b.max.y > 0.02 && b.min.x < centre(plates).x && b.max.x > centre(plates).x && b.min.z < centre(plates).z && b.max.z > centre(plates).z);
    assert.ok(under.length, `no base under the stack at x ${stack}`);
  }
});

test('each carriage slides 16 positions, pin 350 mm up at the lowest and 2330 mm at the highest', () => {
  assert.deepEqual(KITS.cable_crossover.fit.map((f) => f.sets), ['position']);
  const at = (position) => pinAt(crossover({ position }), 'l');
  close(at(1), 0.35, 0.01, 'position 1\'s pin');
  close(at(16), 2.33, 0.01, 'position 16\'s pin');
  close(at(2) - at(1), 0.132, 0.002, 'the holes\' pitch');
  const kit = crossover({ position: [4, 12] });
  close(pinAt(kit, 'l'), 0.35 + 3 * 0.132, 0.01, 'the left tower at 4');
  close(pinAt(kit, 'r'), 0.35 + 11 * 0.132, 0.01, 'the right tower at 12');
  assert.throws(() => crossover({ position: 17 }), /position 17/);
  for (const side of ['carriage_l', 'carriage_r']) {
    const joint = kit.joints[side];
    assert.equal(joint.type, 'prismatic');
    close(joint.axis.y, 1, 1e-9, `${side}'s axis, up`);
    close(joint.limits[0], 0, 1e-9, `${side}'s lowest`);
    close(joint.limits[1], 15 * 0.132, 1e-9, `${side}'s highest`);
  }
});

test('a carriage scrubbed through its limits takes its pulley and grip bar with it, and the cable follows', () => {
  const kit = crossover({ position: 1 });
  const before = pinAt(kit, 'l');
  const steady = contact(kit, 'steady_l').at[0].clone();
  kit.scrub('carriage_l', 0.5);
  kit.group.updateMatrixWorld(true);
  close(pinAt(kit, 'l') - before, 0.5, 1e-6, 'the pulley\'s rise');
  close(contact(kit, 'steady_l').at[0].y - steady.y, 0.5, 1e-6, 'the grip bar\'s rise');
  assert.equal(pinAt(kit, 'r'), pinAt(crossover({ position: 1 }), 'r'), 'the other tower stays');
  kit.scrub('carriage_l', 9);
  close(kit.joints.carriage_l.value, 15 * 0.132, 1e-9, 'held at its upper limit');
  // The cable runs from the handle's ring to the lower pulley, and the rise to the top wheel.
  const [{ cable, hook }] = kit.cables;
  const ends = new THREE.Box3().setFromObject(cable);
  assert.ok(ends.max.y > 2.2, `the cable reaches ${ends.max.y.toFixed(2)} m, not the raised pulley`);
  assert.ok(hook().y < ends.max.y);
});

test('the pulley swivels 180° about the rail, from side to side, and the stack rises half the cable drawn', () => {
  const kit = crossover();
  for (const side of ['l', 'r']) {
    const swivel = kit.joints[`swivel_${side}`];
    assert.equal(swivel.type, 'revolute');
    close(swivel.limits[0], -Math.PI / 2, 1e-9, 'its swing to one side');
    close(swivel.limits[1], Math.PI / 2, 1e-9, 'its swing to the other');
    close(swivel.axis.y, 1, 1e-9, 'its axis, up the rail');
    const stack = kit.joints[`stack_${side}`];
    assert.equal(stack.type, 'prismatic');
    close(stack.limits[1], 2.743, 1e-9, 'the cable\'s travel, 108 in');
  }
  const head = (side) => unite(boxes(kit, (o) => o.userData.stack && Math.sign(centre(new THREE.Box3().setFromObject(o)).x) === side)).max.y;
  const rest = head(1);
  kit.scrub('stack_l', 1);
  kit.group.updateMatrixWorld(true);
  close(head(1) - rest, 0.5, 1e-6, 'the stack\'s rise for a metre of cable');
  // At the end of its travel the head is still under the guide rods\' tops and the crossmember.
  kit.scrub('stack_l', 9);
  kit.group.updateMatrixWorld(true);
  const top = boxes(kit, (o) => o.userData.bridge)[0].min.y;
  assert.ok(head(1) < top - 0.1, `the head at ${head(1).toFixed(2)} m, into the crossmember at ${top.toFixed(2)}`);
});

test('the handles hang free from the cables\' ends under the pulleys, a grip on each, and the swivel follows a pull', () => {
  const kit = crossover({ position: 10 });
  assert.equal(kit.free, true);
  for (const [side, x] of [['l', 1.105], ['r', -1.105]]) {
    const grip = contact(kit, `handle_${side}`);
    assert.equal(grip.landmark, 'hands');
    assert.equal(grip.body, `handle_${side}`);
    close(grip.at[0].x, x, 0.01, 'the handle under its pulley');
    assert.ok(grip.at[0].y > 0.5 && grip.at[0].y < pinAt(kit, side) - 0.2, 'the handle hanging below the pulley, off the floor');
  }
  // A hand off to one side turns the swivel to it, and draws the stack by the cable drawn out.
  const out = buildKit({ equipment: 'cable', kit: { cable_crossover: { position: 10 } } }).kits.cable_crossover;
  const P = { hand: (s) => V(s * 0.3, 1.1, 1.0) };
  out.place(P, P, P);
  assert.ok(out.joints.swivel_l.value < 0 && out.joints.swivel_r.value > 0, 'each pulley turned in toward the hands');
  assert.ok(out.joints.stack_l.value > 0.3, 'the cable drawn out to the hands lifts the stack');
  close(contact(out, 'handle_l').at[0].distanceTo(V(0.3, 1.1, 1.0)), 0, 1e-6, 'the hand\'s grip is where the hand is');
});

test('the hands hold the handles by their grips and steady themselves on the carriage\'s grip bar', () => {
  const kit = crossover();
  const held = [];
  kit.group.traverse((o) => { if (o.isMesh && o.userData.held) held.push(o); });
  for (const point of ['handle_l', 'handle_r', 'steady_l', 'steady_r']) {
    const c = contact(kit, point);
    assert.equal(c.landmark, 'hands', point);
    const on = held.filter((m) => new THREE.Box3().setFromObject(m).distanceToPoint(c.at[0]) < 0.01);
    assert.ok(on.length, `no held part at ${point}`);
  }
});

test('the studio shows it alone, its handles hanging through the rep', async () => {
  const entry = shownAlone('cable_crossover');
  assert.ok(entry.camera, 'a framing to open on');
  const stage = await buildStage(entry);
  const kit = stage.kit.kits.cable_crossover;
  stage.pose(0);
  const hung = contact(kit, 'handle_l').at[0].clone();
  for (const d of [0, 0.5, 1]) {
    stage.pose(d);
    close(kit.joints.stack_l.value, 0, 1e-9, 'the stack at rest');
    close(contact(kit, 'handle_l').at[0].distanceTo(hung), 0, 1e-9, 'the handle, hanging where it did');
  }
});

test('it serves the cable exercises the ref names that no pose has set up on another kit', async () => {
  const ref = await readFile(path.join(HERE, 'refs', 'cable_crossover', 'ref.md'), 'utf8');
  const named = ref.match(/^Ids: (.+)\.$/m)[1].split(', ');
  assert.equal(named.length, 25);
  const posed = Object.entries(POSES).filter(([, p]) => p.equipment === 'cable' && Object.keys(p.kit).some((k) => k !== 'bench')).map(([id]) => id);
  const unposed = named.filter((id) => !posed.includes(id));
  assert.deepEqual(CABLE_MACHINES.cable_crossover, { kit: 'cable_crossover', exercises: unposed });
  assert.ok(unposed.length > 15 && unposed.length < 25, 'some of the 25 are posed already');
  assert.ok(EQUIPMENT.cable.includes('cable_crossover'));
  const library = JSON.parse(await readFile(path.join(HERE, '..', '..', 'app', 'src', 'main', 'assets', 'exercises.json'), 'utf8'));
  for (const id of unposed) assert.equal(library.exercises.find((e) => e.id === id)?.equipment, 'cable', id);
});

test('its recipe names no brand', async () => {
  const source = await readFile(path.join(HERE, 'recipes', 'cable-crossover.mjs'), 'utf8');
  assert.doesNotMatch(source, /rogue|hammer|life ?fitness|atlantis|\bCC-1|CT-1/i);
});
