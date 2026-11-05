// A person can use the hip thrust machine (#229): the machine hip thrust is posed on it, and through
// the whole rep the hips ride on the seat and the belt, the upper back lies on the pads, the feet
// stand flat on the platform, both hands hold the handles, the carriage follows the body through its
// range, and nothing passes through the body.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import * as THREE from 'three';
import { buildStage, FRAMES } from './stage.mjs';
import { prepare } from './entries.mjs';
import { checkPose } from './checks.mjs';
import { BATCHES } from './review.mjs';
import { heldAt, wpos } from './rig.mjs';

const ID = 'machine_hip_thrust';
const close = (a, b, within, why) => assert.ok(Math.abs(a - b) <= within, `${why}: ${a.toFixed(3)}, not ${b} ± ${within}`);
const stage = await buildStage(prepare(ID));
const kit = stage.kit.kits.hip_thrust_machine;
const rig = stage.rig;
const contact = (point) => kit.contacts.find((c) => c.point === point);
/** The body's vertices, each with the bone that moves it most. */
const vertices = [];
for (const m of rig.meshes) {
  const { skinIndex, skinWeight, position } = m.geometry.attributes;
  for (let i = 0; i < position.count; i++) {
    let best = 0;
    for (let j = 1; j < 4; j++) if (skinWeight.getComponent(i, j) > skinWeight.getComponent(i, best)) best = j;
    vertices.push({ m, i, bone: m.skeleton.bones[skinIndex.getComponent(i, best)].name });
  }
}
const skin = (bones) => vertices.filter((v) => bones.test(v.bone)).map((v) => v.m.getVertexPosition(v.i, new THREE.Vector3()).applyMatrix4(v.m.matrixWorld));
const DS = Array.from({ length: 7 }, (_, i) => i / 6);

test('the exercise is posed on the hip thrust machine, in its own review batch', () => {
  assert.deepEqual(BATCHES['hip-thrust'], [ID]);
  assert.ok(kit, 'the pose stands on hip_thrust_machine');
});

test('every automatic check passes on every frame of the rep', async () => {
  const { checks } = await checkPose(ID);
  for (const [name, c] of Object.entries(checks)) assert.ok(c.ok, `${name}: ${c.found} (frame ${c.frame})`);
});

test('the hips sit on the seat at the bottom and the thighs come level at the top', () => {
  stage.pose(1);
  const seatTop = 0.5;
  const pelvis = skin(/^(pelvis|thigh_)/).filter((p) => p.z < 0.14 && p.z > -0.25);
  const lowest = Math.min(...pelvis.map((p) => p.y));
  assert.ok(lowest > seatTop - 0.01 && lowest < seatTop + 0.03, `the lowest skin at the bottom is ${lowest.toFixed(3)} m, the seat's top ${seatTop} m`);
  stage.pose(0);
  const hip = wpos(rig.bones.thigh_l);
  const knee = wpos(rig.bones.calf_l);
  const ankle = wpos(rig.bones.foot_l);
  close(hip.y, 0.93, 0.03, 'the hip joint at the top');
  close(knee.y, hip.y, 0.07, 'the knee level with the hip at the top: the thigh level');
  close(knee.z, ankle.z, 0.15, 'the shin near upright at the top');
});

test('the upper back lies on a pad all through the rep, never more than 2 cm into it or off it', () => {
  for (const d of DS) {
    stage.pose(d);
    const c = contact('pad').at[0];
    const back = skin(/^spine_0[23]$/).filter((p) => Math.abs(p.x) > 0.03 && Math.abs(p.x) < 0.28);
    const gap = Math.min(...back.map((p) => Math.hypot(p.y - c.y, p.z - c.z) - 0.13));
    assert.ok(gap > -0.02 && gap < 0.02, `at ${d.toFixed(2)} of the rep the back is ${(gap * 100).toFixed(1)} cm from the pad`);
  }
});

test('both feet stand flat on the platform, ankle and ball at their standing heights, all through the rep', () => {
  const top = stage.rig.rest.foot_l.p.y;
  const ball = stage.rig.rest.ball_l.p.y;
  const platform = contact('platform').at[0];
  const normal = new THREE.Vector3(0, Math.cos(Math.PI / 12), -Math.sin(Math.PI / 12));
  for (const d of DS) {
    stage.pose(d);
    for (const k of ['l', 'r']) {
      const above = (n) => wpos(rig.bones[`${n}_${k}`]).sub(platform).dot(normal);
      close(above('foot'), top, 0.02, `the ${k} ankle above the platform at ${d.toFixed(2)}`);
      close(above('ball'), ball, 0.02, `the ${k} ball above the platform at ${d.toFixed(2)}`);
    }
  }
});

test('both hands close on the handles all through the rep, which stand upright beside the hips', () => {
  for (const d of DS) {
    stage.pose(d);
    for (const k of ['l', 'r']) {
      const miss = Math.min(...contact('handles').at.map((p) => p.distanceTo(heldAt(rig, k))));
      assert.ok(miss < 0.015, `the ${k} hand ${(miss * 100).toFixed(1)} cm off a handle at ${d.toFixed(2)}`);
    }
    const [a, b] = contact('handles').at;
    assert.ok(Math.abs(a.x) > 0.2 && Math.abs(b.x) > 0.2 && a.x * b.x < 0, 'a handle each side of the body');
  }
});

test('the carriage follows the body: it turns from its stop at the bottom to its top limit, the hips rising with it', () => {
  const { limits } = kit.joints.carriage;
  let last = Infinity;
  let lastHip = Infinity;
  for (let f = 0; f < FRAMES; f++) {
    stage.pose(f / (FRAMES - 1));
    const { value } = kit.joints.carriage;
    const hip = wpos(rig.bones.thigh_l).y;
    assert.ok(value <= last + 1e-9, 'the carriage turns one way down the rep');
    assert.ok(hip <= lastHip + 1e-3, 'the hips sink one way down the rep');
    [last, lastHip] = [value, hip];
  }
  close(last, limits[0], 1e-9, 'on its stop at the bottom');
  stage.pose(0);
  close(kit.joints.carriage.value, limits[1], 1e-9, 'at its top limit at the top');
  // The belt's roll rides the pelvis: it is on the carriage, so it stands where the hips are.
  const roll = [];
  kit.group.traverse((o) => { if (o.isMesh && o.userData.role === 'strap') roll.push(new THREE.Box3().setFromObject(o).getCenter(new THREE.Vector3())); });
  assert.ok(roll[0].distanceTo(wpos(rig.bones.pelvis)) < 0.3, 'the belt over the hips at the top');
});

test('nothing passes through the body: no skin lies inside the carriage\'s arms or any other part the checks take for thin', () => {
  // The checks take a solid part for its box, which a bent arm's is far wider than; so here each
  // part that isn't a solid is tested as the mesh it is: a skin vertex inside it (an odd number of
  // surface crossings along +x) passes through it.
  const parts = [];
  kit.group.traverse((o) => { if (o.isMesh && !o.userData.solid && o.userData.role !== 'strap') parts.push(new THREE.Mesh(o.geometry, new THREE.MeshBasicMaterial({ side: THREE.DoubleSide }))); });
  assert.ok(parts.length > 4, 'the arms and the other thin parts were found');
  const original = [];
  kit.group.traverse((o) => { if (o.isMesh && !o.userData.solid && o.userData.role !== 'strap') original.push(o); });
  const ray = new THREE.Raycaster();
  const body = skin(/./);
  for (const d of DS) {
    stage.pose(d);
    kit.group.updateMatrixWorld(true);
    original.forEach((o, i) => { parts[i].matrixWorld.copy(o.matrixWorld); parts[i].matrixWorldAutoUpdate = false; });
    parts.forEach((m, i) => {
      const box = new THREE.Box3().setFromObject(original[i]);
      for (const p of skin(/./)) {
        if (!box.containsPoint(p)) continue;
        ray.set(p, new THREE.Vector3(1, 0, 0));
        const hits = ray.intersectObject(m, false).length;
        assert.equal(hits % 2, 0, `skin inside ${original[i].name} at ${d.toFixed(2)} of the rep`);
      }
    });
  }
  assert.ok(body.length > 1000);
});

// The belt is soft: padding laid over the hips that gives under the body, so the checks don't take
// it for a solid. It must still lie on the body: no more than 3 cm of the skin in it, and no more
// than 3 cm between it and the hips.
test('the belt lies on the hips all through the rep, giving under the body by no more than 3 cm', () => {
  const [band] = (() => { const out = []; kit.group.traverse((o) => { if (o.isMesh && o.userData.role === 'strap') out.push(o); }); return out; })();
  band.geometry.computeBoundingBox();
  const half = band.geometry.boundingBox.getSize(new THREE.Vector3()).multiplyScalar(0.5);
  const body = vertices.filter((v) => /^(pelvis|spine_01|thigh_)/.test(v.bone));
  for (const d of DS) {
    stage.pose(d);
    kit.group.updateMatrixWorld(true);
    const inverse = band.matrixWorld.clone().invert();
    let deepest = 0;
    let gap = Infinity;
    for (const v of body) {
      const p = v.m.getVertexPosition(v.i, new THREE.Vector3()).applyMatrix4(v.m.matrixWorld).applyMatrix4(inverse);
      if (Math.abs(p.x) > half.x) continue;
      const q = [half.x - Math.abs(p.x), half.y - Math.abs(p.y), half.z - Math.abs(p.z)];
      if (Math.min(...q) > 0) deepest = Math.max(deepest, Math.min(...q));
      else gap = Math.min(gap, Math.hypot(...q.map((x) => Math.max(0, -x))));
    }
    assert.ok(deepest < 0.03, `the skin ${(deepest * 100).toFixed(1)} cm into the belt at ${d.toFixed(2)} of the rep`);
    assert.ok(deepest > 0 || gap < 0.03, `the belt ${(gap * 100).toFixed(1)} cm off the hips at ${d.toFixed(2)} of the rep`);
  }
});
