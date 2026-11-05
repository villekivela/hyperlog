// The shared tubing and pivot parts (#212): a bend is cut on its bisector, not lapped.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import * as THREE from 'three';
import { MATERIALS, mitered, pivotJoint } from './parts.mjs';
import { V } from './rig.mjs';

const M = MATERIALS ?? {};
const box = (mesh) => new THREE.Box3().setFromObject(mesh);

/** The volume a closed mesh holds, from its triangles. */
function volume(mesh) {
  const pos = mesh.geometry.attributes.position;
  const at = (i) => new THREE.Vector3().fromBufferAttribute(pos, i);
  const index = mesh.geometry.index.array;
  let sum = 0;
  for (let i = 0; i < index.length; i += 3) sum += at(index[i]).dot(at(index[i + 1]).cross(at(index[i + 2]))) / 6;
  return Math.abs(sum);
}

test('a right-angle bend of 4 cm tubing is cut on its bisector: its volume is its section by its path, with no lap or gap', () => {
  const w = 0.04;
  const bend = mitered([V(0, 0, 0), V(0.5, 0, 0), V(0.5, 0.5, 0)], w, M.frame ?? new THREE.MeshBasicMaterial());
  const r = w * 0.28;
  const section = w * w - (4 - Math.PI) * r * r;
  const held = volume(bend);
  assert.ok(Math.abs(held / (section * 1.0) - 1) < 0.02, `holds ${(held * 1e6).toFixed(0)} cm³, the path's ${(section * 1e6).toFixed(0)} cm³`);
});

test('a length landed on a face is cut flush to it', () => {
  const tube = mitered([V(0, 0, 0), V(0, 0.4, 0.4)], 0.05, new THREE.MeshBasicMaterial(), { ends: [null, V(0, 0, 1)] });
  assert.ok(Math.abs(box(tube).max.z - 0.4) < 1e-6, `reaches z ${box(tube).max.z}`);
});

test('a pivot joint is a housing round the axle, and the axle runs through it past both ends', () => {
  const material = new THREE.MeshBasicMaterial();
  const { housing, fixed } = pivotJoint({ frame: material, metal: material }, V(0.2, 1.1, 0.3), V(0, 0, 1));
  const [h, axle] = [box(housing), box(fixed[0])];
  assert.ok(axle.min.z < h.min.z && axle.max.z > h.max.z, 'the axle shows at both ends');
  assert.ok(h.max.x - h.min.x > axle.max.x - axle.min.x, 'the housing is wider than the axle');
});
