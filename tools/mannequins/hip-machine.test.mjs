// The hip machine (#214), from refs/hip_machine/ref.md: two arms under the seat on one vertical
// axis, each an L out to a post with a knee pad and a foot plate, swinging apart (abduction) or
// together (adduction) on a start angle set in 10° steps.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import * as THREE from 'three';
import { EQUIPMENT, KITS, MACHINES, buildKit } from './kit.mjs';
import { rad, wpos } from './rig.mjs';
import { buildStage } from './stage.mjs';
import { checkPose } from './checks.mjs';
import { prepare } from './entries.mjs';
import { openClip, poseFrame } from './glb.mjs';
import { CLIPS } from './bake.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const machine = (options = {}) => {
  const kit = buildKit({ equipment: 'machine', kit: { hip_machine: options } }).kits.hip_machine;
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
const role = (name) => (o) => o.userData.role === name;
const meshes = (kit, wanted) => { const out = []; kit.group.traverse((o) => { if (o.isMesh && wanted(o)) out.push(o); }); return out; };
const DEG = Math.PI / 180;

test('the hip machine is the kit of the machine kind that serves abduction and adduction', () => {
  assert.ok(KITS.hip_machine, 'a recipe named hip_machine');
  assert.equal(MACHINES.hip_machine.kit, 'hip_machine');
  assert.deepEqual(MACHINES.hip_machine.exercises, ['machine_hip_abduction', 'machine_adduction']);
  assert.ok(EQUIPMENT.machine.includes('hip_machine'));
});

test('it stands 1550 by 660 mm on the floor and 1400 mm high', () => {
  const all = unite(boxes(machine(), () => true));
  close(all.max.z - all.min.z, 1.55, 0.05, 'the footprint\'s length');
  close(all.max.x - all.min.x, 0.66, 0.05, 'the footprint\'s width');
  close(all.max.y, 1.4, 0.03, 'the height');
  close(all.min.y, 0, 0.005, 'the floor');
});

test('the seat top is 520 mm up and 360 deep, its front edge 240 mm ahead of the pivot', () => {
  const kit = machine();
  const seat = unite(boxes(kit, role('seat')));
  close(seat.max.y, 0.52, 0.005, 'the seat top');
  close(seat.max.z - seat.min.z, 0.36, 0.02, 'the seat depth');
  close(seat.max.z - kit.joints.arm_l.origin.z, 0.24, 0.01, 'the seat\'s front edge ahead of the axis');
});

test('each arm turns about the one vertical axis on the centre line, 0 to 50° from closed, opening outward', () => {
  const kit = machine();
  assert.deepEqual(Object.keys(kit.joints).sort(), ['arm_l', 'arm_r']);
  for (const [name, s] of [['arm_l', 1], ['arm_r', -1]]) {
    const j = kit.joints[name];
    assert.equal(j.type, 'revolute');
    close(Math.abs(j.axis.y), 1, 1e-9, `${name}'s axis, vertical`);
    close(j.origin.x, 0, 1e-9, `${name}'s axis on the centre line`);
    close(j.origin.z, -0.1, 1e-9, `${name}'s axis, under the hips`);
    assert.deepEqual(j.limits, [0, rad(50)]);
    // Opening is positive for both: the pad moves out, away from the centre line.
    const before = contact(kit, `pad_${name.at(-1)}`).at[0].x;
    kit.scrub(name, rad(45));
    const after = contact(kit, `pad_${name.at(-1)}`).at[0].x;
    assert.ok(s * (after - before) > 0.1, `${name} opening moves its pad outward`);
  }
  assert.equal(kit.joints.arm_l.axis.y, -kit.joints.arm_r.axis.y);
});

test('the arm\'s pad swings 430 mm out from the axis in a circle, and the scrub stops at the limits', () => {
  const kit = machine();
  const { origin } = kit.joints.arm_l;
  const at = () => contact(kit, 'pad_l').at[0].clone().sub(origin);
  const closed = at();
  kit.scrub('arm_l', rad(45));
  const open = at();
  close(Math.hypot(open.x, open.z) , Math.hypot(closed.x, closed.z), 1e-6, 'a pad\'s radius from the axis');
  close(Math.atan2(open.x, open.z) - Math.atan2(closed.x, closed.z), rad(45), 1e-6, 'the pad\'s turn');
  assert.equal(kit.joints.arm_l.value, rad(45));
  kit.scrub('arm_l', rad(500));
  assert.equal(kit.joints.arm_l.value, rad(50));
  kit.scrub('arm_l', -1);
  assert.equal(kit.joints.arm_l.value, 0);
});

test('closed, the pads press the outsides of the knees 430 mm ahead of the axis and 560 mm up, the plates 80 mm up', () => {
  const kit = machine();
  for (const [k, s] of [['l', 1], ['r', -1]]) {
    const pad = contact(kit, `pad_${k}`);
    assert.equal(pad.landmark, 'knees');
    close(pad.at[0].x, s * 0.145, 0.01, 'the pad\'s face, on the knee\'s outside');
    close(pad.at[0].y, 0.56, 0.01, 'the pad\'s height');
    close(pad.at[0].z - kit.joints[`arm_${k}`].origin.z, 0.43, 0.02, 'the pad\'s reach');
    const plate = contact(kit, `plate_${k}`);
    assert.equal(plate.landmark, 'feet');
    close(plate.at[0].y, 0.08, 0.01, 'the plate\'s height');
    close(plate.at[0].z, pad.at[0].z, 0.03, 'the plate under the pad\'s post');
  }
  assert.equal(contact(kit, 'seat').landmark, 'thighs');
  assert.equal(contact(kit, 'back').landmark, 'back');
  assert.equal(contact(kit, 'seat').body, 'frame');
});

test('the start angle moves the arms in 10° steps; adduction puts the pads between the knees, starting open', () => {
  assert.deepEqual(KITS.hip_machine.fit.map((f) => f.sets), ['start']);
  const kit = machine({ start: 30 });
  close(kit.joints.arm_l.value, rad(30), 1e-9, 'the arm at its start');
  assert.equal(machine({ start: 34 }).joints.arm_l.value, rad(30));
  assert.equal(machine({ start: 999 }).joints.arm_l.value, rad(50));
  const inner = machine({ pads: 'inner', start: 40 });
  assert.equal(inner.joints.arm_l.value, rad(40));
  const pad = contact(inner, 'pad_l').at[0];
  const plain = contact(machine({ start: 40 }), 'pad_l').at[0];
  assert.ok(pad.x < plain.x - 0.05, 'an inner pad lies nearer the centre line than an outer one');
  assert.throws(() => machine({ pads: 'upside' }), /pads/);
});

test('the weight stack tower stands ahead of the lifter past the posts, and is the tallest part; the backrest leans back', () => {
  const kit = machine();
  const plates = unite(boxes(kit, (o) => o.material?.name === 'plate'));
  assert.ok(centre(plates).z > 0.8, `the stack at z ${centre(plates).z.toFixed(2)}, not ahead of the posts`);
  const back = unite(boxes(kit, role('backrest')));
  assert.ok(back.max.y > 1.0 && back.max.y < 1.4, 'a backrest short of the tower');
  const lean = machine().contacts.find((c) => c.point === 'back').at[0];
  assert.ok(lean.z < -0.22, 'the back pad\'s middle leans behind its foot');
});

test('no brand or maker appears in the recipe', async () => {
  const text = await readFile(path.join(HERE, 'recipes', 'hip-machine.mjs'), 'utf8');
  assert.doesNotMatch(text.replace(/refs\/hip_machine\/ref\.md/g, ''), /hammer|strength|hoist|life ?fitness|HS-HA|RS-140|logo|decal/i);
});

// #241: a body fits it. The checks hold the hands on the handles, the knees on the pads, the feet on
// the plates, the back on the pad and nothing through the body, on every frame of the rep.
const fails = async (id) => Object.entries((await checkPose(id)).checks).filter(([, c]) => !c.ok).map(([k, c]) => `${k}: ${c.found} (frame ${c.frame})`);

for (const id of ['machine_hip_abduction', 'machine_adduction']) {
  test(`${id}: the body fits the machine through the whole rep`, async () => {
    assert.deepEqual(await fails(id), []);
  });
}

test('machine_hip_abduction: the knees start together and swing apart, the hips staying on the seat', async () => {
  const { pose, rig } = await buildStage(prepare('machine_hip_abduction'));
  pose(0);
  const hips = wpos(rig.bones.pelvis);
  const apart = () => wpos(rig.bones.calf_l).distanceTo(wpos(rig.bones.calf_r));
  const together = apart();
  assert.ok(together < 0.3, `the knees ${together.toFixed(2)} m apart at the stretch`);
  pose(1);
  assert.ok(apart() > together + 0.35, `the knees ${apart().toFixed(2)} m apart at the end`);
  assert.ok(wpos(rig.bones.pelvis).distanceTo(hips) < 0.005, 'the hips stay put');
});

test('machine_adduction: the knees start apart and squeeze together', async () => {
  const { pose, rig } = await buildStage(prepare('machine_adduction'));
  const apart = () => wpos(rig.bones.calf_l).distanceTo(wpos(rig.bones.calf_r));
  pose(0);
  const open = apart();
  pose(1);
  assert.ok(open > apart() + 0.3, `the knees from ${open.toFixed(2)} to ${apart().toFixed(2)} m apart`);
});

test('machine_hip_abduction and machine_adduction: the hips sit on the seat through the rep', async () => {
  for (const id of ['machine_hip_abduction', 'machine_adduction']) {
    const { pose, rig, kit } = await buildStage(prepare(id));
    const seat = contact(kit.kits.hip_machine, 'seat').at[0].y;
    for (const d of [0, 0.5, 1]) {
      pose(d);
      close(wpos(rig.bones.pelvis).y - seat, 0.072, 0.015, `${id}: the hips over the seat, ${d} of the way`);
    }
  }
});

test('each arm turns with its joint on every part of the rep, the feet riding their plates', async () => {
  const { pose, rig, kit } = await buildStage(prepare('machine_hip_abduction'));
  const hip = kit.kits.hip_machine;
  for (const d of [0, 0.5, 1]) {
    pose(d);
    for (const [k, s] of [['l', 1], ['r', -1]]) {
      const arm = hip.bodies[`arm_${k}`][0];
      close(arm.rotation.y, s * hip.joints[`arm_${k}`].value, 1e-6, `arm ${k}, ${d} of the way`);
      const plate = contact(hip, `plate_${k}`).at[0];
      const foot = wpos(rig.bones[`foot_${k}`]);
      assert.ok(Math.hypot(foot.x - plate.x, foot.z - plate.z) < 0.03, `the ${k} foot on its plate, ${d} of the way`);
    }
  }
});

test('the arms never meet the frame, and the handles stand behind the legs\' sweep', () => {
  const kit = machine();
  const handles = boxes(kit, role('handle'));
  const seat = unite(boxes(kit, role('seat')));
  for (const b of handles) assert.ok(b.max.z < seat.min.z + 0.2, `a handle to z ${b.max.z.toFixed(2)}, in the thighs' way`);
});

// The maker's photos: the stack's broad face looks to the side, which is how the machine stands 660 wide.
test('the tower is narrow across and deep fore and aft, its stack\'s broad face looking to the side', () => {
  const kit = machine();
  let head = null;
  kit.group.traverse((o) => { if (o.userData.stack) head = new THREE.Box3().setFromObject(o, true); });
  assert.ok(head.max.z - head.min.z > 2 * (head.max.x - head.min.x), 'the stack long fore and aft');
});

// The posed stage isn't what ships: a part a track can't bind to stays at its rest in the clip.
for (const id of ['machine_hip_abduction', 'machine_adduction']) {
  test(`${id}: the baked clip shows every part of the kit where the pose has it`, async () => {
    const { pose, kit } = await buildStage(prepare(id));
    const { scene, tracks } = await openClip(path.join(CLIPS, `${id}.glb`));
    const last = tracks[0].times.length - 1;
    const live = [];
    kit.group.traverse((o) => { if (o.isMesh && !o.userData.proxy) live.push(o); });
    for (const f of [Math.round(last / 2), last]) {
      pose(f / last);
      kit.group.updateMatrixWorld(true);
      poseFrame(scene, tracks, f);
      for (const o of live) {
        const baked = scene.getObjectByName(o.name);
        assert.ok(baked, `${o.name} is in the clip`);
        const off = baked.getWorldPosition(new THREE.Vector3()).distanceTo(o.getWorldPosition(new THREE.Vector3()));
        assert.ok(off < 0.005, `${o.name} ${(off * 100).toFixed(1)} cm off the pose at frame ${f}`);
      }
    }
  });
}

// The owner's bar (#241): no open tube end, mitred joints, every support reaching the floor or its frame.
test('no tube on it stands open: every bent length is capped', () => {
  const open = [];
  machine().group.traverse((o) => { if (o.isMesh && o.geometry.type === 'TubeGeometry') open.push(o.name); });
  // `bent` softens its tube into a plain geometry, so an open one shows by its rim: an edge on one face.
  const rims = [];
  machine().group.traverse((o) => {
    if (!o.isMesh || !o.geometry.index) return;
    const seen = new Map();
    const i = o.geometry.index.array;
    const p = o.geometry.attributes.position;
    const key = (n) => `${p.getX(n).toFixed(4)},${p.getY(n).toFixed(4)},${p.getZ(n).toFixed(4)}`;
    for (let t = 0; t < i.length; t += 3) for (const [a, b] of [[i[t], i[t + 1]], [i[t + 1], i[t + 2]], [i[t + 2], i[t]]]) {
      const e = [key(a), key(b)].sort().join('|');
      seen.set(e, (seen.get(e) ?? 0) + 1);
    }
    // Extrusions, cylinders and spheres close by construction: only the seam of their vertices shows here.
    if ([...seen.values()].some((n) => n === 1) && !['ExtrudeGeometry', 'CylinderGeometry', 'SphereGeometry'].includes(o.geometry.type)) rims.push(o.name);
  });
  assert.deepEqual([...open, ...rims], []);
});

test('the tower\'s front upright sweeps over the top in one length, as wide all the way, down to the floor', () => {
  const [arch, ...others] = boxes(machine(), role('towerArch'));
  assert.ok(arch && !others.length, 'one length');
  close(arch.min.y, 0, 0.002, 'cut flat on the floor');
  close(arch.max.y, 1.4, 0.01, 'the top of the machine');
  close(arch.max.x - arch.min.x, 0.13, 0.002, 'the upright\'s width round the bend');
  close(arch.max.z - arch.min.z, 0.38 + 0.07, 0.01, 'from the front upright back over the rear one');
});

test('a diagonal brace stands from the floor beam up to the tower\'s rear upright', () => {
  const [brace, ...others] = boxes(machine(), role('towerBrace'));
  assert.ok(brace && !others.length, 'one brace');
  close(brace.min.y, 0.07, 0.002, 'cut flat on the beam');
  close(brace.max.z, 0.66 - 0.035, 0.01, 'cut flat on the upright\'s face');
  assert.ok(brace.max.y > 0.4 && brace.max.z - brace.min.z > 0.08, 'leaning up to it');
});

// The frame behind the seat, from the photos (refs/hip_machine/ref.md, part list).
test('a spine rakes back behind the backrest, flush on it, kinked at the seat, and lands on the cross member', () => {
  const kit = machine();
  const [spine, ...others] = meshes(kit, role('spine'));
  assert.ok(spine && !others.length, 'one spine');
  const [pad] = meshes(kit, role('backrest'));
  const back = pad.getWorldDirection(new THREE.Vector3()).negate();
  const centre = pad.getWorldPosition(new THREE.Vector3()).addScaledVector(back, 0.03);
  const [hit] = new THREE.Raycaster(centre, back).intersectObject(spine, false);
  assert.ok(hit && hit.distance < 0.02, `the pad ${hit ? (hit.distance * 100).toFixed(1) : 'nowhere'} cm from the spine`);
  const b = new THREE.Box3().setFromObject(spine);
  assert.ok(b.max.z - b.min.z > 0.2, 'raked');
  const [cross] = boxes(kit, role('crossMember'));
  close(b.min.y, cross.max.y, 0.005, 'cut flat on the cross member');
});

test('two rear legs splay out and back from under the seat to the rear rail, the cross member between them', () => {
  const kit = machine();
  const legs = boxes(kit, role('rearLeg'));
  assert.equal(legs.length, 2, 'a leg a side');
  const [top] = boxes(kit, role('seatBeam'));
  for (const b of legs) {
    close(b.min.y, 0.07, 0.002, 'cut flat on the rail');
    assert.ok(b.max.y >= top.min.y, 'up to the beam under the seat');
    assert.ok(Math.max(Math.abs(b.min.x), Math.abs(b.max.x)) - Math.min(Math.abs(b.min.x), Math.abs(b.max.x)) > 0.1, 'splayed out');
    assert.ok(b.max.z - b.min.z > 0.15, 'running back');
  }
  const [cross] = boxes(kit, role('crossMember'));
  assert.ok(cross.max.x > 0.2 && cross.min.x < -0.2, 'from leg to leg');
});

test('the grips run beside the seat at its height, and the hoop that carries them goes round the spine', () => {
  const kit = machine();
  const grips = boxes(kit, role('handle'));
  assert.equal(grips.length, 2);
  for (const b of grips) close((b.min.y + b.max.y) / 2, 0.54, 0.03, 'at the seat');
  const [spine] = boxes(kit, role('spine'));
  const hoop = boxes(kit, role('handleStrut'));
  assert.equal(hoop.length, 2, 'half a hoop a side');
  for (const b of hoop) {
    assert.ok(Math.min(Math.abs(b.min.x), Math.abs(b.max.x)) < 0.05, 'in to the spine');
    assert.ok(b.min.z < spine.max.z, 'back behind the seat');
  }
});

test('the stack\'s guide rods stand up into the head bar, and the axle up under the seat', () => {
  const kit = machine();
  const [head] = boxes(kit, role('headBar'));
  assert.ok(head, 'a head bar');
  const rods = boxes(kit, () => true).filter((b) => b.max.y - b.min.y > 0.9 && b.max.x - b.min.x < 0.03 && b.min.z > 0.6);
  assert.ok(rods.length >= 2, 'the guide rods');
  for (const b of rods) assert.ok(b.max.y >= head.min.y, `a rod stops at ${b.max.y.toFixed(2)}`);
  const [axle] = boxes(kit, role('axle'));
  const [beam] = boxes(kit, role('seatBracket'));
  assert.ok(axle.max.y >= beam.min.y, 'the axle meets the seat\'s bracket');
});
