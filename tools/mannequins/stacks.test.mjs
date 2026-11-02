// A weight stack rests where the working muscle is longest, at the stretch, and rises as it works:
// the clip's first half is the eccentric unless its lowering is quicker than its lifting.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import * as THREE from 'three';
import { prepare } from './entries.mjs';
import { buildStage } from './stage.mjs';
import { tempoOf } from './tempo.mjs';
import { POSES } from './poses.mjs';

for (const id of Object.keys(POSES)) {
  test(`${id}'s stacks rest at the stretch and rise toward the squeeze`, async () => {
    const entry = prepare(id);
    const stage = await buildStage(entry);
    const heads = [];
    stage.kit.group.traverse((o) => { if (o.userData.stack) heads.push(o); });
    const heights = (d) => { stage.pose(d); return heads.map((h) => h.getWorldPosition(new THREE.Vector3()).y); };
    const { lowering, lifting } = tempoOf(entry);
    const [stretch, squeeze] = lowering < lifting ? [0, 1] : [1, 0];
    const rest = heights(stretch);
    const worked = heights(squeeze);
    heads.forEach((_, i) => assert.ok(worked[i] > rest[i] + 0.03, `stack ${i}: ${rest[i].toFixed(3)} at the stretch, ${worked[i].toFixed(3)} at the squeeze`));
  });
}
