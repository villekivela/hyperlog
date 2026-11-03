// The hack squat, rebuilt from a real plate-loaded one (#150): a Hammer Strength-style machine, its
// platform low in front, the rails rising behind the back to a post at the rear, and a carriage
// with the back pad, shoulder pads, handles and a loaded horn a side riding them.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import * as THREE from 'three';
import { prepare } from './entries.mjs';
import { buildStage } from './stage.mjs';
import { heldAt, turnOf } from './rig.mjs';

const stage = await buildStage(prepare('hack_squat'));
const V3 = (x, y, z) => new THREE.Vector3(x, y, z);

/**
 * The line a bar lies along across the palm of hand `k` now: the knuckles' line, from the little
 * finger's to the index finger's, square to the hand's length, as the rig lays a grip.
 */
function knuckles(rig, k) {
  const { dir, across } = rig.rest['hand_' + k];
  const d = dir.clone().normalize();
  return across.clone().addScaledVector(d, -across.dot(d)).normalize().applyQuaternion(turnOf(rig, 'hand_' + k));
}

/** The held part nearest hand `k`'s grip, and the way it runs: a rod lies along its own y. */
function handleAt(k) {
  const grip = heldAt(stage.rig, k);
  let best = null;
  stage.kit.group.traverse((o) => {
    if (!o.isMesh || !o.userData.held) return;
    const d = o.getWorldPosition(new THREE.Vector3()).distanceTo(grip);
    if (!best || d < best.d) best = { d, o };
  });
  return new THREE.Vector3(0, 1, 0).applyQuaternion(best.o.getWorldQuaternion(new THREE.Quaternion()));
}

/** The kit's meshes at the top of the rep. */
function meshes() {
  stage.pose(0);
  const out = [];
  stage.kit.group.traverse((o) => { if (o.isMesh) { o.geometry.computeBoundingBox(); out.push(o); } });
  return out;
}

/** The way a mesh's longest side runs, in the world. */
function lengthwise(o) {
  const size = o.geometry.boundingBox.getSize(new THREE.Vector3()).multiply(o.scale).toArray();
  const axis = [V3(1, 0, 0), V3(0, 1, 0), V3(0, 0, 1)][size.indexOf(Math.max(...size))];
  return axis.applyQuaternion(o.getWorldQuaternion(new THREE.Quaternion()));
}

test('the carriage rides the rails: the back pad lies along them and moves along them with the hips', () => {
  const all = meshes();
  const rails = all.filter((o) => o.material.name === 'metal' && o.userData.solid).sort((a, b) => b.scale.y - a.scale.y).slice(0, 2);
  const long = (o) => Math.max(...o.geometry.boundingBox.getSize(V3()).multiply(o.scale).toArray());
  const pad = all.filter((o) => o.material.name === 'vinyl' && o.userData.solid).sort((a, b) => long(b) - long(a))[0];
  const along = lengthwise(rails[0]);
  const off = (a, b) => (Math.acos(Math.min(1, Math.abs(a.dot(b)))) * 180) / Math.PI;
  assert.ok(off(along, lengthwise(rails[1])) < 1, 'the rails run together');
  assert.ok(off(along, lengthwise(pad)) < 2, `the back pad runs ${off(along, lengthwise(pad)).toFixed(0)}° off the rails`);
  const from = pad.getWorldPosition(V3());
  stage.pose(1);
  const moved = pad.getWorldPosition(V3()).sub(from);
  assert.ok(moved.length() > 0.2, 'the carriage travels');
  assert.ok(off(along, moved.normalize()) < 1, 'the back pad moves along the rails');
});

// The owner couldn't turn the grip to the old handles (#150): a handle that doesn't lie along the
// knuckles bends the wrist off it, and the grip's twist only turns the knuckles about the forearm.
test('each handle lies along the knuckles of the hand that holds it, all through the rep', () => {
  for (const d of [0, 0.5, 1]) {
    stage.pose(d);
    for (const k of ['l', 'r']) {
      const off = (Math.acos(Math.min(1, Math.abs(handleAt(k).dot(knuckles(stage.rig, k))))) * 180) / Math.PI;
      assert.ok(off < 8, `${k} hand at ${d}: the handle runs ${off.toFixed(0)}° off the knuckles`);
    }
  }
});

test('the hack squat stands the size of a real one, about 2.1 m long, 1.25 m wide and 1.55 m high', () => {
  stage.pose(0);
  const size = new THREE.Box3().setFromObject(stage.kit.group).getSize(new THREE.Vector3());
  assert.ok(size.z > 1.9 && size.z < 2.3, `${size.z.toFixed(2)} m long`);
  assert.ok(size.x > 1.2 && size.x < 1.6, `${size.x.toFixed(2)} m wide across the plates`);
  assert.ok(size.y > 1.3 && size.y < 1.7, `${size.y.toFixed(2)} m high`);
});
