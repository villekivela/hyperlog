// The cable shoulder press (#240): the forearm goes through the stirrup, as the technique video
// shows, so the ring sits behind the wrist and the cable runs from it behind the arm to the low
// pulley, on every frame, on both sides.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import * as THREE from 'three';
import { prepare } from './entries.mjs';
import { buildStage, FRAMES } from './stage.mjs';
import { CHECKS, bodyVertices, checkStage } from './checks.mjs';
import { V, heldAt, wpos } from './rig.mjs';

/** How near the arm's skin the wire counts as passing it, and so has to be behind it. */
const PASSING = 0.1;
/** The skin taken as level with a point: a slice this far either way along the bone. */
const SLICE = 0.015;
/** The skin taken as on a point's side of the bone: this far round from it, since an arm is oval. */
const SIDE_ON = Math.PI / 6;
/** How far the loop may press into the skin it bears on: as far as the palm into what it holds. */
const PRESSED = 0.003;
/** How far a handle may lie from the hand: the hands check's own limit. */
const IN_HAND = 0.015;
/** A full rep's stack travel, the issue's "about 500" mm, with room for the pose's tuning. */
const FULL_REP = 0.45;

const entry = prepare('cable_shoulder_press');
const stage = await buildStage(entry);
const { cables, joints } = stage.kit.kits.cable_column;
const SIDES = [{ k: 'l', s: 1, cable: cables[0] }, { k: 'r', s: -1, cable: cables[1] }];
const skin = bodyVertices(stage.rig);

/** Every frame of the rep, posed. */
const eachFrame = (see) => {
  for (let f = 0; f < FRAMES; f++) see(f, stage.pose(f / (FRAMES - 1)));
};

/** The wire of `cable` from its ring to its pulley, as points 5 mm apart. */
const wireOf = ({ cable, hook }) => {
  const ring = hook();
  const far = [-0.5, 0.5].map((y) => cable.localToWorld(V(0, y, 0))).sort((a, b) => b.distanceTo(ring) - a.distanceTo(ring))[0];
  const n = Math.ceil(ring.distanceTo(far) / 0.005);
  return Array.from({ length: n + 1 }, (_, i) => ring.clone().lerp(far, i / n));
};

/** A bone from joint `from` to joint `to`, as its axis and the posed skin it moves. */
const boneOf = (from, to) => ({
  name: from,
  axis: new THREE.Line3(wpos(stage.rig.bones[from]), wpos(stage.rig.bones[to])),
  points: skin.filter((v) => v.bone === from)
    .map((v) => v.mesh.getVertexPosition(v.i, new THREE.Vector3()).applyMatrix4(v.mesh.matrixWorld)),
});

/** The arm on side `k`, the hand with it. */
const armOf = (k) => [
  boneOf(`upperarm_${k}`, `lowerarm_${k}`), boneOf(`lowerarm_${k}`, `hand_${k}`), boneOf(`hand_${k}`, `middle_01_${k}`),
];

/** The point of a bone's axis nearest `p`, its ends included. */
const nearestOn = ({ axis }, p) => axis.closestPointToPoint(p, true, new THREE.Vector3());

/** How far `p` stands off a bone, past its skin level with it on `p`'s side: negative inside. */
const clearOf = (bone, p) => {
  const on = nearestOn(bone, p);
  const out = p.clone().sub(on);
  const skinOut = bone.points.map((q) => ({ q, at: nearestOn(bone, q) }))
    .filter(({ q, at }) => at.distanceTo(on) < SLICE && q.clone().sub(at).angleTo(out) < SIDE_ON)
    .map(({ q, at }) => q.distanceTo(at));
  return out.length() - Math.max(0, ...skinOut);
};

test('the cable runs behind the arm and clear of it on every frame', () => {
  eachFrame((f) => {
    for (const { k, cable } of SIDES) {
      const arm = armOf(k);
      for (const p of wireOf(cable)) {
        for (const bone of arm) {
          const clear = clearOf(bone, p);
          assert.ok(clear > 0, `frame ${f}: the cable ${(-clear * 100).toFixed(1)} cm inside the ${bone.name}`);
          const on = nearestOn(bone, p);
          if (clear < PASSING) assert.ok(p.z < on.z, `frame ${f}: the cable ${((p.z - on.z) * 100).toFixed(1)} cm in front of the ${bone.name}`);
        }
      }
    }
  });
});

test('the ring sits behind the wrist on every frame, the forearm between it and the handle', () => {
  eachFrame((f, P) => {
    for (const { k, s, cable } of SIDES) {
      const off = cable.hook().sub(wpos(stage.rig.bones[`hand_${k}`]));
      const behind = -off.dot(P.palm[s]);
      const along = Math.abs(off.dot(P.fingers[s]));
      assert.ok(behind > 0.04, `frame ${f}: the ${k} ring ${(behind * 100).toFixed(1)} cm behind the wrist`);
      assert.ok(along < 0.02, `frame ${f}: the ${k} ring ${(along * 100).toFixed(1)} cm along the forearm from the wrist`);
    }
  });
});

/** Side `k`'s stirrup, and its loop: the first part of it the hand doesn't hold. */
const stirrupOf = (k) => {
  const stirrup = stage.kit.group.getObjectByName(`cable_handle_${k}`);
  return { stirrup, loop: stirrup.children.find((m) => m.isMesh && !m.userData.held) };
};

test('the forearm goes through the loop on every frame', () => {
  eachFrame((f) => {
    for (const { k } of SIDES) {
      const { stirrup, loop } = stirrupOf(k);
      // In the stirrup's own frame the loop lies flat at z 0, so the forearm crosses it where z is.
      const [elbow, knuckles] = [`lowerarm_${k}`, `middle_01_${k}`].map((b) => stirrup.worldToLocal(wpos(stage.rig.bones[b])));
      assert.ok(elbow.z * knuckles.z < 0, `frame ${f}: the ${k} forearm stays one side of the loop`);
      const crossing = elbow.clone().lerp(knuckles, elbow.z / (elbow.z - knuckles.z));
      loop.geometry.computeBoundingBox();
      const { min, max } = loop.geometry.boundingBox;
      assert.ok(crossing.x > min.x && crossing.x < max.x && crossing.y > min.y && crossing.y < max.y,
        `frame ${f}: the ${k} forearm crosses ${crossing.x.toFixed(3)}, ${crossing.y.toFixed(3)}, outside the loop`);
    }
  });
});

test('the loop bears on the hand and the wrist without cutting them', () => {
  eachFrame((f) => {
    for (const { k } of SIDES) {
      const { loop } = stirrupOf(k);
      const { position } = loop.geometry.attributes;
      const bones = armOf(k).slice(1);
      for (let i = 0; i < position.count; i++) {
        const p = new THREE.Vector3().fromBufferAttribute(position, i).applyMatrix4(loop.matrixWorld);
        for (const bone of bones) {
          const clear = clearOf(bone, p);
          assert.ok(clear > -PRESSED, `frame ${f}: the loop ${(-clear * 100).toFixed(1)} cm inside the ${bone.name}`);
        }
      }
    }
  });
});

test('every check passes, the hands closed on the handles', () => {
  const checks = checkStage(entry, stage);
  for (const c of CHECKS) assert.ok(checks[c].ok, `${c}: frame ${checks[c].frame}, ${checks[c].found}`);
  eachFrame((f) => {
    for (const { k } of SIDES) {
      const off = stirrupOf(k).stirrup.getWorldPosition(V(0, 0, 0)).distanceTo(heldAt(stage.rig, k));
      assert.ok(off < IN_HAND, `frame ${f}: the ${k} handle ${(off * 100).toFixed(1)} cm from the hand`);
    }
  });
});

test('the stacks move a full rep', () => {
  const raised = (d) => { stage.pose(d); return ['stack_l', 'stack_r'].map((j) => joints[j].value); };
  const [top, bottom] = [raised(0), raised(1)];
  top.forEach((height, i) => assert.ok(height - bottom[i] > FULL_REP, `the stack moves ${(height - bottom[i]).toFixed(3)} m`));
});
