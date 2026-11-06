// The library's last machine exercises (#127), on kits the other lifts built. What each asks of the
// body beyond its machine's first pose is in its own test.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import * as THREE from 'three';
import { prepare } from './entries.mjs';
import { buildStage, FRAMES } from './stage.mjs';
import { CHECKS, bodyVertices, checkStage } from './checks.mjs';
import { skinInto } from './skin-into.mjs';
import { wpos } from './rig.mjs';

const posed = async (id) => {
  const stage = await buildStage(prepare(id));
  const at = (d) => {
    stage.pose(d);
    stage.scene.updateMatrixWorld(true);
    return (name) => wpos(stage.scene.getObjectByName(name));
  };
  return { stage, at };
};
const frames = Array.from({ length: FRAMES }, (_, f) => f / (FRAMES - 1));
const bend = (a, b, c) => (a.clone().sub(b).angleTo(c.clone().sub(b)) * 180) / Math.PI;
/** Every part of the kit keeps above the floor, at both ends of the rep. */
const aboveFloor = (stage) => {
  const box = new THREE.Box3();
  for (const d of [0, 0.5, 1]) {
    stage.pose(d);
    stage.scene.updateMatrixWorld(true);
    stage.kit.group.traverse((m) => {
      if (!m.isMesh) return;
      box.setFromObject(m);
      assert.ok(box.min.y > -0.005, `${m.name} ${(-box.min.y * 100).toFixed(1)} cm under the floor at ${d}`);
    });
  }
};
const failing = (id, stage) => {
  const checks = checkStage(prepare(id), stage);
  return CHECKS.filter((c) => !checks[c].ok).map((c) => `${c}: ${checks[c].found}`);
};

test('the 1-leg leg press drives one foot on the plate, the other flat on the floor beside the seat', async () => {
  const { stage, at } = await posed('single_leg_leg_press');
  assert.deepEqual(failing('single_leg_leg_press', stage), []);
  const rest = frames.map((d) => at(d)('foot_r').clone());
  for (const p of rest) {
    assert.ok(p.y < 0.13, `the resting ankle ${p.y.toFixed(3)} up`);
    assert.ok(p.distanceTo(rest[0]) < 0.002, 'the resting foot stays where it is');
  }
  const [top, bottom] = [at(0)('foot_l').clone(), at(1)('foot_l').clone()];
  assert.ok(top.distanceTo(bottom) > 0.2, `the plate foot travels ${top.distanceTo(bottom).toFixed(3)}`);
  assert.ok(top.y > 0.8, `the plate foot ${top.y.toFixed(3)} up`);
  const g = at(0);
  const knee = bend(g('thigh_l'), g('calf_l'), g('foot_l'));
  assert.ok(knee < 165, `the working knee ${knee.toFixed(0)}° at the top, short of lockout`);
  // A bar running across can pass between the thigh's vertices: it's held off the bone itself.
  const thigh = new THREE.Line3(g('thigh_r').clone(), g('calf_r').clone());
  const on = new THREE.Vector3();
  const box = new THREE.Box3();
  stage.kit.group.traverse((m) => {
    if (!m.isMesh) return;
    box.setFromObject(m);
    if (box.max.x - box.min.x < 0.3 || box.max.y - box.min.y > 0.1 || box.max.z - box.min.z > 0.1) return;
    const middle = box.getCenter(new THREE.Vector3());
    for (let x = box.min.x; x <= box.max.x; x += 0.01) {
      const p = middle.clone().setX(x);
      const gap = thigh.closestPointToPoint(p, true, on).distanceTo(p);
      assert.ok(gap > 0.1, `${m.name} ${(gap * 100).toFixed(1)} cm from the resting thigh's bone`);
    }
  });
  aboveFloor(stage);
  // The working knee comes up by the arm: the arm's skin stays out of the leg's on every frame.
  const { depth, frame, bone } = await skinInto('single_leg_leg_press', 'l', /^(upperarm|lowerarm)_l$/);
  assert.ok(depth <= 0, `${bone} ${(depth * 100).toFixed(1)} cm into the working leg at frame ${frame}`);
});

test('the leg press calf press rolls the ankles on the balls of the feet, the knees near straight', async () => {
  const { stage, at } = await posed('leg_press_calf_press');
  assert.deepEqual(failing('leg_press_calf_press', stage), []);
  const knees = [];
  const ankles = [];
  for (const d of frames) {
    const g = at(d);
    knees.push(bend(g('thigh_l'), g('calf_l'), g('foot_l')));
    ankles.push(bend(g('calf_l'), g('foot_l'), g('ball_l')));
    const P = stage.pose(d);
    const off = g('ball_l').sub(P.plate).dot(P.sled);
    assert.ok(Math.abs(off + 0.045) < 0.03, `the ball ${(off * 100).toFixed(1)} cm off the plate at ${d}`);
  }
  assert.ok(Math.min(...knees) > 150, `the knee bends to ${Math.min(...knees).toFixed(0)}°`);
  assert.ok(Math.max(...knees) - Math.min(...knees) < 12, 'the knees hold their bend');
  assert.ok(Math.max(...ankles) - Math.min(...ankles) > 30, `the ankle moves ${(Math.max(...ankles) - Math.min(...ankles)).toFixed(0)}°`);
  // A heel dropping off the plate needs nothing across the way: no rod of the sled through a foot.
  const feet = bodyVertices(stage.rig).filter((v) => /^(foot|ball)_/.test(v.bone));
  const box = new THREE.Box3();
  for (const d of [0, 0.5, 1]) {
    stage.pose(d);
    stage.scene.updateMatrixWorld(true);
    const skin = feet.map((v) => v.mesh.getVertexPosition(v.i, new THREE.Vector3()).applyMatrix4(v.mesh.matrixWorld));
    stage.kit.group.traverse((m) => {
      if (!m.isMesh || m.geometry.type !== 'CylinderGeometry') return;
      box.setFromObject(m);
      // Only a rod running across the body: its own box is then tight round it.
      if (box.max.x - box.min.x < 0.2) return;
      assert.ok(!skin.some((p) => box.containsPoint(p)), `${m.name} through a foot at ${d}`);
    });
    // Nor does the sole sink into the plate as the heel drops past its edge.
    const P = stage.pose(d);
    stage.scene.updateMatrixWorld(true);
    const under = skin.filter((p) => {
      const at_ = p.clone().sub(P.plate);
      return at_.dot(P.sled) > 0.005 && at_.dot(P.plateUp) > -0.26;
    });
    assert.equal(under.length, 0, `${under.length} of the sole's vertices in the plate at ${d}`);
  }
  aboveFloor(stage);
});

test('the incline machine press drives the hands up and forward, square to a trunk lying well back', async () => {
  const { stage, at } = await posed('incline_machine_press');
  assert.deepEqual(failing('incline_machine_press', stage), []);
  const entry = prepare('incline_machine_press');
  const out = at(0)('hand_l').clone();
  const stretch = at(1);
  const chest = stretch('hand_l').clone();
  const trunk = stretch('neck_01').sub(stretch('pelvis'));
  // The hands sink to the shoulders' line, the elbows below them, the forearms stacked under the
  // handles: how far each is down the trunk, shoulders to hips.
  const shoulders = stretch('upperarm_l').add(stretch('upperarm_r')).multiplyScalar(0.5);
  const down = stretch('pelvis').sub(shoulders);
  const along = (p) => p.clone().sub(shoulders).dot(down) / down.lengthSq();
  assert.ok(Math.abs(along(chest)) < 0.12, `the hands ${(along(chest) * 100).toFixed(0)}% down the trunk at the bottom`);
  assert.ok(along(stretch('lowerarm_l')) > along(chest) + 0.15, `the elbow ${(along(stretch('lowerarm_l')) * 100).toFixed(0)}% down the trunk, the hands ${(along(chest) * 100).toFixed(0)}%`);
  assert.ok(trunk.z < -0.5 * trunk.y, `the trunk ${trunk.y.toFixed(2)} up, ${trunk.z.toFixed(2)} back: an incline, not a seat`);
  const rise = out.y - chest.y;
  const reach = out.z - chest.z;
  assert.ok(rise > 0.15, `the hands rise ${rise.toFixed(3)}`);
  assert.ok(reach > 0.1, `the hands reach ${reach.toFixed(3)} forward`);
  const g = at(0);
  const elbow = bend(g('upperarm_l'), g('lowerarm_l'), g('hand_l'));
  assert.ok(elbow > 145, `the elbow ${elbow.toFixed(0)}° at the top: the press finishes with the arms long`);
  assert.equal(entry.motion, 'chest_press');
  // The brace under the boom reaches the boom, however short the boom is here.
  const named = [];
  stage.kit.group.traverse((m) => { if (m.isMesh) named.push(m); });
  const box = new THREE.Box3();
  const beams = named.map((m) => box.setFromObject(m).clone());
  const boom = beams.find((b) => b.max.y > 1.8 && b.max.z - b.min.z > 0.08 && b.max.x - b.min.x < 0.12);
  assert.ok(boom, 'a boom');
  const braces = beams.filter((b) => b.max.y > 1.5 && b.max.y < boom.min.y + 0.06 && b.max.x - b.min.x < 0.1 && b !== boom);
  for (const b of braces) assert.ok(b.max.z <= boom.max.z + 0.01, `a brace to z ${b.max.z.toFixed(3)}, past the boom's end ${boom.max.z.toFixed(3)}`);
  aboveFloor(stage);
});

test('the machine row drives the elbows out and back at 45°, as its note says', async () => {
  const { stage, at } = await posed('machine_row');
  assert.deepEqual(failing('machine_row', stage), []);
  const start = at(0);
  const low = start('upperarm_l').y - start('hand_l').y;
  assert.ok(low > 0.2, `the hands start ${low.toFixed(3)} below the shoulders: the arms reach down to low handles`);
  const long = bend(start('upperarm_l'), start('lowerarm_l'), start('hand_l'));
  assert.ok(long > 140, `the elbow ${long.toFixed(0)}° at the start: the arms reach long for the stretch`);
  const squeeze = at(1);
  const [elbow, shoulder] = [squeeze('lowerarm_l'), squeeze('upperarm_l')];
  const [out, back] = [elbow.x - shoulder.x, shoulder.z - elbow.z];
  assert.ok(back > 0, `the elbow ${back.toFixed(3)} behind the shoulder at the squeeze`);
  const angle = (Math.atan2(out, back) * 180) / Math.PI;
  assert.ok(angle > 35 && angle < 55, `the elbow ${angle.toFixed(0)}° out from straight back`);
  const drop = shoulder.y - elbow.y;
  assert.ok(drop > 0.1, `the elbow ${drop.toFixed(3)} below the shoulder at the squeeze, not a high row`);
  aboveFloor(stage);
});
