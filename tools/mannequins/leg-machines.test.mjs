// The leg extension and the leg curls are built from a real machine's layout (#149): a selectorized
// tower standing beside the user, not behind them, and handles where the machine has them.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import * as THREE from 'three';
import { prepare } from './entries.mjs';
import { buildStage } from './stage.mjs';

const MACHINES = ['leg_extension', 'seated_leg_curl', 'lying_leg_curl'];

const world = (o) => o.getWorldPosition(new THREE.Vector3());

for (const id of MACHINES) {
  test(`${id}'s stack stands beside the user, level with the hips or the thighs`, async () => {
    const stage = await buildStage(prepare(id));
    const P = stage.pose(0);
    const heads = [];
    stage.kit.group.traverse((o) => { if (o.userData.stack) heads.push(world(o)); });
    assert.equal(heads.length, 1, `${heads.length} stacks`);
    const [head] = heads;
    // Out past the hips' side, and between the hips and the knees along the body.
    assert.ok(Math.abs(head.x) > 0.35, `the stack ${head.x.toFixed(2)} m to the side`);
    const [a, b] = [P.hipC.z, P.knee.z].sort((p, q) => p - q);
    assert.ok(head.z > a - 0.15 && head.z < b + 0.15, `the stack at z ${head.z.toFixed(2)}, the hips at ${P.hipC.z.toFixed(2)} and the knees at ${P.knee.z.toFixed(2)}`);
  });
}

/** The nearest distance from `p` to a held part's axis: its longest extent, as a segment. */
function toGrip(mesh, p) {
  mesh.geometry.computeBoundingBox();
  const box = mesh.geometry.boundingBox;
  const size = box.getSize(new THREE.Vector3()).multiply(mesh.getWorldScale(new THREE.Vector3())).toArray();
  const axis = size.indexOf(Math.max(...size));
  const centre = box.getCenter(new THREE.Vector3());
  const [a, b] = [box.min, box.max].map((end) => mesh.localToWorld(centre.clone().setComponent(axis, end.getComponent(axis))));
  return new THREE.Line3(a, b).closestPointToPoint(p, true, new THREE.Vector3()).distanceTo(p);
}

for (const id of MACHINES) {
  test(`${id}'s hands hold the machine's own grips`, async () => {
    const stage = await buildStage(prepare(id));
    const held = [];
    stage.kit.group.traverse((o) => { if (o.isMesh && o.userData.held) held.push(o); });
    for (const d of [0, 1]) {
      const P = stage.pose(d);
      for (const s of [-1, 1]) {
        const miss = Math.min(...held.map((m) => toGrip(m, P.hand(s))));
        assert.ok(miss < 0.015, `at ${d ? 'the bottom' : 'the top'}, the ${s < 0 ? 'right' : 'left'} hand ${(miss * 100).toFixed(1)} cm from a grip`);
      }
    }
  });
}
