// Bakes each pose entry into a clip the phone plays: the mannequin, its equipment and the floor
// shadow in one .glb, with one animation of the rep's lowering, from the top (0 s) to the bottom
// (1 s). The phone plays it forward, holds, and plays it backward. The mannequin's mesh and skin
// are the same in every clip, so they are written once, to `mannequin.bin`, which each clip reads
// by URI (glb.mjs). `mannequins.json` indexes the clips by exercise id. Every pose is checked
// first (checks.mjs), and if any fails, nothing is written.
//
//   node bake.mjs [out-dir]     (default: app/src/main/assets/mannequins)
//   node bake.mjs [out-dir] --only <id,id>     bake these poses alone, as a save does (`bakeOnly`)
//
// A seat seats.json doesn't hold is solved and added to it. `--seats` (`npm run seats`) solves every
// seat afresh and writes the file whole, for a change to the grip solve or the mannequin.
import { readFile, readdir, writeFile, mkdir } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import * as THREE from 'three';
import { GLTFExporter } from 'three/examples/jsm/exporters/GLTFExporter.js';
import { tempoOf } from './tempo.mjs';
import { prepare, benchAngles, gripsOf } from './entries.mjs';
import { buildStage, FRAMES } from './stage.mjs';
import { CHECKS, checkPose } from './checks.mjs';
import { forgetSeats, newSeats } from './grip.mjs';
import { inlineResources, shareParts } from './glb.mjs';
import { benchHinge } from './recipes/bench.mjs';
import { POSES } from './poses.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));
/** Where the clips and their index ship: the app's assets. */
export const CLIPS = path.join(HERE, '..', '..', 'app', 'src', 'main', 'assets', 'mannequins');
/** Each seat the poses use, solved once (`seatOf`). */
const SEATS = path.join(HERE, 'seats.json');

// GLTFExporter reads its blobs back through FileReader, which Node doesn't have.
globalThis.FileReader ??= class {
  readAsArrayBuffer(blob) {
    blob.arrayBuffer().then((buffer) => { this.result = buffer; this.onloadend(); });
  }
};

const rounded = (v) => Math.round(v * 1e5) / 1e5;

/** Each clip an exercise bakes: [bench angle, grip], one for each bench angle or each grip it has, or one. */
export const variantsOf = (id, poses) => (benchAngles(id) ?? [undefined]).flatMap((angle) => (gripsOf(id, poses) ?? [undefined]).map((grip) => [angle, grip]));

/** Throws, naming each failing check, unless the pose passes them all (at every bench angle and grip it is baked at). */
async function mustPass(id, poses) {
  for (const [angle, grip] of variantsOf(id, poses)) {
    const { checks } = await checkPose(id, { poses, angle, grip });
    const failing = CHECKS.filter((c) => !checks[c].ok);
    if (failing.length) {
      const why = failing.map((c) => `${c} (frame ${checks[c].frame}: ${checks[c].found})`).join(', ');
      throw new Error(`${id}${angle === undefined ? '' : ` at ${angle}°`}${grip === undefined ? '' : ` with the ${grip} grip`} fails its checks: ${why}`);
    }
  }
}

/** The name of a clip's file, without `.glb`: the exercise's id, and the bench angle it stands at or the grip it takes, when it has one. */
const clipName = (id, angle, grip) => [id, angle, grip].filter((p) => p !== undefined).join('_');

/** The file the clips share their mannequin's mesh and skin from. */
export const SHARED = 'mannequin.bin';

/** The file the clips posed in kit `name` share its parts from. */
export const kitFile = (name) => `kit_${name}.bin`;

/** Bakes one entry, at `angle` if it has an adjustable bench and with `grip` if it has grips: its .glb's bytes, the mannequin still in it, and the index entry. */
async function bake(id, poses, angle, grip) {
  const entry = prepare(id, poses, angle, grip);
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
    entry: { clip: `${clipName(id, angle, grip)}.glb`, equipment: entry.equipment, camera: { ...entry.camera }, tempo: tempoOf(entry), resources: [SHARED, ...Object.keys(entry.kit ?? {}).sort().map(kitFile)] },
  };
}

/** Throws unless pose `id` can bake: a bench that fits the library, and every check passed. */
async function mustBake(id, poses) {
  const angles = benchAngles(id);
  const { bench } = poses[id].like ? prepare(id, poses) : poses[id];
  if (angles && !Number.isInteger(bench?.typical)) throw new Error(`${id}: an adjustable-bench pose needs a typical angle (bench: { typical })`);
  if (!angles && bench) throw new Error(`${id}: has a bench angle, but the library gives it no adjustable bench`);
  await mustPass(id, poses);
}

/** A clip per exercise, or per bench angle for an adjustable bench, or per grip: those of poses `ids`, baked. */
async function bakeClips(ids, poses) {
  const clips = [];
  for (const id of ids) {
    for (const [angle, grip] of variantsOf(id, poses)) clips.push({ name: clipName(id, angle, grip), id, angle, grip, ...await bake(id, poses, angle, grip) });
  }
  return clips;
}

/** The index's entry for exercise `id`, from its baked clips `mine`. */
function indexEntry(id, mine, poses) {
  if (mine[0].grip !== undefined) {
    // The first set's grip is the entry's own clip; each grip's clip and camera, in set order.
    return { ...mine[0].entry, grips: mine.map(({ grip, entry }) => ({ name: grip, clip: entry.clip, camera: entry.camera })) };
  }
  if (mine[0].angle === undefined) return mine[0].entry;
  // Each angle's own clip and camera, the typical angle, and the hinge the back pad turns about.
  const { typical } = prepare(id, poses).bench;
  const hinge = benchHinge(prepare(id, poses).kit.bench);
  const nearest = mine.reduce((n, m) => (Math.abs(m.angle - typical) < Math.abs(n.angle - typical) ? m : n));
  return {
    ...nearest.entry,
    bench: {
      typical,
      hinge: [rounded(hinge.y), rounded(hinge.z)],
      clips: Object.fromEntries(mine.map(({ angle, entry }) => [angle, { clip: entry.clip, camera: entry.camera }])),
    },
  };
}

/** The index's entries for poses `ids`, from their baked `clips`. */
const indexEntries = (ids, clips, poses) => Object.fromEntries(ids.map((id) => [id, indexEntry(id, clips.filter((c) => c.id === id), poses)]));

const indexBytes = (index) => Buffer.from(JSON.stringify({ clips: index }, null, 2) + '\n');

/**
 * Bakes every entry into `out`: one .glb each, the mannequin they share and mannequins.json. A
 * pose that fails its checks stops it before anything is written.
 */
export async function bakeAll(out = CLIPS, { poses = POSES } = {}) {
  const ids = Object.keys(poses).sort();
  for (const id of ids) await mustBake(id, poses);
  const baked = await bakeClips(ids, poses);
  const { clips, shared } = shareParts(baked.map(({ name, glb }) => [name, glb]), { body: SHARED, kit: kitFile });
  await mkdir(out, { recursive: true });
  for (const [uri, bytes] of Object.entries(shared)) await writeFile(path.join(out, uri), bytes);
  const index = indexEntries(ids, baked, poses);
  for (const [name, glb] of clips) await writeFile(path.join(out, `${name}.glb`), glb);
  await writeFile(path.join(out, 'mannequins.json'), indexBytes(index));
  return index;
}

/**
 * What `out` holds of a bake of `poses`: its index, and each clip made whole, by name. Null if it
 * can't give them all, as with a pose not baked yet.
 */
async function bakedIn(out, poses) {
  const read = (file) => readFile(path.join(out, file));
  try {
    const { clips: index } = JSON.parse(await read('mannequins.json'));
    const ids = Object.keys(poses).sort();
    if (Object.keys(index).sort().join() !== ids.join()) return null;
    const bins = (await readdir(out)).filter((f) => f.endsWith('.bin'));
    const files = Object.fromEntries(await Promise.all(bins.map(async (f) => [f, await read(f)])));
    const names = ids.flatMap((id) => variantsOf(id, poses).map(([angle, grip]) => clipName(id, angle, grip)));
    const clips = new Map();
    for (const name of names) clips.set(name, inlineResources(await read(`${name}.glb`), files));
    return { index, clips };
  } catch {
    return null;
  }
}

/**
 * Bakes poses `ids` alone into `out`, which holds a bake of the rest: `ids` must name each pose a
 * save changed, a pose that takes it by `like:` included. The other clips aren't baked. They are read
 * back whole and shared again with the new ones, in a full bake's order, so every file comes out as
 * a full bake writes it, and only a file that differs is written: a shared file, and the clips that
 * read it, only if these poses changed it. A pose that fails its checks stops it before anything is
 * written, and `out` holding anything but the same mannequin and poses makes it a full bake. Gives
 * the files it wrote.
 */
export async function bakeOnly(ids, out = CLIPS, { poses = POSES } = {}) {
  for (const id of ids) await mustBake(id, poses);
  const bakeEverything = async () => {
    await bakeAll(out, { poses });
    return readdir(out);
  };
  const held = await bakedIn(out, poses);
  if (!held) return bakeEverything();
  const baked = await bakeClips(ids, poses);
  for (const { name, glb } of baked) held.clips.set(name, glb);
  let packed;
  try {
    packed = shareParts([...held.clips], { body: SHARED, kit: kitFile });
  } catch {
    return bakeEverything();
  }
  const files = {
    ...packed.shared,
    ...Object.fromEntries(packed.clips.map(([name, glb]) => [`${name}.glb`, glb])),
    'mannequins.json': indexBytes({ ...held.index, ...indexEntries(ids, baked, poses) }),
  };
  const written = [];
  for (const [file, bytes] of Object.entries(files)) {
    const at = path.join(out, file);
    if ((await readFile(at).catch(() => null))?.equals(bytes)) continue;
    await writeFile(at, bytes);
    written.push(file);
  }
  return written;
}

/** Writes `seats` into seats.json, a line each, on top of what it holds unless `whole`. */
async function writeSeats(seats, whole) {
  const all = { ...(whole ? {} : JSON.parse(await readFile(SEATS, 'utf8'))), ...seats };
  await writeFile(SEATS, `{\n${Object.entries(all).map(([key, offs]) => `${JSON.stringify(key)}: ${JSON.stringify(offs)}`).join(',\n')}\n}\n`);
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const args = process.argv.slice(2);
  const whole = args.includes('--seats');
  const only = args.includes('--only') ? args[args.indexOf('--only') + 1].split(',') : null;
  const dir = args.find((a, i) => !a.startsWith('--') && args[i - 1] !== '--only');
  const out = dir ? path.resolve(dir) : CLIPS;
  if (whole) forgetSeats();
  if (only) {
    for (const file of await bakeOnly(only, out)) console.log(`wrote ${path.relative(process.cwd(), path.join(out, file))}`);
  } else {
    const index = await bakeAll(out);
    for (const id of Object.keys(index)) console.log(`baked ${id} → ${path.relative(process.cwd(), path.join(out, index[id].clip))}`);
  }
  const seats = newSeats();
  if (whole || Object.keys(seats).length) await writeSeats(seats, whole);
}
