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
