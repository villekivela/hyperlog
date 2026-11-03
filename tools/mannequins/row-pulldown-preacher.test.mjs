// The chest-supported row, the machine pulldown and the preacher curl, laid out as the plate-loaded
// machines they are built from (#151): where each one's levers turn against the lifter, and the
// plates on them rising as the muscle works.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import * as THREE from 'three';
import { prepare } from './entries.mjs';
import { buildStage } from './stage.mjs';
import { tempoOf } from './tempo.mjs';
import { wpos, V, X, UP } from './rig.mjs';

/** The pose's stage, its stretch and squeeze, and its kit's parts in a tone. */
async function staged(id) {
  const entry = prepare(id);
  const stage = await buildStage(entry);
  const { lowering, lifting } = tempoOf(entry);
  const [stretch, squeeze] = lowering < lifting ? [0, 1] : [1, 0];
  const inTone = (tone) => {
    const out = [];
    stage.kit.group.traverse((o) => { if (o.isMesh && o.material.name === tone) out.push(o); });
    return out;
  };
  return { stage, stretch, squeeze, inTone };
}

/**
 * Along the ray from `from` (inside the body) toward `dir`: where the body's surface is, and the
 * first part of the kit past it, with how far past it that part starts (negative: inside the body).
 */
function reach({ stage }, from, dir) {
  const d = dir.clone().normalize();
  const hit = new THREE.Raycaster(from, d, 0, 1).intersectObject(stage.kit.group, true)[0];
  // The body's surface, seen from outside it: back along the ray from a metre out, the last of the
  // body's faces before `from` (past any other part of the body further out).
  stage.rig.meshes.forEach((m) => { m.computeBoundingSphere(); m.computeBoundingBox(); });
  const back = new THREE.Raycaster(from.clone().addScaledVector(d, 1), d.clone().negate(), 0, 1);
  const skin = back.intersectObjects(stage.rig.meshes, false).at(-1);
  assert.ok(skin, 'the ray finds the body');
  return { part: hit?.object, gap: hit ? hit.distance - (1 - skin.distance) : Infinity };
}

/** The kit has no weight stack, and every plate on it is higher at the squeeze than at the stretch. */
function platesRise({ stage, stretch, squeeze, inTone }) {
  let stacks = 0;
  stage.kit.group.traverse((o) => { if (o.userData.stack) stacks += 1; });
  assert.equal(stacks, 0, 'a plate-loaded machine has no weight stack');
  const plates = inTone('plate');
  assert.ok(plates.length >= 2, `${plates.length} plates`);
  const heights = (d) => { stage.pose(d); return plates.map((p) => p.getWorldPosition(new THREE.Vector3()).y); };
  const rest = heights(stretch);
  heights(squeeze).forEach((y, i) => assert.ok(y > rest[i] + 0.1, `plate ${i}: ${rest[i].toFixed(2)} m at the stretch, ${y.toFixed(2)} m at the squeeze`));
}

// A plate-loaded chest-supported row (Gymleco 010): the levers rise from a pivot low in front, ahead
// of the knees, to the handles.
test('the row\'s levers turn low in front, and their plates rise as the handles come back', async () => {
  const s = await staged('chest_supported_machine_row');
  for (const d of [0, 1]) {
    const P = s.stage.pose(d);
    const hand = P.hand(1);
    const knee = wpos(s.stage.rig.bones.calf_l);
    assert.ok(P.pivot.y < hand.y - 0.35, `at ${d} the pivot at ${P.pivot.y.toFixed(2)} m, the hand at ${hand.y.toFixed(2)} m`);
    assert.ok(P.pivot.z > knee.z + 0.1, `at ${d} the pivot at z ${P.pivot.z.toFixed(2)}, the knee at ${knee.z.toFixed(2)}`);
  }
  platesRise(s);
});

test('the row\'s chest rests on its pad, which stands near upright', async () => {
  const s = await staged('chest_supported_machine_row');
  const P = s.stage.pose(s.stretch);
  const up = (Math.atan2(P.u.z, P.u.y) * 180) / Math.PI;
  assert.ok(up >= 10 && up <= 20, `the pad ${up.toFixed(0)}° from upright`);
  const forward = X.clone().cross(P.u).normalize();
  const { part, gap } = reach(s, P.hipC.clone().addScaledVector(P.u, 0.4), forward);
  assert.equal(part?.material.name, 'vinyl', `ahead of the chest is ${part?.name}`);
  assert.ok(gap > -0.02 && gap < 0.01, `the pad ${(gap * 100).toFixed(1)} cm off the chest`);
});

// A plate-loaded iso-lateral pulldown (Gymleco 011): the levers turn on a portal ahead of the
// lifter's head, and the thigh pads hang under their bar onto the thighs.
test('the pulldown\'s levers turn ahead of the head, and the thigh pads rest on the thighs', async () => {
  const s = await staged('machine_pulldown');
  for (const d of [0, 1]) {
    const P = s.stage.pose(d);
    const head = wpos(s.stage.rig.bones.Head);
    assert.ok(P.pivot.z > head.z + 0.4, `at ${d} the pivot at z ${P.pivot.z.toFixed(2)}, the head at ${head.z.toFixed(2)}`);
    for (const k of ['l', 'r']) {
      const hip = wpos(s.stage.rig.bones['thigh_' + k]);
      const knee = wpos(s.stage.rig.bones['calf_' + k]);
      const { part, gap } = reach(s, hip.lerp(knee, 0.75), UP);
      assert.equal(part?.material.name, 'vinyl', `over the ${k} thigh is ${part?.name}`);
      assert.ok(gap > -0.02 && gap < 0.01, `at ${d} the ${k} thigh pad ${(gap * 100).toFixed(1)} cm off the thigh`);
    }
  }
  platesRise(s);
});

// A plate-loaded preacher curl, as the owner's reference (#151): the lifter leans forward about 20°
// onto one wide arm pad, the chest against its back edge and the upper arms on clear pad, which
// stops short of the elbows; the levers turn on hubs in line with the elbows, beyond its front edge.
test('the preacher\'s chest leans onto the pad\'s back edge, and its levers turn in line with the elbows past its front', async () => {
  const s = await staged('machine_preacher_curl');
  const P = s.stage.pose(s.stretch);
  const bone = (n) => wpos(s.stage.rig.bones[n]);
  const shoulders = bone('upperarm_l').lerp(bone('upperarm_r'), 0.5);
  const trunk = shoulders.clone().sub(bone('thigh_l').lerp(bone('thigh_r'), 0.5));
  const lean = (Math.atan2(trunk.z, trunk.y) * 180) / Math.PI;
  assert.ok(lean > 15 && lean < 25, `the hips to the shoulders ${lean.toFixed(0)}° from upright`);
  const under = V(0, -P.upper.z, P.upper.y);
  const back = reach(s, shoulders.clone().addScaledVector(under, 0.08), P.upper);
  assert.equal(back.part?.material.name, 'vinyl', `ahead of the chest is ${back.part?.name}`);
  assert.ok(back.gap > -0.02 && back.gap < 0.01, `the pad's back edge ${(back.gap * 100).toFixed(1)} cm off the chest`);
  for (const k of ['l', 'r']) {
    const elbow = bone('lowerarm_' + k);
    const arm = reach(s, elbow.clone().addScaledVector(P.upper, -0.06), under);
    assert.equal(arm.part?.material.name, 'vinyl', `under the ${k} upper arm is ${arm.part?.name}`);
    assert.ok(arm.gap > -0.02 && arm.gap < 0.01, `the ${k} upper arm ${(arm.gap * 100).toFixed(1)} cm off the pad`);
    const below = new THREE.Raycaster(elbow, under, 0, 0.2).intersectObject(s.stage.kit.group, true)[0];
    assert.notEqual(below?.object.material.name, 'vinyl', `the pad runs on under the ${k} elbow`);
  }
  const hubs = s.inTone('pulley').map((m) => m.getWorldPosition(new THREE.Vector3()));
  assert.equal(hubs.length, 2, `${hubs.length} hubs`);
  for (const h of hubs) {
    assert.ok(Math.hypot(h.y - P.elbow.y, h.z - P.elbow.z) < 0.01, `a hub ${(Math.hypot(h.y - P.elbow.y, h.z - P.elbow.z) * 100).toFixed(1)} cm off the elbows' line`);
    assert.ok(Math.abs(h.x) > 0.25, `a hub at x ${h.x.toFixed(2)}, among the arms`);
  }
  platesRise(s);
});
