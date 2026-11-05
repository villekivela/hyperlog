// The lateral raise machine (#212), from refs/lateral_raise_machine/ref.md: two arm levers turning
// about the shoulders' axes, a seat that moves to the pivots, a chest pad and one weight stack.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import path from 'node:path';
import * as THREE from 'three';
import { KITS, MACHINES, buildKit } from './kit.mjs';
import { MODEL, buildStage, parseMannequin } from './stage.mjs';
import { V, buildRig, rad, wpos } from './rig.mjs';
import { checkPose } from './checks.mjs';
import { prepare } from './entries.mjs';
import { openClip, poseFrame } from './glb.mjs';
import { CLIPS } from './bake.mjs';

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

test('each arm turns about a front-to-back axis 1.15 m up, from 10° out of hanging to horizontal', () => {
  const kit = machine();
  for (const [k, s] of SIDES) {
    const joint = kit.joints[`arm_${k}`];
    assert.equal(joint.type, 'revolute');
    close(deg(joint.limits[0]), 10, 1e-9, `${k}'s lower limit, in degrees`);
    close(deg(joint.limits[1]), 90, 1e-9, `${k}'s upper limit, in degrees`);
    close(joint.value, joint.limits[0], 1e-9, `${k} unposed, at rest`);
    close(joint.origin.y, 1.15, 1e-9, `${k}'s pivot height`);
    // On the mannequin's shoulders (0.192 m out), where the ref lets the recipe set the spacing.
    close(joint.origin.x, s * 0.192, 0.005, `${k}'s pivot, out from the middle`);
    close(Math.abs(joint.axis.z), 1, 1e-9, `${k}'s axis, front to back`);
  }
});

test('the pads hang about 320 mm from the pivots at rest and ride up to horizontal, out at the sides', () => {
  const kit = machine();
  for (const [k, s] of SIDES) {
    const { origin } = kit.joints[`arm_${k}`];
    const [rest] = contact(kit, `pad_${k}`).at;
    // The pad's middle at the elbow: 300 mm along the arm from the pivot, a hand's breadth out of its line.
    close(Math.hypot(rest.x - origin.x, rest.y - origin.y), 0.32, 0.02, `${k}'s pad from the pivot, in the levers' plane`);
    assert.ok(rest.y < origin.y - 0.25, `${k}'s pad at y ${rest.y.toFixed(2)}, not below the pivot at rest`);
    kit.scrub(`arm_${k}`, rad(90));
    const [top] = contact(kit, `pad_${k}`).at;
    close(top.y, origin.y + 0.1, 0.03, `${k}'s pad at the top, on top of the arm`);
    assert.ok(s * (top.x - origin.x) > 0.25, `${k}'s pad ${(s * (top.x - origin.x)).toFixed(2)} m out at the top`);
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

test('the hands hold a grip ahead of each pad, the arms lie on the pads, the chest on its pad', () => {
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
    assert.equal(contact(kit, `pad_${k}`).landmark, 'forearms');
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
  // The pivots at 1.15 m put the mannequin's seat at 39% of the tower, as the demo has it.
  close(seat, 0.55, 0.02, 'the seat top, in metres');
  assert.ok(seat >= 0.44 && seat <= 0.62, `the seat at ${seat}, outside its 0.44 to 0.62 range`);
});

test('the kit is the machine of both lateral raises, with no brand in the recipe', async () => {
  assert.deepEqual(MACHINES.lateral_raise_machine, { kit: 'lateral_raise_machine', exercises: ['machine_lateral_raise', 'standing_machine_lateral_raise'] });
  const source = await readFile(new URL('./recipes/lateral-raise-machine.mjs', import.meta.url), 'utf8');
  assert.doesNotMatch(source, /hammer|life ?fitness|atlantis|strength/i);
});

const boxes = (kit) => {
  const out = [];
  kit.group.updateMatrixWorld(true);
  kit.group.traverse((o) => { if (o.isMesh) out.push({ mesh: o, box: new THREE.Box3().setFromObject(o, true) }); });
  return out;
};
/** The boxes of arm `k`'s stand-ins for its bent tube: the strut and the beam. */
const coresOf = (kit, k) => {
  const cores = [];
  kit.group.updateMatrixWorld(true);
  kit.bodies[`arm_${k}`].forEach((n) => n.traverse((o) => { if (o.userData.proxy) cores.push(new THREE.Box3().setFromObject(o, true)); }));
  return cores;
};
/** Everything of the pulleys' tone, the block's plates and wheels. */
const blockParts = (kit) => boxes(kit).filter(({ mesh }) => mesh.material?.name === 'pulley' && !mesh.userData.solid);
/** Arm `k`'s wedge, as it stands now. */
const wedgeOf = (kit, k) => {
  let box = null;
  kit.group.updateMatrixWorld(true);
  kit.bodies[`arm_${k}`].forEach((n) => n.traverse((o) => { if (/^lateral_raise_wedge/.test(o.name)) box = new THREE.Box3().setFromObject(o, true); }));
  return box;
};
/** The plate behind the wedges: a wide flat panel high on the machine. */
const plateOf = (kit) => boxes(kit).find(({ box: b }) => b.max.x - b.min.x > b.max.y - b.min.y + 0.02 && b.max.y - b.min.y > 0.2 && b.max.z - b.min.z < 0.05 && b.min.y > 0.8 && b.max.z < 0.7)?.box;
const stackOf = (kit) => {
  let found = null;
  kit.group.traverse((o) => { if (o.userData.stack) found = o; });
  return new THREE.Box3().setFromObject(found, true);
};

// The ref's footprint is 940 by 1070 mm. The hero photo and the demo at 0:08 put the tower ahead of
// the lifter, its broad face across the machine, its near end about on the centre line.
test('the base is 1070 mm long and within 940 mm wide, the tower at its far end, ahead of the lifter and to the right', () => {
  const kit = machine();
  const floor = new THREE.Box3();
  for (const { box: b } of boxes(kit)) if (b.min.y < 0.1) floor.union(b);
  close(floor.max.z - floor.min.z, 1.07, 0.03, 'the length');
  const b = new THREE.Box3().setFromObject(kit.group, true);
  close(b.max.x - b.min.x, 0.94, 0.05, 'the width with the arms at rest');
  const stack = stackOf(kit).getCenter(new THREE.Vector3());
  assert.ok(stack.z > 0.75, `the stack ${stack.z.toFixed(2)} m ahead of the hips, past the arms' reach`);
  assert.ok(stack.x < -0.1 && stack.x > -0.35, `the stack ${stack.x.toFixed(2)} m to the side`);
  const size = stackOf(kit).getSize(new THREE.Vector3());
  assert.ok(size.x > 2 * size.z, 'its broad face across the machine');
});

test('nothing fixed stands in the arms\' way, from rest to level', () => {
  const kit = machine();
  const own = new Set();
  [...kit.bodies.arm_l, ...kit.bodies.arm_r].forEach((n) => n.traverse((o) => own.add(o)));
  const fixed = boxes(kit).filter(({ mesh }) => mesh.userData.solid && !own.has(mesh));
  for (const v of [10, 50, 90]) {
    kit.scrub('arm_l', rad(v));
    kit.scrub('arm_r', rad(v));
    for (const point of ['grip_l', 'grip_r', 'pad_l', 'pad_r']) {
      const p = contact(kit, point).at[0];
      for (const { mesh, box } of fixed) assert.ok(box.distanceToPoint(p) > 0.03, `${point} meets ${mesh.name} at ${v}°`);
    }
  }
});

test('one post leaning forward from the floor carries the seat, the chest pad and the pivots', () => {
  const kit = machine();
  const post = boxes(kit).find(({ box: b }) => b.min.y < 0.12 && b.max.y > 1 && b.max.x - b.min.x < 0.12);
  assert.ok(post, 'a post from the floor to the pivots');
  const lean = post.box.max.z - post.box.min.z;
  assert.ok(lean > 0.12 && lean < 0.4, `it leans ${lean.toFixed(2)} m forward over its height`);
  assert.ok(post.box.max.z <= contact(kit, 'grip_l').at[0].z + 0.11, 'and ends behind the pivots\' plate');
});

test('a beam ties the post to the tower at about mid-height', () => {
  const kit = machine();
  const beam = boxes(kit).find(({ box: b }) => b.min.y > 0.7 && b.max.y < 1 && b.max.z - b.min.z > 0.4);
  assert.ok(beam, 'a beam along the machine');
});

// The hero photo: the grip hangs inboard of the pad from a bracket at its front end, in line with the
// upper arm, so the forearm lies level with the upper arm at the top.
test('each grip stands on the arm\'s own line, inboard of its pad, at the elbow, about 255 mm from the pivot', () => {
  const kit = machine();
  for (const [k, s] of SIDES) {
    const { origin } = kit.joints[`arm_${k}`];
    for (const v of [10, 50, 90]) {
      kit.scrub(`arm_${k}`, rad(v));
      const g = contact(kit, `grip_${k}`).at[0];
      const p = contact(kit, `pad_${k}`).at[0];
      close(Math.hypot(g.x - origin.x, g.y - origin.y), 0.255, 0.01, `${k}'s grip from the pivot at ${v}°`);
      close(Math.atan2(s * (g.x - origin.x), origin.y - g.y), rad(v), 0.01, `${k}'s grip on the arm's line at ${v}°`);
      close(Math.hypot(g.x - p.x, g.y - p.y), Math.hypot(0.115, 0.045), 0.01, `${k}'s pad 115 mm out of the grip's line at ${v}°`);
    }
  }
});

// The hero photo and the demo at 0:08: each lever runs on past its pivot into a wedge, the highest
// thing on the lifter's side, and swings down out of the way as the arms rise.
test('each lever carries a wedge up past its pivot, which swings down as the arm rises', () => {
  const kit = machine();
  const wedgeTop = (k) => Math.max(...kit.bodies[`arm_${k}`].flatMap((n) => {
    const out = [];
    n.traverse((o) => { if (o.isMesh) out.push(new THREE.Box3().setFromObject(o, true).max.y); });
    return out;
  }));
  for (const [k] of SIDES) {
    const y = kit.joints[`arm_${k}`].origin.y;
    assert.ok(wedgeTop(k) > y + 0.15, `${k}'s lever tops out ${(wedgeTop(k) - y).toFixed(2)} m over its pivot at rest`);
    const rest = wedgeOf(kit, k).getCenter(new THREE.Vector3()).y;
    kit.scrub(`arm_${k}`, rad(90));
    assert.ok(wedgeOf(kit, k).getCenter(new THREE.Vector3()).y < rest - 0.1, `${k}'s wedge swings down`);
  }
});

test('the lever is a heavy strut flaring about 40° from the pivot, with a beam along a pad thicker than the arm', () => {
  const kit = machine();
  for (const [k, s] of SIDES) {
    const { origin } = kit.joints[`arm_${k}`];
    const moving = boxes(kit).filter(({ mesh }) => kit.bodies[`arm_${k}`].includes(mesh));
    const reach = Math.max(...moving.map(({ box: b }) => (s > 0 ? b.max.x - origin.x : origin.x - b.min.x)));
    assert.ok(reach > 0.27, `${k}'s lever reaches ${reach.toFixed(2)} m out at rest`);
    const pad = contact(kit, `pad_${k}`).at[0];
    const round = moving.filter(({ box: b }) => b.distanceToPoint(pad) < 1e-3 && b.max.z - b.min.z > 0.25 && b.max.y - b.min.y < 0.2);
    assert.ok(round.length, `a pad at ${k}'s pad point`);
    assert.ok(round.some(({ box: b }) => b.max.y - b.min.y > 0.13), `${k}'s pad at least 130 mm through`);
  }
});

test('a posed stack is held just off its rest at the stretch, and rises about 240 mm', async () => {
  const { pose, kit } = await buildStage(prepare('machine_lateral_raise'));
  const rest = 0.57;
  pose(0);
  const low = stackOf(kit.kits.lateral_raise_machine).max.y;
  close(low - rest, 0.06, 0.005, 'held off its rest at the stretch');
  pose(1);
  close(stackOf(kit.kits.lateral_raise_machine).max.y - low, 0.24, 0.03, 'its rise');
});

test('the wedges run fore and aft ahead of the plate, a valley between them, clear of the lifter\'s face', async () => {
  const { pose, rig, kit } = await buildStage(prepare('machine_lateral_raise'));
  const machineKit = kit.kits.lateral_raise_machine;
  pose(0);
  const [l, r] = ['l', 'r'].map((k) => wedgeOf(machineKit, k));
  assert.ok(l.max.z - l.min.z > 0.2, 'long fore and aft');
  assert.ok(l.min.x > 0.02 && r.max.x < -0.02, `a valley on the midline: ${l.min.x.toFixed(2)}, ${r.max.x.toFixed(2)}`);
  const face = wpos(rig.bones.Head).z + 0.15;
  assert.ok(l.min.z > face, `ahead of the face, at z ${l.min.z.toFixed(2)} against ${face.toFixed(2)}`);
});

// The hero photo: the pad's top stands just under the pivots' bar.
test('the chest pad bears on the chest: its top just under the pivots, level with the sternum', async () => {
  const { pose, rig, kit } = await buildStage(prepare('machine_lateral_raise'));
  const machineKit = kit.kits.lateral_raise_machine;
  pose(0);
  const pad = contact(machineKit, 'chestPad').at[0];
  const chest = wpos(rig.bones.spine_03);
  assert.ok(pad.y + 0.15 > 1, `the pad's top at ${(pad.y + 0.15).toFixed(2)} m`);
  assert.ok(pad.y + 0.15 < machineKit.joints.arm_l.origin.y, 'and under the pivots');
  close(pad.y, chest.y, 0.1, 'its middle level with the chest');
});

test('the wedges stand in a tight V, their middles about half the pivots\' spacing apart', () => {
  const kit = machine();
  const middleOf = (k) => wedgeOf(kit, k).getCenter(new THREE.Vector3());
  const apart = middleOf('l').x - middleOf('r').x;
  const spacing = kit.joints.arm_l.origin.x - kit.joints.arm_r.origin.x;
  assert.ok(apart < 0.65 * spacing, `the wedges ${apart.toFixed(2)} m apart against the pivots' ${spacing.toFixed(2)}`);
});

test('from the lifter\'s side a plate stands in front of the drum', () => {
  const kit = machine();
  const plate = plateOf(kit);
  const drum = boxes(kit).find(({ box: b }) => Math.abs((b.max.x - b.min.x) - (b.max.y - b.min.y)) < 0.01 && b.max.x - b.min.x > 0.3);
  assert.ok(plate && drum, 'a plate and a drum');
  assert.ok(plate.max.z <= drum.box.min.z + 0.005, 'the plate on the lifter\'s side of the drum');
});

// The demo at 0:08 and the technique video at 0:02: the thighs slope down from a high seat, the
// knees below the hips, as the shares of the tower's height say: seat 39%, pivots 82%.
test('the seat and the pivots stand high enough that the thighs slope down', async () => {
  const { pose, rig, kit } = await buildStage(prepare('machine_lateral_raise'));
  pose(0);
  const drop = wpos(rig.bones.thigh_l).y - wpos(rig.bones.calf_l).y;
  assert.ok(drop > 0.08, `the knee ${drop.toFixed(2)} m below the hip`);
  close(kit.kits.lateral_raise_machine.joints.arm_l.origin.y / 1.4, 0.82, 0.015, 'the pivots\' share of the tower');
});

test('the wedges stand over the plate, on the lifter\'s side of it, and it stands no higher than they do', () => {
  const kit = machine();
  const plate = plateOf(kit);
  const w = wedgeOf(kit, 'l');
  assert.ok(w.max.y > plate.max.y + 0.05, `the wedge tops out ${(w.max.y - plate.max.y).toFixed(2)} m over the plate`);
  assert.ok(w.max.z <= plate.min.z + 0.005, 'on the lifter\'s side of it, so it drops in front of it as the arm rises');
  assert.ok(w.max.z - w.min.z > 2.2 * (w.max.x - w.min.x), 'a long slab, about 2.5 to 1, not a block');
  assert.ok(w.max.z - w.min.z > 0.3, 'about as long as an arm pad');
  close(w.max.y - kit.joints.arm_l.origin.y, 0.2, 0.03, 'its top over the pivot, close over it as the demo has it');
});

test('the beam runs the pad\'s whole length, tight against it', () => {
  const kit = machine();
  for (const [k] of SIDES) {
    const arm = boxes(kit).filter(({ mesh }) => kit.bodies[`arm_${k}`].includes(mesh));
    const pad = contact(kit, `pad_${k}`).at[0];
    const back = Math.min(...arm.filter(({ box: b }) => b.max.z - b.min.z > 0.3).map(({ box: b }) => b.min.z));
    assert.ok(back < pad.z - 0.1, `${k}'s beam back to z ${back.toFixed(2)}, the pad's middle at ${pad.z.toFixed(2)}`);
    const beam = coresOf(kit, k).reduce((a, b) => (b.max.z - b.min.z > a.max.z - a.min.z ? b : a));
    assert.ok(beam.distanceToPoint(pad) < 0.1, `${k}'s beam ${beam.distanceToPoint(pad).toFixed(2)} m from the pad's middle: tight against it`);
  }
});

// The checks test the body against what is solid, so all of each lever has to be.
test('the whole lever is solid to the body: strut, beam, bracket and wedge', () => {
  const kit = machine();
  for (const [k] of SIDES) {
    const solid = [];
    kit.bodies[`arm_${k}`].forEach((n) => n.traverse((o) => { if (o.isMesh && o.userData.solid) solid.push(new THREE.Box3().setFromObject(o, true)); }));
    const { origin } = kit.joints[`arm_${k}`];
    assert.ok(solid.some((b) => b.max.z - b.min.z > 0.3 && b.max.y > origin.y), `${k}'s wedge`);
    assert.ok(solid.some((b) => b.max.z - b.min.z > 0.3 && b.max.y <= origin.y && b.max.x - b.min.x < 0.1 && b.max.y - b.min.y < 0.1), `${k}'s beam`);
    assert.ok(solid.some((b) => b.max.y - b.min.y > 0.2 && b.max.x - b.min.x > 0.15), `${k}'s strut`);
    assert.ok(kit.bodies[`arm_${k}`].some((n) => n.name === `lateral_raise_bracket_${k}` && n.userData.solid), `${k}'s bracket`);
  }
});

// The hero photo: a block of two pulleys stands between the post and the tower, the cable up from it
// to the levers and on to the stack.
test('a pulley block stands between the post and the tower, on the centre line, clear of the legs', () => {
  const kit = machine();
  const wheels = blockParts(kit);
  assert.ok(wheels.length >= 2, 'its pulleys');
  for (const { box: b } of wheels) {
    const c = b.getCenter(new THREE.Vector3());
    assert.ok(Math.abs(c.x) < 0.06, `on the centre line, at x ${c.x.toFixed(2)}`);
    assert.ok(c.z > 0.3 && c.z < 0.7 && c.y < 0.8, `between the post and the tower, at z ${c.z.toFixed(2)}`);
  }
});

/** The pulley block as it stands now. */
const blockOf = (kit) => blockParts(kit).reduce((all, { box: b }) => all.union(b), new THREE.Box3());

test('the pulley block is a broad bowtie out in the gap, from the seat\'s height down, its cables straight up and down', () => {
  const kit = machine();
  const block = blockOf(kit);
  assert.ok(block.max.x - block.min.x > 0.14, 'broad, facing along the machine');
  assert.ok(block.max.y < 0.65 && block.min.y < 0.32, `from ${block.max.y.toFixed(2)} m down to ${block.min.y.toFixed(2)} m`);
  const middle = block.getCenter(new THREE.Vector3()).z;
  assert.ok(middle > 0.38 && middle < 0.6, `out in the gap, at z ${middle.toFixed(2)}`);
  const cables = boxes(kit).filter(({ mesh, box: b }) => mesh.material?.name === 'metal' && b.max.x - b.min.x < 0.02 && b.max.z - b.min.z < 0.02 && Math.abs(b.getCenter(new THREE.Vector3()).z - middle) < 0.02);
  assert.ok(cables.some(({ box: b }) => b.max.y > 0.95), 'a cable straight up toward the levers');
  assert.ok(cables.some(({ box: b }) => b.min.y < 0.1), 'and one straight down to the base');
  const leaning = boxes(kit).filter(({ mesh, box: b }) => mesh.material?.name === 'metal' && b.max.z - b.min.z > 0.3 && b.max.y - b.min.y > 0.1 && b.max.y < 0.9);
  assert.deepEqual(leaning.map(({ mesh }) => mesh.name), [], 'no cable across the open air');
});

test('the block rises with the rep, its cables following it', async () => {
  const { pose, kit } = await buildStage(prepare('machine_lateral_raise'));
  const blockY = () => blockOf(kit.kits.lateral_raise_machine).getCenter(new THREE.Vector3()).y;
  // Each cable's low end, by its box: the one to the base's top end, the ones to the levers' bottom.
  const ends = () => {
    const wires = boxes(kit.kits.lateral_raise_machine).filter(({ mesh, box: b }) => mesh.material?.name === 'metal' && b.max.x - b.min.x < 0.02 && b.max.z - b.min.z < 0.02 && b.max.y - b.min.y > 0.1 && b.min.z > 0.4 && b.max.z < 0.5);
    return { up: wires.find(({ box: b }) => b.max.y > 0.9).box.min.y, down: wires.find(({ box: b }) => b.min.y < 0.1).box.max.y };
  };
  pose(0);
  const low = blockY();
  const start = ends();
  pose(1);
  const rise = blockY() - low;
  close(rise, 0.12, 0.03, 'its rise, half the stack\'s');
  close(ends().up - start.up, rise, 0.005, 'the cable up from it follows');
  close(ends().down - start.down, rise, 0.005, 'and the cable down from it');
});

// The hero photo and the demo at 0:08: the wedge sits close over the pivot, and the lever rakes
// toward the tower as it comes down.
test('the lever rakes toward the tower as it comes down, its wedge close over the pivot', () => {
  const kit = machine();
  for (const [k] of SIDES) {
    const { origin } = kit.joints[`arm_${k}`];
    // Measured on the strut itself: the lever's own box takes in the wedge it carries.
    const reach = Math.max(...coresOf(kit, k).map((b) => b.max.z));
    assert.ok(reach > origin.z + 0.14 && reach < origin.z + 0.22, `${k}'s lever reaches ${(reach - origin.z).toFixed(2)} m toward the tower from its pivot`);
    assert.ok(wedgeOf(kit, k).min.y < origin.y + 0.1, `${k}'s wedge starts ${(wedgeOf(kit, k).min.y - origin.y).toFixed(2)} m over the pivot`);
  }
});

test('the pads are long, and the bare beam ahead of them is shorter than they are', () => {
  const kit = machine();
  for (const [k] of SIDES) {
    const pad = boxes(kit).filter(({ mesh }) => kit.bodies[`arm_${k}`].includes(mesh)).find(({ box: b }) => b.distanceToPoint(contact(kit, `pad_${k}`).at[0]) < 1e-3 && b.max.z - b.min.z < 0.5 && b.max.y - b.min.y < 0.2).box;
    const length = pad.max.z - pad.min.z;
    assert.ok(length > 0.34, `${k}'s pad ${length.toFixed(2)} m long`);
    const bare = Math.max(...coresOf(kit, k).map((b) => b.max.z)) - pad.max.z;
    assert.ok(bare < 0.8 * length, `${(bare / length).toFixed(2)} of a pad's length of bare beam ahead of it`);
  }
});

// The hero photo: the tower is one arch, its outer top corner swept round.
test('the tower\'s outer top corner is swept round, and its top stands level with the machine\'s', () => {
  const kit = machine();
  const stack = stackOf(kit).getCenter(new THREE.Vector3());
  const tower = boxes(kit).filter(({ box: b }) => b.max.y > 1.25 && b.min.z > stack.z - 0.1).reduce((all, { box: b }) => all.union(b), new THREE.Box3());
  assert.ok(tower.max.x - tower.min.x > 0.48, `the tower ${(tower.max.x - tower.min.x).toFixed(2)} m across`);
  close(tower.max.y, 1.4, 0.04, 'its top');
  const corner = V(tower.min.x + 0.02, tower.max.y - 0.02, stack.z);
  // The arch's own box takes in the corner it sweeps round.
  const filled = boxes(kit).filter(({ mesh, box: b }) => mesh.name !== 'lateral_raise_arch' && b.containsPoint(corner));
  assert.deepEqual(filled.map(({ mesh }) => mesh.name), [], 'nothing square in the corner');
  const arch = kit.group.getObjectByName('lateral_raise_arch');
  const skin = arch.geometry.attributes.position;
  const v = new THREE.Vector3();
  let nearest = Infinity;
  for (let i = 0; i < skin.count; i++) nearest = Math.min(nearest, v.fromBufferAttribute(skin, i).applyMatrix4(arch.matrixWorld).distanceTo(corner));
  assert.ok(nearest > 0.05, `the arch's skin ${(nearest * 100).toFixed(1)} cm from the square corner`);
});

// The ref: a valley between the wedges. They swing inward as the arms rise, so it has to hold all the way.
test('the wedges never meet, and clear the plate\'s stub, through the whole sweep', () => {
  const kit = machine();
  const stub = boxes(kit).find(({ box: b }) => b.max.x - b.min.x < 0.1 && b.min.y > 0.9 && b.max.y < 1.15 && b.max.z - b.min.z > 0.2).box;
  for (let v = 10; v <= 90; v += 10) {
    kit.scrub('arm_l', rad(v));
    kit.scrub('arm_r', rad(v));
    const [l, r] = [wedgeOf(kit, 'l'), wedgeOf(kit, 'r')];
    assert.ok(l.min.x >= r.max.x - 0.002, `the wedges overlap by ${((r.max.x - l.min.x) * 100).toFixed(1)} cm at ${v}°`);
    assert.ok(!l.intersectsBox(stub) && !r.intersectsBox(stub), `a wedge meets the plate's stub at ${v}°`);
  }
});

test('the cables run clear of the beam', () => {
  const kit = machine();
  const beam = boxes(kit).find(({ box: b }) => b.min.y > 0.7 && b.max.y < 1 && b.max.z - b.min.z > 0.4).box;
  const up = boxes(kit).filter(({ mesh, box: b }) => mesh.material?.name === 'metal' && b.max.x - b.min.x < 0.02 && b.max.z - b.min.z < 0.02 && b.max.y > 0.9 && b.min.z > 0.4 && b.max.z < 0.5);
  assert.ok(up.length, 'a cable up past the beam');
  for (const { box: b } of up) assert.ok(Math.abs(b.getCenter(new THREE.Vector3()).x) > 0.04, `a cable at x ${b.getCenter(new THREE.Vector3()).x.toFixed(3)} runs through the beam`);
  assert.ok(beam, 'the beam');
});

// The owner, in the studio: the grips are fixed to the arms and don't turn on them. The hand and the
// wrist follow.
test('each grip is rigid on its arm: it turns with the lever, and the bracket joins the arm to its end', () => {
  const kit = machine();
  const gripOf = (k) => kit.bodies[`arm_${k}`].find((n) => n.userData.held);
  const lineOf = (k) => {
    const g = gripOf(k);
    g.updateWorldMatrix(true, false);
    return g.localToWorld(new THREE.Vector3(0, 0.5, 0)).sub(g.localToWorld(new THREE.Vector3(0, -0.5, 0))).normalize();
  };
  for (const [k] of SIDES) {
    const at = (v) => { kit.scrub(`arm_${k}`, rad(v)); return lineOf(k); };
    const [low, high] = [at(10), at(90)];
    close(low.angleTo(high), rad(80), rad(0.5), `${k}'s grip turns with its lever`);
    assert.ok(Math.abs(low.y) < 0.9 && Math.abs(high.y) < 0.9, 'and its line stays clear of straight up, where the arm solve changes basis');
    const bracket = kit.bodies[`arm_${k}`].find((n) => n.name === `lateral_raise_bracket_${k}`);
    const end = bracket.localToWorld(new THREE.Vector3(0, 0.5, 0));
    const other = bracket.localToWorld(new THREE.Vector3(0, -0.5, 0));
    const g = gripOf(k);
    const tips = [0.5, -0.5].map((y) => g.localToWorld(new THREE.Vector3(0, y, 0)));
    const gap = Math.min(...tips.flatMap((t) => [t.distanceTo(end), t.distanceTo(other)]));
    assert.ok(gap < 0.01, `${k}'s bracket ${(gap * 100).toFixed(1)} cm from its grip's end`);
  }
  assert.equal(kit.group.getObjectByName('lateral_raise_stem_l'), undefined, 'no stem to pivot on');
});

// The owner's bar (#241): no open tube end, and a post cut flat on what it stands on.
test('no tube on it stands open, and the post stands flat on the spine', () => {
  const kit = machine();
  const rims = [];
  kit.group.traverse((o) => {
    if (!o.isMesh || !o.geometry.index) return;
    const seen = new Map();
    const i = o.geometry.index.array;
    const p = o.geometry.attributes.position;
    const key = (n) => `${p.getX(n).toFixed(4)},${p.getY(n).toFixed(4)},${p.getZ(n).toFixed(4)}`;
    for (let t = 0; t < i.length; t += 3) for (const [a, b] of [[i[t], i[t + 1]], [i[t + 1], i[t + 2]], [i[t + 2], i[t]]]) {
      const e = [key(a), key(b)].sort().join('|');
      seen.set(e, (seen.get(e) ?? 0) + 1);
    }
    if ([...seen.values()].some((n) => n === 1) && o.geometry.type !== 'ExtrudeGeometry') rims.push(o.name);
  });
  assert.equal(kit.group.getObjectByName('lateral_raise_arch')?.geometry.type === 'TubeGeometry', false);
  assert.ok(!rims.includes('lateral_raise_arch'), 'the arch is capped');
  const post = boxes(kit).find(({ mesh, box: b }) => !mesh.userData.solid && b.min.y < 0.12 && b.max.y > 1 && b.max.x - b.min.x < 0.12).box;
  close(post.min.y, 0.07, 0.002, 'the post\'s foot flat on the spine\'s top');
});

// #241: a body fits it. The checks hold the hands on the grips, the upper arms on the pads, the chest
// on its pad, the thighs on the seat and nothing through the body, on every frame of the rep.
const fails = async (id) => Object.entries((await checkPose(id)).checks).filter(([, c]) => !c.ok).map(([k, c]) => `${k}: ${c.found} (frame ${c.frame})`);

test('machine_lateral_raise: the body fits the machine through the whole rep', async () => {
  assert.deepEqual(await fails('machine_lateral_raise'), []);
});

test('machine_lateral_raise: the upper arms rise from by the sides to level, the elbows bent about 90°, the shoulders on the pivots', async () => {
  const { pose, rig, kit } = await buildStage(prepare('machine_lateral_raise'));
  const joint = kit.kits.lateral_raise_machine.joints;
  const armAt = () => {
    const [shoulder, elbow, wrist] = ['upperarm_l', 'lowerarm_l', 'hand_l'].map((b) => wpos(rig.bones[b]));
    return { shoulder, elbow, bend: elbow.clone().sub(shoulder).angleTo(wrist.clone().sub(elbow)) };
  };
  pose(0);
  const low = armAt();
  assert.ok(low.shoulder.y - low.elbow.y > 0.2, 'the upper arm hangs at the stretch');
  assert.ok(Math.abs(low.shoulder.y - joint.arm_l.origin.y) < 0.03, 'the shoulder level with the pivot');
  pose(1);
  const high = armAt();
  assert.ok(Math.abs(high.shoulder.y - high.elbow.y) < 0.04, `the elbow ${(high.elbow.y - high.shoulder.y).toFixed(2)} m off the shoulder's height at the top`);
  for (const { bend } of [low, high]) assert.ok(bend > rad(60) && bend < rad(115), `the elbow bent ${(bend * 180 / Math.PI).toFixed(0)}°`);
});

test('machine_lateral_raise: each lever turns with its joint on every part of the rep, and neither is left behind', async () => {
  const { pose, kit } = await buildStage(prepare('machine_lateral_raise'));
  const machineKit = kit.kits.lateral_raise_machine;
  for (const d of [0, 0.5, 1]) {
    pose(d);
    for (const [k, s] of SIDES) {
      const lever = machineKit.bodies[`arm_${k}`].find((n) => n.children.length);
      const turned = 2 * Math.atan2(lever.quaternion.z, lever.quaternion.w) * s;
      close(turned, machineKit.joints[`arm_${k}`].value - rad(10), 1e-6, `${k}'s lever ${d} of the way`);
    }
  }
});

test('machine_lateral_raise: the hips sit on the seat through the rep, neither above it nor sunk in', async () => {
  const { pose, rig, kit } = await buildStage(prepare('machine_lateral_raise'));
  const seatTop = () => contact(kit.kits.lateral_raise_machine, 'seatTop').at[0].y + 0.0375;
  for (const d of [0, 0.5, 1]) {
    pose(d);
    // The pelvis bone stands 72 mm over the cushion's top, the hips' skin on it.
    close(wpos(rig.bones.pelvis).y - seatTop(), 0.072, 0.015, `the hips over the seat, ${d} of the way`);
  }
  const hip = wpos(rig.bones.pelvis);
  const seat = contact(kit.kits.lateral_raise_machine, 'seatTop').at[0];
  close(hip.z, seat.z, 0.1, 'and along it');
});

// The checks test the skin's vertices against each part, and a thin rod can run through a limb
// between them, so the bracket is held clear of the arm's bones.
test('machine_lateral_raise: each grip\'s bracket stays clear of the arm through the rep', async () => {
  const { pose, rig, kit } = await buildStage(prepare('machine_lateral_raise'));
  const brackets = {};
  kit.group.traverse((o) => { const m = /^lateral_raise_bracket_(.)/.exec(o.name); if (m) brackets[m[1]] = o; });
  for (let d = 0; d <= 1; d += 0.1) {
    pose(d);
    for (const [k] of SIDES) {
      const [a, b] = [-0.5, 0.5].map((y) => brackets[k].localToWorld(new THREE.Vector3(0, y, 0)));
      const along = new THREE.Line3(a, b);
      const bones = ['upperarm', 'lowerarm', 'hand'].map((n) => wpos(rig.bones[`${n}_${k}`]));
      for (const limb of [new THREE.Line3(bones[0], bones[1]), new THREE.Line3(bones[1], bones[2])]) {
        // Its foot is at the grip, where the hand is meant to be.
        for (let t = 0; t <= 0.8; t += 0.1) {
          const p = along.at(t, new THREE.Vector3());
          const q = limb.closestPointToPoint(p, true, new THREE.Vector3());
          assert.ok(p.distanceTo(q) > 0.1, `${k}'s bracket ${(p.distanceTo(q) * 100).toFixed(1)} cm from the arm's bone, ${d.toFixed(1)} of the way`);
        }
      }
    }
  }
});

// The posed stage isn't what ships. A part whose name a track can't bind to stays at its rest in the
// clip, so the clip's own nodes are held to the pose, at the middle and the end of the rep.
test('machine_lateral_raise: the baked clip shows every part of the kit where the pose has it', async () => {
  const { pose, kit } = await buildStage(prepare('machine_lateral_raise'));
  const { scene, tracks } = await openClip(path.join(CLIPS, 'machine_lateral_raise.glb'));
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
      const at = baked.getWorldPosition(new THREE.Vector3());
      const want = o.getWorldPosition(new THREE.Vector3());
      assert.ok(at.distanceTo(want) < 0.005, `${o.name} ${(at.distanceTo(want) * 100).toFixed(1)} cm off the pose at frame ${f}`);
    }
  }
});
