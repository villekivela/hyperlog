// The scene a pose is posed in: the mannequin, its rig, the pose's kit and the floor shadow, and a
// way to pose all of it at any point of the rep. The checks and the bake both pose through here.
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import * as THREE from 'three';
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js';
import { buildRig, poseRig } from './rig.mjs';
import { frameAt } from './motions.mjs';
import { MATERIALS, buildKit, floorShadow } from './kit.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));
export const MODEL = path.join(HERE, 'model', 'mannequin.glb');
/** Frames baked and checked over the lowering, top and bottom included. */
export const FRAMES = 31;

let model;

async function loadMannequin() {
  model ??= readFile(MODEL);
  const bytes = await model;
  const buffer = bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength);
  const gltf = await new GLTFLoader().parseAsync(buffer, '');
  const root = gltf.scene;
  root.name = 'mannequin';
  // The first part is the body, the next the joint rings between its pieces.
  let part = 0;
  root.traverse((o) => { if (o.isSkinnedMesh) o.material = part++ === 0 ? MATERIALS.body() : MATERIALS.joint(); });
  return root;
}

/**
 * Builds a resolved entry's stage. `pose(d)` poses the body and the kit `d` of the way down the
 * rep (0 at the top, 1 at the bottom) and returns the pose frame.
 */
export async function buildStage(entry) {
  const scene = new THREE.Scene();
  const mannequin = await loadMannequin();
  scene.add(mannequin);
  const rig = buildRig(mannequin);
  const kit = buildKit(entry);
  scene.add(kit.group);
  const { size = [1.1, 0.8], at = [0, 0] } = entry.shadow ?? {};
  const shadow = floorShadow(...size);
  shadow.position.x = at[0];
  shadow.position.z = at[1];
  scene.add(shadow);
  const top = frameAt(entry, 0, rig.dims);
  const pose = (d) => {
    const P = d === 0 ? top : frameAt(entry, d, rig.dims);
    poseRig(rig, P);
    kit.place(P, top);
    scene.updateMatrixWorld(true);
    return P;
  };
  return { scene, rig, kit, pose };
}
