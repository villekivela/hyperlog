// The lateral raise machine (#212), from refs/lateral_raise_machine/ref.md: two arm levers turning
// about the shoulders' axes, a seat that moves to the pivots, a chest pad and one weight stack.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import * as THREE from 'three';
import { KITS, MACHINES, buildKit } from './kit.mjs';
import { MODEL, buildStage, parseMannequin } from './stage.mjs';
import { buildRig, rad, wpos } from './rig.mjs';
import { checkPose } from './checks.mjs';
import { prepare } from './entries.mjs';

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
  const own = new Set([...kit.bodies.arm_l, ...kit.bodies.arm_r]);
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
      close(Math.hypot(g.x - p.x, g.y - p.y), Math.hypot(0.11, 0.045), 0.01, `${k}'s pad 110 mm out of the grip's line at ${v}°`);
    }
  }
});

test('the empty machine\'s stack sits down as one block', () => {
  close(stackOf(machine()).max.y, 0.57, 0.002, 'the top plate at rest');
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
    kit.scrub(`arm_${k}`, rad(90));
    kit.group.updateMatrixWorld(true);
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
  const wedges = () => ['arm_l', 'arm_r'].map((k) => {
    const found = [];
    machineKit.bodies[k].forEach((n) => n.traverse((o) => { if (o.isMesh && o.parent === n && n !== o) found.push(new THREE.Box3().setFromObject(o, true)); }));
    return found[0];
  });
  pose(0);
  const [l, r] = wedges();
  assert.ok(l.max.z - l.min.z > 0.2, 'long fore and aft');
  assert.ok(l.min.x > 0.02 && r.max.x < -0.02, `a valley on the midline: ${l.min.x.toFixed(2)}, ${r.max.x.toFixed(2)}`);
  const face = wpos(rig.bones.Head).z + 0.15;
  assert.ok(l.min.z > face, `ahead of the face, at z ${l.min.z.toFixed(2)} against ${face.toFixed(2)}`);
});

// The hero photo: the pad's top stands just under the pivots' plate and it overlaps the plate's panel.
test('the chest pad bears on the chest: its top just under the plate, level with the sternum', async () => {
  const { pose, rig, kit } = await buildStage(prepare('machine_lateral_raise'));
  const machineKit = kit.kits.lateral_raise_machine;
  pose(0);
  const pad = contact(machineKit, 'chestPad').at[0];
  const chest = wpos(rig.bones.spine_03);
  assert.ok(pad.y + 0.15 > 1, `the pad's top at ${(pad.y + 0.15).toFixed(2)} m`);
  assert.ok(pad.y + 0.15 < machineKit.joints.arm_l.origin.y, 'and under the pivots');
  close(pad.y, chest.y, 0.1, 'its middle level with the chest');
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
