// The roman chair crunch on the captain's chair (#237, refs/captains_chair/ref.md, video 0:00, 0:03, 0:06):
// a knee raise. The forearms lie on the arm pads, the hands close on the grips, the back leans on the
// back pad, and the legs hang free and swing up. The chair has no seat and no step in use, and nothing on
// it moves, so there are no hips on a seat or feet on a floor to hold: the feet hang clear of the floor.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import * as THREE from 'three';
import { STATIONS, shownAlone } from './kit.mjs';
import { buildStage } from './stage.mjs';
import { prepare } from './entries.mjs';
import { checkPose } from './checks.mjs';
import { between, wpos } from './rig.mjs';
import { BATCHES } from './review.mjs';
import { tempoOf } from './tempo.mjs';
import { tunables } from './tune.mjs';

const ID = 'roman_chair_crunch';
const degrees = (r) => (r * 180) / Math.PI;
const close = (a, b, within, why) => assert.ok(Math.abs(a - b) <= within, `${why}: ${a.toFixed(3)}, not ${b} ± ${within}`);
const FRAMES = [0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1];
const hipFlexion = (rig, k) => {
  const trunk = between(rig, 'upperarm_l', 'upperarm_r').sub(between(rig, 'thigh_l', 'thigh_r')).negate();
  return degrees(wpos(rig.bones[`calf_${k}`]).sub(wpos(rig.bones[`thigh_${k}`])).angleTo(trunk));
};

test('it is posed on the chair, and passes every check on every frame', async () => {
  assert.equal(prepare(ID).kit.captains_chair !== undefined, true);
  const { checks } = await checkPose(ID);
  for (const [name, c] of Object.entries(checks)) assert.ok(c.ok, `${name} on frame ${c.frame}: ${c.found}`);
});

test('both hands stay closed on the grips, the forearms on the arm pads and the back on the back pad, start to end of the rep', async () => {
  const { checks } = await checkPose(ID);
  // The contact check reads each contact of the kit: armPads (forearms), grips (hands), backPad (back).
  for (const name of ['hands', 'contact', 'linkage']) assert.ok(checks[name]?.ok, `${name}: ${checks[name]?.found}`);
  const { rig, kit, pose } = await buildStage(prepare(ID));
  const [pads, grips] = ['armPads', 'grips'].map((p) => kit.kits.captains_chair.contacts.find((c) => c.point === p).at);
  for (const t of FRAMES) {
    pose(t);
    for (const [i, k] of ['l', 'r'].entries()) {
      const hand = wpos(rig.bones[`hand_${k}`]);
      const nearest = (list) => Math.min(...list.map((p) => p.distanceTo(hand)));
      assert.ok(nearest(grips) < 0.13, `the ${k} hand ${nearest(grips).toFixed(3)} m from a grip at ${t}`);
      // The elbow rests on its pad's rear half, about 4 cm above the pad's top, the upper arm near upright.
      const elbow = wpos(rig.bones[`lowerarm_${k}`]);
      const pad = pads.reduce((a, b) => (a.distanceTo(elbow) < b.distanceTo(elbow) ? a : b));
      close(elbow.y, pad.y + 0.04, 0.035, `the ${k} elbow above its pad at ${t}`);
      assert.ok(Math.abs(elbow.x - pad.x) < 0.08, `the ${k} elbow ${(elbow.x - pad.x).toFixed(3)} m off its pad's middle at ${t}`);
      assert.ok(i < 2);
    }
  }
});

test('the shoulders stay put through the rep: only the legs move', async () => {
  const { rig, pose } = await buildStage(prepare(ID));
  pose(0);
  const start = between(rig, 'upperarm_l', 'upperarm_r');
  for (const t of FRAMES) {
    pose(t);
    assert.ok(between(rig, 'upperarm_l', 'upperarm_r').distanceTo(start) < 0.01, `the shoulders moved at ${t}`);
  }
});

test('the legs hang at the bottom, feet clear of the floor and the pegs, and the hips flex to 100-130° with the knees above them at the top', async () => {
  const { rig, pose } = await buildStage(prepare(ID));
  pose(1);
  for (const k of ['l', 'r']) {
    assert.ok(hipFlexion(rig, k) < 30, `the ${k} hip at the bottom: ${hipFlexion(rig, k).toFixed(0)}°`);
    assert.ok(wpos(rig.bones[`foot_${k}`]).y > 0.15, `the ${k} foot at ${wpos(rig.bones[`foot_${k}`]).y.toFixed(2)} m at the bottom`);
  }
  pose(0);
  for (const k of ['l', 'r']) {
    const flexion = hipFlexion(rig, k);
    assert.ok(flexion >= 100 && flexion <= 130, `the ${k} hip at the top: ${flexion.toFixed(0)}°`);
    assert.ok(wpos(rig.bones[`calf_${k}`]).y > wpos(rig.bones[`thigh_${k}`]).y, `the ${k} knee is not above its hip`);
  }
});

test('the whole body clears the chair on every frame, its frame and its pegs too', async () => {
  const { rig, kit, pose } = await buildStage(prepare(ID));
  const solids = [];
  kit.kits.captains_chair.group.traverse((o) => { if (o.isMesh && o.userData.solid) solids.push(o); });
  const skin = () => {
    const out = [];
    for (const m of rig.meshes) for (let i = 0; i < m.geometry.attributes.position.count; i += 3) out.push(m.getVertexPosition(i, new THREE.Vector3()).applyMatrix4(m.matrixWorld));
    return out;
  };
  for (const t of FRAMES) {
    pose(t);
    const points = skin();
    for (const mesh of solids) {
      if (mesh.userData.held || mesh.material?.name === 'vinyl') continue;
      const box = new THREE.Box3().setFromObject(mesh).expandByScalar(-0.012);
      const inside = points.filter((p) => box.containsPoint(p));
      assert.equal(inside.length, 0, `${inside.length} skin points in a frame part at ${t}`);
    }
  }
});

test('the chair is shown by its exercise, in a review batch of its own', () => {
  assert.deepEqual(BATCHES['captains-chair'], [ID]);
  assert.equal(STATIONS.captains_chair, undefined, 'no longer unposed');
  assert.equal(shownAlone('captains_chair'), null);
  assert.deepEqual(tempoOf(prepare(ID)), { lowering: 2, bottom: 0.3, lifting: 1, top: 0.6 });
  const paths = tunables(prepare(ID)).map((n) => n.path);
  for (const path of ['top.thigh', 'bottom.thigh', 'top.shin', 'bottom.trunk', 'body.look']) assert.ok(paths.includes(path), `no ${path}`);
});
