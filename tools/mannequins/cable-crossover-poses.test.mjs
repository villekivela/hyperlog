// The cable crossover's poses (#197): body-first, each cable from its pulley to the hand on every
// frame, the carriage at a hole refs/cable_crossover/ref.md gives for the exercise.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import * as THREE from 'three';
import { prepare } from './entries.mjs';
import { buildStage, FRAMES } from './stage.mjs';
import { CHECKS, checkStage } from './checks.mjs';
import { BATCHES } from './review.mjs';
import { V, heldAt } from './rig.mjs';

/** Each exercise's holes, from the ref's Adjustments: its band, lowest and highest. */
const HOLES = {
  cable_crossover: [15, 16],
  cable_chest_press: [10, 10],
  low_to_high_cable_press: [4, 6],
  high_cable_lateral_raise: [4, 6],
  cable_y_raise: [1, 1],
  behind_the_back_cable_lateral_raise: [1, 1],
  cable_shrug_in: [2, 3],
  '1_arm_cable_press_around': [10, 10],
  '1_arm_katana_cable_triceps_extension': [4, 6],
};
/** Where a hole's cable leaves the lower wheel, from the ref: 350 mm up at the first, 132 mm pitch, 85 mm below the pin. */
const exitOf = (hole) => 0.35 + (hole - 1) * 0.132 - 0.085;

const BATCH = BATCHES['cable-crossover'];

test('the batch is the exercises the ref gives a hole for', () => {
  assert.deepEqual([...BATCH].sort(), Object.keys(HOLES).sort());
});

for (const [id, [low, high]] of Object.entries(HOLES)) {
  test(`${id} stands on the crossover, its cable running from a pulley the ref gives to the hand on every frame`, async () => {
    const entry = prepare(id);
    assert.deepEqual(Object.keys(entry.kit), ['cable_crossover']);
    const stage = await buildStage(entry);
    const checks = checkStage(entry, stage);
    for (const c of CHECKS) assert.ok(checks[c].ok, `${c}: frame ${checks[c].frame}, ${checks[c].found}`);
    const kit = stage.kit.kits.cable_crossover;
    const { position = 10, hands = [1, -1], crossed = false } = entry.kit.cable_crossover;
    const holes = Array.isArray(position) ? position : [position, position];
    for (const h of holes) assert.ok(h >= low && h <= high, `hole ${h}, not ${low} to ${high}`);
    for (let f = 0; f < FRAMES; f++) {
      stage.pose(f / (FRAMES - 1));
      stage.scene.updateMatrixWorld(true);
      for (const side of hands) {
        // The cable on this hand: its own tower's, or with `crossed` the far one's (the left tower first).
        const tower = (crossed ? -side : side) > 0 ? 0 : 1;
        const { cable, hook } = kit.cables[tower];
        const ends = [-0.5, 0.5].map((y) => cable.localToWorld(V(0, y, 0)));
        const [top, end] = ends.sort((a, b) => b.y - a.y);
        const pulley = Math.min(...ends.map((e) => Math.abs(e.y - exitOf(holes[tower]))));
        assert.ok(pulley < 1e-3, `frame ${f}: the cable leaves ${pulley.toFixed(3)} m off the hole's height (${top.y.toFixed(3)}, ${end.y.toFixed(3)})`);
        const held = heldAt(stage.rig, side > 0 ? 'l' : 'r');
        assert.ok(hook().distanceTo(held) < 0.2, `frame ${f}: the cable ends ${hook().distanceTo(held).toFixed(3)} m from the hand`);
      }
    }
  });
}

/** The trunk's bones: an arm may cross the middle of the body, as a press-around's does. */
const TRUNK = /^(pelvis|spine|neck|clavicle)/;

/** Where the cable runs past the middle of the body, and how far the trunk reaches front and back there. */
function pastTheBody(stage, cable) {
  const ends = [-0.5, 0.5].map((y) => cable.localToWorld(V(0, y, 0)));
  const [a, b] = ends;
  const t = a.x / (a.x - b.x);
  if (!(t > 0 && t < 1)) return null;
  const at = a.clone().lerp(b, t);
  const near = [];
  const v = new THREE.Vector3();
  stage.scene.traverse((m) => {
    if (!m.isSkinnedMesh) return;
    const { skinIndex, skinWeight, position } = m.geometry.attributes;
    for (let i = 0; i < position.count; i++) {
      const main = [0, 1, 2, 3].reduce((best, n) => (skinWeight.getComponent(i, n) > skinWeight.getComponent(i, best) ? n : best), 0);
      if (!TRUNK.test(m.skeleton.bones[skinIndex.getComponent(i, main)].name)) continue;
      m.getVertexPosition(i, v).applyMatrix4(m.matrixWorld);
      if (Math.abs(v.x) < 0.06 && Math.abs(v.y - at.y) < 0.06) near.push(v.z);
    }
  });
  return near.length ? { z: at.z, front: Math.max(...near), back: Math.min(...near) } : null;
}

// What the wires can't fail: they aren't solid, so nothing else stops a cable running through the
// body, and which side it passes is what these raises are.
for (const [id, side, tower] of [
  ['high_cable_lateral_raise', 'front', 1], ['cable_y_raise', 'front', 1], ['behind_the_back_cable_lateral_raise', 'back', 1], ['1_arm_cable_press_around', 'front', 0],
]) {
  test(`${id}'s cable passes ${side === 'front' ? 'in front of' : 'behind'} the body on every frame`, async () => {
    const stage = await buildStage(prepare(id));
    const { cable } = stage.kit.kits.cable_crossover.cables[tower];
    let met = 0;
    for (let f = 0; f < FRAMES; f++) {
      stage.pose(f / (FRAMES - 1));
      const past = pastTheBody(stage, cable);
      if (!past) continue;
      met++;
      const clear = side === 'front' ? past.z - past.front : past.back - past.z;
      assert.ok(clear > 0, `frame ${f}: the cable at z ${past.z.toFixed(3)}, the body from ${past.back.toFixed(3)} to ${past.front.toFixed(3)}`);
    }
    assert.ok(met > 0, 'the cable never passes the middle of the body');
  });
}
