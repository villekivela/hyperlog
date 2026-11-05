// The seated dip machine (#226), from refs/dip_machine/ref.md and its photos (build notes in the PR):
// handle arms on a pivot at the top of a rear upright, and a carriage of seat, back pad and footplates
// that rocks back as they go down. Its one exercise, seated_dip_machine, is posed on it: a person sits
// on the seat with the back on the pad and the feet on the plates, and holds the grips through the rep.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import * as THREE from 'three';
import { EQUIPMENT, KITS, MACHINES, buildKit } from './kit.mjs';
import { POSES } from './poses.mjs';
import { prepare } from './entries.mjs';
import { buildStage, FRAMES } from './stage.mjs';
import { CHECKS, checkPose } from './checks.mjs';
import { BATCHES } from './review.mjs';
import { heldAt, rad, wpos } from './rig.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const ID = 'seated_dip_machine';

const machine = () => {
  const kit = buildKit({ equipment: 'machine', kit: { dip_machine: {} } }).kits.dip_machine;
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
const midY = (ps) => ps.reduce((s, p) => s + p.y, 0) / ps.length;

test('the dip machine is the kit of the seated dip machine kind, which is posed on it', () => {
  assert.equal(MACHINES.dip_machine.kit, 'dip_machine');
  assert.deepEqual(MACHINES.dip_machine.exercises, [ID]);
  assert.ok(KITS.dip_machine, 'a recipe named dip_machine');
  assert.ok(EQUIPMENT.machine.includes('dip_machine'));
  assert.ok(POSES[ID]?.kit.dip_machine, 'the exercise is posed on the dip machine');
  assert.ok(BATCHES['dip-machine'].includes(ID), 'in its own review batch');
});

test('no brand, logo or decal is named in the recipe', async () => {
  const src = await readFile(path.join(HERE, 'recipes', 'dip-machine.mjs'), 'utf8');
  for (const word of ['hoist', 'roc-it', 'rs-1101', 'atlantis', 'hammer', 'logo', 'decal']) {
    assert.ok(!src.toLowerCase().replace(/no (brand|logo|decal)[^\n]*/g, '').includes(word), `"${word}" in the recipe`);
  }
});

test('it has three joints: the arms 0 to 40°, the carriage 0 to 12°, and the grips that swivel on the arms', () => {
  const { joints } = machine();
  assert.deepEqual(Object.keys(joints).sort(), ['arms', 'carriage', 'grips']);
  for (const j of Object.values(joints)) {
    assert.equal(j.type, 'revolute');
    close(Math.abs(j.axis.clone().normalize().x), 1, 1e-9, 'a transverse axis');
  }
  assert.deepEqual(joints.arms.limits, [0, rad(40)]);
  assert.deepEqual(joints.carriage.limits, [0, rad(12)]);
  assert.equal(joints.arms.value, 0);
});

test('every joint scrubs through its limits and is held there', () => {
  const kit = machine();
  for (const body of ['arms', 'carriage', 'grips']) {
    const [min, max] = kit.joints[body].limits;
    kit.scrub(body, max);
    assert.equal(kit.joints[body].value, max);
    kit.scrub(body, max + 1);
    assert.equal(kit.joints[body].value, max, 'held at its upper limit');
    kit.scrub(body, min - 1);
    assert.equal(kit.joints[body].value, min, 'held at its lower limit');
  }
});

test('scrubbing the arms carries the grips, and the grips turn about their own middle', () => {
  const kit = machine();
  const held = () => meshes(kit, (o) => o.userData.held)[0].box;
  const first = held().getCenter(new THREE.Vector3());
  kit.scrub('arms', rad(40));
  kit.group.updateMatrixWorld(true);
  const dropped = held().getCenter(new THREE.Vector3());
  assert.ok(first.y - dropped.y > 0.25, `the grips dropped ${(first.y - dropped.y).toFixed(2)} m with the arms`);
  const before = held().getSize(new THREE.Vector3());
  kit.scrub('grips', rad(30));
  kit.group.updateMatrixWorld(true);
  close(held().getCenter(new THREE.Vector3()).distanceTo(dropped), 0, 0.005, 'the grip\'s middle, which the swivel turns about');
  const after = held().getSize(new THREE.Vector3());
  assert.ok(Math.abs(after.y - before.y) > 0.01, 'the bar turned');
});

test('the arms hang on a pivot 880 mm up on a rear upright, grips 440 apart, 790 mm at the start and 500 at the finish', () => {
  const kit = machine();
  close(kit.joints.arms.origin.y, 0.88, 0.005, 'the pivot\'s height');
  const handles = contact(kit, 'handles');
  assert.equal(handles.at.length, 2);
  close(Math.abs(handles.at[0].x - handles.at[1].x), 0.44, 1e-3, 'the grips\' spread');
  close(midY(handles.at), 0.79, 0.03, 'the grips at the start, level with the lower chest');
  kit.scrub('arms', rad(40));
  close(midY(contact(kit, 'handles').at), 0.5, 0.03, 'the grips at the finish, beside the hips');
});

test('the seat top is 480, the pad stands tall behind it, the footplates 180 up at the start', () => {
  const kit = machine();
  const [seat] = named(kit, 'seat');
  close(seat.box.max.y, 0.48, 0.005, 'the seat top');
  const [pad] = named(kit, 'backPad');
  assert.ok(pad.box.max.y > 1.15, 'a tall pad, to the shoulders');
  close(midY(contact(kit, 'footplates').at), 0.18, 0.02, 'the footplates');
});

test('it stands 1.5 m long, 1.3 m wide and 1.39 m tall, on the floor, the stack to the user\'s right', () => {
  const kit = machine();
  const box = meshes(kit).reduce((u, { box }) => u.union(box), new THREE.Box3());
  const size = box.getSize(new THREE.Vector3());
  close(size.z, 1.5, 0.1, 'the length');
  close(size.x, 1.26, 0.1, 'the width');
  close(size.y, 1.39, 0.03, 'the height');
  close(box.min.y, 0, 1e-3, 'on the floor');
  const head = meshes(kit, (o) => o.userData.stack)[0].box;
  assert.ok(head.getCenter(new THREE.Vector3()).x < -0.3, 'the stack on the right of a user facing +z');
});

test('the stack\'s plates face the seat, and everything high on the tower is over it', () => {
  const kit = machine();
  const plates = meshes(kit, (o) => o.material?.name === 'plate').map(({ box }) => box.getSize(new THREE.Vector3()));
  assert.ok(plates.length >= 12);
  for (const s of plates) assert.ok(s.z > s.x * 2, 'a plate wider front to back than across: its face toward the seat');
  const head = meshes(kit, (o) => o.userData.stack)[0].box.getCenter(new THREE.Vector3());
  const high = meshes(kit).filter(({ box }) => box.min.y > 1.15);
  assert.ok(high.length >= 2);
  for (const { box } of high) assert.ok(Math.abs(box.getCenter(new THREE.Vector3()).x - head.x) < 0.3, 'a part high above the machine, away from the stack');
});

// A person on it, start to end of the rep.
const stage = await buildStage(prepare(ID));
const frames = (each) => {
  const out = [];
  for (let f = 0; f < FRAMES; f++) { stage.pose(f / (FRAMES - 1)); out.push(each(f)); }
  return out;
};
const bone = (n) => wpos(stage.rig.bones[n]);
const dip = () => stage.kit.kits.dip_machine;

test('the exercise passes every check on every frame: hands closed on the grips, feet on the plates, nothing through the body', async () => {
  const report = await checkPose(ID);
  assert.deepEqual(CHECKS.filter((c) => !report.checks[c].ok), [], JSON.stringify(report.checks, null, 1));
});

test('the hips stay on the seat and the back on the pad through the whole rep', () => {
  frames(() => {
    const kit = dip();
    kit.group.updateMatrixWorld(true);
    const seat = new THREE.Box3().setFromObject(named(kit, 'seat')[0].mesh);
    const pad = new THREE.Box3().setFromObject(named(kit, 'backPad')[0].mesh);
    const pelvis = bone('pelvis');
    assert.ok(pelvis.y > seat.max.y - 0.02 && pelvis.y < seat.max.y + 0.25, `the pelvis ${(pelvis.y - seat.max.y).toFixed(2)} m over the seat`);
    assert.ok(pelvis.z > seat.min.z && pelvis.z < seat.max.z, 'the pelvis over the seat, front to back');
    const back = bone('spine_03');
    assert.ok(back.z < pad.max.z + 0.2 && back.z > pad.min.z - 0.05, 'the upper back at the pad');
  });
});

test('the feet stay on the footplates, which rise as the carriage rocks', () => {
  const heights = frames(() => {
    const plates = dip().contacts.find((c) => c.point === 'footplates').at;
    for (const [k, p] of [['l', plates[1]], ['r', plates[0]]]) {
      assert.ok(wpos(stage.rig.bones[`foot_${k}`]).distanceTo(p) < 0.2, `the ${k} foot near its plate`);
    }
    return midY(plates);
  });
  assert.ok(Math.max(...heights) - Math.min(...heights) > 0.1, 'the plates rise with the carriage');
});

test('both hands stay closed on the grips from the top of the rep to the bottom', () => {
  frames(() => {
    for (const [k, i] of [['l', 1], ['r', 0]]) {
      const grip = dip().contacts.find((c) => c.point === 'handles').at[i];
      assert.ok(heldAt(stage.rig, k).distanceTo(grip) < 0.015, `the ${k} hand ${(heldAt(stage.rig, k).distanceTo(grip) * 100).toFixed(1)} cm off its grip`);
    }
  });
});

test('the arms go from the handles at the chest to straight, the hands at the hips, the carriage and the body rocking back with them', () => {
  stage.pose(0);
  const top = {
    hand: bone('hand_l'), shoulder: bone('upperarm_l'), elbow: bone('lowerarm_l'), pelvis: bone('pelvis'),
    value: dip().joints.arms.value, carriage: dip().joints.carriage.value,
  };
  stage.pose(1);
  const bottom = { hand: bone('hand_l'), shoulder: bone('upperarm_l'), pelvis: bone('pelvis'), value: dip().joints.arms.value, carriage: dip().joints.carriage.value };
  close(top.value, rad(40), 1e-6, 'the arms\' value at the top of the rep');
  close(bottom.value, 0, 1e-6, 'the arms\' value at the bottom');
  close(top.carriage, rad(12), 1e-6, 'the carriage rocked to its stop at the top');
  close(bottom.carriage, 0, 1e-6, 'the carriage upright at the bottom');
  assert.ok(bottom.hand.y - top.hand.y > 0.2, `the hand dropped ${(bottom.hand.y - top.hand.y).toFixed(2)} m from the bottom to the top`);
  const reach = top.shoulder.distanceTo(top.elbow) + top.elbow.distanceTo(top.hand);
  assert.ok(top.shoulder.distanceTo(top.hand) > reach * 0.85, 'the arm nearly straight at the top');
  assert.ok(bottom.hand.y > bottom.shoulder.y - 0.4, 'the hand at the chest at the bottom');
  assert.ok(top.pelvis.z < bottom.pelvis.z - 0.03, 'the hips rocked back with the carriage');
});

test('the carriage drives the pad: it leans back more at the top than at the bottom', () => {
  const middle = (d) => {
    stage.pose(d);
    const kit = dip();
    kit.group.updateMatrixWorld(true);
    return new THREE.Box3().setFromObject(named(kit, 'backPad')[0].mesh).getCenter(new THREE.Vector3()).z;
  };
  assert.ok(middle(0) < middle(1) - 0.02, 'the pad\'s middle further back at the top');
});
