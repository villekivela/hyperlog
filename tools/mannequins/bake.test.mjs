// Bakes every pose and reads the clips back as the phone would.
import { test, before } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, readFile, readdir } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import * as THREE from 'three';
import { bakeAll } from './bake.mjs';
import { loadClip, readGlb } from './glb.mjs';
import { prepare } from './entries.mjs';
import { buildStage } from './stage.mjs';
import { POSES } from './poses.mjs';

let out;
let index;
before(async () => {
  out = await mkdtemp(path.join(tmpdir(), 'mannequins-'));
  index = (await bakeAll(out), JSON.parse(await readFile(path.join(out, 'mannequins.json'), 'utf8')));
});

/** A baked clip, read as the phone reads it: with the resources the index lists for it. */
const read = (file) => loadClip(file, index.clips[path.basename(file, '.glb')].resources.map((r) => path.join(out, r)));

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
    tempo: { lowering: 1.5, bottom: 0.25, lifting: 1.1, top: 0.7 },
    resources: ['mannequin.bin'],
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

test('every clip shares one mannequin: its mesh and skin are baked once, and a clip holds its kit and animation', async () => {
  const shared = await readFile(path.join(out, 'mannequin.bin'));
  assert.deepEqual((await readdir(out)).filter((f) => !f.endsWith('.glb')).sort(), ['mannequin.bin', 'mannequins.json']);
  for (const id of Object.keys(POSES)) {
    const { json, bin } = readGlb(await readFile(path.join(out, `${id}.glb`)));
    assert.deepEqual(json.buffers, [{ byteLength: bin.length }, { uri: 'mannequin.bin', byteLength: shared.length }], id);
    // Every view of the skinned body is in the shared file, and nothing of the clip's own is.
    const skinned = new Set(json.nodes.filter((n) => n.skin !== undefined).map((n) => n.mesh));
    const body = new Set(json.skins.map((k) => json.accessors[k.inverseBindMatrices].bufferView));
    for (const m of skinned) {
      for (const p of json.meshes[m].primitives) {
        for (const a of [...Object.values(p.attributes), p.indices]) body.add(json.accessors[a].bufferView);
      }
    }
    json.bufferViews.forEach((v, i) => assert.equal(v.buffer, body.has(i) ? 1 : 0, `${id}: view ${i}`));
    assert.ok(bin.length < shared.length / 3, `${id} holds ${bin.length} bytes of its own, against ${shared.length} shared`);
  }
});

test('baking again gives byte-identical files', async () => {
  const again = await mkdtemp(path.join(tmpdir(), 'mannequins-'));
  await bakeAll(again);
  for (const f of (await readdir(out)).sort()) {
    assert.ok((await readFile(path.join(out, f))).equals(await readFile(path.join(again, f))), f);
  }
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

test('every clip carries a four-phase tempo, from its entry or its movement', () => {
  for (const [id, { tempo }] of Object.entries(index.clips)) {
    assert.deepEqual(Object.keys(tempo), ['lowering', 'bottom', 'lifting', 'top'], id);
    for (const s of Object.values(tempo)) assert.ok(s >= 0 && s <= 6, `${id}: ${s}`);
    assert.ok(tempo.lowering > 0 && tempo.lifting > 0, id);
  }
  // An entry's own tempo wins over its movement's.
  assert.deepEqual(index.clips.leg_press.tempo, { lowering: 2, bottom: 0.5, lifting: 1.5, top: 0.5 });
});
