// The starter batch's cable exercises (owner, #127): a flye's or a raise's hands sweep an arc about
// the shoulder with the elbow held, and a curl can stand staggered.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { prepare } from './entries.mjs';
import { buildStage, FRAMES } from './stage.mjs';
import { heldAt, wpos } from './rig.mjs';

for (const id of ['cable_flye', 'cable_lateral_raise', 'reverse_cable_crossover']) {
  test(`${id}'s hand sweeps an arc about the shoulder, the elbow held`, async () => {
    const stage = await buildStage(prepare(id));
    const reach = [];
    for (let f = 0; f < FRAMES; f++) {
      stage.pose(f / (FRAMES - 1));
      reach.push(heldAt(stage.rig, 'l').distanceTo(wpos(stage.rig.bones.upperarm_l)));
    }
    // The reach moves only from the top's to the bottom's: it never dips mid-rep, so the elbow
    // doesn't bend there.
    const dip = Math.min(reach[0], reach.at(-1)) - Math.min(...reach);
    assert.ok(dip < 0.01, `the hand comes ${(dip * 100).toFixed(1)} cm closer to the shoulder mid-rep`);
  });
}

test('a staggered stance puts one foot ahead of the other', async () => {
  const entry = prepare('bayesian_cable_curl');
  const P = (await buildStage(entry)).pose(0);
  assert.ok(Math.abs(P.ankles[0].z - P.ankles[1].z) > 0.2, `the ankles at z ${P.ankles[0].z.toFixed(2)} and ${P.ankles[1].z.toFixed(2)}`);
});
