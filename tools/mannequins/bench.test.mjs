// The bench press pose, from the owner's notes (#124): a slight arch, the feet back toward the hips,
// and the arms straight and vertical at lockout.
import { test, before } from 'node:test';
import assert from 'node:assert/strict';
import { tunables } from './tune.mjs';
import * as THREE from 'three';
import { prepare } from './entries.mjs';
import { buildStage } from './stage.mjs';
import { wpos } from './rig.mjs';

const PAD = 0.457;
let stage;
before(async () => { stage = await buildStage(prepare('barbell_bench_press')); });

/** The lowest the body goes at each z, over the pad (metres), at the top of the rep. */
function undersideAt(z) {
  stage.pose(0);
  const v = new THREE.Vector3();
  let low = Infinity;
  for (const m of stage.rig.meshes) {
    for (let i = 0; i < m.geometry.attributes.position.count; i++) {
      m.getVertexPosition(i, v).applyMatrix4(m.matrixWorld);
      if (Math.abs(v.x) < 0.14 && v.y < 0.8 && Math.abs(v.z - z) < 0.03) low = Math.min(low, v.y - PAD);
    }
  }
  return low;
}

test('the back arches: a gap under the lower back, the upper back and the hips on the pad', () => {
  assert.ok(undersideAt(0.05) > 0.03, `lower back ${undersideAt(0.05)}`);
  assert.ok(undersideAt(-0.2) < 0.015, `upper back ${undersideAt(-0.2)}`);
  assert.ok(undersideAt(0.25) < 0.015, `hips ${undersideAt(0.25)}`);
});

test('the feet are back toward the hips and flat', () => {
  stage.pose(0);
  const ankle = wpos(stage.rig.bones.foot_l);
  const ball = wpos(stage.rig.bones.ball_l);
  assert.ok(ankle.z < 0.7, `ankles at z ${ankle.z}`);
  assert.ok(Math.abs(ankle.y - stage.rig.dims.ankleY) < 0.01 && ball.y < 0.03, 'flat on the floor');
});

// Provisional (#202): the owner's notes (#124) had straight arms here, under 8° and 6° of lean, and
// their tune came out soft. Held to what they tuned until their morning look says which they want.
test('at the top the elbows are soft and the arms stand near upright over the shoulders', () => {
  stage.pose(0);
  const at = (n) => wpos(stage.rig.bones[n]);
  const upper = at('lowerarm_l').sub(at('upperarm_l'));
  const fore = at('hand_l').sub(at('lowerarm_l'));
  const deg = (r) => (r * 180) / Math.PI;
  assert.ok(deg(upper.angleTo(fore)) < 30, `elbow bends ${deg(upper.angleTo(fore))}°`);
  // Side-on, near vertical: no more than 20° of lean.
  for (const w of [upper, fore]) assert.ok(Math.abs(deg(Math.atan2(w.z, w.y))) < 20, `leans ${deg(Math.atan2(w.z, w.y))}°`);
});

test('the grip is a normal bench grip, about 1.5 shoulder widths, with the forearms vertical at the bottom', () => {
  const at = (n) => wpos(stage.rig.bones[n]);
  const deg = (r) => (r * 180) / Math.PI;
  stage.pose(0);
  const shoulderWidth = 2 * at('upperarm_l').x;
  const grip = 2 * at('hand_l').x;
  assert.ok(grip / shoulderWidth > 1.6, `hands ${grip} apart against shoulders ${shoulderWidth}`);
  stage.pose(1);
  const fore = at('hand_l').sub(at('lowerarm_l'));
  assert.ok(Math.abs(deg(Math.atan2(fore.x, fore.y))) < 6, `forearm leans ${deg(Math.atan2(fore.x, fore.y))}° front-on`);
  assert.ok(Math.abs(deg(Math.atan2(fore.z, fore.y))) < 15, `forearm leans ${deg(Math.atan2(fore.z, fore.y))}° side-on`);
});

test('grip.pole points a bench press\'s elbows: the close grip\'s go out as it asks, and its slider is offered', async () => {
  const elbowAt = async (pole) => {
    const entry = prepare('close_grip_bench_press');
    entry.grip.pole = pole;
    const { rig, pose } = await buildStage(entry);
    pose(1);
    return wpos(rig.bones.lowerarm_l).x;
  };
  const [tucked, flared] = [await elbowAt([0.05, -1, 0.66]), await elbowAt([0.9, -1, 0.07])];
  assert.ok(flared - tucked > 0.05, `the elbow moved ${(flared - tucked).toFixed(3)} m`);
  const paths = tunables(prepare('close_grip_bench_press')).map((t) => t.path);
  for (const i of [0, 1, 2]) assert.ok(paths.includes(`grip.pole.${i}`), `no slider grip.pole.${i}`);
});
