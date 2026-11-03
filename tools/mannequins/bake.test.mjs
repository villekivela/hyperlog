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
const read = (file) => loadClip(file, index.clips[path.basename(file, '.glb').replace(/_\d+$/, '')].resources.map((r) => path.join(out, r)));

/** Every clip file of `id`: its one clip, or a clip per bench angle. */
const filesOf = (id) => (index.clips[id].bench ? Object.values(index.clips[id].bench.clips).map((c) => c.clip) : [index.clips[id].clip]);

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
    tempo: { lowering: 2, bottom: 0.25, lifting: 1, top: 0.7 },
    resources: ['mannequin.bin', 'kit_barbell.bin'],
  });

  const gltf = await read(path.join(out, 'barbell_squat.glb'));
  assert.deepEqual(gltf.animations.map((a) => [a.name, a.duration]), [['lowering', 1]]);
  const materials = new Set();
  gltf.scene.traverse((o) => { if (o.material) materials.add(o.material.name); });
  assert.deepEqual([...materials].sort(), ['body', 'joint', 'metal', 'plate', 'shadow']);

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

/** The kits a pose is posed in, by name. */
const kitsOf = (id) => Object.keys(prepare(id).kit ?? {}).sort();

/** The buffer views of a glTF's meshes, the skinned ones or the others, with each skin's matrices for the skinned. */
function meshViews(json, skinned) {
  const views = new Set(skinned ? json.skins.map((k) => json.accessors[k.inverseBindMatrices].bufferView) : []);
  const meshes = new Set(json.nodes.filter((n) => n.mesh !== undefined && (n.skin !== undefined) === skinned && n.name !== 'shadow').map((n) => n.mesh));
  for (const m of meshes) {
    for (const p of json.meshes[m].primitives) {
      for (const a of [...Object.values(p.attributes), p.indices]) views.add(json.accessors[a].bufferView);
    }
  }
  return views;
}

test('every clip shares one mannequin and one file per kit: a clip holds only its animation and its shadow', async () => {
  const kits = [...new Set(Object.keys(POSES).flatMap(kitsOf))].sort();
  assert.deepEqual(
    (await readdir(out)).filter((f) => !f.endsWith('.glb')).sort(),
    [...kits.map((k) => `kit_${k}.bin`), 'mannequin.bin', 'mannequins.json'],
  );
  const sizes = Object.fromEntries(await Promise.all(['mannequin', ...kits.map((k) => `kit_${k}`)].map(async (f) => [`${f}.bin`, (await readFile(path.join(out, `${f}.bin`))).length])));
  for (const id of Object.keys(POSES)) {
    const resources = ['mannequin.bin', ...kitsOf(id).map((k) => `kit_${k}.bin`)];
    assert.deepEqual(index.clips[id].resources, resources, id);
    for (const file of filesOf(id)) {
      const { json, bin } = readGlb(await readFile(path.join(out, file)));
      assert.deepEqual(json.buffers, [{ byteLength: bin.length }, ...resources.map((uri) => ({ uri, byteLength: sizes[uri] }))], file);
      // The skinned body's views are in the mannequin's file, the kit's meshes in the kits', and
      // only the rest in the clip.
      const body = meshViews(json, true);
      const kit = meshViews(json, false);
      json.bufferViews.forEach((v, i) => {
        if (body.has(i)) assert.equal(v.buffer, 1, `${file}: the body's view ${i}`);
        else if (kit.has(i)) assert.ok(v.buffer > 1, `${file}: the kit's view ${i}`);
        else assert.equal(v.buffer, 0, `${file}: view ${i}`);
      });
      assert.ok(bin.length < 60_000, `${file} holds ${bin.length} bytes of its own`);
    }
  }
});

test('a kit file holds each part once, however many clips use it', async () => {
  // The six bench angles use the same dumbbells: the second angle's adds nothing to the file.
  const dumbbells = await readFile(path.join(out, 'kit_dumbbells.bin'));
  const views = new Set();
  for (const file of filesOf('db_incline_press')) {
    const { json } = readGlb(await readFile(path.join(out, file)));
    const at = json.buffers.findIndex((b) => b.uri === 'kit_dumbbells.bin');
    json.bufferViews.filter((v) => v.buffer === at).forEach((v) => views.add(`${v.byteOffset}:${v.byteLength}`));
  }
  const used = [...views].reduce((n, v) => n + Number(v.split(':')[1]), 0);
  assert.ok(used <= dumbbells.length, `${used} bytes read from a file of ${dumbbells.length}`);
  const { json } = readGlb(await readFile(path.join(out, filesOf('db_incline_press')[0])));
  const at = json.buffers.findIndex((b) => b.uri === 'kit_dumbbells.bin');
  const first = new Set(json.bufferViews.filter((v) => v.buffer === at).map((v) => `${v.byteOffset}:${v.byteLength}`));
  assert.equal(views.size, first.size, 'every angle reads the same parts');
});

test('baking again gives byte-identical files', async () => {
  const again = await mkdtemp(path.join(tmpdir(), 'mannequins-'));
  await bakeAll(again);
  assert.deepEqual((await readdir(again)).sort(), (await readdir(out)).sort());
  for (const f of (await readdir(out)).sort()) {
    assert.ok((await readFile(path.join(out, f))).equals(await readFile(path.join(again, f))), f);
  }
});

test('every pose is baked and indexed under its exercise, in its equipment', () => {
  assert.deepEqual(Object.keys(index.clips).sort(), Object.keys(POSES).sort());
  for (const [id, e] of Object.entries(index.clips)) {
    assert.ok(filesOf(id).includes(e.clip));
    for (const f of filesOf(id)) assert.ok(f === `${id}.glb` || f.startsWith(`${id}_`), f);
    assert.equal(e.equipment, prepare(id).equipment);
  }
});

for (const id of Object.keys(POSES)) {
  test(`${id}'s clip holds the lowering half only: the top to the bottom, and never back up`, async () => {
    const gltf = await read(path.join(out, index.clips[id].clip));
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
  // The eccentric takes about 2 s and the concentric about 1 s. The clip bakes the lowering, which
  // is the eccentric except where the bar is pulled down, where it is the concentric.
  const slow = (id, phase) => assert.equal(index.clips[id].tempo[phase], 2, `${id} ${phase}`);
  for (const id of ['barbell_squat', 'barbell_rdl', 'barbell_bench_press', 'leg_press', 'machine_chest_press']) slow(id, 'lowering');
  for (const id of ['lat_pulldown', 'close_grip_lat_pulldown', 'triceps_pressdown']) slow(id, 'lifting');
});
