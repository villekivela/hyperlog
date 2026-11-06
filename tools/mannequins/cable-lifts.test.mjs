// The library's last cable exercises (#127), on kits other lifts built. What each asks of the body
// beyond its kit's first pose is in its own test.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import * as THREE from 'three';
import { prepare } from './entries.mjs';
import { buildStage } from './stage.mjs';
import { CHECKS, checkStage } from './checks.mjs';
import { heldAt, wpos } from './rig.mjs';

const posed = async (id) => {
  const stage = await buildStage(prepare(id));
  const at = (d) => {
    stage.pose(d);
    stage.scene.updateMatrixWorld(true);
    return (name) => wpos(stage.scene.getObjectByName(name));
  };
  return { stage, at };
};
const failing = (id, stage) => {
  const checks = checkStage(prepare(id), stage);
  return CHECKS.filter((c) => !checks[c].ok).map((c) => `${c}: ${checks[c].found}`);
};

test('the wide-grip pulldown holds the bar outside the plain pulldown, from long arms to the chest', async () => {
  const { stage, at } = await posed('lat_pulldown_wide_grip');
  assert.deepEqual(failing('lat_pulldown_wide_grip', stage), []);
  const plain = await posed('lat_pulldown');
  const wide = at(1)('hand_l').x;
  const narrow = plain.at(1)('hand_l').x;
  assert.ok(wide > narrow + 0.02, `the hand ${wide.toFixed(3)} out, the plain pulldown's ${narrow.toFixed(3)}`);
  const held = heldAt(stage.rig, 'l').x;
  assert.ok(held > 0.3 && held < 0.46, `the hand at ${held.toFixed(3)} on the bar: on its sleeve, not its bent tip`);
  const top = at(0);
  assert.ok(top('hand_l').y > top('Head').y + 0.15, `the hands ${top('hand_l').y.toFixed(3)} up at the top, over the head`);
  const reach = top('upperarm_l').sub(top('lowerarm_l')).angleTo(top('hand_l').sub(top('lowerarm_l'))) * 180 / Math.PI;
  assert.ok(reach > 160, `the elbow ${reach.toFixed(0)}° at the top: full range, the arms long`);
  const bottom = at(1);
  assert.ok(bottom('hand_l').y < bottom('neck_01').y + 0.05, `the hands ${bottom('hand_l').y.toFixed(3)} at the bottom, down to the chest`);
});

test('the neutral-grip pulldown holds a close handle, palms facing, the elbows driving down by the sides', async () => {
  const { stage, at } = await posed('neutral_grip_lat_pulldown');
  assert.deepEqual(failing('neutral_grip_lat_pulldown', stage), []);
  // Two grips running front to back, each in its own fist, palms facing: a grip across the body would
  // lie along the fingers.
  assert.equal(prepare('neutral_grip_lat_pulldown').kit.cable_tower.attachment, 'handles');
  const bottom = at(1);
  // Where the hands hold, not the wrists, which a palm's thickness puts further out.
  const apart = heldAt(stage.rig, 'l').x - heldAt(stage.rig, 'r').x;
  assert.ok(apart < 0.2, `the hands ${apart.toFixed(3)} apart on the handle`);
  const out = bottom('lowerarm_l').x - bottom('upperarm_l').x;
  assert.ok(Math.abs(out) < 0.1, `the elbow ${out.toFixed(3)} out from the shoulder: down by the sides`);
  assert.ok(bottom('lowerarm_l').y < bottom('upperarm_l').y - 0.15, 'the elbow below the shoulder at the bottom');
  const ahead = bottom('lowerarm_l').z - bottom('upperarm_l').z;
  // Further back the forearm meets the head or the grip opens.
  assert.ok(ahead < 0.16, `the elbow ${ahead.toFixed(3)} ahead of the shoulder at the bottom: down by the ribs`);
});

test('the rope hammer curl curls thumbs up, the elbows held at the sides', async () => {
  const { stage, at } = await posed('cable_rope_hammer_curl');
  assert.deepEqual(failing('cable_rope_hammer_curl', stage), []);
  const low = at(1);
  const [elbowLow, handLow] = [low('lowerarm_l').clone(), low('hand_l').clone()];
  const up = at(0);
  const [elbowUp, handUp] = [up('lowerarm_l').clone(), up('hand_l').clone()];
  assert.ok(elbowUp.distanceTo(elbowLow) < 0.06, `the elbow moves ${elbowUp.distanceTo(elbowLow).toFixed(3)}`);
  assert.ok(handUp.y - handLow.y > 0.3, `the hand rises ${(handUp.y - handLow.y).toFixed(3)}`);
  assert.ok(handUp.y > elbowUp.y + 0.1, 'the hand ends above the elbow');
  // The rope runs up through each fist to its stop above the thumb, clear of the thighs.
  assert.equal(prepare('cable_rope_hammer_curl').kit.cable_crossover.reversed, true);
  // And the hands finish in front of the thighs, the rope clear of them.
  at(1);
  const front = heldAt(stage.rig, 'l').z;
  assert.ok(front > 0.17, `the hand ${front.toFixed(3)} forward at the bottom: in front of the thigh`);
  // Thumbs up: the knuckles run up and down, not across, through the rep.
  for (const d of [0, 0.5, 1]) {
    const g = at(d);
    const knuckles = g('index_01_l').sub(g('pinky_01_l')).normalize();
    assert.ok(Math.abs(knuckles.x) < 0.6, `the knuckles ${knuckles.x.toFixed(2)} across at ${d}: palms facing, not up`);
  }
});

test('the 1-arm pulldown kneels on one knee and drives the elbow from long overhead down to the side', async () => {
  const { stage, at } = await posed('1_arm_cable_pulldown');
  assert.deepEqual(failing('1_arm_cable_pulldown', stage), []);
  const top = at(0);
  assert.ok(top('calf_l').y < 0.12, `the working side's knee ${top('calf_l').y.toFixed(3)} up`);
  const brace = top('middle_01_r').y - top('calf_r').y;
  assert.ok(brace < 0.13, `the free hand ${brace.toFixed(3)} above the front knee's joint: resting on it, not hovering`);
  assert.ok(top('hand_l').y > top('Head').y, `the hand ${top('hand_l').y.toFixed(3)} at the top, over the head`);
  const reach = top('upperarm_l').sub(top('lowerarm_l')).angleTo(top('hand_l').sub(top('lowerarm_l'))) * 180 / Math.PI;
  assert.ok(reach > 145, `the elbow ${reach.toFixed(0)}° at the top: the arm long`);
  const bottom = at(1);
  const drop = bottom('upperarm_l').y - bottom('lowerarm_l').y;
  assert.ok(drop > 0.2, `the elbow ${drop.toFixed(3)} below the shoulder at the bottom`);
  assert.ok(bottom('hand_l').y < bottom('upperarm_l').y, 'the hand down below the shoulder');
});

test('the cable lat pullover sweeps a rope down on long arms, kneeling, the elbows hardly bending', async () => {
  const { stage, at } = await posed('cable_lat_pullover');
  assert.deepEqual(failing('cable_lat_pullover', stage), []);
  const elbow = (g) => g('upperarm_l').sub(g('lowerarm_l')).angleTo(g('hand_l').sub(g('lowerarm_l'))) * 180 / Math.PI;
  const angles = [0, 0.25, 0.5, 0.75, 1].map((d) => elbow(at(d)));
  assert.ok(Math.min(...angles) > 150, `the elbow bends to ${Math.min(...angles).toFixed(0)}°: a pullover, not a pulldown`);
  // A rope, its two ends free to pass either side of the raised knee where a bar would run through it.
  assert.equal(prepare('cable_lat_pullover').kit.cable_crossover.attachment, 'rope');
  const top = at(0);
  const high = top('hand_l').y;
  assert.ok(top('calf_r').y < 0.12, `the right knee ${top('calf_r').y.toFixed(3)} up`);
  assert.ok(high > top('upperarm_l').y, 'the hands over the shoulders at the top');
  const low = at(1)('hand_l').y;
  assert.ok(high - low > 0.45, `the hands sweep ${(high - low).toFixed(3)} down`);
  // The trunk rocks from well forward to near upright, and the arms finish down by the hips.
  const lean = (g) => {
    const up = g('neck_01').sub(g('pelvis'));
    return Math.atan2(up.z, up.y) * 180 / Math.PI;
  };
  const end = at(1);
  const swing = lean(at(0)) - lean(at(1));
  assert.ok(swing > 25, `the trunk rocks ${swing.toFixed(0)}°`);
  const arm = end('upperarm_l').sub(end('lowerarm_l')).angleTo(end('upperarm_l').sub(end('pelvis'))) * 180 / Math.PI;
  assert.ok(arm < 48, `the upper arm ${arm.toFixed(0)}° off the trunk at the finish: by the hip`);
  // The arms pass beside the raised thigh, the rope's ends spread, and finish by the hips.
  const finish = at(1);
  const beside = finish('hand_l').x - finish('calf_l').x;
  assert.ok(beside > 0.08, `the hand ${beside.toFixed(3)} outside the front knee at the finish`);
  // The rope rides over the knee. A stretched strand has vertices only at its ends, so it's sampled
  // along its centre line and held off the leg's bone.
  const near = new THREE.Vector3();
  for (const d of [0.7, 0.8, 0.9, 1]) {
    const g = at(d);
    const leg = new THREE.Line3(g('thigh_l').clone(), g('calf_l').clone());
    stage.kit.group.traverse((m) => {
      if (!m.isMesh) return;
      m.geometry.computeBoundingBox();
      const { min, max } = m.geometry.boundingBox;
      const size = max.clone().sub(min);
      const axis = ['x', 'y', 'z'].reduce((a, b) => (size[b] > size[a] ? b : a));
      for (let t = 0; t <= 1; t += 0.02) {
        const p = min.clone().add(max).multiplyScalar(0.5);
        p[axis] = min[axis] + t * size[axis];
        p.applyMatrix4(m.matrixWorld);
        const gap = leg.closestPointToPoint(p, true, near).distanceTo(p);
        assert.ok(gap > 0.075, `${m.name} ${(gap * 100).toFixed(1)} cm from the front thigh's bone at ${d}`);
      }
    });
  }
  const start = at(0);
  assert.ok(start('hand_l').y < start('Head').y + 0.2, `the hand ${start('hand_l').y.toFixed(3)} at the top, the head ${start('Head').y.toFixed(3)}`);
});

test('the 1-arm lat pull-in kneels on both knees and draws the long arm in and down to the side', async () => {
  const { stage, at } = await posed('1_arm_lat_pull_in');
  assert.deepEqual(failing('1_arm_lat_pull_in', stage), []);
  const top = at(0);
  for (const k of ['l', 'r']) assert.ok(top(`calf_${k}`).y < 0.12, `the ${k} knee ${top(`calf_${k}`).y.toFixed(3)} up`);
  const out = top('hand_l').x - top('upperarm_l').x;
  assert.ok(out > 0.35, `the hand ${out.toFixed(3)} out from the shoulder at the top`);
  const long = top('upperarm_l').sub(top('lowerarm_l')).angleTo(top('hand_l').sub(top('lowerarm_l'))) * 180 / Math.PI;
  assert.ok(long > 145, `the elbow ${long.toFixed(0)}° at the top: the arm long`);
  const bottom = at(1);
  const tucked = bottom('lowerarm_l').x - bottom('upperarm_l').x;
  assert.ok(tucked < 0.12, `the elbow ${tucked.toFixed(3)} out at the bottom: in at the side`);
  assert.ok(bottom('upperarm_l').y - bottom('lowerarm_l').y > 0.2, 'the elbow well below the shoulder');
});

test('the cable lat pull-around reaches across the body and drives the elbow down to the hip', async () => {
  const { stage, at } = await posed('1_arm_cable_lat_pull_around');
  assert.deepEqual(failing('1_arm_cable_lat_pull_around', stage), []);
  const top = at(0);
  assert.ok(top('calf_l').y < 0.12, `the working side's knee ${top('calf_l').y.toFixed(3)} up`);
  const brace = top('middle_01_r').y - top('calf_r').y;
  assert.ok(brace < 0.13, `the free hand ${brace.toFixed(3)} above the front knee's joint: resting on it, not hovering`);
  assert.ok(top('hand_l').x < top('upperarm_r').x - 0.05, `the left hand ${top('hand_l').x.toFixed(3)} at the top: past the far shoulder, ${top('upperarm_r').x.toFixed(3)}`);
  const across = top('upperarm_l').sub(top('upperarm_r'));
  const turned = Math.atan2(across.z, across.x) * 180 / Math.PI;
  assert.ok(Math.abs(turned) > 15, `the shoulders turned ${turned.toFixed(0)}° toward the cable`);
  assert.ok(top('hand_l').y > top('upperarm_l').y, 'the hand above the shoulder at the top');
  const bottom = at(1);
  assert.ok(bottom('upperarm_l').y - bottom('lowerarm_l').y > 0.2, 'the elbow well below the shoulder at the bottom');
  const hip = bottom('lowerarm_l').distanceTo(bottom('thigh_l'));
  assert.ok(hip < 0.45, `the elbow ${hip.toFixed(3)} from the hip`);
  const grip = heldAt(stage.rig, 'l').x;
  assert.ok(Math.abs(grip) < 0.14, `the hand ${grip.toFixed(3)} at the finish: at the sternum`);
});
