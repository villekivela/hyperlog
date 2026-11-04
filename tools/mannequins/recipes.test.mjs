// Every kit is a recipe (ADR 0007), and what each declares holds on the reps it is baked in: its
// joints stay within their limits, each joint moves its body the way the pose does, and each
// contact meets its landmark on the mannequin.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import * as THREE from 'three';
import { prepare } from './entries.mjs';
import { buildStage, FRAMES } from './stage.mjs';
import { heldAt, rad, wpos } from './rig.mjs';
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

for (const id of JOINTED) {
  test(`${id}: scrubbing every joint from the bottom of the rep to the top's values sets the whole kit as the top has it`, async () => {
    const stage = await buildStage(prepare(id));
    const nodes = (kit) => { const out = []; kit.group.traverse((o) => { if (o !== kit.group) out.push(o); }); return out; };
    // A kit with a free body has parts the hands carry, which no joint moves.
    const kits = Object.entries(stage.kit.kits).filter(([, kit]) => Object.keys(kit.bodies).length && !kit.free);
    stage.pose(0);
    const top = kits.map(([, kit]) => ({ values: Object.fromEntries(Object.entries(kit.joints).map(([b, j]) => [b, j.value])), at: nodes(kit).map(world) }));
    stage.pose(1);
    kits.forEach(([name, kit], i) => {
      for (const [body, value] of Object.entries(top[i].values)) kit.scrub(body, value);
      nodes(kit).forEach((node, k) => {
        const miss = world(node).distanceTo(top[i].at[k]);
        assert.ok(miss < 0.02, `${name}: ${node.name} ${(miss * 100).toFixed(1)} cm from where the top of the rep has it`);
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
  chest: { reach: 0.25, at: (rig) => [wpos(rig.bones.spine_02), wpos(rig.bones.neck_01)] },
  back: { reach: 0.3, at: (rig) => [wpos(rig.bones.spine_02), wpos(rig.bones.neck_01)] },
  upperArms: { reach: 0.25, at: (rig, k) => [wpos(rig.bones['upperarm_' + k]), wpos(rig.bones['lowerarm_' + k])] },
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

// Each lever's limits are where the body stops, not only the machine (#160 reads them as where a
// rep has gone too far): a knee lever between a straight knee and the checks' deepest bend, a lever
// in the hands no further than the arms reach, and the calf raise's no further than the heels rise.
const angleAt = (rig, a, b, c) => {
  const [A, B, C] = [a, b, c].map((n) => wpos(rig.bones[n]));
  return B.clone().sub(A).angleTo(C.clone().sub(B));
};
const KNEE_LEVERS = ['leg_extension', 'seated_leg_curl', 'lying_leg_curl'];

for (const id of KNEE_LEVERS) {
  test(`${id}: the lever's limits keep the knee between straight and its deepest bend`, async () => {
    const stage = await buildStage(prepare(id));
    const [name, kit] = Object.entries(stage.kit.kits).find(([, k]) => k.bodies.lever);
    const knee = () => angleAt(stage.rig, 'thigh_l', 'calf_l', 'foot_l');
    stage.pose(1);
    const stretch = knee();
    stage.pose(0);
    // Which way the lever bends the knee: less for an extension, more for a curl.
    const way = Math.sign(knee() - stretch);
    for (const limit of kit.joints.lever.limits) {
      const bend = stretch + way * limit;
      assert.ok(bend >= -rad(2) && bend <= rad(155), `${name}'s lever at ${(limit * 180 / Math.PI).toFixed(0)}° bends the knee ${(bend * 180 / Math.PI).toFixed(0)}°`);
    }
  });
}

for (const id of ['machine_chest_press', 'chest_supported_machine_row', 'machine_pulldown', 'machine_preacher_curl', 'chest_supported_t_bar_row', 't_bar_row']) {
  test(`${id}: the levers' limits keep the handles within the arms' reach`, async () => {
    const stage = await buildStage(prepare(id));
    stage.pose(0);
    const { rig } = stage;
    const reach = (k) => wpos(rig.bones[`upperarm_${k}`]).distanceTo(wpos(rig.bones[`lowerarm_${k}`]))
      + wpos(rig.bones[`lowerarm_${k}`]).distanceTo(wpos(rig.bones[`hand_${k}`])) + wpos(rig.bones[`hand_${k}`]).distanceTo(heldAt(rig, k));
    const shoulders = ['l', 'r'].map((k) => ({ at: wpos(rig.bones[`upperarm_${k}`]), reach: reach(k) }));
    for (const [name, kit] of Object.entries(stage.kit.kits)) {
      for (const [body, joint] of Object.entries(kit.joints)) {
        for (const limit of joint.limits) {
          stage.pose(0);
          kit.scrub(body, limit);
          for (const { at } of kit.contacts.filter((c) => c.landmark === 'hands' && c.body === body)) {
            for (const p of at) {
              const short = Math.min(...shoulders.map((sh) => p.distanceTo(sh.at) - sh.reach));
              assert.ok(short < 0.01, `${name}'s ${body} at ${(limit * 180 / Math.PI).toFixed(0)}°: a handle ${(short * 100).toFixed(1)} cm past the arms' reach`);
            }
          }
        }
      }
    }
  });
}

test("seated_calf_raise: the lever's upper limit is no further than the heels rise, 45° from flat", async () => {
  const entry = prepare('seated_calf_raise');
  const highest = await buildStage({ ...entry, top: { ...entry.top, lever: 13 } });
  const { heading } = highest.pose(0).foot(1);
  const heels = (Math.atan2(-heading.y, heading.z) * 180) / Math.PI;
  assert.ok(heels > 15 && heels <= 45, `the heels ${heels.toFixed(1)}° from flat at the lever's upper limit`);
});
