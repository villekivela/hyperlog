// Bakes each pose entry into a clip the phone plays: the mannequin, its equipment and the floor
// shadow in one .glb, with one animation of the rep's lowering, from the top (0 s) to the bottom
// (1 s). The phone plays it forward, holds, and plays it backward. The mannequin's mesh and skin
// are the same in every clip, so they are written once, to `mannequin.bin`, which each clip reads
// by URI (glb.mjs). `mannequins.json` indexes the clips by exercise id. Every pose is checked
// first (checks.mjs), and if any fails, nothing is written.
//
//   node bake.mjs [out-dir]     (default: app/src/main/assets/mannequins)
import { writeFile, mkdir } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import * as THREE from 'three';
import { GLTFExporter } from 'three/examples/jsm/exporters/GLTFExporter.js';
import { tempoOf } from './tempo.mjs';
import { prepare, benchAngles } from './entries.mjs';
import { buildStage, FRAMES } from './stage.mjs';
import { CHECKS, checkPose } from './checks.mjs';
import { shareParts } from './glb.mjs';
import { benchHinge } from './kit.mjs';
import { POSES } from './poses.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));
/** Where the clips and their index ship: the app's assets. */
export const CLIPS = path.join(HERE, '..', '..', 'app', 'src', 'main', 'assets', 'mannequins');

// GLTFExporter reads its blobs back through FileReader, which Node doesn't have.
globalThis.FileReader ??= class {
  readAsArrayBuffer(blob) {
    blob.arrayBuffer().then((buffer) => { this.result = buffer; this.onloadend(); });
  }
};

const rounded = (v) => Math.round(v * 1e5) / 1e5;

/** Throws, naming each failing check, unless the pose passes them all (at every bench angle it is baked at). */
async function mustPass(id, poses) {
  for (const angle of benchAngles(id) ?? [undefined]) {
    const { checks } = await checkPose(id, { poses, angle });
    const failing = CHECKS.filter((c) => !checks[c].ok);
    if (failing.length) {
      const why = failing.map((c) => `${c} (frame ${checks[c].frame}: ${checks[c].found})`).join(', ');
      throw new Error(`${id}${angle === undefined ? '' : ` at ${angle}°`} fails its checks: ${why}`);
    }
  }
}

/** The name of a clip's file, without `.glb`: the exercise's id, and the bench angle it stands at when it has one. */
const clipName = (id, angle) => (angle === undefined ? id : `${id}_${angle}`);

/** The file the clips share their mannequin's mesh and skin from. */
export const SHARED = 'mannequin.bin';

/** The file the clips posed in kit `name` share its parts from. */
export const kitFile = (name) => `kit_${name}.bin`;

/** Bakes one entry, at `angle` if it has an adjustable bench: its .glb's bytes, the mannequin still in it, and the index entry. */
async function bake(id, poses, angle) {
  const entry = prepare(id, poses, angle);
  const { scene, rig, kit, pose } = await buildStage(entry);

  // Sample the lowering. Every bone that turns gets a track, and so does every part of the kit
  // that moves, turns or stretches; the pelvis always moves.
  const bones = Object.values(rig.bones);
  const parts = [];
  kit.group.traverse((o) => { if (o !== kit.group) parts.push(o); });
  const times = [];
  const rotations = bones.map(() => []);
  const pelvis = [];
  const placed = parts.map(() => ({ position: [], quaternion: [], scale: [] }));
  for (let f = 0; f < FRAMES; f++) {
    const d = f / (FRAMES - 1);
    pose(d);
    times.push(d);
    bones.forEach((b, i) => rotations[i].push(...b.quaternion.toArray().map(rounded)));
    pelvis.push(...rig.bones.pelvis.position.toArray().map(rounded));
    parts.forEach((o, i) => {
      for (const k of ['position', 'quaternion', 'scale']) placed[i][k].push(...o[k].toArray().map(rounded));
    });
  }
  const tracks = [];
  const moves = (values, size) => values.some((v, i) => Math.abs(v - values[i % size]) > 1e-5);
  bones.forEach((b, i) => {
    if (moves(rotations[i], 4)) tracks.push(new THREE.QuaternionKeyframeTrack(`${b.name}.quaternion`, times, rotations[i]));
  });
  tracks.push(new THREE.VectorKeyframeTrack('pelvis.position', times, pelvis));
  parts.forEach((o, i) => {
    const { position, quaternion, scale } = placed[i];
    if (moves(position, 3)) tracks.push(new THREE.VectorKeyframeTrack(`${o.name}.position`, times, position));
    if (moves(quaternion, 4)) tracks.push(new THREE.QuaternionKeyframeTrack(`${o.name}.quaternion`, times, quaternion));
    if (moves(scale, 3)) tracks.push(new THREE.VectorKeyframeTrack(`${o.name}.scale`, times, scale));
  });
  const clip = new THREE.AnimationClip('lowering', 1, tracks);

  // The file opens on the top of the rep, so a bone or part left without a track keeps its pose
  // there.
  pose(0);

  const glb = await new GLTFExporter().parseAsync(scene, { binary: true, animations: [clip] });
  return {
    glb: Buffer.from(glb),
    entry: { clip: `${clipName(id, angle)}.glb`, equipment: entry.equipment, camera: { ...entry.camera }, tempo: tempoOf(entry), resources: [SHARED, ...Object.keys(entry.kit ?? {}).sort().map(kitFile)] },
  };
}

/**
 * Bakes every entry into `out`: one .glb each, the mannequin they share and mannequins.json. A
 * pose that fails its checks stops it before anything is written.
 */
export async function bakeAll(out = CLIPS, { poses = POSES } = {}) {
  const ids = Object.keys(poses).sort();
  for (const id of ids) {
    const angles = benchAngles(id);
    const { bench } = poses[id].like ? prepare(id, poses) : poses[id];
    if (angles && !Number.isInteger(bench?.typical)) throw new Error(`${id}: an adjustable-bench pose needs a typical angle (bench: { typical })`);
    if (!angles && bench) throw new Error(`${id}: has a bench angle, but the library gives it no adjustable bench`);
    await mustPass(id, poses);
  }
  // A clip per exercise, or per bench angle for an adjustable bench.
  const baked = [];
  for (const id of ids) for (const angle of benchAngles(id) ?? [undefined]) baked.push([clipName(id, angle), id, angle, await bake(id, poses, angle)]);
  const { clips, shared } = shareParts(baked.map(([name, , , b]) => [name, b.glb]), { body: SHARED, kit: kitFile });
  await mkdir(out, { recursive: true });
  for (const [uri, bytes] of Object.entries(shared)) await writeFile(path.join(out, uri), bytes);
  const index = {};
  for (const id of ids) {
    const mine = baked.filter(([, i]) => i === id);
    if (mine[0][2] === undefined) { index[id] = mine[0][3].entry; continue; }
    // Each angle's own clip and camera, the typical angle, and the hinge the back pad turns about.
    const { typical } = prepare(id, poses).bench;
    const hinge = benchHinge(prepare(id, poses).kit.bench);
    const nearest = mine.reduce((n, m) => (Math.abs(m[2] - typical) < Math.abs(n[2] - typical) ? m : n));
    index[id] = {
      ...nearest[3].entry,
      bench: {
        typical,
        hinge: [rounded(hinge.y), rounded(hinge.z)],
        clips: Object.fromEntries(mine.map(([, , angle, b]) => [angle, { clip: b.entry.clip, camera: b.entry.camera }])),
      },
    };
  }
  for (const [name, glb] of clips) await writeFile(path.join(out, `${name}.glb`), glb);
  await writeFile(path.join(out, 'mannequins.json'), JSON.stringify({ clips: index }, null, 2) + '\n');
  return index;
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const out = process.argv[2] ? path.resolve(process.argv[2]) : CLIPS;
  const index = await bakeAll(out);
  for (const id of Object.keys(index)) console.log(`baked ${id} → ${path.relative(process.cwd(), path.join(out, index[id].clip))}`);
}
