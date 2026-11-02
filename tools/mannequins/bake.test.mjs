// Bakes the squat and reads the clip back as the phone would.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, readFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import * as THREE from 'three';
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js';
import { bakeAll } from './bake.mjs';

async function read(file) {
  const bytes = await readFile(file);
  return new GLTFLoader().parseAsync(bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength), '');
}

test('the squat bakes a clip of its lowering and an index entry', async () => {
  const out = await mkdtemp(path.join(tmpdir(), 'mannequins-'));
  await bakeAll(out);
  const index = JSON.parse(await readFile(path.join(out, 'mannequins.json'), 'utf8'));
  assert.deepEqual(index.clips.barbell_squat, {
    clip: 'barbell_squat.glb',
    equipment: 'barbell',
    camera: { target: [0, 0.87, 0], halfHeight: 1.02, azimuth: 62, elevation: 8 },
  });

  const gltf = await read(path.join(out, 'barbell_squat.glb'));
  assert.deepEqual(gltf.animations.map((a) => [a.name, a.duration]), [['lowering', 1]]);
  const materials = new Set();
  gltf.scene.traverse((o) => { if (o.material) materials.add(o.material.name); });
  assert.deepEqual([...materials].sort(), ['bar', 'body', 'joint', 'plate', 'shadow']);

  // Played from the top to the bottom, the hips and the bar go down.
  const mixer = new THREE.AnimationMixer(gltf.scene);
  mixer.clipAction(gltf.animations[0]).play();
  const at = (t) => {
    mixer.setTime(t);
    gltf.scene.updateMatrixWorld(true);
    const y = (name) => gltf.scene.getObjectByName(name).getWorldPosition(new THREE.Vector3()).y;
    return { hips: y('pelvis'), bar: y('barbell') };
  };
  const top = at(0);
  const bottom = at(0.999);
  assert.ok(top.hips > 0.85 && bottom.hips < 0.6, `hips ${top.hips} → ${bottom.hips}`);
  assert.ok(top.bar > 1.3 && bottom.bar < top.bar - 0.35, `bar ${top.bar} → ${bottom.bar}`);
});
