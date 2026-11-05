// What the dumbbell batches' tests measure on a lift: its checks, its bells and its bones through the rep.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import * as THREE from 'three';
import { benchAngles, prepare } from './entries.mjs';
import { buildStage } from './stage.mjs';
import { checkPose, CHECKS } from './checks.mjs';
import { wpos } from './rig.mjs';

export const deg = (r) => (r * 180) / Math.PI;

/** Every check on `id` passes, at bench `angle` if it has one, or the test names the ones that don't. */
export async function passes(id, angle) {
  const { checks } = await checkPose(id, { angle });
  const failed = CHECKS.filter((c) => !checks[c].ok).map((c) => `${angle ?? ''} ${c}: ${checks[c].found} (frame ${checks[c].frame})`);
  assert.deepEqual(failed, []);
}

/** How far the left dumbbell's handle is turned from lying across the body, at `d` down the rep (0 to 90). */
export async function handleTurn(id, d) {
  const stage = await buildStage(prepare(id));
  stage.pose(d);
  const bell = stage.kit.group.getObjectByName('dumbbell_l');
  return deg(Math.acos(Math.min(1, Math.abs(new THREE.Vector3(1, 0, 0).applyQuaternion(bell.quaternion).x))));
}

/** Where the left hand's dumbbell is at `d` down the rep. */
export async function bellAt(id, d) {
  const stage = await buildStage(prepare(id));
  stage.pose(d);
  stage.scene.updateMatrixWorld(true);
  return stage.kit.group.getObjectByName('dumbbell_l').getWorldPosition(new THREE.Vector3());
}

/** Where bone `name` is at `d` down the rep of `id`. */
export async function boneAt(id, name, d) {
  const stage = await buildStage(prepare(id));
  stage.pose(d);
  return wpos(stage.rig.bones[name]);
}

// A bell's turn between frames follows the forearm, about 8° at a curl's fastest. Past that it is
// spinning about its own handle, which the jump check misses where the spin is steady.
export const MAX_TURN = 10;

/** A test on each of `ids` that no dumbbell spins about its handle, at every bench angle it has. */
export function noBellSpins(ids) {
  for (const id of ids) {
    test(`${id}: no dumbbell spins about its handle`, async () => {
      for (const angle of benchAngles(id) ?? [undefined]) {
        const stage = await buildStage(prepare(id, undefined, angle));
        const before = {};
        for (let f = 0; f <= 30; f++) {
          stage.pose(f / 30);
          for (const n of ['dumbbell_l', 'dumbbell_r']) {
            const q = stage.kit.group.getObjectByName(n)?.quaternion.clone();
            if (!q) continue;
            const turn = before[n] ? deg(q.angleTo(before[n])) : 0;
            assert.ok(turn < MAX_TURN, `${n} at ${angle ?? 'its'}°, frame ${f}: ${turn.toFixed(1)}°`);
            before[n] = q;
          }
        }
      }
    });
  }
}
