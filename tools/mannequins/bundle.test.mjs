// The Tune panel runs the tool's own modules in the review page, so what it poses and checks there
// is what the bake does. The bundle must give the same answers as the modules do in node.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import * as THREE from 'three';
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js';
import { bundleTune } from './bundle.mjs';
import { prepare } from './entries.mjs';
import { checkPose } from './checks.mjs';
import { MODEL } from './stage.mjs';
import { POSES } from './poses.mjs';

test('the bundle checks a pose as the modules do, from a mannequin it is given', async () => {
  const source = await bundleTune();
  assert.ok(!/^(import|export)\s/m.test(source) && !source.includes('import.meta'), 'no module syntax left');
  const tune = new Function('THREE', 'GLTFLoader', source)(THREE, GLTFLoader);
  const bytes = await readFile(MODEL);
  const body = await tune.parseMannequin(bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength));
  const entry = prepare('barbell_bench_press');
  const stage = await tune.buildStage(entry, body);
  assert.deepEqual(tune.checkStage(entry, stage), (await checkPose('barbell_bench_press')).checks);
});

// The owner, #127: on close_grip_bench_press the wrist sliders did nothing in the live preview.
// Every pose with hands: each wrist slider, moved from one side to the other, turns the hand.
test('each wrist slider turns the hand in the live preview, on every pose with hands', async () => {
  const source = await bundleTune();
  const tune = new Function('THREE', 'GLTFLoader', source)(THREE, GLTFLoader);
  const bytes = await readFile(MODEL);
  const body = await tune.parseMannequin(bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength));
  const still = [];
  for (const id of Object.keys(POSES)) {
    const entry = prepare(id);
    if (!entry.grip) continue;
    // As the page poses it: an adjustable bench at its typical angle.
    const live = (tuned) => {
      const e = tune.applyTuned(entry, tuned);
      return tune.buildStage(e.bench ? tune.atAngle(e, e.bench.typical) : e, body);
    };
    const wrists = tune.tunables(entry).filter((t) => /^(top|bottom)\.wrist\.[01]$/.test(t.path));
    assert.equal(wrists.length, 4, `${id} offers ${wrists.map((t) => t.path)}`);
    for (const t of wrists) {
      const d = t.path.startsWith('top') ? 0 : 1;
      const hand = async (v) => {
        const stage = await live(tune.withTuned(entry, {}, t.path, v));
        stage.pose(d);
        return tune.turnOf(stage.rig, 'lowerarm_l').invert().multiply(tune.turnOf(stage.rig, 'hand_l'));
      };
      const turned = ((await hand(15)).angleTo(await hand(-15)) * 180) / Math.PI;
      if (turned < 20) still.push(`${id} ${t.path}: ${turned.toFixed(0)}°`);
    }
  }
  assert.deepEqual(still, [], 'from -15 to 15 the hand turned only');
});
