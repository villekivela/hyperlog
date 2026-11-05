// The crunch machine (#233), from refs/crunch_machine/ref.md: a reclined crunch on an inclined seat
// pad, the back pad on an arm turning about a cam axis beside the waist, the ankles hooked between
// rollers, a stack at the feet end. The mannequin lies face up with its head toward -z, so the head
// end of the machine is -z and the feet end +z.
//
// The first half tests the machine alone; the second poses `machine_crunch` on it and holds the
// body to it from the start of the rep to the end: a person could use it.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import * as THREE from 'three';
import { KITS, MACHINES, EQUIPMENT, buildKit, shownAlone } from './kit.mjs';
import { POSES } from './poses.mjs';
import { prepare } from './entries.mjs';
import { buildStage, FRAMES } from './stage.mjs';
import { checkStage } from './checks.mjs';
import { heldAt, rad, sideOf, wpos } from './rig.mjs';

const machine = () => {
  const kit = buildKit({ equipment: 'machine', kit: { crunch_machine: {} } }).kits.crunch_machine;
  kit.place({}, {}, {});
  kit.group.updateMatrixWorld(true);
  return kit;
};
const deg = (r) => (r * 180) / Math.PI;
const close = (a, b, within, why) => assert.ok(Math.abs(a - b) <= within, `${why}: ${a.toFixed(3)}, not ${b} ± ${within}`);
const contact = (kit, point) => kit.contacts.find((c) => c.point === point);
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
const boxOf = (o) => new THREE.Box3().setFromObject(o);
const centre = (m) => boxOf(m).getCenter(new THREE.Vector3());
/** A part's up (its local y) in the world, and how far it leans from vertical toward +z (+) or -z (-), in degrees. */
const leanOf = (m) => {
  const up = new THREE.Vector3(0, 1, 0).applyQuaternion(m.getWorldQuaternion(new THREE.Quaternion()));
  return deg(Math.atan2(up.z, up.y));
};

test('the arm turns about the cam axis 780 mm up, 40° from its rest with the back pad level', () => {
  const kit = machine();
  const { arm } = kit.joints;
  assert.equal(arm.type, 'revolute');
  close(deg(arm.limits[0]), 0, 1e-9, 'its lower limit, in degrees');
  close(deg(arm.limits[1]), 40, 1e-9, 'its upper limit, in degrees');
  close(arm.value, 0, 1e-9, 'unposed, at rest');
  close(arm.origin.y, 0.78, 0.03, 'the cam axis\'s height');
});

test('it stands 1.58 m by 890 mm and 1.4 m tall, the ref\'s footprint and height', () => {
  const size = boxOf(machine().group).getSize(new THREE.Vector3());
  close(size.z, 1.58, 0.12, 'its length');
  close(size.x, 0.89, 0.06, 'its width');
  close(size.y, 1.4, 0.04, 'its height');
});

test('the seat pad is 620 mm long, inclined 15° rising toward the head, its high end 530 mm up and its low end 370', () => {
  const seat = only(machine(), 'seat');
  // Leaning toward the feet end as it rises toward the head.
  close(leanOf(seat), 15, 0.5, 'its incline, in degrees');
  const up = new THREE.Vector3(0, 1, 0).applyQuaternion(seat.getWorldQuaternion(new THREE.Quaternion()));
  // Up along its face toward the head: +y, -z.
  const along = new THREE.Vector3(0, up.z, -up.y).normalize().multiplyScalar(0.31);
  const top = centre(seat).addScaledVector(up, 0.05);
  close(top.clone().add(along).y, 0.53, 0.02, 'the high end\'s top');
  close(top.clone().sub(along).y, 0.37, 0.02, 'the low end\'s top');
  assert.ok(top.clone().add(along).z < top.clone().sub(along).z, 'the high end toward the head');
});

test('the back pad rests tilted 12° up toward the head, close to the seat, its head pad 600 mm from the axis, and tips 40° more with the arm', () => {
  const kit = machine();
  const back = only(kit, 'backPad');
  const head = only(kit, 'headPad');
  const { origin } = kit.joints.arm;
  close(leanOf(back), 12, 0.5, 'the back pad\'s lean at rest');
  // The seat's head end and the pad's near end read as one support: within 4 cm of each other.
  const seatEnd = new THREE.Vector3(0, 0.53, -0.14);
  const nearEnd = new THREE.Vector3(0, boxOf(back).min.y, 0);
  const g = back.geometry; g.computeBoundingBox();
  const padStart = new THREE.Vector3(0, g.boundingBox.max.y, g.boundingBox.max.z).applyMatrix4(back.matrixWorld);
  assert.ok(seatEnd.distanceTo(padStart) < 0.04, `the pad ${(seatEnd.distanceTo(padStart) * 100).toFixed(1)} cm from the seat's head end`);
  assert.ok(nearEnd.y < 0.6, 'the pad low enough to carry the back');
  const rest = centre(head);
  close(Math.hypot(rest.y - origin.y, rest.z - origin.z), 0.6, 0.05, 'the head pad from the axis');
  assert.ok(rest.z < origin.z - 0.4, 'the head pad toward the head');
  kit.scrub('arm', rad(40));
  kit.group.updateMatrixWorld(true);
  close(leanOf(back), 52, 0.5, 'tipped toward the knees, in degrees');
  close(centre(head).distanceTo(rest), 0.41, 0.03, 'how far the head pad moved');
  assert.ok(centre(head).y > rest.y + 0.25, 'the head pad rose');
  kit.scrub('arm', rad(80));
  close(deg(kit.joints.arm.value), 40, 1e-9, 'held at its upper limit');
});

test('two foam grips 200 mm long lean 18° toward the head, 400 mm apart either side of the head pad, held by the hands', () => {
  const kit = machine();
  const handles = byRole(kit, 'handle');
  assert.equal(handles.length, 2);
  for (const h of handles) {
    assert.ok(h.userData.held, 'held');
    const size = boxOf(h).getSize(new THREE.Vector3());
    close(Math.hypot(size.y, size.z), 0.2, 0.025, 'a grip\'s length');
    close(deg(Math.atan2(size.z, size.y)), 18, 5, 'its lean from upright, in degrees');
  }
  close(Math.abs(centre(handles[0]).x - centre(handles[1]).x), 0.4, 0.02, 'their spacing');
  const c = contact(kit, 'handles');
  assert.equal(c.landmark, 'hands');
  assert.equal(c.body, 'arm');
  // Each hand's point is on a grip's axis, in its upper half.
  for (const p of c.at) assert.ok(handles.some((h) => { const b = boxOf(h); const k = centre(h); return Math.abs(p.x - k.x) < 0.01 && p.y > k.y && p.y < b.max.y; }), 'a hand on each handle\'s upper half');
});

test('two pairs of foot rollers on the front frame, at 360 and 600 mm, the ankles between them', () => {
  const kit = machine();
  const rollers = byRole(kit, 'roller');
  assert.equal(rollers.length, 4);
  assert.deepEqual([...new Set(rollers.map((r) => Math.round(centre(r).y * 100) / 100))].sort(), [0.36, 0.6]);
  const feet = contact(kit, 'rollers');
  assert.equal(feet.landmark, 'feet');
  assert.equal(feet.body, 'frame');
  close(feet.at[0].y, 0.48, 0.02, 'the ankles, between the pairs');
});

test('the hips lie on the seat\'s high end and the upper back on the back pad', () => {
  const kit = machine();
  const seat = contact(kit, 'seat');
  assert.equal(seat.landmark, 'hips');
  assert.equal(seat.body, 'frame');
  close(seat.at[0].y, 0.5, 0.04, 'the seat, a little down from its high end');
  const back = contact(kit, 'backPad');
  assert.equal(back.landmark, 'back');
  assert.equal(back.body, 'arm');
  close(back.at[0].distanceTo(centre(only(kit, 'backPad'))), 0, 0.12, 'on the back pad');
});

test('the ref has no adjustment, so it declares no fit rule, and the arm sits to one side of the body', () => {
  assert.deepEqual(KITS.crunch_machine.fit ?? [], []);
  // Nothing crosses the body's width at the cam axis: the hub, its upright and the cam stand outside the hips.
  const kit = machine();
  const hub = kit.joints.arm.origin;
  const posts = [];
  kit.group.traverse((o) => { if (o.isMesh && o.material?.name === 'frame') { const b = boxOf(o); if (b.min.y < 0.1 && b.max.y > 0.8 && Math.abs(b.min.z - hub.z) < 0.2) posts.push(b); } });
  assert.ok(posts.every((b) => Math.abs((b.min.x + b.max.x) / 2) > 0.25), 'every upright at the axis stands outside the body');
});

test('the machine serves machine_crunch and the exercise is posed on it', () => {
  assert.deepEqual(MACHINES.crunch_machine, { kit: 'crunch_machine', exercises: ['machine_crunch'] });
  assert.ok(EQUIPMENT.machine.includes('crunch_machine'));
  assert.deepEqual(POSES.machine_crunch.kit, { crunch_machine: {} });
});

test('its cable follows the cam and its stack rises with the arm', () => {
  const kit = machine();
  const plates = () => { const y = []; kit.group.traverse((o) => { if (o.isMesh && o.material?.name === 'plate') y.push(o.position.y); }); return y; };
  const end = () => boxOf(only(kit, 'cable')).min.y;
  const [stack, top] = [plates(), end()];
  kit.scrub('arm', rad(40));
  kit.group.updateMatrixWorld(true);
  assert.ok(plates().some((y, i) => y - stack[i] > 0.1), 'the stack rose');
  assert.ok(Math.abs(end() - top) > 1e-3, 'the cable end moved with the cam');
});

test('no brand name appears in the recipe', async () => {
  const source = await readFile(new URL('./recipes/crunch-machine.mjs', import.meta.url), 'utf8');
  assert.doesNotMatch(source, /hammer|strength|life ?fitness|\blogo\b|decal/i);
});

test('the studio shows the kit alone, its arm scrubbed through its limits', async () => {
  const entry = shownAlone('crunch_machine');
  assert.ok(entry?.camera, 'a framing to open on');
  const stage = await buildStage(entry);
  const kit = stage.kit.kits.crunch_machine;
  stage.pose(0);
  for (const limit of kit.joints.arm.limits) {
    kit.scrub('arm', limit);
    stage.scene.updateMatrixWorld(true);
    close(kit.joints.arm.value, limit, 1e-9, 'scrubbed to its limit');
  }
});

// The pose: a person lies on it (#233).
const posed = async () => {
  const stage = await buildStage(prepare('machine_crunch'));
  const kit = stage.kit.kits.crunch_machine;
  const at = (d) => { stage.pose(d); stage.scene.updateMatrixWorld(true); return kit; };
  return { stage, kit, at };
};
/** Each skinned vertex of the bones `named`, in the world, posed now. */
const skinOf = ({ rig }, named) => {
  const out = [];
  const p = new THREE.Vector3();
  for (const m of rig.meshes) {
    const { skinIndex, skinWeight, position } = m.geometry.attributes;
    for (let i = 0; i < position.count; i++) {
      let top = 0;
      for (let j = 1; j < 4; j++) if (skinWeight.getComponent(i, j) > skinWeight.getComponent(i, top)) top = j;
      if (!named.test(m.skeleton.bones[skinIndex.getComponent(i, top)].name)) continue;
      out.push(m.getVertexPosition(i, p.clone()).applyMatrix4(m.matrixWorld));
    }
  }
  return out;
};
/** How far above the seat's face the lowest of `points` over it is (negative: inside it). */
const aboveSeat = (points) => {
  const up = new THREE.Vector3(0, Math.cos(rad(15)), Math.sin(rad(15)));
  const along = new THREE.Vector3(0, Math.sin(rad(15)), -Math.cos(rad(15)));
  const high = new THREE.Vector3(0, 0.53, -0.14);
  return Math.min(...points.filter((p) => Math.abs(p.x) < 0.18 && p.clone().sub(high).dot(along) < 0.05 && p.clone().sub(high).dot(along) > -0.6).map((p) => p.clone().sub(high).dot(up)));
};

test('posed, the hips lie on the seat and the upper back on the back pad from the start of the rep to the end', async () => {
  const { stage, kit, at } = await posed();
  const pad = only(kit, 'backPad');
  for (const d of [0, 0.25, 0.5, 0.75, 1]) {
    at(d);
    const gap = aboveSeat(skinOf(stage, /^pelvis$/));
    assert.ok(gap > -0.02 && gap < 0.03, `the pelvis ${(gap * 100).toFixed(1)} cm off the seat at ${d} of the rep`);
    // The back's skin against the pad's face, which turns with the arm: its distance above the face, over the pad.
    const face = new THREE.Vector3(0, 1, 0).applyQuaternion(pad.getWorldQuaternion(new THREE.Quaternion()));
    const o = centre(pad).addScaledVector(face, 0.04);
    const head = new THREE.Vector3(0, 0, -1).applyAxisAngle(new THREE.Vector3(1, 0, 0), kit.joints.arm.value);
    const over = skinOf(stage, /^spine_0[23]$/).filter((p) => Math.abs(p.x) < 0.17 && Math.abs(p.clone().sub(o).dot(head)) < 0.22);
    const off = Math.min(...over.map((p) => p.clone().sub(o).dot(face)));
    assert.ok(off > -0.03 && off < 0.035, `the back ${(off * 100).toFixed(1)} cm off the pad at ${d} of the rep`);
  }
});

test('posed, the feet hook between the rollers and both hands close on the handles, through the whole rep', async () => {
  const { stage, kit, at } = await posed();
  const { rig } = stage;
  for (let f = 0; f < FRAMES; f += 5) {
    at(f / (FRAMES - 1));
    for (const k of ['l', 'r']) {
      const ankle = wpos(rig.bones['foot_' + k]);
      const rollers = contact(kit, 'rollers').at[0];
      assert.ok(Math.hypot(ankle.y - rollers.y, ankle.z - rollers.z) < 0.03, `the ${k} ankle ${(ankle.distanceTo(rollers) * 100).toFixed(1)} cm from between the rollers`);
      const hand = heldAt(rig, k);
      const handle = contact(kit, 'handles').at[sideOf(rig, k) < 0 ? 0 : 1];
      assert.ok(hand.distanceTo(handle) < 0.02, `the ${k} hand ${(hand.distanceTo(handle) * 100).toFixed(1)} cm from its handle at frame ${f}`);
    }
  }
});

test('posed, the arm turns the full 40° as the shoulders curl up toward the knees, and nothing passes through the body', async () => {
  const { stage, kit, at } = await posed();
  at(0);
  close(deg(kit.joints.arm.value), 0, 1e-6, 'the arm at the start');
  const shoulders = () => wpos(stage.rig.bones.upperarm_l);
  const start = shoulders();
  at(1);
  close(deg(kit.joints.arm.value), 40, 1e-6, 'the arm at the end');
  const end = shoulders();
  assert.ok(end.y - start.y > 0.15, `the shoulders rose ${((end.y - start.y) * 100).toFixed(0)} cm`);
  assert.ok(end.z > start.z + 0.05, 'the shoulders came toward the knees');
  // The pose's own checks on every frame: hands, feet, equipment (nothing inside the kit), joints, contact, linkage.
  const checks = checkStage(prepare('machine_crunch'), stage);
  for (const [name, c] of Object.entries(checks)) assert.ok(c.ok, `${name}: ${c.found} (frame ${c.frame})`);
});

test('posed, the cable from the cam to the stack passes clear of the body on every frame', async () => {
  const { stage, kit, at } = await posed();
  const cable = only(kit, 'cable');
  const body = skinOf(stage, /./);
  for (const d of [0, 0.5, 1]) {
    at(d);
    cable.updateWorldMatrix(true, false);
    const a = new THREE.Vector3(0, -0.5, 0).applyMatrix4(cable.matrixWorld);
    const b = new THREE.Vector3(0, 0.5, 0).applyMatrix4(cable.matrixWorld);
    const line = new THREE.Line3(a, b);
    const near = Math.min(...body.map((p) => line.closestPointToPoint(p, true, new THREE.Vector3()).distanceTo(p)));
    assert.ok(near > 0.03, `the cable ${(near * 100).toFixed(1)} cm from the body at ${d} of the rep`);
  }
});

test('posed, the moving pad sits behind the shoulders at rest and carries them through the rep', async () => {
  const { stage, kit, at } = await posed();
  const pad = only(kit, 'backPad');
  pad.geometry.computeBoundingBox();
  const { min, max } = pad.geometry.boundingBox;
  for (const d of [0, 0.25, 0.5, 0.75, 1]) {
    at(d);
    const inverse = pad.matrixWorld.clone().invert();
    for (const k of ['l', 'r']) {
      const shoulder = wpos(stage.rig.bones['upperarm_' + k]).applyMatrix4(inverse);
      assert.ok(shoulder.z > min.z + 0.02 && shoulder.z < max.z - 0.02, `the ${k} shoulder ${(shoulder.z * 100).toFixed(0)} cm along the pad (it spans ${(min.z * 100).toFixed(0)} to ${(max.z * 100).toFixed(0)}) at ${d} of the rep`);
      assert.ok(Math.abs(shoulder.x) < (max.x - min.x) / 2 + 0.03, `the ${k} shoulder off the pad's width at ${d} of the rep`);
      assert.ok(shoulder.y - max.y < 0.2, `the ${k} shoulder ${((shoulder.y - max.y) * 100).toFixed(0)} cm above the pad's face at ${d} of the rep`);
    }
  }
});
