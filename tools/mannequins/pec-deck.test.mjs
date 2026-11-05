// The pec deck (#208), from refs/pec_deck/ref.md: a seat and back pad under an overhead unit with
// two lever arms, each swinging in a horizontal plane about a vertical axis, a drop arm and a
// vertical grip at the end of each, and one weight stack in the tower behind the pad.
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
import { checkPose } from './checks.mjs';
import { heldAt, wpos } from './rig.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const library = JSON.parse(await readFile(path.join(HERE, '..', '..', 'app', 'src', 'main', 'assets', 'exercises.json'), 'utf8'));

const IDS = ['pec_deck', 'reverse_pec_deck', '1_arm_reverse_pec_deck'];

test('it is the kit of the three pec deck exercises the ref names', () => {
  assert.deepEqual(MACHINES.pec_deck, { kit: 'pec_deck', exercises: IDS });
  assert.ok(KITS.pec_deck, 'a recipe named pec_deck');
  assert.ok(EQUIPMENT.machine.includes('pec_deck'));
  for (const id of IDS) {
    assert.equal(library.exercises.find((e) => e.id === id)?.equipment, 'machine');
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

// The page gives 1.80 m and 1.42 m, but the demo's lifter stands against a taller machine: his head
// seated is 71% of its height, his shoulders 55%, the pad's top 60%. It is built to the lifter.
test('it is 2.0 m tall, as the demo has it against its lifter, 1.56 m wide with the arms out, and 1.3 m from the tower\'s back to the arms swept forward', () => {
  const kit = deck();
  const b = box(kit);
  close(b.max.y, 2.0, 0.01, 'the overall height');
  close(b.min.y, 0, 0.01, 'on the floor');
  close(b.max.x - b.min.x, 1.56, 0.03, 'the width');
  kit.scrub('left', rad(104));
  kit.scrub('right', rad(104));
  kit.group.updateMatrixWorld(true);
  const forward = box(kit);
  close(forward.max.z - forward.min.z, 1.3, 0.03, 'the depth, the page\'s 1.245 m at this scale');
});

// The demo at 0:28: the pad nearly edge-on while the stack and the placard stand face-on.
test('the tower is narrow across and deep fore and aft, the stack\'s broad face looking to the side', () => {
  const kit = deck();
  const stack = head(kit);
  assert.ok(stack.max.z - stack.min.z > 2 * (stack.max.x - stack.min.x), 'the stack long fore and aft');
  const uprights = boxes(kit).filter(({ mesh, box: b }) => mesh.userData.solid && b.max.y - b.min.y > 1.3 && b.max.z < -0.2);
  assert.equal(uprights.length, 2, 'two uprights');
  for (const { box: b } of uprights) assert.ok(b.max.x - b.min.x < 0.15 && b.min.x > -0.08, 'each on the centre line, about 130 mm across');
  const [front, rear] = uprights.map(({ box: b }) => b.getCenter(new THREE.Vector3()).z).sort((a, c) => c - a);
  close(front - rear, 0.38, 0.03, 'one ahead of the other');
});

test('seated, the lifter stands to the machine as the demo\'s does: the shoulders 55% of its height, the pad\'s top 60%', async () => {
  const { pose, rig, kit } = await buildStage(prepare('pec_deck'));
  pose(1);
  const top = new THREE.Box3().setFromObject(kit.kits.pec_deck.group).max.y;
  close(wpos(rig.bones.upperarm_l).y / top, 0.55, 0.02, 'the shoulders\' share');
  let pad = null;
  kit.group.traverse((o) => { if (o.name === 'pec_deck_back') pad = new THREE.Box3().setFromObject(o); });
  close(pad.max.y / top, 0.6, 0.02, 'the pad top\'s share');
});

test('the base ends at a small foot under the seat, and its rear beam runs out to each side at the tower', () => {
  const low = boxes(deck()).filter(({ box: b }) => b.max.y < 0.12);
  assert.ok(low.length, 'parts on the floor');
  assert.ok(Math.max(...low.map(({ box: b }) => b.max.z)) < 0.2, 'nothing on the floor ahead of the seat\'s front');
  const rear = low.filter(({ box: b }) => b.max.z < -0.5);
  assert.ok(Math.max(...rear.map(({ box: b }) => b.max.x)) > 0.38, 'the rear beam out to the side');
  assert.ok(Math.min(...rear.map(({ box: b }) => b.min.x)) < -0.38, 'and to the other');
});

test('the lower plate runs back to the tower\'s top, and the arch comes level into the unit', () => {
  const kit = deck();
  const rail = boxes(kit).find(({ box: b }) => b.min.y > 1.6 && b.max.y < 1.75 && b.max.x - b.min.x < 0.1 && b.max.z - b.min.z > 0.15);
  assert.ok(rail, 'a rail from the unit to the tower');
  assert.ok(rail.box.min.z < -0.25, `the rail reaches the tower, to z ${rail.box.min.z.toFixed(2)}`);
});

test('the empty machine\'s stack sits down as one block', () => {
  close(head(deck()).max.y, 0.57, 0.002, 'the top plate at rest');
});

test('each arm is its own revolute joint about a vertical axis 1.8 m up, 0.4 m apart, the sweep 0 to 120° inward', () => {
  const kit = deck();
  assert.deepEqual(Object.keys(kit.joints).sort(), ['left', 'right']);
  for (const [side, s] of [['left', -1], ['right', 1]]) {
    const j = kit.joints[side];
    assert.equal(j.type, 'revolute');
    close(j.origin.y, 1.8, 0.02, `the ${side} axis' height`);
    close(j.origin.x, s * 0.2, 0.01, `the ${side} axis' side`);
    assert.deepEqual(j.limits, [0, rad(120)]);
    assert.ok(Math.abs(j.axis.y) > 0.999 && Math.sign(j.axis.y) === -s, `the ${side} axis is vertical and mirrors the other`);
  }
  assert.ok(kit.bodies.left.length && kit.bodies.right.length);
});

// The demo at 0:25 and 0:28: the grip bends out of the drop arm square to the lever, ahead of it with
// the arms out and inward with them together, so the hand stays an arm's length from the shoulder.
test('the vertical grip stands 100 mm off the drop arm, square to the lever, about 0.56 m from its axis', () => {
  const kit = deck();
  for (const [side, s] of [['left', -1], ['right', 1]]) {
    const axis = kit.joints[side].origin;
    const g = grip(kit, side);
    close(Math.hypot(g.x - axis.x, g.z - axis.z), Math.hypot(0.55, 0.1), 0.002, `the ${side} grip from its axis`);
    assert.ok(g.z > axis.z - 0.02, `the ${side} grip stands ahead of the lever, at z ${g.z.toFixed(3)}`);
  }
});

// The demo at 0:40: the rear delt's hands take the short grip, on the far side of the drop arm from the vertical one.
test('a short level grip stands on the other side of the drop arm, at the vertical grip\'s foot', () => {
  const kit = deck({ facing: 'pad' });
  for (const side of ['left', 'right']) {
    const axis = kit.joints[side].origin;
    const p = kit.contacts.find((c) => c.point === `${side}Short`).at[0];
    close(Math.hypot(p.x - axis.x, p.z - axis.z), Math.hypot(0.55, 0.1), 0.002, `the ${side} short grip from its axis`);
    assert.ok(p.y < 1.1, 'below the vertical grip\'s middle');
  }
  const held = [];
  kit.group.traverse((o) => { if (o.isMesh && o.userData.held) held.push(new THREE.Box3().setFromObject(o)); });
  const at = kit.contacts.find((c) => c.point === 'leftShort').at[0];
  assert.ok(held.some((b) => b.distanceToPoint(at) < 1e-3), 'a held part at the short grip');
});

test('the back pad leans back about 7°, as the demo shows it at 0:28', () => {
  const kit = deck();
  let lean = null;
  kit.group.traverse((o) => { if (o.name === 'pec_deck_back') lean = o.rotation.x; });
  assert.notEqual(lean, null, 'a part named pec_deck_back');
  close(Math.abs(lean), rad(7), rad(1), 'the pad\'s lean');
});

/** Every mesh of the kit with its box in the world. */
const boxes = (kit) => {
  const out = [];
  kit.group.updateMatrixWorld(true);
  kit.group.traverse((o) => { if (o.isMesh) out.push({ mesh: o, box: new THREE.Box3().setFromObject(o, true) }); });
  return out;
};
const sleeveOf = (kit) => boxes(kit).find(({ mesh, box }) => mesh.userData.held && box.max.y - box.min.y > 0.12);

// The bake builds every pose's kit in one process, and a bent part's shape is cached by its key.
test('the vertical grip stays on the arm when the other way of facing was built first', () => {
  deck();
  for (const kit of [deck({ facing: 'pad' }), deck()]) {
    const sleeve = sleeveOf(kit).box;
    const top = sleeve.getCenter(new THREE.Vector3()).setY(sleeve.max.y);
    const near = boxes(kit).filter(({ mesh }) => !mesh.userData.held).some(({ box }) => box.distanceToPoint(top) < 0.02 && box.max.y > top.y + 0.02);
    assert.ok(near, 'the bend runs from the sleeve\'s top up to the drop arm');
  }
});

// The maker's photo: a cane, hung from the top, its foot free.
test('the vertical grip hangs from its top, its foot free of the drop arm', () => {
  const kit = deck();
  const sleeve = sleeveOf(kit).box;
  const foot = sleeve.getCenter(new THREE.Vector3()).setY(sleeve.min.y - 0.03);
  assert.ok(!boxes(kit).some(({ mesh, box }) => !mesh.userData.held && box.containsPoint(foot) && box.max.y - box.min.y < 0.3), 'nothing under the sleeve but the end of its own bend');
});

test('the overhead unit is open: flat plates on the two axles, nothing thick between them', () => {
  const kit = deck();
  const across = boxes(kit).filter(({ box }) => box.min.y > 1.45 && box.min.z > -0.25 && box.min.x < -0.2 && box.max.x > 0.2 && box.max.x - box.min.x < 0.7);
  assert.ok(across.length >= 2, 'a plate above the levers and one below');
  for (const { box } of across) assert.ok(box.max.y - box.min.y < 0.03, `a part ${(box.max.y - box.min.y).toFixed(2)} m thick across the unit`);
});

test('a brace runs out and down each side of the tower from about a quarter of the way up', () => {
  const braces = boxes(deck()).filter(({ box: b }) => b.max.y > 0.3 && b.max.y < 0.55 && b.max.z < -0.3 && b.max.x - b.min.x > 0.25 && (b.min.x > 0.02 || b.max.x < -0.02));
  assert.equal(braces.length, 2, 'one a side, running out sideways');
});

test('no crossbar runs across the tower\'s window: the pad is carried on the seat\'s post', () => {
  const across = boxes(deck()).filter(({ box }) => box.min.y > 0.3 && box.max.y < 1.2 && box.min.x < -0.2 && box.max.x > 0.2 && box.max.z < -0.25);
  assert.deepEqual(across.map(({ mesh }) => mesh.name), []);
});

const head = (kit) => {
  let found = null;
  kit.group.traverse((o) => { if (o.userData.stack) found = o; });
  kit.group.updateMatrixWorld(true);
  return new THREE.Box3().setFromObject(found);
};

test('the stack stands in the tower right behind the pad, with no column between them', () => {
  const { z } = head(deck()).getCenter(new THREE.Vector3());
  assert.ok(z > -0.56 && z < -0.42, `the stack at z ${z.toFixed(2)}, not in the tower behind the pad`);
});

test('the stack rises as the arms come in', () => {
  const kit = deck();
  const rest = head(kit).max.y;
  kit.scrub('left', rad(100));
  assert.ok(head(kit).max.y > rest + 0.1, `the stack rose ${((head(kit).max.y - rest) * 1000).toFixed(0)} mm`);
});

test('the grips are vertical, at 1.10 m: out at the sides, a little behind the shoulders, at rest; close in front of the chest at the end', () => {
  const kit = deck();
  for (const [side, s] of [['left', -1], ['right', 1]]) {
    const g = grip(kit, side);
    close(g.y, 1.1, 0.02, 'the grip\'s height');
    assert.ok(s * g.x > 0.65 && s * g.x < 0.8, `the ${side} hand ${g.x.toFixed(2)} m out at rest`);
    assert.ok(g.z < -0.06 && g.z > -0.3, `the ${side} hand ${g.z.toFixed(2)} m, level with the shoulders or a little behind`);
  }
  const l = swept(kit, 'left', rad(104));
  const r = swept(kit, 'right', rad(104));
  assert.ok(r.x - l.x < 0.13 && r.x - l.x > 0, `the hands ${((r.x - l.x) * 100).toFixed(0)} cm apart at the end`);
  assert.ok(l.z > 0.3 && l.z < 0.55, 'in front of the chest');
  close(l.y, 1.1, 1e-6, 'still at shoulder height');
});

test('a grip stays on its circle through the sweep, and the arms are independent', () => {
  const kit = deck();
  const right = kit.joints.right.origin;
  const rest = grip(kit, 'left').clone();
  for (const v of [0, 30, 60, 110]) {
    const g = swept(kit, 'right', rad(v));
    close(Math.hypot(g.x - right.x, g.z - right.z), Math.hypot(0.55, 0.1), 0.002, `the right grip at ${v}°, from its axis`);
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
  assert.deepEqual(
    deck({ facing: 'pad' }).contacts.map((c) => [c.body, c.point, c.landmark, c.at.length]),
    [['left', 'leftShort', 'hands', 1], ['right', 'rightShort', 'hands', 1], ['frame', 'backPad', 'chest', 1]],
  );
  const g = grip(kit, 'left');
  const held = [];
  kit.group.traverse((o) => { if (o.isMesh && o.userData.held) held.push(new THREE.Box3().setFromObject(o)); });
  assert.ok(held.some((b) => b.distanceToPoint(g) < 1e-3 && b.max.y - b.min.y > 0.1), 'a vertical held grip at the hand');
});

test('the seat goes where the grips meet the shoulders, within its 0.44 to 0.62 m range', async () => {
  const rule = KITS.pec_deck.fit.find((f) => f.sets === 'seat');
  assert.match(rule.rule, /shoulder/);
  const { rig } = await buildStage(prepare('machine_chest_press'));
  const [y] = rule.solve({ L: rig.dims, entry: { body: {} } });
  close(y + 0.095 + rig.dims.trunkUp, 1.1, 0.001, 'the shoulders at the grips');
  assert.ok(y >= 0.44 && y <= 0.62);
  const [tall] = rule.solve({ L: { ...rig.dims, trunkUp: 0.3 }, entry: { body: {} } });
  assert.equal(tall, 0.62, 'the seat stops at its highest');
});

test('its recipe names no brand', async () => {
  const source = await readFile(path.join(HERE, 'recipes', 'pec-deck.mjs'), 'utf8');
  assert.doesNotMatch(source, /hammer|strength|life ?fitness|\bhs-|logo|decal/i);
});

test('the studio shows it alone, standing at rest with both arms out', async () => {
  const entry = shownAlone('pec_deck');
  assert.ok(entry?.camera, 'a framing to open on');
  const stage = await buildStage(entry);
  stage.pose(0);
  assert.ok(stage.kit.kits.pec_deck);
});

// #241: a body fits it. The checks hold the hands on the grips, the pad on the back or the chest, the
// feet on the floor and nothing through the body, on every frame from the start of the rep to its end.
const fails = async (id) => Object.entries((await checkPose(id)).checks).filter(([, c]) => !c.ok).map(([k, c]) => `${k}: ${c.found} (frame ${c.frame})`);

test('pec_deck: the body fits the machine through the whole rep', async () => {
  assert.deepEqual(await fails('pec_deck'), []);
});

test('pec_deck: seated on the pad, the hands meet in front of the chest and open level with the shoulders', async () => {
  const { pose, rig } = await buildStage(prepare('pec_deck'));
  pose(0);
  const [l, r] = ['l', 'r'].map((k) => heldAt(rig, k));
  assert.ok(l.distanceTo(r) < 0.16, `the hands ${(l.distanceTo(r) * 100).toFixed(0)} cm apart at the top`);
  const chest = wpos(rig.bones.spine_03);
  assert.ok(l.z > chest.z + 0.3, 'in front of the chest');
  pose(1);
  const [lo, ro] = ['l', 'r'].map((k) => heldAt(rig, k));
  const shoulder = wpos(rig.bones.upperarm_l);
  assert.ok(lo.distanceTo(ro) > 1.2, `the hands ${lo.distanceTo(ro).toFixed(2)} m apart when open`);
  close(lo.y, shoulder.y, 0.08, 'the hand level with the shoulder');
  assert.ok(lo.z < shoulder.z + 0.08, 'level with the shoulder or a little behind');
});

test('reverse_pec_deck: the body fits the machine through the whole rep', async () => {
  assert.deepEqual(await fails('reverse_pec_deck'), []);
});

test('reverse_pec_deck: chest to the pad on the same seat, the arms open from ahead until in line with the shoulders', async () => {
  const { pose, rig, kit } = await buildStage(prepare('reverse_pec_deck'));
  const padAt = kit.kits.pec_deck.contacts.find((c) => c.point === 'backPad').at[0];
  pose(1);
  const shoulder = wpos(rig.bones.upperarm_l);
  assert.ok(padAt.z > shoulder.z, 'the pad ahead of the body');
  const reach = heldAt(rig, 'l');
  assert.ok(reach.z > shoulder.z + 0.25, `the hand ${(reach.z - shoulder.z).toFixed(2)} m ahead of the shoulder at the stretch`);
  pose(0);
  const open = heldAt(rig, 'l');
  close(open.z, wpos(rig.bones.upperarm_l).z, 0.1, 'the hand in line with the shoulder at the end');
  close(open.y, wpos(rig.bones.upperarm_l).y, 0.05, 'the hand level with the shoulder');
  const [shoulderAt, elbow, wrist] = ['upperarm_l', 'lowerarm_l', 'hand_l'].map((b) => wpos(rig.bones[b]));
  assert.ok(elbow.y < shoulderAt.y + 0.02, 'the elbow no higher than the shoulder: a line, not an inverted V');
  assert.ok(shoulderAt.y - elbow.y < 0.1, `the elbow ${(shoulderAt.y - elbow.y).toFixed(2)} m below the shoulder: nearly a straight arm, as in the demo`);
  const bend = elbow.clone().sub(shoulderAt).angleTo(wrist.clone().sub(elbow));
  assert.ok(bend < rad(65), `the elbow bent ${(bend * 180 / Math.PI).toFixed(0)}°`);
});

test('reverse_pec_deck: the chest bears on the pad through the rep, the hips on the seat', async () => {
  const entry = prepare('reverse_pec_deck');
  const { pose, rig, kit } = await buildStage(entry);
  const seatEdge = 0.21;
  for (const d of [0, 0.5, 1]) {
    pose(d);
    const hip = wpos(rig.bones.pelvis);
    assert.ok(-hip.z < seatEdge - 0.08, `the hips ${(-hip.z).toFixed(2)} m along the seat, not hanging off its edge`);
  }
  assert.equal(kit.kits.pec_deck.contacts.find((c) => c.point === 'backPad').landmark, 'chest');
});

test('1_arm_reverse_pec_deck: one arm opens, and the other arm of the machine stays at its rest', async () => {
  assert.deepEqual(await fails('1_arm_reverse_pec_deck'), []);
  const { pose, kit } = await buildStage(prepare('1_arm_reverse_pec_deck'));
  pose(0);
  const { left, right } = kit.kits.pec_deck.joints;
  assert.ok(left.value > rad(40), 'the working arm is open');
  assert.equal(right.value, 0, 'the other stays');
});
