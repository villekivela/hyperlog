// Every kit is a recipe (ADR 0007), and what each declares holds on the reps it is baked in: its
// joints stay within their limits, each joint moves its body the way the pose does, and each
// contact meets its landmark on the mannequin.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import * as THREE from 'three';
import { prepare } from './entries.mjs';
import { buildStage, FRAMES } from './stage.mjs';
import { heldAt, wpos } from './rig.mjs';
import { buildKit } from './kit.mjs';
import { POSES } from './poses.mjs';

/** Each posed exercise whose kit has a body on a joint. */
const JOINTED = Object.keys(POSES).sort().filter((id) => Object.values(buildKit(prepare(id)).kits).some((kit) => Object.keys(kit.bodies).length));

const world = (o) => { o.updateWorldMatrix(true, false); return o.getWorldPosition(new THREE.Vector3()); };
const jointed = (stage) => Object.entries(stage.kit.kits).flatMap(([name, kit]) => Object.keys(kit.bodies).map((body) => ({ name, kit, body })));

for (const id of JOINTED) {
  test(`${id}: every joint stays within its limits through the rep`, async () => {
    const stage = await buildStage(prepare(id));
    for (let f = 0; f < FRAMES; f++) {
      stage.pose(f / (FRAMES - 1));
      for (const { name, kit, body } of jointed(stage)) {
        const { value, limits: [min, max] } = kit.joints[body];
        assert.ok(value >= min - 1e-9 && value <= max + 1e-9, `${name}'s ${body} at ${value.toFixed(3)} on frame ${f}, past [${min.toFixed(3)}, ${max.toFixed(3)}]`);
      }
    }
  });

  test(`${id}: each joint moves its body from the bottom of the rep to where the top has it`, async () => {
    const stage = await buildStage(prepare(id));
    stage.pose(0);
    const top = jointed(stage).map(({ kit, body }) => ({ value: kit.joints[body].value, at: kit.bodies[body].map(world) }));
    stage.pose(1);
    jointed(stage).forEach(({ name, kit, body }, i) => {
      kit.scrub(body, top[i].value);
      kit.bodies[body].forEach((node, k) => {
        const miss = world(node).distanceTo(top[i].at[k]);
        assert.ok(miss < 0.02, `${name}'s ${body}: ${node.name} ${(miss * 100).toFixed(1)} cm from where the top of the rep has it`);
      });
    });
  });
}

/**
 * Each landmark on side `k`, as a segment between two bones' points, and how far from it a
 * contact's point may be: the hands' grip exactly; a pad, a roller or a foot plate's middle within
 * a limb's breadth.
 */
const LANDMARKS = {
  hands: { reach: 0.03, at: (rig, k) => [heldAt(rig, k), heldAt(rig, k)] },
  feet: { reach: 0.25, at: (rig, k) => [wpos(rig.bones['foot_' + k]), wpos(rig.bones['ball_' + k])] },
  shins: { reach: 0.25, at: (rig, k) => [wpos(rig.bones['calf_' + k]), wpos(rig.bones['foot_' + k])] },
  thighs: { reach: 0.25, at: (rig, k) => [wpos(rig.bones['thigh_' + k]), wpos(rig.bones['calf_' + k])] },
  shoulders: { reach: 0.25, at: (rig, k) => [wpos(rig.bones['clavicle_' + k]), wpos(rig.bones['upperarm_' + k])] },
};

/** The nearest distance from `p` to a held part's axis: its longest extent, as a segment. */
function toHeld(mesh, p) {
  mesh.geometry.computeBoundingBox();
  const box = mesh.geometry.boundingBox;
  const size = box.getSize(new THREE.Vector3()).multiply(mesh.getWorldScale(new THREE.Vector3())).toArray();
  const axis = size.indexOf(Math.max(...size));
  const centre = box.getCenter(new THREE.Vector3());
  const [a, b] = [box.min, box.max].map((end) => mesh.localToWorld(centre.clone().setComponent(axis, end.getComponent(axis))));
  return new THREE.Line3(a, b).closestPointToPoint(p, true, new THREE.Vector3()).distanceTo(p);
}

for (const id of Object.keys(POSES).sort()) {
  test(`${id}: each contact with the hands is on a part the hands hold`, async () => {
    const stage = await buildStage(prepare(id));
    for (const d of [0, 1]) {
      stage.pose(d);
      for (const [name, kit] of Object.entries(stage.kit.kits)) {
        const held = [];
        kit.group.traverse((o) => { if (o.isMesh && o.userData.held) held.push(o); });
        for (const { point, at } of kit.contacts.filter((c) => c.landmark === 'hands')) {
          for (const p of at) {
            const miss = Math.min(...held.map((m) => toHeld(m, p)));
            assert.ok(miss < 0.015, `${name}'s ${point} ${(miss * 100).toFixed(1)} cm off a held part at the ${d ? 'bottom' : 'top'}`);
          }
        }
      }
    }
  });

  test(`${id}: each contact meets its landmark at the top and the bottom of the rep`, async () => {
    const stage = await buildStage(prepare(id));
    for (const d of [0, 1]) {
      stage.pose(d);
      for (const [name, kit] of Object.entries(stage.kit.kits)) {
        for (const { point, landmark, at } of kit.contacts) {
          const { reach, at: segment } = LANDMARKS[landmark];
          for (const p of at) {
            const miss = Math.min(...['l', 'r'].map((k) => new THREE.Line3(...segment(stage.rig, k)).closestPointToPoint(p, true, new THREE.Vector3()).distanceTo(p)));
            assert.ok(miss < reach, `${name}'s ${point} ${(miss * 100).toFixed(1)} cm from the ${landmark} at the ${d ? 'bottom' : 'top'}`);
          }
        }
      }
    }
  });
}
