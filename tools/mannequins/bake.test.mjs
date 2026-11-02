// Bakes every pose and reads the clips back as the phone would.
import { test, before } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, readFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import * as THREE from 'three';
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js';
import { bakeAll } from './bake.mjs';
import { prepare } from './entries.mjs';
import { buildStage } from './stage.mjs';
import { POSES } from './poses.mjs';

let out;
let index;
before(async () => {
  out = await mkdtemp(path.join(tmpdir(), 'mannequins-'));
  index = (await bakeAll(out), JSON.parse(await readFile(path.join(out, 'mannequins.json'), 'utf8')));
});

async function read(file) {
  const bytes = await readFile(file);
  return new GLTFLoader().parseAsync(bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength), '');
}

/** The clip played to `t` seconds: where each joint and each part of the kit is. */
function player(gltf) {
  const mixer = new THREE.AnimationMixer(gltf.scene);
  mixer.clipAction(gltf.animations[0]).play();
  const nodes = [];
  gltf.scene.traverse((o) => { if (o.isBone || o.name.startsWith('kit') || o.parent?.name === 'kit') nodes.push(o); });
  return (t) => {
    mixer.setTime(t);
    gltf.scene.updateMatrixWorld(true);
    return nodes.map((o) => o.getWorldPosition(new THREE.Vector3()));
  };
}

test('the squat bakes a clip of its lowering and an index entry', async () => {
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

test('every pose is baked and indexed under its exercise, in its equipment', () => {
  assert.deepEqual(Object.keys(index.clips).sort(), Object.keys(POSES).sort());
  for (const [id, e] of Object.entries(index.clips)) {
    assert.equal(e.clip, `${id}.glb`);
    assert.equal(e.equipment, prepare(id).equipment);
  }
});

for (const id of Object.keys(POSES)) {
  test(`${id}'s clip holds the lowering half only: the top to the bottom, and never back up`, async () => {
    const gltf = await read(path.join(out, `${id}.glb`));
    assert.deepEqual(gltf.animations.map((a) => [a.name, a.duration]), [['lowering', 1]]);
    const play = player(gltf);
    // It opens on the pose's top and ends on its bottom.
    const { rig, pose } = await buildStage(prepare(id));
    const joints = (d) => {
      pose(d);
      return Object.values(rig.bones).map((b) => b.getWorldPosition(new THREE.Vector3()));
    };
    const bones = (t) => play(t).slice(0, Object.keys(rig.bones).length);
    const near = (a, b) => Math.max(...a.map((p, i) => p.distanceTo(b[i])));
    assert.ok(near(bones(0), joints(0)) < 0.005, 'the first frame is the top');
    assert.ok(near(bones(1 - 1e-6), joints(1)) < 0.005, 'the last frame is the bottom');
    // Each step goes further from the top: nothing comes back up within the clip.
    const top = play(0);
    let last = 0;
    for (let f = 1; f <= 30; f++) {
      const away = play(Math.min(f / 30, 1 - 1e-6)).reduce((sum, p, i) => sum + p.distanceTo(top[i]), 0);
      assert.ok(away >= last - 1e-3, `frame ${f} is ${away.toFixed(3)} from the top, back from ${last.toFixed(3)}`);
      last = away;
    }
  });
}
