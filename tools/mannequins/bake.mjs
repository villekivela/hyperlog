// Bakes each pose entry into a clip the phone plays: the mannequin, its equipment and the floor
// shadow in one .glb, with one animation of the rep's lowering, from the top (0 s) to the bottom
// (1 s). The phone plays it forward, holds, and plays it backward. `mannequins.json` indexes the
// clips by exercise id.
//
//   node bake.mjs [out-dir]     (default: app/src/main/assets/mannequins)
import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import * as THREE from 'three';
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js';
import { GLTFExporter } from 'three/examples/jsm/exporters/GLTFExporter.js';
import { buildRig, poseRig } from './rig.mjs';
import { MOTIONS } from './motions.mjs';
import { MATERIALS, barbell, floorShadow } from './kit.mjs';
import { POSES } from './poses.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));
export const MODEL = path.join(HERE, 'model', 'mannequin.glb');
const DEFAULT_OUT = path.join(HERE, '..', '..', 'app', 'src', 'main', 'assets', 'mannequins');
/** Frames baked over the lowering, top and bottom included. */
export const FRAMES = 31;
const KITS = { barbell };

// GLTFExporter reads its blobs back through FileReader, which Node doesn't have.
globalThis.FileReader ??= class {
  readAsArrayBuffer(blob) {
    blob.arrayBuffer().then((buffer) => { this.result = buffer; this.onloadend(); });
  }
};

async function loadMannequin() {
  const bytes = await readFile(MODEL);
  const buffer = bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength);
  const gltf = await new GLTFLoader().parseAsync(buffer, '');
  const root = gltf.scene;
  root.name = 'mannequin';
  // The first part is the body, the next the joint rings between its pieces.
  let part = 0;
  root.traverse((o) => { if (o.isSkinnedMesh) o.material = part++ === 0 ? MATERIALS.body() : MATERIALS.joint(); });
  return root;
}

/** The pose entry for an exercise id. */
function resolve(id) {
  const e = POSES[id];
  if (!e) throw new Error(`No pose entry for ${id}`);
  return e;
}

const rounded = (v) => Math.round(v * 1e5) / 1e5;

/** Bakes one entry: the .glb's bytes and the index entry. */
export async function bakeOne(id) {
  const entry = resolve(id);
  const motion = MOTIONS[entry.motion];
  if (!motion) throw new Error(`${id}: no motion "${entry.motion}"`);
  const scene = new THREE.Scene();
  const mannequin = await loadMannequin();
  scene.add(mannequin);
  const rig = buildRig(mannequin);
  const kit = KITS[entry.kit]();
  scene.add(kit);
  scene.add(floorShadow(1.1, 0.8));

  // Sample the lowering; every bone that moves and the kit get a track.
  const bones = Object.values(rig.bones);
  const times = [];
  const rotations = bones.map(() => []);
  const pelvis = [];
  const bar = [];
  for (let f = 0; f < FRAMES; f++) {
    const d = f / (FRAMES - 1);
    const P = motion(entry, d, rig.dims);
    poseRig(rig, P);
    times.push(d);
    bones.forEach((b, i) => rotations[i].push(...b.quaternion.toArray().map(rounded)));
    pelvis.push(...rig.bones.pelvis.position.toArray().map(rounded));
    bar.push(...P.bar.toArray().map(rounded));
  }
  const tracks = [];
  const moves = (values, size) => values.some((v, i) => Math.abs(v - values[i % size]) > 1e-5);
  bones.forEach((b, i) => {
    if (moves(rotations[i], 4)) tracks.push(new THREE.QuaternionKeyframeTrack(`${b.name}.quaternion`, times, rotations[i]));
  });
  tracks.push(new THREE.VectorKeyframeTrack('pelvis.position', times, pelvis));
  tracks.push(new THREE.VectorKeyframeTrack(`${kit.name}.position`, times, bar));
  const clip = new THREE.AnimationClip('lowering', 1, tracks);

  // The file opens on the top of the rep, so a bone left without a track keeps its pose there.
  poseRig(rig, motion(entry, 0, rig.dims));
  kit.position.fromArray(bar.slice(0, 3));
  scene.updateMatrixWorld(true);

  const glb = await new GLTFExporter().parseAsync(scene, { binary: true, animations: [clip] });
  const camera = { ...entry.camera };
  return {
    glb: Buffer.from(glb),
    entry: { clip: `${id}.glb`, equipment: entry.equipment, camera },
  };
}

/** Bakes every entry into `out`: one .glb each and mannequins.json. */
export async function bakeAll(out = DEFAULT_OUT) {
  await mkdir(out, { recursive: true });
  const index = {};
  for (const id of Object.keys(POSES).sort()) {
    const { glb, entry } = await bakeOne(id);
    await writeFile(path.join(out, entry.clip), glb);
    index[id] = entry;
  }
  await writeFile(path.join(out, 'mannequins.json'), JSON.stringify({ clips: index }, null, 2) + '\n');
  return index;
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const out = process.argv[2] ? path.resolve(process.argv[2]) : DEFAULT_OUT;
  const index = await bakeAll(out);
  for (const id of Object.keys(index)) console.log(`baked ${id} → ${path.relative(process.cwd(), path.join(out, index[id].clip))}`);
}
