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

/** The mannequin from the bytes of its `.glb`: a scene holding its skinned body. */
export async function parseMannequin(buffer) {
  const gltf = await new GLTFLoader().parseAsync(buffer, '');
  const root = gltf.scene;
  root.name = 'mannequin';
  // The first part is the body, the next the joint rings between its pieces.
  let part = 0;
  root.traverse((o) => { if (o.isSkinnedMesh) o.material = part++ === 0 ? MATERIALS.body() : MATERIALS.joint(); });
  return root;
}

async function loadMannequin() {
  model ??= readFile(MODEL);
  const bytes = await model;
  return parseMannequin(bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength));
}

/** How finely an arm is followed through the rep: steps from the top to the bottom. */
const STEPS = 60;
/** How many steps either side an arm's roll and turn are averaged over, so a change of branch eases in. */
const EASE = 6;

/**
 * Each side's straight arm (the hand's roll `t` and the elbow's turn `p`) followed step by step
 * through the rep, from the top down and from the bottom up, whichever costs the less over the rep,
 * each side on its own; then averaged along the rep with the fingers' closing, so where the arm
 * changes branch, or a finger finds the bar at another point, it turns over a few frames instead
 * of jumping. What `pose(d)` takes, by step; a side without a straight arm keeps its own.
 */
function followArms(rig, frame) {
  const pass = (order) => {
    const out = [];
    let near = {};
    for (const i of order) near = out[i] = poseRig(rig, frame(i / STEPS), near);
    return out;
  };
  const steps = [...Array(STEPS + 1).keys()];
  const down = pass(steps);
  const up = pass([...steps].reverse());
  const cost = (arms, k) => arms.reduce((sum, a) => sum + (a[k]?.cost ?? 0), 0);
  const arms = steps.map(() => ({}));
  // Runs of steps that have a value, each averaged twice (a triangle), evenly either side and
  // narrower toward the ends, so the ends keep their own and a steady change stays as it is.
  const smoothed = (values) => {
    const out = [];
    for (let i = 0; i < values.length;) {
      if (!values[i]) { i++; continue; }
      let j = i;
      while (j < values.length && values[j]) j++;
      const average = (xs) => xs.map((_, n) => {
        const r = Math.min(EASE, n, xs.length - 1 - n);
        const near = xs.slice(n - r, n + r + 1);
        return xs[0].map((__, c) => near.reduce((sum, x) => sum + x[c], 0) / near.length);
      });
      average(average(values.slice(i, j))).forEach((v, n) => { out[i + n] = v; });
      i = j;
    }
    return out;
  };
  for (const k of ['l', 'r']) {
    const chosen = (cost(down, k) <= cost(up, k) ? down : up).map((a) => a[k]);
    // The arm's roll, turn and slant, the first two unwrapped so a turn past half a circle doesn't read as a jump back.
    const turns = chosen.map((a) => (a?.t === undefined ? null : [a.t, a.p, a.q]));
    for (let n = 1; n < turns.length; n++) {
      if (!turns[n] || !turns[n - 1]) continue;
      for (const c of [0, 1]) turns[n][c] += Math.round((turns[n - 1][c] - turns[n][c]) / (2 * Math.PI)) * 2 * Math.PI;
    }
    const names = Object.keys(chosen.find((a) => a?.fingers)?.fingers ?? {});
    const closings = smoothed(chosen.map((a) => (a?.fingers ? names.map((f) => a.fingers[f]) : null)));
    smoothed(turns).forEach(([t, p, q], i) => Object.assign(arms[i][k] ??= { at: true }, { t, p, q }));
    closings.forEach((c, i) => { if (c) (arms[i][k] ??= { at: true }).fingers = Object.fromEntries(names.map((f, n) => [f, c[n]])); });
  }
  return arms;
}

/**
 * Builds a resolved entry's stage, around `body` (the mannequin, already loaded: the studio's page
 * brings its own) or the one in `model/`. `pose(d)` poses the body and the kit `d` of the way down
 * the rep (0 at the top, 1 at the bottom) and returns the pose frame.
 */
export async function buildStage(entry, body) {
  const scene = new THREE.Scene();
  const mannequin = body ?? await loadMannequin();
  scene.add(mannequin);
  const rig = buildRig(mannequin);
  const kit = buildKit(entry);
  scene.add(kit.group);
  const { size = [1.1, 0.8], at = [0, 0] } = entry.shadow ?? {};
  const shadow = floorShadow(...size);
  shadow.position.x = at[0];
  shadow.position.z = at[1];
  scene.add(shadow);
  // A kit shown alone (`shownAlone`) stands as its recipe has it with no pose, the body unposed.
  if (entry.alone) {
    const pose = () => {
      kit.place({}, {}, {});
      scene.updateMatrixWorld(true);
      return {};
    };
    return { scene, rig, kit, pose };
  }
  const top = frameAt(entry, 0, rig.dims);
  const bottom = frameAt(entry, 1, rig.dims);
  const frame = (d) => (d === 0 ? top : d === 1 ? bottom : frameAt(entry, d, rig.dims));
  const arms = followArms(rig, frame);
  const pose = (d) => {
    const P = frame(d);
    poseRig(rig, P, arms[Math.round(d * STEPS)]);
    kit.place(P, top, bottom);
    scene.updateMatrixWorld(true);
    return P;
  };
  return { scene, rig, kit, pose };
}
