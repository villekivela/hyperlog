// The seated calf raise, rebuilt from a real plate-loaded one (#150): the lever pivots on a post in
// front of the toes and runs back over the shins to the knee pads, a horn either side behind the
// pivot loaded with a plate.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import * as THREE from 'three';
import { prepare } from './entries.mjs';
import { buildStage } from './stage.mjs';
import { heldAt, wpos } from './rig.mjs';

const stage = await buildStage(prepare('seated_calf_raise'));

/** The calf raise's held parts: the U-handle's grips. */
function grips() {
  const out = [];
  stage.kit.group.traverse((o) => { if (o.isMesh && o.userData.held) out.push(o); });
  return out;
}

/** Every part of the kit, with where it is at each of `ds` down the rep. */
function track(ds) {
  const parts = [];
  stage.kit.group.traverse((o) => { if (o.isMesh) parts.push({ o, at: [] }); });
  for (const d of ds) {
    stage.pose(d);
    for (const p of parts) p.at.push(p.o.getWorldPosition(new THREE.Vector3()));
  }
  return parts;
}

test('the seated calf raise stands the size of a real one, about 1.35 m long, 0.85 m wide and 1.1 m high', () => {
  stage.pose(0);
  const size = new THREE.Box3().setFromObject(stage.kit.group).getSize(new THREE.Vector3());
  assert.ok(size.z > 1.1 && size.z < 1.4, `${size.z.toFixed(2)} m long`);
  assert.ok(size.x > 0.75 && size.x < 1.0, `${size.x.toFixed(2)} m wide`);
  assert.ok(size.y > 0.85 && size.y < 1.15, `${size.y.toFixed(2)} m high`);
});

test('the lever turns as one about a pivot on a post in front of the toes, level with the pads: what moves keeps its distance from it', () => {
  stage.pose(1);
  const pivot = stage.kit.group.getObjectByName('calf_raise_lever')?.getWorldPosition(new THREE.Vector3());
  assert.ok(pivot, 'no lever');
  const toes = Math.max(...['l', 'r'].map((k) => wpos(stage.rig.bones['ball_' + k]).z));
  const knee = wpos(stage.rig.bones.calf_l);
  assert.ok(pivot.z > toes + 0.15, `pivot ${pivot.z.toFixed(2)} m forward, the toes ${toes.toFixed(2)}`);
  assert.ok(Math.abs(pivot.y - knee.y - 0.1) < 0.08, `pivot ${pivot.y.toFixed(2)} m up, the knees ${knee.y.toFixed(2)}`);
  const moved = track([0, 0.5, 1]).filter((p) => p.at[0].distanceTo(p.at[2]) > 1e-4);
  assert.ok(moved.length > 0, 'nothing moves');
  const radius = (p) => Math.hypot(p.y - pivot.y, p.z - pivot.z);
  for (const { o, at } of moved) {
    assert.ok(Math.abs(radius(at[0]) - radius(at[2])) < 1e-3 && Math.abs(radius(at[1]) - radius(at[2])) < 1e-3, `${o.name} leaves the lever's arc`);
    assert.ok(Math.abs(at[0].x - at[2].x) < 1e-4, `${o.name} moves sideways`);
  }
});

test('the knee pads ride the thighs all through the rep', () => {
  const pads = [];
  stage.kit.group.traverse((o) => { if (o.isMesh && o.userData.solid && !o.userData.held && o.material.name === 'vinyl' && o.parent.name === 'calf_raise_lever') pads.push(o); });
  assert.equal(pads.length, 2, 'a pad over each knee');
  const gaps = [0, 0.5, 1].map((d) => {
    stage.pose(d);
    return ['l', 'r'].map((k) => {
      const knee = wpos(stage.rig.bones['calf_' + k]);
      const pad = pads.reduce((a, b) => (Math.abs(b.getWorldPosition(new THREE.Vector3()).x - knee.x) < Math.abs(a.getWorldPosition(new THREE.Vector3()).x - knee.x) ? b : a));
      return new THREE.Box3().setFromObject(pad).min.y - knee.y;
    });
  });
  for (const [i, k] of ['l', 'r'].entries()) {
    const g = gaps.map((x) => x[i]);
    assert.ok(Math.max(...g) - Math.min(...g) < 0.015, `the ${k} pad's gap over the knee runs ${g.map((v) => (v * 100).toFixed(1)).join(', ')} cm`);
    assert.ok(g.every((v) => v > 0.03 && v < 0.09), `the ${k} pad sits ${g.map((v) => (v * 100).toFixed(1)).join(', ')} cm over the knee`);
  }
});

// The pose's check holds the hands to where the pose puts them: this holds them to the handle, which
// turns with the lever.
test('the hands hold the U-handle\'s grips all through the rep', () => {
  for (const d of [0, 0.5, 1]) {
    stage.pose(d);
    for (const k of ['l', 'r']) {
      const hand = heldAt(stage.rig, k);
      const miss = Math.min(...grips().map((o) => {
        const ends = [-0.5, 0.5].map((y) => o.localToWorld(new THREE.Vector3(0, y, 0)));
        return new THREE.Line3(...ends).closestPointToPoint(hand, true, new THREE.Vector3()).distanceTo(hand);
      }));
      assert.ok(miss < 0.015, `the ${k} hand at ${d} is ${(miss * 100).toFixed(1)} cm off the handle`);
    }
  }
});
